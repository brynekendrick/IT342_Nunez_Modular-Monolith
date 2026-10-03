package edu.cit.nunez.supplier;

public interface SupplierGateway {

    SupplierOrderResult requestReorder(String productId, int unitsNeeded);

    /**
     * Ensure an open PO exists. When {@code submitNow} is true, attempt remote LegacySupply
     * submit immediately (needed before BACKORDERED decisions). When false, only persist a
     * local PENDING row so order decisions are not blocked on HTTP.
     */
    SupplierOrderResult requestReorder(String productId, int unitsNeeded, boolean submitNow);

    boolean hasActivePurchaseOrder(String productId);
}
