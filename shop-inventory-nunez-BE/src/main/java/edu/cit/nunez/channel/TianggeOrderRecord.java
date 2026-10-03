package edu.cit.nunez.channel;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;

@Entity
@Table(name = "tiangge_orders", uniqueConstraints = @UniqueConstraint(columnNames = "tiangge_order_id"))
class TianggeOrderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tiangge_order_id", nullable = false, length = 50, unique = true)
    private String tianggeOrderId;

    @Column(name = "shop_order_id")
    private Long shopOrderId;

    @Column(nullable = false, length = 30)
    private String decision;

    @Column(nullable = false)
    private ZonedDateTime createdAt;

    TianggeOrderRecord() {}

    TianggeOrderRecord(String tianggeOrderId, Long shopOrderId, String decision) {
        this.tianggeOrderId = tianggeOrderId;
        this.shopOrderId = shopOrderId;
        this.decision = decision;
        this.createdAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public String getTianggeOrderId() { return tianggeOrderId; }
    public Long getShopOrderId() { return shopOrderId; }
    public String getDecision() { return decision; }
    public ZonedDateTime getCreatedAt() { return createdAt; }

    public void setDecision(String decision) { this.decision = decision; }
    public void setShopOrderId(Long shopOrderId) { this.shopOrderId = shopOrderId; }
}

interface TianggeOrderRecordRepository extends JpaRepository<TianggeOrderRecord, Long> {
    java.util.Optional<TianggeOrderRecord> findByTianggeOrderId(String tianggeOrderId);
    boolean existsByTianggeOrderId(String tianggeOrderId);
}
