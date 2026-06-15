package com.SWP391.horserace.staffing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query-parameter DTO for {@code GET /api/v1/staffing/staff}.
 * Every field is optional — omitted fields are not included in the filter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffFilterRequest {

    /** Search by full name, email, or user code (case-insensitive LIKE). */
    private String search;

    /** Filter by user status: ACTIVE, INACTIVE, SUSPENDED, BANNED. */
    private String status;

    // -- pagination --
    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    // -- sorting --
    /** Sort field: fullName (default), email, createdAt. */
    @Builder.Default
    private String sortBy = "fullName";

    /** Sort direction: asc (default) / desc. */
    @Builder.Default
    private String sortDir = "asc";
}
