package com.SWP391.horserace.races.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Reference data for the race create/edit form: the distinct values already present in the DB for
 * the free-text {@code race_type}, {@code track_condition} and {@code weather_condition} columns.
 * Lets the form offer real, consistent options instead of free typing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceFieldOptionsResponse {
    private List<String> raceTypes;
    private List<String> trackConditions;
    private List<String> weatherConditions;
}
