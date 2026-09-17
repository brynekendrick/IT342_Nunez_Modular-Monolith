package edu.cit.nunez.shop;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    private String productId;
    private Integer quantity;

    public OrderItem() {}

    public OrderItem(String productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getItemId() { return itemId; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
}