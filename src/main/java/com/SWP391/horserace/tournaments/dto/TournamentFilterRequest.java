package com.SWP391.horserace.tournaments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentFilterRequest {
    private String name;
    private String tournamentCode;
    private String location;
    private String status;

    private String sortBy; // name, startDate, endDate, registrationOpenAt
    private String sortDir; // asc / desc

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;
}
