package edu.cit.nunez.shop.dto;

public class OrderResponse {
    private String status;
    private String reason;
    private Long shopOrderId;

    public OrderResponse() {}

    public OrderResponse(String status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public OrderResponse(String status, String reason, Long shopOrderId) {
        this.status = status;
        this.reason = reason;
        this.shopOrderId = shopOrderId;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getShopOrderId() { return shopOrderId; }
    public void setShopOrderId(Long shopOrderId) { this.shopOrderId = shopOrderId; }
}
