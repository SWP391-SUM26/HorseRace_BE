package com.SWP391.horserace.wallets.service;

import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.WalletTransaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The single, row-locked, money-mutating primitive reused by every wallet flow (deposit, bet,
 * payout, refund, reward). Loads the wallet via a PESSIMISTIC_WRITE lock, checks funds on DEBIT,
 * mutates the balance, and writes the {@code WalletTransaction} ledger row.
 */
public interface WalletLedgerService {

    /**
     * Apply one ledger entry to a user's wallet.
     *
     * @param userId      the wallet owner
     * @param entryType   DEBIT (funds out) or CREDIT (funds in) — sets the direction
     * @param category    the ledger category (DEPOSIT, BET_STAKE, REWARD, ...)
     * @param amount      positive BigDecimal amount
     * @param relatedType polymorphic related-entity type tag (e.g. "PAYMENT_TRANSACTION")
     * @param relatedId   polymorphic related-entity id
     * @return the persisted {@code WalletTransaction} (with {@code balanceAfter} = new balance)
     */
    WalletTransaction applyEntry(UUID userId, EntryType entryType, TxnCategory category,
                                 BigDecimal amount, String relatedType, UUID relatedId);
}
