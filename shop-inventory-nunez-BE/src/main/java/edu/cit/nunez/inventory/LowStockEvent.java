package edu.cit.nunez.inventory;

public class LowStockEvent {
    private final String productId;

    public LowStockEvent(String productId) {
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}