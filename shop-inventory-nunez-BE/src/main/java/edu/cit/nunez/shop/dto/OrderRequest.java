package edu.cit.nunez.shop.dto;

import java.util.List;

public class OrderRequest {
    private List<OrderItemDto> items;

    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }
}