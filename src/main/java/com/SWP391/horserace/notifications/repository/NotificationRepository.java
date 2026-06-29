package com.SWP391.horserace.notifications.repository;

import com.SWP391.horserace.notifications.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** The recipient's most recent notifications (newest first). */
    List<Notification> findTop50ByRecipient_UserIdOrderByCreatedAtDesc(UUID recipientUserId);

    /** Count of unread notifications for the recipient. */
    long countByRecipient_UserIdAndIsReadFalse(UUID recipientUserId);

    /** One notification scoped to its recipient (so users can only touch their own). */
    Optional<Notification> findByNotificationIdAndRecipient_UserId(UUID notificationId, UUID recipientUserId);

    /** All unread notifications for the recipient (used by mark-all-read). */
    List<Notification> findByRecipient_UserIdAndIsReadFalse(UUID recipientUserId);
}
