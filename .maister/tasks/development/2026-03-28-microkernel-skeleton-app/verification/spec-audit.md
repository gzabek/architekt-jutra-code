# Specification Audit: Microkernel Skeleton Application

**Auditor**: Independent specification audit
**Date**: 2026-03-28
**Spec under review**: `implementation/spec.md`
**Supporting artifacts reviewed**: `analysis/requirements.md`, `analysis/gap-analysis.md`, `analysis/scope-clarifications.md`, `research/outputs/research-report.md`, `research/outputs/decision-log.md`
**Codebase examined**: All existing Java source files, pom.xml, .gitignore, application.properties, directory structure

---

## Compliance Status: Mostly Compliant

The specification is well-structured, internally consistent within its own document, and implementable. All requirements have clear acceptance criteria. However, there are inconsistencies with upstream artifacts, one ambiguity that could cause implementation confusion, and several minor gaps in testability and detail.

---

## Summary

| Category | Count |
|----------|-------|
| Critical Issues | 0 |
| High Severity | 1 |
| Medium Severity | 4 |
| Low Severity | 3 |
| Clarification Needed | 2 |

---

## High Severity Findings

### Finding H1: PostgreSQL version inconsistency between spec and research artifacts

**Spec Reference**: Requirement 1 -- "compose.yml at project root with `postgres:18` image"; Requirement 12 -- "Pin image from `postgres:latest` to `postgres:18`"

**Evidence**:
- `implementation/spec.md` lines 19, 40, 49, 92: Consistently uses `postgres:18`
- `analysis/requirements.md` lines 28, 43: Uses `postgres:18` (aligned with spec)
- `analysis/scope-clarifications.md` lines 6-7: Explicitly states "Pin to PostgreSQL 18" as user override of research recommendation
- `analysis/gap-analysis.md` lines 21, 47, 57, 128-130, 142: Uses `postgres:17` throughout -- **never updated to reflect the user's scope clarification**
- `research/outputs/research-report.md` lines 110, 172, 207, 501, 537, 580: Uses `postgres:17` throughout (expected -- research predates scope clarification)
- `research/outputs/decision-log.md` line 155: "PostgreSQL version pinned to 17" (expected -- ADR predates scope clarification)

**Category**: Inconsistent

**Severity**: High -- An implementer reading the gap analysis (which is a natural companion to the spec) would see `postgres:17` everywhere and could implement that version instead of the specified `postgres:18`. The gap analysis table on line 21 explicitly says "PostgreSQL 18" in the purpose column.

**Recommendation**: The spec itself is internally consistent (always says 18). The gap analysis was never updated after the scope clarification. This is an artifact inconsistency, not a spec defect. The implementer should follow the spec (postgres:18), but the gap analysis should ideally be corrected to avoid confusion.

---

## Medium Severity Findings

### Finding M1: PluginRegistry constructor injection with empty List -- potential Spring DI behavior undocumented

**Spec Reference**: Requirement 2 -- "PluginRegistry -- @Component with constructor injection of List<Plugin>"

**Evidence**:
- `implementation/spec.md` line 24: Specifies `List<Plugin>` constructor injection
- `research/outputs/research-report.md` line 551: States "Spring injects empty list for List<T> when no beans match; safe by default"
- The research report code sample (lines 243-264) shows the implementation

**Category**: Ambiguous

**Severity**: Medium -- Spring Boot's behavior of injecting an empty `List<T>` when no matching beans exist is well-known, but it is version-dependent and configuration-dependent. The spec does not mention what happens if Spring cannot inject an empty list (e.g., if `@Autowired(required=true)` is the effective default). In practice, constructor injection with `List<Plugin>` in Spring Boot 4.0.5 will receive an empty list, so this should work. However, the spec should note this behavior explicitly or specify that the constructor should use `@Autowired(required = false)` or `Optional<List<Plugin>>` as a safety measure.

