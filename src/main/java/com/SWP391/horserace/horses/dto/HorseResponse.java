package com.SWP391.horserace.horses.dto;

import com.SWP391.horserace.horses.entity.HorseGender;
import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import com.SWP391.horserace.horses.entity.HorseStatus;
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
    private HorseGender gender;
    private String breed;
    private String color;
    private LocalDate dateOfBirth;
    private BigDecimal weight;
    private String originCountry;
    private HorseHealthStatus healthStatus;
    private String registrationStatus;
    private HorseStatus status;
    private String imageUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
