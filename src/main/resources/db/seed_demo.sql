-- =========================================================
-- SEED DEMO DATA  (môi trường TEST/DEV)
-- =========================================================
-- Chạy SAU 01-schema.sql và 02-seed.sql (Docker: 03-seed-demo.sql).
-- seed.sql  = bootstrap tối thiểu (5 role, 25 permission, ma trận quyền, admin, house wallet).
-- seed_demo.sql (file này) = data test cho toàn bộ 43 bảng.
--
-- QUY ƯỚC:
--   * UUID hardcode tường minh, prefix theo bảng -> đọc & nối FK dễ, deterministic 100%.
--       11111111 app_user    22222222 horse       33333333 venue      44444444 tournament
--       55555555 race        66666666 registration 77777777 race_entry 88888888 race_result
--       99999999 prediction  aaaaaaaa wallet
--   * Ảnh dùng URL Cloudinary THẬT (cloud qtpgbwsh) — upload bằng
--     scripts/seed/upload-cloudinary.sh. URL không kèm version -> re-upload không làm chết link.
--   * attachment.object_key trỏ file THẬT trên disk local (uploads/) — theo thiết kế,
--     attachment KHÔNG bao giờ lên CDN public (xem AttachmentServiceImpl).
--   * MÃ NGHIỆP VỤ theo đúng format generator trong code, để data seed và data tạo qua UI
--     nhìn giống nhau: HRS%04d, USR%04d, REG%05d, ENT%05d, RACE%05d, TRN%05d,
--     REF-<6 hex>, APP-<8 hex>. Ngoại lệ duy nhất: prize_code dạng PRZ-<O|J>-<entry_id> —
--     đó là thứ RaceResultServiceImpl thực sự sinh ra (xấu, đã ghi nhận là nợ kỹ thuật).
--
-- TÀI KHOẢN TEST — 28 user, TẤT CẢ email_verified = TRUE, password chung Test@1234:
--   admin1..5@horserace.local        (ADMIN)
--   horseowner1..5@horserace.local   (HORSE_OWNER)
--   jockey1..8@horserace.local       (JOCKEY — 8 nài: race 6 ngựa cần 6 nài riêng)
--   referee1 => hantnse180242@fpt.edu.vn (RACE_REFEREE — hộp thư THẬT, vì nộp biên bản
--                                        cần mã OTP gửi qua email); referee2..5@horserace.local
--   spectator1..5@horserace.local    (SPECTATOR)
--   (admin@horserace.local / admin123 nằm ở seed.sql, không đụng tới — đây là ví nhà cái)
--
-- NGUYÊN TẮC: mọi dòng trong file này phải là trạng thái mà API CÓ THỂ tạo ra được.
--   * 26 ngựa: UNIQUE(tournament_id, horse_id) + registration trỏ 1 race => 1 ngựa chạy tối đa
--     1 race/giải. TRN00003 có 4 race nên riêng nó đã cần 22 ngựa khác nhau.
--   * 1 registration ↔ 1 entry ↔ 1 race. Mỗi registration APPROVED có dossier do CHÍNH chủ ngựa
--     upload (approveRegistration bắt buộc).
--   * Tiền: rake_percent là PHÂN SỐ (0.10 = 10%). Toàn bộ payout/prize/ledger của 2 race lịch sử
--     là output CHẠY THẬT của certify + settle rồi dump ngược lại — không con số nào tính tay.
-- =========================================================

-- Guard: fail fast with ONE clear message instead of 50+ cascading FK errors if seed.sql
-- (roles/permissions/admin bootstrap) hasn't run yet — e.g. after a fresh schema_v4.sql reset.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM role WHERE role_code = 'ADMIN') THEN
        RAISE EXCEPTION 'seed_demo.sql needs seed.sql to run first (role table is empty). Run: psql "$CONN" -f src/main/resources/db/seed.sql';
    END IF;
END $$;

-- =========================================================
-- APP_USER — 25 user, 5/role, verified + ACTIVE + KYC VERIFIED hết
-- =========================================================
INSERT INTO app_user (user_id, role_id, user_code, full_name, email, phone, password_hash,
                      avatar_url, status, kyc_status, email_verified, last_login_at) VALUES
