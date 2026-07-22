package com.SWP391.horserace.races.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One prize tier in a race's distribution, e.g. { place: "1st", amount: 340260 }.
 *
 * <p>These constraints only take effect because {@code RaceRequest.prizeDistribution} carries
 * {@code @Valid} — without it Bean Validation does not descend into the list, and a tier with a
 * blank place or a negative amount reached the database unchallenged.
 */
public record PrizeDistributionDto(
        @NotBlank(message = "Thứ hạng không được để trống")
        @Size(max = 20, message = "Thứ hạng quá dài")
        String place,

        @NotNull(message = "Tiền thưởng không được để trống")
        @DecimalMin(value = "0.01", message = "Tiền thưởng mỗi thứ hạng phải lớn hơn 0")
        @Digits(integer = 16, fraction = 2, message = "Tiền thưởng không hợp lệ")
        BigDecimal amount) {
}
