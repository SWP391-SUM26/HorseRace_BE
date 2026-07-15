package com.SWP391.horserace.wallets.service.impl;

import com.SWP391.horserace.shared.config.VnPayProperties;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.wallets.dto.VnPayIpnResult;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.PaymentStatus;
import com.SWP391.horserace.wallets.entity.PaymentTransaction;
import com.SWP391.horserace.wallets.entity.PaymentTransactionType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.Wallet;
import com.SWP391.horserace.wallets.repository.PaymentTransactionRepository;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.service.VnPayService;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import com.SWP391.horserace.wallets.util.VnPayHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * VNPay deposit round-trip. Deposits are credited EXACTLY ONCE via the atomic
 * {@code markSuccessIfPending} PENDING->SUCCESS claim (guards VNPay's timeout-retried IPN). The
 * hash secret is used only for signing/verification and is NEVER stored in {@code raw_payload}
 * or logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VnPayServiceImpl implements VnPayService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WalletRepository walletRepository;
    private final WalletLedgerService walletLedgerService;
    private final VnPayProperties vnPayProperties;

    /** True only when a hash-secret is configured (env / local props). Lazy guard — the app still boots without it. */
    private boolean gatewayConfigured() {
        return vnPayProperties.getHashSecret() != null && !vnPayProperties.getHashSecret().isBlank();
    }

    @Override
    @Transactional
    public String buildPaymentUrl(UUID userId, BigDecimal amount) {
        // FR-14: user-facing top-up — surface a clear 503 (ApiResponse envelope) instead of a 500.
        if (!gatewayConfigured()) {
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED);
        }
        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        String txnRef = generateTxnRef();
        PaymentTransaction txn = PaymentTransaction.builder()
                .amount(amount)
                .currencyCode("VND")
                .transactionType(PaymentTransactionType.DEPOSIT)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod("VNPAY")
                .gatewayProvider("VNPAY")
                .businessEntityType("WALLET_TOPUP")
                .idempotencyKey(txnRef)
                .wallet(wallet)
                .createdBy(wallet.getUser())
                .build();
        paymentTransactionRepository.save(txn);

        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        params.put("vnp_Amount", amount.multiply(ONE_HUNDRED).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Topup wallet " + txnRef);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", vnPayProperties.getReturnUrl());
        // NOTE: do NOT send vnp_IpnUrl as a request param. It is not a standard VNPay 2.1.0 payment
        // field — the IPN URL is configured in the merchant portal. VNPay recomputes the signature
        // over its known fields only, so an extra vnp_IpnUrl makes the checksums differ → "Sai chữ ký".
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", now.format(DATE_FMT));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(DATE_FMT));

        String query = VnPayHashUtil.buildQuery(params);
        String secureHash = VnPayHashUtil.hashData(vnPayProperties.getHashSecret(), query);
        return vnPayProperties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    @Override
    @Transactional
    public VnPayIpnResult handleIpn(Map<String, String> params) {
        // FR-14: called by VNPay's servers — must NOT throw (would emit an ApiResponse envelope and
        // break VNPay's {RspCode,Message} contract). Answer in VNPay vocabulary instead. No credit.
        if (!gatewayConfigured()) {
            return result("99", "Gateway not configured", false);
        }
        if (!VnPayHashUtil.verifySignature(params, vnPayProperties.getHashSecret())) {
            return result("97", "Invalid Checksum", false);
        }

        String txnRef = params.get("vnp_TxnRef");
        PaymentTransaction txn = paymentTransactionRepository.findByIdempotencyKey(txnRef).orElse(null);
        if (txn == null) {
            return result("01", "Order not Found", false);
        }

        if (amountMismatch(params, txn)) {
            return result("04", "Invalid Amount", false);
        }

        String responseCode = params.get("vnp_ResponseCode");
        if (!"00".equals(responseCode)) {
            if (txn.getPaymentStatus() == PaymentStatus.PENDING) {
                txn.setPaymentStatus(PaymentStatus.FAILED);
                txn.setExternalTxnRef(params.get("vnp_TransactionNo"));
                txn.setRawPayload(toJson(params));
                paymentTransactionRepository.save(txn);
            }
            // Acknowledge receipt of the (failed) notification; nothing credited.
            return result("00", "Confirm Success", false);
        }

        // responseCode == 00 → exactly-once atomic claim of the PENDING row.
        int claimed = paymentTransactionRepository.markSuccessIfPending(txn.getPaymentTxnId());
        if (claimed == 0) {
            return result("02", "Order already confirmed", false);
        }

        txn.setPaymentStatus(PaymentStatus.SUCCESS);
        txn.setExternalTxnRef(params.get("vnp_TransactionNo"));
        txn.setRawPayload(toJson(params));
        paymentTransactionRepository.save(txn);

        walletLedgerService.applyEntry(
                txn.getWallet().getUser().getUserId(),
                EntryType.CREDIT, TxnCategory.DEPOSIT, txn.getAmount(),
                "PAYMENT_TRANSACTION", txn.getPaymentTxnId());

        return result("00", "Confirm Success", true);
    }

    @Override
    @Transactional
    public VnPayIpnResult handleReturn(Map<String, String> params) {
        // FR-14: display-only return path — same graceful VNPay-vocabulary failure, never throw, no credit.
        if (!gatewayConfigured()) {
            return result("99", "Gateway not configured", false);
        }
        if (!VnPayHashUtil.verifySignature(params, vnPayProperties.getHashSecret())) {
            return result("97", "Invalid Checksum", false);
        }

        boolean paid = "00".equals(params.get("vnp_ResponseCode"));

        // Guarded fallback: same atomic PENDING gate AND same amount re-validation as IPN.
        if (paid && vnPayProperties.isReturnCreditFallback()) {
            PaymentTransaction txn = paymentTransactionRepository
                    .findByIdempotencyKey(params.get("vnp_TxnRef")).orElse(null);
            if (txn != null && !amountMismatch(params, txn)
                    && paymentTransactionRepository.markSuccessIfPending(txn.getPaymentTxnId()) == 1) {
                txn.setPaymentStatus(PaymentStatus.SUCCESS);
                txn.setExternalTxnRef(params.get("vnp_TransactionNo"));
                txn.setRawPayload(toJson(params));
                paymentTransactionRepository.save(txn);
                walletLedgerService.applyEntry(
                        txn.getWallet().getUser().getUserId(),
                        EntryType.CREDIT, TxnCategory.DEPOSIT, txn.getAmount(),
                        "PAYMENT_TRANSACTION", txn.getPaymentTxnId());
            }
        }

        return result(paid ? "00" : params.get("vnp_ResponseCode"),
                paid ? "Payment successful" : "Payment not completed", paid);
    }

    /** True when {@code vnp_Amount/100 != txn.amount}. */
    private boolean amountMismatch(Map<String, String> params, PaymentTransaction txn) {
        BigDecimal callbackAmount = new BigDecimal(params.get("vnp_Amount")).movePointLeft(2);
        return callbackAmount.compareTo(txn.getAmount()) != 0;
    }

    /** Serialize params to JSON for {@code raw_payload}, stripping the signature fields. Secret-free. */
    private String toJson(Map<String, String> params) {
        Map<String, String> safe = new LinkedHashMap<>(params);
        safe.remove("vnp_SecureHash");
        safe.remove("vnp_SecureHashType");
        try {
            return OBJECT_MAPPER.writeValueAsString(safe);
        } catch (Exception ex) {
            log.warn("Failed to serialize VNPay raw payload for txnRef={}", params.get("vnp_TxnRef"));
            return null;
        }
    }

    private VnPayIpnResult result(String rspCode, String message, boolean success) {
        return VnPayIpnResult.builder().rspCode(rspCode).message(message).success(success).build();
    }

    private String generateTxnRef() {
        return ZonedDateTime.now(VN_ZONE).format(DATE_FMT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
