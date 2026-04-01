# Specification: Microkernel Skeleton Application

## Goal

Build a working skeleton that proves all technology layers integrate end-to-end -- Spring Boot backend, React frontend, containerized PostgreSQL, and microkernel plugin interfaces -- unblocking all future feature development by establishing the platform foundation.

## User Stories

1. As a developer, I want to run `./mvnw spring-boot:run` and have PostgreSQL auto-provisioned via Docker Compose so that I can start working without manual database setup.
2. As a developer, I want microkernel plugin interfaces defined so that I can implement plugins using Spring DI without additional libraries.
3. As a developer, I want a health endpoint at `/api/health` so that I can verify the backend is running.
4. As a developer, I want a React frontend that calls `/api/health` and displays the result so that I can verify the full stack works end-to-end.
5. As a developer, I want `./mvnw clean package` to produce a single JAR serving both frontend and API so that deployment is a single artifact.

## Core Requirements

### Backend

1. **Docker Compose PostgreSQL**: `compose.yml` at project root with `postgres:18` image, database `aj`, user `aj`, password `aj`, named volume for persistence, health check. Add `spring-boot-docker-compose` dependency (optional scope) to pom.xml.
2. **Microkernel core interfaces**: Four files in `pl.devstyle.aj.core.plugin` package:
   - `ExtensionPoint` -- marker interface (no methods)
   - `PluginDescriptor` -- metadata contract (`getPluginId()`, `getName()`, `getVersion()`)
   - `Plugin` -- lifecycle contract extending `PluginDescriptor` with default `onStart()`/`onStop()` methods
   - `PluginRegistry` -- `@Component` with constructor injection of `List<Plugin>`, providing `getPlugins()`, `findById(String)`, `getExtensions(Class)`. Note: Spring Boot injects an empty `List<T>` when no matching beans exist — this is expected behavior and the registry starts empty in the skeleton.
3. **Health smoke test endpoint**: `HealthController` at `GET /api/health` returning `{"status":"UP"}` as `Map<String, String>`. Note: this is a static connectivity smoke test, not a production health check (Spring Boot Actuator is deferred).
4. **SPA forwarding**: `SpaForwardController` with two handler methods: `/{path:[^\\.]*}` (single segment) and `/**/{path:[^\\.]*}` (nested segments), both forwarding to `forward:/index.html`. Spring Boot auto-serves `/` from `classpath:/static/index.html` by default, so the SPA controller only handles deep-link paths. `@RestController` endpoints take precedence.
5. **Liquibase master changelog**: `db.changelog-master.yaml` with `databaseChangeLog: []` (empty) to prevent startup errors

### Frontend

6. **React + Vite + TypeScript**: Scaffold via `npm create vite@latest` with `react-ts` template in `src/main/frontend/` (expected Vite 6.x; exact version pinned in generated package-lock.json after scaffold)
7. **Vite configuration**: `build.outDir` set to `../resources/static`, `build.emptyOutDir` enabled, `/api` proxy to `localhost:8080`
8. **Landing page**: Modify `App.tsx` to call `GET /api/health` on mount and display the raw JSON response
9. **Maven build integration**: `frontend-maven-plugin` v1.15.1 in pom.xml with Node v22.14.0, three executions in `generate-resources` phase: install-node-and-npm, npm install, npm run build

### Modifications to Existing Files

10. **pom.xml**: Add `spring-boot-docker-compose` dependency (optional) and `frontend-maven-plugin` plugin with configuration
11. **.gitignore**: Append `src/main/resources/static/`, `src/main/frontend/node/`, `src/main/frontend/node_modules/`, `src/main/frontend/.vite/`
12. **TestcontainersConfiguration.java**: Pin image from `postgres:latest` to `postgres:18` for dev/test parity

## Reusable Components

### Existing Code to Leverage

| Component | File Path | How to Leverage |
|-----------|-----------|-----------------|
| Spring Boot bootstrap | `src/main/java/pl/devstyle/aj/AjApplication.java` | No changes needed; `@SpringBootApplication` auto-scans new packages under `pl.devstyle.aj` |
| TestContainers `@ServiceConnection` pattern | `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java` | Same `@ServiceConnection` abstraction used by Docker Compose module; only change is image version pin to `postgres:18` |
| TestContainers dev runner | `src/test/java/pl/devstyle/aj/TestAjApplication.java` | No changes needed; continues to work as alternative ephemeral dev workflow |
| Context loading test | `src/test/java/pl/devstyle/aj/AjApplicationTests.java` | No changes needed; validates that new components wire correctly |
| Maven build config | `pom.xml` | Spring Boot parent BOM manages all dependency versions; new dependencies should NOT specify version tags |
| Empty `static/` directory | `src/main/resources/static/` | Target for Vite build output; Spring Boot auto-serves from this path |
| Empty `db/changelog/` directory | `src/main/resources/db/changelog/` | Target for Liquibase master changelog |
| Application properties | `src/main/resources/application.properties` | No changes needed; `spring-boot-docker-compose` auto-configures datasource |

### New Components Required

