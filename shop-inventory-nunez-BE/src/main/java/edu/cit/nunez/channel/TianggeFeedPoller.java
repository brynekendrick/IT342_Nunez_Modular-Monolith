package edu.cit.nunez.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.cit.nunez.inventory.Inventory;
import edu.cit.nunez.inventory.InventoryService;
import edu.cit.nunez.shop.OrderService;
import edu.cit.nunez.shop.dto.OrderItemDto;
import edu.cit.nunez.shop.dto.OrderRequest;
import edu.cit.nunez.shop.dto.OrderResponse;
import edu.cit.nunez.supplier.OrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
class TianggeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);

    private final RestClient tianggeRestClient;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final TianggeCursorRepository cursorRepository;
    private final TianggeProcessedEventRepository processedEventRepository;
    private final TianggeBackorderRepository backorderRepository;
    private final TianggeOrderRecordRepository tianggeOrderRecordRepository;
    private final TianggeService tianggeService;
    private final TianggeFeedPoller self;

    private final AtomicBoolean polling = new AtomicBoolean(false);

    TianggeFeedPoller(
            RestClient tianggeRestClient,
            OrderService orderService,
            InventoryService inventoryService,
            TianggeCursorRepository cursorRepository,
            TianggeProcessedEventRepository processedEventRepository,
            TianggeBackorderRepository backorderRepository,
            TianggeOrderRecordRepository tianggeOrderRecordRepository,
            TianggeService tianggeService,
            @Lazy TianggeFeedPoller self) {
        this.tianggeRestClient = tianggeRestClient;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.cursorRepository = cursorRepository;
        this.processedEventRepository = processedEventRepository;
        this.backorderRepository = backorderRepository;
        this.tianggeOrderRecordRepository = tianggeOrderRecordRepository;
        this.tianggeService = tianggeService;
        this.self = self;
    }

    @Scheduled(fixedDelay = 500, initialDelay = 1500)
    void pollFeed() {
        if (!TianggeService.isInstanceRegistered) {
            return;
        }
        if (!polling.compareAndSet(false, true)) {
            return;
        }

        try {
            tianggeService.ensureFreshHeartbeat();
            if (!tianggeService.hasRecentHeartbeat()) {
                log.warn("[Tiangge Feed] Skipping poll — no recent heartbeat yet.");
                return;
            }

            long lastCursor = cursorRepository.findById("tiangge_feed_cursor")
                    .map(TianggeCursor::getCursorValue)
                    .orElse(0L);

            FeedResponse feed = tianggeRestClient.get()
                    .uri("/feed?after={cursor}&limit=50", lastCursor)
                    .retrieve()
                    .body(FeedResponse.class);

            if (feed == null || feed.events() == null || feed.events().isEmpty()) {
                return;
            }

            List<FeedEvent> events = new ArrayList<>(feed.events());
            events.sort(Comparator.comparingLong(FeedEvent::seq));

            long highestSuccessfulSeq = lastCursor;

            for (FeedEvent event : events) {
                if (event == null || event.eventId() == null) {
                    continue;
                }

                if (processedEventRepository.existsById(event.eventId())) {
                    log.info("[Tiangge Feed] Event {} already processed. Skipping.", event.eventId());
                    highestSuccessfulSeq = Math.max(highestSuccessfulSeq, event.seq());
                    continue;
                }

                try {
                    if ("ORDER_PLACED".equalsIgnoreCase(event.type())) {
                        processOrderPlaced(event);
                    } else if ("ORDER_CANCELLED".equalsIgnoreCase(event.type())) {
                        processOrderCancelled(event);
                    }
                    self.markEventProcessedOrSkip(event.eventId(), event.orderId(), event.type());
                    highestSuccessfulSeq = Math.max(highestSuccessfulSeq, event.seq());
                } catch (Exception ex) {
                    log.error("[Tiangge Feed] Error processing event {} ({}) : {} — stopping batch here.",
                            event.eventId(), event.type(), ex.getMessage(), ex);
                    break;
                }
            }

            if (highestSuccessfulSeq > lastCursor) {
                cursorRepository.save(new TianggeCursor("tiangge_feed_cursor", highestSuccessfulSeq));
            }

        } catch (Exception e) {
            log.warn("[Tiangge Feed] Error polling order feed: {}", e.getMessage());
        } finally {
            polling.set(false);
        }
    }

    @Transactional
    public void markEventProcessedOrSkip(String eventId, String orderId, String eventType) {
        if (!processedEventRepository.existsById(eventId)) {
            processedEventRepository.save(new TianggeProcessedEvent(eventId, orderId, eventType));
        }
    }

    void processOrderPlaced(FeedEvent event) {
        String tianggeOrderId = event.orderId();

        if (event.lines() == null || event.lines().isEmpty()) {
            log.warn("[Tiangge Order] Empty lines for {}, rejecting.", tianggeOrderId);
            sendDecision(tianggeOrderId, "REJECTED", "SO-EMPTY-" + tianggeOrderId);
            return;
        }

        // Idempotency: Re-send existing decision if already decided
        Optional<TianggeOrderRecord> existingOpt = tianggeOrderRecordRepository.findByTianggeOrderId(tianggeOrderId);
        if (existingOpt.isPresent()) {
            TianggeOrderRecord existing = existingOpt.get();
            if (existing.getDecision() != null && !existing.getDecision().equals("PENDING")) {
                log.info("[Tiangge Order] Repeated delivery for {} — re-sending existing decision {} (shopOrderId=SO-{})",
                        tianggeOrderId, existing.getDecision(), existing.getShopOrderId());
                sendDecision(tianggeOrderId, existing.getDecision(), "SO-" + existing.getShopOrderId());
                return;
            }
        }

        List<CommandLine> lines = event.lines();
        List<OrderItemDto> items = lines.stream()
                .map(l -> new OrderItemDto(l.sellerSku(), l.qty()))
                .collect(Collectors.toList());

        boolean reserved = orderService.reserveInternalOrder(items, tianggeOrderId);
        Long shopId = orderService.findFirstShopOrderIdByExternalReference(tianggeOrderId).orElse(null);
        String shopOrderId = "SO-" + (shopId != null ? shopId : tianggeOrderId);

        if (reserved) {
            String decision = "ACCEPTED";
            saveTianggeOrderRecord(tianggeOrderId, shopId, decision);
            sendDecision(tianggeOrderId, decision, shopOrderId);
            // Stock update follows decision!
            pushAllListedStockNow();
        } else {
            // Find which SKUs actually lack stock (aggregated across lines)
            Map<String, Integer> neededPerSku = new LinkedHashMap<>();
            for (CommandLine line : lines) {
                neededPerSku.merge(line.sellerSku(), line.qty(), Integer::sum);
            }

            List<String> shortSkus = new ArrayList<>();
            for (Map.Entry<String, Integer> e : neededPerSku.entrySet()) {
                int stock = inventoryService.getItem(e.getKey()).map(Inventory::getStock).orElse(0);
                if (stock < e.getValue()) {
                    shortSkus.add(e.getKey());
                }
            }

            boolean allShortHaveActivePO = true;
            for (String sku : shortSkus) {
                if (!orderService.hasActivePurchaseOrder(sku)) {
                    orderService.ensurePurchaseOrdersForProducts(List.of(sku), 20, true);
                }
                if (!orderService.hasActivePurchaseOrder(sku)) {
                    allShortHaveActivePO = false;
                }
            }

            if (allShortHaveActivePO && !shortSkus.isEmpty()) {
                String decision = "BACKORDERED";
                saveBackorders(tianggeOrderId, shopId != null ? shopId : -1L, lines);
                saveTianggeOrderRecord(tianggeOrderId, shopId, decision);
                sendDecision(tianggeOrderId, decision, shopOrderId);
            } else {
                String decision = "REJECTED";
                saveTianggeOrderRecord(tianggeOrderId, shopId, decision);
                sendDecision(tianggeOrderId, decision, shopOrderId);
            }
        }
    }

    private void saveTianggeOrderRecord(String tianggeOrderId, Long shopOrderId, String decision) {
        tianggeOrderRecordRepository.findByTianggeOrderId(tianggeOrderId).ifPresentOrElse(rec -> {
            rec.setDecision(decision);
            if (shopOrderId != null) {
                rec.setShopOrderId(shopOrderId);
            }
            tianggeOrderRecordRepository.save(rec);
        }, () -> {
            tianggeOrderRecordRepository.save(new TianggeOrderRecord(tianggeOrderId, shopOrderId, decision));
        });
    }

    private void saveBackorders(String tianggeOrderId, Long shopOrderId, List<CommandLine> lines) {
        long id = shopOrderId != null ? shopOrderId : -1L;
        for (CommandLine line : lines) {
            backorderRepository.save(new TianggeBackorder(
                    tianggeOrderId,
                    id,
                    line.sellerSku(),
                    line.qty()
            ));
        }
        log.info("[Tiangge Order] BACKORDERED {} with {} lines.", tianggeOrderId, lines.size());
    }

    private void pushAllListedStockNow() {
        Map<String, Integer> bySku = new LinkedHashMap<>();
        for (Inventory inv : inventoryService.getAllItems()) {
            bySku.put(inv.getProductId(), inv.getStock());
        }
        List<Map<String, Object>> payload = bySku.entrySet().stream()
                .map(e -> Map.<String, Object>of("sellerSku", e.getKey(), "available", e.getValue()))
                .toList();
        tianggeService.pushStockBatch(payload);
    }

    void processOrderCancelled(FeedEvent event) {
        String tianggeOrderId = event.orderId();
        log.info("[Tiangge Cancel] Processing cancellation for {}", tianggeOrderId);

        orderService.cancelByExternalReference(tianggeOrderId);

        for (TianggeBackorder bo : backorderRepository.findByTianggeOrderId(tianggeOrderId)) {
            if ("PENDING".equals(bo.getResolutionStatus())) {
                bo.setResolutionStatus("CANCELLED");
                backorderRepository.save(bo);
            }
        }

        confirmCancellationWithRetry(tianggeOrderId, 5);

        saveTianggeOrderRecord(tianggeOrderId, null, "CANCELLED");

        pushAllListedStockNow();
    }

    private void sendDecision(String orderId, String decision, String shopOrderId) {
        int attempts = 0;
        while (attempts < 5) {
            try {
                tianggeService.ensureFreshHeartbeat();
                Map<String, Object> body = Map.of(
                        "decision", decision,
                        "shopOrderId", shopOrderId
                );

                tianggeRestClient.post()
                        .uri("/orders/{orderId}/decision", orderId)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();

                log.info("--> [Tiangge] DECISION {} for {} (shopOrderId={})", decision, orderId, shopOrderId);
                return;
            } catch (Exception e) {
                attempts++;
                log.warn("[Tiangge] Decision send failed (attempt {}/5) for {}: {}", attempts, orderId, e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("decision_conflict")) {
                    log.info("[Tiangge] Order {} already has a decision on server. Skipping.", orderId);
                    return;
                }
                if (attempts < 5) {
                    try {
                        Thread.sleep(100L * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    log.error("[Tiangge] Giving up on DECISION {} for {} after 5 retries.", decision, orderId);
                    throw new IllegalStateException("Failed to send decision for " + orderId, e);
                }
            }
        }
    }

    private void confirmCancellationWithRetry(String orderId, int maxAttempts) {
        int attempts = 0;
        while (attempts < maxAttempts) {
            try {
                tianggeService.ensureFreshHeartbeat();
                tianggeRestClient.post()
                        .uri("/orders/{orderId}/cancellation", orderId)
                        .body(Map.of("restocked", true))
                        .retrieve()
                        .toBodilessEntity();

                log.info("--> [Tiangge] Cancellation confirmed for {}", orderId);
                return;
            } catch (Exception e) {
                attempts++;
                log.warn("[Tiangge] Cancel confirm failed (attempt {}/{}) for {} : {}",
                        attempts, maxAttempts, orderId, e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("not_cancelled")) {
                    log.info("[Tiangge] Order {} not cancelled on server.", orderId);
                    return;
                }
                if (attempts < maxAttempts) {
                    try {
                        Thread.sleep(100L * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("[Tiangge] Failed to confirm cancellation for {} after {} attempts.", orderId, maxAttempts);
    }

    /**
     * After inventory restock (@Order 1), try to fill pending backorders for the delivered SKU.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Order(100)
    public void onSupplierDelivery(OrderDeliveredEvent deliveredEvent) {
        if (!TianggeService.isInstanceRegistered
                || deliveredEvent == null
                || deliveredEvent.productId() == null) {
            return;
        }

        String productId = deliveredEvent.productId();
        log.info("[Tiangge Backorder] Delivery arrived for product {}.", productId);

        List<TianggeBackorder> pendingForProduct = backorderRepository
                .findByResolutionStatusAndProductId("PENDING", productId);

        if (!pendingForProduct.isEmpty()) {
            Map<String, List<TianggeBackorder>> byTianggeOrder = pendingForProduct.stream()
                    .collect(Collectors.groupingBy(TianggeBackorder::getTianggeOrderId));

            for (Map.Entry<String, List<TianggeBackorder>> entry : byTianggeOrder.entrySet()) {
                String tianggeOrderId = entry.getKey();

                List<TianggeBackorder> allPending = backorderRepository.findByTianggeOrderId(tianggeOrderId).stream()
                        .filter(b -> "PENDING".equals(b.getResolutionStatus()))
                        .toList();

                if (!canFillAllLines(allPending)) {
                    log.info("[Tiangge Backorder] Waiting for remaining stock before filling {}", tianggeOrderId);
                    continue;
                }

                boolean allReserved = tryReserveBackorderFill(allPending);
                if (!allReserved) {
                    log.warn("[Tiangge Backorder] Reserve failed for {} — will retry later.", tianggeOrderId);
                    continue;
                }

                boolean resolved = sendResolutionWithRetry(tianggeOrderId, "ACCEPTED", 5);
                if (resolved) {
                    for (TianggeBackorder bo : allPending) {
                        bo.setResolutionStatus("RESOLVED_ACCEPTED");
                        backorderRepository.save(bo);
                    }

                    tianggeOrderRecordRepository.findByTianggeOrderId(tianggeOrderId).ifPresent(rec -> {
                        rec.setDecision("BACKORDER_RESOLVED_ACCEPTED");
                        tianggeOrderRecordRepository.save(rec);
                    });

                    log.info("--> [Tiangge Backorder] FILLED {} lines for {}", allPending.size(), tianggeOrderId);
                }
            }
        }

        pushAllListedStockNow();
    }

    private boolean canFillAllLines(List<TianggeBackorder> lines) {
        Map<String, Integer> needed = lines.stream()
                .collect(Collectors.groupingBy(
                        TianggeBackorder::getProductId,
                        Collectors.summingInt(TianggeBackorder::getQuantity)
                ));
        for (Map.Entry<String, Integer> e : needed.entrySet()) {
            int available = inventoryService.getItem(e.getKey()).map(Inventory::getStock).orElse(0);
            if (available < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    private boolean tryReserveBackorderFill(List<TianggeBackorder> lines) {
        List<OrderItemDto> items = new ArrayList<>();
        for (TianggeBackorder line : lines) {
            items.add(new OrderItemDto(line.getProductId(), line.getQuantity()));
        }
        try {
            OrderResponse res = orderService.placeOrderWithReference(
                    new OrderRequest(items),
                    "BACKORDER-FILL-" + lines.get(0).getTianggeOrderId()
            );
            return "CONFIRMED".equalsIgnoreCase(res.getStatus());
        } catch (Exception e) {
            log.warn("[Tiangge Backorder] Failed to reserve backorder fill: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendResolutionWithRetry(String tianggeOrderId, String status, int maxAttempts) {
        int attempts = 0;
        while (attempts < maxAttempts) {
            try {
                tianggeService.ensureFreshHeartbeat();
                tianggeRestClient.post()
                        .uri("/orders/{orderId}/resolution", tianggeOrderId)
                        .body(Map.of("status", status))
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (Exception e) {
                attempts++;
                log.warn("[Tiangge Backorder] Resolution send failed (attempt {}/{}) for {} : {}",
                        attempts, maxAttempts, tianggeOrderId, e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("not_backordered")) {
                    return true;
                }
                if (attempts < maxAttempts) {
                    try {
                        Thread.sleep(100L * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 6000)
    void retryPendingBackorders() {
        if (!TianggeService.isInstanceRegistered) {
            return;
        }
        List<TianggeBackorder> pending = backorderRepository.findByResolutionStatus("PENDING");
        if (pending.isEmpty()) {
            return;
        }
        Map<String, List<TianggeBackorder>> byOrder = pending.stream()
                .collect(Collectors.groupingBy(TianggeBackorder::getTianggeOrderId));

        boolean anyFilled = false;
        for (Map.Entry<String, List<TianggeBackorder>> entry : byOrder.entrySet()) {
            String tianggeOrderId = entry.getKey();
            List<TianggeBackorder> allPending = entry.getValue();
            if (!canFillAllLines(allPending)) {
                continue;
            }
            log.info("[Tiangge Backorder] Scheduled retry found fillable backorder: {}", tianggeOrderId);
            boolean reserved = tryReserveBackorderFill(allPending);
            if (!reserved) {
                continue;
            }
            boolean resolved = sendResolutionWithRetry(tianggeOrderId, "ACCEPTED", 5);
            if (resolved) {
                anyFilled = true;
                for (TianggeBackorder bo : allPending) {
                    bo.setResolutionStatus("RESOLVED_ACCEPTED");
                    backorderRepository.save(bo);
                }
                tianggeOrderRecordRepository.findByTianggeOrderId(tianggeOrderId).ifPresent(rec -> {
                    rec.setDecision("BACKORDER_RESOLVED_ACCEPTED");
                    tianggeOrderRecordRepository.save(rec);
                });
                log.info("--> [Tiangge Backorder] Scheduled retry FILLED {} for {}", allPending.size(), tianggeOrderId);
            }
        }
        if (anyFilled) {
            pushAllListedStockNow();
        }
    }

    record InternalOrderResult(boolean wasReserved, Long shopOrderId, boolean duplicateSkipped) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedResponse(List<FeedEvent> events, Long nextCursor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(
            long seq,
            String eventId,
            String type,
            String orderId,
            String placedAt,
            String decisionDeadline,
            List<CommandLine> lines
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CommandLine(String sellerSku, int qty) {}
}
