package edu.cit.nunez.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class LegacySupplyAdapter implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyAdapter.class);

    private final SupplierOrderRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient;
    private final String apiKey;
    private final String clientId;

    private String sessionToken = null;

    private static final Map<String, ProductMapping> PRODUCT_MAPPINGS = Map.of(
            "P100", new ProductMapping("THP-5117", 10), // Wireless Mouse
            "P200", new ProductMapping("THP-2478", 5),  // Mechanical Keyboard
            "P300", new ProductMapping("THP-5129", 12)  // USB Hub 4-Port
    );

    record ProductMapping(String supplierSku, int packSize) {}

    public LegacySupplyAdapter(
            SupplierOrderRepository repository,
            ApplicationEventPublisher eventPublisher,
            @Value("${LS_API_KEY:LSK-D7CCC0B6BF5993F1C549}") String apiKey,
            @Value("${LS_CLIENT_ID:23-1498-418}") String clientId) {

        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.apiKey = apiKey;
        this.clientId = clientId;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl("https://legacysupply.onrender.com/api/v1")
                .requestFactory(factory)
                .build();
    }

    @Override
    @Transactional
    public SupplierOrderResult requestReorder(String productId, int unitsNeeded) {
        ProductMapping mapping = PRODUCT_MAPPINGS.get(productId);
        if (mapping == null) {
            return new SupplierOrderResult(null, null, null, SupplierOrderStatus.FAILED, "Unmapped product: " + productId);
        }

        int casesToOrder = (int) Math.ceil((double) unitsNeeded / (double) mapping.packSize());
        String requestId = UUID.randomUUID().toString();
        String buyerRef = UUID.randomUUID().toString();

        SupplierOrder order = new SupplierOrder(productId, buyerRef, requestId, casesToOrder, unitsNeeded, SupplierOrderStatus.PENDING);
        order = repository.save(order);

        sendToLegacySupply(order, mapping);

        return new SupplierOrderResult(order.getId(), order.getBuyerRef(), order.getPoNumber(), order.getStatus(), "Processed");
    }

    private synchronized void sendToLegacySupply(SupplierOrder order, ProductMapping mapping) {
        try {
            if (sessionToken == null) {
                authenticate();
                fetchCatalog();
            }

            String xmlPayload = """
                <PurchaseOrder>
                  <SupplierSku>%s</SupplierSku>
                  <Qty>%d</Qty>
                  <BuyerRef>%s</BuyerRef>
                </PurchaseOrder>
                """.formatted(mapping.supplierSku(), order.getCases(), order.getBuyerRef());

            String responseXml = restClient.post()
                    .uri("/purchase-orders")
                    .header("X-LS-Session", sessionToken)
                    .header("X-Request-Id", order.getRequestId())
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xmlPayload)
                    .retrieve()
                    .body(String.class);

            String poNumber = extractXmlValue(responseXml, "PoNumber");
            if (poNumber != null && !poNumber.isEmpty()) {
                order.setPoNumber(poNumber);
                order.setStatus(SupplierOrderStatus.SUBMITTED);
                repository.save(order);
                log.info("Successfully created purchase order {} on LegacySupply", poNumber);
            }

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Rate limited (429) during PO submission. Retrying in next polling cycle...");
            order.setStatus(SupplierOrderStatus.PENDING);
            repository.save(order);
        } catch (Exception e) {
            log.error("LegacySupply purchase order submission failed: {}", e.getMessage());
            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("E-AUTH"))) {
                sessionToken = null;
            }
            order.setStatus(SupplierOrderStatus.PENDING);
            repository.save(order);
        }
    }

    private void authenticate() {
        log.info("Authenticating with LegacySupply using ClientId: {}", clientId);
        String authXml = """
            <AuthRequest>
              <ClientId>%s</ClientId>
              <ApiKey>%s</ApiKey>
            </AuthRequest>
            """.formatted(clientId, apiKey);

        String responseXml = restClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_XML)
                .body(authXml)
                .retrieve()
                .body(String.class);

        this.sessionToken = extractXmlValue(responseXml, "SessionToken");
        log.info("LegacySupply session token acquired: {}", sessionToken);
    }

    private void fetchCatalog() {
        try {
            log.info("Fetching catalog from LegacySupply...");
            restClient.get()
                    .uri("/catalog")
                    .header("X-LS-Session", sessionToken)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Catalog fetch failed: {}", e.getMessage());
        }
    }

    // Using fixedDelay instead of fixedRate to prevent execution overlaps
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void pollOpenOrdersAndRetryPending() {
        // 1. Retry any PENDING orders
        List<SupplierOrder> pendingOrders = repository.findByStatusIn(List.of(SupplierOrderStatus.PENDING));
        for (SupplierOrder order : pendingOrders) {
            ProductMapping mapping = PRODUCT_MAPPINGS.get(order.getProductId());
            if (mapping != null) sendToLegacySupply(order, mapping);
        }

        // 2. Poll open orders for status updates
        List<SupplierOrder> openOrders = repository.findByStatusIn(
                List.of(SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.PROCESSING, SupplierOrderStatus.SHIPPED)
        );

        for (SupplierOrder order : openOrders) {
            if (order.getPoNumber() == null) continue;
            try {
                // Throttle: Delay 2.5 seconds BEFORE each poll to guarantee spacing
                Thread.sleep(2500);

                if (sessionToken == null) authenticate();

                String responseXml = restClient.get()
                        .uri("/purchase-orders/{poNumber}", order.getPoNumber())
                        .header("X-LS-Session", sessionToken)
                        .retrieve()
                        .body(String.class);

                String statusCode = extractXmlValue(responseXml, "StatusCode");
                String statusText = extractXmlValue(responseXml, "Status");

                if (("40".equals(statusCode) || "DELIVERED".equalsIgnoreCase(statusText)) && order.getStatus() != SupplierOrderStatus.DELIVERED) {
                    order.setStatus(SupplierOrderStatus.DELIVERED);
                    ProductMapping mapping = PRODUCT_MAPPINGS.get(order.getProductId());
                    if (mapping != null) {
                        eventPublisher.publishEvent(new OrderDeliveredEvent(order.getProductId(), order.getCases() * mapping.packSize()));
                    }
                    log.info("--> [LegacySupply] Order {} tracked to DELIVERED.", order.getPoNumber());
                } else if ("90".equals(statusCode) || "CANCELLED".equalsIgnoreCase(statusText)) {
                    order.setStatus(SupplierOrderStatus.FAILED);
                    log.info("--> [LegacySupply] Order {} noticed as CANCELLED.", order.getPoNumber());
                } else if ("20".equals(statusCode)) {
                    order.setStatus(SupplierOrderStatus.PROCESSING);
                } else if ("30".equals(statusCode)) {
                    order.setStatus(SupplierOrderStatus.SHIPPED);
                }

                repository.save(order);
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("Rate limited (429) during polling. Backing off for 10 seconds...");
                try {
                    Thread.sleep(10000); // Back off 10s on HTTP 429
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Polling error for PO {}: {}", order.getPoNumber(), e.getMessage());

                if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("E-AUTH"))) {
                    log.warn("Clearing expired session token to trigger re-auth on next request.");
                    this.sessionToken = null;
                }
            }
        }
    }

    private String extractXmlValue(String xml, String tag) {
        if (xml == null) return null;
        int start = xml.indexOf("<" + tag + ">");
        int end = xml.indexOf("</" + tag + ">");
        if (start != -1 && end != -1) {
            return xml.substring(start + tag.length() + 2, end);
        }
        return null;
    }
}