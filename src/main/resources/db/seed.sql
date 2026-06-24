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
    ('SPECTATOR',    'Spectator',      'Watches and predicts',    'ACTIVE');

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
    ('PROFILE_MANAGE',       'Manage own profile');

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
    ('RACE_REFEREE','PROFILE_MANAGE'),
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
