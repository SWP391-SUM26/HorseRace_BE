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

`src/main/resources/db/schema_v2.sql` is the authoritative schema (21 tables, UUID PKs,
`app_user`/`role`/`horse`/`race`/`prediction`/`wallet`/…, CHECK + UNIQUE constraints,
`TIMESTAMPTZ`, `gen_random_uuid()` defaults). Schema-only: **no functions/procedures/triggers**
— maintain `updated_at` in app code via Hibernate `@UpdateTimestamp`. It begins with a
`DROP TABLE ... CASCADE` clean-slate block, so it is safe to re-run.

`application.properties` wires this up:
```
spring.jpa.hibernate.ddl-auto=validate                       # Hibernate verifies, never edits
spring.sql.init.mode=always                                  # run the SQL on every startup
spring.sql.init.schema-locations=classpath:db/schema_v2.sql  # schema
spring.sql.init.data-locations=classpath:db/seed.sql         # dev seed (roles + sample users)
```
On startup Spring runs `schema_v2.sql` then `seed.sql`, then Hibernate **validates** the
entities against the tables. **This wipes and re-seeds the DB every restart** (good for dev).
To persist data across restarts after the first run, set `spring.sql.init.mode=never`.

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

Security/JWT dependencies are present but auth is **not yet wired up**. The other ~19 domain
tables in `schema_v2.sql` (horse, tournament, race, registration, entry, assignments, result,
prediction, wallet, payout, prize, …) have **no entities/repositories/services** yet — backlog
to build one feature module at a time, following the `users/` pattern.
