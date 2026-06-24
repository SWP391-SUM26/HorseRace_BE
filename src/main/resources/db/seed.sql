-- =========================================================
-- DEV SEED DATA  (loaded by spring.sql.init.data-locations)
-- Re-applied on every startup together with schema_v2.sql.
--
-- password_hash holds REAL BCrypt hashes ({bcrypt} prefix), so the table shows hashed
-- passwords like a production system. The {bcrypt} prefix tells Spring's
-- DelegatingPasswordEncoder which algorithm to verify with. Plaintext logins below still
-- work because the hash was generated FROM that plaintext:
--     admin@horserace.local / admin123
--     owner@horserace.local / owner123
--     jane@horserace.local  / jane123
-- (Each hash is a salted BCrypt of the password — re-seeding generates the same logins.)
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

INSERT INTO app_user (role_id, user_code, full_name, email, phone, password_hash, status, kyc_status) VALUES
    ((SELECT role_id FROM role WHERE role_code = 'ADMIN'),
        'USR0001', 'System Admin', 'admin@horserace.local', '0900000001',
        '{bcrypt}$2y$10$YiXEZ9vaasQu1BNVmA3XkOMe0pcZaWFl0b.5T6916TEPw4dl.uf..', 'ACTIVE', 'VERIFIED'),
    ((SELECT role_id FROM role WHERE role_code = 'HORSE_OWNER'),
        'USR0002', 'Owen Owner', 'owner@horserace.local', '0900000002',
        '{bcrypt}$2y$10$nWCqnY.SpK9UjGH7YbcqVeioQ4nGh5Yfo7.3YmELXzUh2ztrEJSjC', 'ACTIVE', 'VERIFIED'),
    ((SELECT role_id FROM role WHERE role_code = 'SPECTATOR'),
        'USR0003', 'Jane Spectator', 'jane@horserace.local', '0900000003',
        '{bcrypt}$2y$10$Jmh1fuRSoEIk6k0QW/8p2egPNsx8FWvGu/5VWcvYb0B73iZeeJqOa', 'ACTIVE', 'PENDING');
-- Extra dev admin with a plain (un-prefixed) password. Works because the PasswordEncoder
-- treats un-prefixed hashes as plain text in dev. role_id MUST be looked up (role ids are
-- gen_random_uuid(), different every seed run) — a hardcoded UUID would not match.
INSERT INTO app_user (role_id, user_code, full_name, email, phone, password_hash, status, kyc_status) VALUES
    ((SELECT role_id FROM role WHERE role_code = 'ADMIN'),
        'USR0006', 'System Admin', 'admin@hannn.local', '0900000006',
        '123456', 'ACTIVE', 'VERIFIED');

-- Seed Jockey Users
INSERT INTO app_user (user_id, role_id, user_code, full_name, email, phone, password_hash, status, kyc_status) VALUES
    ('11111111-1111-1111-1111-111111111111', (SELECT role_id FROM role WHERE role_code = 'JOCKEY'),
        'USR0004', 'Alex Mercer', 'alex.mercer@horserace.local', '0900000004',
        'jockey123', 'ACTIVE', 'VERIFIED'),
    ('22222222-2222-2222-2222-222222222222', (SELECT role_id FROM role WHERE role_code = 'JOCKEY'),
        'USR0005', 'Irad Ortiz Jr.', 'irad.ortiz@horserace.local', '0900000005',
        'jockey123', 'ACTIVE', 'VERIFIED');

-- Seed Jockey Profiles (with Jockey Market fields: rating, riding_style, win_rate, recent_form, base_fee, prize_percent, last_trophy)
INSERT INTO jockey_profile (jockey_user_id, license_no, body_weight, height_cm, experience_yrs, win_count, bio,
                            rating, riding_style, win_rate, recent_form, base_fee, prize_percent, last_trophy) VALUES
    ('11111111-1111-1111-1111-111111111111', 'LIC-ALEX-001', 54.50, 165.00, 8, 142, 'Top jockey in the regional division, highly skilled in turf courses.',
        4.7, 'Stalker', 58.50, 'W,L,W,W,L', 12000.00, 10.00, 'Spring Cup 2024'),
    ('22222222-2222-2222-2222-222222222222', 'LIC-IRAD-002', 52.00, 162.00, 10, 230, 'Elite class jockey, multiple champion in various stakes.',
        4.9, 'Closer', 62.50, 'W,W,W,L,W', 18000.00, 12.50, 'Dubai World Cup 2025');

-- =========================================================
-- SEED DATA FOR JOCKEY ASSIGNMENT MODULE
-- (horses, tournaments, races, registrations, entries, assignments)
-- =========================================================

-- Seed Horse Owner (uses existing USR0002 Owen Owner from above)
-- owner_user_id will be looked up by email

-- Seed Horses (owned by Owen Owner)
INSERT INTO horse (horse_id, owner_user_id, horse_code, name, gender, breed, color, date_of_birth, weight, origin_country, health_status, status) VALUES
    ('aaaa1111-aaaa-1111-aaaa-111111111111',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'),
        'HRS0001', 'Midnight Thunder', 'MALE', 'Thoroughbred', 'Dark Bay', '2020-03-15', 480.00, 'USA', 'HEALTHY', 'ACTIVE'),
    ('aaaa2222-aaaa-2222-aaaa-222222222222',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'),
        'HRS0002', 'Silver Bullet', 'MALE', 'Arabian', 'Grey', '2019-07-22', 450.00, 'UAE', 'HEALTHY', 'ACTIVE'),
    ('aaaa3333-aaaa-3333-aaaa-333333333333',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'),
        'HRS0003', 'Sapphire Wind', 'FEMALE', 'Thoroughbred', 'Chestnut', '2021-01-10', 465.00, 'UK', 'HEALTHY', 'ACTIVE'),
    ('aaaa4444-aaaa-4444-aaaa-444444444444',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'),
        'HRS0004', 'Golden Arrow', 'MALE', 'Arabian', 'Palomino', '2020-05-10', 460.00, 'USA', 'HEALTHY', 'ACTIVE');

-- Seed Tournament
INSERT INTO tournament (tournament_id, tournament_code, name, description, start_date, end_date, location, status) VALUES
    ('bbbb1111-bbbb-1111-bbbb-111111111111',
        'TOUR-2024-001', 'Dubai World Cup 2024', 'Premier international horse racing tournament',
        '2024-10-15 09:00:00+07', '2024-11-15 18:00:00+07', 'Dubai', 'ONGOING');

-- Seed Races (belonging to the tournament)
INSERT INTO race (race_id, tournament_id, race_code, name, race_type, distance_meter, track_condition, scheduled_start_at, venue, going_moisture_pct, total_purse, status) VALUES
    ('cccc1111-cccc-1111-cccc-111111111111',
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'RACE-001', 'Belmont Autumn Stakes', 'FLAT', 2000, 'GOOD',
        CURRENT_TIMESTAMP - INTERVAL '30 days', 'Ascot Racecourse, UK', 14, 600000.00, 'SCHEDULED'),
    ('cccc2222-cccc-2222-cccc-222222222222',
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'RACE-002', 'Epsom Derby Qualifier', 'FLAT', 1600, 'GOOD',
        CURRENT_TIMESTAMP - INTERVAL '25 days', NULL, NULL, NULL, 'SCHEDULED');

-- Prize distribution for RACE-001 (1st–4th).
INSERT INTO race_prize_distribution (race_id, place, amount) VALUES
    ('cccc1111-cccc-1111-cccc-111111111111', '1st', 340260.00),
    ('cccc1111-cccc-1111-cccc-111111111111', '2nd', 132000.00),
    ('cccc1111-cccc-1111-cccc-111111111111', '3rd',  78000.00),
    ('cccc1111-cccc-1111-cccc-111111111111', '4th',  49740.00);

-- Seed Tournament Registrations (owner registers horses into tournament)
INSERT INTO tournament_registration (registration_id, owner_user_id, tournament_id, horse_id, registration_code, status, submitted_at) VALUES
    ('dddd1111-dddd-1111-dddd-111111111111',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'),
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'aaaa1111-aaaa-1111-aaaa-111111111111',
        'REG-001', 'APPROVED', CURRENT_TIMESTAMP),
    ('dddd2222-dddd-2222-dddd-222222222222',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'),
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'aaaa2222-aaaa-2222-aaaa-222222222222',
        'REG-002', 'APPROVED', CURRENT_TIMESTAMP),
    ('dddd3333-dddd-3333-dddd-333333333333',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'),
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'aaaa3333-aaaa-3333-aaaa-333333333333',
        'REG-003', 'APPROVED', CURRENT_TIMESTAMP),
    ('dddd4444-dddd-4444-dddd-444444444444',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'),
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'aaaa4444-aaaa-4444-aaaa-444444444444',
        'REG-004', 'APPROVED', CURRENT_TIMESTAMP);

-- Seed Race Entries (horses enter specific races)
INSERT INTO race_entry (entry_id, registration_id, race_id, entry_code, entry_no, lane_no, weight_carried_lbs, recent_form, odds, status) VALUES
    ('eeee1111-eeee-1111-eeee-111111111111',
        'dddd1111-dddd-1111-dddd-111111111111',
        'cccc1111-cccc-1111-cccc-111111111111',
        'ENT-001', 1, 1, 126, '1-2-1-1-3', '5-2', 'ENTERED'),
    ('eeee2222-eeee-2222-eeee-222222222222',
        'dddd2222-dddd-2222-dddd-222222222222',
        'cccc2222-cccc-2222-cccc-222222222222',
        'ENT-002', 2, 2, NULL, NULL, NULL, 'ENTERED'),
    ('eeee3333-eeee-3333-eeee-333333333333',
        'dddd3333-dddd-3333-dddd-333333333333',
        'cccc1111-cccc-1111-cccc-111111111111',
        'ENT-003', 3, 3, 122, '3-1-2-4-1', '7-2', 'ENTERED'),
    ('eeee4444-eeee-4444-eeee-444444444444',
        'dddd4444-dddd-4444-dddd-444444444444',
        'cccc1111-cccc-1111-cccc-111111111111',
        'ENT-004', 4, 4, NULL, NULL, NULL, 'ENTERED');

-- =========================================================
-- FE-v2 Horse Profile (mục 1) — enrich a few horses so the profile page shows real data.
-- =========================================================

-- Career stats + grade + pedigree + medical extras for Midnight Thunder (HRS0001) and Silver Bullet (HRS0002).
UPDATE horse SET
    grade = 'GRADE_1',
    lifetime_earnings = 1842500.00,
    sire_name = 'Storm King', sire_wins = 24, sire_earnings = 4200000.00,
    dam_name = 'Ebony Queen', dam_wins = 12, dam_note = 'Grade 1 Winner',
    trainer_name = 'Marcus Sterling', trainer_license_no = '992',
    vaccinations_up_to_date = TRUE, recovery_percent = NULL,
    -- FE-v2 Registration Management (mục 8): eligibility checklist
    fitness_certified = TRUE,
    fitness_cert_expires_at = CURRENT_TIMESTAMP + INTERVAL '180 days',
    passport_scan_status = 'VALID',
    coggins_test_date = CURRENT_DATE - INTERVAL '90 days'
  WHERE horse_id = 'aaaa1111-aaaa-1111-aaaa-111111111111';

UPDATE horse SET
    grade = 'GRADE_2',
    lifetime_earnings = 760000.00,
    sire_name = 'Desert Mirage', sire_wins = 18, sire_earnings = 2100000.00,
    dam_name = 'Silver Lining', dam_wins = 7, dam_note = 'Multiple stakes placed',
    trainer_name = 'Aisha Rahman', trainer_license_no = '1187',
    vaccinations_up_to_date = TRUE, recovery_percent = 80,
    -- FE-v2 Registration Management (mục 8): eligibility checklist (passport pending)
    fitness_certified = FALSE,
    fitness_cert_expires_at = NULL,
    passport_scan_status = 'MISSING',
    coggins_test_date = CURRENT_DATE - INTERVAL '200 days'
  WHERE horse_id = 'aaaa2222-aaaa-2222-aaaa-222222222222';

-- Characteristic tags (@ElementCollection)
INSERT INTO horse_characteristic (horse_id, tag) VALUES
    ('aaaa1111-aaaa-1111-aaaa-111111111111', 'EARLY_SPRINTER'),
    ('aaaa1111-aaaa-1111-aaaa-111111111111', 'FIRM_TURF'),
    ('aaaa1111-aaaa-1111-aaaa-111111111111', 'CALM'),
    ('aaaa2222-aaaa-2222-aaaa-222222222222', 'CLOSER'),
    ('aaaa2222-aaaa-2222-aaaa-222222222222', 'SOFT_GROUND');

-- Prize earned per race entry (drives lifetimeEarnings + race-history.prizeEarned)
UPDATE race_entry SET prize_earned = 1200000.00 WHERE entry_id = 'eeee1111-eeee-1111-eeee-111111111111'; -- ENT-001 Midnight Thunder
UPDATE race_entry SET prize_earned =  450000.00 WHERE entry_id = 'eeee2222-eeee-2222-eeee-222222222222'; -- ENT-002 Silver Bullet
UPDATE race_entry SET prize_earned =  150000.00 WHERE entry_id = 'eeee3333-eeee-3333-eeee-333333333333'; -- ENT-003 Sapphire Wind

-- FE-v2 Registration Management (mục 8): registration category (drives the category filter)
UPDATE tournament_registration SET category = 'GROUP_1' WHERE registration_id = 'dddd1111-dddd-1111-dddd-111111111111';
UPDATE tournament_registration SET category = 'GROUP_2' WHERE registration_id = 'dddd2222-dddd-2222-dddd-222222222222';
UPDATE tournament_registration SET category = 'GROUP_1' WHERE registration_id = 'dddd3333-dddd-3333-dddd-333333333333';
UPDATE tournament_registration SET category = 'HANDICAP' WHERE registration_id = 'dddd4444-dddd-4444-dddd-444444444444';

-- FE-v2 Live monitor (mục 4): telemetry + clock on Race 1 so GET /races/{id}/live shows real data.
UPDATE race SET
    actual_start_at = CURRENT_TIMESTAMP - INTERVAL '90 seconds',
    wind_speed_kph  = 12.40,
    wind_direction  = 'NW',
    video_feed_url  = 'https://stream.example/race-1.m3u8'
  WHERE race_id = 'cccc1111-cccc-1111-cccc-111111111111';

-- Race results (drive starts/wins/top3 in GET /horses/{id}/stats)
INSERT INTO race_result (result_id, race_id, entry_id, finish_position, current_version_no, officiality_status, published_at) VALUES
    ('a0a0a0a0-0000-0000-0000-000000000001',
        'cccc1111-cccc-1111-cccc-111111111111', 'eeee1111-eeee-1111-eeee-111111111111', 1, 1, 'OFFICIAL', CURRENT_TIMESTAMP), -- Midnight Thunder: win
    ('a0a0a0a0-0000-0000-0000-000000000002',
        'cccc2222-cccc-2222-cccc-222222222222', 'eeee2222-eeee-2222-eeee-222222222222', 2, 1, 'OFFICIAL', CURRENT_TIMESTAMP), -- Silver Bullet: top3
    ('a0a0a0a0-0000-0000-0000-000000000003',
        'cccc1111-cccc-1111-cccc-111111111111', 'eeee3333-eeee-3333-eeee-333333333333', 5, 1, 'OFFICIAL', CURRENT_TIMESTAMP); -- Sapphire Wind: out of top3

