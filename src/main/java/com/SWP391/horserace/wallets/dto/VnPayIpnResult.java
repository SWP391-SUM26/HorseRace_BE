package com.SWP391.horserace.wallets.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Result of processing a VNPay IPN or return callback.
 *
 * <p>{@code rspCode}/{@code message} follow VNPay's own IPN acknowledgement contract
 * (00=confirm success, 01=order not found, 02=already confirmed, 04=amount invalid,
 * 97=checksum failed) — NOT the app's {@code ErrorCode}. {@code success} is a convenience
 * flag for the browser return page.
 */
@Data
@Builder
public class VnPayIpnResult {
    private String rspCode;
    private String message;
    private boolean success;
}
