package com.SWP391.horserace.wallets.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.Wallet;
import com.SWP391.horserace.wallets.entity.WalletStatus;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.repository.WalletTransactionRepository;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Row-locked, idempotent money primitive. Every debit/credit path in the app funnels through here
 * so concurrent mutations serialize on the wallet row (PESSIMISTIC_WRITE) and DEBITs can never
 * overdraw.
 */
@Service
@RequiredArgsConstructor
public class WalletLedgerServiceImpl implements WalletLedgerService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    @Transactional
    public WalletTransaction applyEntry(UUID userId, EntryType entryType, TxnCategory category,
                                        BigDecimal amount, String relatedType, UUID relatedId) {
        // Defensive invariant: the ledger only moves strictly-positive amounts (direction is set by
        // entryType). A zero/negative amount would let a "DEBIT" slip past the funds check and
        // INCREASE the balance — a programming error in a caller, guarded here for defence-in-depth.
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Ledger amount must be strictly positive: " + amount);
        }

        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        // Wallet status gates by DIRECTION (this is the single primitive every move funnels through).
        // A CLOSED wallet is terminal — no money moves at all. A FROZEN wallet is blocked from moving
        // money OUT (spend/withdraw) but may still RECEIVE credits (winnings, refunds, deposits): a
        // freeze should stop outflow, and letting inflow through means one frozen spectator can no
        // longer strand an entire settlement pool on a payout/refund CREDIT.
        WalletStatus status = wallet.getStatus();
        BigDecimal newBalance;
        if (entryType == EntryType.DEBIT) {
            if (status != WalletStatus.ACTIVE) {
                throw new AppException(ErrorCode.WALLET_INACTIVE);
            }
            if (wallet.getBalance().compareTo(amount) < 0) {
                throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
            }
            newBalance = wallet.getBalance().subtract(amount);
        } else {
            if (status == WalletStatus.CLOSED) {
                throw new AppException(ErrorCode.WALLET_INACTIVE);
            }
            newBalance = wallet.getBalance().add(amount);
        }

        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        WalletTransaction txn = WalletTransaction.builder()
                .wallet(wallet)
                .entryType(entryType)
                .txnCategory(category)
                .amount(amount)
                .balanceAfter(newBalance)
                .relatedEntityType(relatedType)
                .relatedEntityId(relatedId)
                .build();
        return walletTransactionRepository.save(txn);
    }
}
