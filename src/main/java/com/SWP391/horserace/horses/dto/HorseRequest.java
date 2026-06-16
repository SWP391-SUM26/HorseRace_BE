package com.SWP391.horserace.horses.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create/update payload for a horse. Used for POST (create — name required, enforced in the
 * service) and PUT (partial update — only non-null fields are applied). Validation mirrors the
 * {@code horse} column constraints; enum-like fields are checked against their CHECK values
 * (the {@code @Pattern}s allow null so partial updates pass).
 */
public record HorseRequest(

        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Size(max = 100, message = "Microchip number must not exceed 100 characters")
        String microchipNo,

        @Pattern(regexp = "^(MALE|FEMALE|GELDING)$", message = "Gender must be MALE, FEMALE or GELDING")
        String gender,

        @Size(max = 100, message = "Breed must not exceed 100 characters")
        String breed,

        @Size(max = 100, message = "Color must not exceed 100 characters")
        String color,

        LocalDate dateOfBirth,

        @Positive(message = "Weight must be positive")
        BigDecimal weight,

        @Size(max = 100, message = "Origin country must not exceed 100 characters")
        String originCountry,

        @Pattern(regexp = "^(HEALTHY|INJURED|QUARANTINE|UNFIT)$",
                message = "Health status must be HEALTHY, INJURED, QUARANTINE or UNFIT")
        String healthStatus,

        @Size(max = 50, message = "Registration status must not exceed 50 characters")
        String registrationStatus,

        @Pattern(regexp = "^(ACTIVE|RETIRED|INACTIVE)$",
                message = "Status must be ACTIVE, RETIRED or INACTIVE")
        String status
) {
}
