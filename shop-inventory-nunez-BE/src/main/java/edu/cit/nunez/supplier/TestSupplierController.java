package edu.cit.nunez.supplier;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestSupplierController {

    private final LegacySupplyAdapter adapter;

    TestSupplierController(LegacySupplyAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping("/api/test/reorder")
    public SupplierOrderResult triggerReorder(
            @RequestParam String productId,
            @RequestParam int units) {
        return adapter.requestReorder(productId, units);
    }
}