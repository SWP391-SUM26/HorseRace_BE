package com.SWP391.horserace.tournaments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embeddable eligibility criteria for a tournament, stored as three nullable columns on
 * the {@code tournament} table: thoroughbreds_only, min_age_years, requires_previous_group_win.
 * Exposed/accepted nested as {@code eligibility} in the tournament DTOs.
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityCriteria {

    @Column(name = "thoroughbreds_only")
    private Boolean thoroughbredsOnly;

    @Column(name = "min_age_years")
    private Integer minAgeYears;

    @Column(name = "requires_previous_group_win")
    private Boolean requiresPreviousGroupWin;
}
