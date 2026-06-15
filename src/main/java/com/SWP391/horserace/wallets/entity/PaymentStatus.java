package com.SWP391.horserace.wallets.entity;

/** Mirrors the CHECK constraint on payment_transaction.payment_status in db/schema_v4.sql. Names MUST match DB values. */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUNDED
}
