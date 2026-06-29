package com.SWP391.horserace.notifications.service.impl;

import com.SWP391.horserace.notifications.dto.NotificationResponse;
import com.SWP391.horserace.notifications.entity.DeliveryStatus;
import com.SWP391.horserace.notifications.entity.Notification;
import com.SWP391.horserace.notifications.entity.NotificationChannel;
import com.SWP391.horserace.notifications.repository.NotificationRepository;
import com.SWP391.horserace.notifications.service.NotificationService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Best-effort emit in its OWN transaction (REQUIRES_NEW) so a notification failure never
     * rolls back the domain action (certifying results, logging a violation, …) that triggered it.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUser(UUID recipientUserId, String title, String message) {
        try {
            if (recipientUserId == null) return;
            User recipient = userRepository.findById(recipientUserId).orElse(null);
            if (recipient == null) return;
            notificationRepository.save(Notification.builder()
                    .recipient(recipient)
                    .title(title)
                    .message(message)
                    .channel(NotificationChannel.IN_APP)
                    .deliveryStatus(DeliveryStatus.SENT)
                    .sentAt(OffsetDateTime.now())
                    .isRead(false)
                    .build());
        } catch (Exception ignored) {
            // best-effort: swallow so the caller's transaction is unaffected
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(UUID userId) {
        return notificationRepository.findTop50ByRecipient_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> NotificationResponse.builder()
                        .notificationId(n.getNotificationId())
                        .title(n.getTitle())
                        .message(n.getMessage())
                        .isRead(Boolean.TRUE.equals(n.getIsRead()))
                        .createdAt(n.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipient_UserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        notificationRepository.findByNotificationIdAndRecipient_UserId(notificationId, userId)
                .ifPresent(n -> {
                    if (!Boolean.TRUE.equals(n.getIsRead())) {
                        n.setIsRead(true);
                        n.setReadAt(OffsetDateTime.now());
                        notificationRepository.save(n);
                    }
                });
    }

    @Override
    @Transactional
    public void markAllRead(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        var unread = notificationRepository.findByRecipient_UserIdAndIsReadFalse(userId);
        for (Notification n : unread) {
            n.setIsRead(true);
            n.setReadAt(now);
        }
        notificationRepository.saveAll(unread);
    }
}
