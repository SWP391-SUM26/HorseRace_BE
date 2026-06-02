-- =========================================================
-- HORSE RACING TOURNAMENT MANAGEMENT SYSTEM
-- PostgreSQL DDL Script  --  V2 (hardened, schema-only)
-- =========================================================
-- Changes vs V1:
--   1. TIMESTAMP        -> TIMESTAMPTZ everywhere (timezone-safe)
--   2. Added CHECK constraints on status / enum columns
--   3. Added CHECK (... >= 0) on all money / quantity columns
--   4. Added UNIQUE business constraints (no duplicate entries/lanes/bets)
--   5. Consistent soft-delete columns where it makes sense
--   6. UUID defaults use built-in gen_random_uuid() (PostgreSQL 13+, no extension)
--
-- NOTE: No functions / procedures / triggers in this file (schema-only).
--   `updated_at` is set to CURRENT_TIMESTAMP only on INSERT. Maintain it in
--   application code instead (e.g. JPA @UpdateTimestamp / @PreUpdate).
-- =========================================================

-- UUID generation uses the built-in gen_random_uuid() (PostgreSQL 13+).
-- No extension required. (The old uuid-ossp / uuid_generate_v4() is not used.)

-- =========================================================
-- CLEAN SLATE  (makes this script safe to re-run)
-- CASCADE also removes dependent foreign keys and indexes.
-- =========================================================
DROP TABLE IF EXISTS password_reset_token     CASCADE;
DROP TABLE IF EXISTS refresh_token            CASCADE;
DROP TABLE IF EXISTS notification             CASCADE;
DROP TABLE IF EXISTS audit_log                CASCADE;
DROP TABLE IF EXISTS attachment               CASCADE;
DROP TABLE IF EXISTS prize                    CASCADE;
DROP TABLE IF EXISTS payout                   CASCADE;
DROP TABLE IF EXISTS prediction               CASCADE;
DROP TABLE IF EXISTS wallet_transaction       CASCADE;
DROP TABLE IF EXISTS payment_transaction      CASCADE;
DROP TABLE IF EXISTS wallet                   CASCADE;
DROP TABLE IF EXISTS referee_report           CASCADE;
DROP TABLE IF EXISTS race_result_version      CASCADE;
DROP TABLE IF EXISTS race_result              CASCADE;
DROP TABLE IF EXISTS referee_assignment       CASCADE;
DROP TABLE IF EXISTS jockey_assignment        CASCADE;
DROP TABLE IF EXISTS race_entry               CASCADE;
DROP TABLE IF EXISTS tournament_registration  CASCADE;
DROP TABLE IF EXISTS race                     CASCADE;
DROP TABLE IF EXISTS tournament               CASCADE;
DROP TABLE IF EXISTS horse                    CASCADE;
DROP TABLE IF EXISTS app_user                 CASCADE;
DROP TABLE IF EXISTS role                     CASCADE;

-- =========================================================
-- ROLE
-- =========================================================
CREATE TABLE role (
    role_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code   VARCHAR(50) UNIQUE NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- USER
-- =========================================================
CREATE TABLE app_user (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id       UUID REFERENCES role(role_id),
    user_code     VARCHAR(50) UNIQUE NOT NULL,
    full_name     VARCHAR(255),
    email         VARCHAR(255) UNIQUE NOT NULL,
    phone         VARCHAR(30),
    password_hash TEXT NOT NULL,
    avatar_url    TEXT,
    status        VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'BANNED')),
    kyc_status    VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                  CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at    TIMESTAMPTZ
);

