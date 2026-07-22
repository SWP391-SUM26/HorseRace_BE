-- 2026-07-20 — Seed extra race disciplines so the race-form dropdowns (which read DISTINCT values
-- straight from the race table) offer more than just FLAT. Adds 5 SCHEDULED races under two
-- upcoming (PUBLISHED) tournaments, introducing race types JUMP / HARNESS / ENDURANCE / HURDLE /
-- STEEPLECHASE (aligned with the jockey discipline vocabulary), plus a couple of new track
-- (YIELDING) and weather (OVERCAST) values. race_type/track_condition/weather_condition are free
-- VARCHARs with no business logic keyed off them, so these are safe descriptive additions.
--
-- Idempotent: ON CONFLICT (race_id) DO NOTHING — safe to re-run. Attached only to non-completed,
-- non-cancelled tournaments so no advancement/lifecycle invariant is disturbed.
--
-- Apply to a live DB (persist mode) by hand:
--   docker exec -i horserace_postgres psql -U postgres -d horserace_db \
--     < src/main/resources/db/patches/2026-07-20-extra-race-types.sql
-- Fresh volumes get the same rows via seed_demo.sql.

INSERT INTO race (race_id, tournament_id, race_code, name, race_type, distance_meter,
                  track_condition, weather_condition, scheduled_start_at, prediction_cutoff_at,
                  max_participants, min_participants, venue, venue_id, going_moisture_pct,
                  total_purse, status) VALUES
 ('55555555-0000-4000-8000-000000000101','44444444-0000-4000-8000-000000000010','RACE00101',
  'Siêu Cúp - Vượt rào','JUMP',3200,'GOOD','OVERCAST',
  NOW() + INTERVAL '121 days', NOW() + INTERVAL '121 days' - INTERVAL '15 minutes',
  14, 6,'Trung tâm đua quốc gia','33333333-0000-4000-8000-000000000010', 26, 500000000,'SCHEDULED'),
 ('55555555-0000-4000-8000-000000000102','44444444-0000-4000-8000-000000000010','RACE00102',
  'Siêu Cúp - Đua xe kéo','HARNESS',2000,'YIELDING','CLOUDY',
  NOW() + INTERVAL '122 days', NOW() + INTERVAL '122 days' - INTERVAL '15 minutes',
  12, 5,'Trường đua Đại Nam','33333333-0000-4000-8000-000000000002', 48, 300000000,'SCHEDULED'),
 ('55555555-0000-4000-8000-000000000103','44444444-0000-4000-8000-000000000005','RACE00103',
  'Sông Hồng - Đường trường','ENDURANCE',6000,'FIRM','SUNNY',
  NOW() + INTERVAL '56 days', NOW() + INTERVAL '56 days' - INTERVAL '15 minutes',
  16, 8,'Trường đua Lâm Viên','33333333-0000-4000-8000-000000000005', 20, 350000000,'SCHEDULED'),
 ('55555555-0000-4000-8000-000000000104','44444444-0000-4000-8000-000000000005','RACE00104',
  'Sông Hồng - Vượt chướng ngại','HURDLE',2800,'SOFT','RAINY',
  NOW() + INTERVAL '57 days', NOW() + INTERVAL '57 days' - INTERVAL '15 minutes',
  12, 5,'Trường đua Sóc Sơn','33333333-0000-4000-8000-000000000003', 65, 320000000,'SCHEDULED'),
 ('55555555-0000-4000-8000-000000000105','44444444-0000-4000-8000-000000000010','RACE00105',
  'Siêu Cúp - Vượt rào lớn','STEEPLECHASE',3600,'HEAVY','WINDY',
  NOW() + INTERVAL '123 days', NOW() + INTERVAL '123 days' - INTERVAL '15 minutes',
  14, 6,'Trường đua Bà Nà','33333333-0000-4000-8000-000000000006', 80, 420000000,'SCHEDULED')
ON CONFLICT (race_id) DO NOTHING;
