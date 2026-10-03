package edu.cit.nunez.shop;

import edu.cit.nunez.inventory.InventoryService;
import edu.cit.nunez.shop.dto.OrderItemDto;
import edu.cit.nunez.shop.dto.OrderRequest;
import edu.cit.nunez.shop.dto.OrderResponse;
import edu.cit.nunez.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final SupplierGateway supplierGateway;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository, SupplierGateway supplierGateway) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.supplierGateway = supplierGateway;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        return placeOrderWithReference(request, null);
    }

    @Transactional
    public OrderResponse placeOrderWithReference(OrderRequest request, String externalReference) {
        List<OrderItemDto> items = request.getItems();

        if (items == null || items.isEmpty()) {
            return new OrderResponse("REJECTED", "Order request contains no items");
        }

        if (externalReference != null && !externalReference.isBlank()) {
            List<Order> existing = orderRepository.findAllByReason(externalReference);
            if (!existing.isEmpty()) {
                Order o = existing.get(0);
                boolean anyConfirmed = existing.stream().anyMatch(ord -> "CONFIRMED".equals(ord.getStatus()));
                return new OrderResponse(
                        anyConfirmed ? "CONFIRMED" : o.getStatus(),
                        "Duplicate of external reference: " + externalReference,
                        o.getOrderId()
                );
            }
        }

        boolean reserved;
        try {
            reserved = inventoryService.reserveAll(items);
        } catch (Exception e) {
            log.warn("Reservation aborted for ref {}: {}", externalReference, e.getMessage());
            reserved = false;
        }
        Long firstOrderId = null;

        for (OrderItemDto item : items) {
            Order order = new Order(
                    item.getProductId(),
                    item.getQuantity(),
                    reserved ? "CONFIRMED" : "REJECTED",
                    externalReference != null ? externalReference : "Placed via UI"
            );
            order = orderRepository.save(order);
            if (firstOrderId == null) {
                firstOrderId = order.getOrderId();
            }
        }

        return new OrderResponse(
                reserved ? "CONFIRMED" : "REJECTED",
                reserved ? "Order processed successfully" : "Insufficient stock",
                firstOrderId
        );
    }

    @Transactional
    public boolean reserveInternalOrder(List<OrderItemDto> items, String externalReference) {
        if (externalReference != null && !externalReference.isBlank()) {
            List<Order> existing = orderRepository.findAllByReason(externalReference);
            if (!existing.isEmpty()) {
                return existing.stream().anyMatch(ord -> "CONFIRMED".equals(ord.getStatus()));
            }
        }

        boolean reserved = inventoryService.reserveAll(items);

        for (OrderItemDto item : items) {
            Order order = new Order(
                    item.getProductId(),
                    item.getQuantity(),
                    reserved ? "CONFIRMED" : "REJECTED",
                    externalReference != null ? externalReference : "Placed via UI"
            );
            orderRepository.save(order);
        }

        return reserved;
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

        if ("CONFIRMED".equals(order.getStatus())) {
            inventoryService.restock(order.getProductId(), order.getQuantity());
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);

        return new OrderResponse("CONFIRMED", "Order #" + orderId + " cancelled and restocked.", orderId);
    }

    /**
     * Cancels all shop lines for an external (Tiangge) order id.
     * Keeps {@code reason} intact so idempotency and restock SKU lookup still work.
     * Only CONFIRMED lines are restocked.
     *
     * @return product ids that were restocked (for channel stock sync)
     */
    @Transactional
    public List<String> cancelByExternalReference(String externalReference) {
        Set<String> restockedProductIds = new LinkedHashSet<>();
        List<Order> matches = orderRepository.findAllByReason(externalReference);

        for (Order order : matches) {
            if ("CANCELLED".equals(order.getStatus())) {
                continue;
            }
            if ("CONFIRMED".equals(order.getStatus())) {
                inventoryService.restock(order.getProductId(), order.getQuantity());
                restockedProductIds.add(order.getProductId());
            }
            order.setStatus("CANCELLED");
            // Do NOT overwrite reason — it is the external reference used for idempotency.
            orderRepository.save(order);
        }

        return new ArrayList<>(restockedProductIds);
    }

    public Optional<Long> findFirstShopOrderIdByExternalReference(String externalReference) {
        List<Order> matches = orderRepository.findAllByReason(externalReference);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matches.get(0).getOrderId());
    }

    public List<String> findProductIdsByExternalReference(String externalReference) {
        return orderRepository.findAllByReason(externalReference).stream()
                .map(Order::getProductId)
                .distinct()
                .toList();
    }

    public boolean hasActivePurchaseOrder(String productId) {
        if (productId == null || productId.isBlank() || supplierGateway == null) {
            return false;
        }
        try {
            return supplierGateway.hasActivePurchaseOrder(productId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasOpenPurchaseOrderForAllProducts(List<String> productIds) {
        if (productIds == null || productIds.isEmpty() || supplierGateway == null) {
            return false;
        }
        try {
            for (String productId : productIds) {
                if (!supplierGateway.hasActivePurchaseOrder(productId)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasOpenPurchaseOrderForAnyProduct(List<String> productIds) {
        if (productIds == null || productIds.isEmpty() || supplierGateway == null) {
            return false;
        }
        try {
            for (String productId : productIds) {
                if (supplierGateway.hasActivePurchaseOrder(productId)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensures each product has an open purchase order.
     * When {@code submitNow} is true, attempts remote LegacySupply submit (for BACKORDERED).
     */
    public void ensurePurchaseOrdersForProducts(List<String> productIds, int unitsNeeded, boolean submitNow) {
        if (productIds == null || productIds.isEmpty() || supplierGateway == null) {
            return;
        }
        for (String productId : productIds) {
            try {
                supplierGateway.requestReorder(productId, unitsNeeded, submitNow);
            } catch (Exception ignored) {
            }
        }
    }

    public void ensurePurchaseOrdersForProducts(List<String> productIds, int unitsNeeded) {
        ensurePurchaseOrdersForProducts(productIds, unitsNeeded, true);
    }
}