**Recommendation**: Low risk in practice. Add a brief note to the spec that the empty list injection is expected and verified behavior. Alternatively, the implementation could use `@Autowired List<Plugin> plugins = List.of()` as a default. No action strictly needed -- this is a "belt and suspenders" concern.

### Finding M2: SpaForwardController regex pattern may not cover all SPA routes

**Spec Reference**: Requirement 4 -- "SpaForwardController forwarding non-API, non-file paths (paths without dots) to forward:/index.html"

**Evidence**:
- `implementation/spec.md` line 27: "paths without dots"
- `research/outputs/research-report.md` lines 386-399: Two mappings: `/{path:[^\\.]*}` (single segment) and `/**/{path:[^\\.]*}` (nested segments)
- The spec itself does not include the regex patterns -- it only describes the intent

**Category**: Incomplete

**Severity**: Medium -- The spec describes the intent clearly ("non-API, non-file paths") but does not specify the exact URL patterns. The research report provides two handler methods with specific regex patterns. However, neither the spec nor the research addresses: (a) the root path `/` -- does it need special handling or does Spring serve `index.html` from static resources automatically? (b) paths like `/api` itself (without trailing segment) -- is this intercepted by SPA or API? (c) paths with multiple dots like `/some.path/nested`.

**Recommendation**: For a skeleton with no React Router routes, this is low risk. The intent is clear enough for implementation. Consider adding a note that Spring Boot auto-serves `/` from `classpath:/static/index.html` by default, so the SPA controller only needs to handle deep-link paths.

### Finding M3: Frontend scaffolding step lacks reproducibility specification

**Spec Reference**: Requirement 6 -- "Scaffold via `npm create vite@latest` with `react-ts` template in `src/main/frontend/`"

**Evidence**:
- `implementation/spec.md` line 31: Uses `npm create vite@latest`
- The `@latest` tag means the Vite scaffold version is not pinned

**Category**: Incomplete

**Severity**: Medium -- Using `@latest` means different developers running the scaffold at different times may get different Vite versions, different default file structures, or different `package.json` contents. This is a one-time scaffolding operation so it only matters if multiple developers are independently setting up, but it contradicts the reproducibility philosophy stated elsewhere (Node version pinned to 22.14.0, frontend-maven-plugin pinned to 1.15.1).

**Recommendation**: After scaffolding, the `package.json` lockfile pins everything. This is acceptable for a one-time scaffold. Optionally, note the expected Vite version (e.g., Vite 6.x) so the implementer can verify they got the expected scaffold.

### Finding M4: No specification for how the health endpoint should handle database-down scenarios

**Spec Reference**: Requirement 3 -- "HealthController at GET /api/health returning {\"status\":\"UP\"} as Map<String, String>"

**Evidence**:
- `implementation/spec.md` line 26: Returns hardcoded `{"status":"UP"}`
- The endpoint returns a static response regardless of actual system health

**Category**: Ambiguous

