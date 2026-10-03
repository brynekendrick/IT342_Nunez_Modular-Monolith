package edu.cit.nunez.inventory;

import edu.cit.nunez.shop.dto.OrderItemDto;
import edu.cit.nunez.supplier.OrderDeliveredEvent;
import edu.cit.nunez.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;

@Service
class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final SupplierGateway supplierGateway;
    private final ApplicationEventPublisher eventPublisher;

    InventoryServiceImpl(InventoryRepository inventoryRepository,
                         SupplierGateway supplierGateway,
                         ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.supplierGateway = supplierGateway;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<Inventory> getAllItems() {
        return inventoryRepository.findAll();
    }

    @Override
    public Optional<Inventory> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    @Override
    @Transactional
    public boolean reserveAll(List<OrderItemDto> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        // Aggregate quantities per SKU to handle duplicate items within the same order
        Map<String, Integer> neededBySku = new LinkedHashMap<>();
        for (OrderItemDto item : items) {
            if (item.getProductId() != null && item.getQuantity() > 0) {
                neededBySku.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
        }

        // Sort keys to prevent deadlocks across concurrent transactions
        List<String> sortedSkus = new ArrayList<>(neededBySku.keySet());
        Collections.sort(sortedSkus);

        Map<String, Inventory> lockedMap = new HashMap<>();
        for (String sku : sortedSkus) {
            Inventory inv = inventoryRepository.findByIdForUpdate(sku).orElse(null);
            int needed = neededBySku.get(sku);
            if (inv == null || inv.getStock() < needed) {
                log.warn("Reservation rejected: SKU {} missing or insufficient stock (needed: {}, available: {})",
                        sku, needed, inv != null ? inv.getStock() : 0);
                return false;
            }
            lockedMap.put(sku, inv);
        }

        for (Map.Entry<String, Integer> entry : neededBySku.entrySet()) {
            String sku = entry.getKey();
            int qty = entry.getValue();
            Inventory inv = lockedMap.get(sku);
            int newStock = inv.getStock() - qty;
            if (newStock < 0) {
                log.error("OVERSELL GUARD TRIGGERED: SKU {} would go negative ({} - {}).", sku, inv.getStock(), qty);
                throw new IllegalStateException("Oversell prevented for " + sku);
            }
            inv.setStock(newStock);
            inventoryRepository.save(inv);
            inventoryRepository.flush();

            log.info("Reserved {} units for SKU {}. Remaining stock: {}", qty, sku, newStock);

            if (newStock < 5) {
                log.info("Stock for SKU {} dropped below threshold ({}), queueing local reorder...", sku, newStock);
                try {
                    supplierGateway.requestReorder(sku, 20, false);
                } catch (Exception e) {
                    log.error("Failed to queue reorder for SKU {}: {}", sku, e.getMessage());
                }
            }
        }

        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        inventoryRepository.findByIdForUpdate(productId).ifPresentOrElse(inventory -> {
            int previousStock = inventory.getStock();
            int newStock = previousStock + quantity;
            inventory.setStock(newStock);
            inventoryRepository.save(inventory);
            inventoryRepository.flush();
            log.info("Restocked product {}: {} -> {} (+{})", productId, previousStock, newStock, quantity);
        }, () -> log.error("Cannot restock: Product {} not found in inventory", productId));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Order(1)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSupplierOrderDelivered(OrderDeliveredEvent event) {
        log.info("Received OrderDeliveredEvent for product {}. Restocking {} units...", event.productId(), event.unitsToRestock());
        String productId = event.productId();
        int quantity = event.unitsToRestock();
        inventoryRepository.findByIdForUpdate(productId).ifPresentOrElse(inventory -> {
            int previousStock = inventory.getStock();
            int newStock = previousStock + quantity;
            inventory.setStock(newStock);
            inventoryRepository.save(inventory);
            inventoryRepository.flush();
            log.info("Restocked product {}: {} -> {} (+{})", productId, previousStock, newStock, quantity);
        }, () -> log.error("Cannot restock: Product {} not found in inventory", productId));
    }
}
