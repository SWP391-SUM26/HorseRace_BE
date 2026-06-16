# Horse Management — Completion (full Figma profile) — Design Spec

> **Date:** 2026-06-17 · **Branch:** `feat/horse-profile-complete` off `feat/horse-management`.
> **Source of truth:** Figma `Horse Profile Management` (node 1:709) + `schema_v4.sql`.
> **Approved decisions:** full Figma horse-profile scope · medical = records table + history ·
> Enter Race = 2-step (register → admin approve → enter race).

## 1. Goal & gap

The horse module today has CRUD + list/search/filter/sort/paginate + image. The Figma horse profile
also needs: **medical records/history**, **race history + lifetime stats**, **enter race**,
**pedigree**, **characteristics**, **transfer horse**. This spec completes all of them.

## 2. Schema changes (`schema_v4.sql`, additive; keep `ddl-auto=validate`)

**2.1 `horse` — add pedigree/trainer columns:**
```
sire_name       VARCHAR(255),
dam_name        VARCHAR(255),
trainer_name    VARCHAR(255),
trainer_license VARCHAR(100),
```

**2.2 New `horse_medical_record`:**
```
record_id          UUID PK DEFAULT gen_random_uuid(),
horse_id           UUID NOT NULL REFERENCES horse(horse_id),
record_type        VARCHAR(30) NOT NULL CHECK (record_type IN
                     ('VET_CHECK','VACCINATION','TREATMENT','INJURY','RECOVERY')),
record_date        DATE NOT NULL,
status             VARCHAR(50),          -- e.g. UP_TO_DATE / DUE / FIT / RECOVERING
recovery_percent   INT CHECK (recovery_percent IS NULL OR (recovery_percent BETWEEN 0 AND 100)),
notes              TEXT,
vet_name           VARCHAR(255),
created_by_user_id UUID REFERENCES app_user(user_id),
created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
-- index (horse_id, record_date DESC)
```

**2.3 New `horse_characteristic` (tag set):**
```
horse_id UUID NOT NULL REFERENCES horse(horse_id),
tag      VARCHAR(100) NOT NULL,
PRIMARY KEY (horse_id, tag)
```

Each schema change ships with the matching JPA entity in the same commit (validate must pass).

## 3. Entities

- **`Horse`** (extend): `+ sireName, damName, trainerName, trainerLicense`; `+ Set<String> characteristics`
  via `@ElementCollection @CollectionTable(name="horse_characteristic", joinColumns=@JoinColumn(name="horse_id")) @Column(name="tag")`.
- **`HorseMedicalRecord`** (new, `horses/entity`): enum `MedicalRecordType {VET_CHECK,VACCINATION,TREATMENT,INJURY,RECOVERY}`; `@ManyToOne Horse`.
- **`TournamentRegistration`** (exists): add repository + service.
- **`RaceEntry`** (exists): `RaceEntryRepository` exists; add service for entry creation.

## 4. Feature slices & endpoints

### 4.1 Pedigree + characteristics
Extend `HorseRequest`/`HorseResponse` with `sireName, damName, trainerName, trainerLicense, characteristics (Set<String>)`. Applied on create/update (characteristics replace-set).

### 4.2 Medical / health (records + history)
- `GET  /api/v1/horses/{id}/medical-records` — full history (newest first).
- `POST /api/v1/horses/{id}/medical-records` — add a record (owner or admin). Body: `recordType, recordDate, status?, recoveryPercent?, notes?, vetName?`.
- `HorseResponse.medicalSummary` derived from latest records: `lastVetCheckDate` (latest VET_CHECK), `vaccinationStatus` (latest VACCINATION status, else "UNKNOWN"), `recoveryStatus`/`recoveryPercent` (latest RECOVERY/INJURY).

