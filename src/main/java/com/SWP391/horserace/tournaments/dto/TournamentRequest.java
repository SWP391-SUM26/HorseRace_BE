package com.SWP391.horserace.tournaments.dto;

import com.SWP391.horserace.tournaments.entity.CircuitTier;
import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "Tournament name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;
    private String description;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private OffsetDateTime registrationOpenAt;
    private OffsetDateTime registrationCloseAt;
    @Size(max = 255, message = "Location must not exceed 255 characters")
    private String location;
    private TournamentStatus status;

    // §C1 — enrichment fields
    private CircuitTier circuitTier;

    @PositiveOrZero(message = "Total purse cannot be negative")
    private BigDecimal totalPurse;

    @Positive(message = "Entry cap must be positive")
    private Integer entryCap;

    // §C2 — nested eligibility sub-object
    @Valid
    private EligibilityDto eligibility;

    // §C3 — optional structured venues to link to this tournament
    private List<UUID> venueIds;
}
