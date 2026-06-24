package com.SWP391.horserace.tournaments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nested eligibility sub-object for tournament request/response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityDto {
    private Boolean thoroughbredsOnly;
    private Integer minAgeYears;
    private Boolean requiresPreviousGroupWin;
}
