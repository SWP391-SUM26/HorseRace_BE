package com.SWP391.horserace.users.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query-parameter DTO for {@code GET /api/v1/users} (admin user listing). Bound via
 * {@code @ModelAttribute}, so every field is an optional request parameter.
 *
 * <ul>
 *   <li>{@code q}        — free-text, case-insensitive contains match on fullName / email / userCode</li>
 *   <li>{@code roleCode} — exact role code (e.g. ADMIN, JOCKEY)</li>
 *   <li>{@code status}   — exact user status (ACTIVE, INACTIVE, SUSPENDED, BANNED)</li>
 *   <li>{@code page}     — 0-indexed page number (default 0)</li>
 *   <li>{@code size}     — page size (default 20, capped at {@link #MAX_PAGE_SIZE})</li>
 *   <li>{@code sortBy}   — createdAt (default), fullName, email, lastLoginAt, status</li>
 *   <li>{@code sortDir}  — asc / desc (default: desc)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFilterRequest {

    /** Hard cap on the page size a client can request, regardless of the {@code size} param. */
    public static final int MAX_PAGE_SIZE = 100;

    private String q;
    private String roleCode;
    private String status;

    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;

    @Builder.Default
    private String sortBy = "createdAt";
    @Builder.Default
    private String sortDir = "desc";

    /** Page size clamped to (1 .. MAX_PAGE_SIZE]. */
    public int resolvedSize() {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Non-negative page index. */
    public int resolvedPage() {
        return Math.max(page, 0);
    }
}
