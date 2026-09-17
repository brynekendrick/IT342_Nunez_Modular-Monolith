package edu.cit.nunez.shop;

import edu.cit.nunez.inventory.InventoryService;
import edu.cit.nunez.shop.dto.OrderItemDto;
import edu.cit.nunez.shop.dto.OrderRequest;
import edu.cit.nunez.shop.dto.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        List<OrderItemDto> items = request.getItems();

        if (items == null || items.isEmpty()) {
            return new OrderResponse("REJECTED", "Order request contains no items");
        }

        // 1. Reserve stock across all items in the list
        boolean reserved = inventoryService.reserveAll(items);

        if (reserved) {
            // Save each item record into the orders table
            for (OrderItemDto item : items) {
                Order order = new Order(item.getProductId(), item.getQuantity(), "CONFIRMED", "Order processed successfully");
                orderRepository.save(order);
            }
            return new OrderResponse("CONFIRMED", "Order processed successfully");
        } else {
            for (OrderItemDto item : items) {
                Order order = new Order(item.getProductId(), item.getQuantity(), "REJECTED", "Insufficient stock");
                orderRepository.save(order);
            }
            return new OrderResponse("REJECTED", "Insufficient stock");
        }
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            return new OrderResponse("REJECTED", "Order ID not found: " + orderId);
        }

        Order order = orderOpt.get();

        if ("CANCELLED".equals(order.getStatus())) {
            return new OrderResponse("REJECTED", "Order #" + orderId + " is already cancelled");
        }

        // 1. Restock the item back to the inventory module
        inventoryService.restock(order.getProductId(), order.getQuantity());

        // 2. Update status in orders table
        order.setStatus("CANCELLED");
        order.setReason("Order cancelled by user - stock returned");
        orderRepository.save(order);

        return new OrderResponse("CONFIRMED", "Order #" + orderId + " cancelled and restocked.");
    }
}