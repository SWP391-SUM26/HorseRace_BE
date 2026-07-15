package com.SWP391.horserace.wallets.service;

import com.SWP391.horserace.wallets.dto.VnPayIpnResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface VnPayService {

    /**
     * Creates a PENDING DEPOSIT {@code PaymentTransaction} (idempotency_key = vnp_TxnRef) and
     * returns the signed VNPay pay URL the client redirects to.
     */
    String buildPaymentUrl(UUID userId, BigDecimal amount);

    /**
     * Authoritative server-to-server IPN handler: verifies checksum, matches the txn, validates
     * the amount, and — on responseCode 00 — credits the wallet EXACTLY ONCE via the atomic
     * PENDING->SUCCESS claim. Returns a VNPay {RspCode,Message} acknowledgement.
     */
    VnPayIpnResult handleIpn(Map<String, String> params);

    /**
     * Browser return handler: verifies checksum and returns a display status. Credits only when
     * {@code app.vnpay.return-credit-fallback=true}, and then only through the SAME atomic gate
     * and amount re-validation as {@link #handleIpn}.
     */
    VnPayIpnResult handleReturn(Map<String, String> params);
}
