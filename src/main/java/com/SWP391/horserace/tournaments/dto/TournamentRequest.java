package com.SWP391.horserace.tournaments.dto;

import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

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
}
