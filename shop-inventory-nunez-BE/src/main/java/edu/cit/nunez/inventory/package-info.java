package edu.cit.nunez.inventory;

import edu.cit.nunez.shop.dto.OrderItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    // Constructor injection
    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public Optional<Inventory> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    @Override
    public List<Inventory> getAllItems() {
        return inventoryRepository.findAll();
    }

    @Override
    @Transactional
    public boolean reserveAll(List<OrderItemDto> items) {
        // 1. Verify stock availability for all items first
        for (OrderItemDto item : items) {
            Optional<Inventory> itemOpt = inventoryRepository.findById(item.getProductId());
            if (itemOpt.isEmpty() || itemOpt.get().getStock() < item.getQuantity()) {
                return false; // Insufficient stock or item missing
            }
        }

        // 2. Deduct stock across all items
        for (OrderItemDto item : items) {
            Inventory inventory = inventoryRepository.findById(item.getProductId()).get();
            inventory.setStock(inventory.getStock() - item.getQuantity());
            inventoryRepository.save(inventory);
        }

        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        Inventory item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);
    }
}