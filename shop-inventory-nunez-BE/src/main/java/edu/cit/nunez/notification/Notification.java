package edu.cit.nunez.notification;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    private String message;
    private ZonedDateTime createdAt;

    public Notification() {}

    public Notification(String message) {
        this.message = message;
        this.createdAt = ZonedDateTime.now();
    }

    public Long getNotificationId() { return notificationId; }
    public String getMessage() { return message; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
}