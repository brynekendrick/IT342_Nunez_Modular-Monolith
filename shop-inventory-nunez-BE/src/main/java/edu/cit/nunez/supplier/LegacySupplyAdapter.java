package edu.cit.nunez.supplier;

import edu.cit.nunez.InstanceLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Service
class LegacySupplyAdapter implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyAdapter.class);

    private final SupplierOrderRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient;
    private final String apiKey;
    private final String clientId;
    private final InstanceLifecycle instanceLifecycle;

    private String sessionToken = null;
    private final AtomicBoolean polling = new AtomicBoolean(false);

    private static final Map<String, ProductMapping> PRODUCT_MAPPINGS = Map.of(
            "P100", new ProductMapping("THP-5117", 10),
            "P200", new ProductMapping("THP-2478", 5),
            "P300", new ProductMapping("THP-5129", 12)
    );

    record ProductMapping(String supplierSku, int packSize) {}

    public LegacySupplyAdapter(
            SupplierOrderRepository repository,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("appInstanceId") String instanceId,
            @Value("${LS_API_KEY:LSK-D7CCC0B6BF5993F1C549}") String apiKey,
            @Value("${LS_CLIENT_ID:23-1498-418}") String clientId,
            InstanceLifecycle instanceLifecycle) {

        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.apiKey = apiKey;
        this.clientId = clientId;
        this.instanceLifecycle = instanceLifecycle;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(6000);

        this.restClient = RestClient.builder()
                .baseUrl("https://legacysupply.onrender.com/api/v1")
                .defaultHeader("X-Client-Instance", instanceId)
                .requestFactory(factory)
                .build();
    }

    @Override
    @Transactional
    public SupplierOrderResult requestReorder(String productId, int unitsNeeded) {
        // Default: non-blocking local queue (poller submits remotely).
        return requestReorder(productId, unitsNeeded, false);
    }

    @Override
    @Transactional
    public SupplierOrderResult requestReorder(String productId, int unitsNeeded, boolean submitNow) {
        ProductMapping mapping = PRODUCT_MAPPINGS.get(productId);
        if (mapping == null) {
            return new SupplierOrderResult(null, null, null, SupplierOrderStatus.FAILED, "Unmapped product: " + productId);
        }

        // Check if an order already exists that is SUBMITTED, PROCESSING, or SHIPPED with poNumber
        List<SupplierOrder> active = repository.findByProductIdAndStatusIn(
                productId,
                List.of(
                        SupplierOrderStatus.SUBMITTED,
                        SupplierOrderStatus.PROCESSING,
                        SupplierOrderStatus.SHIPPED
                )
        );
        for (SupplierOrder openOrder : active) {
            if (openOrder.getPoNumber() != null && !openOrder.getPoNumber().isBlank()) {
                return new SupplierOrderResult(
                        openOrder.getId(), openOrder.getBuyerRef(), openOrder.getPoNumber(),
                        openOrder.getStatus(), "Existing open PO reused");
            }
        }

        // If there's a PENDING order, try to submit it if submitNow is true
        List<SupplierOrder> pending = repository.findByProductIdAndStatusIn(
                productId,
                List.of(SupplierOrderStatus.PENDING)
        );
        if (!pending.isEmpty()) {
            SupplierOrder existingPending = pending.get(0);
            if (submitNow && instanceLifecycle != null && instanceLifecycle.isRegistered()) {
                sendToLegacySupply(existingPending, mapping);
                if (existingPending.getPoNumber() != null && !existingPending.getPoNumber().isBlank()) {
                    return new SupplierOrderResult(
                            existingPending.getId(), existingPending.getBuyerRef(), existingPending.getPoNumber(),
                            existingPending.getStatus(), "Submitted");
                }
            }
            return new SupplierOrderResult(
                    existingPending.getId(), existingPending.getBuyerRef(), existingPending.getPoNumber(),
                    existingPending.getStatus(), "Pending local PO");
        }

        int casesToOrder = (int) Math.ceil((double) unitsNeeded / (double) mapping.packSize());
        String requestId = UUID.randomUUID().toString();

        SupplierOrder order = new SupplierOrder(productId, "TEMP", requestId, casesToOrder, unitsNeeded, SupplierOrderStatus.PENDING);
        order = repository.save(order);

        String buyerRef = "RO-" + order.getId();
        order.setBuyerRef(buyerRef);
        repository.save(order);

        if (submitNow && instanceLifecycle != null && instanceLifecycle.isRegistered()) {
            sendToLegacySupply(order, mapping);
            if (order.getPoNumber() != null && !order.getPoNumber().isBlank()) {
                return new SupplierOrderResult(order.getId(), order.getBuyerRef(), order.getPoNumber(), order.getStatus(), "Submitted");
            }
        }

        log.info("[LegacySupply] Queued local PENDING PO for {} (submitNow={})", productId, submitNow);
        return new SupplierOrderResult(order.getId(), order.getBuyerRef(), order.getPoNumber(), order.getStatus(),
                "Local PO queued");
    }

    @Override
    public boolean hasActivePurchaseOrder(String productId) {
        if (productId == null || productId.isBlank()) {
            return false;
        }

        List<SupplierOrder> activeOrders = repository.findByProductIdAndStatusIn(
                productId,
                List.of(
                        SupplierOrderStatus.SUBMITTED,
                        SupplierOrderStatus.PROCESSING,
                        SupplierOrderStatus.SHIPPED
                )
        );
        return activeOrders.stream().anyMatch(o -> o.getPoNumber() != null && !o.getPoNumber().isBlank());
    }

    private synchronized void sendToLegacySupply(SupplierOrder order, ProductMapping mapping) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                if (sessionToken == null) {
                    authenticate();
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
                    return;
                }
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("Rate limited (429) during PO submission. Will retry on next poll cycle.");
                order.setStatus(SupplierOrderStatus.PENDING);
                repository.save(order);
                return;
            } catch (Exception e) {
                log.error("LegacySupply purchase order submission failed (attempt {}): {}", attempt, e.getMessage());
                if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("E-AUTH"))) {
                    sessionToken = null;
                    if (attempt == 1) {
                        continue; // retry immediately once with fresh token
                    }
                }
                order.setStatus(SupplierOrderStatus.PENDING);
                repository.save(order);
                return;
            }
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
        log.info("LegacySupply session token acquired: {}", sessionToken != null ? "SUCCESS" : "NULL");
    }

    @SuppressWarnings("unused")
    @Scheduled(fixedDelay = 4000, initialDelay = 2000)
    @Transactional
    public void pollOpenOrdersAndRetryPending() {
        if (instanceLifecycle == null || !instanceLifecycle.hasRecentHeartbeat()) {
            return;
        }
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            List<SupplierOrder> pendingOrders = repository.findByStatusIn(List.of(SupplierOrderStatus.PENDING));
            for (SupplierOrder order : pendingOrders) {
                ProductMapping mapping = PRODUCT_MAPPINGS.get(order.getProductId());
                if (mapping != null) {
                    sendToLegacySupply(order, mapping);
                }
            }

            List<SupplierOrder> openOrders = repository.findByStatusIn(
                    List.of(SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.PROCESSING, SupplierOrderStatus.SHIPPED)
            );

            for (SupplierOrder order : openOrders) {
                pollSingleOrder(order.getPoNumber());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            polling.set(false);
        }
    }

    private void pollSingleOrder(String poNumber) {
        if (poNumber == null || poNumber.isBlank()) return;

        try {
            if (sessionToken == null) {
                authenticate();
            }

            String responseXml = restClient.get()
                    .uri("/purchase-orders/{poNumber}", poNumber)
                    .header("X-LS-Session", sessionToken)
                    .retrieve()
                    .body(String.class);

            String statusCode = extractXmlValue(responseXml, "StatusCode");
            String statusText = extractXmlValue(responseXml, "Status");

            if ("90".equals(statusCode) || "CANCELLED".equalsIgnoreCase(statusText)) {
                repository.findByPoNumber(poNumber).ifPresent(order -> {
                    order.setStatus(SupplierOrderStatus.FAILED);
                    repository.save(order);
                    log.info("--> [LegacySupply] Order {} polled as CANCELLED.", poNumber);
                });
            }
            else if ("40".equals(statusCode) || "DELIVERED".equalsIgnoreCase(statusText)) {
                repository.findByPoNumber(poNumber).ifPresent(order -> {
                    if (order.getStatus() != SupplierOrderStatus.DELIVERED) {
                        order.setStatus(SupplierOrderStatus.DELIVERED);
                        repository.save(order);
                        ProductMapping mapping = PRODUCT_MAPPINGS.get(order.getProductId());
                        if (mapping != null) {
                            eventPublisher.publishEvent(new OrderDeliveredEvent(
                                    order.getProductId(), order.getCases() * mapping.packSize()));
                        }
                        log.info("--> [LegacySupply] Order {} tracked to DELIVERED. Restock event published.", poNumber);
                    }
                });
            }

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Rate limited (429) during status check. Skipping remaining polls this cycle.");
        } catch (Exception e) {
            log.error("Error polling PO {}: {}", poNumber, e.getMessage());
            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("E-AUTH"))) {
                this.sessionToken = null;
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
