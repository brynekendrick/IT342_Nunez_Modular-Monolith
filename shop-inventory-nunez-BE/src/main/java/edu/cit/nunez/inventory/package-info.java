package edu.cit.nunez.inventory;

import edu.cit.nunez.shop.dto.OrderItemDto;
import edu.cit.nunez.supplier.OrderDeliveredEvent;
import edu.cit.nunez.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final SupplierGateway supplierGateway;

    InventoryServiceImpl(InventoryRepository inventoryRepository, SupplierGateway supplierGateway) {
        this.inventoryRepository = inventoryRepository;
        this.supplierGateway = supplierGateway;
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
        // Step 1: Pre-check stock for all requested items to prevent partial updates
        for (OrderItemDto item : items) {
            Inventory inventory = inventoryRepository.findById(item.getProductId()).orElse(null);
            if (inventory == null || inventory.getStock() < item.getQuantity()) {
                log.warn("Reservation rejected: Product {} missing or insufficient stock (Requested: {}, Available: {})",
                        item.getProductId(), item.getQuantity(), inventory != null ? inventory.getStock() : 0);
                return false;
            }
        }

        // Step 2: Deduct stock and check low-stock reorder thresholds
        for (OrderItemDto item : items) {
            Inventory inventory = inventoryRepository.findById(item.getProductId()).get();
            int newStock = inventory.getStock() - item.getQuantity();
            inventory.setStock(newStock);
            inventoryRepository.save(inventory);

            log.info("Reserved {} units for product {}. Remaining stock: {}", item.getQuantity(), item.getProductId(), newStock);

            // Trigger Supplier Adapter reorder if stock drops below 5 units
            if (newStock < 5) {
                log.info("Stock for product {} dropped below threshold ({}), requesting reorder from LegacySupply...", item.getProductId(), newStock);
                try {
                    supplierGateway.requestReorder(item.getProductId(), 20);
                } catch (Exception e) {
                    log.error("Failed to place reorder for product {} via SupplierGateway: {}", item.getProductId(), e.getMessage());
                }
            }
        }

        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        inventoryRepository.findById(productId).ifPresentOrElse(inventory -> {
            int previousStock = inventory.getStock();
            inventory.setStock(previousStock + quantity);
            inventoryRepository.save(inventory);
            log.info("Restocked product {}: {} -> {} (+{})", productId, previousStock, inventory.getStock(), quantity);
        }, () -> log.error("Cannot restock: Product {} not found in inventory", productId));
    }

    @EventListener
    public void handleSupplierOrderDelivered(OrderDeliveredEvent event) {
        log.info("Received OrderDeliveredEvent for product {}. Restocking {} units...", event.productId(), event.unitsToRestock());
        restock(event.productId(), event.unitsToRestock());
    }
}