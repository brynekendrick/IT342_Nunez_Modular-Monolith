package edu.cit.nunez.channel;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;

@Entity
@Table(name = "tiangge_processed_events")
@SuppressWarnings("unused")
class TianggeProcessedEvent {

    @Id
    @Column(length = 100)
    private String eventId;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private ZonedDateTime processedAt;

    TianggeProcessedEvent() {}

    TianggeProcessedEvent(String eventId, String orderId, String eventType) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.processedAt = ZonedDateTime.now();
    }

    public String getEventId() { return eventId; }
    public String getOrderId() { return orderId; }
    public String getEventType() { return eventType; }
    public ZonedDateTime getProcessedAt() { return processedAt; }
}

interface TianggeProcessedEventRepository extends JpaRepository<TianggeProcessedEvent, String> {}
