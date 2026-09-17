package edu.cit.nunez.shop.dto;

import java.util.List;

public class MultiItemOrderRequest {
    private List<OrderItemDto> items;

    public MultiItemOrderRequest() {}

    public MultiItemOrderRequest(List<OrderItemDto> items) {
        this.items = items;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }
}