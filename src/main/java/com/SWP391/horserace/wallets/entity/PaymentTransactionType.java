package com.SWP391.horserace.wallets.entity;

/** Mirrors the CHECK constraint on payment_transaction.transaction_type in db/schema_v4.sql. Names MUST match DB values. */
public enum PaymentTransactionType {
    DEPOSIT,
    WITHDRAWAL,
    PAYOUT,
    REFUND
}
