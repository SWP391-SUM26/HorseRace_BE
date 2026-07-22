package com.SWP391.horserace.assignments.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/assignments/invitations}.
 * The horse owner specifies which race entry and which jockey to invite.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendInvitationRequest {

    /** The race entry (horse slot) to assign the jockey to. */
    @NotNull(message = "entryId is required")
    private UUID entryId;

    /** The jockey user to invite. */
    @NotNull(message = "jockeyUserId is required")
    private UUID jockeyUserId;

    /**
     * Share of the horse's prize the jockey takes, agreed for THIS ride. Optional — omitted means
     * the jockey's advertised rate. Stored on the assignment and frozen once accepted, so a later
     * edit to the jockey's asking price cannot change what a completed ride pays.
     */
    @DecimalMin(value = "0.00", message = "Tỉ lệ chia thưởng không được âm")
    @DecimalMax(value = "100.00", message = "Tỉ lệ chia thưởng không được vượt 100%")
    @Digits(integer = 3, fraction = 2, message = "Tỉ lệ chia thưởng không hợp lệ")
    private BigDecimal agreedPrizePercent;

    /** Flat riding fee agreed alongside the share. Shown to the jockey; not charged yet. */
    @PositiveOrZero(message = "Phí thuê nài không được âm")
    @Digits(integer = 16, fraction = 2, message = "Phí thuê nài không hợp lệ")
    private BigDecimal agreedBaseFee;
}
