package com.SWP391.horserace.wallets.entity;

/** Mirrors the CHECK constraint on wallet.status in db/schema_v4.sql. Names MUST match DB values. */
public enum WalletStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