### 4.3 Race history + lifetime stats (read)
- `GET /api/v1/horses/{id}/race-history` → rows {date, eventName (race + tournament), location, finishPosition, earnings, status} — join `tournament_registration → race_entry → race → tournament` left-join `race_result`, ordered by date desc.
- `HorseResponse.stats` (lifetime): `starts` (race_result count), `wins` (finish_position=1), `top3` (finish_position≤3), `totalEarnings` (Σ matched `prize`).
- **Earnings rule:** sum `prize.prize_amount` for the horse's official results where `prize.race_id` + `prize.rank_position` = the horse's `finish_position` and `beneficiary_type IN ('HORSE','OWNER')`. If no prize data → 0. (Read-only; no prize module needed.)

### 4.4 Enter Race — 2-step (register → admin approve → enter)
**Step 1 — register horse to tournament** (new `registrations` module):
- `POST   /api/v1/registrations` {tournamentId, horseId} → `tournament_registration` status `SUBMITTED` (owner of horse only; auto `registration_code`).
- `GET    /api/v1/registrations` filter {status, tournamentId, ownerUserId, horseId} + paging → admin queue / owner list.
- `GET    /api/v1/registrations/{id}`.
- `PATCH  /api/v1/registrations/{id}/approve` (admin) → `APPROVED`, set `approved_by_user_id, reviewed_at`.
- `PATCH  /api/v1/registrations/{id}/reject` {reason} (admin) → `REJECTED`, set `rejection_reason`.
- `PATCH  /api/v1/registrations/{id}/withdraw` (owner) → `WITHDRAWN`.

**Step 2 — enter an approved registration into a race** (race-entry):
- `POST /api/v1/races/{raceId}/entries` {registrationId, laneNo?, entryNo?} → `race_entry` status `ENTERED` (owner of the registration's horse, or admin). Auto `entry_code`.
  - Guards: registration `APPROVED`; `registration.tournament == race.tournament`; race status accepts entries (`SCHEDULED`/`OPEN`); lane/entry-no uniqueness (DB UNIQUE → 409 via existing `DataIntegrityViolation` handler).
- `GET /api/v1/races/{raceId}/entries` — list a race's entries.

### 4.5 Transfer horse
- `POST /api/v1/horses/{id}/transfer` {newOwnerUserId} (current owner or admin) → set `owner_user_id`. New owner must exist + be active.

## 5. Authorization
Owner-or-admin reused from `loadOwnedHorse`. Registration approve/reject = admin. Medical add = owner/admin. (Fine-grained RBAC stays Phase 0.2; current `permitAll` DEV — `@AuthenticationPrincipal` null → `UNAUTHENTICATED` 401, consistent with existing guards.)

## 6. New error codes
`REGISTRATION_NOT_FOUND, REGISTRATION_NOT_APPROVED, REGISTRATION_ALREADY_EXISTS (UNIQUE tournament,horse), RACE_NOT_OPEN, RACE_TOURNAMENT_MISMATCH, MEDICAL_RECORD_NOT_FOUND, NEW_OWNER_NOT_FOUND` (numbering 5006+ for horse / new 7xxx block for registrations to avoid clashes).

## 7. Build order (dependency-ordered slices)
1. Schema + entities (horse cols, medical_record, characteristic, registration repo, race-entry repo).
2. Horse extensions: pedigree + characteristics in DTOs.
3. Medical records (entity/repo/endpoints + summary).
4. Race history + lifetime stats (read).
5. Registrations module (register + approve/reject/withdraw + list).
6. Race entries (enter approved registration into a race).
7. Transfer horse.

## 8. Verification
`./mvnw compile` + boot (Hibernate validate against new tables) + unit tests for: medical summary derivation, stats computation, registration state transitions (approve/reject/withdraw guards), enter-race guards (not-approved / tournament-mismatch / race-not-open).

## 9. Out of scope
FE pages; results recording (referee) — only read of existing result data; prize allocation (only read for earnings); WebSocket live; pedigree as horse-to-horse references (sire/dam are text for now).
