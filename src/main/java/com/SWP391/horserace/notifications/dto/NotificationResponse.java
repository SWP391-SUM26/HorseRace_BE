package com.SWP391.horserace.notifications.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One in-app notification for the recipient's inbox. */
@Data
@Builder
public class NotificationResponse {
    private UUID notificationId;
    private String title;
    private String message;
    private Boolean isRead;
    private OffsetDateTime createdAt;
}
