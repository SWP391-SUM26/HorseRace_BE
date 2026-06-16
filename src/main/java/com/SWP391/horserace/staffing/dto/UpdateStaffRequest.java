package com.SWP391.horserace.staffing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code PUT /api/v1/staffing/staff/{id}} — partial update of a referee.
 * All fields are optional; only non-null values are applied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStaffRequest {

    private String fullName;
    private String phone;
    private String avatarUrl;

    /** ACTIVE | INACTIVE | SUSPENDED | BANNED */
    private String status;
}
