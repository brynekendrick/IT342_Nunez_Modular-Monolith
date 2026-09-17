package edu.cit.nunez.shop.dto;

import edu.cit.nunez.inventory.OrderItemDto;
import java.util.List;

public class MultipleItemOrderRequest {
    private List<OrderItemDto> items;

    public MultipleItemOrderRequest() {}

    public MultipleItemOrderRequest(List<OrderItemDto> items) {
        this.items = items;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }
}