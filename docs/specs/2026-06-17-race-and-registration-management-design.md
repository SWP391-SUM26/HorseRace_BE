# Race Management + Registration Management — Design Spec

> **Date:** 2026-06-17
> **Source of truth:** Figma (Race + Registration screens) + `schema_v4.sql` + existing `tournaments` module pattern.
> **Approved decisions:** base branches off `feat/horse-management`; race uses soft-delete (additive cols);
> schedule sets times + opens race; cancel → CANCELLED; spec/branches pushed to origin before implementation.

## 1. Scope

Two new feature modules, 18 tickets total. Both follow the established package-by-feature pattern
(`controller / dto / entity / repository / service + service/impl`) modelled on the fully-built
`tournaments` module.

- **Registration Management** (6 tickets): Submit, Get List, Search, Filter, Approve, Reject.
- **Race Management** (12 tickets): Get List, Search, Filter, Sort, Pagination, Get Detail,
  Create, Update, Delete, Schedule, Cancel, Assign Participants.

The entities `Race`, `RaceEntry`, `RaceStatus`, `RaceEntryStatus`, `TournamentRegistration`,
`RegistrationStatus` already exist and match `schema_v4.sql`. This work adds the
repository/specification/service/controller/DTO layers (plus one additive schema change for race
soft-delete).

## 2. Branch strategy

`origin/develop` lacks `schema_v4.sql`, the `tournaments` module, and the race/registration
entities — they currently live only on `feat/horse-management`. Therefore:

- `feat/registration-management` ← branched off `feat/horse-management`.
- `feat/race-management` ← branched off `feat/registration-management` (inherits `RegistrationRepository`,
  needed by Assign Participants; avoids a duplicate-file conflict).
- **ErrorCode ranges:** Registration = **7xxx**, Race = **8xxx** (staffing 4xxx / horse 5xxx / file 6xxx already used).
- Schema change (race soft-delete) belongs only to `feat/race-management`.
- PRs target `develop` in order: registration first, then race (after registration merges).

## 3. Schema change (only `feat/race-management`, additive, keep `ddl-auto=validate`)

`race` table currently has **no** soft-delete columns. Add (mirroring `tournament`):
```
is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
deleted_at  TIMESTAMPTZ
```
`Race` entity gains `boolean deleted` (`@Column(name="is_deleted")`) + `OffsetDateTime deletedAt`.
No other schema change. Registration Management needs **no** schema change.

---

## 4. Module 1 — Registration Management

**New files** under `registrations/`: `repository/RegistrationRepository`,
`repository/RegistrationSpecification`, `dto/RegistrationRequest`, `dto/RegistrationFilterRequest`,
`dto/RegistrationResponse`, `dto/RejectRegistrationRequest`, `service/RegistrationService`,
`service/impl/RegistrationServiceImpl`, `controller/RegistrationController`.

### 4.1 Repository
`extends JpaRepository<TournamentRegistration, UUID>, JpaSpecificationExecutor<TournamentRegistration>`;
`boolean existsByTournamentTournamentIdAndHorseHorseId(UUID, UUID)`; `existsByRegistrationCode(String)`;
`findByIdWithDetails(UUID)` (JOIN FETCH owner, tournament, horse) for detail/response.

### 4.2 Endpoints
| Ticket | Method + path | Auth | Behaviour |
|---|---|---|---|
| Submit Registration | `POST /api/v1/registrations` | horse owner | body `{tournamentId, horseId}`; caller must own the horse; tournament must exist & accept registration (status `PUBLISHED`/`REGISTRATION_OPEN`); reject duplicate (UNIQUE `(tournament_id, horse_id)` → 409); gen `registration_code` (`REG%05d`); status `SUBMITTED`, `submittedAt=now` |
| Get List + Pagination | `GET /api/v1/registrations` | owner→own, admin→all | `Page<RegistrationResponse>` |
| Search | `GET /api/v1/registrations?q=` | — | OR-like on `registration_code`, horse name, tournament name |
| Filter | `GET /api/v1/registrations?status=&tournamentId=&horseId=&ownerUserId=` | — | enum/equality predicates |
| Approve | `PATCH /api/v1/registrations/{id}/approve` | admin | `SUBMITTED`/`UNDER_REVIEW` → `APPROVED`; set `approvedBy`, `reviewedAt` |
| Reject | `PATCH /api/v1/registrations/{id}/reject` | admin | body `{reason}`; `SUBMITTED`/`UNDER_REVIEW` → `REJECTED`; set `rejectionReason`, `reviewedAt` |

