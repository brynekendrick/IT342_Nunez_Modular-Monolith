package edu.cit.nunez.inventory;

import edu.cit.nunez.shop.dto.OrderItemDto;
import java.util.List;
import java.util.Optional;

public interface InventoryService {
    Optional<Inventory> getItem(String productId);
    List<Inventory> getAllItems();
    boolean reserveAll(List<OrderItemDto> items);
    void restock(String productId, int quantity);
}