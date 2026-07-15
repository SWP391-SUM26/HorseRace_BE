-- =========================================================
-- MINIMAL SEED DATA
-- Loaded once when the Postgres data volume is empty:
--   docker-entrypoint-initdb.d/01-schema.sql then 02-seed.sql
-- (Also usable by spring.sql.init if that is ever re-enabled.)
--
-- Contains ONLY what the app needs to boot: roles, permissions, the
-- role->permission matrix, and a single ADMIN account. No demo owners,
-- jockeys, horses, tournaments, races, registrations, or results — those
-- come from self-registration / admin provisioning at runtime.
--
-- Login: admin@horserace.local / admin123
-- (password_hash is a real BCrypt of "admin123"; the {bcrypt} prefix tells
--  Spring's DelegatingPasswordEncoder which algorithm to verify with.)
-- =========================================================

INSERT INTO role (role_code, role_name, description, status) VALUES
    ('ADMIN',        'Administrator',  'Full system access',      'ACTIVE'),
    ('HORSE_OWNER',  'Horse Owner',    'Registers and manages horses', 'ACTIVE'),
    ('JOCKEY',       'Jockey',         'Rides horses in races',   'ACTIVE'),
    ('RACE_REFEREE', 'Race Referee',   'Officiates races',        'ACTIVE'),
    ('SPECTATOR',    'Spectator',      'Watches and predicts',    'ACTIVE'),
    ('TRAINER',      'Trainer',        'Trains horses for races',  'ACTIVE'),
    ('VET',          'Veterinarian',   'Performs horse health checks', 'ACTIVE');

-- =========================================================
-- PERMISSIONS (V4) + role -> permission mapping ("phân quyền cho các role")
-- =========================================================
INSERT INTO permission (code, description) VALUES
    ('USER_MANAGE',          'Manage user accounts'),
    ('ROLE_MANAGE',          'Assign roles to users'),
    ('PERMISSION_MANAGE',    'Manage role permissions'),
    ('TOURNAMENT_MANAGE',    'Create/update tournaments and rounds'),
    ('RACE_MANAGE',          'Schedule and arrange races'),
    ('REGISTRATION_APPROVE', 'Approve/reject tournament registrations'),
    ('REFEREE_ASSIGN',       'Assign referees to races'),
    ('RESULT_PUBLISH',       'Publish official race results'),
    ('PREDICTION_OVERSEE',   'Oversee predictions and payouts'),
    ('AUDIT_VIEW',           'View audit logs'),
    ('HORSE_MANAGE_OWN',     'Manage own horses'),
    ('HORSE_REGISTER',       'Register horse into a tournament'),
    ('JOCKEY_HIRE',          'Hire/invite jockeys'),
    ('RACE_ENTRY_CONFIRM',   'Confirm horse participation in a race'),
    ('INVITATION_RESPOND',   'Accept/decline jockey invitations'),
    ('RACE_INSPECT',         'Inspect horses before a race'),
    ('RACE_MONITOR',         'Monitor live races'),
    ('RESULT_RECORD',        'Record race results'),
    ('VIOLATION_RECORD',     'Record and handle violations'),
    ('REPORT_FILE',          'File referee race reports'),
    ('PREDICTION_PLACE',     'Place predictions/bets'),
    ('WALLET_USE',           'Use wallet (deposit/withdraw)'),
    ('NOTIFICATION_VIEW',    'View notifications'),
    ('PROFILE_MANAGE',       'Manage own profile'),
    ('ACCOUNT_APPROVE',      'Review and approve membership/onboarding applications');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r JOIN permission p ON TRUE
WHERE (r.role_code, p.code) IN (
    -- ADMIN: full management
    ('ADMIN','USER_MANAGE'),('ADMIN','ROLE_MANAGE'),('ADMIN','PERMISSION_MANAGE'),
    ('ADMIN','TOURNAMENT_MANAGE'),('ADMIN','RACE_MANAGE'),('ADMIN','REGISTRATION_APPROVE'),
    ('ADMIN','REFEREE_ASSIGN'),('ADMIN','RESULT_PUBLISH'),('ADMIN','PREDICTION_OVERSEE'),
    ('ADMIN','AUDIT_VIEW'),('ADMIN','NOTIFICATION_VIEW'),('ADMIN','PROFILE_MANAGE'),
    -- HORSE_OWNER
    ('HORSE_OWNER','HORSE_MANAGE_OWN'),('HORSE_OWNER','HORSE_REGISTER'),('HORSE_OWNER','JOCKEY_HIRE'),
    ('HORSE_OWNER','RACE_ENTRY_CONFIRM'),('HORSE_OWNER','NOTIFICATION_VIEW'),('HORSE_OWNER','PROFILE_MANAGE'),
    -- JOCKEY
    ('JOCKEY','INVITATION_RESPOND'),('JOCKEY','NOTIFICATION_VIEW'),('JOCKEY','PROFILE_MANAGE'),
    -- RACE_REFEREE
    ('RACE_REFEREE','RACE_INSPECT'),('RACE_REFEREE','RACE_MONITOR'),('RACE_REFEREE','RESULT_RECORD'),
    ('RACE_REFEREE','VIOLATION_RECORD'),('RACE_REFEREE','REPORT_FILE'),('RACE_REFEREE','NOTIFICATION_VIEW'),
    ('RACE_REFEREE','PROFILE_MANAGE'),('RACE_REFEREE','ACCOUNT_APPROVE'),
    -- SPECTATOR
    ('SPECTATOR','PREDICTION_PLACE'),('SPECTATOR','WALLET_USE'),('SPECTATOR','NOTIFICATION_VIEW'),
    ('SPECTATOR','PROFILE_MANAGE')
);

-- The single ADMIN account. role_id MUST be looked up (role ids are gen_random_uuid()).
-- password_hash is a real BCrypt of "admin123" ({bcrypt} prefix -> DelegatingPasswordEncoder).
-- Idempotent: re-running the seed updates the password instead of failing on the UNIQUE(email).
INSERT INTO app_user (role_id, user_code, full_name, email, phone, password_hash, status, kyc_status) VALUES
    ((SELECT role_id FROM role WHERE role_code = 'ADMIN'),
        'USR0001', 'System Admin', 'admin@horserace.local', '0900000001',
        '{bcrypt}$2a$10$VyK/IFv.xkGyxxwColo5feDS2Tq/pBnSLAEP9Fd4TbQIqMwtHkOua', 'ACTIVE', 'VERIFIED')
ON CONFLICT (email) DO UPDATE
    SET password_hash = EXCLUDED.password_hash,
        status        = EXCLUDED.status,
        kyc_status    = EXCLUDED.kyc_status,
        is_deleted    = FALSE;

-- =========================================================
-- HOUSE / ESCROW WALLET (two-sided money flow)
-- Pre-seed the admin's wallet row so it exists BEFORE the first bet. Every bet/payout/refund resolves
-- the house wallet (app.house.user-email -> admin@horserace.local) and locks it FIRST; without this
-- row, two concurrent first-bets could both try to INSERT it and race the wallet.user_id UNIQUE
-- constraint (getOrCreateWallet is a lazy find-or-create with no unique-violation handling) -> a raw
-- 500. Seeding one ACTIVE, zero-balance VND wallet removes that bootstrap race.
-- Idempotent on the wallet.user_id UNIQUE so a re-run of the seed is a no-op.
INSERT INTO wallet (user_id, balance, locked_balance, currency_code, status)
SELECT u.user_id, 0, 0, 'VND', 'ACTIVE'
FROM app_user u
WHERE u.email = 'admin@horserace.local'
ON CONFLICT (user_id) DO NOTHING;
