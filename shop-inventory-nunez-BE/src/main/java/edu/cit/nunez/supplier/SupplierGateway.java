package edu.cit.nunez.supplier;

public interface SupplierGateway {
    SupplierOrderResult requestReorder(String productId, int unitsNeeded);
}