package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.RaceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceFilterRequest {
    private String q;
    private RaceStatus status;
    private UUID tournamentId;
    private String raceType;

    private String sortBy; // scheduledStartAt, name, createdAt
    private String sortDir; // asc / desc

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;
}
