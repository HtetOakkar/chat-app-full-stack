# Phase 1 Changes

This document summarizes the Phase 1 stabilization and security work applied to the backend.

## Scope Completed

- Security and configuration hardening
- JWT claim consistency and auth robustness
- WebSocket identity and sender-trust fixes
- Message persistence integrity fixes
- Validation and standardized error responses
- Test baseline upgrades (H2 test profile + integration tests)
- CI workflow for continuous verification
- Database schema migration baseline via Flyway

## What Changed

### 1) Secrets and configuration hygiene

- Updated `/src/main/resources/application.properties`:
  - Removed hardcoded JWT secret.
  - Externalized env-backed settings (`JWT_SECRET`, `DB_*`, `REDIS_*`, CORS origins).
  - Reduced default operational exposure (`management.endpoints.web.exposure.include=health,info`).
  - Set `server.error.include-message=never`.
  - Disabled Open Session in View (`spring.jpa.open-in-view=false`).
  - Disabled Spring Data Redis repository scanning (`spring.data.redis.repositories.enabled=false`) since RedisTemplate is used directly.
  - Removed schema auto-mutation (`spring.jpa.hibernate.ddl-auto=validate`).
  - Enabled Flyway migrations.
  - Enabled Flyway baseline-on-migrate by default for existing non-empty schemas.
- Added `.env` patterns to `.gitignore` and preserved `.env.example`.
- Added `.env.example` with safe placeholder values.

### 2) Auth and JWT consistency

- Updated `/src/main/java/com/example/chatapp/jwt/service/JwtServiceImpl.java`:
  - `generateTokenFromUser` now includes `roles` claim.
- Updated `/src/main/java/com/example/chatapp/jwt/JwtAuthenticationFilter.java`:
  - Improved handling for missing/invalid claims.
  - Avoids unsafe assumptions when parsing role claims.
  - Improved error logging context.
- Updated `/src/main/java/com/example/chatapp/user/service/AuthenticationServiceImpl.java`:
  - Uses configured token expiration (`jwt.expirationMs`) consistently.
  - Removed role-gating logic that blocked non-ROLE_USER authenticated users.
  - Returns stable unauthorized message for invalid credentials.

### 3) WebSocket identity and trust boundary

- Updated `/src/main/java/com/example/chatapp/websocket/AuthChannelInterceptor.java`:
  - Sets authenticated `UserPrincipal` directly into STOMP accessor principal.
  - Removed unsafe header mutation and token-value logging.
- Updated `/src/main/java/com/example/chatapp/chat/controller/ChatController.java`:
  - Derives sender identity from authenticated principal.
  - Stops trusting `senderId` from client payload.
  - Validates private-message recipient presence.
  - Uses `@Payload` + validation for websocket payloads.
- Updated `/src/main/java/com/example/chatapp/websocket/WebSocketConfig.java`:
  - Replaced wildcard origins with configurable allowed origins.

### 4) Message persistence integrity

- Updated `/src/main/java/com/example/chatapp/config/BatchConfig.java`:
  - Properly maps both sender and optional recipient from DTO.
  - Applies default `MessageType.TEXT` when absent.
- Updated `/src/main/java/com/example/chatapp/message/model/entity/Message.java`:
  - `recipient` is optional (supports public messages).
  - `deliveredAt` no longer forced non-null.
- Updated `/src/main/java/com/example/chatapp/message/mapper/MessageMapperImpl.java`:
  - Handles nullable recipient safely.

### 5) Scheduling duplication removed

- Updated `/src/main/java/com/example/chatapp/message/service/MessageServiceImpl.java`:
  - Removed duplicate scheduled batch trigger.
- Kept dedicated scheduler in `/src/main/java/com/example/chatapp/config/JobScheduler.java`.

### 6) Entity model fixes

- Updated `/src/main/java/com/example/chatapp/user/model/entity/User.java`:
  - Removed password uniqueness.
  - Removed dangerous cascade behavior on role relation.
- Updated `/src/main/java/com/example/chatapp/user/model/entity/Role.java`:
  - Removed cascading user lifecycle from role entity.

### 7) Request validation + API error format

- Updated DTOs:
  - `/src/main/java/com/example/chatapp/user/model/request/UserSignUpRequest.java`
  - `/src/main/java/com/example/chatapp/user/model/request/UserLoginRequest.java`
  - `/src/main/java/com/example/chatapp/message/model/dto/MessageDto.java`
  - `/src/main/java/com/example/chatapp/message/model/dto/OnlineStatusDto.java`
- Updated controller:
  - `/src/main/java/com/example/chatapp/user/controller/AuthenticationController.java` uses `@Valid`.
- Added standardized API error handling:
  - `/src/main/java/com/example/chatapp/exception/ApiError.java`
  - `/src/main/java/com/example/chatapp/exception/GlobalExceptionHandler.java`

### 8) Database migration baseline

- Added Flyway migration:
  - `/src/main/resources/db/migration/V1__init_schema.sql`
- Removed conflicting custom batch job repository override:
  - deleted `/src/main/java/com/example/chatapp/config/BatchIsolationConfig.java`

### 9) Tests and CI

- Added test profile config:
  - `/src/test/resources/application-test.properties`
  - Uses in-memory H2 schema (`create-drop`), disables Flyway, and disables scheduler (`app.scheduler.enabled=false`) for deterministic tests.
- Updated context-load test to use test profile:
  - `/src/test/java/com/example/chatapp/ChatappApplicationTests.java`
- Added auth integration tests:
  - `/src/test/java/com/example/chatapp/user/controller/AuthenticationControllerIntegrationTest.java`
- Added Mockito mock-maker override to avoid inline agent requirement in restricted environments:
  - `/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- Added CI workflow:
  - `/.github/workflows/ci.yml` (runs `./mvnw -B verify` on push/PR)

## New Dependencies

Updated `/pom.xml` with:

- `spring-boot-starter-validation`
- `flyway-core`
- `flyway-mysql` (required with Flyway 11 for MySQL database support)
- `h2` (test scope)

## Operational Notes

- Required runtime environment variables:
  - `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`
  - optional: `JWT_EXPIRATION_MS`, `REDIS_HOST`, `REDIS_PORT`, `APP_CORS_ALLOWED_ORIGINS`
- Flyway baseline defaults:
  - `FLYWAY_BASELINE_ON_MIGRATE=true` (handles existing non-empty schemas without history table)
  - `FLYWAY_BASELINE_VERSION=1` (treats existing schema as already at initial migration)
  - after first successful initialization on legacy DBs, you can set `FLYWAY_BASELINE_ON_MIGRATE=false` for stricter behavior.
- First startup after these changes expects Flyway-managed schema.
- Existing JWTs minted before this update may fail claim parsing if they lack role claims.

### Recovery for failed `V1` migration

If you already have a failed row in `flyway_schema_history`, repair once before restart:

```bash
./mvnw flyway:repair \
  -Dflyway.url=\"jdbc:mysql://localhost:3306/chat_app_db\" \
  -Dflyway.user=\"<user>\" \
  -Dflyway.password=\"<password>\"
```

Then start the app again with:

- `FLYWAY_BASELINE_ON_MIGRATE=true`
- `FLYWAY_BASELINE_VERSION=1`

## Verification

After pulling these changes, run:

```bash
./mvnw -B verify
```
