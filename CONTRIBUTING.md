# Contributing — HorseRace_BE

Conventions for the backend repo. Keep changes small, reviewed, and on their own branch.

## Branch naming
Always branch off `main`. Format: `type/description-slug`.

| Type | Use case | Example |
|---|---|---|
| `feat` | New features | `feat/jockey-assignment` |
| `fix` | Bug fixes | `fix/login-error-handling` |
| `chore` | Maintenance, config, cleanup | `chore/update-dependencies` |
| `refactor` | Code restructuring (no behavior change) | `refactor/entity-enums` |
| `docs` | Documentation | `docs/update-readme` |

## Commit messages — Conventional Commits
Format: `type(scope): description`

- `feat(auth): add google login support`
- `fix(tournaments): reject invalid status transition`
- `refactor(model): replace string status fields with java enums`
- `chore: upgrade spring boot to 3.2.5`

Rules:
1. Use lowercase.
2. No period at the end.
3. Keep it imperative ("add", not "added").
4. Scope = the module/feature (`auth`, `tournaments`, `assignments`, `wallet`, `model`, …).

## Pull request process
1. Create a branch: `git checkout -b feat/amazing-feature`
2. Commit your changes (Conventional Commits).
3. Push: `git push -u origin feat/amazing-feature`
4. Open a PR into `develop` and request review.
5. CI/GitGuardian must pass. **Never commit secrets** — use env vars / gitignored `application-local.properties` (see `application-local.properties.example`).

## Backend-specific rules
- **DB-first**: `src/main/resources/db/schema_v4.sql` is the source of truth (`ddl-auto=validate`). Change the SQL **and** the matching JPA entity in the same commit, then run the app to validate.
- **Entities**: Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`; explicit `@Column(name = "snake_case")`; `OffsetDateTime` for `TIMESTAMPTZ`.
- **Enums**: every column with a SQL `CHECK (... IN (...))` maps to a Java enum whose constant names match the CHECK values exactly, mapped with `@Enumerated(EnumType.STRING)`. Compare with `==`/`!=` or `.equals(EnumConst)` — **never** `enumValue.equals("STRING")` (always false, compiles silently).
- **Responses**: wrap in `ApiResponse.<T>builder().success(true).data(...).build()`. Never return raw entities from controllers — map to a DTO.
- **Errors**: throw `new AppException(ErrorCode.X)`; add new cases to the `ErrorCode` enum.
- **Services**: interface in `service/`, impl in `service/impl/` (`@Service @RequiredArgsConstructor`, `@Transactional(readOnly = true)` for reads); constructor injection via `final` fields (no `@Autowired`).
- **Verify before pushing**: `./mvnw clean test` (boots the app → runs `schema_v4.sql` + `seed.sql` → Hibernate validates entities). Start the dev DB first: `docker compose up -d`.
