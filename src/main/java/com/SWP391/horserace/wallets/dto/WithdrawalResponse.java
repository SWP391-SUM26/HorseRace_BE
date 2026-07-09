package com.SWP391.horserace.wallets.dto;

import com.SWP391.horserace.wallets.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Admin/self view of a withdrawal PaymentTransaction. */
@Data
@Builder
public class WithdrawalResponse {

    /** The PaymentTransaction id (the withdrawal request id). */
    private UUID id;
    private BigDecimal amount;
    private PaymentStatus status;
    private OffsetDateTime createdAt;

    /** Requester identity (for the admin queue); null when unavailable. */
    private UUID requesterId;
    private String requesterName;
}