-- =========================================================
-- REFRESH TOKEN  (DB-stored, rotated, revocable)
-- Stores only a HASH of the opaque refresh token, never the raw value.
-- =========================================================
CREATE TABLE refresh_token (
    token_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES app_user(user_id),
    token_hash           VARCHAR(255) NOT NULL UNIQUE,
    expires_at           TIMESTAMPTZ NOT NULL,
    revoked              BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at           TIMESTAMPTZ,
    replaced_by_token_id UUID,
    user_agent           VARCHAR(255),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- HORSE
-- =========================================================
CREATE TABLE horse (
    horse_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id       UUID REFERENCES app_user(user_id),
    horse_code          VARCHAR(50) UNIQUE NOT NULL,
    name                VARCHAR(255) NOT NULL,
    microchip_no        VARCHAR(100) UNIQUE,
    gender              VARCHAR(30) CHECK (gender IN ('MALE', 'FEMALE', 'GELDING')),
    breed               VARCHAR(100),
    color               VARCHAR(100),
    date_of_birth       DATE,
    weight              NUMERIC(6,2) CHECK (weight IS NULL OR weight > 0),
    origin_country      VARCHAR(100),
    health_status       VARCHAR(50)
                        CHECK (health_status IN ('HEALTHY', 'INJURED', 'QUARANTINE', 'UNFIT')),
    registration_status VARCHAR(50),
    status              VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'RETIRED', 'INACTIVE')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ
);

-- =========================================================
-- TOURNAMENT
-- =========================================================
CREATE TABLE tournament (
    tournament_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_code       VARCHAR(50) UNIQUE NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    description           TEXT,
    start_date            TIMESTAMPTZ,
    end_date              TIMESTAMPTZ,
    registration_open_at  TIMESTAMPTZ,
    registration_close_at TIMESTAMPTZ,
    location              VARCHAR(255),
    status                VARCHAR(50) NOT NULL DEFAULT 'DRAFT'
                          CHECK (status IN ('DRAFT', 'PUBLISHED', 'REGISTRATION_OPEN',
                                            'REGISTRATION_CLOSED', 'ONGOING', 'COMPLETED', 'CANCELLED')),
    created_by_user_id    UUID REFERENCES app_user(user_id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted            BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMPTZ,
    -- date sanity
    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CHECK (registration_close_at IS NULL OR registration_open_at IS NULL
           OR registration_close_at >= registration_open_at)
);

-- =========================================================
-- RACE
-- =========================================================
CREATE TABLE race (
    race_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id        UUID REFERENCES tournament(tournament_id),
    race_code            VARCHAR(50) UNIQUE NOT NULL,
    name                 VARCHAR(255),
    race_type            VARCHAR(50),
    distance_meter       INT CHECK (distance_meter IS NULL OR distance_meter > 0),
    track_condition      VARCHAR(50),
    weather_condition    VARCHAR(50),
    scheduled_start_at   TIMESTAMPTZ,
    actual_start_at      TIMESTAMPTZ,
    actual_end_at        TIMESTAMPTZ,
    prediction_cutoff_at TIMESTAMPTZ,
    max_participants     INT CHECK (max_participants IS NULL OR max_participants > 0),
    status               VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED'
                         CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED', 'RUNNING',
                                           'FINISHED', 'OFFICIAL', 'CANCELLED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (actual_end_at IS NULL OR actual_start_at IS NULL OR actual_end_at >= actual_start_at)
);

-- =========================================================
-- TOURNAMENT REGISTRATION
-- =========================================================
CREATE TABLE tournament_registration (
    registration_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id       UUID REFERENCES app_user(user_id),
    tournament_id       UUID REFERENCES tournament(tournament_id),
    horse_id            UUID REFERENCES horse(horse_id),
    registration_code   VARCHAR(50) UNIQUE NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED'
                        CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW',
                                          'APPROVED', 'REJECTED', 'WITHDRAWN')),
    submitted_at        TIMESTAMPTZ,
    reviewed_at         TIMESTAMPTZ,
    approved_by_user_id UUID REFERENCES app_user(user_id),
    rejection_reason    TEXT,
    legal_basis_ref     VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- a horse can be registered to a given tournament only once
    UNIQUE (tournament_id, horse_id)
);

