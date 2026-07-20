package com.SWP391.horserace.wallets.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TopupResponse {
    /** The VNPay pay URL the client redirects the user to. */
    private String payUrl;
    /** The idempotency key (vnp_TxnRef) of the created PENDING deposit. */
    private String txnRef;
    private BigDecimal amount;
}
