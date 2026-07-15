package com.SWP391.horserace.wallets.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopupRequest {

    /** Amount to deposit, in VND. Minimum enforced in the service (10,000 VND). */
    @NotNull
    private BigDecimal amount;
}
