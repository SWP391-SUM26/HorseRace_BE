package com.SWP391.horserace.horses.dto;

import com.SWP391.horserace.horses.entity.HorseGender;
import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import com.SWP391.horserace.horses.entity.HorseStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create/update payload for a horse. Used for POST (create — name required, enforced in the
 * service) and PUT (partial update — only non-null fields are applied). Enum fields are bound by
 * Jackson from their names (e.g. "MALE"); an invalid value yields a 400 before reaching the service.
 */
public record HorseRequest(

        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Size(max = 100, message = "Microchip number must not exceed 100 characters")
        String microchipNo,

        HorseGender gender,

        @Size(max = 100, message = "Breed must not exceed 100 characters")
        String breed,

        @Size(max = 100, message = "Color must not exceed 100 characters")
        String color,

        LocalDate dateOfBirth,

        @Positive(message = "Weight must be positive")
        @Digits(integer = 4, fraction = 2, message = "Weight must have at most 4 digits and 2 decimals")
        BigDecimal weight,

        @Size(max = 100, message = "Origin country must not exceed 100 characters")
        String originCountry,

        HorseHealthStatus healthStatus,

        @Size(max = 50, message = "Registration status must not exceed 50 characters")
        String registrationStatus,

        HorseStatus status
) {
}
