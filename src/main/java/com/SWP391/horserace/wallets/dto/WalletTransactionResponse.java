package com.SWP391.horserace.wallets.dto;

import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class WalletTransactionResponse {
    private UUID walletTxnId;
    private EntryType entryType;
    private TxnCategory txnCategory;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String relatedEntityType;
    private UUID relatedEntityId;
    private OffsetDateTime createdAt;
}
