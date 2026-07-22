-- =============================================================================
-- Sponsor-funded prizes: PRIZE becomes a two-sided move against the house wallet
-- =============================================================================
-- Until now PRIZE was the only money category minted from nothing: creditPrizes issued a CREDIT to
-- the owner and the jockey with no counterparty debit, so the ledger could not answer "where did
-- the prize money come from". Entry fees never covered it and never could — the house had collected
-- 105,089,000 against 950,000,000 of prizes already awarded.
--
-- The purse is sponsor money. The organiser funds it into the house wallet when a tournament is
-- published, and each certify debits the house as prizes go out.
--
-- Run against a LIVE database that already holds seeded history. schema_v4.sql carries the matching
-- CHECK for a fresh volume; this patch is what fixes a running one (sql.init.mode is off and
-- ddl-auto=validate does not inspect CHECK constraints, so the old 9-value list would survive and
-- every SPONSOR insert would fail).
--
--   docker exec -i horserace_postgres psql -U postgres -d horserace_db \
--     < src/main/resources/db/patches/2026-07-19-sponsor-funded-prizes.sql
-- =============================================================================

BEGIN;

-- 1) Allow the new category ---------------------------------------------------
ALTER TABLE wallet_transaction DROP CONSTRAINT IF EXISTS wallet_transaction_txn_category_check;
ALTER TABLE wallet_transaction ADD CONSTRAINT wallet_transaction_txn_category_check
    CHECK (txn_category IN ('DEPOSIT', 'WITHDRAWAL', 'BET_STAKE', 'BET_PAYOUT', 'PRIZE',
                            'REFUND', 'ADJUSTMENT', 'REWARD', 'ENTRY_FEE', 'SPONSOR'));

-- 2) Sponsor deposits ---------------------------------------------------------
-- One per tournament that has left DRAFT, for its full purse. DRAFT tournaments are funded by
-- publishTournament when they are published, so seeding them here would double-fund them.
--
-- Dated a day BEFORE the earliest existing house row. This ordering is load-bearing: the running
-- balance in step 4 is computed by created_at, and wallet_transaction has CHECK (balance_after >= 0)
-- — sponsor money arriving after the prize debits would drive the chain negative and abort.
INSERT INTO wallet_transaction (wallet_id, entry_type, txn_category, amount, balance_after,
                                related_entity_type, related_entity_id, created_at)
SELECT w.wallet_id, 'CREDIT', 'SPONSOR', t.total_purse, 0,
       'TOURNAMENT_PUBLISH', t.tournament_id,
       (SELECT MIN(wt.created_at) FROM wallet_transaction wt
          JOIN wallet hw ON hw.wallet_id = wt.wallet_id
         WHERE hw.user_id = w.user_id) - INTERVAL '1 day'
  FROM tournament t
 CROSS JOIN (SELECT w2.wallet_id, w2.user_id FROM wallet w2
              JOIN app_user u ON u.user_id = w2.user_id
             WHERE u.email = 'admin@horserace.local') w
 WHERE t.status <> 'DRAFT'
   AND t.is_deleted = false
   AND t.total_purse > 0;

-- 3) Pair every prize already paid with a house debit -------------------------
-- The 18 existing PRIZE CREDITs (owner + jockey cuts) have no counterparty. Insert the matching
-- house DEBIT for each, at the same instant, so the two sides sit together in the ledger.
INSERT INTO wallet_transaction (wallet_id, entry_type, txn_category, amount, balance_after,
                                related_entity_type, related_entity_id, created_at)
SELECT house.wallet_id, 'DEBIT', 'PRIZE', src.amount, 0,
       src.related_entity_type, src.related_entity_id, src.created_at
  FROM wallet_transaction src
 CROSS JOIN (SELECT w.wallet_id FROM wallet w
              JOIN app_user u ON u.user_id = w.user_id
             WHERE u.email = 'admin@horserace.local') house
 WHERE src.txn_category = 'PRIZE'
   AND src.entry_type   = 'CREDIT'
   AND src.wallet_id <> house.wallet_id;

-- 4) Recompute the running balance --------------------------------------------
-- Copied verbatim from the tail of seed_demo.sql — never hand-write balances.
WITH ordered AS (
    SELECT wallet_txn_id,
           SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END)
               OVER (PARTITION BY wallet_id ORDER BY created_at, wallet_txn_id
                     ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
    FROM wallet_transaction
)
UPDATE wallet_transaction wt SET balance_after = o.running_balance
  FROM ordered o WHERE o.wallet_txn_id = wt.wallet_txn_id;

UPDATE wallet w SET balance = COALESCE((
    SELECT wt.balance_after FROM wallet_transaction wt
     WHERE wt.wallet_id = w.wallet_id
     ORDER BY wt.created_at DESC, wt.wallet_txn_id DESC
     LIMIT 1), 0);

-- 5) Invariants — every one must hold, or roll back ---------------------------
DO $$
DECLARE
    negative_rows  BIGINT;
    unpaired_prize BIGINT;
    house_balance  NUMERIC;
    future_owed    NUMERIC;
BEGIN
    SELECT count(*) INTO negative_rows FROM wallet_transaction WHERE balance_after < 0;
    IF negative_rows > 0 THEN
        RAISE EXCEPTION 'Running balance went negative on % row(s)', negative_rows;
    END IF;

    -- PRIZE must now net to zero across all wallets: every credit has its house debit.
    SELECT SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END)
      INTO unpaired_prize FROM wallet_transaction WHERE txn_category = 'PRIZE';
    IF unpaired_prize <> 0 THEN
        RAISE EXCEPTION 'PRIZE does not net to zero: % left over', unpaired_prize;
    END IF;

    -- The house must be able to fund every purse still owed on races not yet run.
    SELECT w.balance INTO house_balance FROM wallet w
      JOIN app_user u ON u.user_id = w.user_id WHERE u.email = 'admin@horserace.local';
    SELECT COALESCE(SUM(total_purse), 0) INTO future_owed FROM race
     WHERE status NOT IN ('OFFICIAL', 'CANCELLED') AND is_deleted = false;
    IF house_balance < future_owed THEN
        RAISE EXCEPTION 'House holds % but still owes % in unrun purses', house_balance, future_owed;
    END IF;

    RAISE NOTICE 'OK — house balance %, future purses owed %', house_balance, future_owed;
END $$;

COMMIT;
