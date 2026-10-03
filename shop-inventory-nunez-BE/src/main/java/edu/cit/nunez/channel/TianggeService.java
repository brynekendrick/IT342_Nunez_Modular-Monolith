package edu.cit.nunez.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.cit.nunez.InstanceRegisteredEvent;
import edu.cit.nunez.inventory.Inventory;
import edu.cit.nunez.inventory.InventoryService;
import edu.cit.nunez.inventory.InventoryUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
class TianggeService {

    private static final Logger log = LoggerFactory.getLogger(TianggeService.class);

    /** Never go longer than this between heartbeats (harness marks instance "dead" quickly). */
    private static final int MAX_HEARTBEAT_PERIOD_SEC = 8;
    private static final int MIN_HEARTBEAT_PERIOD_SEC = 4;
    /** If last heartbeat older than this (15s), refresh before other Tiangge calls. Server nextHeartbeat is 30s. */
    private static final long STALE_AFTER_MS = 15000L;

    private final RestClient tianggeRestClient;
    private final TianggeConfig tianggeConfig;
    private final InventoryService inventoryService;
    private final Instant startTime;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledExecutorService heartbeatExecutor;
    private final edu.cit.nunez.InstanceLifecycle instanceLifecycle;

    static volatile boolean isInstanceRegistered = false;

    private final AtomicInteger nextHeartbeatSeconds = new AtomicInteger(MAX_HEARTBEAT_PERIOD_SEC);
    private final AtomicLong lastHeartbeatOkAtMs = new AtomicLong(0);
    private final Object heartbeatLock = new Object();
    private ScheduledFuture<?> heartbeatFuture;

    private static final List<Map<String, String>> LISTINGS = List.of(
            Map.of("sellerSku", "P100", "title", "Wireless Mouse", "supplierSku", "THP-5117"),
            Map.of("sellerSku", "P200", "title", "Mechanical Keyboard", "supplierSku", "THP-2478"),
            Map.of("sellerSku", "P300", "title", "USB Hub 4-Port", "supplierSku", "THP-5129")
    );

    TianggeService(RestClient tianggeRestClient,
                   TianggeConfig tianggeConfig,
                   InventoryService inventoryService,
                   @Qualifier("appStartedAt") Instant startTime,
                   ApplicationEventPublisher eventPublisher,
                   @Qualifier("heartbeatExecutor") ScheduledExecutorService heartbeatExecutor,
                   edu.cit.nunez.InstanceLifecycle instanceLifecycle) {
        this.tianggeRestClient = tianggeRestClient;
        this.tianggeConfig = tianggeConfig;
        this.inventoryService = inventoryService;
        this.startTime = startTime;
        this.eventPublisher = eventPublisher;
        this.heartbeatExecutor = heartbeatExecutor;
        this.instanceLifecycle = instanceLifecycle;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        String instanceId = tianggeConfig.getInstanceId();
        log.info("========================================");
        log.info("Tiangge integration STARTING for instance: {}", instanceId);
        log.info("========================================");

        boolean registered = executeHeartbeat();

        // Start background heartbeat loop immediately
        scheduleNextHeartbeat();

        if (registered) {
            markRegistered();
            publishListings();
            publishInitialStock();
        } else {
            log.error("Heartbeat failed on boot. Deferring listing publication to scheduled cycle.");
        }
    }

