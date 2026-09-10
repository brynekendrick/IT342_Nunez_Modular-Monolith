package edu.cit.nunez.shop;

import edu.cit.nunez.inventory.Inventory;
import edu.cit.nunez.inventory.InventoryService;
import edu.cit.nunez.shop.dto.OrderRequest;
import edu.cit.nunez.shop.dto.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Optional<Inventory> itemOpt = inventoryService.getItem(request.getProductId());

        if (itemOpt.isEmpty()) {
            Order order = new Order(request.getProductId(), request.getQuantity(), "REJECTED", "Product does not exist");
            orderRepository.save(order);
            return new OrderResponse("REJECTED", "Product does not exist", null);
        }

        boolean reserved = inventoryService.reserve(request.getProductId(), request.getQuantity());
        Inventory updatedInventory = inventoryService.getItem(request.getProductId()).orElse(null);

        if (reserved) {
            Order order = new Order(request.getProductId(), request.getQuantity(), "CONFIRMED", "Order processed successfully");
            orderRepository.save(order);
            return new OrderResponse("CONFIRMED", "Order processed successfully", updatedInventory);
        } else {
            Order order = new Order(request.getProductId(), request.getQuantity(), "REJECTED", "Insufficient stock");
            orderRepository.save(order);
            return new OrderResponse("REJECTED", "Insufficient stock", updatedInventory);
        }
    }
}