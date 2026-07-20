package com.SWP391.horserace.wallets.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawalRequest {

    /** Amount to withdraw, in VND. Minimum (10,000) and integrality enforced in the service. */
    @NotNull
    private BigDecimal amount;
}
