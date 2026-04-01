# High-Level Design: Microkernel Skeleton Application

## Design Overview

The **aj** project needs a working skeleton that proves all technology layers integrate end-to-end: Spring Boot backend, React frontend, containerized PostgreSQL, and microkernel plugin interfaces. No auth, no business features -- just the wiring. This unblocks all future feature development by establishing the platform foundation.

The chosen approach is a **single Maven module monorepo** with an embedded React frontend in `src/main/frontend/`. The backend uses **Spring DI-based plugin discovery** (Strategy/Registry pattern) to implement the microkernel architecture with zero additional libraries. PostgreSQL is provisioned automatically via **Docker Compose** with `spring-boot-docker-compose` for development and **TestContainers** for tests. The frontend is built with **Vite + React 19 + TypeScript** and integrated into the Maven build via **frontend-maven-plugin**, producing a single executable JAR that serves both API and UI.

**Key decisions:**
- Single Maven module with embedded frontend -- avoids multi-module restructuring, simplest viable structure (ADR-001)
- Vite + React 19 + TypeScript -- industry standard, CRA is deprecated, Vite provides fastest DX (ADR-002)
- Spring DI-based microkernel -- zero new dependencies, interfaces remain stable when upgrading to PF4J/JPMS later (ADR-003)
- Docker Compose for dev, TestContainers for tests -- persistent data for development, ephemeral isolation for tests (ADR-004)
- frontend-maven-plugin for build integration -- pinned Node version, reproducible builds, no global Node required (ADR-005)

---

## Architecture

### System Context (C4 Level 1)

Shows the skeleton application in its environment during development.

```
                                +---------------------+
                                |     Developer       |
                                | (Browser + IDE)     |
                                +----------+----------+
                                           |
                          +----------------+----------------+
                          |                                 |
                     HTTP (5173)                       HTTP (8080)
                     Dev only                          API calls
                          |                                 |
               +----------v----------+           +---------v---------+
               |   Vite Dev Server   |  /api/*   |                   |
               |   (port 5173)       +---------->+   aj Application  |
               |   React + HMR      |  proxy     |   (Spring Boot)   |
               +---------------------+           +---------+---------+
                                                           |
                                                      JDBC (5432)
                                                           |
                                                 +---------v---------+
                                                 |    PostgreSQL 18   |
                                                 |  (Docker Compose)  |
                                                 +--------------------+
```

**Actors and systems:**
- **Developer** -- interacts with Vite dev server (frontend) and Spring Boot (API) during development
- **Vite Dev Server** -- serves React app with HMR, proxies `/api/*` to Spring Boot (development only)
- **aj Application** -- Spring Boot 4.0.5 backend serving REST API and (in production) static frontend assets
- **PostgreSQL 18** -- relational database, auto-provisioned via Docker Compose in dev, TestContainers in tests

### Container Overview (C4 Level 2)

Shows the internal structure of the aj application and its runtime dependencies.

```
+===========================================================================+
|                           aj Application (JAR)                            |
|                                                                           |
|  +-------------------+    +-------------------+    +-------------------+  |
|  |    API Layer      |    | Microkernel Core  |    |  SPA Forward      |  |
|  |                   |    |                   |    |  Controller       |  |
|  | /api/health       |    | PluginRegistry    |    |                   |  |
|  | (REST endpoints)  |    | Plugin interface  |    | Forwards non-API  |  |
|  |                   |    | ExtensionPoint    |    | routes to React   |  |
|  +--------+----------+    +--------+----------+    +--------+----------+  |
|           |                        |                        |             |
|           +------------------------+------------------------+             |
|                                    |                                      |
|  +-------------------+    +--------v----------+    +-------------------+  |
|  | Static Resources  |    | Spring Boot       |    |  Liquibase        |  |
|  | (React build)     |    | Auto-Config       |    |  Migrations       |  |
|  |                   |    |                   |    |                   |  |
|  | index.html        |    | DataSource        |    | db.changelog-     |  |
|  | assets/*.js/css   |    | Docker Compose    |    | master.yaml       |  |
|  +-------------------+    +--------+----------+    +--------+----------+  |
|                                    |                        |             |
+===========================================================================+
                                     |                        |
                                JDBC (5432)              Liquibase
                                     |                   DDL/DML
                                     v                        |
                           +---------+---------+              |
                           |   PostgreSQL 18   |<-------------+
                           |                   |
                           |  Database: aj     |
                           |  (Docker Compose  |
                           |   or TestCont.)   |
                           +-------------------+

+===========================================================================+
|                        Build Toolchain (not runtime)                       |
|                                                                           |
|  +-------------------+    +-------------------+    +-------------------+  |
|  | Maven Wrapper     |    | frontend-maven-   |    | Vite + React 19  |  |
|  | (mvnw)            +--->+ plugin            +--->+ + TypeScript      |  |
|  |                   |    | (installs Node)   |    | (src/main/        |  |
|  | Orchestrates full |    |                   |    |  frontend/)       |  |
|  | build lifecycle   |    | npm install       |    |                   |  |
|  +-------------------+    | npm run build     |    | Output ->         |  |
|                           +-------------------+    | resources/static/ |  |
|                                                    +-------------------+  |
+===========================================================================+
```

