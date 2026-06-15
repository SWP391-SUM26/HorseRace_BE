package com.SWP391.horserace.tournaments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentResponse {
    private UUID tournamentId;
    private String tournamentCode;
    private String name;
    private String description;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private OffsetDateTime registrationOpenAt;
    private OffsetDateTime registrationCloseAt;
    private String location;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
