package com.SWP391.horserace.notifications.controller;

import com.SWP391.horserace.notifications.dto.NotificationResponse;
import com.SWP391.horserace.notifications.service.NotificationService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The signed-in user's in-app notification inbox. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** GET /api/v1/notifications — recent notifications for the caller (newest first). */
    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<List<NotificationResponse>>builder()
                .success(true).message("Fetched notifications")
                .data(notificationService.listForUser(userId)).build();
    }

    /** GET /api/v1/notifications/unread-count — number of unread notifications for the caller. */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<Long>builder()
                .success(true).message("Fetched unread count")
                .data(notificationService.unreadCount(userId)).build();
    }

    /** PATCH /api/v1/notifications/{id}/read — mark one notification read. */
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        notificationService.markRead(userId, id);
        return ApiResponse.<Void>builder().success(true).message("Marked read").build();
    }

    /** PATCH /api/v1/notifications/read-all — mark all of the caller's notifications read. */
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal UUID userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.<Void>builder().success(true).message("Marked all read").build();
    }
}