**Severity**: Medium -- The spec explicitly says return `Map.of("status", "UP")` as a static response. This is fine for a skeleton smoke test. However, calling it a "health" endpoint sets expectations that it reflects actual system health. If the database is down, this endpoint still returns UP. This is intentional per the research report (it's a smoke test, not an actuator health check), but the spec could be clearer that this is a connectivity smoke test, not a production health check.

**Recommendation**: No change needed -- the spec is explicit about the static response. The "Out of Scope" section could optionally note that a real health check (Spring Boot Actuator) is deferred.

---

## Low Severity Findings

### Finding L1: Spec claims "Empty static/ directory" exists -- verified correct

**Spec Reference**: Reusable Components table -- "Empty static/ directory ... Target for Vite build output"

**Evidence**:
- `src/main/resources/static/` exists and is empty (verified via `ls`)
- Claim is accurate

**Category**: Verified correct

**Severity**: Low (informational) -- The spec's claim is accurate. However, Spring Boot serves `index.html` from `classpath:/static/` by default. If the directory contains only Vite build output and the implementer runs `./mvnw spring-boot:run` before running the Vite build, there will be no `index.html` and the SPA forwarding controller will forward to a 404. The spec does not mention this expected development sequence dependency.

**Recommendation**: The spec's implementation order (Step 4 frontend before Step 5 Maven integration) handles this implicitly. No change needed.

### Finding L2: .gitignore additions may be incomplete

**Spec Reference**: Requirement 11 -- "Append src/main/resources/static/, src/main/frontend/node/, src/main/frontend/node_modules/"

**Evidence**:
- Current `.gitignore` (verified): Standard Spring Boot patterns only, no frontend entries
- Spec lists three patterns to add

**Category**: Incomplete

**Severity**: Low -- The Vite scaffold also creates `.vite/` cache directory inside the frontend folder. The spec does not mention ignoring `src/main/frontend/.vite/`. Additionally, `package-lock.json` is not mentioned -- it should be committed (not ignored) for reproducible builds, but the spec is silent on this.

**Recommendation**: Add `src/main/frontend/.vite/` to the gitignore list. Confirm that `package-lock.json` should be committed (standard practice, not ignored).

### Finding L3: Testing approach is underspecified

**Spec Reference**: Implementation Guidance > Testing Approach -- "New tests should verify: health endpoint returns correct response, PluginRegistry initializes with empty list"

**Evidence**:
- `implementation/spec.md` lines 103-106: Mentions 2-8 tests but only specifies two test scenarios
- No guidance on test class structure (integration test vs unit test, MockMvc vs full context)

**Category**: Incomplete

**Severity**: Low -- For a skeleton, the existing `AjApplicationTests.contextLoads()` plus 1-2 new tests is sufficient. The spec leaves test implementation details to the implementer, which is appropriate for a skeleton. The success criteria (section at end) are the real verification points.

**Recommendation**: No change needed. The success criteria are clear and testable.

---

## Clarification Needed

### Clarification C1: PluginRegistry.getExtensions() -- is this acceptable under minimal-implementation standard?

**Spec Reference**: Standards Compliance table -- "PluginRegistry.getExtensions() has placeholder implementation -- justified because it defines the interface contract for future use"

**Evidence**:
- `implementation/spec.md` line 112: Explicitly calls out the placeholder
- `.maister/docs/standards/global/minimal-implementation.md` lines 10-11: "No Future Stubs: Avoid empty methods, placeholder functions, or interfaces 'for future extensibility'"
- `research/outputs/decision-log.md` ADR-003 consequences (line 119): Lists this as a known trade-off
- The method returns `List.of()` and is never called in the skeleton

**Question**: The spec acknowledges this tension with the minimal-implementation standard and provides justification ("defines the interface contract"). Is the stakeholder satisfied with this justification? The method IS a future stub that returns an empty list and has no callers. The counterargument (from the spec) is that the interface contract IS the deliverable for the microkernel architecture. Both interpretations are valid.

**Impact if not clarified**: Low -- either way, the method is trivial. If removed, it can be added later when needed. If kept, it adds one unused method.

### Clarification C2: Should application.properties remain truly unchanged?

**Spec Reference**: Reusable Components table -- "application.properties ... No changes needed; spring-boot-docker-compose auto-configures datasource"

**Evidence**:
- Current `application.properties` contains only `spring.application.name=aj`
- `analysis/gap-analysis.md` line 143: "If Liquibase requires explicit changelog path configuration, add spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml"
- Spring Boot's default Liquibase changelog path IS `classpath:db/changelog/db.changelog-master.yaml` (matches the spec's file location)

**Question**: The gap analysis raises a conditional about Liquibase needing an explicit changelog path property. Since the spec places the changelog at the default path (`db/changelog/db.changelog-master.yaml`), no property should be needed. Can the stakeholder confirm that no `application.properties` changes are required? The spec says "no changes" and this appears correct given the default path convention.

