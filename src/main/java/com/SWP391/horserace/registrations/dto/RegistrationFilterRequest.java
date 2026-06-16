package com.SWP391.horserace.registrations.dto;

import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationFilterRequest {
    private String q;
    private RegistrationStatus status;
    private UUID tournamentId;
    private UUID horseId;
    private UUID ownerUserId;

    private String sortBy; // submittedAt, reviewedAt, registrationCode, status, createdAt
    private String sortDir; // asc / desc

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;
}
