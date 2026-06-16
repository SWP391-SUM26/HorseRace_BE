package com.SWP391.horserace.horses.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class HorseResponse {
    private UUID horseId;
    private String horseCode;
    private UUID ownerUserId;
    private String ownerName;
    private String name;
    private String microchipNo;
    private String gender;
    private String breed;
    private String color;
    private LocalDate dateOfBirth;
    private BigDecimal weight;
    private String originCountry;
    private String healthStatus;
    private String registrationStatus;
    private String status;
    private String imageUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
