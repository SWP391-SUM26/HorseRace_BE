package com.SWP391.horserace.tournaments.dto;

import com.SWP391.horserace.tournaments.entity.CircuitTier;
import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRequest {
    private String tournamentCode;
    private String name;
    private String description;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private OffsetDateTime registrationOpenAt;
    private OffsetDateTime registrationCloseAt;
    private String location;
    private TournamentStatus status;

    // §C1 — enrichment fields
    private CircuitTier circuitTier;
    private BigDecimal totalPurse;
    private Integer entryCap;

    // §C2 — nested eligibility sub-object
    private EligibilityDto eligibility;

    // §C3 — optional structured venues to link to this tournament
    private List<UUID> venueIds;
}
