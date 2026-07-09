package com.SWP391.horserace.wallets.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.wallets.dto.TopupRequest;
import com.SWP391.horserace.wallets.dto.WalletTransactionResponse;
import com.SWP391.horserace.wallets.dto.WithdrawalRequest;
import com.SWP391.horserace.wallets.dto.WithdrawalResponse;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.PaymentStatus;
import com.SWP391.horserace.wallets.entity.PaymentTransaction;
import com.SWP391.horserace.wallets.entity.PaymentTransactionType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.Wallet;
import com.SWP391.horserace.wallets.entity.WalletStatus;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
import com.SWP391.horserace.wallets.repository.PaymentTransactionRepository;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.repository.WalletTransactionRepository;
import com.SWP391.horserace.wallets.service.VnPayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock UserRepository userRepository;
    @Mock VnPayService vnPayService;
    @Mock PaymentTransactionRepository paymentTransactionRepository;

    private WalletServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WalletServiceImpl(
                walletRepository, walletTransactionRepository, userRepository,
                vnPayService, paymentTransactionRepository);
    }

    private Wallet wallet() {
        return Wallet.builder()
                .walletId(UUID.randomUUID())
                .user(User.builder().userId(userId).build())
                .balance(new BigDecimal("12345.00"))
                .lockedBalance(BigDecimal.ZERO)
                .currencyCode("VND")
                .status(WalletStatus.ACTIVE)
                .build();
    }

    @Test
    void getOrCreateWallet_returnsExistingWallet() {
        Wallet w = wallet();
        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(w));

        assertThat(service.getOrCreateWallet(userId)).isSameAs(w);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void getOrCreateWallet_createsVndActiveWallet_onFirstRead() {
        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().userId(userId).build());
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Wallet created = service.getOrCreateWallet(userId);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        Wallet saved = captor.getValue();
        assertThat(saved.getCurrencyCode()).isEqualTo("VND");
        assertThat(saved.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(saved.getBalance()).isEqualByComparingTo("0");
        assertThat(created).isSameAs(saved);
    }

    @Test
    void getTransactions_returnsMappedPageForUser() {
        WalletTransaction txn = WalletTransaction.builder()
                .walletTxnId(UUID.randomUUID())
                .entryType(EntryType.CREDIT)
                .txnCategory(TxnCategory.DEPOSIT)
                .amount(new BigDecimal("50000.00"))
                .balanceAfter(new BigDecimal("50000.00"))
                .build();
        when(walletTransactionRepository.findByWallet_User_UserIdOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(txn)));

        var page = service.getTransactions(userId, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        WalletTransactionResponse dto = page.getContent().get(0);
        assertThat(dto.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(dto.getTxnCategory()).isEqualTo(TxnCategory.DEPOSIT);
        assertThat(dto.getAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void createTopup_rejectsAmountBelowMinimum_withTopupAmountInvalid() {
        TopupRequest req = new TopupRequest();
        req.setAmount(new BigDecimal("9999"));

        assertThatThrownBy(() -> service.createTopup(userId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOPUP_AMOUNT_INVALID);

        verify(vnPayService, never()).buildPaymentUrl(any(), any());
    }

    @Test
    void createTopup_rejectsNonPositiveAmount_withTopupAmountInvalid() {
        TopupRequest req = new TopupRequest();
        req.setAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.createTopup(userId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOPUP_AMOUNT_INVALID);
    }

    @Test
    void createTopup_rejectsFractionalVndAmount_withTopupAmountInvalid() {
        // VND has no sub-unit; a fractional amount would truncate in amount*100 sent to VNPay and
        // then false-mismatch the persisted txn.amount on the IPN amount check.
        TopupRequest req = new TopupRequest();
        req.setAmount(new BigDecimal("15000.50"));

        assertThatThrownBy(() -> service.createTopup(userId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOPUP_AMOUNT_INVALID);

        verify(vnPayService, never()).buildPaymentUrl(any(), any());
    }

    @Test
    void createTopup_acceptsIntegralAmountWithTrailingZeroScale() {
        // 50000.00 is integral in value (trailing-zero scale) → allowed.
        Wallet w = wallet();
        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(w));
        when(vnPayService.buildPaymentUrl(eq(userId), any(BigDecimal.class)))
                .thenReturn("https://sandbox.vnpayment.vn/pay?vnp_SecureHash=abc");

        TopupRequest req = new TopupRequest();
        req.setAmount(new BigDecimal("50000.00"));

        assertThat(service.createTopup(userId, req).getPayUrl()).contains("vnp_SecureHash");
    }

    @Test
    void createTopup_validAmount_delegatesToVnPay_andReturnsPayUrl() {
        Wallet w = wallet();
        when(walletRepository.findByUserUserId(userId)).thenReturn(Optional.of(w));
        when(vnPayService.buildPaymentUrl(eq(userId), any(BigDecimal.class)))
                .thenReturn("https://sandbox.vnpayment.vn/pay?vnp_SecureHash=abc");

        TopupRequest req = new TopupRequest();
        req.setAmount(new BigDecimal("50000"));

        var resp = service.createTopup(userId, req);

        assertThat(resp.getPayUrl()).contains("vnp_SecureHash");
        verify(vnPayService).buildPaymentUrl(eq(userId), any(BigDecimal.class));
    }

    // ---- Phase 04: withdrawal lifecycle ----------------------------------------------------

    private WithdrawalRequest withdrawalReq(String amount) {
        WithdrawalRequest req = new WithdrawalRequest();
        req.setAmount(new BigDecimal(amount));
        return req;
    }

    /** A PENDING WITHDRAWAL payment txn whose wallet belongs to {@link #userId}. */
    private PaymentTransaction pendingWithdrawal(Wallet w, String amount) {
        return PaymentTransaction.builder()
                .paymentTxnId(UUID.randomUUID())
                .transactionType(PaymentTransactionType.WITHDRAWAL)
                .paymentStatus(PaymentStatus.PENDING)
                .amount(new BigDecimal(amount))
                .wallet(w)
                .createdBy(w.getUser())
                .build();
    }

    @Test
    void requestWithdrawal_movesBalanceToLocked_andCreatesPendingWithdrawalTxn_noLedgerRow() {
        Wallet w = wallet(); // balance 12345.00, locked 0
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction p = inv.getArgument(0);
            if (p.getPaymentTxnId() == null) p.setPaymentTxnId(UUID.randomUUID());
            return p;
        });

        WithdrawalResponse resp = service.requestWithdrawal(userId, withdrawalReq("10000"));

        // hold moved from spendable to locked
        assertThat(w.getBalance()).isEqualByComparingTo("2345.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("10000");
        verify(walletRepository).save(w);

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertThat(saved.getTransactionType()).isEqualTo(PaymentTransactionType.WITHDRAWAL);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getAmount()).isEqualByComparingTo("10000");
        assertThat(saved.getCreatedBy()).isNotNull();

        // hold model — NO wallet_transaction ledger row until the withdrawal settles
        verify(walletTransactionRepository, never()).save(any());
        assertThat(resp.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void requestWithdrawal_rejectsNegativeAmount_beforeBalanceCheck_walletUntouched() {
        assertThatThrownBy(() -> service.requestWithdrawal(userId, withdrawalReq("-1000000")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_AMOUNT_INVALID);

        // Guard runs BEFORE loading the wallet: a negative amount must never reach `balance -= amount`
        // (which would INCREASE the balance = free money).
        verify(walletRepository, never()).findByUserUserIdForUpdate(any());
        verify(walletRepository, never()).save(any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void requestWithdrawal_rejectsZeroAmount_beforeBalanceCheck_walletUntouched() {
        assertThatThrownBy(() -> service.requestWithdrawal(userId, withdrawalReq("0")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_AMOUNT_INVALID);

        verify(walletRepository, never()).findByUserUserIdForUpdate(any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void requestWithdrawal_rejectsBelowFloor_withWithdrawalAmountInvalid() {
        assertThatThrownBy(() -> service.requestWithdrawal(userId, withdrawalReq("9999")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_AMOUNT_INVALID);

        verify(walletRepository, never()).findByUserUserIdForUpdate(any());
    }

    @Test
    void requestWithdrawal_rejectsFractionalVnd_withWithdrawalAmountInvalid() {
        assertThatThrownBy(() -> service.requestWithdrawal(userId, withdrawalReq("15000.50")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_AMOUNT_INVALID);

        verify(walletRepository, never()).findByUserUserIdForUpdate(any());
    }

    @Test
    void requestWithdrawal_rejectsAmountAboveBalance_withInsufficientBalance() {
        Wallet w = wallet(); // balance 12345.00
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service.requestWithdrawal(userId, withdrawalReq("20000")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        // wallet untouched, no txn
        assertThat(w.getBalance()).isEqualByComparingTo("12345.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0");
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void approveWithdrawal_debitsLocked_marksSuccess_writesDebitWithdrawalLedgerRow() {
        Wallet w = Wallet.builder()
                .walletId(UUID.randomUUID())
                .user(User.builder().userId(userId).build())
                .balance(new BigDecimal("7345.00"))
                .lockedBalance(new BigDecimal("5000.00"))
                .currencyCode("VND")
                .status(WalletStatus.ACTIVE)
                .build();
        PaymentTransaction txn = pendingWithdrawal(w, "5000.00");

        when(paymentTransactionRepository.findById(txn.getPaymentTxnId())).thenReturn(Optional.of(txn));
        when(paymentTransactionRepository.markSuccessIfPending(txn.getPaymentTxnId())).thenReturn(1);
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(walletTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID adminId = UUID.randomUUID();
        WithdrawalResponse resp = service.approveWithdrawal(adminId, txn.getPaymentTxnId());

        // lock released; spendable unchanged (already debited at hold time)
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0");
        assertThat(w.getBalance()).isEqualByComparingTo("7345.00");
        verify(walletRepository).save(w);

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        WalletTransaction ledger = captor.getValue();
        assertThat(ledger.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(ledger.getTxnCategory()).isEqualTo(TxnCategory.WITHDRAWAL);
        assertThat(ledger.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("7345.00");
        assertThat(ledger.getRelatedEntityType()).isEqualTo("PAYMENT_TRANSACTION");
        assertThat(ledger.getRelatedEntityId()).isEqualTo(txn.getPaymentTxnId());
        assertThat(ledger.getPaymentTransaction()).isSameAs(txn);

        assertThat(resp.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void approveWithdrawal_onNonPendingTxn_throwsInvalidState() {
        Wallet w = wallet();
        PaymentTransaction txn = pendingWithdrawal(w, "5000.00");
        when(paymentTransactionRepository.findById(txn.getPaymentTxnId())).thenReturn(Optional.of(txn));
        when(paymentTransactionRepository.markSuccessIfPending(txn.getPaymentTxnId())).thenReturn(0);

        assertThatThrownBy(() -> service.approveWithdrawal(UUID.randomUUID(), txn.getPaymentTxnId()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_INVALID_STATE);

        // atomic claim failed → never touches the wallet or writes a ledger row
        verify(walletRepository, never()).findByUserUserIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void approveWithdrawal_missingTxn_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(paymentTransactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveWithdrawal(UUID.randomUUID(), id))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_NOT_FOUND);
    }

    @Test
    void approveWithdrawal_concurrentDoubleApprove_debitsOnce() {
        Wallet w = Wallet.builder()
                .walletId(UUID.randomUUID())
                .user(User.builder().userId(userId).build())
                .balance(new BigDecimal("7345.00"))
                .lockedBalance(new BigDecimal("5000.00"))
                .currencyCode("VND")
                .status(WalletStatus.ACTIVE)
                .build();
        PaymentTransaction txn = pendingWithdrawal(w, "5000.00");
        when(paymentTransactionRepository.findById(txn.getPaymentTxnId())).thenReturn(Optional.of(txn));
        // first caller wins the claim (1), second sees 0 rows
        when(paymentTransactionRepository.markSuccessIfPending(txn.getPaymentTxnId()))
                .thenReturn(1).thenReturn(0);
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(walletTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approveWithdrawal(UUID.randomUUID(), txn.getPaymentTxnId());
        assertThatThrownBy(() -> service.approveWithdrawal(UUID.randomUUID(), txn.getPaymentTxnId()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_INVALID_STATE);

        // locked debited exactly once; exactly one ledger row
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0");
        verify(walletTransactionRepository).save(any());
    }

    @Test
    void rejectWithdrawal_releasesHold_balanceRestored_txnCancelled() {
        Wallet w = Wallet.builder()
                .walletId(UUID.randomUUID())
                .user(User.builder().userId(userId).build())
                .balance(new BigDecimal("7345.00"))
                .lockedBalance(new BigDecimal("5000.00"))
                .currencyCode("VND")
                .status(WalletStatus.ACTIVE)
                .build();
        PaymentTransaction txn = pendingWithdrawal(w, "5000.00");
        when(paymentTransactionRepository.findById(txn.getPaymentTxnId())).thenReturn(Optional.of(txn));
        when(paymentTransactionRepository.markCancelledIfPending(txn.getPaymentTxnId())).thenReturn(1);
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        WithdrawalResponse resp = service.rejectWithdrawal(UUID.randomUUID(), txn.getPaymentTxnId());

        // hold released back to spendable
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0");
        assertThat(w.getBalance()).isEqualByComparingTo("12345.00");
        verify(walletRepository).save(w);
        // reject records no ledger row (no money left the wallet)
        verify(walletTransactionRepository, never()).save(any());
        assertThat(resp.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void rejectWithdrawal_onNonPendingTxn_throwsInvalidState() {
        Wallet w = wallet();
        PaymentTransaction txn = pendingWithdrawal(w, "5000.00");
        when(paymentTransactionRepository.findById(txn.getPaymentTxnId())).thenReturn(Optional.of(txn));
        when(paymentTransactionRepository.markCancelledIfPending(txn.getPaymentTxnId())).thenReturn(0);

        assertThatThrownBy(() -> service.rejectWithdrawal(UUID.randomUUID(), txn.getPaymentTxnId()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_INVALID_STATE);

        verify(walletRepository, never()).findByUserUserIdForUpdate(any());
    }

    @Test
    void listWithdrawals_returnsPagedWithdrawalTxns() {
        Wallet w = wallet();
        PaymentTransaction txn = pendingWithdrawal(w, "5000.00");
        when(paymentTransactionRepository.findByTransactionTypeAndPaymentStatusOrderByCreatedAtDesc(
                eq(PaymentTransactionType.WITHDRAWAL), eq(PaymentStatus.PENDING), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(txn)));

        var page = service.listWithdrawals(org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        WithdrawalResponse dto = page.getContent().get(0);
        assertThat(dto.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(dto.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void requestWithdrawal_onFrozenWallet_throwsWalletInactive_andHoldsNothing() {
        Wallet w = wallet();
        w.setStatus(WalletStatus.FROZEN);
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service.requestWithdrawal(userId, withdrawalReq("10000")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_INACTIVE);

        assertThat(w.getBalance()).isEqualByComparingTo("12345.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0");
        verify(paymentTransactionRepository, never()).save(any());
    }
}
