package edu.cit.nunez.channel;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(name = "tiangge_backorders")
@SuppressWarnings("unused")
class TianggeBackorder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tianggeOrderId;

    @Column(nullable = false)
    private Long shopOrderId;

    @Column(nullable = false, length = 50)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 30)
    private String resolutionStatus;

    private ZonedDateTime createdAt;
    private ZonedDateTime resolvedAt;

    TianggeBackorder() {}

    TianggeBackorder(String tianggeOrderId, Long shopOrderId, String productId, Integer quantity) {
        this.tianggeOrderId = tianggeOrderId;
        this.shopOrderId = shopOrderId;
        this.productId = productId;
        this.quantity = quantity;
        this.resolutionStatus = "PENDING";
        this.createdAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public String getTianggeOrderId() { return tianggeOrderId; }
    public Long getShopOrderId() { return shopOrderId; }
    public String getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public String getResolutionStatus() { return resolutionStatus; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getResolvedAt() { return resolvedAt; }

    public void setResolutionStatus(String status) {
        this.resolutionStatus = status;
        this.resolvedAt = ZonedDateTime.now();
    }
}

interface TianggeBackorderRepository extends JpaRepository<TianggeBackorder, Long> {
    List<TianggeBackorder> findByResolutionStatusAndProductId(String resolutionStatus, String productId);
    List<TianggeBackorder> findByResolutionStatus(String resolutionStatus);
    List<TianggeBackorder> findByTianggeOrderId(String tianggeOrderId);
}
