package com.SWP391.horserace.wallets.service.impl;

import com.SWP391.horserace.shared.config.VnPayProperties;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.dto.VnPayIpnResult;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.PaymentStatus;
import com.SWP391.horserace.wallets.entity.PaymentTransaction;
import com.SWP391.horserace.wallets.entity.PaymentTransactionType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.Wallet;
import com.SWP391.horserace.wallets.repository.PaymentTransactionRepository;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import com.SWP391.horserace.wallets.util.VnPayHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VnPayServiceImplTest {

    private static final String SECRET = "unit-test-secret";

    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletLedgerService walletLedgerService;

    private VnPayProperties props;
    private VnPayServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID txnId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        props = new VnPayProperties();
        props.setTmnCode("TESTTMN");
        props.setHashSecret(SECRET);
        props.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        props.setReturnUrl("http://localhost:5175/wallet/return");
        props.setIpnUrl("http://localhost:8080/api/v1/wallet/vnpay-ipn");
        props.setReturnCreditFallback(false);
        service = new VnPayServiceImpl(
                paymentTransactionRepository, walletRepository, walletLedgerService, props);
    }

    private Wallet wallet() {
        return Wallet.builder()
                .walletId(UUID.randomUUID())
                .user(User.builder().userId(userId).build())
                .balance(BigDecimal.ZERO)
                .build();
    }

    private PaymentTransaction pendingTxn(BigDecimal amount) {
        return PaymentTransaction.builder()
                .paymentTxnId(txnId)
                .amount(amount)
                .currencyCode("VND")
                .transactionType(PaymentTransactionType.DEPOSIT)
                .paymentStatus(PaymentStatus.PENDING)
                .idempotencyKey("ORDER-REF-1")
                .wallet(wallet())
                .build();
    }

    /** Build a callback param map and attach a valid signature. */
    private Map<String, String> signedCallback(String vnpAmount, String responseCode) {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_Amount", vnpAmount);
        p.put("vnp_TxnRef", "ORDER-REF-1");
        p.put("vnp_TransactionNo", "VNPTXN-999");
        p.put("vnp_ResponseCode", responseCode);
        p.put("vnp_TmnCode", "TESTTMN");
        String hash = VnPayHashUtil.hashData(SECRET, VnPayHashUtil.buildQuery(p));
        p.put("vnp_SecureHash", hash);
        return p;
    }

    // ---- buildPaymentUrl -------------------------------------------------

    @Test
    void buildPaymentUrl_createsPendingDepositTxn_andReturnsSignedUrl() {
        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(wallet()));
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String url = service.buildPaymentUrl(userId, new BigDecimal("50000"));

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getTransactionType()).isEqualTo(PaymentTransactionType.DEPOSIT);
        assertThat(saved.getIdempotencyKey()).isNotBlank();
        assertThat(saved.getAmount()).isEqualByComparingTo("50000");

        assertThat(url).contains("vnp_SecureHash");
        assertThat(url).contains("vnp_Amount=5000000"); // 50000 * 100
        assertThat(url).contains("vnp_CurrCode=VND");
        assertThat(url).contains("vnp_TxnRef=" + saved.getIdempotencyKey());
    }

    @Test
    void buildPaymentUrl_throwsGatewayNotConfigured_whenHashSecretBlank() {
        props.setHashSecret(""); // gateway not configured
        assertThatThrownBy(() -> service.buildPaymentUrl(userId, new BigDecimal("50000")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED);
        // Guard is the first line: no wallet lookup, no PENDING txn persisted.
        verify(paymentTransactionRepository, never()).save(any());
    }

    // ---- handleIpn -------------------------------------------------------

    @Test
    void handleIpn_returnsVnPayFailureCode_whenSecretBlank_doesNotThrow() {
        props.setHashSecret(null); // gateway not configured
        // Must NOT throw — VNPay's servers expect the raw {RspCode,Message} contract.
        VnPayIpnResult result = service.handleIpn(signedCallback("5000000", "00"));

        assertThat(result.getRspCode()).isEqualTo("99");
        assertThat(result.isSuccess()).isFalse();
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleIpn_validChecksum_pending_responseCode00_creditsOnce_returns00() {
        PaymentTransaction txn = pendingTxn(new BigDecimal("50000"));
        when(paymentTransactionRepository.findByIdempotencyKey("ORDER-REF-1")).thenReturn(Optional.of(txn));
        when(paymentTransactionRepository.markSuccessIfPending(txnId)).thenReturn(1);

        VnPayIpnResult result = service.handleIpn(signedCallback("5000000", "00"));

        assertThat(result.getRspCode()).isEqualTo("00");
        assertThat(txn.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(txn.getExternalTxnRef()).isEqualTo("VNPTXN-999");
        verify(walletLedgerService, times(1)).applyEntry(
                eq(userId), eq(EntryType.CREDIT), eq(TxnCategory.DEPOSIT),
                eq(new BigDecimal("50000")), eq("PAYMENT_TRANSACTION"), eq(txnId));
    }

    @Test
    void handleIpn_idempotent_secondCallOnAlreadySuccess_returns02_noCredit() {
        PaymentTransaction txn = pendingTxn(new BigDecimal("50000"));
        txn.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentTransactionRepository.findByIdempotencyKey("ORDER-REF-1")).thenReturn(Optional.of(txn));
        when(paymentTransactionRepository.markSuccessIfPending(txnId)).thenReturn(0);

        VnPayIpnResult result = service.handleIpn(signedCallback("5000000", "00"));

        assertThat(result.getRspCode()).isEqualTo("02");
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleIpn_concurrentDuplicateDelivery_creditsExactlyOnce() {
        PaymentTransaction txn = pendingTxn(new BigDecimal("50000"));
        when(paymentTransactionRepository.findByIdempotencyKey("ORDER-REF-1")).thenReturn(Optional.of(txn));
        // first delivery wins the claim (1), the racing second observes PENDING but loses (0)
        when(paymentTransactionRepository.markSuccessIfPending(txnId)).thenReturn(1, 0);

        VnPayIpnResult first = service.handleIpn(signedCallback("5000000", "00"));
        VnPayIpnResult second = service.handleIpn(signedCallback("5000000", "00"));

        assertThat(first.getRspCode()).isEqualTo("00");
        assertThat(second.getRspCode()).isEqualTo("02");
        verify(walletLedgerService, times(1)).applyEntry(
                any(), eq(EntryType.CREDIT), eq(TxnCategory.DEPOSIT), any(), any(), any());
    }

    @Test
    void handleIpn_badChecksum_returns97_doesNotTouchWallet() {
        Map<String, String> p = signedCallback("5000000", "00");
        p.put("vnp_SecureHash", "deadbeef"); // corrupt signature

        VnPayIpnResult result = service.handleIpn(p);

        assertThat(result.getRspCode()).isEqualTo("97");
        verify(paymentTransactionRepository, never()).findByIdempotencyKey(any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleIpn_unknownTxnRef_returns01() {
        when(paymentTransactionRepository.findByIdempotencyKey("ORDER-REF-1")).thenReturn(Optional.empty());

        VnPayIpnResult result = service.handleIpn(signedCallback("5000000", "00"));

        assertThat(result.getRspCode()).isEqualTo("01");
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleIpn_amountMismatch_returns04_noCredit() {
        PaymentTransaction txn = pendingTxn(new BigDecimal("50000"));
        when(paymentTransactionRepository.findByIdempotencyKey("ORDER-REF-1")).thenReturn(Optional.of(txn));

        // vnp_Amount = 1000000 => 10000 VND, but txn is 50000 VND
        VnPayIpnResult result = service.handleIpn(signedCallback("1000000", "00"));

        assertThat(result.getRspCode()).isEqualTo("04");
        verify(paymentTransactionRepository, never()).markSuccessIfPending(any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleIpn_responseCodeNot00_marksFailed_noCredit() {
        PaymentTransaction txn = pendingTxn(new BigDecimal("50000"));
        when(paymentTransactionRepository.findByIdempotencyKey("ORDER-REF-1")).thenReturn(Optional.of(txn));

        service.handleIpn(signedCallback("5000000", "24")); // 24 = user cancelled

        assertThat(txn.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentTransactionRepository, never()).markSuccessIfPending(any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    // ---- handleReturn ----------------------------------------------------

    @Test
    void handleReturn_returnsFailureResult_whenSecretBlank_doesNotThrow() {
        props.setHashSecret("   "); // gateway not configured
        VnPayIpnResult result = service.handleReturn(signedCallback("5000000", "00"));

        assertThat(result.isSuccess()).isFalse();
        verify(paymentTransactionRepository, never()).markSuccessIfPending(any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleReturn_fallbackOff_returnsStatusOnly_neverCredits() {
        VnPayIpnResult result = service.handleReturn(signedCallback("5000000", "00"));

        assertThat(result.isSuccess()).isTrue();
        verify(paymentTransactionRepository, never()).markSuccessIfPending(any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleReturn_fallbackOn_creditsThroughAtomicGate() {
        props.setReturnCreditFallback(true);
        PaymentTransaction txn = pendingTxn(new BigDecimal("50000"));
        when(paymentTransactionRepository.findByIdempotencyKey("ORDER-REF-1")).thenReturn(Optional.of(txn));
        when(paymentTransactionRepository.markSuccessIfPending(txnId)).thenReturn(1);

        service.handleReturn(signedCallback("5000000", "00"));

        verify(walletLedgerService, times(1)).applyEntry(
                eq(userId), eq(EntryType.CREDIT), eq(TxnCategory.DEPOSIT),
                eq(new BigDecimal("50000")), eq("PAYMENT_TRANSACTION"), eq(txnId));
    }

    @Test
    void handleReturn_fallbackOn_amountMismatch_creditsNothing() {
        props.setReturnCreditFallback(true);
        PaymentTransaction txn = pendingTxn(new BigDecimal("50000"));
        when(paymentTransactionRepository.findByIdempotencyKey("ORDER-REF-1")).thenReturn(Optional.of(txn));

        // forged amount 10000 VND against a 50000 VND txn
        service.handleReturn(signedCallback("1000000", "00"));

        verify(paymentTransactionRepository, never()).markSuccessIfPending(any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }
}
