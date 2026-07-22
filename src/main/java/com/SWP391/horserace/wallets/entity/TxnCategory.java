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
    REWARD,
    ENTRY_FEE,
    /**
     * Organiser funding a tournament's prize purse into the house wallet.
     *
     * Prizes are NOT paid out of entry fees or betting rake — those come nowhere near covering them
     * (fees collected ~105M against ~950M of prizes already awarded). The purse is sponsor money,
     * credited to the house when a tournament is published and debited again as each race is
     * certified, so the ledger can answer "where did the prize money come from".
     */
    SPONSOR,
    /**
     * The owner paying the jockey's agreed hire fee for one ride.
     *
     * Escrowed, not paid on the spot: the amount is locked out of the owner's spendable balance the
     * moment the invitation is sent (so they cannot promise money they no longer have), and only
     * crosses into the jockey's wallet once the race has actually been run and certified. Declining,
     * cancelling, withdrawing or cancelling the race unlocks it again — no ledger row is written for
     * a hold that never settled, because nothing moved between wallets.
     *
     * Distinct from PRIZE: this is the rider's wage, paid by the owner. The prize share is
     * performance money and comes out of the sponsor-funded house wallet. A jockey earns both.
     */
    JOCKEY_FEE
}
