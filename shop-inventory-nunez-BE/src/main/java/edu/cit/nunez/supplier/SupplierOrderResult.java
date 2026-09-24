package edu.cit.nunez.supplier;

public record SupplierOrderResult(
        Long supplierOrderId,
        String buyerRef,
        String poNumber,
        SupplierOrderStatus status,
        String message
) {}