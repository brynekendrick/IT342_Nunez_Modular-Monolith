package edu.cit.nunez.inventory;

import edu.cit.nunez.supplier.SupplierGateway;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LowStockEventListener {

    private final SupplierGateway supplierGateway;

    public LowStockEventListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void handleLowStock(LowStockEvent event) {
        supplierGateway.requestReorder(event.getProductId(), 10);
    }
}