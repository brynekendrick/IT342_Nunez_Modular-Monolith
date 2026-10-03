package edu.cit.nunez.inventory;

import edu.cit.nunez.InstanceLifecycle;
import edu.cit.nunez.supplier.SupplierGateway;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LowStockEventListener {

    private final SupplierGateway supplierGateway;
    private final InstanceLifecycle instanceLifecycle;

    public LowStockEventListener(SupplierGateway supplierGateway, InstanceLifecycle instanceLifecycle) {
        this.supplierGateway = supplierGateway;
        this.instanceLifecycle = instanceLifecycle;
    }

    @EventListener
    public void handleLowStock(LowStockEvent event) {
        if (instanceLifecycle == null || !instanceLifecycle.isRegistered()) {
            return;
        }
        supplierGateway.requestReorder(event.getProductId(), 10);
    }
}