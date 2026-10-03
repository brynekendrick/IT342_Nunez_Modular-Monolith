package edu.cit.nunez.inventory;

public class InventoryUpdatedEvent {
    private final String productId;
    private final int availableQuantity;

    public InventoryUpdatedEvent(String productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }

    public String getProductId() { return productId; }
    public int getAvailableQuantity() { return availableQuantity; }
}