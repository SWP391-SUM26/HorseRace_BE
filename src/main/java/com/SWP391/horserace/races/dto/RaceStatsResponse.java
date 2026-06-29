package com.SWP391.horserace.races.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * §D3 — race count-by-status KPIs for the Race Management dashboard.
 * Maps the BE RaceStatus enum to the FE's coarse buckets:
 *   scheduled = SCHEDULED, active = OPEN, cancelled = CANCELLED, total = all non-deleted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceStatsResponse {
    private long total;
    private long scheduled;
    private long active;
    private long cancelled;
}