**Container responsibilities:**
- **API Layer** -- REST controllers under `/api/` prefix; currently only `/api/health`
- **Microkernel Core** -- Plugin/ExtensionPoint interfaces and PluginRegistry; architectural foundation
- **SPA Forward Controller** -- Routes non-API, non-file requests to `index.html` for React Router
- **Static Resources** -- Vite-built React app served from `classpath:/static/`
- **Spring Boot Auto-Config** -- Wires DataSource from Docker Compose or TestContainers automatically
- **Liquibase Migrations** -- Schema management; empty master changelog for skeleton
- **PostgreSQL 18** -- Data persistence; Docker Compose (dev) or TestContainers (test)
- **Build Toolchain** -- Maven + frontend-maven-plugin + Vite; not present at runtime

---

## Key Components

| Component | Purpose | Responsibilities | Key Interfaces | Dependencies |
|-----------|---------|-----------------|----------------|--------------|
| **PluginRegistry** | Central registry for all discovered plugins | - Auto-collects `Plugin` beans via Spring DI<br>- Provides lookup by plugin ID<br>- Future: discovers `ExtensionPoint` implementations | `getPlugins()`, `findById(String)`, `getExtensions(Class)` | Spring DI, `Plugin` interface |
| **Plugin** | Contract for plugin lifecycle and identity | - Defines plugin ID, name, version<br>- Optional `onStart()`/`onStop()` lifecycle hooks | Extends `PluginDescriptor`; `onStart()`, `onStop()` | None (pure interface) |
| **ExtensionPoint** | Marker for extension point types | - Type-safe marker for all extension point interfaces<br>- Enables registry to discover extensions by type | Marker interface (no methods) | None (pure interface) |
| **HealthController** | Proves API layer works end-to-end | - Returns `{"status": "UP"}`<br>- Validates Spring Boot, database, and frontend integration | `GET /api/health` | Spring WebMVC |
| **SpaForwardController** | Enables client-side routing | - Forwards paths without file extensions to `index.html`<br>- Lets `@RestController` endpoints take precedence | `GET /{path}`, `GET /**/{path}` (non-dot paths) | Spring WebMVC, static resources |
| **React App** | Frontend user interface | - Rendered by Vite + React 19 + TypeScript<br>- Calls `/api/*` endpoints<br>- Built to `resources/static/` | HTTP calls to `/api/*` | Vite, React 19, TypeScript |
| **Docker Compose PostgreSQL** | Development database | - Auto-provisioned by `spring-boot-docker-compose`<br>- Persistent data via named volume<br>- Health-checked | JDBC on port 5432 | Docker, Docker Compose |
| **Liquibase** | Database schema management | - Runs migrations on application startup<br>- Empty master changelog in skeleton | `db.changelog-master.yaml` | PostgreSQL DataSource |

---

## Data Flow

### Development Flow (Two-Server Mode)

