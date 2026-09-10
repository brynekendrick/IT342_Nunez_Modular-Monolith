package edu.cit.nunez.shop;

import edu.cit.nunez.shop.dto.OrderRequest;
import edu.cit.nunez.shop.dto.OrderResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:5173")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.placeOrder(request);
    }
}