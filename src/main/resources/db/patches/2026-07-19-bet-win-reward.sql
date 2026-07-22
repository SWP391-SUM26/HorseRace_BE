-- =========================================================
-- PATCH 2026-07-19 — thêm RewardType.BET_WIN
-- =========================================================
-- Req 24 ("nhận thông báo thưởng dự đoán") trước đây không có nơi nào SINH ra Reward, nên
-- GET /rewards/notifications luôn rỗng. Giờ SettlementServiceImpl tạo một Reward loại BET_WIN mỗi
-- khi một vé cược thắng, nên CHECK constraint của cột reward_type phải chấp nhận giá trị mới.
--
-- An toàn: chỉ nới lỏng CHECK (thêm giá trị hợp lệ), không đụng dữ liệu. Chạy lại nhiều lần vô hại.
--
-- CÁCH CHẠY:
--   docker exec -i horserace_postgres psql -U postgres -d horserace_db -v ON_ERROR_STOP=1 \
--     < src/main/resources/db/patches/2026-07-19-bet-win-reward.sql
-- =========================================================

ALTER TABLE reward DROP CONSTRAINT IF EXISTS reward_reward_type_check;

ALTER TABLE reward ADD CONSTRAINT reward_reward_type_check
    CHECK (reward_type IN ('DAILY_LOGIN', 'MILESTONE', 'PROMOTION', 'REFERRAL', 'COMPENSATION', 'BET_WIN'));
