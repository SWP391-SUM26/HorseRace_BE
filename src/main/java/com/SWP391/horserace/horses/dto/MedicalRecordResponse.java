package com.SWP391.horserace.horses.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class MedicalRecordResponse {
    private UUID recordId;
    private String recordType;
    private String title;
    private String note;
    private LocalDate recordDate;
    private OffsetDateTime createdAt;
}