    /**
     * Call before any non-heartbeat Tiangge API use so the harness never sees
     * "calls from an instance without a recent heartbeat".
     */
    void ensureFreshHeartbeat() {
        long age = System.currentTimeMillis() - lastHeartbeatOkAtMs.get();
        if (lastHeartbeatOkAtMs.get() > 0 && age < STALE_AFTER_MS) {
            return;
        }
        synchronized (heartbeatLock) {
            age = System.currentTimeMillis() - lastHeartbeatOkAtMs.get();
            if (lastHeartbeatOkAtMs.get() > 0 && age < STALE_AFTER_MS) {
                return;
            }
            boolean ok = false;
            for (int i = 0; i < 3; i++) {
                if (executeHeartbeat()) {
                    ok = true;
                    break;
                }
                try {
                    Thread.sleep(200L * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!ok && !hasRecentHeartbeat()) {
                throw new IllegalStateException("Cannot call Tiangge: instance has no recent heartbeat.");
            }
        }
    }

    boolean hasRecentHeartbeat() {
        long last = lastHeartbeatOkAtMs.get();
        return last > 0 && (System.currentTimeMillis() - last) < 25000L;
    }

    private synchronized void scheduleNextHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        int delaySec = Math.min(MAX_HEARTBEAT_PERIOD_SEC,
                Math.max(MIN_HEARTBEAT_PERIOD_SEC, nextHeartbeatSeconds.get()));
        heartbeatFuture = heartbeatExecutor.schedule(
                this::runScheduledHeartbeat,
                delaySec,
                TimeUnit.SECONDS
        );
        log.debug("[Tiangge] Next heartbeat scheduled in {}s", delaySec);
    }

    private void runScheduledHeartbeat() {
        try {
            boolean success = executeHeartbeat();
            if (success && !isInstanceRegistered) {
                markRegistered();
                publishListings();
                publishInitialStock();
            }
        } finally {
            scheduleNextHeartbeat();
        }
    }

    private synchronized void markRegistered() {
        if (!isInstanceRegistered) {
            isInstanceRegistered = true;
            try {
                eventPublisher.publishEvent(new InstanceRegisteredEvent(this));
            } catch (Exception ignore) {}
        }
    }

    private boolean executeHeartbeat() {
        synchronized (heartbeatLock) {
            try {
                long uptime = Instant.now().getEpochSecond() - startTime.getEpochSecond();
                Map<String, Object> body = Map.of(
                        "appName", "shop-inventory-app",
                        "startedAt", startTime.toString(),
                        "uptimeSeconds", uptime
                );

                HeartbeatResponse response = tianggeRestClient.post()
                        .uri("/instances/heartbeat")
                        .body(body)
                        .retrieve()
                        .body(HeartbeatResponse.class);

                lastHeartbeatOkAtMs.set(System.currentTimeMillis());
                if (instanceLifecycle != null) {
                    instanceLifecycle.recordHeartbeat();
                }

                String instanceId = tianggeConfig.getInstanceId();
                if (response != null && response.nextHeartbeatSeconds() != null) {
                    int next = response.nextHeartbeatSeconds();
                    if (next > 0) {
                        // Beat very early and never slower than MAX_HEARTBEAT_PERIOD_SEC.
                        int early = Math.max(MIN_HEARTBEAT_PERIOD_SEC, (int) Math.floor(next * 0.5));
                        nextHeartbeatSeconds.set(Math.min(MAX_HEARTBEAT_PERIOD_SEC, early));
                    }
                    log.info("--> [Tiangge] Heartbeat OK. Instance: {} | serverNext: {}s | schedule: {}s | serverTime: {}",
                            instanceId, response.nextHeartbeatSeconds(), nextHeartbeatSeconds.get(), response.serverTime());
                } else {
                    log.info("--> [Tiangge] Heartbeat registered for instance: {}", instanceId);
                }
                return true;
            } catch (Exception e) {
                log.warn("[Tiangge] Heartbeat request failed: {}", e.getMessage());
                nextHeartbeatSeconds.set(MIN_HEARTBEAT_PERIOD_SEC);
                return false;
            }
        }
    }

    private void publishListings() {
        if (!isInstanceRegistered) {
            log.warn("Skipping publishListings: Instance not registered yet.");
            return;
        }

        try {
            ensureFreshHeartbeat();
            tianggeRestClient.put()
                    .uri("/listings")
                    .body(LISTINGS)
                    .retrieve()
                    .toBodilessEntity();

            log.info("--> [Tiangge] Published {} listings successfully.", LISTINGS.size());
        } catch (Exception e) {
            log.error("Failed to publish listings: {}", e.getMessage());
        }
    }

    private void publishInitialStock() {
        if (!isInstanceRegistered) {
            return;
        }

        try {
            List<Inventory> allItems = inventoryService.getAllItems();
            if (allItems == null || allItems.isEmpty()) {
                log.warn("[Tiangge] No inventory items found for initial stock publish.");
                return;
            }

            List<Map<String, Object>> stockPayload = new ArrayList<>();
            for (Inventory inv : allItems) {
                boolean listed = LISTINGS.stream()
                        .anyMatch(l -> l.get("sellerSku").equalsIgnoreCase(inv.getProductId()));
                if (listed) {
                    stockPayload.add(Map.of(
                            "sellerSku", inv.getProductId(),
                            "available", inv.getStock()
                    ));
                }
            }

            if (stockPayload.isEmpty()) {
                log.warn("[Tiangge] No listed products found in inventory; skipping initial stock publish.");
                return;
            }

            ensureFreshHeartbeat();
            tianggeRestClient.put()
                    .uri("/stock")
                    .body(stockPayload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("--> [Tiangge] Published initial stock for {} products: {}", stockPayload.size(), stockPayload);
        } catch (Exception e) {
            log.error("Failed to publish initial stock: {}", e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInventoryUpdated(InventoryUpdatedEvent event) {
        if (event == null || !isInstanceRegistered) {
            return;
        }
        String productId = event.getProductId();
        boolean listed = LISTINGS.stream()
                .anyMatch(l -> l.get("sellerSku").equalsIgnoreCase(productId));
        if (!listed) {
            return;
        }
        log.info("[Tiangge] InventoryUpdatedEvent -> pushing stock: {}={}",
                productId, event.getAvailableQuantity());
        pushStockUpdateOnce(productId, event.getAvailableQuantity());
    }

    boolean pushStockUpdateWithRetry(String sellerSku, int available, int maxAttempts) {
        ensureFreshHeartbeat();
        List<Map<String, Object>> payload = List.of(
                Map.of("sellerSku", sellerSku, "available", available)
        );

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                tianggeRestClient.put()
                        .uri("/stock")
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();

                log.info("--> [Tiangge] Stock sync: {} = {} (attempt {})", sellerSku, available, attempt);
                return true;
            } catch (Exception e) {
                log.warn("[Tiangge] Stock sync failed (attempt {}/{}) for {} = {} : {}",
                        attempt, maxAttempts, sellerSku, available, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    log.error("[Tiangge] Stock sync GIVING UP after {} attempts: {} = {}",
                            maxAttempts, sellerSku, available);
                }
            }
        }
        return false;
    }

    boolean pushStockUpdateOnce(String sellerSku, int available) {
        return pushStockUpdateWithRetry(sellerSku, available, 3);
    }

    boolean pushStockBatch(List<Map<String, Object>> stockPayload) {
        if (stockPayload == null || stockPayload.isEmpty()) {
            return true;
        }
        ensureFreshHeartbeat();
        try {
            tianggeRestClient.put()
                    .uri("/stock")
                    .body(stockPayload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("--> [Tiangge] Stock batch sync: {}", stockPayload);
            return true;
        } catch (Exception e) {
            log.warn("[Tiangge] Stock batch sync failed, falling back per-SKU: {}", e.getMessage());
            boolean ok = true;
            for (Map<String, Object> row : stockPayload) {
                Object sku = row.get("sellerSku");
                Object avail = row.get("available");
                if (sku != null && avail instanceof Number n) {
                    ok &= pushStockUpdateOnce(sku.toString(), n.intValue());
                }
            }
            return ok;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeartbeatResponse(String serverTime, Integer nextHeartbeatSeconds) {}
}
