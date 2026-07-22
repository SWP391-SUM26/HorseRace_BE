package com.SWP391.horserace.owner.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One settled (OFFICIAL) race of the owner's, with the prize money they were actually awarded
 * against the jockey hire fee they actually paid for that same race — the breakdown the Finances
 * page's flat ledger doesn't show on its own.
 */
@Data
@Builder
public class OwnerRaceEarningsRow {
    private UUID raceId;
    private String raceCode;
    private String raceName;
    private String tournamentName;
    private OffsetDateTime scheduledStartAt;
    /** Comma-joined when the owner ran more than one horse in this race. */
    private String horseName;
    /** Comma-joined; "—" when none of the owner's entries had a confirmed rider. */
    private String jockeyName;
    private BigDecimal prizeWon;
    private BigDecimal jockeyPaid;
    private BigDecimal net;
}
