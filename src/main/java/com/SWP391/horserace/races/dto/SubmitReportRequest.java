package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.violations.dto.CreateViolationRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body for POST /api/v1/races/{raceId}/report (CN3) — the referee's combined results+violations
 * report, unlocked by the emailed OTP. Reuses the existing result-row and violation shapes. On the
 * ADMIN override path the {@code otp} is ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitReportRequest {

    /** The 6-digit code emailed to the referee's verified inbox (ignored for admin override). */
    private String otp;

    /** Finish order to upsert (one row per entry). */
    @Valid
    private List<RecordResultsRequest.ResultRow> results;

    /** Violations to log alongside the results (each may cite an entry). */
    @Valid
    private List<CreateViolationRequest> violations;
}
