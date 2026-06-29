package com.SWP391.horserace.horses.dto;

import com.SWP391.horserace.horses.entity.MedicalRecordType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Create/update body for a horse medical record. */
public record MedicalRecordRequest(
        @NotNull MedicalRecordType recordType,
        @NotBlank @Size(max = 255) String title,
        String note,
        LocalDate recordDate) {
}
