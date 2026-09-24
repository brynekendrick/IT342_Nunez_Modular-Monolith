package edu.cit.nunez.shop.dto;

public class OrderItemDto {
    private String productId;
    private Integer quantity;

    public OrderItemDto() {}

    public OrderItemDto(String productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}