| Component | Justification |
|-----------|---------------|
| `compose.yml` | No Docker Compose file exists; needed for development database provisioning |
| `ExtensionPoint.java` | No microkernel interfaces exist; architectural foundation for plugin system |
| `PluginDescriptor.java` | No microkernel interfaces exist; defines plugin metadata contract |
| `Plugin.java` | No microkernel interfaces exist; defines plugin lifecycle contract |
| `PluginRegistry.java` | No microkernel interfaces exist; Spring-managed registry for auto-discovery |
| `HealthController.java` | No controllers exist; end-to-end smoke test endpoint |
| `SpaForwardController.java` | No SPA support exists; required for React Router to work in production JAR |
| `src/main/frontend/` (React scaffold) | No frontend code exists; required for the UI layer |
| `db.changelog-master.yaml` | No changelog exists; Liquibase will fail on startup without it |

## Technical Approach

### Architecture

Single Maven module monorepo with embedded React frontend (ADR-001). Microkernel interfaces use Spring DI-based Strategy/Registry pattern with zero additional libraries (ADR-003). Database provisioned via Docker Compose for development (ADR-004) and TestContainers for tests.

### Integration Strategy

- **Docker Compose integration**: `spring-boot-docker-compose` auto-discovers `compose.yml` at project root, starts PostgreSQL, and configures the datasource via `ServiceConnection`. Automatically skipped during tests (`spring.docker.compose.skip.in-tests=true` default).
- **Frontend build integration**: `frontend-maven-plugin` downloads Node locally during `generate-resources` phase, runs `npm install` and `npm run build`. Vite outputs static assets to `src/main/resources/static/`. Spring Boot serves these from `classpath:/static/` by default.
- **SPA + API coexistence**: `@RestController` endpoints under `/api/` take precedence over SPA forwarding. Paths with file extensions (`.js`, `.css`, `.svg`) serve as static files. All other paths forward to `index.html`.
- **Plugin auto-discovery**: `PluginRegistry` uses `List<Plugin>` constructor injection. Spring DI automatically injects all `@Component` beans implementing `Plugin`. Starts empty in skeleton.

### Data Flow

**Development (two-server mode)**: Browser -> Vite (5173) -> proxy `/api/*` -> Spring Boot (8080) -> PostgreSQL (5432, Docker Compose)

**Production (single JAR)**: Browser -> Spring Boot (8080) -> static files from `classpath:/static/` OR API controllers OR SPA forward to `index.html`

### Key Constraints

- PostgreSQL version 18 in both Docker Compose and TestContainers
- Liquibase changelog format: YAML
- Node version: 22.14.0 (pinned in frontend-maven-plugin)
- No explicit `spring.datasource.*` properties -- auto-configured
- Follow research code samples closely (validated in research phase)

## Implementation Guidance

### Testing Approach

- 2-8 focused tests per implementation step group
- Test verification runs only new tests, not entire suite
- Existing `AjApplicationTests.contextLoads()` serves as integration smoke test -- should pass after Liquibase changelog is added
- New tests should verify: health endpoint returns correct response, PluginRegistry initializes with empty list
- Frontend testing deferred (skeleton only)

### Standards Compliance

| Standard | Location | Relevance |
|----------|----------|-----------|
| Minimal Implementation | `.maister/docs/standards/global/minimal-implementation.md` | PluginRegistry.getExtensions() has placeholder implementation -- justified because it defines the interface contract for future use |
| Coding Style | `.maister/docs/standards/global/coding-style.md` | Follow naming conventions: PascalCase classes, camelCase methods, lowercase packages |
| API Design | `.maister/docs/standards/backend/api.md` | All REST endpoints under `/api/` prefix; RESTful naming |
| JPA Entity Modeling | `.maister/docs/standards/backend/models.md` | Not applicable in skeleton (no entities) |
| Database Migrations | `.maister/docs/standards/backend/migrations.md` | Empty master changelog follows standard; actual migrations added with first feature |

### Research Artifacts

The research report at `.maister/tasks/research/2026-03-28-microkernel-skeleton-app/outputs/research-report.md` contains validated code samples for all components. Implementation should follow these samples closely. The high-level design document provides architecture diagrams and data flow descriptions. The decision log contains 5 accepted ADRs (ADR-001 through ADR-005) that should not be re-decided.

## Out of Scope

- Authentication and authorization
- Business domain features, entities, or domain-specific extension points
- Production Dockerfile, Kubernetes manifests, CI/CD pipeline
- Lombok (add when first JPA entity is created)
- jOOQ code generation (configure when first complex query is needed)
- Spring profiles (add when environment-specific config is required)
- Frontend state management, routing library, or component library
- CORS configuration (not needed -- Vite proxy in dev, same-origin in prod)
- Plugin framework selection (PF4J/JPMS) -- deferred until runtime loading is needed
- Spring Boot Actuator health checks -- deferred; current /api/health is a static smoke test
- No changes to `application.properties` needed -- Liquibase default changelog path matches `db/changelog/db.changelog-master.yaml`

## Success Criteria

1. `./mvnw spring-boot:run` provisions PostgreSQL via Docker Compose, runs Liquibase, and starts without errors
2. `GET /api/health` returns `{"status":"UP"}` with HTTP 200
3. React app renders at `http://localhost:5173` (dev) or `http://localhost:8080` (prod) and displays the health check response
4. `./mvnw clean package` produces a single JAR at `target/aj-0.0.1-SNAPSHOT.jar` that serves both frontend and API
5. `./mvnw test` passes with TestContainers (Docker Compose auto-skipped)
6. `PluginRegistry` bean initializes with an empty plugin list (no injection errors)
7. SPA forwarding works: navigating to a non-API, non-file path serves `index.html`
