package com.SWP391.horserace.referee.dto;

import com.SWP391.horserace.races.entity.RaceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate payload for {@code GET /api/v1/referee/dashboard} (FE-v2 §1). Bundles the next race,
 * derived alerts, the pre-race inspection summary and the duty roster so screen 1 loads in one call.
 */
@Data
@Builder
public class RefereeDashboardResponse {

    /** The race the referee should focus on next; {@code null} when there is no upcoming race. */
    private NextRace nextRace;

    /** Actionable items derived from pending violations + vet-check inspections of the next race. */
    private List<Alert> alerts;

    /** Counts of inspection statuses across the next race's entries. */
    private InspectionSummary inspectionSummary;

    /** The officiating panel assigned to the next race. */
    private List<DutyRosterItem> dutyRoster;

    @Data
    @Builder
    public static class NextRace {
        private UUID raceId;
        private String raceCode;
        private String name;
        private OffsetDateTime scheduledStartAt;
        /** max(0, scheduledStartAt - now) in seconds; {@code null} when there is no scheduled start. */
        private Long postTimeCountdownSeconds;
        private RaceStatus status;
    }

    @Data
    @Builder
    public static class Alert {
        /** e.g. {@code WHIP_USAGE_PENDING} or {@code VET_CHECK_REQUESTED}. */
        private String type;
        /** LOW | MEDIUM | HIGH | CRITICAL (nullable for violation alerts without severity). */
        private String severity;
        private UUID raceId;
        private UUID entryId;
        /** The id of the source row (violationId for violation alerts). */
        private UUID refId;
        private String label;
    }

    @Data
    @Builder
    public static class InspectionSummary {
        private long cleared;
        private long pending;
        private long vetCheck;
        private long total;
    }

    @Data
    @Builder
    public static class DutyRosterItem {
        private UUID refereeUserId;
        private String refereeName;
        private String panelRole;
        /** No station column in {@code referee_assignment} — always {@code null} (FE-v2 §1). */
        private String station;
    }
}
