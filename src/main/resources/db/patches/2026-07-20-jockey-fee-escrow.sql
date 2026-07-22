-- =============================================================================
-- Jockey hire fee becomes escrowed owner money; the owner stops paying entry fees
-- =============================================================================
-- Business change: the prize purse is sponsor money (see 2026-07-19-sponsor-funded-prizes.sql),
-- so the owner no longer pays to enter a race. The ONLY money an owner now spends is the jockey's
-- hire fee, and that is escrowed rather than paid up front:
--
--   invite   -> owner must hold >= agreed_base_fee; the amount moves balance -> locked_balance
--   decline / owner-cancel / jockey-withdraw / race cancelled -> unlock, owner can withdraw again
--   race certified -> the locked amount leaves the owner and lands in the jockey's wallet
--
-- jockey_assignment gains the escrow columns. Three timestamps, each taken as an atomic claim
-- (UPDATE ... WHERE ... IS NULL) exactly like registration.entry_fee_paid_at, so a retry or a
-- concurrent request cannot double-pay, double-release, or both pay and release the same hold.
--
-- Run against a LIVE database that already holds seeded history. schema_v4.sql carries the matching
-- definitions for a fresh volume; this patch is what fixes a running one (sql.init.mode is off and
-- ddl-auto=validate does not inspect CHECK constraints, so the old category list would survive and
-- every JOCKEY_FEE insert would fail).
--
--   docker exec -i horserace_postgres psql -U postgres -d horserace_db \
--     < src/main/resources/db/patches/2026-07-20-jockey-fee-escrow.sql
-- =============================================================================

BEGIN;

-- 1) Allow the new category ---------------------------------------------------
ALTER TABLE wallet_transaction DROP CONSTRAINT IF EXISTS wallet_transaction_txn_category_check;
ALTER TABLE wallet_transaction ADD CONSTRAINT wallet_transaction_txn_category_check
    CHECK (txn_category IN ('DEPOSIT', 'WITHDRAWAL', 'BET_STAKE', 'BET_PAYOUT', 'PRIZE',
                            'REFUND', 'ADJUSTMENT', 'REWARD', 'ENTRY_FEE', 'SPONSOR',
                            'JOCKEY_FEE'));

-- 2) Escrow columns on the invitation -----------------------------------------
ALTER TABLE jockey_assignment
    ADD COLUMN IF NOT EXISTS fee_held_amount NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS fee_held_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS fee_released_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS fee_paid_at     TIMESTAMPTZ;

ALTER TABLE jockey_assignment DROP CONSTRAINT IF EXISTS jockey_assignment_fee_held_amount_check;
ALTER TABLE jockey_assignment ADD CONSTRAINT jockey_assignment_fee_held_amount_check
    CHECK (fee_held_amount IS NULL OR fee_held_amount >= 0);

-- A hold is either returned or paid out, never both.
ALTER TABLE jockey_assignment DROP CONSTRAINT IF EXISTS jockey_assignment_fee_settled_once_check;
ALTER TABLE jockey_assignment ADD CONSTRAINT jockey_assignment_fee_settled_once_check
    CHECK (fee_released_at IS NULL OR fee_paid_at IS NULL);

-- 3) Existing invitations are grandfathered ------------------------------------
-- Rows created before this patch were never escrowed, so their fee columns stay NULL. The service
-- treats a NULL fee_held_at as "nothing was held" and skips both release and payout, which is what
-- we want: retro-charging owners for hires they already made would take money they never agreed to
-- spend. New invitations from here on escrow normally.

COMMIT;

-- Invariants — run after the app has restarted and taken at least one invitation.
--
--   -- every held-but-unsettled amount must still be sitting in the owner's locked_balance
--   SELECT ja.assignment_id, ja.fee_held_amount, w.locked_balance
--     FROM jockey_assignment ja
--     JOIN race_entry re            ON re.entry_id = ja.entry_id
--     JOIN tournament_registration r ON r.registration_id = re.registration_id
--     JOIN wallet w                  ON w.user_id = r.owner_user_id
--    WHERE ja.fee_held_at IS NOT NULL
--      AND ja.fee_released_at IS NULL
--      AND ja.fee_paid_at IS NULL;
--
--   -- JOCKEY_FEE must net to zero: every owner debit has its jockey credit
--   SELECT SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END)
--     FROM wallet_transaction WHERE txn_category = 'JOCKEY_FEE';   -- expect 0