```
Developer Browser (localhost:5173)
        |
        |--- GET /  --->  Vite Dev Server (5173)  --->  React App (HMR)
        |
        |--- GET /api/health  --->  Vite Dev Server  --proxy-->  Spring Boot (8080)
        |                                                              |
        |                                                         HealthController
        |                                                              |
        |                                                         PostgreSQL (5432)
        |                                                        (Docker Compose)
        |
        |<-- JSON {"status":"UP"} <-- proxy <-- Spring Boot <-- DB query (if any)
```

1. Developer opens `http://localhost:5173` in browser
2. Vite serves React app with instant Hot Module Replacement
3. Frontend calls `/api/health` -- Vite proxy forwards to Spring Boot on port 8080
4. Spring Boot processes request, accesses PostgreSQL if needed
5. Response flows back through proxy to browser
6. Frontend changes trigger instant HMR; backend changes trigger Spring DevTools restart

### Production Flow (Single JAR)

```
User Browser (localhost:8080)
        |
        |--- GET /  --->  Spring Boot (8080)  --->  static/index.html (React)
        |
        |--- GET /dashboard  --->  SpaForwardController  --->  forward:/index.html
        |
        |--- GET /api/health  --->  HealthController  --->  {"status":"UP"}
        |
        |--- GET /assets/index-abc.js  --->  Static Resource Handler  --->  JS file
```

1. Single JAR serves both API and frontend on port 8080
2. Static file requests (paths with dots) served from `classpath:/static/`
3. API requests (`/api/**`) handled by `@RestController` beans
4. All other paths forwarded to `index.html` for React Router

### Build Flow

```
./mvnw clean package
        |
        +-- generate-resources phase:
        |       |
        |       +-- frontend-maven-plugin: install Node 22.14.0 (local)
        |       +-- frontend-maven-plugin: npm install
        |       +-- frontend-maven-plugin: npm run build
        |       |       |
        |       |       +-- Vite builds React -> src/main/resources/static/
        |       |
        +-- compile phase: javac compiles Java sources
        |
        +-- test phase: TestContainers spins up PostgreSQL, runs tests
        |
        +-- package phase: JAR bundles classes + static/ + configs
        |
        +-- Output: target/aj-0.0.1-SNAPSHOT.jar
```

### Database Provisioning Flow

```
Development:                         Testing:
./mvnw spring-boot:run               ./mvnw test
        |                                    |
Spring Boot discovers compose.yml    spring.docker.compose.skip.in-tests=true
        |                                    |
docker compose up (PostgreSQL 18)    TestContainers starts postgres:18
        |                                    |
ServiceConnection auto-created       @ServiceConnection auto-created
        |                                    |
Liquibase runs migrations            Liquibase runs migrations
        |                                    |
App ready (persistent data)          Tests run (ephemeral data)
```

---

## Integration Points

| Integration | From | To | Protocol | Notes |
|-------------|------|----|----------|-------|
| Frontend API calls | React App | Spring Boot API | HTTP (`/api/*`) | Vite proxy in dev; same-origin in prod |
| Database connection | Spring Boot | PostgreSQL | JDBC (port 5432) | Auto-configured via `ServiceConnection` |
| Docker Compose lifecycle | Spring Boot | Docker | Docker API | `spring-boot-docker-compose` manages container lifecycle |
| TestContainers lifecycle | Test runner | Docker | Docker API | `@ServiceConnection` on `PostgreSQLContainer` |
| Liquibase migrations | Spring Boot | PostgreSQL | JDBC | Auto-runs on startup via Spring Boot auto-config |
| Frontend build | Maven | Node/Vite | CLI (npm) | `frontend-maven-plugin` during `generate-resources` |
| Static resource serving | Spring Boot | Browser | HTTP | `classpath:/static/` default handler |
| SPA forwarding | SpaForwardController | Static resources | Internal forward | `forward:/index.html` for client-side routes |

---

## Design Decisions

