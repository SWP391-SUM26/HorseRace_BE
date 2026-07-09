package com.SWP391.horserace.wallets.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.Wallet;
import com.SWP391.horserace.wallets.entity.WalletStatus;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletLedgerServiceImplTest {

    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;

    private WalletLedgerServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID relatedId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WalletLedgerServiceImpl(walletRepository, walletTransactionRepository);
    }

    private Wallet wallet(BigDecimal balance) {
        User user = User.builder().userId(userId).build();
        return Wallet.builder()
                .walletId(UUID.randomUUID())
                .user(user)
                .balance(balance)
                .lockedBalance(BigDecimal.ZERO)
                .currencyCode("VND")
                .status(WalletStatus.ACTIVE)
                .build();
    }

    @Test
    void applyEntry_creditDeposit_increasesBalance_andWritesLedgerRowWithBalanceAfter() {
        Wallet w = wallet(BigDecimal.ZERO);
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(walletTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal amount = new BigDecimal("50000.00");
        WalletTransaction result = service.applyEntry(
                userId, EntryType.CREDIT, TxnCategory.DEPOSIT, amount, "PAYMENT_TRANSACTION", relatedId);

        assertThat(w.getBalance()).isEqualByComparingTo("50000.00");
        verify(walletRepository).save(w);

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        WalletTransaction saved = captor.getValue();
        assertThat(saved.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(saved.getTxnCategory()).isEqualTo(TxnCategory.DEPOSIT);
        assertThat(saved.getAmount()).isEqualByComparingTo("50000.00");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("50000.00");
        assertThat(saved.getRelatedEntityType()).isEqualTo("PAYMENT_TRANSACTION");
        assertThat(saved.getRelatedEntityId()).isEqualTo(relatedId);
        assertThat(saved.getWallet()).isSameAs(w);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void applyEntry_debitWithSufficientFunds_decreasesBalance() {
        Wallet w = wallet(new BigDecimal("100000.00"));
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(walletTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletTransaction result = service.applyEntry(
                userId, EntryType.DEBIT, TxnCategory.BET_STAKE, new BigDecimal("40000.00"), "PREDICTION", relatedId);

        assertThat(w.getBalance()).isEqualByComparingTo("60000.00");
        assertThat(result.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(result.getBalanceAfter()).isEqualByComparingTo("60000.00");
    }

    @Test
    void applyEntry_debitInsufficientFunds_throwsInsufficientBalance_andNeverSaves() {
        Wallet w = wallet(new BigDecimal("5000.00"));
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service.applyEntry(
                userId, EntryType.DEBIT, TxnCategory.BET_STAKE, new BigDecimal("10000.00"), "PREDICTION", relatedId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        verify(walletTransactionRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        assertThat(w.getBalance()).isEqualByComparingTo("5000.00");
    }

    @Test
    void applyEntry_usesForUpdateLookup_notUnlockedRead() {
        Wallet w = wallet(BigDecimal.ZERO);
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(walletTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyEntry(userId, EntryType.CREDIT, TxnCategory.REWARD, new BigDecimal("1000.00"), "REWARD", relatedId);

        verify(walletRepository).findByUserUserIdForUpdate(userId);
        verify(walletRepository, never()).findByUserUserId(any());
    }

    @Test
    void applyEntry_throwsWalletNotFound_whenNoWallet() {
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyEntry(
                userId, EntryType.CREDIT, TxnCategory.DEPOSIT, new BigDecimal("1000.00"), "REWARD", relatedId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    void applyEntry_throwsWalletInactive_whenWalletNotActive_andNeverMovesMoney() {
        Wallet w = wallet(new BigDecimal("100000.00"));
        w.setStatus(WalletStatus.FROZEN);
        when(walletRepository.findByUserUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service.applyEntry(
                userId, EntryType.CREDIT, TxnCategory.REWARD, new BigDecimal("1000.00"), "REWARD", relatedId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_INACTIVE);

        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
        assertThat(w.getBalance()).isEqualByComparingTo("100000.00");
    }

    @Test
    void applyEntry_rejectsNonPositiveAmount_beforeTouchingTheWallet() {
        // Zero and negative amounts are a caller bug; the primitive guards defensively so a "DEBIT"
        // of a negative amount can never slip past the funds check and INCREASE the balance.
        assertThatThrownBy(() -> service.applyEntry(
                userId, EntryType.DEBIT, TxnCategory.BET_STAKE, new BigDecimal("-5000.00"), "PREDICTION", relatedId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyEntry(
                userId, EntryType.CREDIT, TxnCategory.DEPOSIT, BigDecimal.ZERO, "PAYMENT_TRANSACTION", relatedId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(walletRepository, never()).findByUserUserIdForUpdate(any());
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }
}
