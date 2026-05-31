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