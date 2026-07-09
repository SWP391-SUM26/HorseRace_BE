package com.SWP391.horserace.wallets.service;

import com.SWP391.horserace.wallets.dto.TopupRequest;
import com.SWP391.horserace.wallets.dto.TopupResponse;
import com.SWP391.horserace.wallets.dto.WalletResponse;
import com.SWP391.horserace.wallets.dto.WalletTransactionResponse;
import com.SWP391.horserace.wallets.dto.WithdrawalRequest;
import com.SWP391.horserace.wallets.dto.WithdrawalResponse;
import com.SWP391.horserace.wallets.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WalletService {

    /** Returns the user's wallet, creating a VND ACTIVE wallet on first access. */
    Wallet getOrCreateWallet(UUID userId);

    /** Balance/locked/currency/status view (auto-creates the wallet). */
    WalletResponse getBalance(UUID userId);

    /** Paged ledger history, newest first. */
    Page<WalletTransactionResponse> getTransactions(UUID userId, Pageable pageable);

    /** Starts a VNPay deposit: validates the amount and returns a pay URL. */
    TopupResponse createTopup(UUID userId, TopupRequest request);

    /**
     * Requests a withdrawal: validates the amount (>= 10,000 VND, integral) BEFORE any balance check,
     * then holds the funds (balance -> locked_balance) and records a PENDING WITHDRAWAL payment txn.
     */
    WithdrawalResponse requestWithdrawal(UUID userId, WithdrawalRequest request);

    /** Admin queue: all withdrawal payment txns, newest first, as {@link WithdrawalResponse}. */
    Page<WithdrawalResponse> listWithdrawals(Pageable pageable);

    /** Admin approve: atomically claims the PENDING txn, releases the lock, and writes the DEBIT ledger row. */
    WithdrawalResponse approveWithdrawal(UUID adminId, UUID withdrawalId);

    /** Admin reject: atomically claims the PENDING txn and releases the hold back to spendable balance. */
    WithdrawalResponse rejectWithdrawal(UUID adminId, UUID withdrawalId);
}
