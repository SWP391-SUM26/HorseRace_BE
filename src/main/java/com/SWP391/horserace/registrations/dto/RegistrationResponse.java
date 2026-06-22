package com.SWP391.horserace.registrations.dto;

import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class RegistrationResponse {
    private UUID registrationId;
    private String registrationCode;
    private RegistrationStatus status;

    private UUID ownerUserId;
    private String ownerName;

    private UUID tournamentId;
    private String tournamentName;

    private UUID horseId;
    private String horseName;
    private String horseCode;

    private UUID raceId;
    private String raceName;

    private OffsetDateTime submittedAt;
    private OffsetDateTime reviewedAt;
    private UUID approvedByUserId;
    private String rejectionReason;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