| ADR | Decision | Rationale | Status |
|-----|----------|-----------|--------|
| [ADR-001](decision-log.md#adr-001-monorepo-structure) | Single Maven module with embedded frontend | Simplest structure; project already single-module; no team separation needs | Accepted |
| [ADR-002](decision-log.md#adr-002-frontend-tooling) | Vite + React 19 + TypeScript | CRA deprecated; Vite is industry standard; fastest DX | Accepted |
| [ADR-003](decision-log.md#adr-003-microkernel-pattern) | Spring DI-based plugin pattern | Zero dependencies; interfaces stable across future framework upgrades | Accepted |
| [ADR-004](decision-log.md#adr-004-database-development-setup) | Docker Compose (dev) + TestContainers (tests) | Persistent dev data; ephemeral test isolation; same Spring abstraction | Accepted |
| [ADR-005](decision-log.md#adr-005-maven-frontend-build-integration) | frontend-maven-plugin for build | Pinned Node version; reproducible; no global Node required | Accepted |

---

## Concrete Examples

### Example 1: Developer starts working on the project for the first time

**Given** a developer has cloned the repo and has JDK 25, Maven (via wrapper), and Docker installed
**When** they run `./mvnw spring-boot:run` in one terminal and `cd src/main/frontend && npm run dev` in another
**Then** Spring Boot auto-discovers `compose.yml`, starts PostgreSQL 18 via Docker Compose, runs Liquibase (empty changelog), and serves the API on port 8080. Vite serves the React app on port 5173 with HMR. Opening `http://localhost:5173` shows the React app, and the frontend can call `/api/health` through the Vite proxy, receiving `{"status":"UP"}`.

### Example 2: Adding the first plugin

**Given** the skeleton is running with empty PluginRegistry
**When** a developer creates a new class annotated with `@Component` that implements `Plugin` (providing `getPluginId()`, `getName()`, `getVersion()`)
**Then** Spring DI automatically injects it into `PluginRegistry`'s `List<Plugin>` constructor parameter. The registry's `getPlugins()` returns the new plugin. No configuration changes, no registration code, no additional dependencies needed.

### Example 3: Production build and deployment

**Given** the skeleton is complete with all components in place
**When** a developer runs `./mvnw clean package`
**Then** Maven executes: frontend-maven-plugin installs Node locally, runs `npm install`, runs `npm run build` (Vite outputs to `resources/static/`), Java compiles, tests run with TestContainers, and a single JAR is produced at `target/aj-0.0.1-SNAPSHOT.jar`. Running `java -jar target/aj-0.0.1-SNAPSHOT.jar` serves both the React frontend and the Spring Boot API on port 8080 from a single process.

---

## Out of Scope

The following are explicitly **not addressed** by this skeleton design:

- **Authentication and authorization** -- no auth mechanism; add when first secured feature is needed
- **Business domain features** -- no entities, no domain logic, no domain-specific extension points
- **Production deployment** -- no Dockerfile, no Kubernetes manifests, no CI/CD pipeline
- **JPA entities and jOOQ queries** -- dependencies are present but unused; add with first domain feature
- **Lombok** -- referenced in backend standards but not needed until first entity
- **jOOQ code generation** -- dependency present but codegen not configured; configure with first complex query
- **Spring profiles** -- not needed for skeleton; add when environment-specific config is required
- **CORS configuration** -- not needed (Vite proxy in dev, same-origin in prod)
- **Plugin framework selection (PF4J/JPMS)** -- deferred until runtime plugin loading is actually needed
- **Multi-tenant strategy** -- deferred; not relevant to skeleton
- **Frontend state management, routing library, or component library** -- default Vite scaffold is sufficient

---

## Success Criteria

1. **Application starts successfully** -- `./mvnw spring-boot:run` provisions PostgreSQL via Docker Compose, runs Liquibase, and starts without errors
2. **Health endpoint responds** -- `GET /api/health` returns `{"status":"UP"}` with HTTP 200
3. **React app renders** -- Opening `http://localhost:5173` (dev) or `http://localhost:8080` (prod) shows the React default page
4. **API proxy works** -- Frontend can call `/api/health` and receive a response (no CORS errors)
5. **Single JAR builds** -- `./mvnw clean package` produces a working JAR that serves both frontend and API
6. **Tests pass** -- `./mvnw test` runs with TestContainers (Docker Compose skipped) and passes
7. **Plugin registry initializes** -- `PluginRegistry` bean is created with an empty plugin list (no injection errors)
