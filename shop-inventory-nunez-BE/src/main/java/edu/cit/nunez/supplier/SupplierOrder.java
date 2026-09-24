package edu.cit.nunez.supplier;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productId;
    private String buyerRef;
    private String requestId;
    private String poNumber;
    private int cases;
    private int units;

    @Enumerated(EnumType.STRING)
    private SupplierOrderStatus status;

    private ZonedDateTime createdAt = ZonedDateTime.now();
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    public SupplierOrder() {}

    public SupplierOrder(String productId, String buyerRef, String requestId, int cases, int units, SupplierOrderStatus status) {
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getProductId() { return productId; }
    public String getBuyerRef() { return buyerRef; }
    public void setBuyerRef(String buyerRef) { this.buyerRef = buyerRef; }
    public String getRequestId() { return requestId; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public int getCases() { return cases; }
    public int getUnits() { return units; }
    public SupplierOrderStatus getStatus() { return status; }
    public void setStatus(SupplierOrderStatus status) {
        this.status = status;
        this.updatedAt = ZonedDateTime.now();
    }
}