package com.SWP391.horserace.wallets.entity;

/** Mirrors the CHECK constraint on wallet_transaction.txn_category in db/schema_v4.sql. Names MUST match DB values. */
public enum TxnCategory {
    DEPOSIT,
    WITHDRAWAL,
    BET_STAKE,
    BET_PAYOUT,
    PRIZE,
    REFUND,
    ADJUSTMENT,
    REWARD
}
