# Implementation Plan: Microkernel Skeleton Application

## Overview
Total Steps: 24
Task Groups: 5
Expected Tests: 16-26

## Implementation Steps

### Task Group 1: Database Foundation
**Dependencies:** None
**Estimated Steps:** 5

- [x] 1.0 Complete database foundation layer
  - [x] 1.1 Write 3 focused tests for database foundation
    - Test that application context loads successfully (leverage existing `AjApplicationTests.contextLoads()` -- it should pass once Liquibase changelog exists)
    - Test that Liquibase runs without errors on startup (context load implicitly tests this)
    - Test that `DataSource` bean is present and configured via TestContainers `@ServiceConnection`
  - [x] 1.2 Create `compose.yml` at project root
    - PostgreSQL 18 image (user override from research's postgres:17)
    - Database `aj`, user `aj`, password `aj`
    - Named volume `postgres-data` for persistence
    - Health check: `pg_isready -U aj -d aj`
    - Follow research code sample closely
  - [x] 1.3 Add `spring-boot-docker-compose` dependency to `pom.xml`
    - Add as `<optional>true</optional>` scope
    - No version tag -- managed by Spring Boot parent BOM
    - Insert in dependencies section after existing starters
  - [x] 1.4 Create `src/main/resources/db/changelog/db.changelog-master.yaml`
    - Content: `databaseChangeLog: []` (empty YAML changelog)
    - Liquibase auto-discovers at default path `db/changelog/db.changelog-master.yaml`
  - [x] 1.5 Pin TestContainers PostgreSQL to `postgres:18` in `TestcontainersConfiguration.java`
    - Change `DockerImageName.parse("postgres:latest")` to `DockerImageName.parse("postgres:18")`
    - Ensures dev/test parity with Docker Compose
  - [x] 1.6 Ensure database foundation tests pass
    - Run ONLY: `./mvnw test -pl . -Dtest="AjApplicationTests"` (or equivalent targeted test)
    - Verify context loads, Liquibase runs, DataSource configured

**Acceptance Criteria:**
- The 3 tests pass (context load, Liquibase, DataSource)
- `compose.yml` exists at project root with PostgreSQL 18
- `db.changelog-master.yaml` exists with empty changelog
- `pom.xml` has `spring-boot-docker-compose` dependency
- TestContainers pinned to `postgres:18`

---

### Task Group 2: Microkernel Core Interfaces
**Dependencies:** Group 1
**Estimated Steps:** 5

- [x] 2.0 Complete microkernel core interfaces
  - [x] 2.1 Write 4 focused tests for microkernel core
    - Test that `PluginRegistry` bean is created in Spring context
    - Test that `PluginRegistry.getPlugins()` returns empty unmodifiable list when no plugins exist
    - Test that `PluginRegistry.findById("nonexistent")` returns `Optional.empty()`
    - Test that `PluginRegistry.getExtensions(ExtensionPoint.class)` returns empty list
  - [x] 2.2 Create `ExtensionPoint.java` in `pl.devstyle.aj.core.plugin`
    - Marker interface with no methods
    - Package: `src/main/java/pl/devstyle/aj/core/plugin/`
    - Follow research code sample exactly
  - [x] 2.3 Create `PluginDescriptor.java` and `Plugin.java` in same package
    - `PluginDescriptor`: interface with `getPluginId()`, `getName()`, `getVersion()`
    - `Plugin`: interface extending `PluginDescriptor` with default `onStart()`/`onStop()` methods
    - Follow research code samples exactly
  - [x] 2.4 Create `PluginRegistry.java` in same package
    - `@Component` annotation
    - Constructor injection of `List<Plugin>` (Spring injects empty list when no beans match)
    - `getPlugins()` returns `Collections.unmodifiableList(plugins)`
    - `findById(String)` streams and filters by plugin ID
    - `getExtensions(Class<T>)` returns `List.of()` (placeholder per spec)
    - Follow research code sample exactly
  - [x] 2.5 Ensure microkernel core tests pass
    - Run ONLY the 4 tests written in 2.1
    - Do NOT run entire test suite

**Acceptance Criteria:**
- The 4 tests pass
- All 4 interfaces/classes exist in `pl.devstyle.aj.core.plugin`
- `PluginRegistry` is a Spring `@Component` with empty plugin list
- No compile errors or Spring context failures

---

### Task Group 3: Backend API Layer
**Dependencies:** Group 1, Group 2
**Estimated Steps:** 4

- [x] 3.0 Complete backend API layer
  - [x] 3.1 Write 4 focused tests for API layer
    - Test `GET /api/health` returns HTTP 200 with `{"status":"UP"}`
    - Test `GET /api/health` content type is `application/json`
    - Test SPA forward: `GET /some-path` returns forward to `index.html` (or 200 if static resource exists)
    - Test that API paths (`/api/*`) take precedence over SPA forwarding
  - [x] 3.2 Create `HealthController.java` in `pl.devstyle.aj.api`
    - `@RestController` with `@RequestMapping("/api")`
    - `@GetMapping("/health")` returning `Map.of("status", "UP")`
    - Package: `src/main/java/pl/devstyle/aj/api/`
    - Follow research code sample exactly
  - [x] 3.3 Create `SpaForwardController.java` in same package
    - `@Controller` (not `@RestController`)
    - Two handler methods:
      - `@GetMapping("/{path:[^\\.]*}")` -> `return "forward:/index.html"`
      - `@GetMapping("/**/{path:[^\\.]*}")` -> `return "forward:/index.html"`
    - Follow research code sample exactly
  - [x] 3.4 Ensure API layer tests pass
    - Run ONLY the 4 tests written in 3.1
    - Use `@WebMvcTest` or `MockMvc` for controller tests (no full context needed)
    - Do NOT run entire test suite

**Acceptance Criteria:**
- The 4 tests pass
- `GET /api/health` returns `{"status":"UP"}` with HTTP 200
- SPA forwarding works for non-API, non-file paths
- Both controllers exist in `pl.devstyle.aj.api`

---

### Task Group 4: React Frontend & Maven Build Integration
**Dependencies:** Group 3
**Estimated Steps:** 6

- [x] 4.0 Complete React frontend and Maven build integration
  - [x] 4.1 Write 2 focused tests for build integration
    - Test that `src/main/frontend/package.json` exists after scaffold (file existence check)
    - Test that `src/main/frontend/vite.config.ts` contains proxy configuration for `/api`
    - Note: these are verification checks run after the scaffold and modification steps
  - [x] 4.2 Scaffold React + Vite + TypeScript app
    - Run `npm create vite@latest frontend -- --template react-ts` in `src/main/` directory
    - This is a one-time scaffold operation generating the full Vite project structure
    - Expected Vite 6.x (exact version pinned in generated package-lock.json)
  - [x] 4.3 Modify `vite.config.ts` after scaffolding
    - Set `build.outDir` to `'../resources/static'`
    - Set `build.emptyOutDir` to `true`
    - Add `server.proxy` with `/api` target `http://localhost:8080` and `changeOrigin: true`
    - Follow research code sample exactly
  - [x] 4.4 Modify `App.tsx` to display health check
    - Replace default Vite content with a component that:
      - Calls `GET /api/health` on mount (using `fetch` and `useEffect`)
      - Displays the raw JSON response on the page
    - Keep it minimal -- skeleton only, no styling library
  - [x] 4.5 Add `frontend-maven-plugin` to `pom.xml`
    - Plugin version: `1.15.1`
    - `workingDirectory`: `src/main/frontend`
    - `nodeVersion`: `v22.14.0`
    - Three executions in `generate-resources` phase:
      1. `install-node-and-npm` goal
      2. `npm` goal with `install` argument
      3. `npm` goal with `run build` argument
    - Follow research code sample exactly
  - [x] 4.6 Update `.gitignore` with frontend patterns
    - Append: `src/main/resources/static/`
    - Append: `src/main/frontend/node/`
    - Append: `src/main/frontend/node_modules/`
    - Append: `src/main/frontend/.vite/`
  - [x] 4.7 Ensure frontend build integration works
    - Run `npm install && npm run build` in `src/main/frontend/` to verify Vite outputs to `resources/static/`
    - Verify `index.html` exists in `src/main/resources/static/`
    - Run the 2 verification checks from 4.1

**Acceptance Criteria:**
- The 2 verification checks pass
- React app scaffolded in `src/main/frontend/`
- `vite.config.ts` has correct `outDir` and proxy config
- `App.tsx` calls `/api/health` and displays response
- `frontend-maven-plugin` configured in `pom.xml`
- `.gitignore` updated with all 4 frontend patterns
- `npm run build` produces output in `src/main/resources/static/`

---

### Task Group 5: Test Review & Gap Analysis
**Dependencies:** Groups 1, 2, 3, 4
**Estimated Steps:** 4

- [x] 5.0 Review and fill critical gaps
  - [x] 5.1 Review tests from previous groups (13 existing tests)
    - Group 1: 3 tests (context load, Liquibase, DataSource)
    - Group 2: 4 tests (PluginRegistry bean, empty list, findById, getExtensions)
    - Group 3: 4 tests (health 200, content-type, SPA forward, API precedence)
    - Group 4: 2 tests (package.json exists, vite.config proxy)
  - [x] 5.2 Analyze gaps for THIS feature only
    - Check: full Maven build lifecycle (`./mvnw clean package`) produces working JAR
    - Check: all success criteria from spec are covered
    - Check: integration between layers (frontend calls backend, backend reaches database)
  - [x] 5.3 Write up to 8 additional strategic tests
    - Full Maven build test: verify `./mvnw clean package` succeeds (may be manual verification)
    - Integration test: verify `PluginRegistry` is wired in full application context
    - Verify `HealthController` returns correct JSON structure in integration context
    - Verify SPA controller does not intercept `/api/*` paths
    - Verify static resources are served (if `resources/static/` has content)
    - Additional tests as gaps dictate (up to 8 max)
  - [x] 5.4 Run feature-specific tests only (expect 16-21 total)
    - Run all tests in `pl.devstyle.aj` package
    - Command: `./mvnw test`
    - All tests must pass

**Acceptance Criteria:**
- All feature tests pass (16-21 total)
- No more than 8 additional tests added
- All 7 success criteria from spec verified by at least one test or manual check
- Maven build produces `target/aj-0.0.1-SNAPSHOT.jar`

---

## Execution Order

1. **Group 1: Database Foundation** (6 steps) -- no dependencies, establishes data layer
2. **Group 2: Microkernel Core Interfaces** (5 steps, depends on 1) -- architectural foundation
3. **Group 3: Backend API Layer** (4 steps, depends on 1, 2) -- REST endpoints and SPA routing
4. **Group 4: React Frontend & Maven Build Integration** (7 steps, depends on 3) -- UI layer and build pipeline
5. **Group 5: Test Review & Gap Analysis** (4 steps, depends on 1, 2, 3, 4) -- final validation

## Standards Compliance

Follow standards from `.maister/docs/standards/`:
- `global/minimal-implementation.md` -- Build only what is needed; `PluginRegistry.getExtensions()` placeholder is justified as interface contract
- `global/coding-style.md` -- PascalCase classes, camelCase methods, lowercase packages
- `global/conventions.md` -- Predictable file structure, clean version control
- `global/commenting.md` -- Let code speak; comment only non-obvious logic
- `backend/api.md` -- All REST endpoints under `/api/` prefix
- `backend/migrations.md` -- Empty master changelog follows standard

## Notes

- **Test-Driven**: Each group starts with tests before implementation
- **Run Incrementally**: Only new tests after each group, full suite only in Group 5
- **Mark Progress**: Check off steps as completed in this file
- **Reuse First**: Leverage existing `AjApplicationTests`, `TestcontainersConfiguration`, `AjApplication` -- no changes to entry point needed
- **Research Samples**: Implementation should follow research report code samples closely (validated in research phase)
- **PostgreSQL 18**: User override from research's postgres:17 -- applies to both `compose.yml` and `TestcontainersConfiguration.java`
- **Frontend Scaffold**: `npm create vite@latest` is a one-time operation; generated files (vite.config.ts, App.tsx) are then modified
- **pom.xml Gets Two Changes**: `spring-boot-docker-compose` dependency (Group 1) AND `frontend-maven-plugin` plugin (Group 4) -- split across groups matching their dependency chain
