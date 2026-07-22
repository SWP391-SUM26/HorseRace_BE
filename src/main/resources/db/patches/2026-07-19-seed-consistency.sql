-- =========================================================
-- PATCH 2026-07-19 — nạp lại demo data đã sửa lên DB ĐANG CHẠY
-- =========================================================
-- VÌ SAO LÀ NẠP LẠI CHỨ KHÔNG PHẢI MIGRATION TỪNG DÒNG:
-- Bản seed cũ sai ở tầng cấu trúc, không phải vài ô dữ liệu lệch:
--   * 6 race_entry dùng chung registration của race khác (UNIQUE(tournament,horse) ép 1 ngựa chỉ
--     chạy 1 race/giải, nên không thể "vá" bằng cách thêm registration).
--   * Toàn bộ payout tính từ locked_odds cố định thay vì chia pool -> mọi pool đều bội chi.
--   * 14/16 prediction đặt sau cutoff của chính nó.
-- Viết migration lần theo từng dòng cho ~40 bảng phụ thuộc nhau sẽ rủi ro hơn nhiều so với việc
-- dựng lại nguyên bộ demo data từ seed đã được kiểm chứng.
--
-- AN TOÀN: script này CHỈ xoá demo data. Nó giữ nguyên:
--   * role / permission / role_permission  (bootstrap từ seed.sql)
--   * tài khoản admin@horserace.local và ví nhà cái của nó
-- Đã kiểm tra DB đang chạy: 0 dòng nghiệp vụ do người dùng tự tạo (chỉ có refresh_token của các
-- phiên đăng nhập). Nếu về sau DB có data thật, PHẢI backup và rà lại trước khi chạy.
--
-- CÁCH CHẠY (một transaction, tự rollback nếu có lỗi):
--   cd src/main/resources/db
--   cat patches/2026-07-19-seed-consistency.sql seed_demo.sql \
--     | docker exec -i horserace_postgres psql -U postgres -d horserace_db -1 -v ON_ERROR_STOP=1
--
-- Chạy lại nhiều lần đều cho ra cùng một kết quả (xoá sạch rồi seed lại).
-- Backup trước cho chắc:
--   docker exec horserace_postgres pg_dump -U postgres -d horserace_db > backup.sql
-- =========================================================

-- 1) Xoá toàn bộ demo data. CASCADE lo thứ tự FK giúp; role/permission không nằm trong danh sách
--    nên ma trận quyền được giữ nguyên.
TRUNCATE TABLE
    attachment, betting_pool, email_change_request, email_verification_token,
    entry_document_review, horse, horse_characteristic, horse_medical_record,
    jockey_assignment, jockey_profile, membership_application, notification,
    owner_profile, password_reset_token, payment_transaction, payout, penalty,
    prediction, prize, race, race_entry, race_entry_inspection, race_fraction,
    race_prize_distribution, race_result, race_result_version, race_violation,
    referee_assignment, referee_report, referee_submission_code, refresh_token,
    reward, tournament, tournament_referee_assignment, tournament_registration,
    tournament_venue, venue, wallet_transaction
CASCADE;

-- 2) Ví: giữ lại đúng ví nhà cái (mọi giao dịch 2 vế đều khoá ví này trước).
DELETE FROM wallet
 WHERE user_id <> (SELECT user_id FROM app_user WHERE email = 'admin@horserace.local');

-- 3) User: giữ lại đúng admin bootstrap. 28 user demo sẽ được seed_demo.sql tạo lại.
DELETE FROM app_user WHERE email <> 'admin@horserace.local';

-- 4) Ví nhà cái về 0 — seed_demo.sql sẽ dựng lại số dư qua sổ cái (phí tham dự + tiền cược thu
--    vào, tiền thắng chi ra), rồi khối "CHỐT SỔ CÁI" ở cuối file tính lại số dư luỹ kế.
UPDATE wallet SET balance = 0, locked_balance = 0
 WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'admin@horserace.local');

-- Sau file này, nối tiếp seed_demo.sql (xem lệnh ở phần CÁCH CHẠY bên trên).
