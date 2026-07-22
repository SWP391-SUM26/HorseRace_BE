-- 2026-07-20 — Enrich + clean the race-form reference data (race type / track / weather / venue).
-- The form dropdowns read DISTINCT values straight from the race table (conditions) and from the
-- venue table (venue). This patch:
--   1. Normalises case-duplicate free-text values created via the old text inputs
--      ('Good' -> 'GOOD', 'Clear'/'clear' -> 'CLEAR') so the dropdowns don't show near-duplicates.
--   2. Adds 3 SCHEDULED reference races that introduce standard track (STANDARD / FAST / SLOW) and
--      weather (FOGGY / CLEAR) values. race_type/track_condition/weather_condition are free VARCHARs
--      with no business logic keyed off them — purely descriptive.
--   3. Adds 5 more venues.
--
-- Idempotent: INSERTs use ON CONFLICT DO NOTHING; UPDATEs are naturally repeatable.
-- Apply to a live (persist-mode) DB:
--   docker exec -i horserace_postgres psql -U postgres -d horserace_db \
--     < src/main/resources/db/patches/2026-07-20-more-field-options.sql

-- 1) Normalise case-duplicate condition values.
UPDATE race SET track_condition   = 'GOOD'  WHERE track_condition   = 'Good';
UPDATE race SET weather_condition = 'CLEAR' WHERE weather_condition IN ('Clear', 'clear');

-- 2) Reference races carrying new track/weather values (descriptive only, SCHEDULED, upcoming).
INSERT INTO race (race_id, tournament_id, race_code, name, race_type, distance_meter,
                  track_condition, weather_condition, scheduled_start_at, prediction_cutoff_at,
                  max_participants, min_participants, venue, venue_id, going_moisture_pct,
                  total_purse, status) VALUES
 ('55555555-0000-4000-8000-000000000106','44444444-0000-4000-8000-000000000010','RACE00106',
  'Siêu Cúp - Đường tổng hợp','FLAT',1800,'STANDARD','FOGGY',
  NOW() + INTERVAL '124 days', NOW() + INTERVAL '124 days' - INTERVAL '15 minutes',
  14, 6,'Trung tâm đua quốc gia','33333333-0000-4000-8000-000000000010', 30, 260000000,'SCHEDULED'),
 ('55555555-0000-4000-8000-000000000107','44444444-0000-4000-8000-000000000010','RACE00107',
  'Siêu Cúp - Nước rút khô','HARNESS',1400,'FAST','CLEAR',
  NOW() + INTERVAL '125 days', NOW() + INTERVAL '125 days' - INTERVAL '15 minutes',
  12, 5,'Trường đua Đại Nam','33333333-0000-4000-8000-000000000002', 12, 240000000,'SCHEDULED'),
 ('55555555-0000-4000-8000-000000000108','44444444-0000-4000-8000-000000000005','RACE00108',
  'Sông Hồng - Sình lầy','ENDURANCE',5000,'SLOW','OVERCAST',
  NOW() + INTERVAL '58 days', NOW() + INTERVAL '58 days' - INTERVAL '15 minutes',
  16, 8,'Trường đua Lâm Viên','33333333-0000-4000-8000-000000000005', 72, 300000000,'SCHEDULED')
ON CONFLICT (race_id) DO NOTHING;

-- 3) More venues.
INSERT INTO venue (venue_id, name, track_name, city, country, capacity, surface) VALUES
 ('33333333-0000-4000-8000-000000000011','Trường đua Đà Lạt',  'Cao nguyên 2',      'Lâm Đồng',            'Việt Nam', 7000,'TURF'),
 ('33333333-0000-4000-8000-000000000012','Trường đua Nha Trang','Đường đua biển',    'Khánh Hòa',           'Việt Nam', 8500,'DIRT'),
 ('33333333-0000-4000-8000-000000000013','Trường đua Huế',     'Đường đua cố đô',   'Thừa Thiên Huế',      'Việt Nam', 6000,'TURF'),
 ('33333333-0000-4000-8000-000000000014','Trường đua Vũng Tàu','Đường đua ven biển','Bà Rịa - Vũng Tàu',   'Việt Nam', 9500,'SYNTHETIC'),
 ('33333333-0000-4000-8000-000000000015','Trường đua Sa Pa',   'Đường đua vùng cao','Lào Cai',             'Việt Nam', 4800,'TURF')
ON CONFLICT (venue_id) DO NOTHING;
