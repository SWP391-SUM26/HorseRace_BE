# HorseRace_BE — Horse Racing Tournament Management System (Backend)

SWP391 course project. Spring Boot REST API for managing horse racing tournaments:
horse owners register horses, hire jockeys, referees officiate and record results,
spectators predict (bet on) outcomes, and admins run the whole thing.

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Architecture | Spring Modulith 1.1.4 (modular monolith) |
| Web | spring-boot-starter-web (REST) |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL 15 |
| Security | spring-boot-starter-security + JWT (io.jsonwebtoken 0.11.5) |
| Validation | spring-boot-starter-validation |
| Boilerplate | Lombok |
| Build | Maven (`./mvnw`) |

Base package: `com.SWP391.horserace`

## Commands

```bash
# Start PostgreSQL (db: horserace_db, user: postgres, pw: 12345, port 5432)
docker-compose up -d

# Run the app (port 8080)
./mvnw spring-boot:run

# Build / test / package
./mvnw clean test
./mvnw clean package        # jar in target/

# Run a single test
./mvnw test -Dtest=HorseraceApplicationTests
```

API base path: `/api/v1/...` (e.g. `GET /api/v1/users`, `GET /api/v1/users/{id}`).

API docs (springdoc-openapi): **Swagger UI** at `http://localhost:8080/swagger-ui.html`,
OpenAPI JSON at `/v3/api-docs`. A JWT bearer scheme is registered (`OpenApiConfig`) — use the
**Authorize** button with an access token from `POST /api/v1/auth/login`. Swagger paths are
permitted in `SecurityConfig`.

## Project Structure (package-by-feature)

```
com.SWP391.horserace
├── HorseraceApplication.java        # @SpringBootApplication entry point
├── shared/                          # cross-cutting, shared across modules
│   ├── dto/ApiResponse<T>           # standard response envelope (success, message, data, timestamp)
│   └── exception/
│       ├── ErrorCode                # enum: code + message + HttpStatus
│       ├── AppException             # throws an ErrorCode
│       └── GlobalExceptionHandler   # @RestControllerAdvice -> ApiResponse
└── users/                           # feature module (the ONLY implemented module so far)
    ├── controller/   UserController
    ├── dto/          UserResponse
    ├── entity/       User
    ├── repository/   UserRepository (JpaRepository)
    └── service/      UserService + impl/UserServiceImpl
```

**Each feature module mirrors `users/`**: `controller/`, `dto/`, `entity/`, `repository/`,
`service/` (interface) + `service/impl/` (implementation). New domains (horse, tournament,
race, prediction, wallet, …) should follow this same layout.

## Conventions (follow these when adding code)

- **Responses**: always wrap in `ApiResponse.<T>builder().success(true).data(...).build()`.
  Never return raw entities/DTOs from controllers.
- **Errors**: throw `new AppException(ErrorCode.X)`. Add new cases to the `ErrorCode` enum
  (code + message + HttpStatus). `GlobalExceptionHandler` translates them automatically.
- **Service pattern**: interface in `service/`, implementation in `service/impl/`,
  annotated `@Service @RequiredArgsConstructor`, `@Transactional(readOnly = true)` for reads.
- **DI**: constructor injection via Lombok `@RequiredArgsConstructor` + `final` fields. No `@Autowired`.
- **Entities**: Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, `@Entity @Table(name = "...")`.
- **DTOs**: Lombok `@Data @Builder`. Keep entities out of the controller/response layer.

## Database — DB-first (schema is the source of truth)

`src/main/resources/db/schema_v4.sql` is the authoritative schema (UUID PKs,
`app_user`/`role`/`horse`/`race`/`prediction`/`wallet`/…, CHECK + UNIQUE constraints,
`TIMESTAMPTZ`, `gen_random_uuid()` defaults). Schema-only: **no functions/procedures/triggers**
— maintain `updated_at` in app code via Hibernate `@UpdateTimestamp`. It begins with a
`DROP TABLE ... CASCADE` clean-slate block. **43 tables** (`tournament_round`, `standing` and
`audit_log` were removed — they had an entity but no repository and zero references; `race.round_id`
went with `tournament_round`).

**5 roles**: `ADMIN`, `HORSE_OWNER`, `JOCKEY`, `RACE_REFEREE`, `SPECTATOR`. `TRAINER`/`VET` were
removed (0 users, 0 permissions); `membership_application.requested_role` is now `('OWNER','JOCKEY')`
only, matching the `RequestedRole` enum.

Two seed files:
- `db/seed.sql` — **bootstrap**: 5 roles, 25 permissions, the role→permission matrix, one admin
  (`admin@horserace.local` / `admin123`), and the house wallet.
- `db/seed_demo.sql` — **test data for all 43 tables**. 25 users (5 per role), **every one
  `email_verified = TRUE` + `ACTIVE` + KYC `VERIFIED`**, password `Test@1234`:
  `admin1..5@`, `horseowner1..5@`, `jockey1..5@`, `referee1..5@`, `spectator1..5@horserace.local`.
  Plus 10 horses, 10 venues, 10 tournaments, 15 races, 16 entries, 11 results, 16 predictions,
  10 payouts, ~10 rows in every other table. Explicit UUID literals throughout, so it is
  deterministic and FK-wiring is readable.
  (`jockey_profile`/`owner_profile` have 5 rows each — their PK *is* the user FK, so they are
  capped by the 5-users-per-role choice.)

