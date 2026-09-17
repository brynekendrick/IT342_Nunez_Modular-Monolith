package edu.cit.nunez.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface NotificationRepository extends JpaRepository<Notification, Long> {}