-- ADMIN
('11111111-0000-4000-8000-000000000001', (SELECT role_id FROM role WHERE role_code='ADMIN'), 'USR0002', 'Nguyễn Quản Trị',   'admin1@horserace.local',      '0900000002', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-01.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '2 hours'),
('11111111-0000-4000-8000-000000000002', (SELECT role_id FROM role WHERE role_code='ADMIN'), 'USR0003', 'Trần Điều Hành',    'admin2@horserace.local',      '0900000003', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-02.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '1 day'),
('11111111-0000-4000-8000-000000000003', (SELECT role_id FROM role WHERE role_code='ADMIN'), 'USR0004', 'Lê Giám Sát',       'admin3@horserace.local',      '0900000004', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-03.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '3 days'),
('11111111-0000-4000-8000-000000000004', (SELECT role_id FROM role WHERE role_code='ADMIN'), 'USR0005', 'Phạm Vận Hành',     'admin4@horserace.local',      '0900000005', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-04.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '5 days'),
('11111111-0000-4000-8000-000000000005', (SELECT role_id FROM role WHERE role_code='ADMIN'), 'USR0006', 'Hoàng Kỹ Thuật',    'admin5@horserace.local',      '0900000006', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-05.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '8 days'),
-- HORSE_OWNER
('11111111-0000-4000-8000-000000000011', (SELECT role_id FROM role WHERE role_code='HORSE_OWNER'), 'USR0007', 'Đặng Minh Chủ',   'horseowner1@horserace.local', '0900000007', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-06.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '4 hours'),
('11111111-0000-4000-8000-000000000012', (SELECT role_id FROM role WHERE role_code='HORSE_OWNER'), 'USR0008', 'Vũ Thanh Trại',   'horseowner2@horserace.local', '0900000008', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-07.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '2 days'),
('11111111-0000-4000-8000-000000000013', (SELECT role_id FROM role WHERE role_code='HORSE_OWNER'), 'USR0009', 'Bùi Đại Phát',    'horseowner3@horserace.local', '0900000009', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-08.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '6 days'),
('11111111-0000-4000-8000-000000000014', (SELECT role_id FROM role WHERE role_code='HORSE_OWNER'), 'USR0010', 'Ngô Kim Long',    'horseowner4@horserace.local', '0900000010', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-09.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '9 days'),
('11111111-0000-4000-8000-000000000015', (SELECT role_id FROM role WHERE role_code='HORSE_OWNER'), 'USR0011', 'Dương Bảo Ngọc',  'horseowner5@horserace.local', '0900000011', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-10.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '12 days'),
-- JOCKEY
('11111111-0000-4000-8000-000000000021', (SELECT role_id FROM role WHERE role_code='JOCKEY'), 'USR0012', 'Lý Tuấn Kiệt',   'jockey1@horserace.local', '0900000012', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-01.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '1 hour'),
('11111111-0000-4000-8000-000000000022', (SELECT role_id FROM role WHERE role_code='JOCKEY'), 'USR0013', 'Trịnh Gia Huy',  'jockey2@horserace.local', '0900000013', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-02.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '1 day'),
('11111111-0000-4000-8000-000000000023', (SELECT role_id FROM role WHERE role_code='JOCKEY'), 'USR0014', 'Cao Minh Nhật',  'jockey3@horserace.local', '0900000014', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-03.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '4 days'),
('11111111-0000-4000-8000-000000000024', (SELECT role_id FROM role WHERE role_code='JOCKEY'), 'USR0015', 'Đỗ Thành Đạt',   'jockey4@horserace.local', '0900000015', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-04.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '7 days'),
('11111111-0000-4000-8000-000000000025', (SELECT role_id FROM role WHERE role_code='JOCKEY'), 'USR0016', 'Hồ Anh Khoa',    'jockey5@horserace.local', '0900000016', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-05.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '10 days'),
-- JOCKEY (bổ sung) — một race 6 ngựa cần 6 nài riêng: JockeyAssignmentServiceImpl chặn 1 nài
-- ACCEPTED 2 ride trong cùng race, nên 5 nài không đủ cho race đông nhất.
('11111111-0000-4000-8000-000000000026', (SELECT role_id FROM role WHERE role_code='JOCKEY'), 'USR0027', 'Đinh Bá Lộc',    'jockey6@horserace.local', '0900000027', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-06.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '5 hours'),
('11111111-0000-4000-8000-000000000027', (SELECT role_id FROM role WHERE role_code='JOCKEY'), 'USR0028', 'Lâm Chí Dũng',   'jockey7@horserace.local', '0900000028', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-07.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '3 days'),
('11111111-0000-4000-8000-000000000028', (SELECT role_id FROM role WHERE role_code='JOCKEY'), 'USR0029', 'Tô Hoàng Sơn',   'jockey8@horserace.local', '0900000029', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-08.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '6 days'),
-- RACE_REFEREE  (BẮT BUỘC verified: StaffRefereeAssignmentServiceImpl + RefereeSubmissionCodeServiceImpl chặn referee chưa verify)
('11111111-0000-4000-8000-000000000031', (SELECT role_id FROM role WHERE role_code='RACE_REFEREE'), 'USR0017', 'Phan Công Lý',   'hantnse180242@fpt.edu.vn', '0900000017', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-06.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '3 hours'),
('11111111-0000-4000-8000-000000000032', (SELECT role_id FROM role WHERE role_code='RACE_REFEREE'), 'USR0018', 'Tạ Nghiêm Minh', 'referee2@horserace.local', '0900000018', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-07.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '2 days'),
('11111111-0000-4000-8000-000000000033', (SELECT role_id FROM role WHERE role_code='RACE_REFEREE'), 'USR0019', 'Chu Bình An',    'referee3@horserace.local', '0900000019', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-08.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '5 days'),
('11111111-0000-4000-8000-000000000034', (SELECT role_id FROM role WHERE role_code='RACE_REFEREE'), 'USR0020', 'Mai Chính Trực', 'referee4@horserace.local', '0900000020', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-09.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '11 days'),
('11111111-0000-4000-8000-000000000035', (SELECT role_id FROM role WHERE role_code='RACE_REFEREE'), 'USR0021', 'Đinh Trọng Tín', 'referee5@horserace.local', '0900000021', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-10.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '15 days'),
-- SPECTATOR
('11111111-0000-4000-8000-000000000041', (SELECT role_id FROM role WHERE role_code='SPECTATOR'), 'USR0022', 'Nguyễn Khán Giả', 'spectator1@horserace.local', '0900000022', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-01.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '30 minutes'),
('11111111-0000-4000-8000-000000000042', (SELECT role_id FROM role WHERE role_code='SPECTATOR'), 'USR0023', 'Trần Cổ Vũ',      'spectator2@horserace.local', '0900000023', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-02.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '6 hours'),
('11111111-0000-4000-8000-000000000043', (SELECT role_id FROM role WHERE role_code='SPECTATOR'), 'USR0024', 'Lê Hâm Mộ',       'spectator3@horserace.local', '0900000024', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-03.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '2 days'),
('11111111-0000-4000-8000-000000000044', (SELECT role_id FROM role WHERE role_code='SPECTATOR'), 'USR0025', 'Phạm Đam Mê',     'spectator4@horserace.local', '0900000025', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-04.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '4 days'),
('11111111-0000-4000-8000-000000000045', (SELECT role_id FROM role WHERE role_code='SPECTATOR'), 'USR0026', 'Võ Nhiệt Huyết',  'spectator5@horserace.local', '0900000026', '{noop}Test@1234', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/avatars/seed-avatar-05.jpg', 'ACTIVE', 'VERIFIED', TRUE, NOW() - INTERVAL '20 days');

-- =========================================================
-- OWNER_PROFILE — 5 (PK = owner_user_id nên tối đa = số HORSE_OWNER)
-- =========================================================
INSERT INTO owner_profile (owner_user_id, stable_name, primary_region, bio) VALUES
('11111111-0000-4000-8000-000000000011', 'Trại ngựa Minh Long',   'Hà Nội',        'Trại ngựa thuần chủng, thành lập 2012, chuyên ngựa đua cự ly trung bình.'),
('11111111-0000-4000-8000-000000000012', 'Thanh Trại Stable',     'TP. Hồ Chí Minh','Nhập giống từ Úc, tập trung cự ly ngắn tốc độ cao.'),
('11111111-0000-4000-8000-000000000013', 'Đại Phát Racing',       'Đà Nẵng',       'Đội đua nghiệp dư lên chuyên nghiệp từ 2020.'),
('11111111-0000-4000-8000-000000000014', 'Kim Long Equestrian',   'Lâm Đồng',      'Trại vùng cao nguyên, thế mạnh ngựa bền cự ly dài.'),
('11111111-0000-4000-8000-000000000015', 'Bảo Ngọc Bloodstock',   'Hải Phòng',     'Chuyên phối giống và bán ngựa đua trẻ.');

-- =========================================================
-- JOCKEY_PROFILE — 5 (PK = jockey_user_id)
-- =========================================================
INSERT INTO jockey_profile (jockey_user_id, license_no, body_weight, height_cm, experience_yrs, win_count,
                            bio, rating, riding_style, win_rate, recent_form, base_fee, prize_percent,
                            last_trophy, age, nationality, application_riding_style,
                            jockey_license_url, fitness_certificate_url) VALUES
('11111111-0000-4000-8000-000000000021', 'JK-2021-0001', 52.5, 163.0, 8, 46, 'Nài kỳ cựu, mạnh ở đoạn nước rút cuối.', 4.6, 'FRONT_RUNNER', 32.40, 'W-2-1-W-3',  5000000, 10.00, 'Cúp Mùa Xuân 2025', 29, 'Việt Nam', 'FRONT_RUNNER', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/licenses/seed-license-1.jpg', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/certificates/seed-certificate-1.jpg'),
('11111111-0000-4000-8000-000000000022', 'JK-2022-0007', 50.0, 160.5, 5, 23, 'Chuyên bám nhóm rồi bứt tốc ở khúc cua cuối.', 4.1, 'STALKER',      24.70, '3-W-4-2-W',  3800000,  8.50, 'Giải Cúp Sông Hồng 2024', 26, 'Việt Nam', 'STALKER',      'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/licenses/seed-license-2.jpg', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/certificates/seed-certificate-2.jpg'),
('11111111-0000-4000-8000-000000000023', 'JK-2023-0015', 54.0, 166.0, 3, 9,  'Nài trẻ, thể lực tốt, đang lên phong độ.',   3.6, 'CLOSER',       15.20, '5-3-W-6-4',  2500000,  7.00, NULL, 23, 'Việt Nam', 'CLOSER',       'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/licenses/seed-license-3.jpg', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/certificates/seed-certificate-3.jpg'),
('11111111-0000-4000-8000-000000000024', 'JK-2020-0033', 51.5, 162.0, 10, 61, 'Nhiều kinh nghiệm giải lớn, ổn định.',       4.8, 'PACE_SETTER',  38.90, 'W-W-2-W-1',  6500000, 12.00, 'Đại hội Đua ngựa QG 2025', 32, 'Việt Nam', 'PACE_SETTER', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/licenses/seed-license-4.jpg', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/certificates/seed-certificate-4.jpg'),
('11111111-0000-4000-8000-000000000025', 'JK-2024-0002', 49.5, 158.0, 2, 4,  'Tân binh, vừa hoàn tất khoá đào tạo nài.',   3.1, 'STALKER',       9.80, '7-5-4-6-3',  1800000,  6.00, NULL, 21, 'Việt Nam', 'STALKER',      'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/licenses/seed-license-5.jpg', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/certificates/seed-certificate-5.jpg'),
('11111111-0000-4000-8000-000000000026', 'JK-2022-0019', 53.0, 164.5, 6, 31, 'Đọc thế trận tốt, mạnh ở cự ly 1600m.',      4.3, 'STALKER',      27.10, 'W-3-2-W-4',  4200000,  9.00, 'Cúp Duyên Hải 2025', 27, 'Việt Nam', 'STALKER',     'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/licenses/seed-license-1.jpg', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/certificates/seed-certificate-1.jpg'),
('11111111-0000-4000-8000-000000000027', 'JK-2023-0028', 51.0, 161.0, 4, 17, 'Xuất phát nhanh, giữ nhịp ổn định.',         3.9, 'FRONT_RUNNER', 21.60, '2-W-5-3-W',  3300000,  8.00, NULL, 25, 'Việt Nam', 'FRONT_RUNNER', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/licenses/seed-license-2.jpg', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/certificates/seed-certificate-2.jpg'),
('11111111-0000-4000-8000-000000000028', 'JK-2021-0044', 52.0, 163.5, 7, 38, 'Bền bỉ, chuyên cự ly dài trên 2000m.',       4.4, 'CLOSER',       29.80, 'W-W-3-2-W',  4800000,  9.50, 'Giải Cao Nguyên 2025', 30, 'Việt Nam', 'CLOSER',    'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/licenses/seed-license-3.jpg', 'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/certificates/seed-certificate-3.jpg');

-- =========================================================
-- WALLET — 1 ví / user (UNIQUE user_id). 25 ví cho 25 user demo.
-- =========================================================
INSERT INTO wallet (wallet_id, user_id, balance, locked_balance, currency_code, status) VALUES
('aaaaaaaa-0000-4000-8000-000000000001', '11111111-0000-4000-8000-000000000001',        0,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000002', '11111111-0000-4000-8000-000000000002',        0,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000003', '11111111-0000-4000-8000-000000000003',        0,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000004', '11111111-0000-4000-8000-000000000004',        0,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000005', '11111111-0000-4000-8000-000000000005',        0,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000011', '11111111-0000-4000-8000-000000000011', 45000000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000012', '11111111-0000-4000-8000-000000000012', 32500000, 500000, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000013', '11111111-0000-4000-8000-000000000013', 18000000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000014', '11111111-0000-4000-8000-000000000014', 27400000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000015', '11111111-0000-4000-8000-000000000015',  9600000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000021', '11111111-0000-4000-8000-000000000021', 12800000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000022', '11111111-0000-4000-8000-000000000022',  7300000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000023', '11111111-0000-4000-8000-000000000023',  3100000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000024', '11111111-0000-4000-8000-000000000024', 21500000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000025', '11111111-0000-4000-8000-000000000025',  1200000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000031', '11111111-0000-4000-8000-000000000031',  5000000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000032', '11111111-0000-4000-8000-000000000032',  4200000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000033', '11111111-0000-4000-8000-000000000033',  3800000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000034', '11111111-0000-4000-8000-000000000034',  6100000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000035', '11111111-0000-4000-8000-000000000035',  2900000,      0, 'VND', 'FROZEN'),
('aaaaaaaa-0000-4000-8000-000000000041', '11111111-0000-4000-8000-000000000041',  2500000, 200000, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000042', '11111111-0000-4000-8000-000000000042',  1750000, 100000, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000043', '11111111-0000-4000-8000-000000000043',   980000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000044', '11111111-0000-4000-8000-000000000044',  4300000, 150000, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000045', '11111111-0000-4000-8000-000000000045',      500,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000026', '11111111-0000-4000-8000-000000000026',  8400000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000027', '11111111-0000-4000-8000-000000000027',  5600000,      0, 'VND', 'ACTIVE'),
('aaaaaaaa-0000-4000-8000-000000000028', '11111111-0000-4000-8000-000000000028',  9900000,      0, 'VND', 'ACTIVE');

-- =========================================================
-- HORSE — 10 con, chia cho 5 chủ (2 con/chủ)
-- =========================================================
INSERT INTO horse (horse_id, owner_user_id, horse_code, name, microchip_no, gender, breed, color,
                   date_of_birth, weight, origin_country, health_status, registration_status, status,
                   image_url, last_health_check_at, medical_note, grade, lifetime_earnings,
                   sire_name, sire_wins, sire_earnings, dam_name, dam_wins, dam_note,
                   trainer_name, trainer_license_no, vaccinations_up_to_date, recovery_percent,
                   fitness_certified, fitness_cert_expires_at, passport_scan_status, coggins_test_date) VALUES
('22222222-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000011','HRS0001','Bạch Long Mã','985141000101001','MALE','Thoroughbred','Trắng','2019-03-14',480.5,'Việt Nam','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-01.jpg', NOW() - INTERVAL '10 days','Thể trạng tốt, không có tiền sử chấn thương.','A', 320000000,'Northern Dancer II',18, 900000000,'Bạch Vân',6,'Dòng mẹ bền bỉ','Trần Huấn Luyện','TRN-0001',TRUE,100,TRUE, NOW() + INTERVAL '180 days','VALID','2026-05-02'),
('22222222-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000011','HRS0002','Hắc Phong','985141000101002','GELDING','Thoroughbred','Đen','2020-06-22',465.0,'Úc','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-02.jpg', NOW() - INTERVAL '18 days','Đã tiêm phòng đầy đủ.','B', 145000000,'Storm Cat Line',12, 610000000,'Hắc Nguyệt',3,NULL,'Trần Huấn Luyện','TRN-0001',TRUE,100,TRUE, NOW() + INTERVAL '120 days','VALID','2026-04-18'),
('22222222-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000012','HRS0003','Xích Thố','985141000101003','MALE','Arabian','Hồng đào','2018-11-05',492.0,'UAE','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-03.jpg', NOW() - INTERVAL '5 days','Sức bền vượt trội ở cự ly 2000m+.','A', 510000000,'Desert Wind',24,1400000000,'Hồng Nhan',9,'Vô địch 3 mùa','Nguyễn Thể Lực','TRN-0002',TRUE,100,TRUE, NOW() + INTERVAL '200 days','VALID','2026-06-01'),
('22222222-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000012','HRS0004','Thanh Vân','985141000101004','FEMALE','Thoroughbred','Xám','2021-02-17',438.5,'Việt Nam','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-04.jpg', NOW() - INTERVAL '3 days','Đã hồi phục hoàn toàn sau chấn thương căng cơ đầu mùa.','C', 38000000,'Blue Sky',7, 210000000,'Vân Anh',2,NULL,'Nguyễn Thể Lực','TRN-0002',TRUE,100,TRUE, NOW() + INTERVAL '150 days','VALID','2026-03-27'),
('22222222-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000013','HRS0005','Truy Phong','985141000101005','MALE','Thoroughbred','Nâu','2019-09-30',475.0,'Nhật Bản','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-05.jpg', NOW() - INTERVAL '12 days','Ổn định.','B', 198000000,'Sakura Bakushin',15, 780000000,'Phong Linh',5,NULL,'Lê Chăm Sóc','TRN-0003',TRUE,100,TRUE, NOW() + INTERVAL '90 days','VALID','2026-05-15'),
('22222222-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000013','HRS0006','Ngân Tuyết','985141000101006','FEMALE','Akhal-Teke','Bạc','2020-12-08',452.0,'Turkmenistan','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-06.jpg', NOW() - INTERVAL '20 days','Bộ lông ánh kim đặc trưng giống Akhal-Teke.','B', 87000000,'Golden Steppe',10, 350000000,'Tuyết Mai',4,NULL,'Lê Chăm Sóc','TRN-0003',TRUE,100,TRUE, NOW() + INTERVAL '150 days','VALID','2026-04-30'),
('22222222-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000014','HRS0007','Lôi Đình','985141000101007','MALE','Thoroughbred','Hạt dẻ','2018-04-19',488.0,'Việt Nam','QUARANTINE','PENDING','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-07.jpg', NOW() - INTERVAL '1 day','Cách ly theo dõi sau khi vận chuyển liên tỉnh.','B', 156000000,'Thunder Road',13, 520000000,'Đình Lan',3,NULL,'Phạm Y Tế','TRN-0004',FALSE, 90,FALSE, NULL,'MISSING','2026-01-11'),
('22222222-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000014','HRS0008','Phi Vân','985141000101008','GELDING','Standardbred','Nâu đỏ','2021-07-25',441.0,'Mông Cổ','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-08.jpg', NOW() - INTERVAL '25 days','Ngựa trẻ tiềm năng.','C', 22000000,'Steppe Runner',6, 140000000,'Vân Du',1,NULL,'Phạm Y Tế','TRN-0004',TRUE,100,TRUE, NOW() + INTERVAL '60 days','VALID','2026-06-10'),
('22222222-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000015','HRS0009','Kim Ô','985141000101009','MALE','Thoroughbred','Vàng kim','2017-08-02',497.5,'Ireland','UNFIT','APPROVED','RETIRED','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-09.jpg', NOW() - INTERVAL '60 days','Đã giải nghệ, chuyển sang phối giống.','A', 780000000,'Galileo Line',31,2900000000,'Kim Chi',11,'Dòng mẹ vô địch','Hoàng Lão Luyện','TRN-0005',TRUE,100,FALSE, NULL,'VALID','2025-12-20'),
('22222222-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000015','HRS0010','Tiểu Bạch','985141000101010','FEMALE','Pony','Trắng sữa','2022-05-11',320.0,'Việt Nam','HEALTHY','PENDING','INACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-10.jpg', NOW() - INTERVAL '40 days','Chưa đủ tuổi thi đấu giải chuyên nghiệp.','D', 0,'Mini Star',2,  15000000,'Bạch Tiểu Thư',0,NULL,'Hoàng Lão Luyện','TRN-0005',TRUE,100,FALSE, NULL,'MISSING','2026-02-14'),
-- HRS0011..HRS0026 — đàn ngựa thi đấu mở rộng.
-- Lý do: UNIQUE(tournament_id, horse_id) + registration trỏ đúng 1 race => một ngựa chỉ chạy được
-- 1 race trong 1 giải. TRN2026002 có 4 race (6+6+5+5) nên cần 22 ngựa RIÊNG chỉ cho giải đó.
-- Tất cả HEALTHY + APPROVED + fitness_certified, sinh 2018–2021 để vượt min_age_years=4 của GROUP_1.
('22222222-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000011','HRS0011','Thiết Kỵ','985141000101011','MALE','Thoroughbred','Nâu sẫm','2019-04-08',478.0,'Việt Nam','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-01.jpg', NOW() - INTERVAL '14 days','Thể trạng ổn định.','B', 132000000,'Iron Cavalry',11, 480000000,'Thiết Lan',4,NULL,'Trần Huấn Luyện','TRN-0001',TRUE,100,TRUE, NOW() + INTERVAL '160 days','VALID','2026-05-10'),
('22222222-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000011','HRS0012','Vân Tiêu','985141000101012','FEMALE','Thoroughbred','Xám bạc','2020-01-22',455.0,'Úc','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-02.jpg', NOW() - INTERVAL '9 days','Tiêm phòng đầy đủ.','B',  96000000,'Cloud Runner',9, 340000000,'Tiêu Dao',3,NULL,'Trần Huấn Luyện','TRN-0001',TRUE,100,TRUE, NOW() + INTERVAL '140 days','VALID','2026-05-18'),
('22222222-0000-4000-8000-000000000013','11111111-0000-4000-8000-000000000011','HRS0013','Hoả Vân','985141000101013','MALE','Arabian','Hồng đào','2018-09-15',486.0,'UAE','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-03.jpg', NOW() - INTERVAL '21 days','Sức bền tốt.','A', 265000000,'Fire Desert',17, 720000000,'Vân Hà',6,NULL,'Trần Huấn Luyện','TRN-0001',TRUE,100,TRUE, NOW() + INTERVAL '190 days','VALID','2026-04-26'),
('22222222-0000-4000-8000-000000000014','11111111-0000-4000-8000-000000000012','HRS0014','Lam Phong','985141000101014','GELDING','Thoroughbred','Xanh xám','2019-11-30',468.5,'Việt Nam','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-04.jpg', NOW() - INTERVAL '11 days','Ổn định.','B', 118000000,'Blue Gale',10, 395000000,'Phong Nhi',3,NULL,'Nguyễn Thể Lực','TRN-0002',TRUE,100,TRUE, NOW() + INTERVAL '130 days','VALID','2026-06-03'),
('22222222-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000012','HRS0015','Nhật Quang','985141000101015','MALE','Thoroughbred','Vàng nhạt','2020-03-05',472.0,'Nhật Bản','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-05.jpg', NOW() - INTERVAL '16 days','Phong độ đi lên.','B', 143000000,'Sun Blaze',13, 510000000,'Quang Minh',5,NULL,'Nguyễn Thể Lực','TRN-0002',TRUE,100,TRUE, NOW() + INTERVAL '175 days','VALID','2026-05-29'),
('22222222-0000-4000-8000-000000000016','11111111-0000-4000-8000-000000000012','HRS0016','Thanh Trúc','985141000101016','FEMALE','Akhal-Teke','Bạc','2021-01-18',449.0,'Turkmenistan','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-06.jpg', NOW() - INTERVAL '7 days','Bộ lông ánh kim.','C',  62000000,'Silver Steppe',8, 245000000,'Trúc Lâm',2,NULL,'Nguyễn Thể Lực','TRN-0002',TRUE,100,TRUE, NOW() + INTERVAL '120 days','VALID','2026-06-12'),
('22222222-0000-4000-8000-000000000017','11111111-0000-4000-8000-000000000013','HRS0017','Bắc Đẩu','985141000101017','MALE','Thoroughbred','Đen tuyền','2018-12-03',491.0,'Ireland','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-07.jpg', NOW() - INTERVAL '19 days','Kinh nghiệm giải lớn.','A', 388000000,'North Star',22, 980000000,'Đẩu Nương',8,'Dòng mẹ bền','Lê Chăm Sóc','TRN-0003',TRUE,100,TRUE, NOW() + INTERVAL '205 days','VALID','2026-04-22'),
('22222222-0000-4000-8000-000000000018','11111111-0000-4000-8000-000000000013','HRS0018','Tường Vân','985141000101018','FEMALE','Thoroughbred','Trắng ngà','2020-07-09',447.5,'Việt Nam','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-08.jpg', NOW() - INTERVAL '13 days','Thể trạng tốt.','C',  71000000,'Auspice',7, 260000000,'Vân Thường',3,NULL,'Lê Chăm Sóc','TRN-0003',TRUE,100,TRUE, NOW() + INTERVAL '145 days','VALID','2026-05-06'),
('22222222-0000-4000-8000-000000000019','11111111-0000-4000-8000-000000000013','HRS0019','Cuồng Phong','985141000101019','MALE','Standardbred','Nâu đỏ','2019-06-27',481.0,'Mông Cổ','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-09.jpg', NOW() - INTERVAL '23 days','Bứt tốc mạnh.','B', 156000000,'Wild Gust',14, 545000000,'Phong Vũ',5,NULL,'Lê Chăm Sóc','TRN-0003',TRUE,100,TRUE, NOW() + INTERVAL '165 days','VALID','2026-05-21'),
('22222222-0000-4000-8000-000000000020','11111111-0000-4000-8000-000000000014','HRS0020','Hạc Tuyết','985141000101020','FEMALE','Thoroughbred','Trắng','2020-10-14',452.5,'Úc','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-10.jpg', NOW() - INTERVAL '8 days','Ổn định.','B',  88000000,'Snow Crane',9, 305000000,'Tuyết Hạc',4,NULL,'Phạm Y Tế','TRN-0004',TRUE,100,TRUE, NOW() + INTERVAL '155 days','VALID','2026-06-07'),
('22222222-0000-4000-8000-000000000021','11111111-0000-4000-8000-000000000014','HRS0021','Long Vân','985141000101021','MALE','Thoroughbred','Nâu vàng','2018-05-21',495.0,'Việt Nam','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-01.jpg', NOW() - INTERVAL '17 days','Nhiều kinh nghiệm.','A', 342000000,'Dragon Cloud',20, 890000000,'Vân Long',7,NULL,'Phạm Y Tế','TRN-0004',TRUE,100,TRUE, NOW() + INTERVAL '185 days','VALID','2026-04-29'),
('22222222-0000-4000-8000-000000000022','11111111-0000-4000-8000-000000000014','HRS0022','Tuấn Mã','985141000101022','GELDING','Thoroughbred','Hạt dẻ','2019-08-16',474.0,'Việt Nam','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-02.jpg', NOW() - INTERVAL '12 days','Thể trạng tốt.','B', 127000000,'Swift Steed',12, 435000000,'Mã Nương',4,NULL,'Phạm Y Tế','TRN-0004',TRUE,100,TRUE, NOW() + INTERVAL '135 days','VALID','2026-05-24'),
('22222222-0000-4000-8000-000000000023','11111111-0000-4000-8000-000000000015','HRS0023','Ngọc Diện','985141000101023','FEMALE','Arabian','Kem','2020-04-02',444.0,'UAE','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-03.jpg', NOW() - INTERVAL '15 days','Nhập từ UAE, đã kiểm dịch.','B',  93000000,'Pearl Face',10, 355000000,'Diện Nhi',3,NULL,'Hoàng Lão Luyện','TRN-0005',TRUE,100,TRUE, NOW() + INTERVAL '170 days','VALID','2026-05-13'),
('22222222-0000-4000-8000-000000000024','11111111-0000-4000-8000-000000000015','HRS0024','Phi Tuyết','985141000101024','MALE','Thoroughbred','Xám trắng','2019-02-11',483.5,'Ireland','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-04.jpg', NOW() - INTERVAL '20 days','Sức bền cự ly dài.','A', 298000000,'Flying Snow',18, 760000000,'Tuyết Nhi',6,NULL,'Hoàng Lão Luyện','TRN-0005',TRUE,100,TRUE, NOW() + INTERVAL '195 days','VALID','2026-04-19'),
('22222222-0000-4000-8000-000000000025','11111111-0000-4000-8000-000000000015','HRS0025','Hồng Nhật','985141000101025','FEMALE','Thoroughbred','Hồng nhạt','2021-03-28',441.5,'Việt Nam','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-05.jpg', NOW() - INTERVAL '6 days','Ngựa trẻ tiềm năng.','C',  54000000,'Red Sun',6, 195000000,'Nhật Hà',2,NULL,'Hoàng Lão Luyện','TRN-0005',TRUE,100,TRUE, NOW() + INTERVAL '110 days','VALID','2026-06-15'),
('22222222-0000-4000-8000-000000000026','11111111-0000-4000-8000-000000000011','HRS0026','Thần Tốc','985141000101026','MALE','Thoroughbred','Nâu','2020-08-19',469.0,'Nhật Bản','HEALTHY','APPROVED','ACTIVE','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/horses/seed-horse-06.jpg', NOW() - INTERVAL '10 days','Ổn định, đang lên phong độ.','B', 109000000,'Godspeed',11, 410000000,'Tốc Nhi',4,NULL,'Trần Huấn Luyện','TRN-0001',TRUE,100,TRUE, NOW() + INTERVAL '150 days','VALID','2026-06-01');

-- =========================================================
-- HORSE_CHARACTERISTIC — @ElementCollection, PK (horse_id, tag)
-- =========================================================
INSERT INTO horse_characteristic (horse_id, tag) VALUES
('22222222-0000-4000-8000-000000000001','SPRINTER'),
('22222222-0000-4000-8000-000000000001','FRONT_RUNNER'),
('22222222-0000-4000-8000-000000000002','MUD_LOVER'),
('22222222-0000-4000-8000-000000000003','STAYER'),
('22222222-0000-4000-8000-000000000003','HIGH_STAMINA'),
('22222222-0000-4000-8000-000000000004','FAST_STARTER'),
('22222222-0000-4000-8000-000000000005','CLOSER'),
('22222222-0000-4000-8000-000000000006','TURF_SPECIALIST'),
('22222222-0000-4000-8000-000000000007','TEMPERAMENTAL'),
('22222222-0000-4000-8000-000000000008','YOUNG_PROSPECT'),
('22222222-0000-4000-8000-000000000009','VETERAN'),
('22222222-0000-4000-8000-000000000010','TRAINING_ONLY'),
('22222222-0000-4000-8000-000000000011','FRONT_RUNNER'),
('22222222-0000-4000-8000-000000000012','FAST_STARTER'),
('22222222-0000-4000-8000-000000000013','STAYER'),
('22222222-0000-4000-8000-000000000013','HIGH_STAMINA'),
('22222222-0000-4000-8000-000000000014','MUD_LOVER'),
('22222222-0000-4000-8000-000000000015','CLOSER'),
('22222222-0000-4000-8000-000000000016','TURF_SPECIALIST'),
('22222222-0000-4000-8000-000000000017','VETERAN'),
('22222222-0000-4000-8000-000000000017','HIGH_STAMINA'),
('22222222-0000-4000-8000-000000000018','FAST_STARTER'),
('22222222-0000-4000-8000-000000000019','SPRINTER'),
('22222222-0000-4000-8000-000000000020','TURF_SPECIALIST'),
('22222222-0000-4000-8000-000000000021','VETERAN'),
('22222222-0000-4000-8000-000000000022','MUD_LOVER'),
('22222222-0000-4000-8000-000000000023','SPRINTER'),
('22222222-0000-4000-8000-000000000024','STAYER'),
('22222222-0000-4000-8000-000000000025','YOUNG_PROSPECT'),
('22222222-0000-4000-8000-000000000026','CLOSER');

-- =========================================================
-- HORSE_MEDICAL_RECORD — 10
-- =========================================================
INSERT INTO horse_medical_record (record_id, horse_id, record_type, title, note, record_date, file_url, file_name) VALUES
('12222222-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000001','VACCINATION','Tiêm phòng cúm ngựa mũi 2','Vaccine EI, không phản ứng phụ.', '2026-05-02','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-01.jpg','vaccine-hrs0001.jpg'),
('12222222-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000001','CERTIFICATE','Giấy chứng nhận sức khoẻ 2026','Đủ điều kiện thi đấu đến hết 2026.','2026-05-02','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-02.jpg','health-cert-hrs0001.jpg'),
('12222222-0000-4000-8000-000000000003','22222222-0000-4000-8000-000000000002','VACCINATION','Tiêm phòng uốn ván','Định kỳ hằng năm.','2026-04-18','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-03.jpg','vaccine-hrs0002.jpg'),
('12222222-0000-4000-8000-000000000004','22222222-0000-4000-8000-000000000003','CERTIFICATE','Chứng nhận nhập khẩu & kiểm dịch','Nhập từ UAE, đã qua kiểm dịch 21 ngày.','2026-06-01','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-04.jpg','import-cert-hrs0003.jpg'),
('12222222-0000-4000-8000-000000000005','22222222-0000-4000-8000-000000000004','INJURY','Căng cơ chân sau trái','Nghỉ thi đấu 3 tuần, vật lý trị liệu 2 lần/tuần.','2026-07-16','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-05.jpg','injury-hrs0004.jpg'),
('12222222-0000-4000-8000-000000000006','22222222-0000-4000-8000-000000000005','NOTE','Theo dõi khẩu phần ăn','Tăng protein trước giải đấu tháng 8.','2026-07-07', NULL, NULL),
('12222222-0000-4000-8000-000000000007','22222222-0000-4000-8000-000000000006','VACCINATION','Tiêm phòng cúm ngựa mũi 1','Mũi 2 dự kiến sau 4 tuần.','2026-04-30','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-06.jpg','vaccine-hrs0006.jpg'),
('12222222-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000007','NOTE','Cách ly sau vận chuyển','Theo dõi thân nhiệt 2 lần/ngày trong 14 ngày.','2026-07-18','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-07.jpg','quarantine-hrs0007.jpg'),
('12222222-0000-4000-8000-000000000009','22222222-0000-4000-8000-000000000008','CERTIFICATE','Chứng nhận Coggins âm tính','Xét nghiệm EIA âm tính.','2026-06-10','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-08.jpg','coggins-hrs0008.jpg'),
('12222222-0000-4000-8000-000000000010','22222222-0000-4000-8000-000000000009','INJURY','Thoái hoá khớp gối','Không đủ điều kiện thi đấu, đề xuất giải nghệ.','2026-05-20','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/medical/seed-medical-09.jpg','retire-hrs0009.jpg');

-- =========================================================
-- VENUE — 10
-- =========================================================
INSERT INTO venue (venue_id, name, track_name, city, country, capacity, surface) VALUES
('33333333-0000-4000-8000-000000000001','Trường đua Phú Thọ',      'Đường đua chính A','TP. Hồ Chí Minh','Việt Nam',15000,'TURF'),
('33333333-0000-4000-8000-000000000002','Trường đua Đại Nam',      'Vòng ngoài B',     'Bình Dương',     'Việt Nam',12000,'DIRT'),
('33333333-0000-4000-8000-000000000003','Trường đua Sóc Sơn',      'Đường đua Bắc',    'Hà Nội',         'Việt Nam',18000,'TURF'),
('33333333-0000-4000-8000-000000000004','Trường đua Vân Đồn',      'Đường đua ven biển','Quảng Ninh',    'Việt Nam', 8000,'SYNTHETIC'),
('33333333-0000-4000-8000-000000000005','Trường đua Lâm Viên',     'Cao nguyên 1',     'Lâm Đồng',       'Việt Nam', 6500,'TURF'),
('33333333-0000-4000-8000-000000000006','Trường đua Bà Nà',        'Đường đua đồi',    'Đà Nẵng',        'Việt Nam', 9000,'DIRT'),
('33333333-0000-4000-8000-000000000007','Trường đua Cần Giờ',      'Đường đua duyên hải','TP. Hồ Chí Minh','Việt Nam',7000,'SYNTHETIC'),
('33333333-0000-4000-8000-000000000008','Trường đua Tam Đảo',      'Đường đua núi',    'Vĩnh Phúc',      'Việt Nam', 5500,'TURF'),
('33333333-0000-4000-8000-000000000009','Trường đua Cát Bà',       'Đường đua đảo',    'Hải Phòng',      'Việt Nam', 4200,'DIRT'),
('33333333-0000-4000-8000-000000000010','Trung tâm đua quốc gia',  'Sân vận động chính','Hà Nội',        'Việt Nam',25000,'TURF');

-- =========================================================
-- TOURNAMENT — 10, phủ đủ 7 trạng thái (DRAFT..CANCELLED)
-- =========================================================
INSERT INTO tournament (tournament_id, tournament_code, name, description, start_date, end_date,
                        registration_open_at, registration_close_at, location, circuit_tier,
                        total_purse, entry_cap, thoroughbreds_only, min_age_years,
                        requires_previous_group_win, status, image_url, created_by_user_id) VALUES
('44444444-0000-4000-8000-000000000001','TRN00002','Cúp Mùa Xuân 2026','Giải mở màn mùa giải 2026, cự ly trung bình.',            NOW() - INTERVAL '90 days', NOW() - INTERVAL '85 days', NOW() - INTERVAL '120 days', NOW() - INTERVAL '95 days','TP. Hồ Chí Minh','GROUP_2', 800000000, 24, TRUE,  3, FALSE,'COMPLETED',        'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-01.jpg','11111111-0000-4000-8000-000000000001'),
('44444444-0000-4000-8000-000000000002','TRN00003','Giải Vô Địch Quốc Gia 2026','Giải đấu lớn nhất năm, quy tụ ngựa hàng đầu.',   NOW() - INTERVAL '3 days',  NOW() + INTERVAL '4 days',  NOW() - INTERVAL '40 days',  NOW() - INTERVAL '10 days','Hà Nội','GROUP_1',        2500000000, 32, TRUE,  4, TRUE, 'ONGOING',          'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-02.jpg','11111111-0000-4000-8000-000000000001'),
('44444444-0000-4000-8000-000000000003','TRN00004','Cúp Duyên Hải 2026','Giải ven biển, mặt sân tổng hợp.',                        NOW() + INTERVAL '25 days', NOW() + INTERVAL '28 days', NOW() - INTERVAL '5 days',   NOW() + INTERVAL '15 days','Quảng Ninh','GROUP_3',    450000000, 18, FALSE, 3, FALSE,'REGISTRATION_OPEN','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-03.jpg','11111111-0000-4000-8000-000000000002'),
('44444444-0000-4000-8000-000000000004','TRN00005','Giải Cao Nguyên 2026','Thi đấu ở độ cao 1500m, thử thách sức bền.',            NOW() + INTERVAL '10 days', NOW() + INTERVAL '12 days', NOW() - INTERVAL '30 days',  NOW() - INTERVAL '2 days','Lâm Đồng','LISTED',         280000000, 16, FALSE, 3, FALSE,'REGISTRATION_CLOSED','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-04.jpg','11111111-0000-4000-8000-000000000002'),
('44444444-0000-4000-8000-000000000005','TRN00006','Cúp Sông Hồng 2026','Giải truyền thống miền Bắc.',                             NOW() + INTERVAL '55 days', NOW() + INTERVAL '58 days', NOW() + INTERVAL '20 days',  NOW() + INTERVAL '45 days','Hà Nội','GROUP_2',        620000000, 20, TRUE,  3, FALSE,'PUBLISHED',        'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-05.jpg','11111111-0000-4000-8000-000000000003'),
('44444444-0000-4000-8000-000000000006','TRN00007','Giải Giao Hữu Mở Rộng','Bản nháp, chưa công bố.',                               NOW() + INTERVAL '80 days', NOW() + INTERVAL '82 days', NOW() + INTERVAL '50 days',  NOW() + INTERVAL '75 days','Bình Dương','LISTED',     120000000, 12, FALSE, 2, FALSE,'DRAFT',            'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-06.jpg','11111111-0000-4000-8000-000000000003'),
('44444444-0000-4000-8000-000000000007','TRN00008','Cúp Tam Đảo 2026','Huỷ do điều kiện thời tiết cực đoan kéo dài.',               NOW() + INTERVAL '18 days', NOW() + INTERVAL '20 days', NOW() - INTERVAL '25 days',  NOW() + INTERVAL '5 days','Vĩnh Phúc','GROUP_3',      200000000, 14, FALSE, 3, FALSE,'CANCELLED',        'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-07.jpg','11111111-0000-4000-8000-000000000004'),
('44444444-0000-4000-8000-000000000008','TRN00001','Giải Tổng Kết 2025','Giải khép lại mùa 2025.',                                  NOW() - INTERVAL '200 days',NOW() - INTERVAL '196 days',NOW() - INTERVAL '240 days', NOW() - INTERVAL '210 days','Đà Nẵng','GROUP_2',      700000000, 22, TRUE,  3, FALSE,'COMPLETED',        'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-08.jpg','11111111-0000-4000-8000-000000000004'),
('44444444-0000-4000-8000-000000000009','TRN00009','Cúp Đảo Ngọc 2026','Giải trên đảo, quy mô nhỏ.',                                NOW() + INTERVAL '35 days', NOW() + INTERVAL '36 days', NOW() - INTERVAL '2 days',   NOW() + INTERVAL '25 days','Hải Phòng','LISTED',      150000000, 10, FALSE, 3, FALSE,'REGISTRATION_OPEN','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-09.jpg','11111111-0000-4000-8000-000000000005'),
('44444444-0000-4000-8000-000000000010','TRN00010','Siêu Cúp Quốc Tế 2026','Mời đội quốc tế, cự ly dài.',                            NOW() + INTERVAL '120 days',NOW() + INTERVAL '125 days',NOW() + INTERVAL '60 days',  NOW() + INTERVAL '110 days','Hà Nội','GROUP_1',      3000000000, 30, TRUE,  4, TRUE, 'PUBLISHED',        'https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/tournaments/seed-tournament-10.jpg','11111111-0000-4000-8000-000000000005');

-- =========================================================
-- TOURNAMENT_VENUE — 12 (UNIQUE tournament_id + venue_id)
-- =========================================================
INSERT INTO tournament_venue (tournament_venue_id, tournament_id, venue_id) VALUES
('43333333-0000-4000-8000-000000000001','44444444-0000-4000-8000-000000000001','33333333-0000-4000-8000-000000000001'),
('43333333-0000-4000-8000-000000000002','44444444-0000-4000-8000-000000000001','33333333-0000-4000-8000-000000000007'),
('43333333-0000-4000-8000-000000000003','44444444-0000-4000-8000-000000000002','33333333-0000-4000-8000-000000000010'),
('43333333-0000-4000-8000-000000000004','44444444-0000-4000-8000-000000000002','33333333-0000-4000-8000-000000000003'),
('43333333-0000-4000-8000-000000000005','44444444-0000-4000-8000-000000000003','33333333-0000-4000-8000-000000000004'),
('43333333-0000-4000-8000-000000000006','44444444-0000-4000-8000-000000000004','33333333-0000-4000-8000-000000000005'),
('43333333-0000-4000-8000-000000000007','44444444-0000-4000-8000-000000000005','33333333-0000-4000-8000-000000000003'),
('43333333-0000-4000-8000-000000000008','44444444-0000-4000-8000-000000000006','33333333-0000-4000-8000-000000000002'),
('43333333-0000-4000-8000-000000000009','44444444-0000-4000-8000-000000000007','33333333-0000-4000-8000-000000000008'),
('43333333-0000-4000-8000-000000000010','44444444-0000-4000-8000-000000000008','33333333-0000-4000-8000-000000000006'),
('43333333-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000009','33333333-0000-4000-8000-000000000009'),
('43333333-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000010','33333333-0000-4000-8000-000000000010');

-- =========================================================
-- RACE — 15, phủ đủ 7 trạng thái. R01/R02 (giải đã xong) và R03 (đang mở cược) là trục data chính.
-- =========================================================
INSERT INTO race (race_id, tournament_id, race_code, name, race_type, distance_meter, track_condition,
                  weather_condition, scheduled_start_at, actual_start_at, actual_end_at,
                  prediction_cutoff_at, max_participants, min_participants, venue, venue_id,
                  going_moisture_pct, total_purse, entry_fee, wind_speed_kph, wind_direction,
                  track_bias, photofinish_url, video_feed_url, certified_by_user_id, certified_at,
                  stewards_report, status) VALUES
('55555555-0000-4000-8000-000000000001','44444444-0000-4000-8000-000000000001','RACE00003','Chung kết Cúp Mùa Xuân','FLAT',1600,'FIRM','SUNNY', NOW() - INTERVAL '85 days', NOW() - INTERVAL '85 days', NOW() - INTERVAL '85 days' + INTERVAL '2 minutes', NOW() - INTERVAL '85 days' - INTERVAL '15 minutes', 12, 5,'Trường đua Phú Thọ','33333333-0000-4000-8000-000000000001', 22, 400000000, 2000000, 12.5,'NE','INSIDE_FAVOURED','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/photofinish/seed-photofinish-01.jpg',NULL, NULL, NULL, NULL,'FINISHED'),
('55555555-0000-4000-8000-000000000002','44444444-0000-4000-8000-000000000001','RACE00004','Vòng loại Cúp Mùa Xuân','FLAT',1200,'GOOD','CLOUDY', NOW() - INTERVAL '88 days', NOW() - INTERVAL '88 days', NOW() - INTERVAL '88 days' + INTERVAL '90 seconds', NOW() - INTERVAL '88 days' - INTERVAL '15 minutes', 10, 4,'Trường đua Cần Giờ','33333333-0000-4000-8000-000000000007', 35, 150000000, 1000000,  8.0,'E','NEUTRAL','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/photofinish/seed-photofinish-02.jpg', NULL, NULL, NULL, NULL,'FINISHED'),
('55555555-0000-4000-8000-000000000003','44444444-0000-4000-8000-000000000002','RACE00005','Bán kết 1 - Vô Địch QG','FLAT',2000,'GOOD','SUNNY', NOW() + INTERVAL '2 days', NULL, NULL, NOW() + INTERVAL '2 days' - INTERVAL '15 minutes', 14, 6,'Trung tâm đua quốc gia','33333333-0000-4000-8000-000000000010', 28, 600000000, 3000000, 10.0,'N','NEUTRAL', NULL,NULL, NULL, NULL, NULL,'OPEN'),
('55555555-0000-4000-8000-000000000004','44444444-0000-4000-8000-000000000002','RACE00006','Bán kết 2 - Vô Địch QG','FLAT',2000,'SOFT','RAINY', NOW() + INTERVAL '1 day', NULL, NULL, NOW() + INTERVAL '1 day' - INTERVAL '15 minutes', 14, 6,'Trung tâm đua quốc gia','33333333-0000-4000-8000-000000000010', 62, 600000000, 3000000, 22.0,'SW','OUTSIDE_FAVOURED', NULL, NULL, NULL, NULL, NULL,'CLOSED'),
('55555555-0000-4000-8000-000000000005','44444444-0000-4000-8000-000000000002','RACE00007','Vòng loại A - Vô Địch QG','FLAT',1400,'FIRM','SUNNY', NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '8 minutes', NULL, NOW() - INTERVAL '25 minutes', 12, 5,'Trường đua Sóc Sơn','33333333-0000-4000-8000-000000000003', 18, 300000000, 2500000,  6.5,'NE','INSIDE_FAVOURED', NULL,NULL, NULL, NULL, NULL,'RUNNING'),
('55555555-0000-4000-8000-000000000006','44444444-0000-4000-8000-000000000003','RACE00008','Cúp Duyên Hải - Lượt 1','FLAT',1000,'GOOD','WINDY', NOW() + INTERVAL '25 days', NULL, NULL, NOW() + INTERVAL '25 days' - INTERVAL '15 minutes', 10, 4,'Trường đua Vân Đồn','33333333-0000-4000-8000-000000000004', 40, 200000000, 1500000, 30.0,'SE','NEUTRAL', NULL, NULL, NULL, NULL, NULL,'OPEN'),
('55555555-0000-4000-8000-000000000007','44444444-0000-4000-8000-000000000003','RACE00009','Cúp Duyên Hải - Lượt 2','FLAT',1600,'GOOD','SUNNY', NOW() + INTERVAL '26 days', NULL, NULL, NOW() + INTERVAL '26 days' - INTERVAL '15 minutes', 10, 4,'Trường đua Vân Đồn','33333333-0000-4000-8000-000000000004', 33, 250000000, 1500000, 14.0,'S','NEUTRAL', NULL, NULL, NULL, NULL, NULL,'OPEN'),
('55555555-0000-4000-8000-000000000008','44444444-0000-4000-8000-000000000004','RACE00010','Giải Cao Nguyên - Chung kết','FLAT',2400,'FIRM','SUNNY', NOW() + INTERVAL '10 days', NULL, NULL, NOW() + INTERVAL '10 days' - INTERVAL '15 minutes', 16, 6,'Trường đua Lâm Viên','33333333-0000-4000-8000-000000000005', 20, 280000000, 1800000,  5.0,'W','NEUTRAL', NULL, NULL, NULL, NULL, NULL,'SCHEDULED'),
('55555555-0000-4000-8000-000000000009','44444444-0000-4000-8000-000000000005','RACE00011','Cúp Sông Hồng - Mở màn','FLAT',1800,'GOOD','CLOUDY', NOW() + INTERVAL '55 days', NULL, NULL, NOW() + INTERVAL '55 days' - INTERVAL '15 minutes', 12, 5,'Trường đua Sóc Sơn','33333333-0000-4000-8000-000000000003', 30, 300000000, 2000000,  9.0,'N','NEUTRAL', NULL, NULL, NULL, NULL, NULL,'SCHEDULED'),
('55555555-0000-4000-8000-000000000010','44444444-0000-4000-8000-000000000007','RACE00012','Cúp Tam Đảo - Lượt 1','FLAT',1400,'HEAVY','STORM', NOW() + INTERVAL '18 days', NULL, NULL, NOW() + INTERVAL '18 days' - INTERVAL '15 minutes', 10, 4,'Trường đua Tam Đảo','33333333-0000-4000-8000-000000000008', 88, 100000000, 1200000, 45.0,'NW','OUTSIDE_FAVOURED', NULL, NULL, NULL, NULL,'Huỷ do bão, hoàn phí toàn bộ.','CANCELLED'),
('55555555-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000008','RACE00001','Chung kết Tổng Kết 2025','FLAT',2000,'FIRM','SUNNY', NOW() - INTERVAL '196 days', NOW() - INTERVAL '196 days', NOW() - INTERVAL '196 days' + INTERVAL '150 seconds', NOW() - INTERVAL '196 days' - INTERVAL '15 minutes', 14, 6,'Trường đua Bà Nà','33333333-0000-4000-8000-000000000006', 25, 400000000, 2200000, 11.0,'E','NEUTRAL','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/photofinish/seed-photofinish-03.jpg', NULL, NULL, NULL, NULL,'FINISHED'),
('55555555-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000008','RACE00002','Vòng loại Tổng Kết 2025','FLAT',1200,'GOOD','CLOUDY', NOW() - INTERVAL '199 days', NOW() - INTERVAL '199 days', NOW() - INTERVAL '199 days' + INTERVAL '80 seconds', NOW() - INTERVAL '199 days' - INTERVAL '15 minutes', 12, 5,'Trường đua Bà Nà','33333333-0000-4000-8000-000000000006', 31, 120000000,  900000,  7.5,'NE','NEUTRAL','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/photofinish/seed-photofinish-04.jpg', NULL, NULL, NULL, NULL,'FINISHED'),
('55555555-0000-4000-8000-000000000013','44444444-0000-4000-8000-000000000009','RACE00013','Cúp Đảo Ngọc - Duy nhất','FLAT',1000,'GOOD','WINDY', NOW() + INTERVAL '35 days', NULL, NULL, NOW() + INTERVAL '35 days' - INTERVAL '15 minutes',  8, 4,'Trường đua Cát Bà','33333333-0000-4000-8000-000000000009', 38, 150000000, 1000000, 26.0,'SE','NEUTRAL', NULL, NULL, NULL, NULL, NULL,'OPEN'),
('55555555-0000-4000-8000-000000000014','44444444-0000-4000-8000-000000000010','RACE00014','Siêu Cúp - Vòng 1','FLAT',2400,'FIRM','SUNNY', NOW() + INTERVAL '120 days', NULL, NULL, NOW() + INTERVAL '120 days' - INTERVAL '15 minutes', 16, 8,'Trung tâm đua quốc gia','33333333-0000-4000-8000-000000000010', 24,1000000000, 5000000,  8.0,'N','NEUTRAL', NULL, NULL, NULL, NULL, NULL,'SCHEDULED'),
('55555555-0000-4000-8000-000000000015','44444444-0000-4000-8000-000000000002','RACE00015','Vòng loại B - Vô Địch QG','FLAT',1400,'GOOD','SUNNY', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '95 seconds', NOW() - INTERVAL '2 days' - INTERVAL '15 minutes', 12, 5,'Trường đua Sóc Sơn','33333333-0000-4000-8000-000000000003', 27, 300000000, 2500000,  9.5,'NE','NEUTRAL','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/photofinish/seed-photofinish-05.jpg', NULL, NULL, NULL,'Đang chờ trọng tài chính xác nhận.','FINISHED');

-- Extra disciplines so the race-form dropdowns (DISTINCT race_type/track/weather straight from this
-- table) offer more than FLAT: JUMP / HARNESS / ENDURANCE / HURDLE / STEEPLECHASE, plus new track
-- YIELDING and weather OVERCAST. SCHEDULED races under upcoming tournaments; descriptive only.
INSERT INTO race (race_id, tournament_id, race_code, name, race_type, distance_meter,
                  track_condition, weather_condition, scheduled_start_at, prediction_cutoff_at,
                  max_participants, min_participants, venue, venue_id, going_moisture_pct,
                  total_purse, status) VALUES
('55555555-0000-4000-8000-000000000101','44444444-0000-4000-8000-000000000010','RACE00101','Siêu Cúp - Vượt rào','JUMP',3200,'GOOD','OVERCAST', NOW() + INTERVAL '121 days', NOW() + INTERVAL '121 days' - INTERVAL '15 minutes', 14, 6,'Trung tâm đua quốc gia','33333333-0000-4000-8000-000000000010', 26, 500000000,'SCHEDULED'),
('55555555-0000-4000-8000-000000000102','44444444-0000-4000-8000-000000000010','RACE00102','Siêu Cúp - Đua xe kéo','HARNESS',2000,'YIELDING','CLOUDY', NOW() + INTERVAL '122 days', NOW() + INTERVAL '122 days' - INTERVAL '15 minutes', 12, 5,'Trường đua Đại Nam','33333333-0000-4000-8000-000000000002', 48, 300000000,'SCHEDULED'),
('55555555-0000-4000-8000-000000000103','44444444-0000-4000-8000-000000000005','RACE00103','Sông Hồng - Đường trường','ENDURANCE',6000,'FIRM','SUNNY', NOW() + INTERVAL '56 days', NOW() + INTERVAL '56 days' - INTERVAL '15 minutes', 16, 8,'Trường đua Lâm Viên','33333333-0000-4000-8000-000000000005', 20, 350000000,'SCHEDULED'),
('55555555-0000-4000-8000-000000000104','44444444-0000-4000-8000-000000000005','RACE00104','Sông Hồng - Vượt chướng ngại','HURDLE',2800,'SOFT','RAINY', NOW() + INTERVAL '57 days', NOW() + INTERVAL '57 days' - INTERVAL '15 minutes', 12, 5,'Trường đua Sóc Sơn','33333333-0000-4000-8000-000000000003', 65, 320000000,'SCHEDULED'),
('55555555-0000-4000-8000-000000000105','44444444-0000-4000-8000-000000000010','RACE00105','Siêu Cúp - Vượt rào lớn','STEEPLECHASE',3600,'HEAVY','WINDY', NOW() + INTERVAL '123 days', NOW() + INTERVAL '123 days' - INTERVAL '15 minutes', 14, 6,'Trường đua Bà Nà','33333333-0000-4000-8000-000000000006', 80, 420000000,'SCHEDULED');

-- =========================================================
-- RACE_PRIZE_DISTRIBUTION — @ElementCollection (race_id, place, amount). Bảng KHÔNG có PK -> tự giữ unique.
-- =========================================================
-- TOURNAMENT_REGISTRATION — 44 — 1 registration ↔ 1 entry ↔ 1 race
-- =========================================================
-- UNIQUE(tournament_id,horse_id) means a horse runs at most one race per tournament, so every
-- entry below owns its own registration. The old seed reused 6 registration_ids across two
-- races, producing entries the API could never have created.
INSERT INTO tournament_registration (registration_id, owner_user_id, tournament_id, horse_id, race_id,
                                     registration_code, status, submitted_at, reviewed_at,
                                     approved_by_user_id, rejection_reason, legal_basis_ref, category) VALUES
('66666666-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','REG00001','APPROVED', NOW() - INTERVAL '196 days' - INTERVAL '20 days', NOW() - INTERVAL '196 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000011','REG00002','APPROVED', NOW() - INTERVAL '196 days' - INTERVAL '20 days', NOW() - INTERVAL '196 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000011','REG00003','APPROVED', NOW() - INTERVAL '196 days' - INTERVAL '20 days', NOW() - INTERVAL '196 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000033', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000011','REG00004','APPROVED', NOW() - INTERVAL '196 days' - INTERVAL '20 days', NOW() - INTERVAL '196 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000034', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000011','REG00005','APPROVED', NOW() - INTERVAL '196 days' - INTERVAL '20 days', NOW() - INTERVAL '196 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000035', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000011','REG00006','APPROVED', NOW() - INTERVAL '196 days' - INTERVAL '20 days', NOW() - INTERVAL '196 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000014','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000012','REG00007','APPROVED', NOW() - INTERVAL '199 days' - INTERVAL '20 days', NOW() - INTERVAL '199 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000012','REG00008','APPROVED', NOW() - INTERVAL '199 days' - INTERVAL '20 days', NOW() - INTERVAL '199 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000033', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000012','REG00009','APPROVED', NOW() - INTERVAL '199 days' - INTERVAL '20 days', NOW() - INTERVAL '199 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000034', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000012','REG00010','APPROVED', NOW() - INTERVAL '199 days' - INTERVAL '20 days', NOW() - INTERVAL '199 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000035', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000008','22222222-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000012','REG00011','APPROVED', NOW() - INTERVAL '199 days' - INTERVAL '20 days', NOW() - INTERVAL '199 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/08-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000001','REG00012','APPROVED', NOW() - INTERVAL '85 days' - INTERVAL '20 days', NOW() - INTERVAL '85 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000013','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000001','REG00013','APPROVED', NOW() - INTERVAL '85 days' - INTERVAL '20 days', NOW() - INTERVAL '85 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000033', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000014','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000001','REG00014','APPROVED', NOW() - INTERVAL '85 days' - INTERVAL '20 days', NOW() - INTERVAL '85 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000034', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000001','REG00015','APPROVED', NOW() - INTERVAL '85 days' - INTERVAL '20 days', NOW() - INTERVAL '85 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000035', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000016','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000001','REG00016','APPROVED', NOW() - INTERVAL '85 days' - INTERVAL '20 days', NOW() - INTERVAL '85 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000017','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000002','REG00017','APPROVED', NOW() - INTERVAL '88 days' - INTERVAL '20 days', NOW() - INTERVAL '88 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000018','11111111-0000-4000-8000-000000000014','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000002','REG00018','APPROVED', NOW() - INTERVAL '88 days' - INTERVAL '20 days', NOW() - INTERVAL '88 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000033', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000019','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000002','REG00019','APPROVED', NOW() - INTERVAL '88 days' - INTERVAL '20 days', NOW() - INTERVAL '88 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000034', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000020','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000001','22222222-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000002','REG00020','APPROVED', NOW() - INTERVAL '88 days' - INTERVAL '20 days', NOW() - INTERVAL '88 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000035', NULL,'QĐ-2026/01-ĐUA','GROUP_2'),
('66666666-0000-4000-8000-000000000021','11111111-0000-4000-8000-000000000014','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000021','55555555-0000-4000-8000-000000000015','REG00021','APPROVED', NOW() - INTERVAL '2 days' - INTERVAL '20 days', NOW() - INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000022','11111111-0000-4000-8000-000000000014','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000022','55555555-0000-4000-8000-000000000015','REG00022','APPROVED', NOW() - INTERVAL '2 days' - INTERVAL '20 days', NOW() - INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000023','11111111-0000-4000-8000-000000000015','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000023','55555555-0000-4000-8000-000000000015','REG00023','APPROVED', NOW() - INTERVAL '2 days' - INTERVAL '20 days', NOW() - INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000033', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000024','11111111-0000-4000-8000-000000000015','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000024','55555555-0000-4000-8000-000000000015','REG00024','APPROVED', NOW() - INTERVAL '2 days' - INTERVAL '20 days', NOW() - INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000034', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000025','11111111-0000-4000-8000-000000000015','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000025','55555555-0000-4000-8000-000000000015','REG00025','APPROVED', NOW() - INTERVAL '2 days' - INTERVAL '20 days', NOW() - INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000035', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000026','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000016','55555555-0000-4000-8000-000000000005','REG00026','APPROVED', NOW() - INTERVAL '0.01 days' - INTERVAL '20 days', NOW() - INTERVAL '0.01 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000027','11111111-0000-4000-8000-000000000013','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000017','55555555-0000-4000-8000-000000000005','REG00027','APPROVED', NOW() - INTERVAL '0.01 days' - INTERVAL '20 days', NOW() - INTERVAL '0.01 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000028','11111111-0000-4000-8000-000000000013','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000018','55555555-0000-4000-8000-000000000005','REG00028','APPROVED', NOW() - INTERVAL '0.01 days' - INTERVAL '20 days', NOW() - INTERVAL '0.01 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000033', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000029','11111111-0000-4000-8000-000000000013','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000019','55555555-0000-4000-8000-000000000005','REG00029','APPROVED', NOW() - INTERVAL '0.01 days' - INTERVAL '20 days', NOW() - INTERVAL '0.01 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000034', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000030','11111111-0000-4000-8000-000000000014','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000020','55555555-0000-4000-8000-000000000005','REG00030','APPROVED', NOW() - INTERVAL '0.01 days' - INTERVAL '20 days', NOW() - INTERVAL '0.01 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000035', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000031','11111111-0000-4000-8000-000000000014','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000004','REG00031','APPROVED', NOW() + INTERVAL '1 days' - INTERVAL '20 days', NOW() + INTERVAL '1 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000032','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000004','REG00032','APPROVED', NOW() + INTERVAL '1 days' - INTERVAL '20 days', NOW() + INTERVAL '1 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000033','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000004','REG00033','APPROVED', NOW() + INTERVAL '1 days' - INTERVAL '20 days', NOW() + INTERVAL '1 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000033', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000034','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000004','REG00034','APPROVED', NOW() + INTERVAL '1 days' - INTERVAL '20 days', NOW() + INTERVAL '1 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000034', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000035','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000004','REG00035','APPROVED', NOW() + INTERVAL '1 days' - INTERVAL '20 days', NOW() + INTERVAL '1 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000035', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000036','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000015','55555555-0000-4000-8000-000000000004','REG00036','APPROVED', NOW() + INTERVAL '1 days' - INTERVAL '20 days', NOW() + INTERVAL '1 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000037','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000003','REG00037','APPROVED', NOW() + INTERVAL '2 days' - INTERVAL '20 days', NOW() + INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000038','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000003','REG00038','APPROVED', NOW() + INTERVAL '2 days' - INTERVAL '20 days', NOW() + INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000033', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000039','11111111-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000003','REG00039','APPROVED', NOW() + INTERVAL '2 days' - INTERVAL '20 days', NOW() + INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000034', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000040','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000003','REG00040','APPROVED', NOW() + INTERVAL '2 days' - INTERVAL '20 days', NOW() + INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000035', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000041','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000003','REG00041','APPROVED', NOW() + INTERVAL '2 days' - INTERVAL '20 days', NOW() + INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000031', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000042','11111111-0000-4000-8000-000000000012','44444444-0000-4000-8000-000000000002','22222222-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000003','REG00042','APPROVED', NOW() + INTERVAL '2 days' - INTERVAL '20 days', NOW() + INTERVAL '2 days' - INTERVAL '18 days','11111111-0000-4000-8000-000000000032', NULL,'QĐ-2026/02-ĐUA','GROUP_1'),
('66666666-0000-4000-8000-000000000043','11111111-0000-4000-8000-000000000014','44444444-0000-4000-8000-000000000003','22222222-0000-4000-8000-000000000007',NULL,'REG00043','UNDER_REVIEW', NOW() - INTERVAL '3 days', NULL,NULL, NULL,'QĐ-2026/03-ĐUA','GROUP_3'),
('66666666-0000-4000-8000-000000000044','11111111-0000-4000-8000-000000000015','44444444-0000-4000-8000-000000000003','22222222-0000-4000-8000-000000000010',NULL,'REG00044','REJECTED', NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day','11111111-0000-4000-8000-000000000034', 'Ngựa chưa đủ tuổi tối thiểu 3 năm theo điều lệ giải.','QĐ-2026/03-ĐUA','GROUP_3');

-- =========================================================
-- RACE_ENTRY — 42
-- =========================================================
INSERT INTO race_entry (entry_id, registration_id, race_id, entry_code, entry_no, lane_no,
                        weight_carried_lbs, recent_form, odds, status, checked_in_at, prize_earned) VALUES
('77777777-0000-4000-8000-000000000001','66666666-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','ENT00001',1,1,118,'W-2-1-3','5/2','FINISHED', NOW() - INTERVAL '196 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000002','66666666-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000011','ENT00002',2,2,120,'3-W-4-2','7/2','FINISHED', NOW() - INTERVAL '196 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000003','66666666-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000011','ENT00003',3,3,122,'5-3-W-6','2/1','FINISHED', NOW() - INTERVAL '196 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000004','66666666-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000011','ENT00004',4,4,124,'2-4-3-W','9/1','FINISHED', NOW() - INTERVAL '196 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000005','66666666-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000011','ENT00005',5,5,126,'4-5-2-3','6/1','FINISHED', NOW() - INTERVAL '196 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000006','66666666-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000011','ENT00006',6,6,128,'6-3-5-4','12/1','FINISHED', NOW() - INTERVAL '196 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000007','66666666-0000-4000-8000-000000000007','55555555-0000-4000-8000-000000000012','ENT00007',1,1,118,'W-2-1-3','5/2','FINISHED', NOW() - INTERVAL '199 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000008','66666666-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000012','ENT00008',2,2,120,'3-W-4-2','7/2','FINISHED', NOW() - INTERVAL '199 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000009','66666666-0000-4000-8000-000000000009','55555555-0000-4000-8000-000000000012','ENT00009',3,3,122,'5-3-W-6','2/1','FINISHED', NOW() - INTERVAL '199 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000010','66666666-0000-4000-8000-000000000010','55555555-0000-4000-8000-000000000012','ENT00010',4,4,124,'2-4-3-W','9/1','FINISHED', NOW() - INTERVAL '199 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000011','66666666-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000012','ENT00011',5,5,126,'4-5-2-3','6/1','FINISHED', NOW() - INTERVAL '199 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000012','66666666-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000001','ENT00012',1,1,118,'W-2-1-3','5/2','FINISHED', NOW() - INTERVAL '85 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000013','66666666-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000001','ENT00013',2,2,120,'3-W-4-2','7/2','FINISHED', NOW() - INTERVAL '85 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000014','66666666-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000001','ENT00014',3,3,122,'5-3-W-6','2/1','FINISHED', NOW() - INTERVAL '85 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000015','66666666-0000-4000-8000-000000000015','55555555-0000-4000-8000-000000000001','ENT00015',4,4,124,'2-4-3-W','9/1','FINISHED', NOW() - INTERVAL '85 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000016','66666666-0000-4000-8000-000000000016','55555555-0000-4000-8000-000000000001','ENT00016',5,5,126,'4-5-2-3','6/1','FINISHED', NOW() - INTERVAL '85 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000017','66666666-0000-4000-8000-000000000017','55555555-0000-4000-8000-000000000002','ENT00017',1,1,118,'W-2-1-3','5/2','FINISHED', NOW() - INTERVAL '88 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000018','66666666-0000-4000-8000-000000000018','55555555-0000-4000-8000-000000000002','ENT00018',2,2,120,'3-W-4-2','7/2','FINISHED', NOW() - INTERVAL '88 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000019','66666666-0000-4000-8000-000000000019','55555555-0000-4000-8000-000000000002','ENT00019',3,3,122,'5-3-W-6','2/1','FINISHED', NOW() - INTERVAL '88 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000020','66666666-0000-4000-8000-000000000020','55555555-0000-4000-8000-000000000002','ENT00020',4,4,124,'2-4-3-W','9/1','FINISHED', NOW() - INTERVAL '88 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000021','66666666-0000-4000-8000-000000000021','55555555-0000-4000-8000-000000000015','ENT00021',1,1,118,'W-2-1-3','5/2','FINISHED', NOW() - INTERVAL '2 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000022','66666666-0000-4000-8000-000000000022','55555555-0000-4000-8000-000000000015','ENT00022',2,2,120,'3-W-4-2','7/2','FINISHED', NOW() - INTERVAL '2 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000023','66666666-0000-4000-8000-000000000023','55555555-0000-4000-8000-000000000015','ENT00023',3,3,122,'5-3-W-6','2/1','FINISHED', NOW() - INTERVAL '2 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000024','66666666-0000-4000-8000-000000000024','55555555-0000-4000-8000-000000000015','ENT00024',4,4,124,'2-4-3-W','9/1','FINISHED', NOW() - INTERVAL '2 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000025','66666666-0000-4000-8000-000000000025','55555555-0000-4000-8000-000000000015','ENT00025',5,5,126,'4-5-2-3','6/1','FINISHED', NOW() - INTERVAL '2 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000026','66666666-0000-4000-8000-000000000026','55555555-0000-4000-8000-000000000005','ENT00026',1,1,118,'W-2-1-3','5/2','CHECKED_IN', NOW() - INTERVAL '0.01 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000027','66666666-0000-4000-8000-000000000027','55555555-0000-4000-8000-000000000005','ENT00027',2,2,120,'3-W-4-2','7/2','CHECKED_IN', NOW() - INTERVAL '0.01 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000028','66666666-0000-4000-8000-000000000028','55555555-0000-4000-8000-000000000005','ENT00028',3,3,122,'5-3-W-6','2/1','CHECKED_IN', NOW() - INTERVAL '0.01 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000029','66666666-0000-4000-8000-000000000029','55555555-0000-4000-8000-000000000005','ENT00029',4,4,124,'2-4-3-W','9/1','CHECKED_IN', NOW() - INTERVAL '0.01 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000030','66666666-0000-4000-8000-000000000030','55555555-0000-4000-8000-000000000005','ENT00030',5,5,126,'4-5-2-3','6/1','CHECKED_IN', NOW() - INTERVAL '0.01 days' - INTERVAL '1 hour', 0),
('77777777-0000-4000-8000-000000000031','66666666-0000-4000-8000-000000000031','55555555-0000-4000-8000-000000000004','ENT00031',1,1,118,'W-2-1-3','5/2','CHECKED_IN', NOW() - INTERVAL '2 hours', 0),
('77777777-0000-4000-8000-000000000032','66666666-0000-4000-8000-000000000032','55555555-0000-4000-8000-000000000004','ENT00032',2,2,120,'3-W-4-2','7/2','CHECKED_IN', NOW() - INTERVAL '2 hours', 0),
('77777777-0000-4000-8000-000000000033','66666666-0000-4000-8000-000000000033','55555555-0000-4000-8000-000000000004','ENT00033',3,3,122,'5-3-W-6','2/1','CHECKED_IN', NOW() - INTERVAL '2 hours', 0),
('77777777-0000-4000-8000-000000000034','66666666-0000-4000-8000-000000000034','55555555-0000-4000-8000-000000000004','ENT00034',4,4,124,'2-4-3-W','9/1','CHECKED_IN', NOW() - INTERVAL '2 hours', 0),
('77777777-0000-4000-8000-000000000035','66666666-0000-4000-8000-000000000035','55555555-0000-4000-8000-000000000004','ENT00035',5,5,126,'4-5-2-3','6/1','CHECKED_IN', NOW() - INTERVAL '2 hours', 0),
('77777777-0000-4000-8000-000000000036','66666666-0000-4000-8000-000000000036','55555555-0000-4000-8000-000000000004','ENT00036',6,6,128,'6-3-5-4','12/1','CHECKED_IN', NOW() - INTERVAL '2 hours', 0),
('77777777-0000-4000-8000-000000000037','66666666-0000-4000-8000-000000000037','55555555-0000-4000-8000-000000000003','ENT00037',1,1,118,'W-2-1-3','5/2','ENTERED', NULL, 0),
('77777777-0000-4000-8000-000000000038','66666666-0000-4000-8000-000000000038','55555555-0000-4000-8000-000000000003','ENT00038',2,2,120,'3-W-4-2','7/2','ENTERED', NULL, 0),
('77777777-0000-4000-8000-000000000039','66666666-0000-4000-8000-000000000039','55555555-0000-4000-8000-000000000003','ENT00039',3,3,122,'5-3-W-6','2/1','ENTERED', NULL, 0),
('77777777-0000-4000-8000-000000000040','66666666-0000-4000-8000-000000000040','55555555-0000-4000-8000-000000000003','ENT00040',4,4,124,'2-4-3-W','9/1','ENTERED', NULL, 0),
('77777777-0000-4000-8000-000000000041','66666666-0000-4000-8000-000000000041','55555555-0000-4000-8000-000000000003','ENT00041',5,5,126,'4-5-2-3','6/1','ENTERED', NULL, 0),
('77777777-0000-4000-8000-000000000042','66666666-0000-4000-8000-000000000042','55555555-0000-4000-8000-000000000003','ENT00042',6,6,128,'6-3-5-4','12/1','ENTERED', NULL, 0);

-- =========================================================
-- JOCKEY_ASSIGNMENT — 42 — 1 nài / 1 entry, không trùng nài trong cùng race
-- =========================================================
-- JockeyAssignmentServiceImpl rejects a second ACCEPTED ride for the same jockey in one race,
-- so each race draws its riders from a distinct slice of the 8-jockey pool.
INSERT INTO jockey_assignment (assignment_id, entry_id, jockey_user_id, status, invited_at, responded_at,
                               assigned_by_user_id) VALUES
('73333333-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000021','ACCEPTED', NOW() - INTERVAL '196 days' - INTERVAL '12 days', NOW() - INTERVAL '196 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000002','77777777-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000022','ACCEPTED', NOW() - INTERVAL '196 days' - INTERVAL '12 days', NOW() - INTERVAL '196 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000003','77777777-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000023','ACCEPTED', NOW() - INTERVAL '196 days' - INTERVAL '12 days', NOW() - INTERVAL '196 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000004','77777777-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000024','ACCEPTED', NOW() - INTERVAL '196 days' - INTERVAL '12 days', NOW() - INTERVAL '196 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000005','77777777-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000025','ACCEPTED', NOW() - INTERVAL '196 days' - INTERVAL '12 days', NOW() - INTERVAL '196 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000006','77777777-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000026','ACCEPTED', NOW() - INTERVAL '196 days' - INTERVAL '12 days', NOW() - INTERVAL '196 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000007','77777777-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000021','ACCEPTED', NOW() - INTERVAL '199 days' - INTERVAL '12 days', NOW() - INTERVAL '199 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000014'),
('73333333-0000-4000-8000-000000000008','77777777-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000022','ACCEPTED', NOW() - INTERVAL '199 days' - INTERVAL '12 days', NOW() - INTERVAL '199 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000009','77777777-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000023','ACCEPTED', NOW() - INTERVAL '199 days' - INTERVAL '12 days', NOW() - INTERVAL '199 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000010','77777777-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000024','ACCEPTED', NOW() - INTERVAL '199 days' - INTERVAL '12 days', NOW() - INTERVAL '199 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000025','ACCEPTED', NOW() - INTERVAL '199 days' - INTERVAL '12 days', NOW() - INTERVAL '199 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000021','ACCEPTED', NOW() - INTERVAL '85 days' - INTERVAL '12 days', NOW() - INTERVAL '85 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000013','77777777-0000-4000-8000-000000000013','11111111-0000-4000-8000-000000000022','ACCEPTED', NOW() - INTERVAL '85 days' - INTERVAL '12 days', NOW() - INTERVAL '85 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000014','77777777-0000-4000-8000-000000000014','11111111-0000-4000-8000-000000000023','ACCEPTED', NOW() - INTERVAL '85 days' - INTERVAL '12 days', NOW() - INTERVAL '85 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000015','77777777-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000024','ACCEPTED', NOW() - INTERVAL '85 days' - INTERVAL '12 days', NOW() - INTERVAL '85 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000016','77777777-0000-4000-8000-000000000016','11111111-0000-4000-8000-000000000025','ACCEPTED', NOW() - INTERVAL '85 days' - INTERVAL '12 days', NOW() - INTERVAL '85 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000017','77777777-0000-4000-8000-000000000017','11111111-0000-4000-8000-000000000021','ACCEPTED', NOW() - INTERVAL '88 days' - INTERVAL '12 days', NOW() - INTERVAL '88 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000018','77777777-0000-4000-8000-000000000018','11111111-0000-4000-8000-000000000022','ACCEPTED', NOW() - INTERVAL '88 days' - INTERVAL '12 days', NOW() - INTERVAL '88 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000014'),
('73333333-0000-4000-8000-000000000019','77777777-0000-4000-8000-000000000019','11111111-0000-4000-8000-000000000023','ACCEPTED', NOW() - INTERVAL '88 days' - INTERVAL '12 days', NOW() - INTERVAL '88 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000020','77777777-0000-4000-8000-000000000020','11111111-0000-4000-8000-000000000024','ACCEPTED', NOW() - INTERVAL '88 days' - INTERVAL '12 days', NOW() - INTERVAL '88 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000021','77777777-0000-4000-8000-000000000021','11111111-0000-4000-8000-000000000021','ACCEPTED', NOW() - INTERVAL '2 days' - INTERVAL '12 days', NOW() - INTERVAL '2 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000014'),
('73333333-0000-4000-8000-000000000022','77777777-0000-4000-8000-000000000022','11111111-0000-4000-8000-000000000022','ACCEPTED', NOW() - INTERVAL '2 days' - INTERVAL '12 days', NOW() - INTERVAL '2 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000014'),
('73333333-0000-4000-8000-000000000023','77777777-0000-4000-8000-000000000023','11111111-0000-4000-8000-000000000023','ACCEPTED', NOW() - INTERVAL '2 days' - INTERVAL '12 days', NOW() - INTERVAL '2 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000015'),
('73333333-0000-4000-8000-000000000024','77777777-0000-4000-8000-000000000024','11111111-0000-4000-8000-000000000024','ACCEPTED', NOW() - INTERVAL '2 days' - INTERVAL '12 days', NOW() - INTERVAL '2 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000015'),
('73333333-0000-4000-8000-000000000025','77777777-0000-4000-8000-000000000025','11111111-0000-4000-8000-000000000025','ACCEPTED', NOW() - INTERVAL '2 days' - INTERVAL '12 days', NOW() - INTERVAL '2 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000015'),
('73333333-0000-4000-8000-000000000026','77777777-0000-4000-8000-000000000026','11111111-0000-4000-8000-000000000021','ACCEPTED', NOW() - INTERVAL '0.01 days' - INTERVAL '12 days', NOW() - INTERVAL '0.01 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000027','77777777-0000-4000-8000-000000000027','11111111-0000-4000-8000-000000000022','ACCEPTED', NOW() - INTERVAL '0.01 days' - INTERVAL '12 days', NOW() - INTERVAL '0.01 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000013'),
('73333333-0000-4000-8000-000000000028','77777777-0000-4000-8000-000000000028','11111111-0000-4000-8000-000000000023','ACCEPTED', NOW() - INTERVAL '0.01 days' - INTERVAL '12 days', NOW() - INTERVAL '0.01 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000013'),
('73333333-0000-4000-8000-000000000029','77777777-0000-4000-8000-000000000029','11111111-0000-4000-8000-000000000024','ACCEPTED', NOW() - INTERVAL '0.01 days' - INTERVAL '12 days', NOW() - INTERVAL '0.01 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000013'),
('73333333-0000-4000-8000-000000000030','77777777-0000-4000-8000-000000000030','11111111-0000-4000-8000-000000000025','ACCEPTED', NOW() - INTERVAL '0.01 days' - INTERVAL '12 days', NOW() - INTERVAL '0.01 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000014'),
('73333333-0000-4000-8000-000000000031','77777777-0000-4000-8000-000000000031','11111111-0000-4000-8000-000000000021','ACCEPTED', NOW() + INTERVAL '1 days' - INTERVAL '12 days', NOW() + INTERVAL '1 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000014'),
('73333333-0000-4000-8000-000000000032','77777777-0000-4000-8000-000000000032','11111111-0000-4000-8000-000000000022','ACCEPTED', NOW() + INTERVAL '1 days' - INTERVAL '12 days', NOW() + INTERVAL '1 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000033','77777777-0000-4000-8000-000000000033','11111111-0000-4000-8000-000000000023','ACCEPTED', NOW() + INTERVAL '1 days' - INTERVAL '12 days', NOW() + INTERVAL '1 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000034','77777777-0000-4000-8000-000000000034','11111111-0000-4000-8000-000000000024','ACCEPTED', NOW() + INTERVAL '1 days' - INTERVAL '12 days', NOW() + INTERVAL '1 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000035','77777777-0000-4000-8000-000000000035','11111111-0000-4000-8000-000000000025','ACCEPTED', NOW() + INTERVAL '1 days' - INTERVAL '12 days', NOW() + INTERVAL '1 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000036','77777777-0000-4000-8000-000000000036','11111111-0000-4000-8000-000000000026','ACCEPTED', NOW() + INTERVAL '1 days' - INTERVAL '12 days', NOW() + INTERVAL '1 days' - INTERVAL '11 days','11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000037','77777777-0000-4000-8000-000000000037','11111111-0000-4000-8000-000000000021','INVITED', NOW() + INTERVAL '2 days' - INTERVAL '12 days', NULL,'11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000038','77777777-0000-4000-8000-000000000038','11111111-0000-4000-8000-000000000022','INVITED', NOW() + INTERVAL '2 days' - INTERVAL '12 days', NULL,'11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000039','77777777-0000-4000-8000-000000000039','11111111-0000-4000-8000-000000000023','INVITED', NOW() + INTERVAL '2 days' - INTERVAL '12 days', NULL,'11111111-0000-4000-8000-000000000011'),
('73333333-0000-4000-8000-000000000040','77777777-0000-4000-8000-000000000040','11111111-0000-4000-8000-000000000024','INVITED', NOW() + INTERVAL '2 days' - INTERVAL '12 days', NULL,'11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000041','11111111-0000-4000-8000-000000000025','INVITED', NOW() + INTERVAL '2 days' - INTERVAL '12 days', NULL,'11111111-0000-4000-8000-000000000012'),
('73333333-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000042','11111111-0000-4000-8000-000000000026','INVITED', NOW() + INTERVAL '2 days' - INTERVAL '12 days', NULL,'11111111-0000-4000-8000-000000000012');

-- =========================================================
-- RACE_ENTRY_INSPECTION — 36
-- =========================================================
INSERT INTO race_entry_inspection (inspection_id, entry_id, race_id, health_cert_passed, weight_verified,
                                   weight_carried_lbs, coggins_test_passed, pre_race_exam_passed,
                                   inspection_status, steward_note, inspected_by_user_id, inspected_at) VALUES
('71111111-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011',TRUE,TRUE,118,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '196 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000002','77777777-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000011',TRUE,TRUE,120,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '196 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000003','77777777-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000011',TRUE,TRUE,122,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '196 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000004','77777777-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000011',TRUE,TRUE,124,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '196 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000005','77777777-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000011',TRUE,TRUE,126,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '196 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000006','77777777-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000011',TRUE,TRUE,128,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '196 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000007','77777777-0000-4000-8000-000000000007','55555555-0000-4000-8000-000000000012',TRUE,TRUE,118,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '199 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000008','77777777-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000012',TRUE,TRUE,120,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '199 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000009','77777777-0000-4000-8000-000000000009','55555555-0000-4000-8000-000000000012',TRUE,TRUE,122,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '199 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000010','77777777-0000-4000-8000-000000000010','55555555-0000-4000-8000-000000000012',TRUE,TRUE,124,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '199 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000012',TRUE,TRUE,126,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '199 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000001',TRUE,TRUE,118,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '85 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000013','77777777-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000001',TRUE,TRUE,120,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '85 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000014','77777777-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000001',TRUE,TRUE,122,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '85 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000015','77777777-0000-4000-8000-000000000015','55555555-0000-4000-8000-000000000001',TRUE,TRUE,124,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '85 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000016','77777777-0000-4000-8000-000000000016','55555555-0000-4000-8000-000000000001',TRUE,TRUE,126,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '85 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000017','77777777-0000-4000-8000-000000000017','55555555-0000-4000-8000-000000000002',TRUE,TRUE,118,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '88 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000018','77777777-0000-4000-8000-000000000018','55555555-0000-4000-8000-000000000002',TRUE,TRUE,120,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '88 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000019','77777777-0000-4000-8000-000000000019','55555555-0000-4000-8000-000000000002',TRUE,TRUE,122,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '88 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000020','77777777-0000-4000-8000-000000000020','55555555-0000-4000-8000-000000000002',TRUE,TRUE,124,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '88 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000021','77777777-0000-4000-8000-000000000021','55555555-0000-4000-8000-000000000015',TRUE,TRUE,118,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '2 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000022','77777777-0000-4000-8000-000000000022','55555555-0000-4000-8000-000000000015',TRUE,TRUE,120,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '2 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000023','77777777-0000-4000-8000-000000000023','55555555-0000-4000-8000-000000000015',TRUE,TRUE,122,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '2 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000024','77777777-0000-4000-8000-000000000024','55555555-0000-4000-8000-000000000015',TRUE,TRUE,124,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '2 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000025','77777777-0000-4000-8000-000000000025','55555555-0000-4000-8000-000000000015',TRUE,TRUE,126,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '2 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000026','77777777-0000-4000-8000-000000000026','55555555-0000-4000-8000-000000000005',TRUE,TRUE,118,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '0.01 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000027','77777777-0000-4000-8000-000000000027','55555555-0000-4000-8000-000000000005',TRUE,TRUE,120,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '0.01 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000028','77777777-0000-4000-8000-000000000028','55555555-0000-4000-8000-000000000005',TRUE,TRUE,122,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '0.01 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000029','77777777-0000-4000-8000-000000000029','55555555-0000-4000-8000-000000000005',TRUE,TRUE,124,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '0.01 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000030','77777777-0000-4000-8000-000000000030','55555555-0000-4000-8000-000000000005',TRUE,TRUE,126,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '0.01 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000031','77777777-0000-4000-8000-000000000031','55555555-0000-4000-8000-000000000004',TRUE,TRUE,118,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000032', NOW() + INTERVAL '1 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000032','77777777-0000-4000-8000-000000000032','55555555-0000-4000-8000-000000000004',TRUE,TRUE,120,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000033', NOW() + INTERVAL '1 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000033','77777777-0000-4000-8000-000000000033','55555555-0000-4000-8000-000000000004',TRUE,TRUE,122,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000034', NOW() + INTERVAL '1 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000034','77777777-0000-4000-8000-000000000034','55555555-0000-4000-8000-000000000004',TRUE,TRUE,124,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000035', NOW() + INTERVAL '1 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000035','77777777-0000-4000-8000-000000000035','55555555-0000-4000-8000-000000000004',TRUE,TRUE,126,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000031', NOW() + INTERVAL '1 days' - INTERVAL '2 hours'),
('71111111-0000-4000-8000-000000000036','77777777-0000-4000-8000-000000000036','55555555-0000-4000-8000-000000000004',TRUE,TRUE,128,TRUE,TRUE,'CLEARED','Đủ điều kiện thi đấu.','11111111-0000-4000-8000-000000000032', NOW() + INTERVAL '1 days' - INTERVAL '2 hours');

-- =========================================================
-- ENTRY_DOCUMENT_REVIEW — 36
-- =========================================================
INSERT INTO entry_document_review (review_id, entry_id, race_id, document_status, review_reason,
                                   reviewed_by_user_id, reviewed_at) VALUES
('72222222-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '196 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000002','77777777-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000011','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '196 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000003','77777777-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000011','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '196 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000004','77777777-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000011','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '196 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000005','77777777-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000011','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '196 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000006','77777777-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000011','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '196 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000007','77777777-0000-4000-8000-000000000007','55555555-0000-4000-8000-000000000012','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '199 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000008','77777777-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000012','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '199 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000009','77777777-0000-4000-8000-000000000009','55555555-0000-4000-8000-000000000012','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '199 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000010','77777777-0000-4000-8000-000000000010','55555555-0000-4000-8000-000000000012','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '199 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000012','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '199 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000001','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '85 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000013','77777777-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000001','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '85 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000014','77777777-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000001','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '85 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000015','77777777-0000-4000-8000-000000000015','55555555-0000-4000-8000-000000000001','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '85 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000016','77777777-0000-4000-8000-000000000016','55555555-0000-4000-8000-000000000001','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '85 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000017','77777777-0000-4000-8000-000000000017','55555555-0000-4000-8000-000000000002','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '88 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000018','77777777-0000-4000-8000-000000000018','55555555-0000-4000-8000-000000000002','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '88 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000019','77777777-0000-4000-8000-000000000019','55555555-0000-4000-8000-000000000002','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '88 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000020','77777777-0000-4000-8000-000000000020','55555555-0000-4000-8000-000000000002','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '88 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000021','77777777-0000-4000-8000-000000000021','55555555-0000-4000-8000-000000000015','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '2 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000022','77777777-0000-4000-8000-000000000022','55555555-0000-4000-8000-000000000015','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '2 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000023','77777777-0000-4000-8000-000000000023','55555555-0000-4000-8000-000000000015','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '2 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000024','77777777-0000-4000-8000-000000000024','55555555-0000-4000-8000-000000000015','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '2 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000025','77777777-0000-4000-8000-000000000025','55555555-0000-4000-8000-000000000015','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '2 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000026','77777777-0000-4000-8000-000000000026','55555555-0000-4000-8000-000000000005','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '0.01 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000027','77777777-0000-4000-8000-000000000027','55555555-0000-4000-8000-000000000005','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000033', NOW() - INTERVAL '0.01 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000028','77777777-0000-4000-8000-000000000028','55555555-0000-4000-8000-000000000005','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '0.01 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000029','77777777-0000-4000-8000-000000000029','55555555-0000-4000-8000-000000000005','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000035', NOW() - INTERVAL '0.01 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000030','77777777-0000-4000-8000-000000000030','55555555-0000-4000-8000-000000000005','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '0.01 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000031','77777777-0000-4000-8000-000000000031','55555555-0000-4000-8000-000000000004','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000032', NOW() + INTERVAL '1 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000032','77777777-0000-4000-8000-000000000032','55555555-0000-4000-8000-000000000004','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000033', NOW() + INTERVAL '1 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000033','77777777-0000-4000-8000-000000000033','55555555-0000-4000-8000-000000000004','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000034', NOW() + INTERVAL '1 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000034','77777777-0000-4000-8000-000000000034','55555555-0000-4000-8000-000000000004','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000035', NOW() + INTERVAL '1 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000035','77777777-0000-4000-8000-000000000035','55555555-0000-4000-8000-000000000004','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000031', NOW() + INTERVAL '1 days' - INTERVAL '3 hours'),
('72222222-0000-4000-8000-000000000036','77777777-0000-4000-8000-000000000036','55555555-0000-4000-8000-000000000004','ACCEPTED', NULL,'11111111-0000-4000-8000-000000000032', NOW() + INTERVAL '1 days' - INTERVAL '3 hours');

-- =========================================================
-- RACE_RESULT — PROVISIONAL — certify() mới đẩy lên OFFICIAL và sinh prize
-- =========================================================
INSERT INTO race_result (result_id, race_id, entry_id, finish_position, finish_time_ms, lengths_behind,
                         score, current_version_no, officiality_status, approved_by_user_id,
                         published_at, referee_submitted_at) VALUES
('88888888-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000001',1,148000,0.00,100,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '196 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000002',2,148250,1.15,92,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '196 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000003',3,148500,2.30,84,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '196 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000004',4,148750,3.45,76,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '196 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000005',5,149000,4.60,68,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '196 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000006',6,149250,5.75,60,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '196 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000007','55555555-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000007',1,70900,0.00,100,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '199 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000008',2,71150,1.15,92,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '199 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000009','55555555-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000009',3,71400,2.30,84,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '199 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000010','55555555-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000010',4,71650,3.45,76,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '199 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000011',5,71900,4.60,68,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '199 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000012',1,96300,0.00,100,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '85 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000013',2,96550,1.15,92,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '85 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000014',3,96800,2.30,84,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '85 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000015','55555555-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000015',4,97050,3.45,76,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '85 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000016','55555555-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000016',5,97300,4.60,68,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '85 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000017','55555555-0000-4000-8000-000000000002','77777777-0000-4000-8000-000000000017',1,70900,0.00,100,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '88 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000018','55555555-0000-4000-8000-000000000002','77777777-0000-4000-8000-000000000018',2,71150,1.15,92,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '88 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000019','55555555-0000-4000-8000-000000000002','77777777-0000-4000-8000-000000000019',3,71400,2.30,84,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '88 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000020','55555555-0000-4000-8000-000000000002','77777777-0000-4000-8000-000000000020',4,71650,3.45,76,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '88 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000021','55555555-0000-4000-8000-000000000015','77777777-0000-4000-8000-000000000021',1,96300,0.00,100,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '2 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000022','55555555-0000-4000-8000-000000000015','77777777-0000-4000-8000-000000000022',2,96550,1.15,92,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '2 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000023','55555555-0000-4000-8000-000000000015','77777777-0000-4000-8000-000000000023',3,96800,2.30,84,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '2 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000024','55555555-0000-4000-8000-000000000015','77777777-0000-4000-8000-000000000024',4,97050,3.45,76,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '2 days' + INTERVAL '30 minutes'),
('88888888-0000-4000-8000-000000000025','55555555-0000-4000-8000-000000000015','77777777-0000-4000-8000-000000000025',5,97300,4.60,68,1,'PROVISIONAL', NULL, NULL, NOW() - INTERVAL '2 days' + INTERVAL '30 minutes');

-- =========================================================
-- RACE_RESULT_VERSION — 25
-- =========================================================
INSERT INTO race_result_version (result_version_id, result_id, version_no, finish_position, finish_time_ms,
                                 score, officiality_status, changed_by_user_id, change_reason) VALUES
('89999999-0000-4000-8000-000000000001','88888888-0000-4000-8000-000000000001',1,1,148000,100,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000002','88888888-0000-4000-8000-000000000002',1,2,148250,92,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000003','88888888-0000-4000-8000-000000000003',1,3,148500,84,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000004','88888888-0000-4000-8000-000000000004',1,4,148750,76,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000005','88888888-0000-4000-8000-000000000005',1,5,149000,68,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000006','88888888-0000-4000-8000-000000000006',1,6,149250,60,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000007','88888888-0000-4000-8000-000000000007',1,1,70900,100,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000008','88888888-0000-4000-8000-000000000008',1,2,71150,92,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000009','88888888-0000-4000-8000-000000000009',1,3,71400,84,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000010','88888888-0000-4000-8000-000000000010',1,4,71650,76,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000011','88888888-0000-4000-8000-000000000011',1,5,71900,68,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000012','88888888-0000-4000-8000-000000000012',1,1,96300,100,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000013','88888888-0000-4000-8000-000000000013',1,2,96550,92,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000014','88888888-0000-4000-8000-000000000014',1,3,96800,84,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000015','88888888-0000-4000-8000-000000000015',1,4,97050,76,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000016','88888888-0000-4000-8000-000000000016',1,5,97300,68,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000017','88888888-0000-4000-8000-000000000017',1,1,70900,100,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000018','88888888-0000-4000-8000-000000000018',1,2,71150,92,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000019','88888888-0000-4000-8000-000000000019',1,3,71400,84,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000020','88888888-0000-4000-8000-000000000020',1,4,71650,76,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000021','88888888-0000-4000-8000-000000000021',1,1,96300,100,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000022','88888888-0000-4000-8000-000000000022',1,2,96550,92,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000023','88888888-0000-4000-8000-000000000023',1,3,96800,84,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000024','88888888-0000-4000-8000-000000000024',1,4,97050,76,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.'),
('89999999-0000-4000-8000-000000000025','88888888-0000-4000-8000-000000000025',1,5,97300,68,'PROVISIONAL','11111111-0000-4000-8000-000000000031','Trọng tài nhập kết quả lần đầu.');

-- =========================================================
-- RACE_PRIZE_DISTRIBUTION — MỌI race có purse đều phải có phân bổ
-- =========================================================
-- Bất biến: SUM(amount) của một race = race.total_purse, tuyệt đối, không xấp xỉ.
-- Trước đây chỉ 5 race đã có kết quả mới được phân bổ, nên 10 race còn lại (kể cả
-- RACE00007 đang RUNNING với purse 300tr) sẽ chi ĐÚNG 0 ĐỒNG khi certify:
-- creditPrizes thoát sớm ở `dist.isEmpty()` mà không báo lỗi gì.
INSERT INTO race_prize_distribution (race_id, place, amount) VALUES
('55555555-0000-4000-8000-000000000011','1',200000000),
('55555555-0000-4000-8000-000000000011','2',120000000),
('55555555-0000-4000-8000-000000000011','3',80000000),
('55555555-0000-4000-8000-000000000012','1',60000000),
('55555555-0000-4000-8000-000000000012','2',36000000),
('55555555-0000-4000-8000-000000000012','3',24000000),
('55555555-0000-4000-8000-000000000001','1',200000000),
('55555555-0000-4000-8000-000000000001','2',120000000),
('55555555-0000-4000-8000-000000000001','3',80000000),
('55555555-0000-4000-8000-000000000002','1',75000000),
('55555555-0000-4000-8000-000000000002','2',45000000),
('55555555-0000-4000-8000-000000000002','3',30000000),
('55555555-0000-4000-8000-000000000015','1',150000000),
('55555555-0000-4000-8000-000000000015','2',90000000),
('55555555-0000-4000-8000-000000000015','3',60000000);

-- Các race còn lại: 50/30/20 theo đúng quy ước 5 dòng ở trên. Hạng 1 gánh phần dư
-- (purse − hạng2 − hạng3) nên tổng khớp purse kể cả khi purse không chia hết cho 10.
INSERT INTO race_prize_distribution (race_id, place, amount)
SELECT r.race_id, p.place,
       CASE p.place
           WHEN '1' THEN r.total_purse - trunc(r.total_purse * 0.30, 2) - trunc(r.total_purse * 0.20, 2)
           WHEN '2' THEN trunc(r.total_purse * 0.30, 2)
           ELSE            trunc(r.total_purse * 0.20, 2)
       END
  FROM race r
 CROSS JOIN (VALUES ('1'), ('2'), ('3')) AS p(place)
 WHERE r.total_purse > 0
   AND r.status <> 'CANCELLED'
   AND r.is_deleted = false
   AND NOT EXISTS (SELECT 1 FROM race_prize_distribution d WHERE d.race_id = r.race_id);

-- =========================================================
-- RACE_FRACTION — chỉ cho race đã chạy xong
-- =========================================================
INSERT INTO race_fraction (race_id, split_no, time_ms) VALUES
('55555555-0000-4000-8000-000000000011',1,37000),
('55555555-0000-4000-8000-000000000011',2,74000),
('55555555-0000-4000-8000-000000000011',3,111000),
('55555555-0000-4000-8000-000000000012',1,17725),
('55555555-0000-4000-8000-000000000012',2,35450),
('55555555-0000-4000-8000-000000000012',3,53175),
('55555555-0000-4000-8000-000000000001',1,24075),
('55555555-0000-4000-8000-000000000001',2,48150),
('55555555-0000-4000-8000-000000000001',3,72225),
('55555555-0000-4000-8000-000000000002',1,17725),
('55555555-0000-4000-8000-000000000002',2,35450),
('55555555-0000-4000-8000-000000000002',3,53175),
('55555555-0000-4000-8000-000000000015',1,24075),
('55555555-0000-4000-8000-000000000015',2,48150),
('55555555-0000-4000-8000-000000000015',3,72225);

-- =========================================================
-- REFEREE_ASSIGNMENT — CHIEF cho mọi race đã/đang chạy — certify và OTP đều đòi CONFIRMED
-- =========================================================
INSERT INTO referee_assignment (ref_assignment_id, race_id, referee_user_id, panel_role, ref_code,
                                status, assigned_at, responded_at, decline_reason, created_by_user_id) VALUES
('74444444-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000032','CHIEF','REF-A3F91C','CONFIRMED', NOW() - INTERVAL '196 days' - INTERVAL '15 days', NOW() - INTERVAL '196 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000001'),
('74444444-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000034','JUDGE','REF-B7D204','CONFIRMED', NOW() - INTERVAL '196 days' - INTERVAL '15 days', NOW() - INTERVAL '196 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000002'),
('74444444-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000034','CHIEF','REF-C1E8A5','CONFIRMED', NOW() - INTERVAL '199 days' - INTERVAL '15 days', NOW() - INTERVAL '199 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000001'),
('74444444-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000031','JUDGE','REF-D9F3B6','CONFIRMED', NOW() - INTERVAL '199 days' - INTERVAL '15 days', NOW() - INTERVAL '199 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000002'),
('74444444-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000031','CHIEF','REF-E4A7C8','CONFIRMED', NOW() - INTERVAL '85 days' - INTERVAL '15 days', NOW() - INTERVAL '85 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000001'),
('74444444-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000033','JUDGE','REF-F2B5D1','CONFIRMED', NOW() - INTERVAL '85 days' - INTERVAL '15 days', NOW() - INTERVAL '85 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000002'),
('74444444-0000-4000-8000-000000000007','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000033','CHIEF','REF-A8C3E9','CONFIRMED', NOW() - INTERVAL '88 days' - INTERVAL '15 days', NOW() - INTERVAL '88 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000001'),
('74444444-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000035','JUDGE','REF-B6D1F4','CONFIRMED', NOW() - INTERVAL '88 days' - INTERVAL '15 days', NOW() - INTERVAL '88 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000002'),
('74444444-0000-4000-8000-000000000009','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000035','CHIEF','REF-C5E2A7','CONFIRMED', NOW() - INTERVAL '2 days' - INTERVAL '15 days', NOW() - INTERVAL '2 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000001'),
('74444444-0000-4000-8000-000000000010','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000032','JUDGE','REF-D3F8B2','CONFIRMED', NOW() - INTERVAL '2 days' - INTERVAL '15 days', NOW() - INTERVAL '2 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000002'),
('74444444-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000032','CHIEF','REF-E7A4C6','CONFIRMED', NOW() - INTERVAL '0.01 days' - INTERVAL '15 days', NOW() - INTERVAL '0.01 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000001'),
('74444444-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000034','JUDGE','REF-F1B9D3','CONFIRMED', NOW() - INTERVAL '0.01 days' - INTERVAL '15 days', NOW() - INTERVAL '0.01 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000002'),
('74444444-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000034','CHIEF','REF-A2C7E5','CONFIRMED', NOW() + INTERVAL '1 days' - INTERVAL '15 days', NOW() + INTERVAL '1 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000001'),
('74444444-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000031','JUDGE','REF-B4D6F8','CONFIRMED', NOW() + INTERVAL '1 days' - INTERVAL '15 days', NOW() + INTERVAL '1 days' - INTERVAL '14 days', NULL,'11111111-0000-4000-8000-000000000002');

-- =========================================================
-- REFEREE_SUBMISSION_CODE — chỉ cấp cho referee có assignment CONFIRMED trên đúng race đó
-- =========================================================
-- RefereeSubmissionCodeServiceImpl requires status=CONFIRMED before issuing a code, so an OTP on a
-- race the referee is not assigned to (or has DECLINED) is unreachable through the API.
INSERT INTO referee_submission_code (code_id, race_id, referee_user_id, code_hash, expires_at, consumed_at, attempt_count) VALUES
('76666666-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000032',encode(sha256('100111'::bytea),'hex'), NOW() - INTERVAL '196 days' + INTERVAL '1 hour', NOW() - INTERVAL '196 days' + INTERVAL '35 minutes', 1),
('76666666-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000034',encode(sha256('100222'::bytea),'hex'), NOW() - INTERVAL '199 days' + INTERVAL '1 hour', NOW() - INTERVAL '199 days' + INTERVAL '35 minutes', 1),
('76666666-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000031',encode(sha256('100333'::bytea),'hex'), NOW() - INTERVAL '85 days' + INTERVAL '1 hour', NOW() - INTERVAL '85 days' + INTERVAL '35 minutes', 1),
('76666666-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000033',encode(sha256('100444'::bytea),'hex'), NOW() - INTERVAL '88 days' + INTERVAL '1 hour', NOW() - INTERVAL '88 days' + INTERVAL '35 minutes', 1),
('76666666-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000035',encode(sha256('100555'::bytea),'hex'), NOW() - INTERVAL '2 days' + INTERVAL '1 hour', NOW() - INTERVAL '2 days' + INTERVAL '35 minutes', 1);

-- =========================================================
-- REFEREE_REPORT — mỗi report do chính CHIEF của race đó viết
-- =========================================================
INSERT INTO referee_report (report_id, race_id, author_user_id, report_type, summary, decision,
                            severity_level, report_status, submitted_at) VALUES
('91111111-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000032','GENERAL','Cuộc đua diễn ra an toàn, không có sự cố nghiêm trọng.','Không có hình phạt bổ sung.','LOW','CLOSED', NOW() - INTERVAL '196 days' + INTERVAL '40 minutes'),
('91111111-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000034','GENERAL','Cuộc đua diễn ra an toàn, không có sự cố nghiêm trọng.','Không có hình phạt bổ sung.','LOW','CLOSED', NOW() - INTERVAL '199 days' + INTERVAL '40 minutes'),
('91111111-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000031','GENERAL','Cuộc đua diễn ra an toàn, không có sự cố nghiêm trọng.','Không có hình phạt bổ sung.','LOW','CLOSED', NOW() - INTERVAL '85 days' + INTERVAL '40 minutes'),
('91111111-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000033','GENERAL','Cuộc đua diễn ra an toàn, không có sự cố nghiêm trọng.','Không có hình phạt bổ sung.','LOW','CLOSED', NOW() - INTERVAL '88 days' + INTERVAL '40 minutes'),
('91111111-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000035','GENERAL','Cuộc đua diễn ra an toàn, không có sự cố nghiêm trọng.','Không có hình phạt bổ sung.','LOW','CLOSED', NOW() - INTERVAL '2 days' + INTERVAL '40 minutes');

-- =========================================================
-- PENALTY — entry luôn thuộc đúng race của penalty
-- =========================================================
INSERT INTO penalty (penalty_id, race_id, entry_id, report_id, penalty_type, time_penalty_ms,
                     fine_amount, reason, issued_by_user_id, status) VALUES
('92222222-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000006','91111111-0000-4000-8000-000000000001','WARNING', NULL, NULL,'Nhắc nhở do dùng roi quá số lần quy định.','11111111-0000-4000-8000-000000000032','ISSUED'),
('92222222-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000011','91111111-0000-4000-8000-000000000002','WARNING', NULL, NULL,'Nhắc nhở do dùng roi quá số lần quy định.','11111111-0000-4000-8000-000000000034','ISSUED'),
('92222222-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000016','91111111-0000-4000-8000-000000000003','WARNING', NULL, NULL,'Nhắc nhở do dùng roi quá số lần quy định.','11111111-0000-4000-8000-000000000031','ISSUED');

-- =========================================================
-- RACE_VIOLATION — entry + jockey luôn thuộc đúng race của violation
-- =========================================================
INSERT INTO race_violation (violation_id, race_id, entry_id, jockey_user_id, infraction_type, severity,
                            turn_no, race_time_offset_ms, remarks, regulatory_ref, footage_attachment_id,
                            status, reported_by_user_id, penalty_id, decision_type, ruling_notes,
                            ruled_by_user_id, ruled_at) VALUES
('93333333-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','77777777-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000026','WHIP_USAGE','LOW',4,142000,'Dùng roi vượt quá số lần cho phép ở đoạn nước rút.','ĐL-2026 Điều 12.3', NULL,'RESOLVED','11111111-0000-4000-8000-000000000032','92222222-0000-4000-8000-000000000001','WARN','Cảnh cáo, không ảnh hưởng thứ hạng.','11111111-0000-4000-8000-000000000032', NOW() - INTERVAL '196 days' + INTERVAL '45 minutes'),
('93333333-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000012','77777777-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000025','WHIP_USAGE','LOW',4,64900,'Dùng roi vượt quá số lần cho phép ở đoạn nước rút.','ĐL-2026 Điều 12.3', NULL,'RESOLVED','11111111-0000-4000-8000-000000000034','92222222-0000-4000-8000-000000000002','WARN','Cảnh cáo, không ảnh hưởng thứ hạng.','11111111-0000-4000-8000-000000000034', NOW() - INTERVAL '199 days' + INTERVAL '45 minutes'),
('93333333-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000001','77777777-0000-4000-8000-000000000016','11111111-0000-4000-8000-000000000025','WHIP_USAGE','LOW',4,90300,'Dùng roi vượt quá số lần cho phép ở đoạn nước rút.','ĐL-2026 Điều 12.3', NULL,'RESOLVED','11111111-0000-4000-8000-000000000031','92222222-0000-4000-8000-000000000003','WARN','Cảnh cáo, không ảnh hưởng thứ hạng.','11111111-0000-4000-8000-000000000031', NOW() - INTERVAL '85 days' + INTERVAL '45 minutes');

-- =========================================================
-- ATTACHMENT — mỗi registration APPROVED có đúng 1 dossier do CHÍNH owner của nó upload
-- =========================================================
-- approveRegistration() requires an attachment on the registration uploaded by that
-- registration's owner. 7 of the old seed's 10 approvals had none and could not be re-approved.
INSERT INTO attachment (attachment_id, owner_entity_type, owner_entity_id, object_key, file_name,
                        mime_type, file_size, sensitivity_level, uploaded_by_user_id) VALUES
('a3333333-0000-4000-8000-000000000001','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000001','attachments/seed-dossier-001.pdf','ho-so-REG00001.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000002','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000002','attachments/seed-dossier-002.pdf','ho-so-REG00002.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000003','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000003','attachments/seed-dossier-003.pdf','ho-so-REG00003.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000004','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000004','attachments/seed-dossier-004.pdf','ho-so-REG00004.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000005','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000005','attachments/seed-dossier-005.pdf','ho-so-REG00005.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000006','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000006','attachments/seed-dossier-006.pdf','ho-so-REG00006.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000007','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000007','attachments/seed-dossier-007.pdf','ho-so-REG00007.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000014'),
('a3333333-0000-4000-8000-000000000008','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000008','attachments/seed-dossier-008.pdf','ho-so-REG00008.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000009','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000009','attachments/seed-dossier-009.pdf','ho-so-REG00009.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000010','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000010','attachments/seed-dossier-010.pdf','ho-so-REG00010.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000011','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000011','attachments/seed-dossier-011.pdf','ho-so-REG00011.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000012','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000012','attachments/seed-dossier-012.pdf','ho-so-REG00012.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000013','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000013','attachments/seed-dossier-013.pdf','ho-so-REG00013.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000014','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000014','attachments/seed-dossier-014.pdf','ho-so-REG00014.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000015','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000015','attachments/seed-dossier-015.pdf','ho-so-REG00015.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000016','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000016','attachments/seed-dossier-016.pdf','ho-so-REG00016.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000017','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000017','attachments/seed-dossier-017.pdf','ho-so-REG00017.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000018','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000018','attachments/seed-dossier-018.pdf','ho-so-REG00018.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000014'),
('a3333333-0000-4000-8000-000000000019','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000019','attachments/seed-dossier-019.pdf','ho-so-REG00019.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000020','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000020','attachments/seed-dossier-020.pdf','ho-so-REG00020.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000021','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000021','attachments/seed-dossier-021.pdf','ho-so-REG00021.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000014'),
('a3333333-0000-4000-8000-000000000022','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000022','attachments/seed-dossier-022.pdf','ho-so-REG00022.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000014'),
('a3333333-0000-4000-8000-000000000023','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000023','attachments/seed-dossier-023.pdf','ho-so-REG00023.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000015'),
('a3333333-0000-4000-8000-000000000024','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000024','attachments/seed-dossier-024.pdf','ho-so-REG00024.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000015'),
('a3333333-0000-4000-8000-000000000025','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000025','attachments/seed-dossier-025.pdf','ho-so-REG00025.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000015'),
('a3333333-0000-4000-8000-000000000026','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000026','attachments/seed-dossier-026.pdf','ho-so-REG00026.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000027','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000027','attachments/seed-dossier-027.pdf','ho-so-REG00027.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000013'),
('a3333333-0000-4000-8000-000000000028','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000028','attachments/seed-dossier-028.pdf','ho-so-REG00028.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000013'),
('a3333333-0000-4000-8000-000000000029','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000029','attachments/seed-dossier-029.pdf','ho-so-REG00029.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000013'),
('a3333333-0000-4000-8000-000000000030','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000030','attachments/seed-dossier-030.pdf','ho-so-REG00030.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000014'),
('a3333333-0000-4000-8000-000000000031','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000031','attachments/seed-dossier-031.pdf','ho-so-REG00031.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000014'),
('a3333333-0000-4000-8000-000000000032','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000032','attachments/seed-dossier-032.pdf','ho-so-REG00032.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000033','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000033','attachments/seed-dossier-033.pdf','ho-so-REG00033.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000034','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000034','attachments/seed-dossier-034.pdf','ho-so-REG00034.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000035','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000035','attachments/seed-dossier-035.pdf','ho-so-REG00035.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000036','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000036','attachments/seed-dossier-036.pdf','ho-so-REG00036.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000037','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000037','attachments/seed-dossier-037.pdf','ho-so-REG00037.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000038','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000038','attachments/seed-dossier-038.pdf','ho-so-REG00038.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000039','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000039','attachments/seed-dossier-039.pdf','ho-so-REG00039.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000040','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000040','attachments/seed-dossier-040.pdf','ho-so-REG00040.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000041','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000041','attachments/seed-dossier-041.pdf','ho-so-REG00041.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000042','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000042','attachments/seed-dossier-042.pdf','ho-so-REG00042.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000043','JOCKEY_PROFILE','11111111-0000-4000-8000-000000000021','restricted/seed-jockey-license-01.png','giay-phep-jockey1.png','image/png',184320,'RESTRICTED','11111111-0000-4000-8000-000000000021'),
('a3333333-0000-4000-8000-000000000044','JOCKEY_PROFILE','11111111-0000-4000-8000-000000000022','restricted/seed-jockey-license-02.pdf','giay-phep-jockey2.pdf','application/pdf',96256,'RESTRICTED','11111111-0000-4000-8000-000000000022');

-- =========================================================
-- PAYMENT_TRANSACTION — 10 — nạp tiền mở đầu cho chủ ngựa và khán giả
-- =========================================================
INSERT INTO payment_transaction (payment_txn_id, business_entity_type, business_entity_id, transaction_type,
                                 amount, currency_code, payment_method, payment_status, external_txn_ref,
                                 idempotency_key, gateway_provider, raw_payload, wallet_id, created_by_user_id) VALUES
('95555555-0000-4000-8000-000000000001','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000011','DEPOSIT',41000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0001','idem-pay-0001','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000011'),
('95555555-0000-4000-8000-000000000002','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000012','DEPOSIT',35000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0002','idem-pay-0002','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000012'),
('95555555-0000-4000-8000-000000000003','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000013','DEPOSIT',13000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0003','idem-pay-0003','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000013','11111111-0000-4000-8000-000000000013'),
('95555555-0000-4000-8000-000000000004','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000014','DEPOSIT',18000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0004','idem-pay-0004','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000014','11111111-0000-4000-8000-000000000014'),
('95555555-0000-4000-8000-000000000005','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000015','DEPOSIT',13000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0005','idem-pay-0005','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000015'),
('95555555-0000-4000-8000-000000000006','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000041','DEPOSIT',11000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0006','idem-pay-0006','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000041','11111111-0000-4000-8000-000000000041'),
('95555555-0000-4000-8000-000000000007','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000042','DEPOSIT',10000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0007','idem-pay-0007','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000042','11111111-0000-4000-8000-000000000042'),
('95555555-0000-4000-8000-000000000008','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000043','DEPOSIT',10000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0008','idem-pay-0008','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000043','11111111-0000-4000-8000-000000000043'),
('95555555-0000-4000-8000-000000000009','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000044','DEPOSIT',10000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0009','idem-pay-0009','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000044','11111111-0000-4000-8000-000000000044'),
('95555555-0000-4000-8000-000000000010','WALLET_TOPUP','aaaaaaaa-0000-4000-8000-000000000045','DEPOSIT',10000000,'VND','MOCK','SUCCESS','MOCK-TOPUP-0010','idem-pay-0010','MOCK','{"seeded":true}'::jsonb,'aaaaaaaa-0000-4000-8000-000000000045','11111111-0000-4000-8000-000000000045');

-- =========================================================
-- BETTING_POOL — rake_percent là PHÂN SỐ (0.10 = 10%), không phải phần trăm
-- =========================================================
-- SettlementServiceImpl computes net = stake * (1 - rake_percent) and throws on any value >= 1.
-- The old seed stored 10.00 meaning "10%", which would have stranded every unsettled pool.
INSERT INTO betting_pool (pool_id, race_id, prediction_type, total_stake, rake_percent, status) VALUES
('94444444-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','WIN',1400000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000011','PLACE',900000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000011','SHOW',700000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000012','WIN',1400000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000012','PLACE',900000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000012','SHOW',700000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000007','55555555-0000-4000-8000-000000000001','WIN',1400000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000001','PLACE',900000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000009','55555555-0000-4000-8000-000000000001','SHOW',700000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000010','55555555-0000-4000-8000-000000000002','WIN',1400000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000002','PLACE',900000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000002','SHOW',550000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000015','WIN',1400000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000015','PLACE',900000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000015','55555555-0000-4000-8000-000000000015','SHOW',700000, 0.10,'CLOSED'),
('94444444-0000-4000-8000-000000000016','55555555-0000-4000-8000-000000000004','WIN',1400000, 0.10,'OPEN'),
('94444444-0000-4000-8000-000000000017','55555555-0000-4000-8000-000000000004','PLACE',900000, 0.10,'OPEN'),
('94444444-0000-4000-8000-000000000018','55555555-0000-4000-8000-000000000004','SHOW',700000, 0.10,'OPEN');

-- =========================================================
-- PREDICTION — 77 — tất cả PENDING, đặt TRƯỚC cutoff
-- =========================================================
-- locked_odds and potential_payout stay NULL: the backend never writes them (payout is derived
-- from the pool at settle time). submitted_at is cutoff-minus-30-minutes, so every historic bet
-- is one the API would have accepted — the old seed placed all of them 15 minutes too late.
INSERT INTO prediction (prediction_id, race_id, spectator_user_id, predicted_entry_id, prediction_type,
                        locked_odds, stake_amount, potential_payout, status, submitted_at, settled_at,
                        idempotency_key) VALUES
('99999999-0000-4000-8000-000000000001','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000001','WIN', NULL,500000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0001'),
('99999999-0000-4000-8000-000000000002','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000002','WIN', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0002'),
('99999999-0000-4000-8000-000000000003','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000003','WIN', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0003'),
('99999999-0000-4000-8000-000000000004','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000045','77777777-0000-4000-8000-000000000001','WIN', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0004'),
('99999999-0000-4000-8000-000000000005','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000001','PLACE', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0005'),
('99999999-0000-4000-8000-000000000006','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000002','PLACE', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0006'),
('99999999-0000-4000-8000-000000000007','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000003','PLACE', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0007'),
('99999999-0000-4000-8000-000000000008','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000001','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0008'),
('99999999-0000-4000-8000-000000000009','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000003','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0009'),
('99999999-0000-4000-8000-000000000010','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000004','WIN', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0010'),
('99999999-0000-4000-8000-000000000011','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000004','PLACE', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0011'),
('99999999-0000-4000-8000-000000000012','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000004','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0012'),
('99999999-0000-4000-8000-000000000013','55555555-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000005','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '196 days' - INTERVAL '45 minutes', NULL,'idem-pred-0013'),
('99999999-0000-4000-8000-000000000014','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000007','WIN', NULL,500000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0014'),
('99999999-0000-4000-8000-000000000015','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000008','WIN', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0015'),
('99999999-0000-4000-8000-000000000016','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000009','WIN', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0016'),
('99999999-0000-4000-8000-000000000017','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000045','77777777-0000-4000-8000-000000000007','WIN', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0017'),
('99999999-0000-4000-8000-000000000018','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000007','PLACE', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0018'),
('99999999-0000-4000-8000-000000000019','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000008','PLACE', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0019'),
('99999999-0000-4000-8000-000000000020','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000009','PLACE', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0020'),
('99999999-0000-4000-8000-000000000021','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000007','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0021'),
('99999999-0000-4000-8000-000000000022','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000009','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0022'),
('99999999-0000-4000-8000-000000000023','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000010','WIN', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0023'),
('99999999-0000-4000-8000-000000000024','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000010','PLACE', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0024'),
('99999999-0000-4000-8000-000000000025','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000010','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0025'),
('99999999-0000-4000-8000-000000000026','55555555-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000011','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '199 days' - INTERVAL '45 minutes', NULL,'idem-pred-0026'),
('99999999-0000-4000-8000-000000000027','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000012','WIN', NULL,500000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0027'),
('99999999-0000-4000-8000-000000000028','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000013','WIN', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0028'),
('99999999-0000-4000-8000-000000000029','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000014','WIN', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0029'),
('99999999-0000-4000-8000-000000000030','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000045','77777777-0000-4000-8000-000000000012','WIN', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0030'),
('99999999-0000-4000-8000-000000000031','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000012','PLACE', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0031'),
('99999999-0000-4000-8000-000000000032','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000013','PLACE', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0032'),
('99999999-0000-4000-8000-000000000033','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000014','PLACE', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0033'),
('99999999-0000-4000-8000-000000000034','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000012','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0034'),
('99999999-0000-4000-8000-000000000035','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000014','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0035'),
('99999999-0000-4000-8000-000000000036','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000015','WIN', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0036'),
('99999999-0000-4000-8000-000000000037','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000015','PLACE', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0037'),
('99999999-0000-4000-8000-000000000038','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000015','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0038'),
('99999999-0000-4000-8000-000000000039','55555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000016','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '85 days' - INTERVAL '45 minutes', NULL,'idem-pred-0039'),
('99999999-0000-4000-8000-000000000040','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000017','WIN', NULL,500000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0040'),
('99999999-0000-4000-8000-000000000041','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000018','WIN', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0041'),
('99999999-0000-4000-8000-000000000042','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000019','WIN', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0042'),
('99999999-0000-4000-8000-000000000043','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000045','77777777-0000-4000-8000-000000000017','WIN', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0043'),
('99999999-0000-4000-8000-000000000044','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000017','PLACE', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0044'),
('99999999-0000-4000-8000-000000000045','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000018','PLACE', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0045'),
('99999999-0000-4000-8000-000000000046','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000019','PLACE', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0046'),
('99999999-0000-4000-8000-000000000047','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000017','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0047'),
('99999999-0000-4000-8000-000000000048','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000019','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0048'),
('99999999-0000-4000-8000-000000000049','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000020','WIN', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0049'),
('99999999-0000-4000-8000-000000000050','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000020','PLACE', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0050'),
('99999999-0000-4000-8000-000000000051','55555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000020','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '88 days' - INTERVAL '45 minutes', NULL,'idem-pred-0051'),
('99999999-0000-4000-8000-000000000052','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000021','WIN', NULL,500000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0052'),
('99999999-0000-4000-8000-000000000053','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000022','WIN', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0053'),
('99999999-0000-4000-8000-000000000054','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000023','WIN', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0054'),
('99999999-0000-4000-8000-000000000055','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000045','77777777-0000-4000-8000-000000000021','WIN', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0055'),
('99999999-0000-4000-8000-000000000056','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000021','PLACE', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0056'),
('99999999-0000-4000-8000-000000000057','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000022','PLACE', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0057'),
('99999999-0000-4000-8000-000000000058','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000023','PLACE', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0058'),
('99999999-0000-4000-8000-000000000059','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000021','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0059'),
('99999999-0000-4000-8000-000000000060','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000023','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0060'),
('99999999-0000-4000-8000-000000000061','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000024','WIN', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0061'),
('99999999-0000-4000-8000-000000000062','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000024','PLACE', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0062'),
('99999999-0000-4000-8000-000000000063','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000024','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0063'),
('99999999-0000-4000-8000-000000000064','55555555-0000-4000-8000-000000000015','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000025','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '2 days' - INTERVAL '45 minutes', NULL,'idem-pred-0064'),
('99999999-0000-4000-8000-000000000065','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000031','WIN', NULL,500000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0065'),
('99999999-0000-4000-8000-000000000066','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000032','WIN', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0066'),
('99999999-0000-4000-8000-000000000067','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000033','WIN', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0067'),
('99999999-0000-4000-8000-000000000068','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000045','77777777-0000-4000-8000-000000000031','WIN', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0068'),
('99999999-0000-4000-8000-000000000069','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000031','PLACE', NULL,300000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0069'),
('99999999-0000-4000-8000-000000000070','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000032','PLACE', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0070'),
('99999999-0000-4000-8000-000000000071','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000033','PLACE', NULL,250000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0071'),
('99999999-0000-4000-8000-000000000072','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000041','77777777-0000-4000-8000-000000000031','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0072'),
('99999999-0000-4000-8000-000000000073','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000042','77777777-0000-4000-8000-000000000033','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0073'),
('99999999-0000-4000-8000-000000000074','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000034','WIN', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0074'),
('99999999-0000-4000-8000-000000000075','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000034','PLACE', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0075'),
('99999999-0000-4000-8000-000000000076','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000043','77777777-0000-4000-8000-000000000034','SHOW', NULL,200000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0076'),
('99999999-0000-4000-8000-000000000077','55555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000044','77777777-0000-4000-8000-000000000035','SHOW', NULL,150000, NULL,'PENDING', NOW() - INTERVAL '6 hours', NULL,'idem-pred-0077');

-- =========================================================
-- WALLET_TRANSACTION — 248 — sổ cái 2 vế, house trước rồi user
-- =========================================================
INSERT INTO wallet_transaction (wallet_txn_id, wallet_id, entry_type, txn_category, amount, balance_after,
                                related_entity_type, related_entity_id, payment_txn_id, created_at) VALUES
('96666666-0000-4000-8000-000000000001','aaaaaaaa-0000-4000-8000-000000000011','CREDIT','DEPOSIT',41000000,41000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000001','95555555-0000-4000-8000-000000000001', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000002','aaaaaaaa-0000-4000-8000-000000000012','CREDIT','DEPOSIT',35000000,35000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000002','95555555-0000-4000-8000-000000000002', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000003','aaaaaaaa-0000-4000-8000-000000000013','CREDIT','DEPOSIT',13000000,13000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000003','95555555-0000-4000-8000-000000000003', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000004','aaaaaaaa-0000-4000-8000-000000000014','CREDIT','DEPOSIT',18000000,18000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000004','95555555-0000-4000-8000-000000000004', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000005','aaaaaaaa-0000-4000-8000-000000000015','CREDIT','DEPOSIT',13000000,13000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000005','95555555-0000-4000-8000-000000000005', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000006','aaaaaaaa-0000-4000-8000-000000000041','CREDIT','DEPOSIT',11000000,11000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000006','95555555-0000-4000-8000-000000000006', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000007','aaaaaaaa-0000-4000-8000-000000000042','CREDIT','DEPOSIT',10000000,10000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000007','95555555-0000-4000-8000-000000000007', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000008','aaaaaaaa-0000-4000-8000-000000000043','CREDIT','DEPOSIT',10000000,10000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000008','95555555-0000-4000-8000-000000000008', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000009','aaaaaaaa-0000-4000-8000-000000000044','CREDIT','DEPOSIT',10000000,10000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000009','95555555-0000-4000-8000-000000000009', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000010','aaaaaaaa-0000-4000-8000-000000000045','CREDIT','DEPOSIT',10000000,10000000,'PAYMENT_TRANSACTION','95555555-0000-4000-8000-000000000010','95555555-0000-4000-8000-000000000010', NOW() - INTERVAL '400 days'),
('96666666-0000-4000-8000-000000000011',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',900000,900000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000012','aaaaaaaa-0000-4000-8000-000000000014','DEBIT','ENTRY_FEE',900000,17100000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000013',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',900000,1800000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000014','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',900000,40100000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000015',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',900000,2700000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000016','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',900000,39200000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000017',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',900000,3600000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000018','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',900000,38300000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000019',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',900000,4500000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000020','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',900000,34100000,'RACE','55555555-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '199 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000021',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2200000,6700000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000022','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',2200000,36100000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000023',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2200000,8900000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000024','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',2200000,33900000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000025',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2200000,11100000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000026','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',2200000,31700000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000027',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2200000,13300000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000028','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',2200000,31900000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000029',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2200000,15500000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000030','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',2200000,29700000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000031',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2200000,17700000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000032','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',2200000,27500000,'RACE','55555555-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000033',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',500000,18200000,'PREDICTION','99999999-0000-4000-8000-000000000014',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000034','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',500000,10500000,'PREDICTION','99999999-0000-4000-8000-000000000014',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000035',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,18500000,'PREDICTION','99999999-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000036','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',300000,9700000,'PREDICTION','99999999-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000037',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,18750000,'PREDICTION','99999999-0000-4000-8000-000000000016',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000038','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,9750000,'PREDICTION','99999999-0000-4000-8000-000000000016',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000039',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,18900000,'PREDICTION','99999999-0000-4000-8000-000000000017',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000040','aaaaaaaa-0000-4000-8000-000000000045','DEBIT','BET_STAKE',150000,9850000,'PREDICTION','99999999-0000-4000-8000-000000000017',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000041',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,19200000,'PREDICTION','99999999-0000-4000-8000-000000000018',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000042','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',300000,10200000,'PREDICTION','99999999-0000-4000-8000-000000000018',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000043',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,19400000,'PREDICTION','99999999-0000-4000-8000-000000000019',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000044','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',200000,9500000,'PREDICTION','99999999-0000-4000-8000-000000000019',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000045',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,19650000,'PREDICTION','99999999-0000-4000-8000-000000000020',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000046','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,9500000,'PREDICTION','99999999-0000-4000-8000-000000000020',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000047',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,19850000,'PREDICTION','99999999-0000-4000-8000-000000000021',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000048','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',200000,10000000,'PREDICTION','99999999-0000-4000-8000-000000000021',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000049',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,20000000,'PREDICTION','99999999-0000-4000-8000-000000000022',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000050','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',150000,9350000,'PREDICTION','99999999-0000-4000-8000-000000000022',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000051',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,20200000,'PREDICTION','99999999-0000-4000-8000-000000000023',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000052','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',200000,9800000,'PREDICTION','99999999-0000-4000-8000-000000000023',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000053',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,20350000,'PREDICTION','99999999-0000-4000-8000-000000000024',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000054','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,9650000,'PREDICTION','99999999-0000-4000-8000-000000000024',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000055',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,20550000,'PREDICTION','99999999-0000-4000-8000-000000000025',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000056','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',200000,9300000,'PREDICTION','99999999-0000-4000-8000-000000000025',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000057',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,20700000,'PREDICTION','99999999-0000-4000-8000-000000000026',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000058','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,9500000,'PREDICTION','99999999-0000-4000-8000-000000000026',NULL, NOW() - INTERVAL '199 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000059',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',500000,21200000,'PREDICTION','99999999-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000060','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',500000,9500000,'PREDICTION','99999999-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000061',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,21500000,'PREDICTION','99999999-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000062','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',300000,9050000,'PREDICTION','99999999-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000063',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,21750000,'PREDICTION','99999999-0000-4000-8000-000000000003',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000064','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,9050000,'PREDICTION','99999999-0000-4000-8000-000000000003',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000065',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,21900000,'PREDICTION','99999999-0000-4000-8000-000000000004',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000066','aaaaaaaa-0000-4000-8000-000000000045','DEBIT','BET_STAKE',150000,9700000,'PREDICTION','99999999-0000-4000-8000-000000000004',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000067',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,22200000,'PREDICTION','99999999-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000068','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',300000,9200000,'PREDICTION','99999999-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000069',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,22400000,'PREDICTION','99999999-0000-4000-8000-000000000006',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000070','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',200000,8850000,'PREDICTION','99999999-0000-4000-8000-000000000006',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000071',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,22650000,'PREDICTION','99999999-0000-4000-8000-000000000007',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000072','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,8800000,'PREDICTION','99999999-0000-4000-8000-000000000007',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000073',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,22850000,'PREDICTION','99999999-0000-4000-8000-000000000008',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000074','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',200000,9000000,'PREDICTION','99999999-0000-4000-8000-000000000008',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000075',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,23000000,'PREDICTION','99999999-0000-4000-8000-000000000009',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000076','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',150000,8700000,'PREDICTION','99999999-0000-4000-8000-000000000009',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000077',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,23200000,'PREDICTION','99999999-0000-4000-8000-000000000010',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000078','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',200000,9300000,'PREDICTION','99999999-0000-4000-8000-000000000010',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000079',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,23350000,'PREDICTION','99999999-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000080','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,9150000,'PREDICTION','99999999-0000-4000-8000-000000000011',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000081',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,23550000,'PREDICTION','99999999-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000082','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',200000,8600000,'PREDICTION','99999999-0000-4000-8000-000000000012',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000083',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,23700000,'PREDICTION','99999999-0000-4000-8000-000000000013',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000084','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,9000000,'PREDICTION','99999999-0000-4000-8000-000000000013',NULL, NOW() - INTERVAL '196 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000085',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',1000000,24700000,'RACE','55555555-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '88 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000086','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',1000000,26500000,'RACE','55555555-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '88 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000087',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',1000000,25700000,'RACE','55555555-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '88 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000088','aaaaaaaa-0000-4000-8000-000000000014','DEBIT','ENTRY_FEE',1000000,16100000,'RACE','55555555-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '88 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000089',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',1000000,26700000,'RACE','55555555-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '88 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000090','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',1000000,30700000,'RACE','55555555-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '88 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000091',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',1000000,27700000,'RACE','55555555-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '88 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000092','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',1000000,29700000,'RACE','55555555-0000-4000-8000-000000000002',NULL, NOW() - INTERVAL '88 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000093',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2000000,29700000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000094','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',2000000,27700000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000095',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2000000,31700000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000096','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',2000000,25700000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000097',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2000000,33700000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000098','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',2000000,23700000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000099',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2000000,35700000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000100','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',2000000,24500000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000101',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2000000,37700000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000102','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',2000000,22500000,'RACE','55555555-0000-4000-8000-000000000001',NULL, NOW() - INTERVAL '85 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000103',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',500000,38200000,'PREDICTION','99999999-0000-4000-8000-000000000040',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000104','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',500000,8500000,'PREDICTION','99999999-0000-4000-8000-000000000040',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000105',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,38500000,'PREDICTION','99999999-0000-4000-8000-000000000041',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000106','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',300000,8400000,'PREDICTION','99999999-0000-4000-8000-000000000041',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000107',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,38750000,'PREDICTION','99999999-0000-4000-8000-000000000042',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000108','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,8350000,'PREDICTION','99999999-0000-4000-8000-000000000042',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000109',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,38900000,'PREDICTION','99999999-0000-4000-8000-000000000043',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000110','aaaaaaaa-0000-4000-8000-000000000045','DEBIT','BET_STAKE',150000,9550000,'PREDICTION','99999999-0000-4000-8000-000000000043',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000111',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,39200000,'PREDICTION','99999999-0000-4000-8000-000000000044',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000112','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',300000,8200000,'PREDICTION','99999999-0000-4000-8000-000000000044',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000113',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,39400000,'PREDICTION','99999999-0000-4000-8000-000000000045',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000114','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',200000,8200000,'PREDICTION','99999999-0000-4000-8000-000000000045',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000115',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,39650000,'PREDICTION','99999999-0000-4000-8000-000000000046',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000116','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,8100000,'PREDICTION','99999999-0000-4000-8000-000000000046',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000117',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,39850000,'PREDICTION','99999999-0000-4000-8000-000000000047',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000118','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',200000,8000000,'PREDICTION','99999999-0000-4000-8000-000000000047',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000119',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,40000000,'PREDICTION','99999999-0000-4000-8000-000000000048',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000120','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',150000,8050000,'PREDICTION','99999999-0000-4000-8000-000000000048',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000121',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,40200000,'PREDICTION','99999999-0000-4000-8000-000000000049',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000122','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',200000,8800000,'PREDICTION','99999999-0000-4000-8000-000000000049',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000123',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,40350000,'PREDICTION','99999999-0000-4000-8000-000000000050',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000124','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,8650000,'PREDICTION','99999999-0000-4000-8000-000000000050',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000125',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,40550000,'PREDICTION','99999999-0000-4000-8000-000000000051',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000126','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',200000,7900000,'PREDICTION','99999999-0000-4000-8000-000000000051',NULL, NOW() - INTERVAL '88 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000127',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',500000,41050000,'PREDICTION','99999999-0000-4000-8000-000000000027',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000128','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',500000,7500000,'PREDICTION','99999999-0000-4000-8000-000000000027',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000129',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,41350000,'PREDICTION','99999999-0000-4000-8000-000000000028',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000130','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',300000,7750000,'PREDICTION','99999999-0000-4000-8000-000000000028',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000131',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,41600000,'PREDICTION','99999999-0000-4000-8000-000000000029',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000132','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,7650000,'PREDICTION','99999999-0000-4000-8000-000000000029',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000133',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,41750000,'PREDICTION','99999999-0000-4000-8000-000000000030',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000134','aaaaaaaa-0000-4000-8000-000000000045','DEBIT','BET_STAKE',150000,9400000,'PREDICTION','99999999-0000-4000-8000-000000000030',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000135',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,42050000,'PREDICTION','99999999-0000-4000-8000-000000000031',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000136','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',300000,7200000,'PREDICTION','99999999-0000-4000-8000-000000000031',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000137',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,42250000,'PREDICTION','99999999-0000-4000-8000-000000000032',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000138','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',200000,7550000,'PREDICTION','99999999-0000-4000-8000-000000000032',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000139',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,42500000,'PREDICTION','99999999-0000-4000-8000-000000000033',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000140','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,7400000,'PREDICTION','99999999-0000-4000-8000-000000000033',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000141',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,42700000,'PREDICTION','99999999-0000-4000-8000-000000000034',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000142','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',200000,7000000,'PREDICTION','99999999-0000-4000-8000-000000000034',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000143',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,42850000,'PREDICTION','99999999-0000-4000-8000-000000000035',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000144','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',150000,7400000,'PREDICTION','99999999-0000-4000-8000-000000000035',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000145',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,43050000,'PREDICTION','99999999-0000-4000-8000-000000000036',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000146','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',200000,8450000,'PREDICTION','99999999-0000-4000-8000-000000000036',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000147',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,43200000,'PREDICTION','99999999-0000-4000-8000-000000000037',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000148','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,8300000,'PREDICTION','99999999-0000-4000-8000-000000000037',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000149',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,43400000,'PREDICTION','99999999-0000-4000-8000-000000000038',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000150','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',200000,7200000,'PREDICTION','99999999-0000-4000-8000-000000000038',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000151',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,43550000,'PREDICTION','99999999-0000-4000-8000-000000000039',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000152','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,8150000,'PREDICTION','99999999-0000-4000-8000-000000000039',NULL, NOW() - INTERVAL '85 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000153',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,46050000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000154','aaaaaaaa-0000-4000-8000-000000000014','DEBIT','ENTRY_FEE',2500000,13600000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000155',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,48550000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000156','aaaaaaaa-0000-4000-8000-000000000014','DEBIT','ENTRY_FEE',2500000,11100000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000157',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,51050000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000158','aaaaaaaa-0000-4000-8000-000000000015','DEBIT','ENTRY_FEE',2500000,10500000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000159',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,53550000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000160','aaaaaaaa-0000-4000-8000-000000000015','DEBIT','ENTRY_FEE',2500000,8000000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000161',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,56050000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000162','aaaaaaaa-0000-4000-8000-000000000015','DEBIT','ENTRY_FEE',2500000,5500000,'RACE','55555555-0000-4000-8000-000000000015',NULL, NOW() - INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000163',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,58550000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000164','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',2500000,20000000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000165',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,61050000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000166','aaaaaaaa-0000-4000-8000-000000000013','DEBIT','ENTRY_FEE',2500000,10500000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000167',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,63550000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000168','aaaaaaaa-0000-4000-8000-000000000013','DEBIT','ENTRY_FEE',2500000,8000000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000169',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,66050000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000170','aaaaaaaa-0000-4000-8000-000000000013','DEBIT','ENTRY_FEE',2500000,5500000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000171',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',2500000,68550000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000172','aaaaaaaa-0000-4000-8000-000000000014','DEBIT','ENTRY_FEE',2500000,8600000,'RACE','55555555-0000-4000-8000-000000000005',NULL, NOW() - INTERVAL '0.01 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000173',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,71550000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000174','aaaaaaaa-0000-4000-8000-000000000014','DEBIT','ENTRY_FEE',3000000,5600000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000175',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,74550000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000176','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',3000000,20700000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000177',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,77550000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000178','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',3000000,17700000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000179',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,80550000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000180','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',3000000,14700000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000181',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,83550000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000182','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',3000000,17000000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000183',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,86550000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000184','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',3000000,14000000,'RACE','55555555-0000-4000-8000-000000000004',NULL, NOW() + INTERVAL '1 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000185',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,89550000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000186','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',3000000,11700000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000187',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,92550000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000188','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',3000000,8700000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000189',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,95550000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000190','aaaaaaaa-0000-4000-8000-000000000011','DEBIT','ENTRY_FEE',3000000,5700000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000191',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,98550000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000192','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',3000000,11000000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000193',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,101550000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000194','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',3000000,8000000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000195',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','ENTRY_FEE',3000000,104550000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000196','aaaaaaaa-0000-4000-8000-000000000012','DEBIT','ENTRY_FEE',3000000,5000000,'RACE','55555555-0000-4000-8000-000000000003',NULL, NOW() + INTERVAL '2 days' - INTERVAL '18 days'),
('96666666-0000-4000-8000-000000000197',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',500000,105050000,'PREDICTION','99999999-0000-4000-8000-000000000052',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000198','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',500000,6500000,'PREDICTION','99999999-0000-4000-8000-000000000052',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000199',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,105350000,'PREDICTION','99999999-0000-4000-8000-000000000053',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000200','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',300000,7100000,'PREDICTION','99999999-0000-4000-8000-000000000053',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000201',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,105600000,'PREDICTION','99999999-0000-4000-8000-000000000054',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000202','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,6950000,'PREDICTION','99999999-0000-4000-8000-000000000054',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000203',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,105750000,'PREDICTION','99999999-0000-4000-8000-000000000055',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000204','aaaaaaaa-0000-4000-8000-000000000045','DEBIT','BET_STAKE',150000,9250000,'PREDICTION','99999999-0000-4000-8000-000000000055',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000205',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,106050000,'PREDICTION','99999999-0000-4000-8000-000000000056',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000206','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',300000,6200000,'PREDICTION','99999999-0000-4000-8000-000000000056',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000207',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,106250000,'PREDICTION','99999999-0000-4000-8000-000000000057',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000208','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',200000,6900000,'PREDICTION','99999999-0000-4000-8000-000000000057',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000209',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,106500000,'PREDICTION','99999999-0000-4000-8000-000000000058',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000210','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,6700000,'PREDICTION','99999999-0000-4000-8000-000000000058',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000211',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,106700000,'PREDICTION','99999999-0000-4000-8000-000000000059',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000212','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',200000,6000000,'PREDICTION','99999999-0000-4000-8000-000000000059',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000213',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,106850000,'PREDICTION','99999999-0000-4000-8000-000000000060',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000214','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',150000,6750000,'PREDICTION','99999999-0000-4000-8000-000000000060',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000215',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,107050000,'PREDICTION','99999999-0000-4000-8000-000000000061',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000216','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',200000,7950000,'PREDICTION','99999999-0000-4000-8000-000000000061',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000217',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,107200000,'PREDICTION','99999999-0000-4000-8000-000000000062',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000218','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,7800000,'PREDICTION','99999999-0000-4000-8000-000000000062',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000219',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,107400000,'PREDICTION','99999999-0000-4000-8000-000000000063',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000220','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',200000,6500000,'PREDICTION','99999999-0000-4000-8000-000000000063',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000221',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,107550000,'PREDICTION','99999999-0000-4000-8000-000000000064',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000222','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,7650000,'PREDICTION','99999999-0000-4000-8000-000000000064',NULL, NOW() - INTERVAL '2 days' - INTERVAL '45 minutes'),
('96666666-0000-4000-8000-000000000223',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',500000,108050000,'PREDICTION','99999999-0000-4000-8000-000000000065',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000224','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',500000,5500000,'PREDICTION','99999999-0000-4000-8000-000000000065',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000225',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,108350000,'PREDICTION','99999999-0000-4000-8000-000000000066',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000226','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',300000,6450000,'PREDICTION','99999999-0000-4000-8000-000000000066',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000227',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,108600000,'PREDICTION','99999999-0000-4000-8000-000000000067',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000228','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,6250000,'PREDICTION','99999999-0000-4000-8000-000000000067',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000229',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,108750000,'PREDICTION','99999999-0000-4000-8000-000000000068',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000230','aaaaaaaa-0000-4000-8000-000000000045','DEBIT','BET_STAKE',150000,9100000,'PREDICTION','99999999-0000-4000-8000-000000000068',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000231',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',300000,109050000,'PREDICTION','99999999-0000-4000-8000-000000000069',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000232','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',300000,5200000,'PREDICTION','99999999-0000-4000-8000-000000000069',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000233',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,109250000,'PREDICTION','99999999-0000-4000-8000-000000000070',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000234','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',200000,6250000,'PREDICTION','99999999-0000-4000-8000-000000000070',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000235',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',250000,109500000,'PREDICTION','99999999-0000-4000-8000-000000000071',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000236','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',250000,6000000,'PREDICTION','99999999-0000-4000-8000-000000000071',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000237',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,109700000,'PREDICTION','99999999-0000-4000-8000-000000000072',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000238','aaaaaaaa-0000-4000-8000-000000000041','DEBIT','BET_STAKE',200000,5000000,'PREDICTION','99999999-0000-4000-8000-000000000072',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000239',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,109850000,'PREDICTION','99999999-0000-4000-8000-000000000073',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000240','aaaaaaaa-0000-4000-8000-000000000042','DEBIT','BET_STAKE',150000,6100000,'PREDICTION','99999999-0000-4000-8000-000000000073',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000241',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,110050000,'PREDICTION','99999999-0000-4000-8000-000000000074',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000242','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',200000,7450000,'PREDICTION','99999999-0000-4000-8000-000000000074',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000243',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,110200000,'PREDICTION','99999999-0000-4000-8000-000000000075',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000244','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,7300000,'PREDICTION','99999999-0000-4000-8000-000000000075',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000245',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',200000,110400000,'PREDICTION','99999999-0000-4000-8000-000000000076',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000246','aaaaaaaa-0000-4000-8000-000000000043','DEBIT','BET_STAKE',200000,5800000,'PREDICTION','99999999-0000-4000-8000-000000000076',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000247',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'CREDIT','BET_STAKE',150000,110550000,'PREDICTION','99999999-0000-4000-8000-000000000077',NULL, NOW() - INTERVAL '6 hours'),
('96666666-0000-4000-8000-000000000248','aaaaaaaa-0000-4000-8000-000000000044','DEBIT','BET_STAKE',150000,7150000,'PREDICTION','99999999-0000-4000-8000-000000000077',NULL, NOW() - INTERVAL '6 hours');

INSERT INTO tournament_referee_assignment (tournament_ref_assignment_id, tournament_id, referee_user_id,
                                           panel_role, status, invited_by_user_id, invited_at, responded_at) VALUES
('75555555-0000-4000-8000-000000000001','44444444-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000031','CHIEF',  'ACCEPTED','11111111-0000-4000-8000-000000000001', NOW() - INTERVAL '115 days', NOW() - INTERVAL '113 days'),
('75555555-0000-4000-8000-000000000002','44444444-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000032','JUDGE',  'ACCEPTED','11111111-0000-4000-8000-000000000001', NOW() - INTERVAL '115 days', NOW() - INTERVAL '112 days'),
('75555555-0000-4000-8000-000000000003','44444444-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000031','CHIEF',  'ACCEPTED','11111111-0000-4000-8000-000000000001', NOW() - INTERVAL '38 days', NOW() - INTERVAL '37 days'),
('75555555-0000-4000-8000-000000000004','44444444-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000033','STEWARD','ACCEPTED','11111111-0000-4000-8000-000000000001', NOW() - INTERVAL '38 days', NOW() - INTERVAL '36 days'),
('75555555-0000-4000-8000-000000000005','44444444-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000035','JUDGE',  'INVITED', '11111111-0000-4000-8000-000000000002', NOW() - INTERVAL '4 days', NULL),
('75555555-0000-4000-8000-000000000006','44444444-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000032','CHIEF',  'ACCEPTED','11111111-0000-4000-8000-000000000002', NOW() - INTERVAL '6 days', NOW() - INTERVAL '5 days'),
('75555555-0000-4000-8000-000000000007','44444444-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000034','CHIEF',  'ACCEPTED','11111111-0000-4000-8000-000000000002', NOW() - INTERVAL '28 days', NOW() - INTERVAL '27 days'),
('75555555-0000-4000-8000-000000000008','44444444-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000033','CHIEF',  'ACCEPTED','11111111-0000-4000-8000-000000000003', NOW() - INTERVAL '230 days', NOW() - INTERVAL '229 days'),
('75555555-0000-4000-8000-000000000009','44444444-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000035','CHIEF',  'DECLINED','11111111-0000-4000-8000-000000000004', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days'),
('75555555-0000-4000-8000-000000000010','44444444-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000031','CHIEF',  'INVITED', '11111111-0000-4000-8000-000000000005', NOW() - INTERVAL '1 day', NULL);

-- =========================================================
-- REFEREE_SUBMISSION_CODE — 10 (OTP gửi email để trọng tài chốt kết quả; chỉ lưu SHA-256 hash)
-- =========================================================
INSERT INTO reward (reward_id, user_id, reward_type, amount, title, description, status, expires_at, claimed_at) VALUES
('a1111111-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000041','DAILY_LOGIN',   10000,'Điểm danh ngày 1', 'Thưởng đăng nhập hằng ngày.',                'CLAIMED', NOW() + INTERVAL '30 days', NOW() - INTERVAL '2 days'),
('a1111111-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000041','MILESTONE',    100000,'Đặt cược lần thứ 10','Mốc 10 lượt dự đoán.',                    'CLAIMED', NOW() + INTERVAL '60 days', NOW() - INTERVAL '10 days'),
('a1111111-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000042','DAILY_LOGIN',   10000,'Điểm danh ngày 1', 'Thưởng đăng nhập hằng ngày.',                'PENDING', NOW() + INTERVAL '30 days', NULL),
('a1111111-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000042','PROMOTION',    200000,'Khuyến mãi Cúp Mùa Xuân','Ưu đãi nhân dịp giải mở màn.',        'CLAIMED', NOW() - INTERVAL '80 days', NOW() - INTERVAL '86 days'),
('a1111111-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000043','REFERRAL',      50000,'Giới thiệu bạn bè','Bạn được giới thiệu đã đăng ký thành công.','PENDING', NOW() + INTERVAL '45 days', NULL),
('a1111111-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000044','MILESTONE',    500000,'Tổng cược 10 triệu','Mốc tích luỹ cược.',                    'CLAIMED', NOW() + INTERVAL '90 days', NOW() - INTERVAL '5 days'),
('a1111111-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000045','COMPENSATION', 120000,'Hoàn tiền đua bị huỷ','Bồi thường do Cúp Tam Đảo bị huỷ.',    'PENDING', NOW() + INTERVAL '20 days', NULL),
('a1111111-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000045','DAILY_LOGIN',   10000,'Điểm danh ngày 1','Thưởng đăng nhập hằng ngày.',                'EXPIRED', NOW() - INTERVAL '5 days', NULL),
('a1111111-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000011','MILESTONE',   1000000,'Chủ ngựa vô địch','Ngựa đầu tiên vô địch một giải Group.',     'CLAIMED', NOW() + INTERVAL '120 days', NOW() - INTERVAL '84 days'),
('a1111111-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000021','MILESTONE',    800000,'Nài ngựa 50 chiến thắng','Mốc 50 lần về nhất.',              'PENDING', NOW() + INTERVAL '180 days', NULL);

-- =========================================================
-- NOTIFICATION — 12
-- =========================================================
INSERT INTO notification (notification_id, recipient_user_id, title, message, channel, delivery_status,
                          is_read, sent_at, read_at) VALUES
('a2222222-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000011','Hồ sơ đăng ký được duyệt','Ngựa Bạch Long Mã đã được duyệt tham dự Cúp Mùa Xuân 2026.','IN_APP','SENT',  TRUE,  NOW() - INTERVAL '105 days', NOW() - INTERVAL '105 days'),
('a2222222-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000011','Kết quả chính thức','Bạch Long Mã về nhất Chung kết Cúp Mùa Xuân, tiền thưởng 200.000.000đ.','EMAIL','SENT',  TRUE,  NOW() - INTERVAL '84 days',  NOW() - INTERVAL '84 days'),
('a2222222-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000021','Lời mời cầm cương','Bạn được mời cầm cương Bạch Long Mã tại Bán kết 1 - Vô Địch QG.','IN_APP','SENT',  FALSE, NOW() - INTERVAL '2 days',   NULL),
('a2222222-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000021','Cảnh cáo kỷ luật','Bạn nhận cảnh cáo do lỗi dùng roi vượt giới hạn.','EMAIL','SENT',  TRUE,  NOW() - INTERVAL '84 days',  NOW() - INTERVAL '83 days'),
('a2222222-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000031','Phân công trọng tài','Bạn được phân công làm Trọng tài chính cuộc đua Bán kết 1.','IN_APP','SENT',  TRUE,  NOW() - INTERVAL '20 days',  NOW() - INTERVAL '19 days'),
('a2222222-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000035','Lời mời làm trọng tài','Bạn được mời tham gia ban trọng tài Giải Vô Địch Quốc Gia 2026.','EMAIL','SENT',  FALSE, NOW() - INTERVAL '4 days',   NULL),
('a2222222-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000041','Chúc mừng thắng cược','Vé WIN của bạn thắng 1.750.000đ, đã cộng vào ví.','IN_APP','SENT',  TRUE,  NOW() - INTERVAL '84 days',  NOW() - INTERVAL '84 days'),
('a2222222-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000042','Kết quả dự đoán','Rất tiếc, vé WIN của bạn không trúng.','IN_APP','SENT',  TRUE,  NOW() - INTERVAL '84 days',  NOW() - INTERVAL '83 days'),
('a2222222-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000043','Nạp tiền thành công','Ví của bạn đã được cộng 1.000.000đ.','SMS','SENT',  FALSE, NOW() - INTERVAL '30 days',  NULL),
('a2222222-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000045','Giao dịch thất bại','Giao dịch nạp 300.000đ không thành công, vui lòng thử lại.','EMAIL','FAILED',FALSE, NULL, NULL),
('a2222222-0000-4000-8000-000000000011','11111111-0000-4000-8000-000000000014','Hồ sơ đang chờ duyệt','Hồ sơ đăng ký ngựa Lôi Đình đang được xem xét.','IN_APP','PENDING',FALSE, NULL, NULL),
('a2222222-0000-4000-8000-000000000012','11111111-0000-4000-8000-000000000015','Hồ sơ bị từ chối','Ngựa Tiểu Bạch chưa đủ tuổi tối thiểu theo điều lệ Cúp Duyên Hải 2026.','PUSH','SENT',  TRUE,  NOW() - INTERVAL '2 days',   NOW() - INTERVAL '1 day');

-- =========================================================
-- ATTACHMENT — 10. object_key trỏ FILE THẬT trong uploads/ (KHÔNG lên Cloudinary:
-- AttachmentServiceImpl ép @Qualifier(localFileStorage), RESTRICTED không bao giờ ra CDN public).
-- =========================================================
INSERT INTO membership_application (application_id, application_code, requested_role, status, priority,
                                    full_name, date_of_birth, tax_id, email, phone, avatar_url, location,
                                    org_name, id_verification_status, id_document_ref, license_class,
                                    license_status, license_valid_until, background_check_status,
                                    submitted_at, reviewed_at, reviewed_by_user_id, rejection_reason,
                                    requested_info_note, created_user_id) VALUES
('a4444444-0000-4000-8000-000000000001','APP-7B2E44D1','JOCKEY','APPROVED',      'NORMAL','Lý Tuấn Kiệt',   '1997-03-12', NULL,'jockey1@horserace.local','0900000012','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-01.jpg','Hà Nội',        NULL,        'VALID',  '0010****4521','A','ACTIVE','2027-06-30','PASSED',  NOW() - INTERVAL '400 days', NOW() - INTERVAL '395 days','11111111-0000-4000-8000-000000000031', NULL, NULL,'11111111-0000-4000-8000-000000000021'),
('a4444444-0000-4000-8000-000000000002','APP-3F91C2A5','OWNER', 'APPROVED',      'NORMAL','Đặng Minh Chủ',  '1985-08-25','0312****89','horseowner1@horserace.local','0900000007','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-02.jpg','Hà Nội','Trại ngựa Minh Long','VALID','0010****7788', NULL, NULL, NULL,'PASSED', NOW() - INTERVAL '500 days', NOW() - INTERVAL '494 days','11111111-0000-4000-8000-000000000031', NULL, NULL,'11111111-0000-4000-8000-000000000011'),
('a4444444-0000-4000-8000-000000000003','APP-C8D0B6E4','JOCKEY','PENDING',       'URGENT','Nguyễn Văn Hùng','2001-11-02', NULL,'hung.nguyen.applicant@example.com','0911000001','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-03.jpg','TP. Hồ Chí Minh',NULL,      'PENDING','0079****1122','B','NONE',   NULL,       'PENDING', NOW() - INTERVAL '2 days', NULL, NULL, NULL, NULL, NULL),
('a4444444-0000-4000-8000-000000000004','APP-A1F73B92','OWNER', 'UNDER_REVIEW',  'NORMAL','Trần Thị Mai',   '1990-05-19','0313****45','mai.tran.applicant@example.com','0911000002','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-04.jpg','Bình Dương','Mai Stable','VALID','0079****3344', NULL, NULL, NULL,'PENDING', NOW() - INTERVAL '5 days', NULL, NULL, NULL, NULL, NULL),
('a4444444-0000-4000-8000-000000000005','APP-6E24D5C8','JOCKEY','INFO_REQUESTED','NORMAL','Phạm Quốc Bảo',  '2003-01-30', NULL,'bao.pham.applicant@example.com','0911000003','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-05.jpg','Đà Nẵng',      NULL,        'PENDING','0048****5566','C','NONE',   NULL,       'PENDING', NOW() - INTERVAL '8 days', NOW() - INTERVAL '6 days','11111111-0000-4000-8000-000000000032', NULL,'Vui lòng bổ sung giấy chứng nhận sức khoẻ còn hiệu lực trong 30 ngày.', NULL),
('a4444444-0000-4000-8000-000000000006','APP-B9C15A37','JOCKEY','REJECTED',      'NORMAL','Võ Thành Nam',   '2008-07-14', NULL,'nam.vo.applicant@example.com','0911000004','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-06.jpg','Cần Thơ',      NULL,        'FAILED', '0092****7788', NULL,'NONE',   NULL,       'FAILED',  NOW() - INTERVAL '15 days', NOW() - INTERVAL '12 days','11111111-0000-4000-8000-000000000032','Chưa đủ 16 tuổi theo quy định tối thiểu cho nài ngựa.', NULL, NULL),
('a4444444-0000-4000-8000-000000000007','APP-D4E86F21','OWNER', 'REJECTED',      'NORMAL','Hoàng Gia Bảo',  '1979-09-08','0314****11','baohoang.applicant@example.com','0911000005','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-07.jpg','Hải Dương','Gia Bảo Farm','FAILED','0030****9900', NULL, NULL, NULL,'FAILED', NOW() - INTERVAL '25 days', NOW() - INTERVAL '20 days','11111111-0000-4000-8000-000000000033','Giấy tờ pháp nhân không khớp với mã số thuế cung cấp.', NULL, NULL),
('a4444444-0000-4000-8000-000000000008','APP-2A7B93C6','OWNER', 'APPROVED',      'NORMAL','Vũ Thanh Trại',  '1988-12-01','0315****22','horseowner2@horserace.local','0900000008','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-08.jpg','TP. Hồ Chí Minh','Thanh Trại Stable','VALID','0079****2233', NULL, NULL, NULL,'PASSED', NOW() - INTERVAL '450 days', NOW() - INTERVAL '445 days','11111111-0000-4000-8000-000000000031', NULL, NULL,'11111111-0000-4000-8000-000000000012'),
('a4444444-0000-4000-8000-000000000009','APP-F05D1E48','JOCKEY','PENDING',       'URGENT','Bùi Khánh Linh', '2002-04-22', NULL,'linh.bui.applicant@example.com','0911000006','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-09.jpg','Hà Nội',       NULL,        'VALID',  '0010****4455','B','ACTIVE','2028-01-15','PASSED', NOW() - INTERVAL '1 day', NULL, NULL, NULL, NULL, NULL),
('a4444444-0000-4000-8000-000000000010','APP-8C36A2B7','OWNER', 'UNDER_REVIEW',  'NORMAL','Ngô Kim Long',   '1982-02-11','0316****33','horseowner4@horserace.local','0900000010','https://res.cloudinary.com/qtpgbwsh/image/upload/f_auto,q_auto/applications/seed-applicant-10.jpg','Lâm Đồng','Kim Long Equestrian','VALID','0068****6677', NULL, NULL, NULL,'PENDING', NOW() - INTERVAL '3 days', NULL, NULL, NULL, NULL,'11111111-0000-4000-8000-000000000014');

-- =========================================================
-- REFRESH_TOKEN — 10 (chỉ lưu SHA-256 hash của token, UNIQUE token_hash)
-- =========================================================
INSERT INTO refresh_token (token_id, user_id, token_hash, expires_at, revoked, revoked_at, replaced_by_token_id, user_agent) VALUES
('a5555555-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000001', encode(sha256('seed-rt-admin1'::bytea),'hex'),     NOW() + INTERVAL '7 days',  FALSE, NULL, NULL,'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/126.0'),
('a5555555-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000011', encode(sha256('seed-rt-owner1'::bytea),'hex'),     NOW() + INTERVAL '7 days',  FALSE, NULL, NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0'),
('a5555555-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000012', encode(sha256('seed-rt-owner2'::bytea),'hex'),     NOW() + INTERVAL '7 days',  FALSE, NULL, NULL,'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5) Safari/605.1'),
('a5555555-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000021', encode(sha256('seed-rt-jockey1'::bytea),'hex'),    NOW() + INTERVAL '7 days',  FALSE, NULL, NULL,'Mozilla/5.0 (Linux; Android 14) Chrome/126.0'),
('a5555555-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000022', encode(sha256('seed-rt-jockey2'::bytea),'hex'),    NOW() - INTERVAL '1 day',   FALSE, NULL, NULL,'Mozilla/5.0 (Windows NT 10.0) Firefox/127.0'),
('a5555555-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000031', encode(sha256('seed-rt-referee1'::bytea),'hex'),   NOW() + INTERVAL '7 days',  FALSE, NULL, NULL,'Mozilla/5.0 (Macintosh) Safari/17.5'),
('a5555555-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000032', encode(sha256('seed-rt-referee2-old'::bytea),'hex'),NOW() + INTERVAL '7 days', TRUE,  NOW() - INTERVAL '2 hours','a5555555-0000-4000-8000-000000000008','Mozilla/5.0 (Windows NT 10.0) Edge/126.0'),
('a5555555-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000032', encode(sha256('seed-rt-referee2-new'::bytea),'hex'),NOW() + INTERVAL '7 days', FALSE, NULL, NULL,'Mozilla/5.0 (Windows NT 10.0) Edge/126.0'),
('a5555555-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000041', encode(sha256('seed-rt-spectator1'::bytea),'hex'), NOW() + INTERVAL '7 days',  FALSE, NULL, NULL,'Mozilla/5.0 (Linux; Android 13) Chrome/125.0'),
('a5555555-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000042', encode(sha256('seed-rt-spectator2'::bytea),'hex'), NOW() + INTERVAL '7 days',  TRUE,  NOW() - INTERVAL '1 day', NULL,'Mozilla/5.0 (iPad; CPU OS 17_5) Safari/605.1');

-- =========================================================
-- EMAIL_VERIFICATION_TOKEN — 10 (OTP 6 số, lưu hash). 25 user demo đều đã verified.
-- =========================================================
INSERT INTO email_verification_token (token_id, user_id, code_hash, expires_at, used, used_at) VALUES
('a6666666-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000011', encode(sha256('100001'::bytea),'hex'), NOW() - INTERVAL '480 days', TRUE,  NOW() - INTERVAL '480 days'),
('a6666666-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000012', encode(sha256('100002'::bytea),'hex'), NOW() - INTERVAL '440 days', TRUE,  NOW() - INTERVAL '440 days'),
('a6666666-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000013', encode(sha256('100003'::bytea),'hex'), NOW() - INTERVAL '300 days', TRUE,  NOW() - INTERVAL '300 days'),
('a6666666-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000021', encode(sha256('100004'::bytea),'hex'), NOW() - INTERVAL '390 days', TRUE,  NOW() - INTERVAL '390 days'),
('a6666666-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000022', encode(sha256('100005'::bytea),'hex'), NOW() - INTERVAL '250 days', TRUE,  NOW() - INTERVAL '250 days'),
('a6666666-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000031', encode(sha256('100006'::bytea),'hex'), NOW() - INTERVAL '200 days', TRUE,  NOW() - INTERVAL '200 days'),
('a6666666-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000032', encode(sha256('100007'::bytea),'hex'), NOW() - INTERVAL '180 days', TRUE,  NOW() - INTERVAL '180 days'),
('a6666666-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000041', encode(sha256('100008'::bytea),'hex'), NOW() - INTERVAL '150 days', TRUE,  NOW() - INTERVAL '150 days'),
('a6666666-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000042', encode(sha256('100009'::bytea),'hex'), NOW() - INTERVAL '120 days', TRUE,  NOW() - INTERVAL '120 days'),
('a6666666-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000045', encode(sha256('100010'::bytea),'hex'), NOW() + INTERVAL '15 minutes', FALSE, NULL);

-- =========================================================
-- PASSWORD_RESET_TOKEN — 10
-- =========================================================
INSERT INTO password_reset_token (token_id, user_id, code_hash, expires_at, used, used_at, attempt_count) VALUES
('a7777777-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000011', encode(sha256('200001'::bytea),'hex'), NOW() - INTERVAL '60 days',    TRUE,  NOW() - INTERVAL '60 days', 1),
('a7777777-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000012', encode(sha256('200002'::bytea),'hex'), NOW() - INTERVAL '45 days',    TRUE,  NOW() - INTERVAL '45 days', 1),
('a7777777-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000021', encode(sha256('200003'::bytea),'hex'), NOW() - INTERVAL '30 days',    FALSE, NULL, 3),
('a7777777-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000022', encode(sha256('200004'::bytea),'hex'), NOW() - INTERVAL '20 days',    TRUE,  NOW() - INTERVAL '20 days', 2),
('a7777777-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000031', encode(sha256('200005'::bytea),'hex'), NOW() - INTERVAL '10 days',    TRUE,  NOW() - INTERVAL '10 days', 1),
('a7777777-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000032', encode(sha256('200006'::bytea),'hex'), NOW() - INTERVAL '5 days',     FALSE, NULL, 0),
('a7777777-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000041', encode(sha256('200007'::bytea),'hex'), NOW() - INTERVAL '2 days',     TRUE,  NOW() - INTERVAL '2 days',  1),
('a7777777-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000042', encode(sha256('200008'::bytea),'hex'), NOW() + INTERVAL '10 minutes', FALSE, NULL, 0),
('a7777777-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000043', encode(sha256('200009'::bytea),'hex'), NOW() + INTERVAL '12 minutes', FALSE, NULL, 1),
('a7777777-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000044', encode(sha256('200010'::bytea),'hex'), NOW() - INTERVAL '1 hour',     FALSE, NULL, 5);

-- =========================================================
-- EMAIL_CHANGE_REQUEST — 10
-- =========================================================
INSERT INTO email_change_request (request_id, user_id, new_email, code_hash, expires_at, consumed, consumed_at) VALUES
('a8888888-0000-4000-8000-000000000001','11111111-0000-4000-8000-000000000011','minhchu.dang@example.com',   encode(sha256('300001'::bytea),'hex'), NOW() - INTERVAL '90 days',    TRUE,  NOW() - INTERVAL '90 days'),
('a8888888-0000-4000-8000-000000000002','11111111-0000-4000-8000-000000000012','thanhtrai.vu@example.com',   encode(sha256('300002'::bytea),'hex'), NOW() - INTERVAL '70 days',    TRUE,  NOW() - INTERVAL '70 days'),
('a8888888-0000-4000-8000-000000000003','11111111-0000-4000-8000-000000000013','daiphat.bui@example.com',    encode(sha256('300003'::bytea),'hex'), NOW() - INTERVAL '50 days',    FALSE, NULL),
('a8888888-0000-4000-8000-000000000004','11111111-0000-4000-8000-000000000021','tuankiet.ly@example.com',    encode(sha256('300004'::bytea),'hex'), NOW() - INTERVAL '40 days',    TRUE,  NOW() - INTERVAL '40 days'),
('a8888888-0000-4000-8000-000000000005','11111111-0000-4000-8000-000000000022','giahuy.trinh@example.com',   encode(sha256('300005'::bytea),'hex'), NOW() - INTERVAL '25 days',    FALSE, NULL),
('a8888888-0000-4000-8000-000000000006','11111111-0000-4000-8000-000000000031','congly.phan@example.com',    encode(sha256('300006'::bytea),'hex'), NOW() - INTERVAL '15 days',    TRUE,  NOW() - INTERVAL '15 days'),
('a8888888-0000-4000-8000-000000000007','11111111-0000-4000-8000-000000000032','nghiemminh.ta@example.com',  encode(sha256('300007'::bytea),'hex'), NOW() - INTERVAL '8 days',     FALSE, NULL),
('a8888888-0000-4000-8000-000000000008','11111111-0000-4000-8000-000000000041','khangia.nguyen@example.com', encode(sha256('300008'::bytea),'hex'), NOW() + INTERVAL '10 minutes', FALSE, NULL),
('a8888888-0000-4000-8000-000000000009','11111111-0000-4000-8000-000000000042','covu.tran@example.com',      encode(sha256('300009'::bytea),'hex'), NOW() + INTERVAL '13 minutes', FALSE, NULL),
('a8888888-0000-4000-8000-000000000010','11111111-0000-4000-8000-000000000043','hammo.le@example.com',       encode(sha256('300010'::bytea),'hex'), NOW() - INTERVAL '2 hours',    FALSE, NULL);


-- =========================================================
-- KẾT QUẢ CERTIFY + SETTLE — sinh bởi CHÍNH code thật
-- =========================================================
-- Toàn bộ số tiền dưới đây là output của RaceResultServiceImpl.certify() và
-- SettlementServiceImpl.settleRace() chạy thật trên 2 race lịch sử (RACE2026001, RACE2025011),
-- rồi dump ngược lại. Không con số nào được tính tay — đó là lý do pool cân bằng chính xác:
-- tiền chi ra = total_stake * (1 - rake) trừ phần lẻ làm tròn xuống bội 1.000 (floorTo1000).

-- Race + kết quả lên OFFICIAL (certify là thứ duy nhất set certified_by/certified_at).
UPDATE race SET status='OFFICIAL', certified_by_user_id='11111111-0000-4000-8000-000000000001', certified_at = NOW() - INTERVAL '84 days', stewards_report='Cuộc đua diễn ra an toàn, kết quả đã được công bố chính thức.' WHERE race_id='55555555-0000-4000-8000-000000000001';
UPDATE race SET status='OFFICIAL', certified_by_user_id='11111111-0000-4000-8000-000000000001', certified_at = NOW() - INTERVAL '195 days', stewards_report='Cuộc đua diễn ra an toàn, kết quả đã được công bố chính thức.' WHERE race_id='55555555-0000-4000-8000-000000000011';

-- Kết quả được duyệt: officiality_status=OFFICIAL + approved_by + published_at.
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '195 days' WHERE result_id='88888888-0000-4000-8000-000000000001';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '195 days' WHERE result_id='88888888-0000-4000-8000-000000000002';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '195 days' WHERE result_id='88888888-0000-4000-8000-000000000003';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '195 days' WHERE result_id='88888888-0000-4000-8000-000000000004';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '195 days' WHERE result_id='88888888-0000-4000-8000-000000000005';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '195 days' WHERE result_id='88888888-0000-4000-8000-000000000006';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '84 days' WHERE result_id='88888888-0000-4000-8000-000000000012';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '84 days' WHERE result_id='88888888-0000-4000-8000-000000000013';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '84 days' WHERE result_id='88888888-0000-4000-8000-000000000014';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '84 days' WHERE result_id='88888888-0000-4000-8000-000000000015';
UPDATE race_result SET officiality_status='OFFICIAL', approved_by_user_id='11111111-0000-4000-8000-000000000001', published_at = NOW() - INTERVAL '84 days' WHERE result_id='88888888-0000-4000-8000-000000000016';

-- 11 bản version tương ứng cũng chuyển OFFICIAL (certify không tạo version mới).
UPDATE race_result_version SET officiality_status='OFFICIAL' WHERE result_id IN (SELECT result_id FROM race_result WHERE officiality_status='OFFICIAL');

-- prize_earned do creditPrizes() ghi (tổng giải, gồm cả phần chia cho nài).
UPDATE race_entry SET prize_earned = 200000000.00 WHERE entry_id='77777777-0000-4000-8000-000000000001';
UPDATE race_entry SET prize_earned = 120000000.00 WHERE entry_id='77777777-0000-4000-8000-000000000002';
UPDATE race_entry SET prize_earned = 80000000.00 WHERE entry_id='77777777-0000-4000-8000-000000000003';
UPDATE race_entry SET prize_earned = 200000000.00 WHERE entry_id='77777777-0000-4000-8000-000000000012';
UPDATE race_entry SET prize_earned = 120000000.00 WHERE entry_id='77777777-0000-4000-8000-000000000013';
UPDATE race_entry SET prize_earned = 80000000.00 WHERE entry_id='77777777-0000-4000-8000-000000000014';

-- 26 vé đã được settle: WON nếu về đúng nhóm thắng của loại cược, còn lại LOST.
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000001';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000002';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000003';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000004';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000005';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000006';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000007';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000008';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000009';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000010';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000011';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000012';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '195 days' WHERE prediction_id='99999999-0000-4000-8000-000000000013';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000027';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000028';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000029';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000030';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000031';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000032';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000033';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000034';
UPDATE prediction SET status='WON', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000035';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000036';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000037';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000038';
UPDATE prediction SET status='LOST', settled_at = NOW() - INTERVAL '84 days' WHERE prediction_id='99999999-0000-4000-8000-000000000039';

-- 6 pool đã settle (markSettledIfNotSettled đảm bảo settle đúng 1 lần).
UPDATE betting_pool SET status='SETTLED' WHERE pool_id IN ('94444444-0000-4000-8000-000000000001','94444444-0000-4000-8000-000000000002','94444444-0000-4000-8000-000000000003','94444444-0000-4000-8000-000000000007','94444444-0000-4000-8000-000000000008','94444444-0000-4000-8000-000000000009');

-- 36 dòng sổ cái do settle sinh ra: BET_PAYOUT 2 vế (house DEBIT rồi người thắng CREDIT)
-- và PRIZE 1 vế (creditPrizes mint thẳng vào ví chủ ngựa/nài, không có vế đối ứng — đúng như code).
INSERT INTO wallet_transaction (wallet_txn_id, wallet_id, entry_type, txn_category, amount, balance_after,
                                related_entity_type, related_entity_id, payment_txn_id, created_at) VALUES
('1429e248-ff40-4d71-85e6-733688dd3f44','aaaaaaaa-0000-4000-8000-000000000011','CREDIT','PRIZE',180000000.00,185700000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000012', NULL, NOW() - INTERVAL '84 days'),
('65ba1a09-2b15-4b3f-ba85-9a0f818c2d06','aaaaaaaa-0000-4000-8000-000000000021','CREDIT','PRIZE',20000000.00,32800000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000012', NULL, NOW() - INTERVAL '84 days'),
('6c0bce91-9def-4a08-bf3b-f9b091e904f9','aaaaaaaa-0000-4000-8000-000000000011','CREDIT','PRIZE',109800000.00,295500000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000013', NULL, NOW() - INTERVAL '84 days'),
('4f9a3825-ddae-48f9-b69e-429d7d96c948','aaaaaaaa-0000-4000-8000-000000000022','CREDIT','PRIZE',10200000.00,17500000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000013', NULL, NOW() - INTERVAL '84 days'),
('8c4c0944-7b7a-460f-99a0-a89b8c13c938','aaaaaaaa-0000-4000-8000-000000000011','CREDIT','PRIZE',74400000.00,369900000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000014', NULL, NOW() - INTERVAL '84 days'),
('6bcec3fc-35cd-4a4b-a068-cf11bb1e75a2','aaaaaaaa-0000-4000-8000-000000000023','CREDIT','PRIZE',5600000.00,8700000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000014', NULL, NOW() - INTERVAL '84 days'),
('3c78f438-e313-4e2f-aeb9-7d897392ad56',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',290000.00,110260000.00,'PREDICTION','99999999-0000-4000-8000-000000000030', NULL, NOW() - INTERVAL '84 days'),
('ce7b6a50-d0e0-40f0-bb79-ec37e509b872','aaaaaaaa-0000-4000-8000-000000000045','CREDIT','BET_PAYOUT',290000.00,9390000.00,'PREDICTION','99999999-0000-4000-8000-000000000030', NULL, NOW() - INTERVAL '84 days'),
('2c70fdad-4ce9-483d-ae59-6b82d8607573',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',969000.00,109291000.00,'PREDICTION','99999999-0000-4000-8000-000000000027', NULL, NOW() - INTERVAL '84 days'),
('cd7eb1e7-ebbf-4613-96cf-f31b7dde1f50','aaaaaaaa-0000-4000-8000-000000000041','CREDIT','BET_PAYOUT',969000.00,5969000.00,'PREDICTION','99999999-0000-4000-8000-000000000027', NULL, NOW() - INTERVAL '84 days'),
('61e57648-f8b8-483b-93ef-2682fb835ab6',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',455000.00,108836000.00,'PREDICTION','99999999-0000-4000-8000-000000000031', NULL, NOW() - INTERVAL '84 days'),
('af97ffe6-c918-4ad7-9e3e-bb0f6ff3a169','aaaaaaaa-0000-4000-8000-000000000041','CREDIT','BET_PAYOUT',455000.00,6424000.00,'PREDICTION','99999999-0000-4000-8000-000000000031', NULL, NOW() - INTERVAL '84 days'),
('b603a157-d753-4e7a-8f8f-d9c158300473',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',355000.00,108481000.00,'PREDICTION','99999999-0000-4000-8000-000000000032', NULL, NOW() - INTERVAL '84 days'),
('19b251e1-59c1-4e18-8609-44fb157803f0','aaaaaaaa-0000-4000-8000-000000000042','CREDIT','BET_PAYOUT',355000.00,6455000.00,'PREDICTION','99999999-0000-4000-8000-000000000032', NULL, NOW() - INTERVAL '84 days'),
('30dcd38c-6d93-4fd2-9489-c451b3157ee0',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',340000.00,108141000.00,'PREDICTION','99999999-0000-4000-8000-000000000034', NULL, NOW() - INTERVAL '84 days'),
('8053537f-3b54-487f-82da-d1715e48173b','aaaaaaaa-0000-4000-8000-000000000041','CREDIT','BET_PAYOUT',340000.00,6764000.00,'PREDICTION','99999999-0000-4000-8000-000000000034', NULL, NOW() - INTERVAL '84 days'),
('1ebca6bc-4093-45a1-9a7b-a1f75687cbc2',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',290000.00,107851000.00,'PREDICTION','99999999-0000-4000-8000-000000000035', NULL, NOW() - INTERVAL '84 days'),
('61e388dd-a622-4375-bbff-5d52ec11cefb','aaaaaaaa-0000-4000-8000-000000000042','CREDIT','BET_PAYOUT',290000.00,6745000.00,'PREDICTION','99999999-0000-4000-8000-000000000035', NULL, NOW() - INTERVAL '84 days'),
('8cd14c92-a9df-4d09-81d9-e4d0b25de44d','aaaaaaaa-0000-4000-8000-000000000011','CREDIT','PRIZE',180000000.00,549900000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000001', NULL, NOW() - INTERVAL '84 days'),
('aeb73201-e5fd-4210-96e1-fb49c62d94a7','aaaaaaaa-0000-4000-8000-000000000021','CREDIT','PRIZE',20000000.00,52800000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000001', NULL, NOW() - INTERVAL '84 days'),
('70b88fb8-b68b-4d73-8d14-98ef5d975999','aaaaaaaa-0000-4000-8000-000000000011','CREDIT','PRIZE',109800000.00,659700000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000002', NULL, NOW() - INTERVAL '84 days'),
('877a41d6-76ec-44e4-b5c3-51b2816775a0','aaaaaaaa-0000-4000-8000-000000000022','CREDIT','PRIZE',10200000.00,27700000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000002', NULL, NOW() - INTERVAL '84 days'),
('7c156767-00be-43d5-95bf-a93f80bf17c9','aaaaaaaa-0000-4000-8000-000000000011','CREDIT','PRIZE',74400000.00,734100000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000003', NULL, NOW() - INTERVAL '84 days'),
('b223f4ca-327c-4f48-8fec-971997344b1d','aaaaaaaa-0000-4000-8000-000000000023','CREDIT','PRIZE',5600000.00,14300000.00,'RACE_ENTRY','77777777-0000-4000-8000-000000000003', NULL, NOW() - INTERVAL '84 days'),
('aea23dce-91a4-4040-98b6-aaa504bddba7',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',290000.00,107561000.00,'PREDICTION','99999999-0000-4000-8000-000000000004', NULL, NOW() - INTERVAL '195 days'),
('1a3d4710-6a62-4c3c-a40a-2ff0c17d526b','aaaaaaaa-0000-4000-8000-000000000045','CREDIT','BET_PAYOUT',290000.00,9680000.00,'PREDICTION','99999999-0000-4000-8000-000000000004', NULL, NOW() - INTERVAL '195 days'),
('75123230-9146-4554-bfb1-4cc585bbf238',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',969000.00,106592000.00,'PREDICTION','99999999-0000-4000-8000-000000000001', NULL, NOW() - INTERVAL '195 days'),
('e1444204-c284-447e-b905-ecae9e2f5f74','aaaaaaaa-0000-4000-8000-000000000041','CREDIT','BET_PAYOUT',969000.00,7733000.00,'PREDICTION','99999999-0000-4000-8000-000000000001', NULL, NOW() - INTERVAL '195 days'),
('9baa3868-3444-4be6-9423-4ff73acd9771',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',455000.00,106137000.00,'PREDICTION','99999999-0000-4000-8000-000000000005', NULL, NOW() - INTERVAL '195 days'),
('177f1e8e-9ae3-40f7-a229-617778f7b61a','aaaaaaaa-0000-4000-8000-000000000041','CREDIT','BET_PAYOUT',455000.00,8188000.00,'PREDICTION','99999999-0000-4000-8000-000000000005', NULL, NOW() - INTERVAL '195 days'),
('c370e14a-e14e-47ab-a0f1-68665bc09571',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',355000.00,105782000.00,'PREDICTION','99999999-0000-4000-8000-000000000006', NULL, NOW() - INTERVAL '195 days'),
('c4fe7b2f-fe9f-4261-b1b8-d3036ac7e38a','aaaaaaaa-0000-4000-8000-000000000042','CREDIT','BET_PAYOUT',355000.00,7100000.00,'PREDICTION','99999999-0000-4000-8000-000000000006', NULL, NOW() - INTERVAL '195 days'),
('4b40b63b-d38e-4169-9741-9e9b91afc467',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',340000.00,105442000.00,'PREDICTION','99999999-0000-4000-8000-000000000008', NULL, NOW() - INTERVAL '195 days'),
('a8417358-792f-4dbe-848b-7f2f2a2021aa','aaaaaaaa-0000-4000-8000-000000000041','CREDIT','BET_PAYOUT',340000.00,8528000.00,'PREDICTION','99999999-0000-4000-8000-000000000008', NULL, NOW() - INTERVAL '195 days'),
('2f919182-e064-4a4c-997c-e109faf0e605',(SELECT wallet_id FROM wallet WHERE user_id = (SELECT user_id FROM app_user WHERE email='admin@horserace.local')),'DEBIT','BET_PAYOUT',290000.00,105152000.00,'PREDICTION','99999999-0000-4000-8000-000000000009', NULL, NOW() - INTERVAL '195 days'),
('05244a90-57d6-4dd4-8bfe-a1d7c92ec88a','aaaaaaaa-0000-4000-8000-000000000042','CREDIT','BET_PAYOUT',290000.00,7390000.00,'PREDICTION','99999999-0000-4000-8000-000000000009', NULL, NOW() - INTERVAL '195 days');

-- 12 prize. prize_code có dạng PRZ-<O|J>-<entry_id> vì đó là format generator hiện tại
-- sinh ra (RaceResultServiceImpl). Xấu nhưng đây là giá trị thật — đã ghi lại thành nợ kỹ thuật.
INSERT INTO prize (prize_id, tournament_id, race_id, prize_code, beneficiary_type, rank_position,
                   prize_amount, currency_code, status) VALUES
('626585aa-9852-4ad0-b93d-b45405e24787', NULL,'55555555-0000-4000-8000-000000000011','PRZ-J-77777777-0000-4000-8000-000000000001','JOCKEY',1,20000000.00,'VND','AWARDED'),
('f0f75181-fd80-4496-bc71-6840fc411726', NULL,'55555555-0000-4000-8000-000000000011','PRZ-J-77777777-0000-4000-8000-000000000002','JOCKEY',2,10200000.00,'VND','AWARDED'),
('61b481eb-85d6-4f8e-9414-5e496dc1b3b0', NULL,'55555555-0000-4000-8000-000000000011','PRZ-J-77777777-0000-4000-8000-000000000003','JOCKEY',3,5600000.00,'VND','AWARDED'),
('30657013-e1ac-4724-9807-02958a223e08', NULL,'55555555-0000-4000-8000-000000000001','PRZ-J-77777777-0000-4000-8000-000000000012','JOCKEY',1,20000000.00,'VND','AWARDED'),
('5921dbee-72c5-4504-99cc-41cb3da470ff', NULL,'55555555-0000-4000-8000-000000000001','PRZ-J-77777777-0000-4000-8000-000000000013','JOCKEY',2,10200000.00,'VND','AWARDED'),
('d4c5ee88-6612-4e87-845a-f4eb8772f3aa', NULL,'55555555-0000-4000-8000-000000000001','PRZ-J-77777777-0000-4000-8000-000000000014','JOCKEY',3,5600000.00,'VND','AWARDED'),
('6d795880-564b-4ea5-bcee-ffd6a29d9a4d', NULL,'55555555-0000-4000-8000-000000000011','PRZ-O-77777777-0000-4000-8000-000000000001','OWNER',1,180000000.00,'VND','AWARDED'),
('e31a74ee-2515-49fa-b5af-56f625a29b51', NULL,'55555555-0000-4000-8000-000000000011','PRZ-O-77777777-0000-4000-8000-000000000002','OWNER',2,109800000.00,'VND','AWARDED'),
('be69a1d5-ffb7-433c-be62-a0fc29124ad7', NULL,'55555555-0000-4000-8000-000000000011','PRZ-O-77777777-0000-4000-8000-000000000003','OWNER',3,74400000.00,'VND','AWARDED'),
('2a21e07f-497f-4d93-b065-10458e5fa36e', NULL,'55555555-0000-4000-8000-000000000001','PRZ-O-77777777-0000-4000-8000-000000000012','OWNER',1,180000000.00,'VND','AWARDED'),
('ce483232-5626-449e-b0c2-3a8fc1489321', NULL,'55555555-0000-4000-8000-000000000001','PRZ-O-77777777-0000-4000-8000-000000000013','OWNER',2,109800000.00,'VND','AWARDED'),
('daff4a22-821c-47a3-8e93-a1bcac671e17', NULL,'55555555-0000-4000-8000-000000000001','PRZ-O-77777777-0000-4000-8000-000000000014','OWNER',3,74400000.00,'VND','AWARDED');

-- 12 payout. settled_by NULL vì settle chạy tự động (không do người bấm).
INSERT INTO payout (payout_id, prediction_id, payout_amount, wallet_txn_id, status, settled_by_user_id, settled_at) VALUES
('0d96dd1d-b647-434c-8f22-b0f5e0aadd83','99999999-0000-4000-8000-000000000032',355000.00,'19b251e1-59c1-4e18-8609-44fb157803f0','PAID', NULL, NOW() - INTERVAL '84 days'),
('2fd0a82e-be61-4915-bfbc-6d8f41e897a1','99999999-0000-4000-8000-000000000006',355000.00,'c4fe7b2f-fe9f-4261-b1b8-d3036ac7e38a','PAID', NULL, NOW() - INTERVAL '195 days'),
('2ff25e30-8cc4-4997-8b68-6c2247c8c1ff','99999999-0000-4000-8000-000000000001',969000.00,'e1444204-c284-447e-b905-ecae9e2f5f74','PAID', NULL, NOW() - INTERVAL '195 days'),
('3e0f18b5-703c-4dd1-88d4-0ea78c2844d2','99999999-0000-4000-8000-000000000035',290000.00,'61e388dd-a622-4375-bbff-5d52ec11cefb','PAID', NULL, NOW() - INTERVAL '84 days'),
('4b2d30de-f729-4350-8a23-ca516da5b41c','99999999-0000-4000-8000-000000000004',290000.00,'1a3d4710-6a62-4c3c-a40a-2ff0c17d526b','PAID', NULL, NOW() - INTERVAL '195 days'),
('65c4db69-ed68-44ba-8b5e-a2adad2501ed','99999999-0000-4000-8000-000000000030',290000.00,'ce7b6a50-d0e0-40f0-bb79-ec37e509b872','PAID', NULL, NOW() - INTERVAL '84 days'),
('6fd286bd-6b44-4ef0-9e93-1715160eb268','99999999-0000-4000-8000-000000000031',455000.00,'af97ffe6-c918-4ad7-9e3e-bb0f6ff3a169','PAID', NULL, NOW() - INTERVAL '84 days'),
('85bf8f19-88e0-4c20-a3c4-e46b784293c5','99999999-0000-4000-8000-000000000034',340000.00,'8053537f-3b54-487f-82da-d1715e48173b','PAID', NULL, NOW() - INTERVAL '84 days'),
('95e13136-f966-4df3-966a-3ae4f3b1dc6d','99999999-0000-4000-8000-000000000005',455000.00,'177f1e8e-9ae3-40f7-a229-617778f7b61a','PAID', NULL, NOW() - INTERVAL '195 days'),
('ae11d450-7449-4c53-affe-e1981d9a3afd','99999999-0000-4000-8000-000000000008',340000.00,'a8417358-792f-4dbe-848b-7f2f2a2021aa','PAID', NULL, NOW() - INTERVAL '195 days'),
('c15bdf6b-47bf-4f5b-8a41-57108eb58dd8','99999999-0000-4000-8000-000000000009',290000.00,'05244a90-57d6-4dd4-8bfe-a1d7c92ec88a','PAID', NULL, NOW() - INTERVAL '195 days'),
('ce8c10a9-1210-4355-9e6f-7bcb1b3f9b4a','99999999-0000-4000-8000-000000000027',969000.00,'cd7eb1e7-ebbf-4613-96cf-f31b7dde1f50','PAID', NULL, NOW() - INTERVAL '84 days');

-- =========================================================
-- TIỀN TÀI TRỢ — nguồn của giải thưởng
-- =========================================================
-- Giải thưởng KHÔNG lấy từ phí tham dự hay tiền cược: phí thu về khoảng 105 triệu trong khi tiền
-- thưởng đã chi là 950 triệu. Purse là tiền ban tổ chức tài trợ, nạp vào ví nhà cái khi giải được
-- công bố (publishTournament), rồi trừ dần mỗi lần trọng tài công nhận kết quả.
--
-- Giải còn DRAFT thì KHÔNG nạp ở đây — publishTournament sẽ nạp khi admin công bố.
--
-- Ngày phải SỚM NHẤT: khối tính lại số dư bên dưới chạy theo created_at, mà wallet_transaction có
-- CHECK (balance_after >= 0) — tiền tài trợ về sau các khoản chi thưởng sẽ làm chuỗi số dư âm và
-- huỷ cả lần seed.
INSERT INTO wallet_transaction (wallet_id, entry_type, txn_category, amount, balance_after,
                                related_entity_type, related_entity_id, created_at)
SELECT w.wallet_id, 'CREDIT', 'SPONSOR', t.total_purse, 0,
       'TOURNAMENT_PUBLISH', t.tournament_id, NOW() - INTERVAL '400 days'
  FROM tournament t
 CROSS JOIN (SELECT w2.wallet_id FROM wallet w2
              JOIN app_user u ON u.user_id = w2.user_id
             WHERE u.email = 'admin@horserace.local') w
 WHERE t.status <> 'DRAFT' AND t.is_deleted = false AND t.total_purse > 0;

-- Mỗi khoản thưởng đã chi ở trên phải có một khoản trừ ví nhà cái đối ứng, cùng thời điểm.
INSERT INTO wallet_transaction (wallet_id, entry_type, txn_category, amount, balance_after,
                                related_entity_type, related_entity_id, created_at)
SELECT house.wallet_id, 'DEBIT', 'PRIZE', src.amount, 0,
       src.related_entity_type, src.related_entity_id, src.created_at
  FROM wallet_transaction src
 CROSS JOIN (SELECT w.wallet_id FROM wallet w
              JOIN app_user u ON u.user_id = w.user_id
             WHERE u.email = 'admin@horserace.local') house
 WHERE src.txn_category = 'PRIZE' AND src.entry_type = 'CREDIT'
   AND src.wallet_id <> house.wallet_id;

-- =========================================================
-- CHỐT SỔ CÁI — tính lại balance_after theo đúng thứ tự thời gian
-- =========================================================
-- WalletLedgerServiceImpl ghi balance_after = số dư của ví SAU mỗi lần chuyển. Các dòng ở trên được
-- chèn theo từng nhóm (nạp tiền → phí tham dự → tiền cược → tiền thắng), nhưng thứ tự thời gian
-- thật lại đan xen nhau, nên thay vì hardcode từng con số, ta dựng lại chuỗi số dư bằng một window
-- function. Không phát sinh hay mất đi đồng nào — chỉ đánh lại số dư luỹ kế cho đúng.
WITH ordered AS (
    SELECT wallet_txn_id,
           SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END)
               OVER (PARTITION BY wallet_id ORDER BY created_at, wallet_txn_id
                     ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
    FROM wallet_transaction
)
UPDATE wallet_transaction wt
   SET balance_after = o.running_balance
  FROM ordered o
 WHERE o.wallet_txn_id = wt.wallet_txn_id;

-- Số dư ví = balance_after của dòng cuối cùng. Ví không có giao dịch nào giữ nguyên số dư ban đầu.
UPDATE wallet w
   SET balance = t.balance_after
  FROM (SELECT DISTINCT ON (wallet_id) wallet_id, balance_after
          FROM wallet_transaction
         ORDER BY wallet_id, created_at DESC, wallet_txn_id DESC) t
 WHERE t.wallet_id = w.wallet_id;

-- =========================================================
-- DEMO-READINESS — chừa sẵn dữ liệu cho các luồng cần thao tác trực tiếp
-- =========================================================
-- RACE00005 là race đang OPEN duy nhất, nên nó phải gánh 2 luồng demo đối nghịch nhau:
--   * "Chủ ngựa thuê/chọn nài": cần entry CHƯA có nài -> 3 entry của horseowner1 bỏ trống.
--     (Trước đó mọi entry đều đã có nài, nên GET /owner/unassigned-entries trả về rỗng và
--      màn "Jockey Selection" không có gì để thao tác.)
--   * "Nài nhận/từ chối lời mời": cần lời mời đang INVITED -> giữ 3 entry của horseowner2,
--     mời jockey1..3 để chính các tài khoản nài đó có việc để duyệt.
DELETE FROM jockey_assignment
 WHERE entry_id IN (SELECT entry_id FROM race_entry WHERE entry_code IN ('ENT00037','ENT00038','ENT00039'));

UPDATE jockey_assignment SET jockey_user_id = '11111111-0000-4000-8000-000000000021'
 WHERE entry_id = (SELECT entry_id FROM race_entry WHERE entry_code = 'ENT00040');
UPDATE jockey_assignment SET jockey_user_id = '11111111-0000-4000-8000-000000000022'
 WHERE entry_id = (SELECT entry_id FROM race_entry WHERE entry_code = 'ENT00041');
UPDATE jockey_assignment SET jockey_user_id = '11111111-0000-4000-8000-000000000023'
 WHERE entry_id = (SELECT entry_id FROM race_entry WHERE entry_code = 'ENT00042');

-- Một lời mời đã bị TỪ CHỐI để màn hình của chủ ngựa có đủ cả 3 trạng thái
-- (INVITED / ACCEPTED / DECLINED), không chỉ toàn ca thuận lợi.
UPDATE jockey_assignment
   SET status = 'DECLINED', responded_at = NOW() - INTERVAL '2 days'
 WHERE entry_id = (SELECT entry_id FROM race_entry WHERE entry_code = 'ENT00036');

-- Luồng "Trọng tài kiểm tra thông tin ngựa trước cuộc đua" (Req 15) cần hàng đợi đăng ký đang chờ
-- duyệt TRÊN RACE MÀ CHÍNH TRỌNG TÀI ĐÓ ĐƯỢC PHÂN CÔNG. Trước đây referee1 chỉ được phân vào race
-- đã FINISHED/OFFICIAL/CLOSED, mà 42/44 đăng ký đều đã APPROVED -> hàng đợi trống trơn.
-- Phân referee1 làm CHIEF cho RACE00008 (đang OPEN, thuộc giải đang mở đăng ký) rồi thêm 4 đăng ký
-- SUBMITTED để trọng tài có việc thật để duyệt/từ chối khi demo.
INSERT INTO referee_assignment (ref_assignment_id, race_id, referee_user_id, panel_role, ref_code,
                                status, assigned_at, responded_at, decline_reason, created_by_user_id) VALUES
('74444444-0000-4000-8000-000000000021',(SELECT race_id FROM race WHERE race_code='RACE00008'),
 '11111111-0000-4000-8000-000000000031','CHIEF','REF-C3A8E2','CONFIRMED',
 NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days', NULL,'11111111-0000-4000-8000-000000000001');

INSERT INTO tournament_registration (registration_id, owner_user_id, tournament_id, horse_id, race_id,
                                     registration_code, status, submitted_at, reviewed_at,
                                     approved_by_user_id, rejection_reason, legal_basis_ref, category) VALUES
('66666666-0000-4000-8000-000000000045','11111111-0000-4000-8000-000000000011',
 (SELECT tournament_id FROM tournament WHERE tournament_code='TRN00004'),
 (SELECT horse_id FROM horse WHERE horse_code='HRS0011'),
 (SELECT race_id FROM race WHERE race_code='RACE00008'),
 'REG00045','SUBMITTED', NOW() - INTERVAL '2 days', NULL, NULL, NULL,'QĐ-2026/04-ĐUA','GROUP_3'),
('66666666-0000-4000-8000-000000000046','11111111-0000-4000-8000-000000000012',
 (SELECT tournament_id FROM tournament WHERE tournament_code='TRN00004'),
 (SELECT horse_id FROM horse WHERE horse_code='HRS0014'),
 (SELECT race_id FROM race WHERE race_code='RACE00008'),
 'REG00046','SUBMITTED', NOW() - INTERVAL '2 days', NULL, NULL, NULL,'QĐ-2026/04-ĐUA','GROUP_3'),
('66666666-0000-4000-8000-000000000047','11111111-0000-4000-8000-000000000013',
 (SELECT tournament_id FROM tournament WHERE tournament_code='TRN00004'),
 (SELECT horse_id FROM horse WHERE horse_code='HRS0017'),
 (SELECT race_id FROM race WHERE race_code='RACE00008'),
 'REG00047','SUBMITTED', NOW() - INTERVAL '1 day', NULL, NULL, NULL,'QĐ-2026/04-ĐUA','GROUP_3'),
('66666666-0000-4000-8000-000000000048','11111111-0000-4000-8000-000000000014',
 (SELECT tournament_id FROM tournament WHERE tournament_code='TRN00004'),
 (SELECT horse_id FROM horse WHERE horse_code='HRS0020'),
 (SELECT race_id FROM race WHERE race_code='RACE00008'),
 'REG00048','UNDER_REVIEW', NOW() - INTERVAL '1 day', NULL, NULL, NULL,'QĐ-2026/04-ĐUA','GROUP_3');

-- Dossier do CHÍNH chủ ngựa upload — nếu thiếu, approveRegistration sẽ ném
-- REGISTRATION_DOCUMENT_REQUIRED ngay giữa buổi demo.
INSERT INTO attachment (attachment_id, owner_entity_type, owner_entity_id, object_key, file_name,
                        mime_type, file_size, sensitivity_level, uploaded_by_user_id) VALUES
('a3333333-0000-4000-8000-000000000051','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000045','attachments/seed-dossier-045.pdf','ho-so-REG00045.pdf','application/pdf',131072,'INTERNAL','11111111-0000-4000-8000-000000000011'),
('a3333333-0000-4000-8000-000000000052','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000046','attachments/seed-dossier-046.pdf','ho-so-REG00046.pdf','application/pdf',128000,'INTERNAL','11111111-0000-4000-8000-000000000012'),
('a3333333-0000-4000-8000-000000000053','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000047','attachments/seed-dossier-047.pdf','ho-so-REG00047.pdf','application/pdf',126976,'INTERNAL','11111111-0000-4000-8000-000000000013'),
('a3333333-0000-4000-8000-000000000054','TOURNAMENT_REGISTRATION','66666666-0000-4000-8000-000000000048','attachments/seed-dossier-048.pdf','ho-so-REG00048.pdf','application/pdf',124928,'INTERNAL','11111111-0000-4000-8000-000000000014');

-- Live Monitor lấy danh sách race của chính trọng tài rồi sort theo scheduled_start_at GIẢM DẦN,
-- nên nó chọn race có ngày xa nhất — tức RACE00008 (còn 25 ngày, 0 runner) — thay vì race ĐANG chạy.
-- Phân referee1 vào cả RACE00007 (RUNNING, 5 runner) để màn theo dõi trực tiếp có dữ liệu thật.
INSERT INTO referee_assignment (ref_assignment_id, race_id, referee_user_id, panel_role, ref_code,
                                status, assigned_at, responded_at, decline_reason, created_by_user_id) VALUES
('74444444-0000-4000-8000-000000000022',(SELECT race_id FROM race WHERE race_code='RACE00007'),
 '11111111-0000-4000-8000-000000000031','CHIEF','REF-B1D7F3','CONFIRMED',
 NOW() - INTERVAL '6 days', NOW() - INTERVAL '5 days', NULL,'11111111-0000-4000-8000-000000000001');

-- =========================================================
-- PHÍ THAM GIA — đánh dấu đăng ký nào đã trả
-- =========================================================
-- Phí giờ được trừ lúc chủ ngựa NỘP ĐƠN (trước đây là lúc trọng tài duyệt). Hai mốc thời gian dưới
-- đây là cơ chế chốt chống trừ/hoàn hai lần. Không đánh dấu thì:
--   - xoá đăng ký sẽ TỪ CHỐI hoàn khoản tiền đã thu thật, và
--   - gán lại vào cuộc đua sẽ thu phí LẦN THỨ HAI.
-- Suy từ sổ cái (nguồn xác thực), khớp qua race_entry vì các dòng cũ gắn theo race_id.
--
-- PHẢI nằm CUỐI FILE: khối DEMO-READINESS bên trên còn chèn thêm đăng ký và suất đua, nên đặt câu
-- lệnh này ở giữa file sẽ bỏ sót đúng những dòng được thêm sau đó.
UPDATE tournament_registration r
   SET entry_fee_paid_at = src.created_at,
       entry_fee_amount  = src.amount
  FROM (
    SELECT re.registration_id, wt.created_at, wt.amount,
           ROW_NUMBER() OVER (PARTITION BY re.registration_id ORDER BY wt.created_at) AS rn
      FROM race_entry re
      JOIN tournament_registration r2 ON r2.registration_id = re.registration_id
      JOIN wallet w  ON w.user_id  = r2.owner_user_id
      JOIN wallet_transaction wt
        ON wt.wallet_id           = w.wallet_id
       AND wt.txn_category        = 'ENTRY_FEE'
       AND wt.entry_type          = 'DEBIT'
       AND wt.related_entity_type = 'RACE'
       AND wt.related_entity_id   = re.race_id
  ) src
 WHERE src.registration_id = r.registration_id
   AND src.rn = 1
   AND r.entry_fee_paid_at IS NULL;