**Images are real Cloudinary assets** on cloud `qtpgbwsh`, uploaded by
`scripts/seed/upload-cloudinary.sh` (signed upload via curl+openssl, remote-fetch from
picsum.photos, fixed `public_id` + `overwrite=true` so it is idempotent). Seeded URLs are
version-less (`.../image/upload/horses/seed-horse-01.jpg`) so re-running the script never
breaks a link. Re-run it before seeding a fresh machine. **Exception:** `attachment.object_key`
stays on local disk (`uploads/`) by design — `AttachmentServiceImpl` pins
`@Qualifier(localFileStorage)` so RESTRICTED files never reach a public CDN.

**The DB self-initializes via Docker.** `docker-compose.yml` mounts the three SQL files into the
Postgres `docker-entrypoint-initdb.d/` (`01-schema.sql`, `02-seed.sql`, `03-seed-demo.sql`);
Postgres runs them **only when the `postgres_data` volume is empty** (first boot). Comment out the
`seed_demo.sql` mount for a DB with bootstrap data only. So:
```
docker compose up -d                        # DB only (dev) — self-inits on first run
docker compose --profile full up -d --build # BE + DB (teammate/demo, one command)
docker compose down -v && docker compose up -d   # RESET: wipe volume + re-init from schema+seed
```
`application.properties`: `ddl-auto=validate` (Hibernate verifies, never edits);
`spring.sql.init.mode` stays **off** (the DB initializes itself, so Spring must not double-run it);
`spring.datasource.url` is env-overridable (`SPRING_DATASOURCE_URL`) so the same file works on host
(`localhost`) and in-container (`db`).

**Schema changes:** edit `schema_v4.sql`, then either reset the volume (`down -v`) OR, to keep data,
apply a matching `ALTER`/`CREATE` on the live DB by hand
(`docker exec horserace_postgres psql -U postgres -d horserace_db -c "…"`) before restarting — else
`ddl-auto=validate` fails on the mismatch.

**Entity-mapping rules (so `validate` passes):** every entity must match the SQL exactly —
UUID PK with `@GeneratedValue(strategy = GenerationType.UUID)`, explicit `@Column(name="...")`
for snake_case columns, `OffsetDateTime` for `TIMESTAMPTZ`, `@Enumerated(EnumType.STRING)`
enums whose names match the CHECK constraint values, `columnDefinition="text"` for TEXT columns.
See `users/entity/User.java` as the canonical example.

## Current State

The **users** module is the reference vertical slice, fully aligned to `app_user`:
`User` entity (UUID, `@ManyToOne Role`, `UserStatus`/`KycStatus` enums, soft-delete via
`deleted` flag), `UserRepository` (UUID id, `findAllByDeletedFalse`), `UserResponse` DTO
(no password), `UserService`/`UserServiceImpl`, `UserController`
(`GET /api/v1/users`, `GET /api/v1/users/{id}`). A minimal **roles** module (`Role` entity +
`RoleRepository`) backs the FK. **Copy this slice** when building new domains.

**Authentication is implemented** (stateless JWT + DB-stored rotating refresh tokens):
- Modules: `auth/` (controller/service/dto/entity/repository/config) and `security/`
  (`SecurityConfig`, `JwtAuthenticationFilter`, `JwtAuthEntryPoint`, `RestAccessDeniedHandler`).
- Endpoints (all public): `POST /api/v1/auth/login` (email+password), `POST /auth/google`
  (Google ID token), `POST /auth/refresh` (rotates the refresh token), `POST /auth/logout`.
- **Access token**: signed HS256 JWT (`JwtService`), 15-min TTL, carries `sub`(userId)/`email`/`role`.
- **Refresh token**: opaque random, only its SHA-256 hash stored in the `refresh_token` table
  (added to `schema_v2.sql`); rotated on every `/refresh`, with reuse-detection that revokes all
  of a user's tokens (`RefreshTokenService`).
- **Password**: `DelegatingPasswordEncoder` — verifies seeded `{noop}` passwords AND new `{bcrypt}` ones.
- **Google sign-in**: `GoogleTokenVerifier` validates the ID token via Google's tokeninfo endpoint
  (no extra dependency); first-time users are auto-provisioned with the `SPECTATOR` role.
- **All non-`/auth/**` endpoints now require `Authorization: Bearer <accessToken>`.**
- Config in `application.properties`: `app.jwt.*` (override secret via `APP_JWT_SECRET`) and
  `app.google.client-id` (set `APP_GOOGLE_CLIENT_ID`). `@EnableMethodSecurity` is on, so
  `@PreAuthorize("hasRole('ADMIN')")` works on controllers/services.

The domain modules (horse, tournament, race, registration, entry, assignments, inspections, results,
reports, penalties, violations, prediction, betting pool, wallet, payout, prize, reward, notification,
attachment, onboarding, staffing, venue) are **built out** — 39 entities backed by 38 repositories
plus services/controllers. Four tables intentionally have no entity of their own and are reached
through their owner: `role_permission` (`@JoinTable` on `Role.permissions`) and the three
`@ElementCollection`s `horse_characteristic`, `race_prize_distribution`, `race_fraction`.
`tournament_venue` has no repository by design — it is cascaded from `Tournament.venues`.
