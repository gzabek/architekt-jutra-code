# Gap Analysis: Microkernel Skeleton Application

## Summary
- **Risk Level**: Low
- **Estimated Effort**: Medium (2-4 hours implementation)
- **Detected Characteristics**: creates_new_entities, modifies_existing_code, ui_heavy

## Task Characteristics
- Has reproducible defect: **no** (greenfield skeleton work)
- Modifies existing code: **yes** (pom.xml, .gitignore, optionally TestcontainersConfiguration)
- Creates new entities: **yes** (microkernel interfaces, controllers, React app, Docker Compose, Liquibase changelog -- but NOT JPA entities)
- Involves data operations: **no** (empty Liquibase changelog, no domain entities, no CRUD)
- UI heavy: **yes** (React + Vite + TypeScript frontend scaffold with health check display)

## Gaps Identified

### Files to Create (9 new files/directories)

| # | File | Purpose | Evidence of Gap |
|---|------|---------|-----------------|
| 1 | `compose.yml` | Docker Compose PostgreSQL 18 service | No Docker files exist in project root |
| 2 | `src/main/java/pl/devstyle/aj/core/plugin/ExtensionPoint.java` | Extension point marker interface | No `core/` package exists |
| 3 | `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptor.java` | Plugin metadata interface | No `core/` package exists |
| 4 | `src/main/java/pl/devstyle/aj/core/plugin/Plugin.java` | Plugin lifecycle interface | No `core/` package exists |
| 5 | `src/main/java/pl/devstyle/aj/core/plugin/PluginRegistry.java` | Spring-managed plugin registry | No `core/` package exists |
| 6 | `src/main/java/pl/devstyle/aj/api/HealthController.java` | `GET /api/health` endpoint | No `api/` package exists, no controllers |
| 7 | `src/main/java/pl/devstyle/aj/api/SpaForwardController.java` | Forward non-API paths to React | No SPA routing exists |
| 8 | `src/main/frontend/` (entire directory) | React + Vite + TypeScript app | No frontend code exists |
| 9 | `src/main/resources/db/changelog/db.changelog-master.yaml` | Empty Liquibase master changelog | Directory exists but is empty; Liquibase will fail on startup without this |

### Files to Modify (3 modifications)

| # | File | Current State | Required Change |
|---|------|---------------|-----------------|
| 1 | `pom.xml` | Has Spring Boot starters, no Docker Compose support, no frontend build | Add `spring-boot-docker-compose` dependency (optional); add `frontend-maven-plugin` with Node 22.14.0, npm install, npm build executions |
| 2 | `.gitignore` | Standard Spring Boot patterns only | Add: `src/main/resources/static/`, `src/main/frontend/node/`, `src/main/frontend/node_modules/` |
| 3 | `src/main/frontend/vite.config.ts` | Does not exist yet (created during scaffolding) | After Vite scaffold: set `build.outDir` to `../resources/static`, add `/api` proxy to `localhost:8080` |

### Files Unchanged (5 files)

| File | Reason |
|------|--------|
| `AjApplication.java` | `@SpringBootApplication` entry point is complete |
| `application.properties` | Docker Compose auto-configures datasource; no manual properties needed |
| `AjApplicationTests.java` | Context load test remains valid |
| `TestAjApplication.java` | Dev runner with TestContainers works as-is |
| `TestcontainersConfiguration.java` | Functional as-is (optional: pin `postgres:latest` to `postgres:17`) |

## Existing Feature Analysis (modifies_existing_code)

### Change Type: Additive

All modifications to existing files are strictly additive -- no existing behavior is changed or removed.

- **pom.xml**: Adding new dependencies and a new plugin; existing dependencies and spring-boot-maven-plugin untouched
- **.gitignore**: Appending new patterns; existing patterns untouched
- **TestcontainersConfiguration**: Optional version pin from `postgres:latest` to `postgres:17`; behavior unchanged

### Compatibility Requirements: Flexible

No breaking changes. All existing tests will continue to pass. The application currently cannot start (no datasource configured, no Liquibase changelog), so there is no existing runtime behavior to break.

## New Capability Analysis (creates_new_entities)

### Integration Points

