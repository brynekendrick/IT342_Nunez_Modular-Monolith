    package edu.cit.nunez.inventory;

    import java.util.List;
    import java.util.Optional;

    public interface InventoryService {
        List<Inventory> getAllItems();
        Optional<Inventory> getItem(String productId);
        boolean reserveAll(List<edu.cit.nunez.shop.dto.OrderItemDto> items);
        void restock(String productId, int quantity);
    }