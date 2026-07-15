package com.SWP391.horserace.wallets.dto;

import com.SWP391.horserace.wallets.entity.WalletStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {
    private UUID walletId;
    private BigDecimal balance;
    private BigDecimal lockedBalance;
    private String currencyCode;
    private WalletStatus status;
}
