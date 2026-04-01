# Reality Check: Microkernel Skeleton Application

**Date**: 2026-03-28
**Assessor**: Independent reality assessment
**Status**: NOT READY -- Critical gap in Success Criterion #1

---

## Deployment Decision: NO-GO

The implementation is 85% functionally complete. Tests pass, the JAR builds, microkernel interfaces work, and the frontend integrates correctly. However, the primary success criterion -- `./mvnw spring-boot:run` provisions PostgreSQL and starts without errors -- fails due to a breaking change in the postgres:18 Docker image's volume mount path.

---

## Success Criteria Verification

| # | Criterion | Claimed | Reality | Evidence |
|---|-----------|---------|---------|----------|
| 1 | `./mvnw spring-boot:run` provisions PostgreSQL, runs Liquibase, starts without errors | Complete | **FAILS** | Container exits with code 1. See Finding C1 below. |
| 2 | `GET /api/health` returns `{"status":"UP"}` with HTTP 200 | Complete | **Cannot verify via live server** | Tests pass via MockMvc. Live server does not start (blocked by #1). |
| 3 | React app renders and displays health check response | Complete | **Cannot verify via live server** | `App.tsx` correctly calls `/api/health` and displays response. Static assets are built and present in JAR. Cannot verify end-to-end because server does not start. |
| 4 | `./mvnw clean package` produces single JAR | Complete | **PASS** | `target/aj-0.0.1-SNAPSHOT.jar` (62 MB) contains both `BOOT-INF/classes/static/index.html` and all backend classes. Verified independently. |
| 5 | `./mvnw test` passes with TestContainers | Complete | **PASS** | 18 tests, 0 failures, 0 errors. Independently verified. TestContainers uses ephemeral containers without volume mounts, so it avoids the postgres:18 volume issue. |
| 6 | PluginRegistry bean initializes with empty plugin list | Complete | **PASS** | `PluginRegistryTests` verifies bean creation, empty unmodifiable list, findById returns empty Optional, getExtensions returns empty list. All 4 tests pass. |
| 7 | SPA forwarding works for non-API, non-file paths | Complete | **PASS (in tests)** | `IntegrationTests.spaForwardingWorksForClientRoutePaths()` and `spaForwardingWorksForNestedClientRoutePaths()` both pass. MockMvc confirms `/dashboard` and `/settings/profile` return HTTP 200 via forward to index.html. Cannot verify on live server (blocked by #1). |

**Summary**: 4/7 confirmed working. 1 confirmed failing. 2 cannot be verified end-to-end because the server does not start.

---

## Critical Findings

### C1: Docker Compose PostgreSQL 18 volume mount path is incompatible (CRITICAL)

**Claim**: `./mvnw spring-boot:run` provisions PostgreSQL via Docker Compose and starts without errors.

**Reality**: The application fails immediately on startup. PostgreSQL 18 Docker image changed the data directory structure. The image no longer accepts volumes mounted at `/var/lib/postgresql/data`. It requires volumes mounted at `/var/lib/postgresql` (the parent directory), and the image itself creates version-specific subdirectories underneath.

**Evidence**:
```
Error: in 18+, these Docker images are configured to store database data in a
       format which is compatible with "pg_ctlcluster" (specifically, using
       major-version-specific directory names).

       Counter to that, there appears to be PostgreSQL data in:
         /var/lib/postgresql/data (unused mount/volume)

       The suggested container configuration for 18+ is to place a single mount
         at /var/lib/postgresql
```

Container exits with code 1 even on a fresh volume (tested with `docker compose down -v` followed by `docker compose up`).

**Root Cause**: `compose.yml` line 11 mounts `postgres-data:/var/lib/postgresql/data`. PostgreSQL 18 requires `postgres-data:/var/lib/postgresql` instead. See https://github.com/docker-library/postgres/pull/1259.

**Impact**: The entire developer experience story (Success Criterion #1) is broken. A developer running `./mvnw spring-boot:run` for the first time will see a startup failure. This also blocks verification of criteria #2, #3, and #7 on a live server.

**Fix**: Change `compose.yml` line 11 from:
```yaml
      - postgres-data:/var/lib/postgresql/data
```
to:
```yaml
      - postgres-data:/var/lib/postgresql
```

**Estimated effort**: 1 minute. Single line change.

---

## Quality Findings (from independent verification)

### Q1: Tests pass but rely on MockMvc, not live server (Medium)

**Observation**: All 18 tests pass, but the test suite only exercises the application through MockMvc and TestContainers (which uses ephemeral containers without volume mounts). No test catches the Docker Compose volume mount issue because:
- TestContainers does not use `compose.yml` at all
- `@SpringBootTest` with TestContainers bypasses Docker Compose entirely
- The `spring-boot-docker-compose` module is auto-skipped during tests by default

This is expected behavior for a test suite, but it means the primary developer workflow was never actually validated end-to-end.

### Q2: Test coverage is adequate for a skeleton (Low)

18 tests cover: context loading, DataSource configuration, database connectivity, PluginRegistry behavior (4 tests), API layer (4 tests with MockMvc), and integration tests (7 tests covering health endpoint, bean coexistence, SPA forwarding, static resources, and Liquibase). This is proportional to the skeleton's complexity.

### Q3: Code quality is clean and minimal (Informational)

- 104 LOC production code, 240 LOC test code (2.3:1 test ratio -- healthy)
- No over-engineering detected
- All files match spec requirements
- Naming conventions followed consistently

---

## Verification Reports Summary

| Report | Status | Key Finding |
|--------|--------|-------------|
| Spec Audit | Mostly Compliant | 0 critical, 1 high (postgres version inconsistency in artifacts -- not in code) |
| Pragmatic Review | Appropriate | 0 critical, 0 high. Complexity matches project scale. |
| Code Review | Warning | 1 critical (hardcoded credentials in compose.yml -- acceptable for dev skeleton) |
| Production Readiness | Not Ready (15%) | Expected for pre-alpha skeleton. Production deployment is explicitly out of scope. |

None of the previous verification reports caught the Docker Compose volume mount issue because none attempted to run `./mvnw spring-boot:run`.

---

## Reality vs Claims

| Aspect | Claimed | Actual |
|--------|---------|--------|
| Implementation steps | 24/24 complete | 23/24 functionally correct. compose.yml has a config error. |
| Tests | 18/18 passing | Confirmed: 18/18 passing. But tests don't cover the Docker Compose path. |
| JAR packaging | Working | Confirmed working. JAR contains frontend and backend. |
| Microkernel interfaces | Working | Confirmed working. PluginRegistry, Plugin, PluginDescriptor, ExtensionPoint all correct. |
| Health endpoint | Working | Working in tests. Cannot verify on live server. |
| SPA forwarding | Working | Working in tests. Cannot verify on live server. |
| Docker Compose PostgreSQL | Working | **BROKEN**. Container fails to start due to volume mount path. |
| Frontend build | Working | Confirmed. Vite builds to `resources/static/`, index.html present in JAR. |

---

## Pragmatic Action Plan

### Priority 1: Fix Docker Compose volume mount (CRITICAL)

**Task**: Change `compose.yml` volume mount from `/var/lib/postgresql/data` to `/var/lib/postgresql`.

**Success criteria**: `./mvnw spring-boot:run` starts without errors, PostgreSQL container stays running, Liquibase executes, health endpoint responds at `http://localhost:8080/api/health`.

**Estimated effort**: 1 minute (single line change + verification).

### Priority 2: Verify full developer workflow after fix (HIGH)

**Task**: After fixing compose.yml, manually verify:
1. `./mvnw spring-boot:run` starts cleanly
2. `curl http://localhost:8080/api/health` returns `{"status":"UP"}`
3. `curl http://localhost:8080/` serves React app
4. `curl http://localhost:8080/dashboard` serves React app (SPA forward)

**Success criteria**: All 7 success criteria pass.

**Estimated effort**: 5 minutes.

---

## Conclusion

The implementation is well-executed with one critical defect: the Docker Compose volume mount path is incompatible with the postgres:18 Docker image. This is a one-line fix. Once applied, all 7 success criteria should pass. The rest of the implementation -- microkernel interfaces, health endpoint, SPA forwarding, frontend build integration, test suite, JAR packaging -- is solid and verified.

The defect went undetected because:
1. TestContainers uses ephemeral containers (no volume mounts), so all tests pass
2. No verification step actually ran `./mvnw spring-boot:run` with Docker Compose
3. The postgres:18 volume change is a recent breaking change (see docker-library/postgres#1259)

**After the one-line fix**: This implementation is ready for use as the project skeleton.