-- Seed Jockey Assignments (invitations in various statuses for testing)
INSERT INTO jockey_assignment (assignment_id, entry_id, jockey_user_id, status, invited_at, responded_at, assigned_by_user_id) VALUES
    -- Midnight Thunder → Alex Mercer (ACCEPTED for Race 1)
    ('ffff1111-ffff-1111-ffff-111111111111',
        'eeee1111-eeee-1111-eeee-111111111111',
        '11111111-1111-1111-1111-111111111111',
        'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '2 days',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local')),
    -- Silver Bullet → Irad Ortiz (INVITED/PENDING for Race 2)
    ('ffff2222-ffff-2222-ffff-222222222222',
        'eeee2222-eeee-2222-eeee-222222222222',
        '22222222-2222-2222-2222-222222222222',
        'INVITED', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL,
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local')),
    -- Sapphire Wind → Alex Mercer (DECLINED — Race 1 is now in the past; also gives the Declined tab data)
    ('ffff3333-ffff-3333-ffff-333333333333',
        'eeee3333-eeee-3333-eeee-333333333333',
        '11111111-1111-1111-1111-111111111111',
        'DECLINED', CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '27 days',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'));

-- =========================================================
-- FE-v2 Jockey schedule — a FUTURE race so the jockey's Upcoming + Pending tabs
-- populate (the races above are all in the past and only feed Past Results / Trophy).
-- Relative date so it stays in the future on every re-seed.
-- =========================================================
INSERT INTO race (race_id, tournament_id, race_code, name, race_type, distance_meter, track_condition, scheduled_start_at, venue, going_moisture_pct, total_purse, status) VALUES
    ('cccc3333-cccc-3333-cccc-333333333333',
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'RACE-009', 'Kingsway Mile', 'FLAT', 1600, 'GOOD',
        CURRENT_TIMESTAMP + INTERVAL '14 days', 'Kingsway Park, UK', 12, 800000.00, 'SCHEDULED');

-- Entries for the future race (reuse the owner's existing tournament registrations).
INSERT INTO race_entry (entry_id, registration_id, race_id, entry_code, entry_no, lane_no, weight_carried_lbs, recent_form, odds, status) VALUES
    ('eeee7777-eeee-7777-eeee-777777777777',
        'dddd1111-dddd-1111-dddd-111111111111',   -- Midnight Thunder
        'cccc3333-cccc-3333-cccc-333333333333',
        'ENT-009', 1, 1, 126, '1-2-1-1-3', '3-1', 'ENTERED'),
    ('eeee8888-eeee-8888-eeee-888888888888',
        'dddd2222-dddd-2222-dddd-222222222222',   -- Silver Bullet
        'cccc3333-cccc-3333-cccc-333333333333',
        'ENT-010', 2, 2, 120, '2-1-3', '9-2', 'ENTERED');

-- Alex Mercer on the future race: ACCEPTED ride (→ Upcoming) + INVITED invitation (→ Pending).
INSERT INTO jockey_assignment (assignment_id, entry_id, jockey_user_id, status, invited_at, responded_at, assigned_by_user_id) VALUES
    ('ffff4444-ffff-4444-ffff-444444444444',
        'eeee7777-eeee-7777-eeee-777777777777',
        '11111111-1111-1111-1111-111111111111',
        'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day',
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local')),
    ('ffff5555-ffff-5555-ffff-555555555555',
        'eeee8888-eeee-8888-eeee-888888888888',
        '11111111-1111-1111-1111-111111111111',
        'INVITED', CURRENT_TIMESTAMP - INTERVAL '6 hours', NULL,
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'));

-- =========================================================
-- EXTRA DEMO DATA (V4) — richer dataset for FE list/detail pages.
-- All passwords are un-prefixed plaintext (dev encoder treats them as raw).
-- =========================================================

-- More users: 2 owners + 2 spectators (looked up later by email)
INSERT INTO app_user (role_id, user_code, full_name, email, phone, password_hash, status, kyc_status) VALUES
    ((SELECT role_id FROM role WHERE role_code = 'HORSE_OWNER'),
        'USR0007', 'Maria Stables', 'maria@horserace.local', '0900000007', 'owner123', 'ACTIVE', 'VERIFIED'),
    ((SELECT role_id FROM role WHERE role_code = 'HORSE_OWNER'),
        'USR0008', 'Khalid Al Falah', 'khalid@horserace.local', '0900000008', 'owner123', 'ACTIVE', 'VERIFIED'),
    ((SELECT role_id FROM role WHERE role_code = 'SPECTATOR'),
        'USR0013', 'Sam Watcher', 'sam@horserace.local', '0900000013', 'spec123', 'ACTIVE', 'VERIFIED'),
    ((SELECT role_id FROM role WHERE role_code = 'SPECTATOR'),
        'USR0014', 'Nina Fan', 'nina@horserace.local', '0900000014', 'spec123', 'INACTIVE', 'PENDING');

-- More jockeys + referees (fixed UUIDs so profiles/assignments can reference them)
INSERT INTO app_user (user_id, role_id, user_code, full_name, email, phone, password_hash, status, kyc_status) VALUES
    ('33333333-3333-3333-3333-333333333333', (SELECT role_id FROM role WHERE role_code = 'JOCKEY'),
        'USR0009', 'Frankie Dettori', 'frankie@horserace.local', '0900000009', 'jockey123', 'ACTIVE', 'VERIFIED'),
    ('44444444-4444-4444-4444-444444444444', (SELECT role_id FROM role WHERE role_code = 'JOCKEY'),
        'USR0010', 'Ryan Moore', 'ryan@horserace.local', '0900000010', 'jockey123', 'ACTIVE', 'VERIFIED'),
    ('55555555-5555-5555-5555-555555555555', (SELECT role_id FROM role WHERE role_code = 'RACE_REFEREE'),
        'USR0011', 'Robert Steward', 'robert@horserace.local', '0900000011', 'ref123', 'ACTIVE', 'VERIFIED'),
    ('66666666-6666-6666-6666-666666666666', (SELECT role_id FROM role WHERE role_code = 'RACE_REFEREE'),
        'USR0012', 'Linda Judge', 'linda@horserace.local', '0900000012', 'ref123', 'ACTIVE', 'VERIFIED');

INSERT INTO jockey_profile (jockey_user_id, license_no, body_weight, height_cm, experience_yrs, win_count, bio) VALUES
    ('33333333-3333-3333-3333-333333333333', 'LIC-FRANKIE-003', 53.00, 160.00, 20, 500, 'Legendary jockey with decades of Group 1 wins.'),
    ('44444444-4444-4444-4444-444444444444', 'LIC-RYAN-004', 53.50, 168.00, 15, 320, 'Multiple champion jockey, strong on the big stage.');

-- More horses (owned by Maria, Khalid and Owen) — varied gender/health/status
INSERT INTO horse (horse_id, owner_user_id, horse_code, name, microchip_no, gender, breed, color, date_of_birth, weight, origin_country, health_status, status) VALUES
    ('aaaa0000-0000-0000-0000-000000000005', (SELECT user_id FROM app_user WHERE email='maria@horserace.local'),
        'HRS0005', 'Desert Storm', 'MC-0005', 'MALE', 'Arabian', 'Bay', '2019-04-12', 455.00, 'KSA', 'HEALTHY', 'ACTIVE'),
    ('aaaa0000-0000-0000-0000-000000000006', (SELECT user_id FROM app_user WHERE email='maria@horserace.local'),
        'HRS0006', 'Lunar Eclipse', 'MC-0006', 'FEMALE', 'Thoroughbred', 'Black', '2021-02-18', 470.00, 'IRE', 'HEALTHY', 'ACTIVE'),
    ('aaaa0000-0000-0000-0000-000000000007', (SELECT user_id FROM app_user WHERE email='khalid@horserace.local'),
        'HRS0007', 'Royal Flush', 'MC-0007', 'GELDING', 'Thoroughbred', 'Chestnut', '2018-09-30', 490.00, 'GBR', 'HEALTHY', 'ACTIVE'),
    ('aaaa0000-0000-0000-0000-000000000008', (SELECT user_id FROM app_user WHERE email='khalid@horserace.local'),
        'HRS0008', 'Sandstorm', 'MC-0008', 'MALE', 'Arabian', 'Grey', '2020-11-05', 460.00, 'UAE', 'INJURED', 'ACTIVE'),
    ('aaaa0000-0000-0000-0000-000000000009', (SELECT user_id FROM app_user WHERE email='owner@horserace.local'),
        'HRS0009', 'Old Glory', 'MC-0009', 'MALE', 'Thoroughbred', 'Bay', '2015-06-20', 500.00, 'USA', 'HEALTHY', 'RETIRED'),
    ('aaaa0000-0000-0000-0000-000000000010', (SELECT user_id FROM app_user WHERE email='owner@horserace.local'),
        'HRS0010', 'Misty Dawn', 'MC-0010', 'FEMALE', 'Arabian', 'White', '2022-03-08', 430.00, 'USA', 'QUARANTINE', 'INACTIVE'),
    ('aaaa0000-0000-0000-0000-000000000011', (SELECT user_id FROM app_user WHERE email='maria@horserace.local'),
        'HRS0011', 'Thunderbolt', 'MC-0011', 'MALE', 'Thoroughbred', 'Dark Bay', '2019-12-01', 485.00, 'AUS', 'HEALTHY', 'ACTIVE'),
    ('aaaa0000-0000-0000-0000-000000000012', (SELECT user_id FROM app_user WHERE email='khalid@horserace.local'),
        'HRS0012', 'Comet Tail', 'MC-0012', 'FEMALE', 'Arabian', 'Palomino', '2021-08-14', 445.00, 'UAE', 'UNFIT', 'ACTIVE');

-- More tournaments (varied statuses)
INSERT INTO tournament (tournament_id, tournament_code, name, description, start_date, end_date, registration_open_at, registration_close_at, location, status, created_by_user_id) VALUES
    ('bbbb0000-0000-0000-0000-000000000002', 'TOUR-2025-002', 'Royal Ascot 2025', 'Prestigious British flat racing festival.',
        '2025-06-17 13:00:00+07', '2025-06-21 19:00:00+07', NULL, NULL, 'Ascot, UK', 'PUBLISHED',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),
    ('bbbb0000-0000-0000-0000-000000000003', 'TOUR-2025-003', 'Kentucky Derby 2025', 'The most exciting two minutes in sports.',
        '2025-05-03 02:00:00+07', '2025-05-03 09:00:00+07', '2025-03-01 00:00:00+07', '2025-04-20 23:59:00+07', 'Louisville, USA', 'REGISTRATION_OPEN',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),
    ('bbbb0000-0000-0000-0000-000000000004', 'TOUR-2024-004', 'Melbourne Cup 2024', 'The race that stops a nation.',
        '2024-11-05 10:00:00+07', '2024-11-05 16:00:00+07', NULL, NULL, 'Melbourne, AUS', 'COMPLETED',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),
    ('bbbb0000-0000-0000-0000-000000000005', 'TOUR-2025-005', 'Saudi Cup 2025', 'The world''s richest horse race.',
        '2025-02-22 20:00:00+07', '2025-02-22 23:30:00+07', NULL, NULL, 'Riyadh, KSA', 'DRAFT',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local'));

-- More races (varied statuses across tournaments)
INSERT INTO race (race_id, tournament_id, race_code, name, race_type, distance_meter, track_condition, scheduled_start_at, actual_start_at, actual_end_at, status) VALUES
    ('cccc0000-0000-0000-0000-000000000003', 'bbbb0000-0000-0000-0000-000000000002', 'RACE-003', 'Ascot Gold Cup', 'FLAT', 4000, 'GOOD', CURRENT_TIMESTAMP + INTERVAL '10 days', NULL, NULL, 'OPEN'),
    ('cccc0000-0000-0000-0000-000000000004', 'bbbb0000-0000-0000-0000-000000000002', 'RACE-004', 'Queen Anne Stakes', 'FLAT', 1600, 'FIRM', CURRENT_TIMESTAMP + INTERVAL '7 days', NULL, NULL, 'SCHEDULED'),
    ('cccc0000-0000-0000-0000-000000000005', 'bbbb0000-0000-0000-0000-000000000003', 'RACE-005', 'Derby Trial', 'FLAT', 2000, 'GOOD', CURRENT_TIMESTAMP + INTERVAL '21 days', NULL, NULL, 'SCHEDULED'),
    ('cccc0000-0000-0000-0000-000000000006', 'bbbb0000-0000-0000-0000-000000000004', 'RACE-006', 'Melbourne Cup Final', 'FLAT', 3200, 'SOFT', '2024-11-05 11:00:00+07', '2024-11-05 11:02:00+07', '2024-11-05 11:05:30+07', 'FINISHED'),
    ('cccc0000-0000-0000-0000-000000000007', 'bbbb0000-0000-0000-0000-000000000004', 'RACE-007', 'Lightning Stakes', 'FLAT', 1000, 'GOOD', '2024-11-05 10:00:00+07', '2024-11-05 10:01:00+07', '2024-11-05 10:02:10+07', 'OFFICIAL'),
    ('cccc0000-0000-0000-0000-000000000008', 'bbbb1111-bbbb-1111-bbbb-111111111111', 'RACE-008', 'Dubai Sprint', 'FLAT', 1200, 'GOOD', '2024-10-30 15:00:00+07', NULL, NULL, 'CANCELLED');

-- More registrations (varied statuses — drives the approval queue UI)
INSERT INTO tournament_registration (registration_id, owner_user_id, tournament_id, horse_id, registration_code, status, submitted_at, reviewed_at, approved_by_user_id, rejection_reason) VALUES
    ('dddd0000-0000-0000-0000-000000000005', (SELECT user_id FROM app_user WHERE email='maria@horserace.local'),
        'bbbb0000-0000-0000-0000-000000000002', 'aaaa0000-0000-0000-0000-000000000005', 'REG-005', 'SUBMITTED', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL, NULL, NULL),
    ('dddd0000-0000-0000-0000-000000000006', (SELECT user_id FROM app_user WHERE email='maria@horserace.local'),
        'bbbb0000-0000-0000-0000-000000000002', 'aaaa0000-0000-0000-0000-000000000006', 'REG-006', 'UNDER_REVIEW', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL, NULL, NULL),
    ('dddd0000-0000-0000-0000-000000000007', (SELECT user_id FROM app_user WHERE email='khalid@horserace.local'),
        'bbbb0000-0000-0000-0000-000000000002', 'aaaa0000-0000-0000-0000-000000000007', 'REG-007', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '4 days',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), NULL),
    ('dddd0000-0000-0000-0000-000000000008', (SELECT user_id FROM app_user WHERE email='khalid@horserace.local'),
        'bbbb0000-0000-0000-0000-000000000002', 'aaaa0000-0000-0000-0000-000000000008', 'REG-008', 'REJECTED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '4 days',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), 'Horse flagged INJURED at vet inspection.'),
    ('dddd0000-0000-0000-0000-000000000009', (SELECT user_id FROM app_user WHERE email='maria@horserace.local'),
        'bbbb0000-0000-0000-0000-000000000003', 'aaaa0000-0000-0000-0000-000000000011', 'REG-009', 'SUBMITTED', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL, NULL, NULL),
    ('dddd0000-0000-0000-0000-000000000010', (SELECT user_id FROM app_user WHERE email='khalid@horserace.local'),
        'bbbb0000-0000-0000-0000-000000000003', 'aaaa0000-0000-0000-0000-000000000012', 'REG-010', 'SUBMITTED', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL, NULL, NULL),
    ('dddd0000-0000-0000-0000-000000000011', (SELECT user_id FROM app_user WHERE email='maria@horserace.local'),
        'bbbb0000-0000-0000-0000-000000000003', 'aaaa0000-0000-0000-0000-000000000005', 'REG-011', 'WITHDRAWN', CURRENT_TIMESTAMP - INTERVAL '6 days', NULL, NULL, NULL),
    ('dddd0000-0000-0000-0000-000000000012', (SELECT user_id FROM app_user WHERE email='owner@horserace.local'),
        'bbbb0000-0000-0000-0000-000000000004', 'aaaa0000-0000-0000-0000-000000000009', 'REG-012', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '19 days',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), NULL);

-- More race entries (from APPROVED registrations into races of the same tournament)
INSERT INTO race_entry (entry_id, registration_id, race_id, entry_code, entry_no, lane_no, status) VALUES
    ('eeee0000-0000-0000-0000-000000000005', 'dddd0000-0000-0000-0000-000000000007', 'cccc0000-0000-0000-0000-000000000003', 'ENT-005', 1, 1, 'ENTERED'),
    ('eeee0000-0000-0000-0000-000000000006', 'dddd0000-0000-0000-0000-000000000012', 'cccc0000-0000-0000-0000-000000000006', 'ENT-006', 1, 1, 'FINISHED'),
    ('eeee0000-0000-0000-0000-000000000007', 'dddd3333-dddd-3333-dddd-333333333333', 'cccc2222-cccc-2222-cccc-222222222222', 'ENT-007', 1, 1, 'ENTERED'),
    ('eeee0000-0000-0000-0000-000000000008', 'dddd4444-dddd-4444-dddd-444444444444', 'cccc2222-cccc-2222-cccc-222222222222', 'ENT-008', 3, 3, 'ENTERED');

-- Referee assignments (so staffing pages show data)
INSERT INTO referee_assignment (ref_assignment_id, race_id, referee_user_id, panel_role, status, assigned_at, created_by_user_id) VALUES
    ('ffff0000-0000-0000-0000-000000000001', 'cccc1111-cccc-1111-cccc-111111111111', '55555555-5555-5555-5555-555555555555', 'CHIEF', 'ASSIGNED', CURRENT_TIMESTAMP - INTERVAL '3 days',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),
    ('ffff0000-0000-0000-0000-000000000002', 'cccc1111-cccc-1111-cccc-111111111111', '66666666-6666-6666-6666-666666666666', 'JUDGE', 'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '3 days',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),
    ('ffff0000-0000-0000-0000-000000000003', 'cccc0000-0000-0000-0000-000000000006', '55555555-5555-5555-5555-555555555555', 'CHIEF', 'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '21 days',
        (SELECT user_id FROM app_user WHERE email='admin@horserace.local'));

-- =========================================================
-- SEED DATA FOR WALLET SYSTEM
-- =========================================================
INSERT INTO wallet (wallet_id, user_id, balance, locked_balance, currency_code, status) VALUES
    ('20000000-0000-0000-0000-000000000001', (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), 0.00, 0.00, 'VND', 'ACTIVE'),
    ('20000000-0000-0000-0000-000000000002', (SELECT user_id FROM app_user WHERE email='owner@horserace.local'), 10000000.00, 0.00, 'VND', 'ACTIVE'),
    ('20000000-0000-0000-0000-000000000003', (SELECT user_id FROM app_user WHERE email='jane@horserace.local'), 500000.00, 0.00, 'VND', 'ACTIVE'),
    ('20000000-0000-0000-0000-000000000004', (SELECT user_id FROM app_user WHERE email='maria@horserace.local'), 20000000.00, 0.00, 'VND', 'ACTIVE'),
    ('20000000-0000-0000-0000-000000000005', (SELECT user_id FROM app_user WHERE email='khalid@horserace.local'), 30000000.00, 0.00, 'VND', 'ACTIVE');

-- =========================================================
-- SEED DATA FOR REWARD SYSTEM
-- =========================================================
INSERT INTO reward (reward_id, user_id, reward_type, amount, title, description, status) VALUES
    ('10000000-0000-0000-0000-000000000001', (SELECT user_id FROM app_user WHERE email='jane@horserace.local'),
        'DAILY_LOGIN', 50000.00, 'Daily Login Reward', 'Reward for logging in today.', 'PENDING'),
    ('10000000-0000-0000-0000-000000000002', (SELECT user_id FROM app_user WHERE email='jane@horserace.local'),
        'MILESTONE', 200000.00, '10th Prediction Milestone', 'Reward for placing 10 predictions.', 'CLAIMED');

-- =========================================================
-- FE-v2 Referee demo data — inspections, violations, result times + race telemetry
-- so the Referee portal pages render rich content. Belmont Autumn Stakes = cccc1111
-- (entries: eeee1111 Midnight Thunder, eeee3333 Sapphire Wind, eeee4444 Golden Arrow).
-- =========================================================

-- Pre-race inspections for Belmont's runners (varied states: CLEARED / VET_CHECK / PENDING).
INSERT INTO race_entry_inspection (inspection_id, entry_id, race_id, health_cert_passed, weight_verified, weight_carried_lbs, coggins_test_passed, pre_race_exam_passed, inspection_status, steward_note, inspected_by_user_id, inspected_at) VALUES
    ('a1a1a1a1-0000-0000-0000-000000000001', 'eeee1111-eeee-1111-eeee-111111111111', 'cccc1111-cccc-1111-cccc-111111111111',
        TRUE, TRUE, 126, TRUE, TRUE, 'CLEARED', 'Sound on the trot-up. Cleared to race.', '55555555-5555-5555-5555-555555555555', CURRENT_TIMESTAMP - INTERVAL '40 minutes'),
    ('a1a1a1a1-0000-0000-0000-000000000002', 'eeee3333-eeee-3333-eeee-333333333333', 'cccc1111-cccc-1111-cccc-111111111111',
        FALSE, TRUE, 122, TRUE, FALSE, 'VET_CHECK', 'Acting fractious in the paddock — vet review requested.', '55555555-5555-5555-5555-555555555555', CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
    ('a1a1a1a1-0000-0000-0000-000000000003', 'eeee4444-eeee-4444-eeee-444444444444', 'cccc1111-cccc-1111-cccc-111111111111',
        TRUE, FALSE, NULL, FALSE, FALSE, 'PENDING', NULL, NULL, NULL);

-- Violations for Belmont (Violation Log + Inquiry Details): 2 pending + 1 resolved.
INSERT INTO race_violation (violation_id, race_id, entry_id, jockey_user_id, infraction_type, severity, turn_no, race_time_offset_ms, remarks, regulatory_ref, status, reported_by_user_id) VALUES
    ('b1b1b1b1-0000-0000-0000-000000000001', 'cccc1111-cccc-1111-cccc-111111111111', 'eeee3333-eeee-3333-eeee-333333333333', '11111111-1111-1111-1111-111111111111',
        'WHIP_USAGE', 'CRITICAL', 3, 72040, 'Jockey appeared to strike the horse aggressively and repeatedly entering Turn 3, exceeding the operational limit.', 'Rule 4.2.1 - Whip Limitations', 'PENDING', '55555555-5555-5555-5555-555555555555'),
    ('b1b1b1b1-0000-0000-0000-000000000002', 'cccc1111-cccc-1111-cccc-111111111111', 'eeee4444-eeee-4444-eeee-444444444444', NULL,
        'INTERFERENCE', 'MEDIUM', 5, 105220, 'Impeded progress of a rival on the home stretch.', 'Rule 6.1 - Interference', 'PENDING', '55555555-5555-5555-5555-555555555555');
INSERT INTO race_violation (violation_id, race_id, entry_id, jockey_user_id, infraction_type, severity, turn_no, race_time_offset_ms, remarks, regulatory_ref, status, reported_by_user_id, decision_type, ruling_notes, ruled_by_user_id, ruled_at) VALUES
    ('b1b1b1b1-0000-0000-0000-000000000003', 'cccc1111-cccc-1111-cccc-111111111111', 'eeee1111-eeee-1111-eeee-111111111111', '22222222-2222-2222-2222-222222222222',
        'BUMPING', 'LOW', 2, 40000, 'Minor contact at the start, no material effect.', 'Rule 6.3 - Bumping', 'RESOLVED', '55555555-5555-5555-5555-555555555555', 'WARNING', 'Warning issued; no change to the result.', '55555555-5555-5555-5555-555555555555', CURRENT_TIMESTAMP - INTERVAL '10 minutes');

-- Finish times + margins for Belmont results (Results page shows real times instead of —).
UPDATE race_result SET finish_time_ms = 94200, lengths_behind = 0.00 WHERE result_id = 'a0a0a0a0-0000-0000-0000-000000000001';
UPDATE race_result SET finish_time_ms = 95050, lengths_behind = 6.50 WHERE result_id = 'a0a0a0a0-0000-0000-0000-000000000003';

-- Race telemetry / photofinish for Belmont (Results conditions + Live monitor).
UPDATE race SET wind_speed_kph = 12.40, wind_direction = 'NW', track_bias = 'Inside Rail Advantage',
    photofinish_url = '/api/v1/files/photofinish-belmont.jpg', video_feed_url = 'https://stream.example/belmont.m3u8'
  WHERE race_id = 'cccc1111-cccc-1111-cccc-111111111111';

-- Digital-passport fields for Belmont's runners (Pre-Race Inspection passport panel).
UPDATE horse SET microchip_no = '981020012345678', trainer_name = COALESCE(trainer_name, 'T. Pletcher') WHERE horse_id = 'aaaa1111-aaaa-1111-aaaa-111111111111';
UPDATE horse SET microchip_no = '985112003456789', trainer_name = COALESCE(trainer_name, 'A. O''Brien') WHERE horse_id = 'aaaa3333-aaaa-3333-aaaa-333333333333';
UPDATE horse SET microchip_no = '981020087654321', trainer_name = COALESCE(trainer_name, 'C. Appleby') WHERE horse_id = 'aaaa4444-aaaa-4444-aaaa-444444444444';
-- =========================================================
-- COMPREHENSIVE INTERCONNECTED DEMO DATASET (appended)
-- Every role/portal manually testable. Canonical logins (password = 123456):
--   admin@test.local / owner@test.local / jockey@test.local
--   referee@test.local / spectator@test.local
-- All app_user rows below use password_hash '{noop}123456'.
-- New PK UUID prefixes (collision-free vs existing): 7..a for users,
--   a5a5(horse) b5b5(tournament) c5c5(round) c6c6(race) d5d5(registration)
--   e5e5(entry) f5f5(jockey_assignment) a6a6(inspection) a7a7(result)
--   a8a8(result_version) b6b6(violation) b7b7(referee_assignment)
--   b8b8(referee_report) b9b9(penalty) 30000000(wallet) 31000000(payment_txn)
--   32000000(wallet_txn) 33000000(prediction) 34000000(betting_pool)
--   35000000(payout) 36000000(prize) 37000000(standing) 38000000(reward)
--   39000000(notification) 3a000000(attachment) 3b000000(audit_log)
--   3c000000(refresh_token) 3d000000(email_verif) 3e000000(email_change)
--   3f000000(pwd_reset)
-- =========================================================

-- ---------- APP_USER : canonical + extras (all {noop}123456) ----------
INSERT INTO app_user (user_id, role_id, user_code, full_name, email, phone, password_hash, status, kyc_status, email_verified) VALUES
    -- canonical one-per-role
    ('77777777-0000-0000-0000-000000000001', (SELECT role_id FROM role WHERE role_code='ADMIN'),
        'USR1001', 'Canonical Admin',     'admin@test.local',     '0911000001', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('77777777-0000-0000-0000-000000000002', (SELECT role_id FROM role WHERE role_code='HORSE_OWNER'),
        'USR1002', 'Canonical Owner',     'owner@test.local',     '0911000002', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('77777777-0000-0000-0000-000000000003', (SELECT role_id FROM role WHERE role_code='JOCKEY'),
        'USR1003', 'Canonical Jockey',    'jockey@test.local',    '0911000003', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('77777777-0000-0000-0000-000000000004', (SELECT role_id FROM role WHERE role_code='RACE_REFEREE'),
        'USR1004', 'Canonical Referee',   'referee@test.local',   '0911000004', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('77777777-0000-0000-0000-000000000005', (SELECT role_id FROM role WHERE role_code='SPECTATOR'),
        'USR1005', 'Canonical Spectator', 'spectator@test.local', '0911000005', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    -- extra owners
    ('88888888-0000-0000-0000-000000000001', (SELECT role_id FROM role WHERE role_code='HORSE_OWNER'),
        'USR1006', 'Diana Bloodstock', 'diana.owner@test.local', '0911000006', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('88888888-0000-0000-0000-000000000002', (SELECT role_id FROM role WHERE role_code='HORSE_OWNER'),
        'USR1007', 'Hiroshi Tanaka',   'hiroshi.owner@test.local','0911000007', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    -- extra jockeys
    ('99999999-0000-0000-0000-000000000001', (SELECT role_id FROM role WHERE role_code='JOCKEY'),
        'USR1008', 'Lanfranco Vega', 'lanfranco.jockey@test.local','0911000008', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('99999999-0000-0000-0000-000000000002', (SELECT role_id FROM role WHERE role_code='JOCKEY'),
        'USR1009', 'Mickael Barboza','mickael.jockey@test.local','0911000009', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('99999999-0000-0000-0000-000000000003', (SELECT role_id FROM role WHERE role_code='JOCKEY'),
        'USR1015', 'Pierre Lemaire','pierre.jockey@test.local','0911000015', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('99999999-0000-0000-0000-000000000004', (SELECT role_id FROM role WHERE role_code='JOCKEY'),
        'USR1016', 'Joao Moreira',  'joao.jockey@test.local',  '0911000016', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('99999999-0000-0000-0000-000000000005', (SELECT role_id FROM role WHERE role_code='JOCKEY'),
        'USR1017', 'Yutaka Take',   'yutaka.jockey@test.local','0911000017', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    -- extra referees
    ('aaaaaaaa-0000-0000-0000-000000000001', (SELECT role_id FROM role WHERE role_code='RACE_REFEREE'),
        'USR1010', 'Grace Timekeeper','grace.ref@test.local',   '0911000010', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('aaaaaaaa-0000-0000-0000-000000000002', (SELECT role_id FROM role WHERE role_code='RACE_REFEREE'),
        'USR1011', 'Paul Observer',  'paul.ref@test.local',     '0911000011', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    -- extra spectators
    ('bbbbbbbb-0000-0000-0000-000000000001', (SELECT role_id FROM role WHERE role_code='SPECTATOR'),
        'USR1012', 'Tom Punter',  'tom.spec@test.local',  '0911000012', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('bbbbbbbb-0000-0000-0000-000000000002', (SELECT role_id FROM role WHERE role_code='SPECTATOR'),
        'USR1013', 'Lily Bettor', 'lily.spec@test.local', '0911000013', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE),
    ('bbbbbbbb-0000-0000-0000-000000000003', (SELECT role_id FROM role WHERE role_code='SPECTATOR'),
        'USR1014', 'Max Viewer',  'max.spec@test.local',  '0911000014', '{noop}123456', 'ACTIVE', 'VERIFIED', TRUE);

-- ---------- JOCKEY_PROFILE for new jockeys (reach >=10) ----------
INSERT INTO jockey_profile (jockey_user_id, license_no, body_weight, height_cm, experience_yrs, win_count, bio, rating, riding_style, win_rate, recent_form, base_fee, prize_percent, last_trophy) VALUES
    ('77777777-0000-0000-0000-000000000003', 'LIC-CANON-010', 54.00, 166.00, 6, 88,  'Canonical demo jockey for portal testing.',          4.5, 'Stalker', 51.20, 'W,L,W,L,W', 9000.00, 9.00,  'Demo Stakes 2025'),
    ('99999999-0000-0000-0000-000000000001', 'LIC-LANF-011',  52.50, 161.00, 12, 260, 'Consistent front-runner, strong in classics.',       4.8, 'Front-runner', 60.10, 'W,W,L,W,W', 15000.00, 11.00, 'Autumn Classic 2025'),
    ('99999999-0000-0000-0000-000000000002', 'LIC-MICK-012',  53.20, 164.00, 9,  175, 'Tactical closer with excellent big-field record.',   4.6, 'Closer', 55.40, 'L,W,W,L,W', 11000.00, 10.50, 'City Sprint 2024'),
    ('99999999-0000-0000-0000-000000000003', 'LIC-PIER-013',  51.80, 160.00, 14, 300, 'Classic specialist, calm under pressure.',           4.9, 'Stalker', 64.00, 'W,W,W,W,L', 17000.00, 12.00, 'Prix Demo 2025'),
    ('99999999-0000-0000-0000-000000000004', 'LIC-JOAO-014',  52.20, 163.00, 13, 285, 'Magic man, exceptional tactical awareness.',         4.8, 'Closer', 61.50, 'W,L,W,W,W', 16000.00, 11.50, 'City Mile 2025'),
    ('99999999-0000-0000-0000-000000000005', 'LIC-YUTK-015',  53.00, 162.00, 22, 410, 'Legendary rider with countless big-race wins.',      4.9, 'Front-runner', 63.20, 'W,W,L,W,W', 18500.00, 12.50, 'Grand Demo 2024');

-- ---------- WALLETS for new users (reach >=10) ----------
INSERT INTO wallet (wallet_id, user_id, balance, locked_balance, currency_code, status) VALUES
    ('30000000-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000001', 1000000.00,   0.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002', 15000000.00,  0.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000003', 3000000.00,   0.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000004', 500000.00,    0.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000005', 2000000.00,   50000.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000001', 8000000.00,   0.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000002', 6000000.00,   0.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-000000000008', 'bbbbbbbb-0000-0000-0000-000000000001', 1200000.00,   30000.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-000000000009', 'bbbbbbbb-0000-0000-0000-000000000002', 900000.00,    0.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-00000000000a', 'bbbbbbbb-0000-0000-0000-000000000003', 750000.00,    0.00, 'VND', 'FROZEN'),
    ('30000000-0000-0000-0000-00000000000b', '99999999-0000-0000-0000-000000000001', 400000.00,    0.00, 'VND', 'ACTIVE'),
    ('30000000-0000-0000-0000-00000000000c', '99999999-0000-0000-0000-000000000002', 350000.00,    0.00, 'VND', 'ACTIVE');

-- ---------- HORSES owned by new canonical/extra owners ----------
INSERT INTO horse (horse_id, owner_user_id, horse_code, name, microchip_no, gender, breed, color, date_of_birth, weight, origin_country, health_status, status, grade, lifetime_earnings, vaccinations_up_to_date, fitness_certified, passport_scan_status) VALUES
    ('a5a5a5a5-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000002', 'HRS1001', 'Crimson Comet',  'MC-1001', 'MALE',   'Thoroughbred', 'Chestnut', '2020-02-02', 482.00, 'USA', 'HEALTHY', 'ACTIVE', 'GRADE_1', 920000.00, TRUE, TRUE, 'VALID'),
    ('a5a5a5a5-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002', 'HRS1002', 'Azure Dream',    'MC-1002', 'FEMALE', 'Arabian',      'Grey',     '2021-06-11', 451.00, 'UAE', 'HEALTHY', 'ACTIVE', 'GRADE_2', 410000.00, TRUE, TRUE, 'VALID'),
    ('a5a5a5a5-0000-0000-0000-000000000003', '88888888-0000-0000-0000-000000000001', 'HRS1003', 'Velvet Night',   'MC-1003', 'FEMALE', 'Thoroughbred', 'Black',    '2019-09-19', 468.00, 'IRE', 'HEALTHY', 'ACTIVE', 'GRADE_1', 1350000.00, TRUE, TRUE, 'VALID'),
    ('a5a5a5a5-0000-0000-0000-000000000004', '88888888-0000-0000-0000-000000000001', 'HRS1004', 'Iron Will',      'MC-1004', 'GELDING','Thoroughbred', 'Bay',      '2018-03-30', 495.00, 'GBR', 'HEALTHY', 'ACTIVE', 'GRADE_3', 280000.00, TRUE, FALSE, 'MISSING'),
    ('a5a5a5a5-0000-0000-0000-000000000005', '88888888-0000-0000-0000-000000000002', 'HRS1005', 'Sakura Spirit',  'MC-1005', 'FEMALE', 'Thoroughbred', 'White',    '2020-04-04', 458.00, 'JPN', 'HEALTHY', 'ACTIVE', 'GRADE_2', 640000.00, TRUE, TRUE, 'VALID'),
    ('a5a5a5a5-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000002', 'HRS1006', 'Tokyo Bullet',   'MC-1006', 'MALE',   'Arabian',      'Bay',      '2021-12-12', 463.00, 'JPN', 'QUARANTINE', 'ACTIVE', NULL, 95000.00, FALSE, FALSE, 'MISSING');

INSERT INTO horse_characteristic (horse_id, tag) VALUES
    ('a5a5a5a5-0000-0000-0000-000000000001', 'EARLY_SPRINTER'),
    ('a5a5a5a5-0000-0000-0000-000000000001', 'FRONT_RUNNER'),
    ('a5a5a5a5-0000-0000-0000-000000000002', 'CLOSER'),
    ('a5a5a5a5-0000-0000-0000-000000000003', 'STAYER'),
    ('a5a5a5a5-0000-0000-0000-000000000003', 'SOFT_GROUND'),
    ('a5a5a5a5-0000-0000-0000-000000000004', 'CONSISTENT'),
    ('a5a5a5a5-0000-0000-0000-000000000005', 'FAST_FINISHER'),
    ('a5a5a5a5-0000-0000-0000-000000000006', 'YOUNG_PROSPECT');

-- ---------- TOURNAMENT (new, drives end-to-end scenario) ----------
INSERT INTO tournament (tournament_id, tournament_code, name, description, start_date, end_date, registration_open_at, registration_close_at, location, status, created_by_user_id) VALUES
    ('b5b5b5b5-0000-0000-0000-000000000001', 'TOUR-2026-006', 'Spring Championship 2026', 'Multi-round demo tournament wired end-to-end across all roles.',
        CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP + INTERVAL '20 days',
        CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '15 days',
        'Grand Arena, Demo City', 'ONGOING', '77777777-0000-0000-0000-000000000001');

-- More tournaments (varied statuses) to reach >=10
INSERT INTO tournament (tournament_id, tournament_code, name, description, start_date, end_date, registration_open_at, registration_close_at, location, status, created_by_user_id) VALUES
    ('b5b5b5b5-0000-0000-0000-000000000002', 'TOUR-2026-007', 'Autumn Sprint Series 2026', 'Short-distance speed series.',
        CURRENT_TIMESTAMP + INTERVAL '30 days', CURRENT_TIMESTAMP + INTERVAL '45 days',
        CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '20 days',
        'Seaside Track, Demo Bay', 'REGISTRATION_OPEN', '77777777-0000-0000-0000-000000000001'),
    ('b5b5b5b5-0000-0000-0000-000000000003', 'TOUR-2026-008', 'Winter Classic 2026', 'Prestigious winter staying contest.',
        CURRENT_TIMESTAMP + INTERVAL '60 days', CURRENT_TIMESTAMP + INTERVAL '70 days',
        CURRENT_TIMESTAMP + INTERVAL '10 days', CURRENT_TIMESTAMP + INTERVAL '50 days',
        'Highland Park, Demo Hills', 'PUBLISHED', '77777777-0000-0000-0000-000000000001'),
    ('b5b5b5b5-0000-0000-0000-000000000004', 'TOUR-2025-009', 'Summer Invitational 2025', 'Invitation-only elite meeting.',
        CURRENT_TIMESTAMP - INTERVAL '120 days', CURRENT_TIMESTAMP - INTERVAL '110 days', NULL, NULL,
        'Riverside Course, Demo Town', 'COMPLETED', '77777777-0000-0000-0000-000000000001'),
    ('b5b5b5b5-0000-0000-0000-000000000005', 'TOUR-2026-010', 'Founders Cup 2026', 'New showcase tournament, still drafting.',
        NULL, NULL, NULL, NULL, 'Central Stadium, Demo City', 'DRAFT', '77777777-0000-0000-0000-000000000001');

-- ---------- TOURNAMENT_ROUND (>=10 across tournaments) ----------
INSERT INTO tournament_round (round_id, tournament_id, round_no, name, stage, scheduled_at, status) VALUES
    ('c5c5c5c5-0000-0000-0000-000000000001', 'b5b5b5b5-0000-0000-0000-000000000001', 1, 'Qualifier 1', 'QUALIFIER', CURRENT_TIMESTAMP - INTERVAL '9 days', 'COMPLETED'),
    ('c5c5c5c5-0000-0000-0000-000000000002', 'b5b5b5b5-0000-0000-0000-000000000001', 2, 'Qualifier 2', 'QUALIFIER', CURRENT_TIMESTAMP - INTERVAL '7 days', 'COMPLETED'),
    ('c5c5c5c5-0000-0000-0000-000000000003', 'b5b5b5b5-0000-0000-0000-000000000001', 3, 'Heat A',      'HEAT',      CURRENT_TIMESTAMP - INTERVAL '3 days', 'COMPLETED'),
    ('c5c5c5c5-0000-0000-0000-000000000004', 'b5b5b5b5-0000-0000-0000-000000000001', 4, 'Semi-Final',  'SEMI',      CURRENT_TIMESTAMP + INTERVAL '5 days', 'PLANNED'),
    ('c5c5c5c5-0000-0000-0000-000000000005', 'b5b5b5b5-0000-0000-0000-000000000001', 5, 'Grand Final', 'FINAL',     CURRENT_TIMESTAMP + INTERVAL '15 days', 'PLANNED'),
    ('c5c5c5c5-0000-0000-0000-000000000006', 'bbbb1111-bbbb-1111-bbbb-111111111111', 1, 'Dubai Qualifier', 'QUALIFIER', CURRENT_TIMESTAMP - INTERVAL '32 days', 'COMPLETED'),
    ('c5c5c5c5-0000-0000-0000-000000000007', 'bbbb1111-bbbb-1111-bbbb-111111111111', 2, 'Dubai Final',     'FINAL',     CURRENT_TIMESTAMP - INTERVAL '28 days', 'COMPLETED'),
    ('c5c5c5c5-0000-0000-0000-000000000008', 'bbbb0000-0000-0000-0000-000000000002', 1, 'Ascot Heat',  'HEAT',  CURRENT_TIMESTAMP + INTERVAL '7 days', 'PLANNED'),
    ('c5c5c5c5-0000-0000-0000-000000000009', 'bbbb0000-0000-0000-0000-000000000002', 2, 'Ascot Final', 'FINAL', CURRENT_TIMESTAMP + INTERVAL '10 days', 'PLANNED'),
    ('c5c5c5c5-0000-0000-0000-00000000000a', 'bbbb0000-0000-0000-0000-000000000003', 1, 'Derby Trial Round', 'QUALIFIER', CURRENT_TIMESTAMP + INTERVAL '21 days', 'PLANNED'),
    ('c5c5c5c5-0000-0000-0000-00000000000b', 'bbbb0000-0000-0000-0000-000000000004', 1, 'Melbourne Final Round', 'FINAL', '2024-11-05 11:00:00+07', 'COMPLETED');

-- ---------- RACE (new, in Spring Championship; reach richer set) ----------
INSERT INTO race (race_id, tournament_id, round_id, race_code, name, race_type, distance_meter, track_condition, weather_condition, scheduled_start_at, actual_start_at, actual_end_at, prediction_cutoff_at, max_participants, venue, going_moisture_pct, total_purse, status) VALUES
    ('c6c6c6c6-0000-0000-0000-000000000001', 'b5b5b5b5-0000-0000-0000-000000000001', 'c5c5c5c5-0000-0000-0000-000000000001', 'RACE-101', 'Spring Qualifier Heat 1', 'FLAT', 1600, 'GOOD', 'SUNNY',
        CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '9 days' + INTERVAL '2 minutes', CURRENT_TIMESTAMP - INTERVAL '9 days' + INTERVAL '5 minutes', CURRENT_TIMESTAMP - INTERVAL '9 days' - INTERVAL '1 hour', 8, 'Grand Arena, Demo City', 15, 500000.00, 'OFFICIAL'),
    ('c6c6c6c6-0000-0000-0000-000000000002', 'b5b5b5b5-0000-0000-0000-000000000001', 'c5c5c5c5-0000-0000-0000-000000000003', 'RACE-102', 'Spring Heat A', 'FLAT', 2000, 'GOOD', 'CLOUDY',
        CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '1 minute', CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '4 minutes', CURRENT_TIMESTAMP - INTERVAL '3 days' - INTERVAL '30 minutes', 8, 'Grand Arena, Demo City', 18, 700000.00, 'OFFICIAL'),
    ('c6c6c6c6-0000-0000-0000-000000000003', 'b5b5b5b5-0000-0000-0000-000000000001', 'c5c5c5c5-0000-0000-0000-000000000004', 'RACE-103', 'Spring Semi-Final', 'FLAT', 2400, 'GOOD', 'SUNNY',
        CURRENT_TIMESTAMP + INTERVAL '5 days', NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '5 days' - INTERVAL '1 hour', 10, 'Grand Arena, Demo City', 12, 1000000.00, 'OPEN'),
    ('c6c6c6c6-0000-0000-0000-000000000004', 'b5b5b5b5-0000-0000-0000-000000000001', 'c5c5c5c5-0000-0000-0000-000000000005', 'RACE-104', 'Spring Grand Final', 'FLAT', 3200, 'GOOD', 'SUNNY',
        CURRENT_TIMESTAMP + INTERVAL '15 days', NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '15 days' - INTERVAL '1 hour', 12, 'Grand Arena, Demo City', 10, 2000000.00, 'SCHEDULED');

-- ---------- TOURNAMENT_REGISTRATION (new horses into Spring Championship) ----------
INSERT INTO tournament_registration (registration_id, owner_user_id, tournament_id, horse_id, race_id, registration_code, status, submitted_at, reviewed_at, approved_by_user_id, category) VALUES
    ('d5d5d5d5-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000002', 'b5b5b5b5-0000-0000-0000-000000000001', 'a5a5a5a5-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', 'REG-101', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '18 days', '77777777-0000-0000-0000-000000000001', 'GROUP_1'),
    ('d5d5d5d5-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002', 'b5b5b5b5-0000-0000-0000-000000000001', 'a5a5a5a5-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000001', 'REG-102', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '18 days', '77777777-0000-0000-0000-000000000001', 'GROUP_2'),
    ('d5d5d5d5-0000-0000-0000-000000000003', '88888888-0000-0000-0000-000000000001', 'b5b5b5b5-0000-0000-0000-000000000001', 'a5a5a5a5-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000002', 'REG-103', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '19 days', CURRENT_TIMESTAMP - INTERVAL '17 days', '77777777-0000-0000-0000-000000000001', 'GROUP_1'),
    ('d5d5d5d5-0000-0000-0000-000000000004', '88888888-0000-0000-0000-000000000001', 'b5b5b5b5-0000-0000-0000-000000000001', 'a5a5a5a5-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000002', 'REG-104', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '19 days', CURRENT_TIMESTAMP - INTERVAL '17 days', '77777777-0000-0000-0000-000000000001', 'GROUP_3'),
    ('d5d5d5d5-0000-0000-0000-000000000005', '88888888-0000-0000-0000-000000000002', 'b5b5b5b5-0000-0000-0000-000000000001', 'a5a5a5a5-0000-0000-0000-000000000005', 'c6c6c6c6-0000-0000-0000-000000000003', 'REG-105', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '11 days', '77777777-0000-0000-0000-000000000001', 'GROUP_2'),
    ('d5d5d5d5-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000002', 'b5b5b5b5-0000-0000-0000-000000000001', 'a5a5a5a5-0000-0000-0000-000000000006', NULL, 'REG-106', 'SUBMITTED', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL, NULL, 'HANDICAP');

-- ---------- RACE_ENTRY (new entries; lanes/entry_no unique per race) ----------
INSERT INTO race_entry (entry_id, registration_id, race_id, entry_code, entry_no, lane_no, weight_carried_lbs, recent_form, odds, status, prize_earned) VALUES
    -- RACE-101 (OFFICIAL): Crimson Comet vs Azure Dream
    ('e5e5e5e5-0000-0000-0000-000000000001', 'd5d5d5d5-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', 'ENT-101', 1, 1, 126, '1-1-2', '2-1', 'FINISHED', 300000.00),
    ('e5e5e5e5-0000-0000-0000-000000000002', 'd5d5d5d5-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000001', 'ENT-102', 2, 2, 122, '2-3-1', '3-1', 'FINISHED', 120000.00),
    -- RACE-102 (OFFICIAL): Velvet Night vs Iron Will
    ('e5e5e5e5-0000-0000-0000-000000000003', 'd5d5d5d5-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000002', 'ENT-103', 1, 1, 128, '1-1-1', '6-4', 'FINISHED', 420000.00),
    ('e5e5e5e5-0000-0000-0000-000000000004', 'd5d5d5d5-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000002', 'ENT-104', 2, 2, 124, '3-2-4', '5-1', 'FINISHED', 90000.00),
    -- RACE-103 (OPEN, future): Sakura Spirit entered, awaiting
    ('e5e5e5e5-0000-0000-0000-000000000005', 'd5d5d5d5-0000-0000-0000-000000000005', 'c6c6c6c6-0000-0000-0000-000000000003', 'ENT-105', 1, 1, 120, '2-1-2', '4-1', 'ENTERED', 0.00);

-- ---------- JOCKEY_ASSIGNMENT (new jockeys ride new entries; reach >=10) ----------
INSERT INTO jockey_assignment (assignment_id, entry_id, jockey_user_id, status, invited_at, responded_at, assigned_by_user_id) VALUES
    ('f5f5f5f5-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000003', 'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '14 days', '77777777-0000-0000-0000-000000000002'),
    ('f5f5f5f5-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000002', '99999999-0000-0000-0000-000000000001', 'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '14 days', '77777777-0000-0000-0000-000000000002'),
    ('f5f5f5f5-0000-0000-0000-000000000003', 'e5e5e5e5-0000-0000-0000-000000000003', '99999999-0000-0000-0000-000000000002', 'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '13 days', '88888888-0000-0000-0000-000000000001'),
    ('f5f5f5f5-0000-0000-0000-000000000004', 'e5e5e5e5-0000-0000-0000-000000000004', '33333333-3333-3333-3333-333333333333', 'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '13 days', '88888888-0000-0000-0000-000000000001'),
    ('f5f5f5f5-0000-0000-0000-000000000005', 'e5e5e5e5-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000003', 'INVITED', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL, '88888888-0000-0000-0000-000000000002');

-- ---------- RACE_ENTRY_INSPECTION (reach >=10) ----------
INSERT INTO race_entry_inspection (inspection_id, entry_id, race_id, health_cert_passed, weight_verified, weight_carried_lbs, coggins_test_passed, pre_race_exam_passed, inspection_status, steward_note, inspected_by_user_id, inspected_at) VALUES
    ('a6a6a6a6-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', TRUE, TRUE, 126, TRUE, TRUE, 'CLEARED', 'All checks passed.', '77777777-0000-0000-0000-000000000004', CURRENT_TIMESTAMP - INTERVAL '9 days' - INTERVAL '1 hour'),
    ('a6a6a6a6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000001', TRUE, TRUE, 122, TRUE, TRUE, 'CLEARED', 'Cleared to race.', '77777777-0000-0000-0000-000000000004', CURRENT_TIMESTAMP - INTERVAL '9 days' - INTERVAL '1 hour'),
    ('a6a6a6a6-0000-0000-0000-000000000003', 'e5e5e5e5-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000002', TRUE, TRUE, 128, TRUE, TRUE, 'CLEARED', 'Excellent condition.', 'aaaaaaaa-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '3 days' - INTERVAL '1 hour'),
    ('a6a6a6a6-0000-0000-0000-000000000004', 'e5e5e5e5-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000002', TRUE, TRUE, 124, FALSE, TRUE, 'VET_CHECK', 'Coggins paperwork pending verification.', 'aaaaaaaa-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '3 days' - INTERVAL '1 hour'),
    ('a6a6a6a6-0000-0000-0000-000000000005', 'e5e5e5e5-0000-0000-0000-000000000005', 'c6c6c6c6-0000-0000-0000-000000000003', FALSE, FALSE, NULL, FALSE, FALSE, 'PENDING', NULL, NULL, NULL),
    -- inspections for existing belmont/other entries to reach >=10
    ('a6a6a6a6-0000-0000-0000-000000000006', 'eeee2222-eeee-2222-eeee-222222222222', 'cccc2222-cccc-2222-cccc-222222222222', TRUE, TRUE, 120, TRUE, TRUE, 'CLEARED', 'Sound and ready.', '55555555-5555-5555-5555-555555555555', CURRENT_TIMESTAMP - INTERVAL '25 days'),
    ('a6a6a6a6-0000-0000-0000-000000000007', 'eeee0000-0000-0000-0000-000000000005', 'cccc0000-0000-0000-0000-000000000003', TRUE, FALSE, NULL, TRUE, FALSE, 'VET_CHECK', 'Weight to be confirmed at scale.', '66666666-6666-6666-6666-666666666666', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ('a6a6a6a6-0000-0000-0000-000000000008', 'eeee0000-0000-0000-0000-000000000006', 'cccc0000-0000-0000-0000-000000000006', TRUE, TRUE, 130, TRUE, TRUE, 'CLEARED', 'Final cleared.', '55555555-5555-5555-5555-555555555555', '2024-11-05 10:30:00+07'),
    ('a6a6a6a6-0000-0000-0000-000000000009', 'eeee0000-0000-0000-0000-000000000007', 'cccc2222-cccc-2222-cccc-222222222222', TRUE, TRUE, 118, TRUE, TRUE, 'CLEARED', 'Cleared.', '55555555-5555-5555-5555-555555555555', CURRENT_TIMESTAMP - INTERVAL '24 days'),
    ('a6a6a6a6-0000-0000-0000-00000000000a', 'eeee0000-0000-0000-0000-000000000008', 'cccc2222-cccc-2222-cccc-222222222222', TRUE, TRUE, 121, TRUE, TRUE, 'CLEARED', 'Cleared.', '66666666-6666-6666-6666-666666666666', CURRENT_TIMESTAMP - INTERVAL '24 days');

-- ---------- RACE_RESULT (reach >=10) for finished/official races ----------
INSERT INTO race_result (result_id, race_id, entry_id, finish_position, finish_time_ms, lengths_behind, score, current_version_no, officiality_status, approved_by_user_id, published_at) VALUES
    ('a7a7a7a7-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000001', 1, 96120, 0.00, 100.00, 1, 'OFFICIAL', '77777777-0000-0000-0000-000000000004', CURRENT_TIMESTAMP - INTERVAL '9 days'),
    ('a7a7a7a7-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000002', 2, 96980, 3.20, 80.00, 1, 'OFFICIAL', '77777777-0000-0000-0000-000000000004', CURRENT_TIMESTAMP - INTERVAL '9 days'),
    ('a7a7a7a7-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000003', 1, 121340, 0.00, 100.00, 2, 'AMENDED', '77777777-0000-0000-0000-000000000004', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('a7a7a7a7-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000004', 2, 122100, 2.80, 78.00, 1, 'OFFICIAL', '77777777-0000-0000-0000-000000000004', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    -- results for existing entries lacking one (Melbourne final, lightning, etc.)
    ('a7a7a7a7-0000-0000-0000-000000000005', 'cccc0000-0000-0000-0000-000000000006', 'eeee0000-0000-0000-0000-000000000006', 1, 198450, 0.00, 100.00, 1, 'OFFICIAL', (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), '2024-11-05 11:10:00+07'),
    ('a7a7a7a7-0000-0000-0000-000000000006', 'cccc1111-cccc-1111-cccc-111111111111', 'eeee4444-eeee-4444-eeee-444444444444', 3, 95600, 9.10, 60.00, 1, 'OFFICIAL', '55555555-5555-5555-5555-555555555555', CURRENT_TIMESTAMP - INTERVAL '5 minutes'),
    ('a7a7a7a7-0000-0000-0000-000000000007', 'cccc2222-cccc-2222-cccc-222222222222', 'eeee0000-0000-0000-0000-000000000007', 1, 88300, 0.00, 100.00, 1, 'PROVISIONAL', NULL, NULL),
    ('a7a7a7a7-0000-0000-0000-000000000008', 'cccc2222-cccc-2222-cccc-222222222222', 'eeee0000-0000-0000-0000-000000000008', 3, 89500, 4.50, 62.00, 1, 'PROVISIONAL', NULL, NULL);

-- ---------- RACE_RESULT_VERSION (history; reach >=10) ----------
INSERT INTO race_result_version (result_version_id, result_id, version_no, finish_position, finish_time_ms, score, officiality_status, changed_by_user_id, change_reason) VALUES
    ('a8a8a8a8-0000-0000-0000-000000000001', 'a7a7a7a7-0000-0000-0000-000000000001', 1, 1, 96120, 100.00, 'OFFICIAL', '77777777-0000-0000-0000-000000000004', 'Initial official result.'),
    ('a8a8a8a8-0000-0000-0000-000000000002', 'a7a7a7a7-0000-0000-0000-000000000002', 1, 2, 96980, 80.00, 'OFFICIAL', '77777777-0000-0000-0000-000000000004', 'Initial official result.'),
    ('a8a8a8a8-0000-0000-0000-000000000003', 'a7a7a7a7-0000-0000-0000-000000000003', 1, 1, 121800, 98.00, 'PROVISIONAL', '77777777-0000-0000-0000-000000000004', 'Provisional from photo-finish.'),
    ('a8a8a8a8-0000-0000-0000-000000000004', 'a7a7a7a7-0000-0000-0000-000000000003', 2, 1, 121340, 100.00, 'AMENDED', '77777777-0000-0000-0000-000000000004', 'Timing corrected after review of fraction data.'),
    ('a8a8a8a8-0000-0000-0000-000000000005', 'a7a7a7a7-0000-0000-0000-000000000004', 1, 2, 122100, 78.00, 'OFFICIAL', '77777777-0000-0000-0000-000000000004', 'Initial official result.'),
    ('a8a8a8a8-0000-0000-0000-000000000006', 'a7a7a7a7-0000-0000-0000-000000000005', 1, 1, 198450, 100.00, 'OFFICIAL', (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), 'Melbourne Cup final result.'),
    ('a8a8a8a8-0000-0000-0000-000000000007', 'a7a7a7a7-0000-0000-0000-000000000006', 1, 3, 95600, 60.00, 'OFFICIAL', '55555555-5555-5555-5555-555555555555', 'Belmont Golden Arrow result.'),
    ('a8a8a8a8-0000-0000-0000-000000000008', 'a0a0a0a0-0000-0000-0000-000000000001', 1, 1, 94200, 100.00, 'OFFICIAL', '55555555-5555-5555-5555-555555555555', 'Belmont winner Midnight Thunder.'),
    ('a8a8a8a8-0000-0000-0000-000000000009', 'a0a0a0a0-0000-0000-0000-000000000003', 1, 5, 95050, 40.00, 'OFFICIAL', '55555555-5555-5555-5555-555555555555', 'Belmont Sapphire Wind result.'),
    ('a8a8a8a8-0000-0000-0000-00000000000a', 'a0a0a0a0-0000-0000-0000-000000000002', 1, 2, NULL, 80.00, 'OFFICIAL', (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), 'Epsom Silver Bullet result.');

-- ---------- RACE_FRACTION (split times; reach >=10) ----------
INSERT INTO race_fraction (race_id, split_no, time_ms) VALUES
    ('c6c6c6c6-0000-0000-0000-000000000001', 1, 23800),
    ('c6c6c6c6-0000-0000-0000-000000000001', 2, 47600),
    ('c6c6c6c6-0000-0000-0000-000000000001', 3, 71900),
    ('c6c6c6c6-0000-0000-0000-000000000001', 4, 96120),
    ('c6c6c6c6-0000-0000-0000-000000000002', 1, 24100),
    ('c6c6c6c6-0000-0000-0000-000000000002', 2, 48500),
    ('c6c6c6c6-0000-0000-0000-000000000002', 3, 73200),
    ('cccc1111-cccc-1111-cccc-111111111111', 1, 23200),
    ('cccc1111-cccc-1111-cccc-111111111111', 2, 47100),
    ('cccc1111-cccc-1111-cccc-111111111111', 3, 70900),
    ('cccc1111-cccc-1111-cccc-111111111111', 4, 94200);

-- ---------- REFEREE_ASSIGNMENT (reach >=10) ----------
INSERT INTO referee_assignment (ref_assignment_id, race_id, referee_user_id, panel_role, status, assigned_at, created_by_user_id) VALUES
    ('b7b7b7b7-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000004', 'CHIEF',      'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '10 days', '77777777-0000-0000-0000-000000000001'),
    ('b7b7b7b7-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'TIMEKEEPER', 'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '10 days', '77777777-0000-0000-0000-000000000001'),
    ('b7b7b7b7-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000004', 'CHIEF',      'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '5 days', '77777777-0000-0000-0000-000000000001'),
    ('b7b7b7b7-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000002', 'JUDGE',      'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '5 days', '77777777-0000-0000-0000-000000000001'),
    ('b7b7b7b7-0000-0000-0000-000000000005', 'c6c6c6c6-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000004', 'CHIEF',      'ASSIGNED',  CURRENT_TIMESTAMP - INTERVAL '1 day', '77777777-0000-0000-0000-000000000001'),
    ('b7b7b7b7-0000-0000-0000-000000000006', 'c6c6c6c6-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000001', 'STEWARD',    'ASSIGNED',  CURRENT_TIMESTAMP - INTERVAL '1 day', '77777777-0000-0000-0000-000000000001'),
    ('b7b7b7b7-0000-0000-0000-000000000007', 'c6c6c6c6-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000004', 'CHIEF',      'ASSIGNED',  CURRENT_TIMESTAMP, '77777777-0000-0000-0000-000000000001'),
    ('b7b7b7b7-0000-0000-0000-000000000008', 'cccc0000-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'OBSERVER',   'ASSIGNED',  CURRENT_TIMESTAMP - INTERVAL '2 days', (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),
    ('b7b7b7b7-0000-0000-0000-000000000009', 'cccc2222-cccc-2222-cccc-222222222222', '55555555-5555-5555-5555-555555555555', 'CHIEF',      'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '25 days', (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),
    ('b7b7b7b7-0000-0000-0000-00000000000a', 'cccc0000-0000-0000-0000-000000000007', '66666666-6666-6666-6666-666666666666', 'JUDGE',      'CONFIRMED', '2024-11-05 09:30:00+07', (SELECT user_id FROM app_user WHERE email='admin@horserace.local'));

-- ---------- REFEREE_REPORT (reach >=10) ----------
INSERT INTO referee_report (report_id, race_id, author_user_id, report_type, summary, decision, severity_level, report_status, submitted_at) VALUES
    ('b8b8b8b8-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000004', 'GENERAL',  'Clean race, no incidents.', 'No action required.', 'LOW', 'CLOSED', CURRENT_TIMESTAMP - INTERVAL '9 days'),
    ('b8b8b8b8-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000004', 'INCIDENT', 'Contact at the first turn between lanes 1 and 2.', 'Reviewed; no change to placings.', 'MEDIUM', 'REVIEWED', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('b8b8b8b8-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000002', 'VIOLATION','Excessive whip use flagged on the home straight.', 'Time penalty issued to entry 104.', 'HIGH', 'SUBMITTED', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('b8b8b8b8-0000-0000-0000-000000000004', 'cccc1111-cccc-1111-cccc-111111111111', '55555555-5555-5555-5555-555555555555', 'OBJECTION','Owner objection regarding interference in Turn 3.', 'Objection under review.', 'HIGH', 'SUBMITTED', CURRENT_TIMESTAMP - INTERVAL '20 minutes'),
    ('b8b8b8b8-0000-0000-0000-000000000005', 'cccc1111-cccc-1111-cccc-111111111111', '66666666-6666-6666-6666-666666666666', 'INCIDENT', 'Minor bumping at the start.', 'Warning issued.', 'LOW', 'CLOSED', CURRENT_TIMESTAMP - INTERVAL '10 minutes'),
    ('b8b8b8b8-0000-0000-0000-000000000006', 'cccc0000-0000-0000-0000-000000000006', '55555555-5555-5555-5555-555555555555', 'GENERAL',  'Melbourne Cup final ran without incident.', 'Result certified.', 'LOW', 'CLOSED', '2024-11-05 11:15:00+07'),
    ('b8b8b8b8-0000-0000-0000-000000000007', 'cccc0000-0000-0000-0000-000000000007', '66666666-6666-6666-6666-666666666666', 'GENERAL',  'Lightning Stakes clean run.', 'Result certified.', 'LOW', 'CLOSED', '2024-11-05 10:05:00+07'),
    ('b8b8b8b8-0000-0000-0000-000000000008', 'cccc2222-cccc-2222-cccc-222222222222', '55555555-5555-5555-5555-555555555555', 'GENERAL',  'Epsom qualifier monitored.', NULL, 'LOW', 'DRAFT', NULL),
    ('b8b8b8b8-0000-0000-0000-000000000009', 'cccc0000-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'GENERAL',  'Pre-race observation report for Ascot Gold Cup.', NULL, 'LOW', 'DRAFT', NULL),
    ('b8b8b8b8-0000-0000-0000-00000000000a', 'c6c6c6c6-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000004', 'GENERAL',  'Semi-final staffing confirmed.', NULL, 'LOW', 'DRAFT', NULL);

-- ---------- PENALTY (reach >=10; some link to reports/entries) ----------
INSERT INTO penalty (penalty_id, race_id, entry_id, report_id, penalty_type, time_penalty_ms, fine_amount, reason, issued_by_user_id, status) VALUES
    ('b9b9b9b9-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000004', 'b8b8b8b8-0000-0000-0000-000000000003', 'TIME_PENALTY', 500, NULL, 'Excessive whip use beyond permitted strikes.', 'aaaaaaaa-0000-0000-0000-000000000002', 'UPHELD'),
    ('b9b9b9b9-0000-0000-0000-000000000002', 'cccc1111-cccc-1111-cccc-111111111111', 'eeee3333-eeee-3333-eeee-333333333333', 'b8b8b8b8-0000-0000-0000-000000000004', 'WARNING', NULL, NULL, 'Interference warning, no demotion.', '55555555-5555-5555-5555-555555555555', 'ISSUED'),
    ('b9b9b9b9-0000-0000-0000-000000000003', 'cccc1111-cccc-1111-cccc-111111111111', 'eeee1111-eeee-1111-eeee-111111111111', 'b8b8b8b8-0000-0000-0000-000000000005', 'WARNING', NULL, NULL, 'Minor bumping at the start.', '66666666-6666-6666-6666-666666666666', 'UPHELD'),
    ('b9b9b9b9-0000-0000-0000-000000000004', 'cccc1111-cccc-1111-cccc-111111111111', 'eeee4444-eeee-4444-eeee-444444444444', NULL, 'FINE', NULL, 500000.00, 'Late arrival at the parade ring.', '55555555-5555-5555-5555-555555555555', 'ISSUED'),
    ('b9b9b9b9-0000-0000-0000-000000000005', 'c6c6c6c6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000003', NULL, 'WARNING', NULL, NULL, 'Drifted off true line briefly.', 'aaaaaaaa-0000-0000-0000-000000000002', 'OVERTURNED'),
    ('b9b9b9b9-0000-0000-0000-000000000006', 'c6c6c6c6-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000002', NULL, 'WARNING', NULL, NULL, 'Slow to load into the gate.', '77777777-0000-0000-0000-000000000004', 'ISSUED'),
    ('b9b9b9b9-0000-0000-0000-000000000007', 'cccc0000-0000-0000-0000-000000000006', 'eeee0000-0000-0000-0000-000000000006', 'b8b8b8b8-0000-0000-0000-000000000006', 'WARNING', NULL, NULL, 'Routine caution.', '55555555-5555-5555-5555-555555555555', 'CANCELLED'),
    ('b9b9b9b9-0000-0000-0000-000000000008', 'cccc2222-cccc-2222-cccc-222222222222', 'eeee0000-0000-0000-0000-000000000008', NULL, 'TIME_PENALTY', 300, NULL, 'Crowding at the rail.', '55555555-5555-5555-5555-555555555555', 'ISSUED'),
    ('b9b9b9b9-0000-0000-0000-000000000009', 'cccc0000-0000-0000-0000-000000000007', NULL, 'b8b8b8b8-0000-0000-0000-000000000007', 'FINE', NULL, 250000.00, 'Trainer paperwork late.', '66666666-6666-6666-6666-666666666666', 'UPHELD'),
    ('b9b9b9b9-0000-0000-0000-00000000000a', 'c6c6c6c6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000004', NULL, 'DISQUALIFICATION', NULL, NULL, 'Severe repeat infraction (demo).', 'aaaaaaaa-0000-0000-0000-000000000002', 'OVERTURNED');

-- ---------- RACE_VIOLATION (reach >=10; some link to penalties) ----------
INSERT INTO race_violation (violation_id, race_id, entry_id, jockey_user_id, infraction_type, severity, turn_no, race_time_offset_ms, remarks, regulatory_ref, status, reported_by_user_id, penalty_id, decision_type, ruling_notes, ruled_by_user_id, ruled_at) VALUES
    ('b6b6b6b6-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000004', '33333333-3333-3333-3333-333333333333', 'WHIP_USAGE', 'HIGH', 4, 110200, 'Whip used beyond permitted strikes on home straight.', 'Rule 4.2.1 - Whip Limitations', 'RESOLVED', 'aaaaaaaa-0000-0000-0000-000000000002', 'b9b9b9b9-0000-0000-0000-000000000001', 'TIME_PENALTY', 'Half-second time penalty applied.', 'aaaaaaaa-0000-0000-0000-000000000002', CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '30 minutes'),
    ('b6b6b6b6-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000003', '99999999-0000-0000-0000-000000000002', 'INTERFERENCE', 'MEDIUM', 2, 48700, 'Drifted across rival briefly.', 'Rule 6.1 - Interference', 'DISMISSED', 'aaaaaaaa-0000-0000-0000-000000000002', NULL, 'NO_ACTION', 'No material effect on the result.', 'aaaaaaaa-0000-0000-0000-000000000002', CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '40 minutes'),
    ('b6b6b6b6-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000002', '99999999-0000-0000-0000-000000000001', 'CROWDING', 'LOW', 1, 24500, 'Squeezed a rival at the start.', 'Rule 6.4 - Crowding', 'PENDING', '77777777-0000-0000-0000-000000000004', NULL, NULL, NULL, NULL, NULL),
    ('b6b6b6b6-0000-0000-0000-000000000004', 'cccc2222-cccc-2222-cccc-222222222222', 'eeee0000-0000-0000-0000-000000000008', '44444444-4444-4444-4444-444444444444', 'CROWDING', 'MEDIUM', 3, 60300, 'Crowded the rail entering the bend.', 'Rule 6.4 - Crowding', 'RESOLVED', '55555555-5555-5555-5555-555555555555', 'b9b9b9b9-0000-0000-0000-000000000008', 'TIME_PENALTY', '0.3s penalty applied.', '55555555-5555-5555-5555-555555555555', CURRENT_TIMESTAMP - INTERVAL '23 days'),
    ('b6b6b6b6-0000-0000-0000-000000000005', 'cccc0000-0000-0000-0000-000000000006', 'eeee0000-0000-0000-0000-000000000006', '33333333-3333-3333-3333-333333333333', 'OTHER', 'LOW', NULL, NULL, 'Late to scale post-race (demo).', 'Rule 9.1 - Procedure', 'DISMISSED', '55555555-5555-5555-5555-555555555555', NULL, 'NO_ACTION', 'Excused.', '55555555-5555-5555-5555-555555555555', '2024-11-05 11:20:00+07'),
    ('b6b6b6b6-0000-0000-0000-000000000006', 'cccc0000-0000-0000-0000-000000000007', NULL, NULL, 'OTHER', NULL, NULL, NULL, 'Administrative paperwork flag.', 'Rule 9.2', 'UNDER_REVIEW', '66666666-6666-6666-6666-666666666666', 'b9b9b9b9-0000-0000-0000-000000000009', NULL, NULL, NULL, NULL),
    ('b6b6b6b6-0000-0000-0000-000000000007', 'c6c6c6c6-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000003', 'BUMPING', 'LOW', 1, 22000, 'Light contact, no effect.', 'Rule 6.3 - Bumping', 'DISMISSED', '77777777-0000-0000-0000-000000000004', NULL, 'NO_ACTION', 'No further action.', '77777777-0000-0000-0000-000000000004', CURRENT_TIMESTAMP - INTERVAL '9 days'),
    ('b6b6b6b6-0000-0000-0000-000000000008', 'cccc2222-cccc-2222-cccc-222222222222', 'eeee0000-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'INTERFERENCE', 'HIGH', 5, 80100, 'Caused another runner to check.', 'Rule 6.1 - Interference', 'PENDING', '55555555-5555-5555-5555-555555555555', NULL, NULL, NULL, NULL, NULL),
    ('b6b6b6b6-0000-0000-0000-000000000009', 'cccc0000-0000-0000-0000-000000000003', 'eeee0000-0000-0000-0000-000000000005', NULL, 'OTHER', 'LOW', NULL, NULL, 'Pre-race conduct note.', 'Rule 2.1', 'UNDER_REVIEW', 'aaaaaaaa-0000-0000-0000-000000000002', NULL, NULL, NULL, NULL, NULL),
    ('b6b6b6b6-0000-0000-0000-00000000000a', 'c6c6c6c6-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000004', '33333333-3333-3333-3333-333333333333', 'BUMPING', 'MEDIUM', 2, 45000, 'Repeat contact in midfield.', 'Rule 6.3 - Bumping', 'UNDER_REVIEW', 'aaaaaaaa-0000-0000-0000-000000000002', NULL, NULL, NULL, NULL, NULL);

-- ---------- STANDING (per-tournament rankings; reach >=10) ----------
INSERT INTO standing (standing_id, tournament_id, subject_type, subject_id, total_points, races_count, wins_count, rank_position) VALUES
    -- Spring Championship: horses
    ('37000000-0000-0000-0000-000000000001', 'b5b5b5b5-0000-0000-0000-000000000001', 'HORSE',  'a5a5a5a5-0000-0000-0000-000000000003', 100.00, 1, 1, 1),
    ('37000000-0000-0000-0000-000000000002', 'b5b5b5b5-0000-0000-0000-000000000001', 'HORSE',  'a5a5a5a5-0000-0000-0000-000000000001', 100.00, 1, 1, 2),
    ('37000000-0000-0000-0000-000000000003', 'b5b5b5b5-0000-0000-0000-000000000001', 'HORSE',  'a5a5a5a5-0000-0000-0000-000000000002', 80.00,  1, 0, 3),
    ('37000000-0000-0000-0000-000000000004', 'b5b5b5b5-0000-0000-0000-000000000001', 'HORSE',  'a5a5a5a5-0000-0000-0000-000000000004', 78.00,  1, 0, 4),
    -- Spring Championship: jockeys
    ('37000000-0000-0000-0000-000000000005', 'b5b5b5b5-0000-0000-0000-000000000001', 'JOCKEY', '99999999-0000-0000-0000-000000000002', 100.00, 1, 1, 1),
    ('37000000-0000-0000-0000-000000000006', 'b5b5b5b5-0000-0000-0000-000000000001', 'JOCKEY', '77777777-0000-0000-0000-000000000003', 100.00, 1, 1, 2),
    ('37000000-0000-0000-0000-000000000007', 'b5b5b5b5-0000-0000-0000-000000000001', 'JOCKEY', '99999999-0000-0000-0000-000000000001', 80.00,  1, 0, 3),
    ('37000000-0000-0000-0000-000000000008', 'b5b5b5b5-0000-0000-0000-000000000001', 'JOCKEY', '33333333-3333-3333-3333-333333333333', 78.00,  1, 0, 4),
    -- Dubai World Cup: horses + jockey
    ('37000000-0000-0000-0000-000000000009', 'bbbb1111-bbbb-1111-bbbb-111111111111', 'HORSE',  'aaaa1111-aaaa-1111-aaaa-111111111111', 100.00, 1, 1, 1),
    ('37000000-0000-0000-0000-00000000000a', 'bbbb1111-bbbb-1111-bbbb-111111111111', 'HORSE',  'aaaa3333-aaaa-3333-aaaa-333333333333', 40.00,  1, 0, 2),
    ('37000000-0000-0000-0000-00000000000b', 'bbbb1111-bbbb-1111-bbbb-111111111111', 'JOCKEY', '11111111-1111-1111-1111-111111111111', 100.00, 1, 1, 1);

-- ---------- PRIZE (tournament/race prizes; reach >=10) ----------
INSERT INTO prize (prize_id, tournament_id, race_id, prize_code, beneficiary_type, rank_position, prize_amount, currency_code, status) VALUES
    ('36000000-0000-0000-0000-000000000001', 'b5b5b5b5-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', 'PRZ-101', 'OWNER',  1, 300000.00, 'VND', 'PAID'),
    ('36000000-0000-0000-0000-000000000002', 'b5b5b5b5-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', 'PRZ-102', 'OWNER',  2, 120000.00, 'VND', 'PAID'),
    ('36000000-0000-0000-0000-000000000003', 'b5b5b5b5-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', 'PRZ-103', 'JOCKEY', 1, 30000.00,  'VND', 'PAID'),
    ('36000000-0000-0000-0000-000000000004', 'b5b5b5b5-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000002', 'PRZ-104', 'OWNER',  1, 420000.00, 'VND', 'AWARDED'),
    ('36000000-0000-0000-0000-000000000005', 'b5b5b5b5-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000002', 'PRZ-105', 'OWNER',  2, 90000.00,  'VND', 'AWARDED'),
    ('36000000-0000-0000-0000-000000000006', 'b5b5b5b5-0000-0000-0000-000000000001', NULL, 'PRZ-106', 'TEAM', NULL, 1000000.00, 'VND', 'ANNOUNCED'),
    ('36000000-0000-0000-0000-000000000007', 'bbbb1111-bbbb-1111-bbbb-111111111111', 'cccc1111-cccc-1111-cccc-111111111111', 'PRZ-107', 'OWNER',  1, 340260.00, 'VND', 'PAID'),
    ('36000000-0000-0000-0000-000000000008', 'bbbb1111-bbbb-1111-bbbb-111111111111', 'cccc1111-cccc-1111-cccc-111111111111', 'PRZ-108', 'JOCKEY', 1, 34026.00,  'VND', 'AWARDED'),
    ('36000000-0000-0000-0000-000000000009', 'bbbb0000-0000-0000-0000-000000000004', 'cccc0000-0000-0000-0000-000000000006', 'PRZ-109', 'OWNER',  1, 5000000.00,'VND', 'PAID'),
    ('36000000-0000-0000-0000-00000000000a', 'bbbb0000-0000-0000-0000-000000000002', NULL, 'PRZ-110', 'HORSE', 1, 250000.00, 'VND', 'DRAFT');

-- ---------- RACE_PRIZE_DISTRIBUTION (reach >=10) ----------
INSERT INTO race_prize_distribution (race_id, place, amount) VALUES
    ('c6c6c6c6-0000-0000-0000-000000000001', '1st', 300000.00),
    ('c6c6c6c6-0000-0000-0000-000000000001', '2nd', 120000.00),
    ('c6c6c6c6-0000-0000-0000-000000000001', '3rd', 80000.00),
    ('c6c6c6c6-0000-0000-0000-000000000002', '1st', 420000.00),
    ('c6c6c6c6-0000-0000-0000-000000000002', '2nd', 180000.00),
    ('c6c6c6c6-0000-0000-0000-000000000002', '3rd', 100000.00),
    ('c6c6c6c6-0000-0000-0000-000000000004', '1st', 1200000.00),
    ('c6c6c6c6-0000-0000-0000-000000000004', '2nd', 500000.00),
    ('cccc0000-0000-0000-0000-000000000006', '1st', 5000000.00),
    ('cccc0000-0000-0000-0000-000000000006', '2nd', 2000000.00);

-- ---------- BETTING_POOL (one per race+type; reach >=10) ----------
INSERT INTO betting_pool (pool_id, race_id, prediction_type, total_stake, rake_percent, status) VALUES
    ('34000000-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', 'WIN',   250000.00, 10.00, 'SETTLED'),
    ('34000000-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000001', 'PLACE', 120000.00, 10.00, 'SETTLED'),
    ('34000000-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000002', 'WIN',   400000.00, 12.00, 'SETTLED'),
    ('34000000-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000003', 'WIN',   80000.00,  10.00, 'OPEN'),
    ('34000000-0000-0000-0000-000000000005', 'c6c6c6c6-0000-0000-0000-000000000003', 'PLACE', 40000.00,  10.00, 'OPEN'),
    ('34000000-0000-0000-0000-000000000006', 'c6c6c6c6-0000-0000-0000-000000000004', 'WIN',   0.00,      10.00, 'OPEN'),
    ('34000000-0000-0000-0000-000000000007', 'cccc1111-cccc-1111-cccc-111111111111', 'WIN',   500000.00, 8.00,  'SETTLED'),
    ('34000000-0000-0000-0000-000000000008', 'cccc1111-cccc-1111-cccc-111111111111', 'PLACE', 200000.00, 8.00,  'SETTLED'),
    ('34000000-0000-0000-0000-000000000009', 'cccc3333-cccc-3333-cccc-333333333333', 'WIN',   60000.00,  10.00, 'OPEN'),
    ('34000000-0000-0000-0000-00000000000a', 'cccc0000-0000-0000-0000-000000000003', 'WIN',   30000.00,  10.00, 'OPEN');

-- ---------- PREDICTION (spectators bet; reach >=10; mix of statuses) ----------
INSERT INTO prediction (prediction_id, race_id, spectator_user_id, predicted_entry_id, prediction_type, locked_odds, stake_amount, potential_payout, status, submitted_at, settled_at, idempotency_key) VALUES
    -- Settled WON on RACE-101 (winner e5e5...0001)
    ('33000000-0000-0000-0000-000000000001', 'c6c6c6c6-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000005', 'e5e5e5e5-0000-0000-0000-000000000001', 'WIN',  2.50, 50000.00, 125000.00, 'WON',  CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '9 days' + INTERVAL '10 minutes', 'IDEMP-PRED-001'),
    ('33000000-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000002', 'WIN',  3.00, 30000.00, 90000.00,  'LOST', CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '9 days' + INTERVAL '10 minutes', 'IDEMP-PRED-002'),
    ('33000000-0000-0000-0000-000000000003', 'c6c6c6c6-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000001', 'PLACE',1.50, 20000.00, 30000.00,  'WON',  CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '9 days' + INTERVAL '10 minutes', 'IDEMP-PRED-003'),
    -- Settled on RACE-102
    ('33000000-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000005', 'e5e5e5e5-0000-0000-0000-000000000003', 'WIN',  1.80, 60000.00, 108000.00, 'WON',  CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '10 minutes', 'IDEMP-PRED-004'),
    ('33000000-0000-0000-0000-000000000005', 'c6c6c6c6-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000002', 'e5e5e5e5-0000-0000-0000-000000000004', 'WIN',  5.00, 40000.00, 200000.00, 'LOST', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '10 minutes', 'IDEMP-PRED-005'),
    -- Pending/confirmed on future RACE-103
    ('33000000-0000-0000-0000-000000000006', 'c6c6c6c6-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000005', 'e5e5e5e5-0000-0000-0000-000000000005', 'WIN',  4.00, 25000.00, 100000.00, 'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '6 hours', NULL, 'IDEMP-PRED-006'),
    ('33000000-0000-0000-0000-000000000007', 'c6c6c6c6-0000-0000-0000-000000000003', 'bbbbbbbb-0000-0000-0000-000000000001', 'e5e5e5e5-0000-0000-0000-000000000005', 'PLACE',2.00, 15000.00, 30000.00,  'PENDING',   CURRENT_TIMESTAMP - INTERVAL '5 hours', NULL, 'IDEMP-PRED-007'),
    -- Belmont (cccc1111) predictions by jane (existing spectator)
    ('33000000-0000-0000-0000-000000000008', 'cccc1111-cccc-1111-cccc-111111111111', (SELECT user_id FROM app_user WHERE email='jane@horserace.local'), 'eeee1111-eeee-1111-eeee-111111111111', 'WIN',  2.50, 100000.00, 250000.00, 'WON',  CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days' + INTERVAL '10 minutes', 'IDEMP-PRED-008'),
    ('33000000-0000-0000-0000-000000000009', 'cccc1111-cccc-1111-cccc-111111111111', 'bbbbbbbb-0000-0000-0000-000000000003', 'eeee3333-eeee-3333-eeee-333333333333', 'WIN',  4.50, 40000.00, 180000.00, 'LOST', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days' + INTERVAL '10 minutes', 'IDEMP-PRED-009'),
    -- A VOID/REFUNDED on cancelled race RACE-008 (cccc0000...0008 is CANCELLED)
    ('33000000-0000-0000-0000-00000000000a', 'cccc0000-0000-0000-0000-000000000008', '77777777-0000-0000-0000-000000000005', NULL, 'EXACTA', NULL, 35000.00, NULL, 'REFUNDED', CURRENT_TIMESTAMP - INTERVAL '35 days', CURRENT_TIMESTAMP - INTERVAL '34 days', 'IDEMP-PRED-010'),
    ('33000000-0000-0000-0000-00000000000b', 'cccc0000-0000-0000-0000-000000000008', 'bbbbbbbb-0000-0000-0000-000000000001', NULL, 'QUINELLA', NULL, 20000.00, NULL, 'VOID', CURRENT_TIMESTAMP - INTERVAL '35 days', CURRENT_TIMESTAMP - INTERVAL '34 days', 'IDEMP-PRED-011');

-- ---------- PAYMENT_TRANSACTION (gateway txns; reach >=10) ----------
INSERT INTO payment_transaction (payment_txn_id, business_entity_type, business_entity_id, transaction_type, amount, currency_code, payment_method, payment_status, external_txn_ref, idempotency_key, gateway_provider, wallet_id, created_by_user_id) VALUES
    ('31000000-0000-0000-0000-000000000001', 'WALLET', '30000000-0000-0000-0000-000000000005', 'DEPOSIT', 2000000.00, 'VND', 'MOCK_CARD', 'SUCCESS', 'EXT-PAY-001', 'IDEMP-PAY-001', 'MOCK', '30000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000005'),
    ('31000000-0000-0000-0000-000000000002', 'WALLET', '30000000-0000-0000-0000-000000000008', 'DEPOSIT', 1200000.00, 'VND', 'MOCK_BANK', 'SUCCESS', 'EXT-PAY-002', 'IDEMP-PAY-002', 'MOCK', '30000000-0000-0000-0000-000000000008', 'bbbbbbbb-0000-0000-0000-000000000001'),
    ('31000000-0000-0000-0000-000000000003', 'WALLET', '30000000-0000-0000-0000-000000000009', 'DEPOSIT', 900000.00,  'VND', 'MOCK_CARD', 'SUCCESS', 'EXT-PAY-003', 'IDEMP-PAY-003', 'MOCK', '30000000-0000-0000-0000-000000000009', 'bbbbbbbb-0000-0000-0000-000000000002'),
    ('31000000-0000-0000-0000-000000000004', 'WALLET', '30000000-0000-0000-0000-000000000005', 'WITHDRAWAL', 100000.00, 'VND', 'MOCK_BANK', 'SUCCESS', 'EXT-PAY-004', 'IDEMP-PAY-004', 'MOCK', '30000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000005'),
    ('31000000-0000-0000-0000-000000000005', 'WALLET', '30000000-0000-0000-0000-000000000002', 'DEPOSIT', 15000000.00,'VND', 'MOCK_BANK', 'SUCCESS', 'EXT-PAY-005', 'IDEMP-PAY-005', 'MOCK', '30000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002'),
    ('31000000-0000-0000-0000-000000000006', 'WALLET', '30000000-0000-0000-0000-000000000008', 'WITHDRAWAL', 50000.00, 'VND', 'MOCK_CARD', 'PENDING', 'EXT-PAY-006', 'IDEMP-PAY-006', 'MOCK', '30000000-0000-0000-0000-000000000008', 'bbbbbbbb-0000-0000-0000-000000000001'),
    ('31000000-0000-0000-0000-000000000007', 'WALLET', '30000000-0000-0000-0000-00000000000a', 'DEPOSIT', 750000.00,  'VND', 'MOCK_CARD', 'FAILED', 'EXT-PAY-007', 'IDEMP-PAY-007', 'MOCK', '30000000-0000-0000-0000-00000000000a', 'bbbbbbbb-0000-0000-0000-000000000003'),
    ('31000000-0000-0000-0000-000000000008', 'WALLET', '30000000-0000-0000-0000-000000000003', 'PAYOUT', 125000.00, 'VND', 'INTERNAL', 'SUCCESS', 'EXT-PAY-008', 'IDEMP-PAY-008', 'MOCK', '30000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000001'),
    ('31000000-0000-0000-0000-000000000009', 'WALLET', '30000000-0000-0000-0000-000000000008', 'REFUND', 35000.00, 'VND', 'INTERNAL', 'REFUNDED', 'EXT-PAY-009', 'IDEMP-PAY-009', 'MOCK', '30000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000001'),
    ('31000000-0000-0000-0000-00000000000a', 'WALLET', '30000000-0000-0000-0000-000000000006', 'DEPOSIT', 8000000.00, 'VND', 'MOCK_BANK', 'SUCCESS', 'EXT-PAY-010', 'IDEMP-PAY-010', 'MOCK', '30000000-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000001'),
    ('31000000-0000-0000-0000-00000000000b', 'WALLET', '30000000-0000-0000-0000-000000000007', 'DEPOSIT', 6000000.00, 'VND', 'MOCK_CARD', 'CANCELLED', 'EXT-PAY-011', 'IDEMP-PAY-011', 'MOCK', '30000000-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000002');

-- ---------- WALLET_TRANSACTION (ledger; reach >=10) ----------
INSERT INTO wallet_transaction (wallet_txn_id, wallet_id, entry_type, txn_category, amount, balance_after, related_entity_type, related_entity_id, payment_txn_id) VALUES
    ('32000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000005', 'CREDIT', 'DEPOSIT',    2000000.00, 2000000.00, 'PAYMENT', '31000000-0000-0000-0000-000000000001', '31000000-0000-0000-0000-000000000001'),
    ('32000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000005', 'DEBIT',  'BET_STAKE',  50000.00,   1950000.00, 'PREDICTION', '33000000-0000-0000-0000-000000000001', NULL),
    ('32000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000005', 'CREDIT', 'BET_PAYOUT', 125000.00,  2075000.00, 'PREDICTION', '33000000-0000-0000-0000-000000000001', '31000000-0000-0000-0000-000000000008'),
    ('32000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000005', 'DEBIT',  'BET_STAKE',  60000.00,   2015000.00, 'PREDICTION', '33000000-0000-0000-0000-000000000004', NULL),
    ('32000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000005', 'CREDIT', 'BET_PAYOUT', 108000.00,  2123000.00, 'PREDICTION', '33000000-0000-0000-0000-000000000004', NULL),
    ('32000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000005', 'DEBIT',  'WITHDRAWAL', 100000.00,  2023000.00, 'PAYMENT', '31000000-0000-0000-0000-000000000004', '31000000-0000-0000-0000-000000000004'),
    ('32000000-0000-0000-0000-000000000007', '30000000-0000-0000-0000-000000000008', 'CREDIT', 'DEPOSIT',    1200000.00, 1200000.00, 'PAYMENT', '31000000-0000-0000-0000-000000000002', '31000000-0000-0000-0000-000000000002'),
    ('32000000-0000-0000-0000-000000000008', '30000000-0000-0000-0000-000000000008', 'DEBIT',  'BET_STAKE',  30000.00,   1170000.00, 'PREDICTION', '33000000-0000-0000-0000-000000000002', NULL),
    ('32000000-0000-0000-0000-000000000009', '30000000-0000-0000-0000-000000000009', 'CREDIT', 'DEPOSIT',    900000.00,  900000.00,  'PAYMENT', '31000000-0000-0000-0000-000000000003', '31000000-0000-0000-0000-000000000003'),
    ('32000000-0000-0000-0000-00000000000a', '20000000-0000-0000-0000-000000000003', 'CREDIT', 'BET_PAYOUT', 250000.00,  750000.00,  'PREDICTION', '33000000-0000-0000-0000-000000000008', NULL),
    ('32000000-0000-0000-0000-00000000000b', '20000000-0000-0000-0000-000000000003', 'CREDIT', 'REWARD',     200000.00,  950000.00,  'REWARD', '10000000-0000-0000-0000-000000000002', NULL),
    ('32000000-0000-0000-0000-00000000000c', '30000000-0000-0000-0000-000000000005', 'CREDIT', 'REFUND',     35000.00,   2058000.00, 'PREDICTION', '33000000-0000-0000-0000-00000000000a', '31000000-0000-0000-0000-000000000009');

-- ---------- PAYOUT (winning predictions; reach >=10 by adding more WON preds? we have 4 WON) ----------
-- We have WON predictions: 001,003,004,008. To reach >=10 payouts, add PENDING payouts for others too.
INSERT INTO payout (payout_id, prediction_id, payout_amount, wallet_txn_id, status, settled_by_user_id, settled_at) VALUES
    ('35000000-0000-0000-0000-000000000001', '33000000-0000-0000-0000-000000000001', 125000.00, '32000000-0000-0000-0000-000000000003', 'PAID', '77777777-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '9 days'),
    ('35000000-0000-0000-0000-000000000002', '33000000-0000-0000-0000-000000000003', 30000.00,  NULL, 'PAID', '77777777-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '9 days'),
    ('35000000-0000-0000-0000-000000000003', '33000000-0000-0000-0000-000000000004', 108000.00, '32000000-0000-0000-0000-000000000005', 'PAID', '77777777-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('35000000-0000-0000-0000-000000000004', '33000000-0000-0000-0000-000000000008', 250000.00, '32000000-0000-0000-0000-00000000000a', 'PAID', (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), CURRENT_TIMESTAMP - INTERVAL '30 days'),
    -- LOST predictions get zero/cancelled payout records for completeness
    ('35000000-0000-0000-0000-000000000005', '33000000-0000-0000-0000-000000000002', 0.00, NULL, 'CANCELLED', '77777777-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '9 days'),
    ('35000000-0000-0000-0000-000000000006', '33000000-0000-0000-0000-000000000005', 0.00, NULL, 'CANCELLED', '77777777-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('35000000-0000-0000-0000-000000000007', '33000000-0000-0000-0000-000000000009', 0.00, NULL, 'CANCELLED', (SELECT user_id FROM app_user WHERE email='admin@horserace.local'), CURRENT_TIMESTAMP - INTERVAL '30 days'),
    -- REFUNDED prediction payout
    ('35000000-0000-0000-0000-000000000008', '33000000-0000-0000-0000-00000000000a', 35000.00, '32000000-0000-0000-0000-00000000000c', 'PAID', '77777777-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '34 days'),
    -- Pending payouts for confirmed/pending predictions (future race)
    ('35000000-0000-0000-0000-000000000009', '33000000-0000-0000-0000-000000000006', 100000.00, NULL, 'PENDING', NULL, NULL),
    ('35000000-0000-0000-0000-00000000000a', '33000000-0000-0000-0000-000000000007', 30000.00,  NULL, 'PENDING', NULL, NULL),
    ('35000000-0000-0000-0000-00000000000b', '33000000-0000-0000-0000-00000000000b', 20000.00,  NULL, 'CANCELLED', '77777777-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '34 days');

-- ---------- REWARD (reach >=10) ----------
INSERT INTO reward (reward_id, user_id, reward_type, amount, title, description, status, expires_at, claimed_at) VALUES
    ('38000000-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000005', 'DAILY_LOGIN', 50000.00,  'Daily Login Bonus',    'Logged in today.',            'PENDING', CURRENT_TIMESTAMP + INTERVAL '1 day', NULL),
    ('38000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000005', 'MILESTONE',   200000.00, 'First Win Milestone',  'Won your first prediction.',  'CLAIMED', NULL, CURRENT_TIMESTAMP - INTERVAL '8 days'),
    ('38000000-0000-0000-0000-000000000003', 'bbbbbbbb-0000-0000-0000-000000000001', 'PROMOTION',   100000.00, 'Spring Promo',         'Spring promotion credit.',    'PENDING', CURRENT_TIMESTAMP + INTERVAL '10 days', NULL),
    ('38000000-0000-0000-0000-000000000004', 'bbbbbbbb-0000-0000-0000-000000000002', 'REFERRAL',    150000.00, 'Referral Bonus',       'Referred a friend.',          'CLAIMED', NULL, CURRENT_TIMESTAMP - INTERVAL '5 days'),
    ('38000000-0000-0000-0000-000000000005', 'bbbbbbbb-0000-0000-0000-000000000003', 'COMPENSATION',75000.00,  'Service Credit',       'Compensation for downtime.',  'EXPIRED', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL),
    ('38000000-0000-0000-0000-000000000006', '77777777-0000-0000-0000-000000000002', 'MILESTONE',   300000.00, 'Top Owner',            'Five approved registrations.','PENDING', CURRENT_TIMESTAMP + INTERVAL '30 days', NULL),
    ('38000000-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000001', 'PROMOTION',   120000.00, 'Loyalty Reward',       'Loyal owner reward.',         'CLAIMED', NULL, CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('38000000-0000-0000-0000-000000000008', '77777777-0000-0000-0000-000000000003', 'MILESTONE',   80000.00,  'Jockey Win Bonus',     'First race win as jockey.',   'PENDING', CURRENT_TIMESTAMP + INTERVAL '15 days', NULL),
    ('38000000-0000-0000-0000-000000000009', '99999999-0000-0000-0000-000000000001', 'DAILY_LOGIN', 50000.00,  'Daily Login Bonus',    'Logged in today.',            'PENDING', CURRENT_TIMESTAMP + INTERVAL '1 day', NULL),
    ('38000000-0000-0000-0000-00000000000a', 'bbbbbbbb-0000-0000-0000-000000000001', 'DAILY_LOGIN', 50000.00,  'Daily Login Bonus',    'Logged in today.',            'CLAIMED', NULL, CURRENT_TIMESTAMP - INTERVAL '1 day');

-- ---------- NOTIFICATION (reach >=10) ----------
INSERT INTO notification (notification_id, recipient_user_id, title, message, channel, delivery_status, is_read, sent_at, read_at) VALUES
    ('39000000-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000005', 'Prediction Won!', 'Your WIN bet on Crimson Comet paid out 125,000 VND.', 'IN_APP', 'SENT', TRUE,  CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '8 days'),
    ('39000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000005', 'Bet Confirmed',   'Your WIN bet on Sakura Spirit is confirmed.',        'IN_APP', 'SENT', FALSE, CURRENT_TIMESTAMP - INTERVAL '6 hours', NULL),
    ('39000000-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000003', 'Ride Accepted',   'You are confirmed to ride Crimson Comet in RACE-101.','IN_APP', 'SENT', TRUE,  CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days'),
    ('39000000-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000003', 'New Invitation',  'You have been invited to ride Sakura Spirit.',       'EMAIL',  'SENT', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 day', NULL),
    ('39000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000002', 'Registration Approved', 'Crimson Comet approved for Spring Championship.','IN_APP', 'SENT', TRUE,  CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days'),
    ('39000000-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000002', 'Registration Pending', 'Tokyo Bullet registration is under review.',     'IN_APP', 'PENDING', FALSE, NULL, NULL),
    ('39000000-0000-0000-0000-000000000007', '77777777-0000-0000-0000-000000000004', 'Race Assignment', 'You are assigned CHIEF referee for RACE-103.',       'IN_APP', 'SENT', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 day', NULL),
    ('39000000-0000-0000-0000-000000000008', 'aaaaaaaa-0000-0000-0000-000000000001', 'Race Assignment', 'You are assigned STEWARD for RACE-103.',             'IN_APP', 'SENT', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 day', NULL),
    ('39000000-0000-0000-0000-000000000009', '77777777-0000-0000-0000-000000000001', 'New Registration',  'A new registration awaits review.',                'IN_APP', 'SENT', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', NULL),
    ('39000000-0000-0000-0000-00000000000a', 'bbbbbbbb-0000-0000-0000-000000000001', 'Promo Credit',      'You received a 100,000 VND spring promo.',          'PUSH',   'SENT', TRUE,  CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '4 days'),
    ('39000000-0000-0000-0000-00000000000b', 'bbbbbbbb-0000-0000-0000-000000000002', 'Bet Lost',          'Your WIN bet on Iron Will did not place.',          'SMS',    'FAILED', FALSE, NULL, NULL);

-- ---------- ATTACHMENT (reach >=10) ----------
INSERT INTO attachment (attachment_id, owner_entity_type, owner_entity_id, object_key, file_name, mime_type, file_size, sensitivity_level, uploaded_by_user_id) VALUES
    ('3a000000-0000-0000-0000-000000000001', 'HORSE', 'a5a5a5a5-0000-0000-0000-000000000001', 'horses/crimson-comet/passport.pdf', 'passport.pdf', 'application/pdf', 204800, 'CONFIDENTIAL', '77777777-0000-0000-0000-000000000002'),
    ('3a000000-0000-0000-0000-000000000002', 'HORSE', 'a5a5a5a5-0000-0000-0000-000000000001', 'horses/crimson-comet/photo.jpg', 'photo.jpg', 'image/jpeg', 512000, 'PUBLIC', '77777777-0000-0000-0000-000000000002'),
    ('3a000000-0000-0000-0000-000000000003', 'HORSE', 'a5a5a5a5-0000-0000-0000-000000000003', 'horses/velvet-night/coggins.pdf', 'coggins.pdf', 'application/pdf', 153600, 'CONFIDENTIAL', '88888888-0000-0000-0000-000000000001'),
    ('3a000000-0000-0000-0000-000000000004', 'RACE', 'c6c6c6c6-0000-0000-0000-000000000001', 'races/race-101/photofinish.jpg', 'photofinish.jpg', 'image/jpeg', 768000, 'INTERNAL', '77777777-0000-0000-0000-000000000004'),
    ('3a000000-0000-0000-0000-000000000005', 'RACE', 'c6c6c6c6-0000-0000-0000-000000000002', 'races/race-102/photofinish.jpg', 'photofinish.jpg', 'image/jpeg', 742000, 'INTERNAL', '77777777-0000-0000-0000-000000000004'),
    ('3a000000-0000-0000-0000-000000000006', 'REFEREE_REPORT', 'b8b8b8b8-0000-0000-0000-000000000003', 'reports/whip-incident.pdf', 'whip-incident.pdf', 'application/pdf', 98000, 'RESTRICTED', 'aaaaaaaa-0000-0000-0000-000000000002'),
    ('3a000000-0000-0000-0000-000000000007', 'VIOLATION', 'b6b6b6b6-0000-0000-0000-000000000001', 'violations/footage-101.mp4', 'footage-101.mp4', 'video/mp4', 10485760, 'RESTRICTED', 'aaaaaaaa-0000-0000-0000-000000000002'),
    ('3a000000-0000-0000-0000-000000000008', 'HORSE', 'a5a5a5a5-0000-0000-0000-000000000005', 'horses/sakura-spirit/photo.jpg', 'photo.jpg', 'image/jpeg', 480000, 'PUBLIC', '88888888-0000-0000-0000-000000000002'),
    ('3a000000-0000-0000-0000-000000000009', 'TOURNAMENT', 'b5b5b5b5-0000-0000-0000-000000000001', 'tournaments/spring/poster.png', 'poster.png', 'image/png', 1024000, 'PUBLIC', '77777777-0000-0000-0000-000000000001'),
    ('3a000000-0000-0000-0000-00000000000a', 'USER', '77777777-0000-0000-0000-000000000003', 'users/jockey/license-scan.pdf', 'license-scan.pdf', 'application/pdf', 87000, 'CONFIDENTIAL', '77777777-0000-0000-0000-000000000003');

-- ---------- AUDIT_LOG (reach >=10) ----------
INSERT INTO audit_log (audit_log_id, actor_user_id, race_id, entity_type, entity_id, action_type, ip_address, device_info) VALUES
    ('3b000000-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000001', NULL, 'TOURNAMENT', 'b5b5b5b5-0000-0000-0000-000000000001', 'CREATE_TOURNAMENT', '10.0.0.1', 'Chrome/Windows'),
    ('3b000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000001', NULL, 'REGISTRATION', 'd5d5d5d5-0000-0000-0000-000000000001', 'APPROVE_REGISTRATION', '10.0.0.1', 'Chrome/Windows'),
    ('3b000000-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000001', 'RACE_RESULT', 'a7a7a7a7-0000-0000-0000-000000000001', 'PUBLISH_RESULT', '10.0.0.2', 'Firefox/macOS'),
    ('3b000000-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000004', 'c6c6c6c6-0000-0000-0000-000000000002', 'RACE_RESULT', 'a7a7a7a7-0000-0000-0000-000000000003', 'AMEND_RESULT', '10.0.0.2', 'Firefox/macOS'),
    ('3b000000-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000002', 'c6c6c6c6-0000-0000-0000-000000000002', 'PENALTY', 'b9b9b9b9-0000-0000-0000-000000000001', 'ISSUE_PENALTY', '10.0.0.3', 'Chrome/Android'),
    ('3b000000-0000-0000-0000-000000000006', '77777777-0000-0000-0000-000000000002', NULL, 'HORSE', 'a5a5a5a5-0000-0000-0000-000000000001', 'CREATE_HORSE', '10.0.0.4', 'Safari/iOS'),
    ('3b000000-0000-0000-0000-000000000007', '77777777-0000-0000-0000-000000000005', 'c6c6c6c6-0000-0000-0000-000000000001', 'PREDICTION', '33000000-0000-0000-0000-000000000001', 'PLACE_PREDICTION', '10.0.0.5', 'Chrome/Windows'),
    ('3b000000-0000-0000-0000-000000000008', '77777777-0000-0000-0000-000000000003', NULL, 'JOCKEY_ASSIGNMENT', 'f5f5f5f5-0000-0000-0000-000000000001', 'ACCEPT_INVITATION', '10.0.0.6', 'Chrome/Windows'),
    ('3b000000-0000-0000-0000-000000000009', '77777777-0000-0000-0000-000000000001', NULL, 'USER', '77777777-0000-0000-0000-000000000005', 'LOGIN', '10.0.0.7', 'Chrome/Windows'),
    ('3b000000-0000-0000-0000-00000000000a', '77777777-0000-0000-0000-000000000001', NULL, 'PAYOUT', '35000000-0000-0000-0000-000000000001', 'SETTLE_PAYOUT', '10.0.0.1', 'Chrome/Windows');

-- ---------- REFRESH_TOKEN (>=10 valid, future expiry, unique hashes) ----------
INSERT INTO refresh_token (token_id, user_id, token_hash, expires_at, revoked, user_agent) VALUES
    ('3c000000-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000001', 'rt-hash-0000000000000000000000000000001', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Chrome/Windows'),
    ('3c000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002', 'rt-hash-0000000000000000000000000000002', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Safari/iOS'),
    ('3c000000-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000003', 'rt-hash-0000000000000000000000000000003', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Chrome/Android'),
    ('3c000000-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000004', 'rt-hash-0000000000000000000000000000004', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Firefox/macOS'),
    ('3c000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000005', 'rt-hash-0000000000000000000000000000005', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Chrome/Windows'),
    ('3c000000-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000001', 'rt-hash-0000000000000000000000000000006', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Edge/Windows'),
    ('3c000000-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000002', 'rt-hash-0000000000000000000000000000007', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Chrome/Linux'),
    ('3c000000-0000-0000-0000-000000000008', '99999999-0000-0000-0000-000000000001', 'rt-hash-0000000000000000000000000000008', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Chrome/Windows'),
    ('3c000000-0000-0000-0000-000000000009', 'bbbbbbbb-0000-0000-0000-000000000001', 'rt-hash-0000000000000000000000000000009', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Safari/iOS'),
    ('3c000000-0000-0000-0000-00000000000a', 'bbbbbbbb-0000-0000-0000-000000000002', 'rt-hash-0000000000000000000000000000010', CURRENT_TIMESTAMP + INTERVAL '30 days', FALSE, 'Chrome/Android');

-- ---------- EMAIL_VERIFICATION_TOKEN (>=10, future expiry) ----------
INSERT INTO email_verification_token (token_id, user_id, code_hash, expires_at, used) VALUES
    ('3d000000-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000001', 'evt-hash-001', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002', 'evt-hash-002', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000003', 'evt-hash-003', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000004', 'evt-hash-004', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000005', 'evt-hash-005', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000001', 'evt-hash-006', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000002', 'evt-hash-007', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-000000000008', '99999999-0000-0000-0000-000000000001', 'evt-hash-008', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-000000000009', '99999999-0000-0000-0000-000000000002', 'evt-hash-009', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3d000000-0000-0000-0000-00000000000a', 'bbbbbbbb-0000-0000-0000-000000000001', 'evt-hash-010', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE);

-- ---------- EMAIL_CHANGE_REQUEST (>=10, future expiry) ----------
INSERT INTO email_change_request (request_id, user_id, new_email, code_hash, expires_at, consumed) VALUES
    ('3e000000-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000001', 'admin.new1@test.local', 'ecr-hash-001', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002', 'owner.new1@test.local', 'ecr-hash-002', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000003', 'jockey.new1@test.local','ecr-hash-003', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000004', 'referee.new1@test.local','ecr-hash-004', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000005', 'spec.new1@test.local',  'ecr-hash-005', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000001', 'diana.new1@test.local', 'ecr-hash-006', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000002', 'hiroshi.new1@test.local','ecr-hash-007', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-000000000008', '99999999-0000-0000-0000-000000000001', 'lanf.new1@test.local',  'ecr-hash-008', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-000000000009', 'bbbbbbbb-0000-0000-0000-000000000001', 'tom.new1@test.local',   'ecr-hash-009', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE),
    ('3e000000-0000-0000-0000-00000000000a', 'bbbbbbbb-0000-0000-0000-000000000002', 'lily.new1@test.local',  'ecr-hash-010', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE);

-- ---------- PASSWORD_RESET_TOKEN (>=10, future expiry) ----------
INSERT INTO password_reset_token (token_id, user_id, code_hash, expires_at, used) VALUES
    ('3f000000-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000001', 'prt-hash-001', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002', 'prt-hash-002', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000003', 'prt-hash-003', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000004', 'prt-hash-004', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000005', 'prt-hash-005', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000001', 'prt-hash-006', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000002', 'prt-hash-007', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-000000000008', '99999999-0000-0000-0000-000000000001', 'prt-hash-008', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-000000000009', 'bbbbbbbb-0000-0000-0000-000000000001', 'prt-hash-009', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE),
    ('3f000000-0000-0000-0000-00000000000a', 'bbbbbbbb-0000-0000-0000-000000000002', 'prt-hash-010', CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE);

-- =========================================================
-- END COMPREHENSIVE DEMO DATASET
-- =========================================================

-- =========================================================
-- CONSOLIDATION: reduce to exactly the 5 canonical @test.local logins.
-- All data created above for the ~12 EXTRA @test.local users is repointed,
-- by role, onto the matching canonical account; rows that would violate a
-- UNIQUE/PK after repointing are dropped first. Then the extra users (and
-- their PK/UNIQUE-keyed jockey_profile + wallet rows) are deleted.
--   extra OWNERS  (88888888-*)            -> owner@test.local     (77777777-*2)
--   extra JOCKEYS (99999999-*)            -> jockey@test.local    (77777777-*3)
--   extra REFEREES(aaaaaaaa-*)            -> referee@test.local   (77777777-*4)
--   extra SPECTAT.(bbbbbbbb-*)            -> spectator@test.local (77777777-*5)
-- =========================================================

-- ---------- 0) Drop rows that would collide on a UNIQUE after repointing ----------

-- referee_assignment UNIQUE(race_id, referee_user_id): drop the extra-ref row on
-- any race where the canonical referee is already assigned.
DELETE FROM referee_assignment ra
USING referee_assignment keep
WHERE ra.referee_user_id IN ('aaaaaaaa-0000-0000-0000-000000000001',
                             'aaaaaaaa-0000-0000-0000-000000000002')
  AND keep.referee_user_id = '77777777-0000-0000-0000-000000000004'
  AND keep.race_id = ra.race_id;

-- jockey_assignment / race_result / prediction etc. are UNIQUE on (entry_id) or
-- (entry-scoped) keys that already differ per row, so repointing the user FK is safe.
-- prediction UNIQUE(race_id, spectator_user_id, prediction_type, predicted_entry_id):
-- drop any extra-spectator prediction that would duplicate one the canonical
-- spectator already holds for the same (race, type, entry).
DELETE FROM payout p
USING prediction ex, prediction keep
WHERE p.prediction_id = ex.prediction_id
  AND ex.spectator_user_id IN ('bbbbbbbb-0000-0000-0000-000000000001',
                               'bbbbbbbb-0000-0000-0000-000000000002',
                               'bbbbbbbb-0000-0000-0000-000000000003')
  AND keep.spectator_user_id = '77777777-0000-0000-0000-000000000005'
  AND keep.race_id = ex.race_id
  AND keep.prediction_type = ex.prediction_type
  AND keep.predicted_entry_id IS NOT DISTINCT FROM ex.predicted_entry_id;
DELETE FROM prediction ex
USING prediction keep
WHERE ex.spectator_user_id IN ('bbbbbbbb-0000-0000-0000-000000000001',
                               'bbbbbbbb-0000-0000-0000-000000000002',
                               'bbbbbbbb-0000-0000-0000-000000000003')
  AND keep.spectator_user_id = '77777777-0000-0000-0000-000000000005'
  AND keep.race_id = ex.race_id
  AND keep.prediction_type = ex.prediction_type
  AND keep.predicted_entry_id IS NOT DISTINCT FROM ex.predicted_entry_id;

-- standing UNIQUE(tournament_id, subject_type, subject_id): drop extra-jockey
-- standings on any tournament where the canonical jockey already has a JOCKEY row.
DELETE FROM standing ex
USING standing keep
WHERE ex.subject_type = 'JOCKEY'
  AND ex.subject_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002',
                        '99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004',
                        '99999999-0000-0000-0000-000000000005')
  AND keep.subject_type = 'JOCKEY'
  AND keep.subject_id = '77777777-0000-0000-0000-000000000003'
  AND keep.tournament_id = ex.tournament_id;

-- ---------- 1) Repoint every FK from extra users -> canonical (by role) ----------

-- OWNERS -> owner@test.local
UPDATE horse                   SET owner_user_id       = '77777777-0000-0000-0000-000000000002' WHERE owner_user_id       IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE tournament_registration SET owner_user_id       = '77777777-0000-0000-0000-000000000002' WHERE owner_user_id       IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE tournament_registration SET approved_by_user_id = '77777777-0000-0000-0000-000000000002' WHERE approved_by_user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE jockey_assignment       SET assigned_by_user_id = '77777777-0000-0000-0000-000000000002' WHERE assigned_by_user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');

-- JOCKEYS -> jockey@test.local
UPDATE jockey_assignment SET jockey_user_id = '77777777-0000-0000-0000-000000000003' WHERE jockey_user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE race_violation    SET jockey_user_id = '77777777-0000-0000-0000-000000000003' WHERE jockey_user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE standing          SET subject_id     = '77777777-0000-0000-0000-000000000003' WHERE subject_type = 'JOCKEY' AND subject_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');

-- REFEREES -> referee@test.local
UPDATE referee_assignment    SET referee_user_id      = '77777777-0000-0000-0000-000000000004' WHERE referee_user_id      IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE referee_assignment    SET created_by_user_id   = '77777777-0000-0000-0000-000000000004' WHERE created_by_user_id   IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE referee_report        SET author_user_id       = '77777777-0000-0000-0000-000000000004' WHERE author_user_id       IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE race_violation        SET reported_by_user_id  = '77777777-0000-0000-0000-000000000004' WHERE reported_by_user_id  IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE race_violation        SET ruled_by_user_id     = '77777777-0000-0000-0000-000000000004' WHERE ruled_by_user_id     IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE race_entry_inspection SET inspected_by_user_id = '77777777-0000-0000-0000-000000000004' WHERE inspected_by_user_id IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE penalty               SET issued_by_user_id    = '77777777-0000-0000-0000-000000000004' WHERE issued_by_user_id    IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE race_result           SET approved_by_user_id  = '77777777-0000-0000-0000-000000000004' WHERE approved_by_user_id  IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE race_result_version   SET changed_by_user_id   = '77777777-0000-0000-0000-000000000004' WHERE changed_by_user_id   IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE audit_log             SET actor_user_id        = '77777777-0000-0000-0000-000000000004' WHERE actor_user_id        IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE attachment            SET uploaded_by_user_id  = '77777777-0000-0000-0000-000000000004' WHERE uploaded_by_user_id  IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
-- attachment uploaders also include extra owners -> owner@test.local
UPDATE attachment            SET uploaded_by_user_id  = '77777777-0000-0000-0000-000000000002' WHERE uploaded_by_user_id  IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');

-- SPECTATORS -> spectator@test.local
UPDATE prediction   SET spectator_user_id = '77777777-0000-0000-0000-000000000005' WHERE spectator_user_id IN ('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');

-- reward + notification are emitted to users of ANY role -> repoint by role group.
UPDATE reward       SET user_id           = '77777777-0000-0000-0000-000000000002' WHERE user_id           IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE reward       SET user_id           = '77777777-0000-0000-0000-000000000003' WHERE user_id           IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE reward       SET user_id           = '77777777-0000-0000-0000-000000000004' WHERE user_id           IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE reward       SET user_id           = '77777777-0000-0000-0000-000000000005' WHERE user_id           IN ('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');
UPDATE notification SET recipient_user_id = '77777777-0000-0000-0000-000000000002' WHERE recipient_user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE notification SET recipient_user_id = '77777777-0000-0000-0000-000000000003' WHERE recipient_user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE notification SET recipient_user_id = '77777777-0000-0000-0000-000000000004' WHERE recipient_user_id IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE notification SET recipient_user_id = '77777777-0000-0000-0000-000000000005' WHERE recipient_user_id IN ('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');

-- payment_transaction.created_by_user_id may point at any extra user -> canonical by role.
UPDATE payment_transaction SET created_by_user_id = '77777777-0000-0000-0000-000000000002' WHERE created_by_user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE payment_transaction SET created_by_user_id = '77777777-0000-0000-0000-000000000003' WHERE created_by_user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE payment_transaction SET created_by_user_id = '77777777-0000-0000-0000-000000000004' WHERE created_by_user_id IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002');
UPDATE payment_transaction SET created_by_user_id = '77777777-0000-0000-0000-000000000005' WHERE created_by_user_id IN ('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');

-- ---------- 2) Token tables FK to user_id (hashes already unique) -> canonical by role ----------
UPDATE refresh_token            SET user_id = '77777777-0000-0000-0000-000000000002' WHERE user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE refresh_token            SET user_id = '77777777-0000-0000-0000-000000000003' WHERE user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE refresh_token            SET user_id = '77777777-0000-0000-0000-000000000005' WHERE user_id IN ('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');
UPDATE email_verification_token SET user_id = '77777777-0000-0000-0000-000000000002' WHERE user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE email_verification_token SET user_id = '77777777-0000-0000-0000-000000000003' WHERE user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE email_verification_token SET user_id = '77777777-0000-0000-0000-000000000005' WHERE user_id IN ('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');
UPDATE email_change_request     SET user_id = '77777777-0000-0000-0000-000000000002' WHERE user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE email_change_request     SET user_id = '77777777-0000-0000-0000-000000000003' WHERE user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE email_change_request     SET user_id = '77777777-0000-0000-0000-000000000005' WHERE user_id IN ('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');
UPDATE password_reset_token     SET user_id = '77777777-0000-0000-0000-000000000002' WHERE user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002');
UPDATE password_reset_token     SET user_id = '77777777-0000-0000-0000-000000000003' WHERE user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');
UPDATE password_reset_token     SET user_id = '77777777-0000-0000-0000-000000000005' WHERE user_id IN ('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');

-- ---------- 3) Delete rows keyed by the extra user that cannot be repointed ----------
-- jockey_profile PK = jockey_user_id (canonical jockey already has a profile).
DELETE FROM jockey_profile WHERE jockey_user_id IN ('99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005');

-- wallet has UNIQUE(user_id); canonical users already own wallets, so the extra
-- wallets and everything pointing at them must go. wallet_transaction.wallet_id is
-- NOT NULL, so delete those ledger rows; payment_transaction.wallet_id is nullable
-- so just detach it (keeps the payment txns as standalone gateway records).
DELETE FROM wallet_transaction  WHERE wallet_id IN (SELECT wallet_id FROM wallet WHERE user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005','bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003'));
UPDATE payment_transaction SET wallet_id = NULL WHERE wallet_id IN (SELECT wallet_id FROM wallet WHERE user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005','bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003'));
DELETE FROM wallet WHERE user_id IN ('88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002','99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004','99999999-0000-0000-0000-000000000005','bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002','bbbbbbbb-0000-0000-0000-000000000003');

-- ---------- 4) Finally, delete the EXTRA @test.local app_user rows ----------
DELETE FROM app_user WHERE user_id IN (
    '88888888-0000-0000-0000-000000000001','88888888-0000-0000-0000-000000000002',
    '99999999-0000-0000-0000-000000000001','99999999-0000-0000-0000-000000000002',
    '99999999-0000-0000-0000-000000000003','99999999-0000-0000-0000-000000000004',
    '99999999-0000-0000-0000-000000000005',
    'aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002',
    'bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000002',
    'bbbbbbbb-0000-0000-0000-000000000003');

-- =========================================================
-- MEMBERSHIP_APPLICATION seed (Referee Applicant Onboarding, FE-v2)
-- reviewed rows use CURRENT_TIMESTAMP so they fall in the "today" stats window.
-- reviewed_by_user_id -> existing referee 'robert@horserace.local'.
-- =========================================================
INSERT INTO membership_application
    (application_code, requested_role, status, priority, full_name, date_of_birth, tax_id, email, phone,
     avatar_url, location, org_name, id_verification_status, id_document_ref, license_class, license_status,
     license_valid_until, background_check_status, submitted_at, reviewed_at, reviewed_by_user_id,
     rejection_reason, requested_info_note)
VALUES
    -- PENDING / URGENT owner with full dossier
    ('APP-8832','OWNER','PENDING','URGENT','Jonathan Pierce Sterling','1978-05-12','111-22-4421',
     'jonathan.sterling@example.com','+1 (555) 012-9932',NULL,'Lexington, Kentucky','Sterling Equine Holdings',
     'VALID','Passport #A2399201','Class A','ACTIVE','2026-12-31','PASSED',
     CURRENT_TIMESTAMP - INTERVAL '2 hours',NULL,NULL,NULL,NULL),
    -- PENDING normal trainer
    ('APP-8833','TRAINER','PENDING','NORMAL','Maria Gonzalez','1985-09-30','111-22-7788',
     'maria.gonzalez@example.com','+1 (555) 220-1188',NULL,'Ocala, Florida','Gonzalez Training Stables',
     'PENDING','Driver License #FL88231','Class B','ACTIVE','2027-06-30','PENDING',
     CURRENT_TIMESTAMP - INTERVAL '1 day',NULL,NULL,NULL,NULL),
    -- PENDING vet
    ('APP-8834','VET','PENDING','NORMAL','Dr. Henry Albright','1972-01-15','111-33-1100',
     'henry.albright@example.com','+1 (555) 776-2231',NULL,'Saratoga Springs, New York','Albright Equine Vet Clinic',
     'VALID','Vet License #VET-77120',NULL,NULL,NULL,'PASSED',
     CURRENT_TIMESTAMP - INTERVAL '3 days',NULL,NULL,NULL,NULL),
    -- PENDING jockey
    ('APP-8835','JOCKEY','PENDING','NORMAL','Liam OConnor','1996-07-22','111-44-5566',
     'liam.oconnor@example.com','+1 (555) 330-9087',NULL,'Louisville, Kentucky',NULL,
     'VALID','Passport #B7781234','Class C','ACTIVE','2026-03-31','PASSED',
     CURRENT_TIMESTAMP - INTERVAL '5 hours',NULL,NULL,NULL,NULL),
    -- UNDER_REVIEW owner
    ('APP-8836','OWNER','UNDER_REVIEW','NORMAL','Aisha Rahman','1989-11-03','111-55-2244',
     'aisha.rahman@example.com','+1 (555) 901-3322',NULL,'Dubai, UAE','Rahman Bloodstock LLC',
     'PENDING','Passport #C9921001','Class A','ACTIVE','2028-01-31','PENDING',
     CURRENT_TIMESTAMP - INTERVAL '2 days',NULL,NULL,NULL,NULL),
    -- INFO_REQUESTED trainer
    ('APP-8837','TRAINER','INFO_REQUESTED','NORMAL','Carlos Mendes','1980-02-28','111-66-3311',
     'carlos.mendes@example.com','+1 (555) 412-7765',NULL,'San Diego, California','Mendes Racing',
     'FAILED','Driver License #CA55120','Class B','EXPIRED','2024-12-31','PENDING',
     CURRENT_TIMESTAMP - INTERVAL '4 days',NULL,NULL,NULL,'Please re-upload a clear copy of your government ID; the current scan is unreadable.'),
    -- APPROVED today (owner) -> counts toward approvedToday
    ('APP-8838','OWNER','APPROVED','NORMAL','Emily Watson','1983-06-18','111-77-9090',
     'emily.watson@example.com','+1 (555) 553-2210',NULL,'Newmarket, England','Watson Racing Partners',
     'VALID','Passport #D1209887','Class A','ACTIVE','2027-09-30','PASSED',
     CURRENT_TIMESTAMP - INTERVAL '6 days',CURRENT_TIMESTAMP,
     '55555555-5555-5555-5555-555555555555',NULL,NULL),
    -- APPROVED today (vet) -> counts toward approvedToday
    ('APP-8839','VET','APPROVED','NORMAL','Dr. Nina Petrova','1979-04-09','111-88-1212',
     'nina.petrova@example.com','+1 (555) 661-4490',NULL,'Chantilly, France','Petrova Equine Health',
     'VALID','Vet License #VET-44021',NULL,NULL,NULL,'PASSED',
     CURRENT_TIMESTAMP - INTERVAL '7 days',CURRENT_TIMESTAMP,
     '55555555-5555-5555-5555-555555555555',NULL,NULL),
    -- REJECTED today (jockey) -> counts toward rejectedToday
    ('APP-8840','JOCKEY','REJECTED','NORMAL','Tom Blake','1999-12-01','111-99-7373',
     'tom.blake@example.com','+1 (555) 778-6543',NULL,'Dublin, Ireland',NULL,
     'FAILED','Passport #E5567211','Class C','NONE',NULL,'FAILED',
     CURRENT_TIMESTAMP - INTERVAL '8 days',CURRENT_TIMESTAMP,
     '55555555-5555-5555-5555-555555555555','Background check failed; prior racing suspension on record.',NULL),
    -- Older REJECTED (NOT today) for history of tom.blake
    ('APP-8820','JOCKEY','REJECTED','NORMAL','Tom Blake','1999-12-01','111-99-7373',
     'tom.blake@example.com','+1 (555) 778-6543',NULL,'Dublin, Ireland',NULL,
     'FAILED','Passport #E5567211','Class C','NONE',NULL,'FAILED',
     CURRENT_TIMESTAMP - INTERVAL '90 days',CURRENT_TIMESTAMP - INTERVAL '88 days',
     '55555555-5555-5555-5555-555555555555','Incomplete documentation submitted.',NULL),
    -- PENDING owner whose email matches an existing seeded user (maria@horserace.local) for horsesRegistered>0
    ('APP-8841','OWNER','PENDING','NORMAL','Maria Existing','1990-03-14','111-10-2020',
     'maria@horserace.local','+1 (555) 100-2000',NULL,'Riyadh, KSA','Maria Stables',
     'VALID','Passport #F0011223','Class A','ACTIVE','2027-12-31','PASSED',
     CURRENT_TIMESTAMP - INTERVAL '12 hours',NULL,NULL,NULL,NULL);
