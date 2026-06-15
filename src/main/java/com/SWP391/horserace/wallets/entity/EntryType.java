package com.SWP391.horserace.wallets.entity;

/** Mirrors the CHECK constraint on wallet_transaction.entry_type in db/schema_v4.sql. Names MUST match DB values. */
public enum EntryType {
    DEBIT,
    CREDIT
}
