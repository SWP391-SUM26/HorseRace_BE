package com.SWP391.horserace.users.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Aggregate user counts for the admin dashboard ({@code GET /api/v1/users/stats}).
 * Counts cover non soft-deleted users only.
 */
@Data
@Builder
public class UserStatsResponse {
    /** Total non-deleted users. */
    private long totalUsers;
    /** Count keyed by role code (e.g. {@code {"ADMIN": 1, "JOCKEY": 4}}). */
    private Map<String, Long> byRole;
    /** Count keyed by user status (e.g. {@code {"ACTIVE": 9, "SUSPENDED": 1}}). */
    private Map<String, Long> byStatus;
}
