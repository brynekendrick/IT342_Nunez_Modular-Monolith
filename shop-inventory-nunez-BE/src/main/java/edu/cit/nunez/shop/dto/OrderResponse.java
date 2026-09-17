package edu.cit.nunez.shop.dto;

import edu.cit.nunez.inventory.Inventory;
import java.util.List;

public class OrderResponse {
    private String status;
    private String reason;
    private Inventory inventory;
    private List<Inventory> inventoryList;

    // Default No-Args Constructor
    public OrderResponse() {}

    // 2-Argument Constructor (Used in OrderService)
    public OrderResponse(String status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    // 3-Argument Constructor (Status, Reason, Single Inventory)
    public OrderResponse(String status, String reason, Inventory inventory) {
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
    }

    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public List<Inventory> getInventoryList() { return inventoryList; }
    public void setInventoryList(List<Inventory> inventoryList) { this.inventoryList = inventoryList; }
}