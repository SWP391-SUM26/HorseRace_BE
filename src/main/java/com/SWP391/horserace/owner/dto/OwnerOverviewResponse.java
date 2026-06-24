package com.SWP391.horserace.owner.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated dashboard payload for the FE-v2 Owner Overview (be-contracts-todo.md §4).
 * Read-only — assembled from horses, race entries and race results that belong to the caller.
 */
@Data
@Builder
public class OwnerOverviewResponse {

    private Kpis kpis;
    private List<OverviewHorse> horses;
    private List<UpcomingRace> upcomingRaces;

    @Data
    @Builder
    public static class Kpis {
        /** SUM of the owner's horses' lifetimeEarnings. */
        private BigDecimal lifetimeEarnings;
        /** Number of race results with a finish position over the owner's entries. */
        private long starts;
        /** Finish position == 1. */
        private long wins;
        /** Finish position <= 3. */
        private long top3;
        /** Count of the owner's non-deleted horses whose status is ACTIVE. */
        private long activeHorses;

        // ── Finance-derived KPIs — placeholders until the Finance module (§5) lands. ──
        private BigDecimal netProfit;
        private BigDecimal margin;
        private BigDecimal netProfitTrend;
        private BigDecimal pendingPayouts;
        private Integer pendingCount;
        private Integer pendingEtaDays;
    }

    @Data
    @Builder
    public static class OverviewHorse {
        private UUID horseId;
        /** Maps to horse.horseCode (the FE calls this "registrationCode"). */
        private String registrationCode;
        private String name;
        /** HorseStatus enum name, e.g. "ACTIVE". */
        private String status;
        /** horse.lifetimeEarnings. */
        private BigDecimal earnings;
    }

    @Data
    @Builder
    public static class UpcomingRace {
        private UUID raceId;
        private String name;
        private String venue;
        /** race.scheduledStartAt. */
        private OffsetDateTime date;
        /** The owner's horse entered in this race. */
        private String yourHorse;
        /** RaceEntryStatus enum name, e.g. "ENTERED". */
        private String entryStatus;
    }
}
