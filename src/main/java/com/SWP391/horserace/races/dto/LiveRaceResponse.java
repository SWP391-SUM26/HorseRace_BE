package com.SWP391.horserace.races.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Body for GET /api/v1/races/{raceId}/live — poll snapshot of a running race (FE-v2 mục 4).
 *
 * <p>Polling fallback. There is no live telemetry source yet, so {@code currentSpeedKph} is always
 * null; {@code raceClockMs} is derived from {@code actual_start_at}. See the service for the
 * WebSocket TODO.
 */
@Data
@Builder
public class LiveRaceResponse {
    private UUID raceId;
    /** Milliseconds since actual_start_at (>= 0), or 0 if the race hasn't started. */
    private Long raceClockMs;
    private String videoFeedUrl;
    private BigDecimal windSpeedKph;
    private String windDirection;
    private List<RunnerRow> runningOrder;

    /** One runner's current standing in the live order. */
    @Data
    @Builder
    public static class RunnerRow {
        /** Finish position when results exist; null while the race is still running. */
        private Integer position;
        private Integer entryNo;
        private String horseName;
        private String jockeyName;
        /** No live telemetry source yet — always null. */
        private BigDecimal currentSpeedKph;
    }
}