**Impact if not clarified**: Low -- if Liquibase fails to find the changelog, the error message will be obvious and the fix is a single property line.

---

## Extra Features (Not in Original Requirements but in Spec)

None identified. The spec is well-scoped and does not add features beyond what the requirements document specifies.

---

## Verification of Spec Claims Against Codebase

| Spec Claim | Verified | Evidence |
|---|---|---|
| `AjApplication.java` exists with `@SpringBootApplication` | Yes | `src/main/java/pl/devstyle/aj/AjApplication.java` line 6 |
| TestContainers uses `postgres:latest` | Yes | `TestcontainersConfiguration.java` line 15: `DockerImageName.parse("postgres:latest")` |
| `TestAjApplication.java` exists and works as dev runner | Yes | `src/test/java/pl/devstyle/aj/TestAjApplication.java` -- uses `SpringApplication.from().with()` pattern |
| `AjApplicationTests.java` has `contextLoads()` test | Yes | `src/test/java/pl/devstyle/aj/AjApplicationTests.java` lines 9-11 |
| `static/` directory exists and is empty | Yes | Verified via `ls` |
| `db/changelog/` directory exists and is empty | Yes | Verified via `ls` |
| `application.properties` only has app name | Yes | Contains `spring.application.name=aj` only |
| No Docker Compose file exists | Yes | `ls` of project root shows no `compose.yml` or `docker-compose.yml` |
| No `core/` or `api/` packages exist | Yes | Only `AjApplication.java` in `pl.devstyle.aj` package |
| No frontend code exists | Yes | No `src/main/frontend/` directory |
| pom.xml has no `spring-boot-docker-compose` dependency | Yes | Verified in `pom.xml` -- not present |
| pom.xml has no `frontend-maven-plugin` | Yes | Only `spring-boot-maven-plugin` in build plugins |
| Spring Boot version is 4.0.5 | Yes | `pom.xml` line 8: `<version>4.0.5</version>` |
| Java version is 25 | Yes | `pom.xml` line 30: `<java.version>25</java.version>` |
| Maven Wrapper exists | Yes | `mvnw` and `mvnw.cmd` in project root |

All 15 codebase claims verified as accurate.

---

## Success Criteria Testability Assessment

| # | Criterion | Objectively Testable | Notes |
|---|---|---|---|
| 1 | `./mvnw spring-boot:run` provisions PostgreSQL, runs Liquibase, starts without errors | Yes | Run command, check exit code and logs |
| 2 | `GET /api/health` returns `{"status":"UP"}` with HTTP 200 | Yes | `curl` command with status code check |
| 3 | React app renders and displays health check response | Mostly | Dev mode (port 5173) is testable; prod mode (port 8080) is testable; "displays the health check response" requires visual or DOM verification |
| 4 | `./mvnw clean package` produces single JAR | Yes | Check file exists at `target/aj-0.0.1-SNAPSHOT.jar` |
| 5 | `./mvnw test` passes with TestContainers | Yes | Run command, check exit code |
| 6 | PluginRegistry bean initializes with empty list | Yes | Integration test with `@Autowired` + assertion |
| 7 | SPA forwarding works for non-API, non-file paths | Yes | `curl` to `/some-path`, check response contains index.html content |

All success criteria are objectively verifiable.

---

## Recommendations

1. **Proceed with implementation** -- The spec is clear, complete, and implementable. No critical issues found.
2. **Use postgres:18 as specified** -- Ignore the postgres:17 references in the gap analysis and research report. The scope clarification explicitly overrides to 18.
3. **Decide on getExtensions() placeholder** (Clarification C1) -- Either keep it with the stated justification or remove it. Both are defensible.
4. **Add `src/main/frontend/.vite/` to .gitignore** during implementation (Finding L2).
5. **No need to modify application.properties** (Clarification C2) -- The default Liquibase path matches the spec's file location.