Plus (for a complete flow, not separate tickets): `GET /api/v1/registrations/{id}` (detail) and
`PATCH /api/v1/registrations/{id}/withdraw` (owner → `WITHDRAWN`, only from non-terminal states).

### 4.3 State guards
Approve/Reject only from `SUBMITTED` or `UNDER_REVIEW`; Withdraw only from `DRAFT`/`SUBMITTED`/`UNDER_REVIEW`.
Any other source state → `REGISTRATION_INVALID_STATUS` (400).

### 4.4 Error codes (7xxx)
`REGISTRATION_NOT_FOUND` (404), `REGISTRATION_ALREADY_EXISTS` (409),
`REGISTRATION_INVALID_STATUS` (400), `NOT_REGISTRATION_OWNER` (403),
`TOURNAMENT_NOT_ACCEPTING_REGISTRATION` (400). (Reuse existing `HORSE_NOT_FOUND`,
`TOURNAMENT_NOT_FOUND`, `NOT_HORSE_OWNER`, `UNAUTHENTICATED`.)

---

## 5. Module 2 — Race Management

**New files** under `races/`: `repository/RaceSpecification`, `dto/RaceRequest`,
`dto/RaceFilterRequest`, `dto/RaceResponse`, `dto/ScheduleRaceRequest`, `dto/AssignParticipantRequest`,
`dto/RaceEntryResponse`, `service/RaceService`, `service/impl/RaceServiceImpl`,
`controller/RaceController`. Modify: `RaceRepository` (add `JpaSpecificationExecutor` + helpers),
`Race` entity (soft-delete fields).

### 5.1 Repository
`RaceRepository extends JpaRepository<Race, UUID>, JpaSpecificationExecutor<Race>`;
`existsByRaceCode(String)`; `findByRaceIdAndDeletedFalse(UUID)`;
`findByIdWithTournament(UUID)` (JOIN FETCH tournament). Reuse existing `RaceEntryRepository`;
add `findByRaceRaceId(UUID)` + `countByRaceRaceId(UUID)` for listing/`max_participants` check.

### 5.2 Endpoints
| Ticket | Method + path | Behaviour |
|---|---|---|
| List/Search/Filter/Sort/Pagination | `GET /api/v1/races` | params `q, status, tournamentId, raceType, sortBy, sortDir, page, size`; `deleted=false`; `Page<RaceResponse>` |
| Get Detail | `GET /api/v1/races/{id}` | 404 if missing/deleted |
| Create | `POST /api/v1/races` | validate tournament exists; gen `race_code` (`RACE%05d`); status `SCHEDULED` |
| Update | `PUT /api/v1/races/{id}` | partial; cannot edit a `CANCELLED`/`FINISHED`/`OFFICIAL` race → `RACE_INVALID_STATUS` |
| Delete | `DELETE /api/v1/races/{id}` | soft-delete (`deleted=true`, `deletedAt=now`) |
| Schedule | `PATCH /api/v1/races/{id}/schedule` | body `{scheduledStartAt, predictionCutoffAt?}`; only from `SCHEDULED`; sets times, status → `OPEN` |
| Cancel | `PATCH /api/v1/races/{id}/cancel` | status → `CANCELLED`; guard: not already `FINISHED`/`OFFICIAL`/`CANCELLED` |
| Assign Participants | `POST /api/v1/races/{id}/entries` | body `{registrationId, laneNo?, entryNo?}`; create `race_entry` (status `ENTERED`, gen `entry_code` `ENT%05d`) |