-- =========================================================
-- RACE ENTRY
-- =========================================================
CREATE TABLE race_entry (
    entry_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    registration_id UUID REFERENCES tournament_registration(registration_id),
    race_id         UUID REFERENCES race(race_id),
    entry_code      VARCHAR(50) UNIQUE NOT NULL,
    entry_no        INT CHECK (entry_no IS NULL OR entry_no > 0),
    lane_no         INT CHECK (lane_no IS NULL OR lane_no > 0),
    status          VARCHAR(50) NOT NULL DEFAULT 'ENTERED'
                    CHECK (status IN ('ENTERED', 'CHECKED_IN', 'SCRATCHED', 'DISQUALIFIED', 'FINISHED')),
    checked_in_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- no duplicate registration in the same race; no shared lane/number
    UNIQUE (race_id, registration_id),
    UNIQUE (race_id, lane_no),
    UNIQUE (race_id, entry_no)
);

-- =========================================================
-- JOCKEY ASSIGNMENT
-- =========================================================
CREATE TABLE jockey_assignment (
    assignment_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id            UUID UNIQUE REFERENCES race_entry(entry_id),
    jockey_user_id      UUID REFERENCES app_user(user_id),
    status              VARCHAR(50) NOT NULL DEFAULT 'INVITED'
                        CHECK (status IN ('INVITED', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    invited_at          TIMESTAMPTZ,
    responded_at        TIMESTAMPTZ,
    assigned_by_user_id UUID REFERENCES app_user(user_id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- REFEREE ASSIGNMENT
-- =========================================================
CREATE TABLE referee_assignment (
    ref_assignment_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id            UUID REFERENCES race(race_id),
    referee_user_id    UUID REFERENCES app_user(user_id),
    panel_role         VARCHAR(50)
                       CHECK (panel_role IN ('CHIEF', 'JUDGE', 'STEWARD', 'TIMEKEEPER', 'OBSERVER')),
    status             VARCHAR(50) NOT NULL DEFAULT 'ASSIGNED'
                       CHECK (status IN ('ASSIGNED', 'CONFIRMED', 'REVOKED')),
    assigned_at        TIMESTAMPTZ,
    created_by_user_id UUID REFERENCES app_user(user_id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- a referee is assigned to a race at most once
    UNIQUE (race_id, referee_user_id)
);

-- =========================================================
-- RACE RESULT
-- =========================================================
CREATE TABLE race_result (
    result_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id             UUID REFERENCES race(race_id),
    entry_id            UUID UNIQUE REFERENCES race_entry(entry_id),
    finish_position     INT CHECK (finish_position IS NULL OR finish_position > 0),
    finish_time_ms      BIGINT CHECK (finish_time_ms IS NULL OR finish_time_ms >= 0),
    score               NUMERIC(10,2),
    current_version_no  INT NOT NULL DEFAULT 1,
    officiality_status  VARCHAR(50) NOT NULL DEFAULT 'PROVISIONAL'
                        CHECK (officiality_status IN ('PROVISIONAL', 'UNDER_REVIEW', 'OFFICIAL', 'AMENDED')),
    approved_by_user_id UUID REFERENCES app_user(user_id),
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- one finishing position per race
    UNIQUE (race_id, finish_position)
);

-- =========================================================
-- RACE RESULT VERSION
-- =========================================================
CREATE TABLE race_result_version (
    result_version_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id           UUID REFERENCES race_result(result_id),
    version_no          INT NOT NULL CHECK (version_no > 0),
    finish_position     INT CHECK (finish_position IS NULL OR finish_position > 0),
    finish_time_ms      BIGINT CHECK (finish_time_ms IS NULL OR finish_time_ms >= 0),
    score               NUMERIC(10,2),
    officiality_status  VARCHAR(50),
    changed_by_user_id  UUID REFERENCES app_user(user_id),
    change_reason       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (result_id, version_no)
);

-- =========================================================
-- REFEREE REPORT
-- =========================================================
CREATE TABLE referee_report (
    report_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id        UUID REFERENCES race(race_id),
    author_user_id UUID REFERENCES app_user(user_id),
    report_type    VARCHAR(50)
                   CHECK (report_type IN ('INCIDENT', 'VIOLATION', 'OBJECTION', 'GENERAL')),
    summary        TEXT,
    decision       TEXT,
    severity_level VARCHAR(50)
                   CHECK (severity_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    report_status  VARCHAR(50) NOT NULL DEFAULT 'DRAFT'
                   CHECK (report_status IN ('DRAFT', 'SUBMITTED', 'REVIEWED', 'CLOSED')),
    submitted_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- WALLET
-- =========================================================
CREATE TABLE wallet (
    wallet_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID UNIQUE REFERENCES app_user(user_id),
    balance        NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    locked_balance NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (locked_balance >= 0),
    currency_code  VARCHAR(10) NOT NULL DEFAULT 'VND',
    status         VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PAYMENT TRANSACTION  (external / gateway)
-- =========================================================
CREATE TABLE payment_transaction (
    payment_txn_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_entity_type VARCHAR(50),
    business_entity_id   UUID,          -- polymorphic: no FK by design
    transaction_type     VARCHAR(50)
                         CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'PAYOUT', 'REFUND')),
    amount               NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
    currency_code        VARCHAR(10) NOT NULL DEFAULT 'VND',
    payment_method       VARCHAR(50),
    payment_status       VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                         CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED')),
    external_txn_ref     VARCHAR(255),
    created_by_user_id   UUID REFERENCES app_user(user_id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- WALLET TRANSACTION  (internal ledger)
-- =========================================================
CREATE TABLE wallet_transaction (
    wallet_txn_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id           UUID REFERENCES wallet(wallet_id),
    entry_type          VARCHAR(20) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    txn_category        VARCHAR(50)
                        CHECK (txn_category IN ('DEPOSIT', 'WITHDRAWAL', 'BET_STAKE',
                                                'BET_PAYOUT', 'PRIZE', 'REFUND', 'ADJUSTMENT')),
    amount              NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
    balance_after       NUMERIC(18,2) NOT NULL CHECK (balance_after >= 0),
    related_entity_type VARCHAR(50),
    related_entity_id   UUID,          -- polymorphic: no FK by design
    payment_txn_id      UUID REFERENCES payment_transaction(payment_txn_id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PREDICTION
-- =========================================================
CREATE TABLE prediction (
    prediction_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id             UUID REFERENCES race(race_id),
    spectator_user_id   UUID REFERENCES app_user(user_id),
    predicted_entry_id  UUID REFERENCES race_entry(entry_id),
    prediction_type     VARCHAR(50)
                        CHECK (prediction_type IN ('WIN', 'PLACE', 'SHOW', 'EXACTA', 'QUINELLA')),
    locked_odds         NUMERIC(10,2) CHECK (locked_odds IS NULL OR locked_odds >= 0),
    stake_amount        NUMERIC(18,2) NOT NULL CHECK (stake_amount > 0),
    potential_payout    NUMERIC(18,2) CHECK (potential_payout IS NULL OR potential_payout >= 0),
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'CONFIRMED', 'WON', 'LOST', 'VOID', 'REFUNDED')),
    submitted_at        TIMESTAMPTZ,
    settled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- one bet of a given type per spectator per race
    UNIQUE (race_id, spectator_user_id, prediction_type)
);

-- =========================================================
-- PAYOUT
-- =========================================================
CREATE TABLE payout (
    payout_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prediction_id      UUID UNIQUE REFERENCES prediction(prediction_id),
    payout_amount      NUMERIC(18,2) NOT NULL CHECK (payout_amount >= 0),
    wallet_txn_id      UUID REFERENCES wallet_transaction(wallet_txn_id),
    status             VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    settled_by_user_id UUID REFERENCES app_user(user_id),
    settled_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PRIZE
-- =========================================================
CREATE TABLE prize (
    prize_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id     UUID REFERENCES tournament(tournament_id),
    race_id           UUID REFERENCES race(race_id),
    prize_code        VARCHAR(50) UNIQUE NOT NULL,
    beneficiary_type  VARCHAR(50)
                      CHECK (beneficiary_type IN ('OWNER', 'JOCKEY', 'HORSE', 'TEAM')),
    rank_position     INT CHECK (rank_position IS NULL OR rank_position > 0),
    prize_amount      NUMERIC(18,2) NOT NULL CHECK (prize_amount >= 0),
    currency_code     VARCHAR(10) NOT NULL DEFAULT 'VND',
    status            VARCHAR(50) NOT NULL DEFAULT 'DRAFT'
                      CHECK (status IN ('DRAFT', 'ANNOUNCED', 'AWARDED', 'PAID', 'CANCELLED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- ATTACHMENT
-- =========================================================
CREATE TABLE attachment (
    attachment_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_entity_type   VARCHAR(50),
    owner_entity_id     UUID,          -- polymorphic: no FK by design
    object_key          TEXT,
    file_name           VARCHAR(255),
    mime_type           VARCHAR(100),
    file_size           BIGINT CHECK (file_size IS NULL OR file_size >= 0),
    sensitivity_level   VARCHAR(50)
                        CHECK (sensitivity_level IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')),
    uploaded_by_user_id UUID REFERENCES app_user(user_id),
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- AUDIT LOG
-- =========================================================
CREATE TABLE audit_log (
    audit_log_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id  UUID REFERENCES app_user(user_id),
    race_id        UUID REFERENCES race(race_id),
    entity_type    VARCHAR(50),
    entity_id      UUID,             -- polymorphic: no FK by design
    action_type    VARCHAR(100),
    old_value_json JSONB,
    new_value_json JSONB,
    ip_address     VARCHAR(100),
    device_info    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- NOTIFICATION
-- =========================================================
CREATE TABLE notification (
    notification_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id UUID REFERENCES app_user(user_id),
    title             VARCHAR(255),
    message           TEXT,
    channel           VARCHAR(50)
                      CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')),
    delivery_status   VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                      CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'READ')),
    sent_at           TIMESTAMPTZ,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PASSWORD RESET TOKEN (6-digit OTP for forgot-password flow)
-- Stores only a HASH of the 6-digit code, never the raw value.
-- =========================================================
CREATE TABLE password_reset_token (
    token_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES app_user(user_id),
    code_hash   VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- INDEXES
-- =========================================================
CREATE INDEX idx_app_user_role_id             ON app_user(role_id);
CREATE INDEX idx_horse_owner_user_id          ON horse(owner_user_id);
CREATE INDEX idx_race_tournament_id           ON race(tournament_id);
CREATE INDEX idx_race_status                  ON race(status);
CREATE INDEX idx_race_schedule                ON race(scheduled_start_at);
CREATE INDEX idx_registration_tournament_id   ON tournament_registration(tournament_id);
CREATE INDEX idx_registration_horse_id        ON tournament_registration(horse_id);
CREATE INDEX idx_race_entry_race_id           ON race_entry(race_id);
CREATE INDEX idx_race_entry_registration_id   ON race_entry(registration_id);
CREATE INDEX idx_jockey_assignment_jockey     ON jockey_assignment(jockey_user_id);
CREATE INDEX idx_referee_assignment_race_id   ON referee_assignment(race_id);
CREATE INDEX idx_race_result_race_id          ON race_result(race_id);
CREATE INDEX idx_prediction_race_id           ON prediction(race_id);
CREATE INDEX idx_prediction_user_id           ON prediction(spectator_user_id);
CREATE INDEX idx_wallet_transaction_wallet_id ON wallet_transaction(wallet_id);
CREATE INDEX idx_payment_transaction_status   ON payment_transaction(payment_status);
CREATE INDEX idx_audit_log_race_id            ON audit_log(race_id);
CREATE INDEX idx_audit_log_entity             ON audit_log(entity_type, entity_id);
CREATE INDEX idx_notification_recipient       ON notification(recipient_user_id);
CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX idx_password_reset_token_user_id ON password_reset_token(user_id);

-- =========================================================
-- END  --  V2 (schema-only)
-- =========================================================
