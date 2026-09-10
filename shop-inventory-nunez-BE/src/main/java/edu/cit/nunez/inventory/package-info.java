package edu.cit.nunez.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public Optional<Inventory> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        Inventory item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        if (item.getStock() < quantity) {
            return false;
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);
        return true;
    }
}