package edu.cit.nunez.shop;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    private String productId;
    private Integer quantity;
    private String status;
    private String reason;
    private ZonedDateTime createdAt;

    public Order() {}

    public Order(String productId, Integer quantity, String status, String reason) {
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.reason = reason;
        this.createdAt = ZonedDateTime.now();
    }

    public Long getOrderId() { return orderId; }
    public String getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
}