| New Component | Integrates With | Mechanism |
|---------------|----------------|-----------|
| `compose.yml` | Spring Boot startup | `spring-boot-docker-compose` auto-discovers `compose.yml` at project root |
| `PluginRegistry` | Spring context | `@Component` annotation, constructor injection of `List<Plugin>` |
| `HealthController` | Spring WebMVC | `@RestController` + `@RequestMapping("/api")` |
| `SpaForwardController` | Spring WebMVC + static resources | `@Controller` + `forward:/index.html` |
| React app (built) | Spring Boot static serving | Vite outputs to `src/main/resources/static/`, served by default static handler |
| React app (dev) | Spring Boot API | Vite proxy forwards `/api/*` to `localhost:8080` |
| `db.changelog-master.yaml` | Liquibase auto-config | Spring Boot discovers changelog at default path `db/changelog/db.changelog-master.yaml` |
| `frontend-maven-plugin` | Maven lifecycle | Executes during `generate-resources` phase, before Java compilation |

### Patterns to Follow

| Pattern | Source | Apply To |
|---------|--------|----------|
| Package structure `pl.devstyle.aj.*` | `AjApplication.java` | New packages: `core.plugin`, `api` |
| Spring Boot conventions | Existing project setup | Controllers, components |
| TestContainers `@ServiceConnection` | `TestcontainersConfiguration.java` | Docker Compose uses same abstraction |

### Architectural Impact: Medium

- **4 new Java source files** in 2 new packages (`core.plugin`, `api`)
- **1 new infrastructure file** (`compose.yml`) at project root
- **1 new resource file** (`db.changelog-master.yaml`)
- **Entire frontend directory** (`src/main/frontend/`) -- largest addition by file count
- **Build process changes** -- Maven now includes Node.js download and frontend build in `generate-resources` phase, adding ~30-60 seconds to clean builds

## UI Impact Analysis (ui_heavy)

### Navigation Paths

The skeleton frontend has a single path:

| Path | Component | Purpose |
|------|-----------|---------|
| `/` | `App.tsx` | Landing page displaying `/api/health` response |

### Discoverability Score: 9/10

The health check result is the only content on the landing page. It is immediately visible upon opening the application. No navigation required.

### Persona Impact

Single persona (developer) for the skeleton. The frontend exists solely to prove the full stack works end-to-end.

### Frontend Scope

The React app in this phase is minimal:
- Default Vite scaffold with `App.tsx` modified to call `/api/health` and display the result
- No routing library, state management, or component library
- Frontend features explicitly deferred to next step per task description

## Issues Requiring Decisions

### Critical (Must Decide Before Proceeding)

None. The research phase has already resolved all architectural decisions via 5 ADRs.

### Important (Should Decide)

1. **TestContainers PostgreSQL version pinning**
   - Currently `postgres:latest`, research recommends `postgres:17` for consistency with Docker Compose
   - Options: [Pin to `postgres:17`] [Keep `postgres:latest`]
   - Default: Pin to `postgres:17`
   - Rationale: Dev (Docker Compose) and test (TestContainers) environments should use the same PostgreSQL version to avoid subtle behavior differences

2. **Liquibase changelog format**
   - Research recommends YAML (`db.changelog-master.yaml`)
   - Options: [YAML] [XML] [SQL]
   - Default: YAML
   - Rationale: YAML is more concise; XML is more traditional in Java projects. Both are fully supported. The research already recommends YAML.

## Recommendations

1. **Follow the 5-step implementation order from research**: Database foundation -> Microkernel interfaces -> API layer -> Frontend scaffold -> Maven build integration. Each step has a clear verification point.
2. **Pin TestContainers to `postgres:17`** for dev/test parity.
3. **Verify `application.properties` needs no changes** -- the `spring-boot-docker-compose` module should auto-configure the datasource without manual properties. If Liquibase requires explicit changelog path configuration, add `spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml`.

## Risk Assessment

- **Complexity Risk**: **Low**. All components are well-documented, standard patterns. Research confidence is 90-95%.
- **Integration Risk**: **Low-Medium**. The main integration risk is `frontend-maven-plugin` compatibility with Maven 3.9.14 / Java 25, but the plugin operates at Maven level, not Java compile level. Fallback: `exec-maven-plugin`.
- **Regression Risk**: **None**. The application currently cannot start (no datasource, no Liquibase changelog). All changes are additive. Existing tests should continue to pass once the Liquibase changelog exists.
