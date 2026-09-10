package edu.cit.nunez.inventory;

import java.util.Optional;

public interface InventoryService {
    Optional<Inventory> getItem(String productId);
    boolean reserve(String productId, int quantity);
}