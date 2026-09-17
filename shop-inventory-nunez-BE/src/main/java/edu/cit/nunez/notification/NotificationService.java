package edu.cit.nunez.notification;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void logNotification(String message) {
        notificationRepository.save(new Notification(message));
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }
}