-- =========================================================
-- HORSE RACING TOURNAMENT MANAGEMENT SYSTEM
-- PostgreSQL DDL Script  --  V4 (complete)
-- =========================================================
-- Thay đổi chính so với V3 (xem spec docs/specs/2026-06-15-...-design.md §4):
--   1. + permission + role_permission : phân quyền fine-grained cho từng role (Admin "phân quyền")
--   2. + betting_pool                 : mô hình cược PARI-MUTUEL (gộp pool, chia theo tỷ lệ)
--   3. prediction: nới UNIQUE (thêm predicted_entry_id) + idempotency_key (chống double-submit)
--   4. payment_transaction: + idempotency_key, gateway_provider, raw_payload(JSONB), wallet_id
--      -> phục vụ cổng thanh toán GIẢ LẬP (mock gateway), nạp/rút idempotent, reconcilable
--   5. notification: + is_read (tách read-state khỏi delivery_status); delivery_status bỏ 'READ'
--
-- Kế thừa nguyên vẹn V3:
--   - tournament_round, jockey_profile, penalty, standing
--   - NOT NULL cho FK nghiệp vụ; cho phép đồng hạng (dead-heat); CHECK prize; index.
--   - schema-only: KHÔNG trigger/function. updated_at do app quản qua @UpdateTimestamp.
--
-- UUID dùng gen_random_uuid() có sẵn (PostgreSQL 13+), không cần extension.
-- =========================================================

-- =========================================================
-- CLEAN SLATE (an toàn khi chạy lại). CASCADE gỡ FK & index phụ thuộc.
-- =========================================================
DROP TABLE IF EXISTS role_permission          CASCADE;
DROP TABLE IF EXISTS permission               CASCADE;
DROP TABLE IF EXISTS betting_pool             CASCADE;
DROP TABLE IF EXISTS email_change_request     CASCADE;
DROP TABLE IF EXISTS email_verification_token CASCADE;
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
DROP TABLE IF EXISTS standing                 CASCADE;
DROP TABLE IF EXISTS penalty                  CASCADE;
DROP TABLE IF EXISTS referee_report           CASCADE;
DROP TABLE IF EXISTS race_result_version      CASCADE;
DROP TABLE IF EXISTS race_result              CASCADE;
DROP TABLE IF EXISTS referee_assignment       CASCADE;
DROP TABLE IF EXISTS jockey_assignment        CASCADE;
DROP TABLE IF EXISTS race_entry               CASCADE;
DROP TABLE IF EXISTS tournament_registration  CASCADE;
DROP TABLE IF EXISTS race                     CASCADE;
DROP TABLE IF EXISTS tournament_round         CASCADE;
DROP TABLE IF EXISTS tournament               CASCADE;
DROP TABLE IF EXISTS horse_characteristic     CASCADE;
DROP TABLE IF EXISTS horse                    CASCADE;
DROP TABLE IF EXISTS jockey_profile           CASCADE;
DROP TABLE IF EXISTS app_user                 CASCADE;
DROP TABLE IF EXISTS role                     CASCADE;

