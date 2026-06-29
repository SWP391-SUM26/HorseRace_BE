package com.SWP391.horserace.notifications.service;

import com.SWP391.horserace.notifications.dto.NotificationResponse;

import java.util.List;
import java.util.UUID;

/** In-app notifications: emit on domain events + the recipient's inbox reads. */
public interface NotificationService {

    /** Best-effort: create an IN_APP notification for a recipient. Never throws to the caller. */
    void notifyUser(UUID recipientUserId, String title, String message);

    /** The recipient's recent notifications (newest first). */
    List<NotificationResponse> listForUser(UUID userId);

    /** Count of the recipient's unread notifications. */
    long unreadCount(UUID userId);

    /** Mark one of the recipient's notifications read. */
    void markRead(UUID userId, UUID notificationId);

    /** Mark all of the recipient's notifications read. */
    void markAllRead(UUID userId);
}
