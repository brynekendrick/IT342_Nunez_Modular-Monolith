package edu.cit.nunez.inventory;

import edu.cit.nunez.supplier.SupplierGateway;
import edu.cit.nunez.supplier.SupplierOrderResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "http://localhost:5173")
public class InventoryController {

    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;

    public InventoryController(InventoryService inventoryService, SupplierGateway supplierGateway) {
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
    }

    @GetMapping
    public List<Inventory> getAllInventory() {
        return inventoryService.getAllItems();
    }

    @GetMapping("/{productId}")
    public Inventory getInventoryItem(@PathVariable String productId) {
        return inventoryService.getItem(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
    }

    @PostMapping("/reorder/{productId}")
    public SupplierOrderResult reorderProduct(@PathVariable String productId) {
        return supplierGateway.requestReorder(productId, 20);
    }
}