-- =========================================================
-- ROLE  (Horse Owner / Jockey / Race Referee / Spectator / Admin)
-- =========================================================
CREATE TABLE role (
    role_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code   VARCHAR(50) UNIQUE NOT NULL,        -- HORSE_OWNER, JOCKEY, REFEREE, SPECTATOR, ADMIN
    role_name   VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PERMISSION  (NEW) -- quyền hạn fine-grained, gán cho role qua role_permission
-- =========================================================
CREATE TABLE permission (
    permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(100) UNIQUE NOT NULL,   -- vd: TOURNAMENT_MANAGE, RESULT_PUBLISH
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- ROLE_PERMISSION  (NEW) -- bảng nối nhiều-nhiều role <-> permission
-- =========================================================
CREATE TABLE role_permission (
    role_id       UUID NOT NULL REFERENCES role(role_id),
    permission_id UUID NOT NULL REFERENCES permission(permission_id),
    PRIMARY KEY (role_id, permission_id)
);

-- =========================================================
-- USER
-- =========================================================
CREATE TABLE app_user (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id       UUID NOT NULL REFERENCES role(role_id),     -- mỗi user phải có 1 role
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
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at    TIMESTAMPTZ
);

-- =========================================================
-- JOCKEY PROFILE  (NEW) -- thông tin chuyên môn của jockey
-- 1 jockey (app_user role=JOCKEY) <-> 1 hồ sơ. App layer đảm bảo đúng role.
-- =========================================================
CREATE TABLE jockey_profile (
    jockey_user_id UUID PRIMARY KEY REFERENCES app_user(user_id),
    license_no     VARCHAR(100) UNIQUE,
    body_weight    NUMERIC(5,2) CHECK (body_weight IS NULL OR body_weight > 0),  -- kg, dùng cho handicap
    height_cm      NUMERIC(5,2) CHECK (height_cm IS NULL OR height_cm > 0),
    experience_yrs INT CHECK (experience_yrs IS NULL OR experience_yrs >= 0),
    win_count      INT NOT NULL DEFAULT 0 CHECK (win_count >= 0),  -- cache thành tích, cập nhật khi chốt kết quả
    bio            TEXT,
    -- Jockey Market (FE-v2 §2): marketing/stat fields surfaced by GET /jockeys
    rating         NUMERIC(3,1) CHECK (rating IS NULL OR (rating BETWEEN 0 AND 5)),     -- ★ rating, e.g. 4.9
    riding_style   VARCHAR(50),                                                          -- e.g. Stalker / Closer
    win_rate       NUMERIC(5,2) CHECK (win_rate IS NULL OR (win_rate BETWEEN 0 AND 100)),-- win % e.g. 62.50
    recent_form    VARCHAR(50),                                                          -- comma-joined form, e.g. "W,L,W,W,L"
    base_fee       NUMERIC(18,2),                                                        -- hire fee
    prize_percent  NUMERIC(5,2),                                                         -- % of prize taken
    last_trophy    VARCHAR(255),                                                         -- most recent trophy
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- REFRESH TOKEN  (lưu HASH, có thể xoay & thu hồi)
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
-- EMAIL CHANGE REQUEST  (đổi email có xác thực OTP, lưu HASH)
-- =========================================================
CREATE TABLE email_change_request (
    request_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES app_user(user_id),
    new_email    VARCHAR(255) NOT NULL,
    code_hash    VARCHAR(255) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed     BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PASSWORD RESET TOKEN  (OTP 6 số quên mật khẩu, lưu HASH)
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
-- EMAIL VERIFICATION TOKEN  (OTP 6 số xác thực email, lưu HASH)
-- =========================================================
CREATE TABLE email_verification_token (
    token_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES app_user(user_id),
    code_hash   VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- HORSE
-- =========================================================
CREATE TABLE horse (
    horse_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id       UUID NOT NULL REFERENCES app_user(user_id),  -- ngựa phải có chủ
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
    image_url           TEXT,                                         -- V4: ảnh ngựa (served via /api/v1/files)
    last_health_check_at TIMESTAMPTZ,
    medical_note         TEXT,
    -- FE-v2 Horse Profile (mục 1): career stats + pedigree + medical extras
    grade                   VARCHAR(50),
    lifetime_earnings       NUMERIC(18,2) NOT NULL DEFAULT 0,
    sire_name               VARCHAR(255),
    sire_wins               INT,
    sire_earnings           NUMERIC(18,2),
    dam_name                VARCHAR(255),
    dam_wins                INT,
    dam_note                VARCHAR(255),
    trainer_name            VARCHAR(255),
    trainer_license_no      VARCHAR(100),
    vaccinations_up_to_date BOOLEAN NOT NULL DEFAULT FALSE,
    recovery_percent        INT CHECK (recovery_percent IS NULL OR (recovery_percent BETWEEN 0 AND 100)),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ
);

-- FE-v2 Horse Profile (mục 1): horse characteristics tags (@ElementCollection)
CREATE TABLE horse_characteristic (
    horse_id UUID NOT NULL REFERENCES horse(horse_id),
    tag      VARCHAR(100) NOT NULL,
    PRIMARY KEY (horse_id, tag)
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
    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CHECK (registration_close_at IS NULL OR registration_open_at IS NULL
           OR registration_close_at >= registration_open_at)
);

-- =========================================================
-- TOURNAMENT ROUND  (NEW) -- "vòng đua": vòng loại / bán kết / chung kết...
-- =========================================================
CREATE TABLE tournament_round (
    round_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id UUID NOT NULL REFERENCES tournament(tournament_id),
    round_no      INT  NOT NULL CHECK (round_no > 0),
    name          VARCHAR(100),                 -- "Vòng loại 1", "Chung kết"
    stage         VARCHAR(30)
                  CHECK (stage IN ('QUALIFIER', 'HEAT', 'SEMI', 'FINAL')),
    scheduled_at  TIMESTAMPTZ,
    status        VARCHAR(30) NOT NULL DEFAULT 'PLANNED'
                  CHECK (status IN ('PLANNED', 'ONGOING', 'COMPLETED', 'CANCELLED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tournament_id, round_no)
);

-- =========================================================
-- RACE  (cuộc đua, có thể thuộc 1 vòng đua)
-- =========================================================
CREATE TABLE race (
    race_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id        UUID NOT NULL REFERENCES tournament(tournament_id),
    round_id             UUID REFERENCES tournament_round(round_id),  -- nullable: giải có thể không chia vòng
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
    is_deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMPTZ,
    CHECK (actual_end_at IS NULL OR actual_start_at IS NULL OR actual_end_at >= actual_start_at)
);

-- =========================================================
-- TOURNAMENT REGISTRATION  (chủ ngựa đăng ký ngựa dự giải -> admin duyệt)
-- =========================================================
CREATE TABLE tournament_registration (
    registration_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id       UUID NOT NULL REFERENCES app_user(user_id),
    tournament_id       UUID NOT NULL REFERENCES tournament(tournament_id),
    horse_id            UUID NOT NULL REFERENCES horse(horse_id),
    race_id             UUID REFERENCES race(race_id),  -- ngựa chọn cuộc đua khi đăng ký (nullable)
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
    UNIQUE (tournament_id, horse_id)   -- 1 ngựa đăng ký 1 giải chỉ 1 lần
);

-- =========================================================
-- RACE ENTRY  (suất ngựa vào 1 cuộc đua cụ thể)
-- =========================================================
CREATE TABLE race_entry (
    entry_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    registration_id UUID NOT NULL REFERENCES tournament_registration(registration_id),
    race_id         UUID NOT NULL REFERENCES race(race_id),
    entry_code      VARCHAR(50) UNIQUE NOT NULL,
    entry_no        INT CHECK (entry_no IS NULL OR entry_no > 0),
    lane_no         INT CHECK (lane_no IS NULL OR lane_no > 0),
    status          VARCHAR(50) NOT NULL DEFAULT 'ENTERED'
                    CHECK (status IN ('ENTERED', 'CHECKED_IN', 'SCRATCHED', 'DISQUALIFIED', 'FINISHED')),
    checked_in_at   TIMESTAMPTZ,
    prize_earned    NUMERIC(18,2) NOT NULL DEFAULT 0,  -- FE-v2 Horse Profile (mục 1): tiền thưởng mỗi race
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (race_id, registration_id),  -- 1 đăng ký vào 1 cuộc đua chỉ 1 lần
    UNIQUE (race_id, lane_no),          -- không trùng làn
    UNIQUE (race_id, entry_no)          -- không trùng số áo
);

-- =========================================================
-- JOCKEY ASSIGNMENT  (mời/chọn jockey -> jockey accept/decline)
-- =========================================================
CREATE TABLE jockey_assignment (
    assignment_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id            UUID NOT NULL UNIQUE REFERENCES race_entry(entry_id),  -- 1 entry 1 jockey
    jockey_user_id      UUID NOT NULL REFERENCES app_user(user_id),
    status              VARCHAR(50) NOT NULL DEFAULT 'INVITED'
                        CHECK (status IN ('INVITED', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    invited_at          TIMESTAMPTZ,
    responded_at        TIMESTAMPTZ,
    assigned_by_user_id UUID REFERENCES app_user(user_id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- REFEREE ASSIGNMENT  (admin phân công trọng tài cho cuộc đua)
-- =========================================================
CREATE TABLE referee_assignment (
    ref_assignment_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id            UUID NOT NULL REFERENCES race(race_id),
    referee_user_id    UUID NOT NULL REFERENCES app_user(user_id),
    panel_role         VARCHAR(50)
                       CHECK (panel_role IN ('CHIEF', 'JUDGE', 'STEWARD', 'TIMEKEEPER', 'OBSERVER')),
    status             VARCHAR(50) NOT NULL DEFAULT 'ASSIGNED'
                       CHECK (status IN ('ASSIGNED', 'CONFIRMED', 'REVOKED')),
    assigned_at        TIMESTAMPTZ,
    created_by_user_id UUID REFERENCES app_user(user_id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (race_id, referee_user_id)  -- 1 trọng tài / cuộc đua tối đa 1 lần
);

-- =========================================================
-- RACE RESULT  (kết quả hiện hành của mỗi entry)
-- Cho phép đồng hạng (dead-heat): KHÔNG unique trên finish_position.
-- =========================================================
CREATE TABLE race_result (
    result_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id             UUID NOT NULL REFERENCES race(race_id),
    entry_id            UUID NOT NULL UNIQUE REFERENCES race_entry(entry_id),  -- 1 kết quả / entry
    finish_position     INT CHECK (finish_position IS NULL OR finish_position > 0),
    finish_time_ms      BIGINT CHECK (finish_time_ms IS NULL OR finish_time_ms >= 0),
    score               NUMERIC(10,2),
    current_version_no  INT NOT NULL DEFAULT 1 CHECK (current_version_no > 0),
    officiality_status  VARCHAR(50) NOT NULL DEFAULT 'PROVISIONAL'
                        CHECK (officiality_status IN ('PROVISIONAL', 'UNDER_REVIEW', 'OFFICIAL', 'AMENDED')),
    approved_by_user_id UUID REFERENCES app_user(user_id),
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- RACE RESULT VERSION  (lịch sử chỉnh sửa kết quả - audit)
-- =========================================================
CREATE TABLE race_result_version (
    result_version_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id           UUID NOT NULL REFERENCES race_result(result_id),
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
-- REFEREE REPORT  (biên bản thi đấu)
-- =========================================================
CREATE TABLE referee_report (
    report_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id        UUID NOT NULL REFERENCES race(race_id),
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
-- PENALTY  (NEW) -- hình phạt cụ thể gắn với 1 entry, có thể trỏ tới biên bản
-- =========================================================
CREATE TABLE penalty (
    penalty_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id        UUID NOT NULL REFERENCES race(race_id),
    entry_id       UUID REFERENCES race_entry(entry_id),       -- ngựa/đối tượng bị phạt
    report_id      UUID REFERENCES referee_report(report_id),  -- biên bản liên quan
    penalty_type   VARCHAR(50) NOT NULL
                   CHECK (penalty_type IN ('WARNING', 'TIME_PENALTY', 'FINE',
                                           'DISQUALIFICATION', 'SUSPENSION')),
    time_penalty_ms BIGINT CHECK (time_penalty_ms IS NULL OR time_penalty_ms >= 0),
    fine_amount    NUMERIC(18,2) CHECK (fine_amount IS NULL OR fine_amount >= 0),
    reason         TEXT,
    issued_by_user_id UUID REFERENCES app_user(user_id),
    status         VARCHAR(50) NOT NULL DEFAULT 'ISSUED'
                   CHECK (status IN ('ISSUED', 'UPHELD', 'OVERTURNED', 'CANCELLED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- STANDING  (NEW, tùy chọn) -- bảng xếp hạng tích điểm theo giải
-- Có thể xếp hạng theo ngựa hoặc theo jockey -> dùng subject_type/id.
-- =========================================================
CREATE TABLE standing (
    standing_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id UUID NOT NULL REFERENCES tournament(tournament_id),
    subject_type  VARCHAR(20) NOT NULL CHECK (subject_type IN ('HORSE', 'JOCKEY')),
    subject_id    UUID NOT NULL,            -- horse_id hoặc app_user(user_id) tùy subject_type
    total_points  NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (total_points >= 0),
    races_count   INT NOT NULL DEFAULT 0 CHECK (races_count >= 0),
    wins_count    INT NOT NULL DEFAULT 0 CHECK (wins_count >= 0),
    rank_position INT CHECK (rank_position IS NULL OR rank_position > 0),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tournament_id, subject_type, subject_id)
);

-- =========================================================
-- WALLET
-- =========================================================
CREATE TABLE wallet (
    wallet_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL UNIQUE REFERENCES app_user(user_id),
    balance        NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    locked_balance NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (locked_balance >= 0),
    currency_code  VARCHAR(10) NOT NULL DEFAULT 'VND',
    status         VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PAYMENT TRANSACTION  (giao dịch cổng thanh toán ngoài)
-- =========================================================
CREATE TABLE payment_transaction (
    payment_txn_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_entity_type VARCHAR(50),
    business_entity_id   UUID,          -- polymorphic: không FK theo thiết kế
    transaction_type     VARCHAR(50)
                         CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'PAYOUT', 'REFUND')),
    amount               NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
    currency_code        VARCHAR(10) NOT NULL DEFAULT 'VND',
    payment_method       VARCHAR(50),
    payment_status       VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                         CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED')),
    external_txn_ref     VARCHAR(255),
    idempotency_key      VARCHAR(255) UNIQUE,               -- V4: nạp/rút idempotent
    gateway_provider     VARCHAR(50) DEFAULT 'MOCK',        -- V4: cổng thanh toán (giả lập)
    raw_payload          JSONB,                             -- V4: body callback từ gateway giả lập
    wallet_id            UUID REFERENCES wallet(wallet_id), -- V4: ví liên quan (nullable)
    created_by_user_id   UUID REFERENCES app_user(user_id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- WALLET TRANSACTION  (sổ cái nội bộ)
-- =========================================================
CREATE TABLE wallet_transaction (
    wallet_txn_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id           UUID NOT NULL REFERENCES wallet(wallet_id),
    entry_type          VARCHAR(20) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    txn_category        VARCHAR(50)
                        CHECK (txn_category IN ('DEPOSIT', 'WITHDRAWAL', 'BET_STAKE',
                                                'BET_PAYOUT', 'PRIZE', 'REFUND', 'ADJUSTMENT')),
    amount              NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
    balance_after       NUMERIC(18,2) NOT NULL CHECK (balance_after >= 0),
    related_entity_type VARCHAR(50),
    related_entity_id   UUID,          -- polymorphic: không FK theo thiết kế
    payment_txn_id      UUID REFERENCES payment_transaction(payment_txn_id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PREDICTION  (Bet - dự đoán kết quả của khán giả)
-- =========================================================
CREATE TABLE prediction (
    prediction_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id             UUID NOT NULL REFERENCES race(race_id),
    spectator_user_id   UUID NOT NULL REFERENCES app_user(user_id),
    predicted_entry_id  UUID REFERENCES race_entry(entry_id),  -- nullable cho cược tổ hợp (EXACTA...)
    prediction_type     VARCHAR(50)
                        CHECK (prediction_type IN ('WIN', 'PLACE', 'SHOW', 'EXACTA', 'QUINELLA')),
    locked_odds         NUMERIC(10,2) CHECK (locked_odds IS NULL OR locked_odds >= 0),
    stake_amount        NUMERIC(18,2) NOT NULL CHECK (stake_amount > 0),
    potential_payout    NUMERIC(18,2) CHECK (potential_payout IS NULL OR potential_payout >= 0),
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'CONFIRMED', 'WON', 'LOST', 'VOID', 'REFUNDED')),
    submitted_at        TIMESTAMPTZ,
    settled_at          TIMESTAMPTZ,
    idempotency_key     VARCHAR(255) UNIQUE,  -- V4: chống double-submit (client gửi 1 key/lần đặt)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- V4: nới UNIQUE -> cho phép cược nhiều entry khác nhau cùng loại cược; chặn trùng ở service layer
    UNIQUE (race_id, spectator_user_id, prediction_type, predicted_entry_id)
);

-- =========================================================
-- BETTING POOL  (NEW) -- mô hình PARI-MUTUEL: gộp tiền cược theo (cuộc đua, loại cược)
-- Khi settle: payout_i = total_stake*(1 - rake%) * stake_i / Σ(stake của kèo THẮNG).
-- =========================================================
CREATE TABLE betting_pool (
    pool_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id          UUID NOT NULL REFERENCES race(race_id),
    prediction_type  VARCHAR(50) NOT NULL
                     CHECK (prediction_type IN ('WIN', 'PLACE', 'SHOW', 'EXACTA', 'QUINELLA')),
    total_stake      NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (total_stake >= 0),
    rake_percent     NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (rake_percent >= 0 AND rake_percent <= 100),
    status           VARCHAR(30)   NOT NULL DEFAULT 'OPEN'
                     CHECK (status IN ('OPEN', 'CLOSED', 'SETTLED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (race_id, prediction_type)   -- 1 pool / (cuộc đua, loại cược)
);

-- =========================================================
-- PAYOUT  (chi thưởng cho dự đoán thắng)
-- =========================================================
CREATE TABLE payout (
    payout_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prediction_id      UUID NOT NULL UNIQUE REFERENCES prediction(prediction_id),
    payout_amount      NUMERIC(18,2) NOT NULL CHECK (payout_amount >= 0),
    wallet_txn_id      UUID REFERENCES wallet_transaction(wallet_txn_id),
    status             VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    settled_by_user_id UUID REFERENCES app_user(user_id),
    settled_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PRIZE  (tiền thưởng cho chủ ngựa/jockey/ngựa theo thứ hạng)
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
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- prize phải gắn với giải HOẶC cuộc đua (không treo lơ lửng)
    CHECK (tournament_id IS NOT NULL OR race_id IS NOT NULL)
);

-- =========================================================
-- ATTACHMENT  (tài liệu: hồ sơ ngựa, ảnh, biên bản...)
-- =========================================================
CREATE TABLE attachment (
    attachment_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_entity_type   VARCHAR(50),
    owner_entity_id     UUID,          -- polymorphic: không FK theo thiết kế
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
    entity_id      UUID,             -- polymorphic: không FK theo thiết kế
    action_type    VARCHAR(100),
    old_value_json JSONB,
    new_value_json JSONB,
    ip_address     VARCHAR(100),
    device_info    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- NOTIFICATION  (thông báo: thưởng dự đoán, mời jockey, kết quả...)
-- =========================================================
CREATE TABLE notification (
    notification_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id UUID NOT NULL REFERENCES app_user(user_id),
    title             VARCHAR(255),
    message           TEXT,
    channel           VARCHAR(50)
                      CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')),
    delivery_status   VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                      CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),  -- V4: bỏ 'READ'
    is_read           BOOLEAN NOT NULL DEFAULT FALSE,   -- V4: read-state tách khỏi delivery_status
    sent_at           TIMESTAMPTZ,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- INDEXES
-- =========================================================
CREATE INDEX idx_app_user_role_id             ON app_user(role_id);
CREATE INDEX idx_horse_owner_user_id          ON horse(owner_user_id);
CREATE INDEX idx_round_tournament_id          ON tournament_round(tournament_id);
CREATE INDEX idx_race_tournament_id           ON race(tournament_id);
CREATE INDEX idx_race_round_id                ON race(round_id);
CREATE INDEX idx_race_status                  ON race(status);
CREATE INDEX idx_race_schedule                ON race(scheduled_start_at);
CREATE INDEX idx_registration_tournament_id   ON tournament_registration(tournament_id);
CREATE INDEX idx_registration_horse_id        ON tournament_registration(horse_id);
CREATE INDEX idx_registration_owner_id        ON tournament_registration(owner_user_id);
CREATE INDEX idx_race_entry_race_id           ON race_entry(race_id);
CREATE INDEX idx_race_entry_registration_id   ON race_entry(registration_id);
CREATE INDEX idx_jockey_assignment_jockey     ON jockey_assignment(jockey_user_id);
CREATE INDEX idx_referee_assignment_race_id   ON referee_assignment(race_id);
CREATE INDEX idx_referee_assignment_referee   ON referee_assignment(referee_user_id);
CREATE INDEX idx_race_result_race_id          ON race_result(race_id);
CREATE INDEX idx_race_result_position         ON race_result(race_id, finish_position);
CREATE INDEX idx_result_version_result_id     ON race_result_version(result_id);
CREATE INDEX idx_referee_report_race_id       ON referee_report(race_id);
CREATE INDEX idx_penalty_race_id              ON penalty(race_id);
CREATE INDEX idx_penalty_entry_id             ON penalty(entry_id);
CREATE INDEX idx_standing_tournament_id       ON standing(tournament_id);
CREATE INDEX idx_prediction_race_id           ON prediction(race_id);
CREATE INDEX idx_prediction_user_id           ON prediction(spectator_user_id);
CREATE INDEX idx_payout_prediction_id         ON payout(prediction_id);
CREATE INDEX idx_prize_tournament_id          ON prize(tournament_id);
CREATE INDEX idx_prize_race_id                ON prize(race_id);
CREATE INDEX idx_wallet_transaction_wallet_id ON wallet_transaction(wallet_id);
CREATE INDEX idx_payment_transaction_status   ON payment_transaction(payment_status);
CREATE INDEX idx_audit_log_race_id            ON audit_log(race_id);
CREATE INDEX idx_audit_log_entity             ON audit_log(entity_type, entity_id);
CREATE INDEX idx_notification_recipient       ON notification(recipient_user_id);
CREATE INDEX idx_refresh_token_user_id        ON refresh_token(user_id);
CREATE INDEX idx_password_reset_token_user_id ON password_reset_token(user_id);
-- V4 indexes
CREATE INDEX idx_role_permission_permission   ON role_permission(permission_id);
CREATE INDEX idx_betting_pool_race_id         ON betting_pool(race_id);
CREATE INDEX idx_payment_transaction_wallet   ON payment_transaction(wallet_id);
CREATE INDEX idx_notification_recipient_unread ON notification(recipient_user_id, is_read);

-- =========================================================
-- GHI CHÚ: updated_at do tầng ứng dụng quản lý qua Hibernate @UpdateTimestamp
-- (xem users/entity/User.java). File này là schema-only, KHÔNG dùng
-- trigger/function ở tầng DB (tránh xung đột với app và tránh lỗi tách câu
-- lệnh dollar-quote $$ của spring.sql.init lúc khởi động).
-- =========================================================

-- =========================================================
-- END  --  V4 (complete)
-- =========================================================