Plus: `GET /api/v1/races/{id}/entries` (list a race's participants).

### 5.3 Assign Participants — guards
- Race exists, not deleted, status in (`SCHEDULED`, `OPEN`) → else `RACE_NOT_OPEN_FOR_ENTRY` (400).
- Registration exists and status `APPROVED` → else `REGISTRATION_NOT_APPROVED` (400).
- `registration.tournament == race.tournament` → else `RACE_TOURNAMENT_MISMATCH` (400).
- `max_participants` (if set) not exceeded (count existing entries) → else `RACE_FULL` (400).
- DB enforces uniqueness `(race_id, registration_id)`, `(race_id, lane_no)`, `(race_id, entry_no)` →
  409 via existing `DataIntegrityViolationException` handler.

### 5.4 Validation (RaceRequest)
`@Size` on string fields; `distanceMeter` `@Positive`; `maxParticipants` `@Positive`;
`tournamentId` `@NotNull` on create. Enum `status` bound by Jackson (invalid → 400 via existing handler).

### 5.5 Error codes (8xxx)
`RACE_CODE_EXISTED` (409), `RACE_INVALID_STATUS` (400), `RACE_NOT_OPEN_FOR_ENTRY` (400),
`RACE_TOURNAMENT_MISMATCH` (400), `RACE_FULL` (400), `REGISTRATION_NOT_APPROVED` (400).
(Reuse existing `RACE_NOT_FOUND` (4003), `TOURNAMENT_NOT_FOUND`, `ENTRY_NOT_FOUND`,
`REGISTRATION_NOT_FOUND`.)

---

## 6. Authorization
Current dev posture (`permitAll`) is preserved; caller id resolved via the existing controller
convention (`@AuthenticationPrincipal` / `resolveUserId`). Null principal on a write → `UNAUTHENTICATED`
(401). Owner-scoped actions (submit, withdraw) verify horse/registration ownership; admin-scoped
actions (approve, reject, create/update/delete/schedule/cancel race) are documented as admin and
will gate behind `@PreAuthorize("hasRole('ADMIN')")` when RBAC (Phase 0.2) lands.

## 7. Schedule (working days from 2026-06-17)
| Date | Branch | Work |
|---|---|---|
| Wed 17/6 | `feat/registration-management` | scaffold + repo/spec/DTO; **Submit** + code gen + validation |
| Thu 18/6 | `feat/registration-management` | **List/Search/Filter/Pagination** + detail; **Approve** + **Reject** + guards; unit tests + boot verify; push + PR |
| Fri 19/6 | `feat/race-management` | soft-delete schema/entity; repo/spec/DTO; **Create/Update/Get detail** |
| Mon 22/6 | `feat/race-management` | **List/Search/Filter/Sort/Pagination** + **Delete** (soft) |
| Tue 23/6 | `feat/race-management` | **Schedule** + **Cancel** + **Assign Participants** + guards; unit tests + boot verify |
| Wed 24/6 | both | buffer: `./mvnw test` integration verify + open PRs into develop |

## 8. Verification
Each module: `./mvnw compile` + boot (Hibernate `validate` against schema, incl. new race columns) +
unit tests for: registration submit (ownership, duplicate, tournament-state), approve/reject/withdraw
state guards; race create (code gen, tournament check), schedule/cancel guards, assign-participants
guards (not-approved / tournament-mismatch / not-open / full). Mockito note: concrete deps use real
instances (Java 25 cannot mock concrete classes); only interfaces (`*Repository`) are `@Mock`.

## 9. Out of scope
FE pages; jockey assignment (existing module); race results/officiality recording (referee flow);
prize/payout; check-in / scratch / disqualify transitions on entries (only ENTERED on assign);
WebSocket live; fine-grained RBAC (Phase 0.2).
