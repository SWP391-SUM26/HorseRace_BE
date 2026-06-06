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

-- Seed Jockey Profiles
INSERT INTO jockey_profile (jockey_user_id, license_no, body_weight, height_cm, experience_yrs, win_count, bio) VALUES
    ('11111111-1111-1111-1111-111111111111', 'LIC-ALEX-001', 54.50, 165.00, 8, 142, 'Top jockey in the regional division, highly skilled in turf courses.'),
    ('22222222-2222-2222-2222-222222222222', 'LIC-IRAD-002', 52.00, 162.00, 10, 230, 'Elite class jockey, multiple champion in various stakes.');

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
INSERT INTO race (race_id, tournament_id, race_code, name, race_type, distance_meter, track_condition, scheduled_start_at, status) VALUES
    ('cccc1111-cccc-1111-cccc-111111111111',
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'RACE-001', 'Belmont Autumn Stakes', 'FLAT', 2000, 'GOOD',
        '2024-10-24 14:30:00+07', 'SCHEDULED'),
    ('cccc2222-cccc-2222-cccc-222222222222',
        'bbbb1111-bbbb-1111-bbbb-111111111111',
        'RACE-002', 'Epsom Derby Qualifier', 'FLAT', 1600, 'GOOD',
        '2024-11-02 13:00:00+07', 'SCHEDULED');

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
INSERT INTO race_entry (entry_id, registration_id, race_id, entry_code, entry_no, lane_no, status) VALUES
    ('eeee1111-eeee-1111-eeee-111111111111',
        'dddd1111-dddd-1111-dddd-111111111111',
        'cccc1111-cccc-1111-cccc-111111111111',
        'ENT-001', 1, 1, 'ENTERED'),
    ('eeee2222-eeee-2222-eeee-222222222222',
        'dddd2222-dddd-2222-dddd-222222222222',
        'cccc2222-cccc-2222-cccc-222222222222',
        'ENT-002', 2, 2, 'ENTERED'),
    ('eeee3333-eeee-3333-eeee-333333333333',
        'dddd3333-dddd-3333-dddd-333333333333',
        'cccc1111-cccc-1111-cccc-111111111111',
        'ENT-003', 3, 3, 'ENTERED'),
    ('eeee4444-eeee-4444-eeee-444444444444',
        'dddd4444-dddd-4444-dddd-444444444444',
        'cccc1111-cccc-1111-cccc-111111111111',
        'ENT-004', 4, 4, 'ENTERED');

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
    -- Sapphire Wind → Alex Mercer (INVITED/PENDING for Race 1)
    ('ffff3333-ffff-3333-ffff-333333333333',
        'eeee3333-eeee-3333-eeee-333333333333',
        '11111111-1111-1111-1111-111111111111',
        'INVITED', CURRENT_TIMESTAMP, NULL,
        (SELECT user_id FROM app_user WHERE email = 'owner@horserace.local'));