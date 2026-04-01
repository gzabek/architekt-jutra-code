# Research Report: Microkernel Skeleton Application

**Research Type**: Mixed (Technical + Literature)
**Date**: 2026-03-28
**Project**: aj (pl.devstyle.aj)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Research Objectives](#2-research-objectives)
3. [Methodology](#3-methodology)
4. [What Already Exists](#4-what-already-exists)
5. [Recommended Approach](#5-recommended-approach)
   - 5.1 Project Structure
   - 5.2 Docker Compose PostgreSQL
   - 5.3 Microkernel Core Interfaces
   - 5.4 React Frontend Setup
   - 5.5 Maven Build Integration
   - 5.6 SPA Routing and API Convention
   - 5.7 Liquibase Baseline
   - 5.8 Development Workflow
6. [Concrete File List](#6-concrete-file-list)
7. [Recommended Implementation Order](#7-recommended-implementation-order)
8. [Risks and Mitigations](#8-risks-and-mitigations)
9. [Conclusions](#9-conclusions)
10. [Appendix: Sources](#10-appendix-sources)

---

## 1. Executive Summary

**Question**: How to build a microkernel architecture skeleton app with Spring Boot backend, React frontend, and containerized PostgreSQL -- all technology integrated and running, no auth, no business features?

**Answer**: The existing `aj` project provides a solid Spring Boot 4.0.5 foundation. The skeleton can be completed by: (1) adding a `compose.yml` with PostgreSQL 18 and the `spring-boot-docker-compose` dependency for automatic database provisioning, (2) scaffolding a React + Vite + TypeScript app in `src/main/frontend/` with `frontend-maven-plugin` for Maven integration, (3) defining minimal microkernel interfaces (`Plugin`, `ExtensionPoint`, `PluginRegistry`) using Spring DI with zero additional libraries, and (4) wiring everything together with a health endpoint, SPA forwarding controller, and Liquibase master changelog.

The result is a single Maven project that produces one executable JAR serving both the React frontend and Spring Boot API, with PostgreSQL auto-provisioned via Docker Compose for development and TestContainers for tests.

**Overall Confidence**: High (90-95%). All recommendations are backed by official documentation, established community patterns, and direct codebase observation.

---

## 2. Research Objectives

### Primary Question
How to build a microkernel architecture skeleton app with Spring Boot backend, React frontend, and containerized PostgreSQL -- all technology integrated and running, no auth, no business features?

### Sub-Questions
1. How should a Spring Boot + React monorepo be structured with Maven?
2. What is the recommended way to integrate a React frontend build into a Spring Boot project?
3. How to set up Docker Compose for development PostgreSQL that works with both app runtime and TestContainers tests?
4. What does a microkernel core look like in Spring Boot -- core interfaces, plugin registry, extension points?
5. What is the minimal end-to-end vertical slice to prove all layers work?
6. What development workflow allows running backend + frontend + database together?

### Scope
- **Included**: Backend skeleton, React setup, Docker Compose PostgreSQL, microkernel core interfaces, Maven build integration, development workflow
- **Excluded**: Auth, business features, production deployment, CI/CD, frontend feature implementation

---

## 3. Methodology

### Approach
Multi-strategy research combining codebase analysis, best practices research, and configuration analysis across four parallel investigation streams.

### Data Sources
| Category | Count | Description |
|----------|-------|-------------|
| Codebase files analyzed | 10+ | pom.xml, application.properties, Java source files, test files, directory structure |
| Project documentation | 8 | Architecture, tech stack, backend/frontend/global standards |
| External sources | 25+ | Spring Boot docs, React/Vite guides, Docker Compose references, microkernel pattern articles, GitHub templates |

### Analysis Framework
Mixed Research Framework: Current State Analysis + Best Practices Comparison + Trade-Off Analysis + Applicability Assessment.

---

## 4. What Already Exists

The `aj` project has a strong foundation that requires no restructuring.

### Existing Assets (Leverage These)

| Asset | Status | Notes |
|-------|--------|-------|
| Spring Boot 4.0.5 with `spring-boot-starter-webmvc` | Working | Serves static content from `classpath:/static/` by default |
| Spring Data JPA + jOOQ | Dependencies present | No entities or queries yet, but wired |
| Liquibase | Dependency present | `db/changelog/` directory exists but empty |
| PostgreSQL JDBC driver | Runtime dependency | Ready for database connection |
| TestContainers setup | Fully working | `TestcontainersConfiguration.java` + `TestAjApplication.java` + test dependencies |
| Maven Wrapper 3.9.14 | Working | `mvnw` / `mvnw.cmd` scripts present |
| Empty `static/` directory | Prepared | Target for Vite build output |
| Empty `db/changelog/` directory | Prepared | Target for Liquibase migrations |
| Project documentation | Comprehensive | Architecture, tech stack, coding standards in `.maister/docs/` |

### Gaps to Fill

| Gap | Priority | Solution |
|-----|----------|----------|
| Docker Compose for PostgreSQL | High | Add `compose.yml` + `spring-boot-docker-compose` dependency |
| React frontend | High | Scaffold with Vite in `src/main/frontend/` |
| Microkernel core interfaces | High | Create `Plugin`, `ExtensionPoint`, `PluginRegistry` in `core.plugin` package |
| Maven frontend build integration | High | Add `frontend-maven-plugin` to `pom.xml` |
| Liquibase master changelog | High | Create `db.changelog-master.yaml` |
| SPA routing controller | Medium | Create `SpaForwardController` |
| Health endpoint | Medium | Create `/api/health` controller |
| `.gitignore` updates | Medium | Add frontend-related ignores |
| PostgreSQL version pinning | Low | Change TestContainers from `postgres:latest` to `postgres:17` |

---

## 5. Recommended Approach

### 5.1 Project Structure (Monorepo Layout)

**Decision**: Single Maven module with embedded frontend directory.

```
aj/
+-- pom.xml                                 # Maven build (updated)
+-- compose.yml                             # Docker Compose PostgreSQL (NEW)
+-- mvnw / mvnw.cmd                        # Maven wrapper (existing)
+-- .gitignore                              # Updated with frontend ignores
+-- src/
|   +-- main/
|   |   +-- java/pl/devstyle/aj/
|   |   |   +-- AjApplication.java          # Spring Boot entry point (existing)
|   |   |   +-- core/
|   |   |   |   +-- plugin/
|   |   |   |       +-- Plugin.java          # Plugin lifecycle interface (NEW)
|   |   |   |       +-- PluginDescriptor.java # Plugin metadata interface (NEW)
|   |   |   |       +-- ExtensionPoint.java  # Extension point marker (NEW)
|   |   |   |       +-- PluginRegistry.java  # Registry component (NEW)
|   |   |   +-- api/
|   |   |       +-- SpaForwardController.java # SPA routing (NEW)
|   |   |       +-- HealthController.java    # /api/health endpoint (NEW)
|   |   +-- frontend/                        # React + Vite + TypeScript (NEW)
|   |   |   +-- package.json
|   |   |   +-- vite.config.ts
|   |   |   +-- tsconfig.json
|   |   |   +-- index.html
|   |   |   +-- src/
|   |   |       +-- main.tsx
|   |   |       +-- App.tsx
|   |   |       +-- App.css
|   |   +-- resources/
|   |       +-- application.properties       # Updated with app name (existing)
|   |       +-- static/                      # Vite build output (git-ignored)
|   |       +-- db/changelog/
|   |           +-- db.changelog-master.yaml # Liquibase master changelog (NEW)
|   +-- test/java/pl/devstyle/aj/           # Tests (existing, unchanged)
|       +-- AjApplicationTests.java
|       +-- TestAjApplication.java
|       +-- TestcontainersConfiguration.java
+-- .maister/                               # Project docs (existing, unchanged)
```

**Rationale**: The project is already a single Maven module. Restructuring to multi-module adds complexity with no benefit for a skeleton. The embedded frontend pattern is the most documented approach for single-team Spring Boot + React projects.

**Confidence**: 95%

### 5.2 Docker Compose PostgreSQL

**Decision**: Use `compose.yml` at project root with `spring-boot-docker-compose` module for automatic database provisioning.

**compose.yml**:
```yaml
services:
  postgres:
    image: 'postgres:17'
    ports:
      - '5432:5432'
    environment:
      POSTGRES_DB: aj
      POSTGRES_USER: aj
      POSTGRES_PASSWORD: aj
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aj -d aj"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

volumes:
  postgres-data:
```

**pom.xml addition**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <optional>true</optional>
</dependency>
```

**How it works**:
- When you run `./mvnw spring-boot:run`, Spring Boot discovers `compose.yml`, starts PostgreSQL via Docker Compose, and auto-configures the datasource from the running container.
- No `spring.datasource.*` properties needed. No explicit profile configuration.
- Data persists in the `postgres-data` named volume across restarts. Run `docker compose down -v` to reset.
- During `./mvnw test`, Docker Compose is automatically skipped (`spring.docker.compose.skip.in-tests=true` default). Tests use TestContainers as before.

**Also recommended**: Pin TestContainers to `postgres:17` (currently uses `postgres:latest`) for consistency between dev and test environments.

**Confidence**: 95%

### 5.3 Microkernel Core Interfaces

**Decision**: Use Spring DI-based plugin pattern with zero additional dependencies.

**Four files in `pl.devstyle.aj.core.plugin`**:

**ExtensionPoint.java** -- Marker interface for all extension point types:
```java
public interface ExtensionPoint {
}
```

**PluginDescriptor.java** -- Plugin metadata contract:
```java
public interface PluginDescriptor {
    String getPluginId();
    String getName();
    String getVersion();
}
```

**Plugin.java** -- Plugin lifecycle contract:
```java
public interface Plugin extends PluginDescriptor {
    default void onStart() {}
    default void onStop() {}
}
```

**PluginRegistry.java** -- Spring-managed registry that auto-discovers plugins:
```java
@Component
public class PluginRegistry {
    private final List<Plugin> plugins;

    public PluginRegistry(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public List<Plugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    public Optional<Plugin> findById(String pluginId) {
        return plugins.stream()
            .filter(p -> p.getPluginId().equals(pluginId))
            .findFirst();
    }

    public <T extends ExtensionPoint> List<T> getExtensions(Class<T> type) {
        // Future: discover extensions via Spring context
        return List.of();
    }
}
```

**How it works**: Any Spring `@Component` implementing `Plugin` is automatically injected into the `PluginRegistry` via `List<Plugin>` constructor injection. No plugins exist yet in the skeleton -- the registry starts empty. When the first plugin is created, it just needs to implement `Plugin` and be annotated with `@Component`.

**Evolution path**: These interfaces remain stable. When runtime loading or classloader isolation is needed, the registry implementation switches to PF4J or JPMS without changing the interface contracts.

**Why not PF4J/JPMS/Spring Plugin now**: The project's "minimal implementation" standard says build only what is needed. The skeleton needs the interfaces (they ARE the architecture) but not runtime loading infrastructure.

**Confidence**: 90%

### 5.4 React Frontend Setup

**Decision**: Vite + React 19 + TypeScript, scaffolded in `src/main/frontend/`.

**Scaffolding**:
```bash
cd src/main
npm create vite@latest frontend -- --template react-ts
```

This generates the standard Vite + React + TypeScript project structure with `index.html`, `src/main.tsx`, `src/App.tsx`, `vite.config.ts`, `tsconfig.json`, and `package.json`.

**vite.config.ts** (modify after scaffolding):
```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

**Key configuration**:
- `build.outDir`: Vite outputs directly to `src/main/resources/static/` (relative to frontend dir)
- `build.emptyOutDir`: Cleans output before each build
- `server.proxy`: During development, `/api/*` requests are forwarded to Spring Boot on port 8080

**Confidence**: 100% (Vite is industry standard; configuration is consistent across all sources)

### 5.5 Maven Build Integration

**Decision**: `frontend-maven-plugin` for reproducible builds.

**pom.xml addition**:
```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.15.1</version>
    <configuration>
        <workingDirectory>src/main/frontend</workingDirectory>
        <nodeVersion>v22.14.0</nodeVersion>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <goals><goal>install-node-and-npm</goal></goals>
            <phase>generate-resources</phase>
        </execution>
        <execution>
            <id>npm-install</id>
            <goals><goal>npm</goal></goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>install</arguments>
            </configuration>
        </execution>
        <execution>
            <id>npm-build</id>
            <goals><goal>npm</goal></goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>run build</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**How it works**:
1. `generate-resources` phase: Downloads Node.js 22.14.0 locally (into `src/main/frontend/node/`), runs `npm install`, runs `npm run build`
2. Vite build outputs to `src/main/resources/static/`
3. `package` phase: Everything (Java classes + static frontend) bundled into a single JAR

**Production build**: `./mvnw clean package` produces `target/aj-0.0.1-SNAPSHOT.jar` containing both API and frontend.

**Rationale over `exec-maven-plugin`**: Pinned Node version in `pom.xml` ensures reproducible builds. No global Node installation required -- aligns with Maven Wrapper philosophy.

**Confidence**: 90%

### 5.6 SPA Routing and API Convention

**API convention**: All REST endpoints under `/api/` prefix.

**HealthController.java**:
```java
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

**SpaForwardController.java** (forwards non-API, non-file requests to React):
```java
@Controller
public class SpaForwardController {

    @GetMapping(value = "/{path:[^\\.]*}")
    public String forwardSingle() {
        return "forward:/index.html";
    }

    @GetMapping(value = "/**/{path:[^\\.]*}")
    public String forwardNested() {
        return "forward:/index.html";
    }
}
```

**How it works**: Paths without a dot (no file extension) forward to `index.html` for React Router. Paths with dots (`.js`, `.css`, `.svg`) serve as static files. `@RestController` endpoints (`/api/**`) take precedence.

**CORS**: Not needed. Vite proxy handles cross-origin in development; same-origin in production (single JAR).

**Confidence**: 90%

### 5.7 Liquibase Baseline

**Decision**: Create a minimal master changelog to prevent startup errors.

**db.changelog-master.yaml**:
```yaml
databaseChangeLog: []
```

This empty changelog allows Liquibase to run without errors. Actual migrations are added as the first domain features are implemented.

**Confidence**: 95%

### 5.8 Development Workflow

#### Regular Development (Two Terminals)

**Terminal 1 -- Spring Boot backend (port 8080)**:
```bash
./mvnw spring-boot:run
# Auto-starts PostgreSQL via Docker Compose
# Runs Liquibase migrations
# Serves API at http://localhost:8080/api/*
```

**Terminal 2 -- Vite dev server (port 5173)**:
```bash
cd src/main/frontend
npm run dev
# Serves React at http://localhost:5173
# Proxies /api/* to http://localhost:8080
# Instant HMR for frontend changes
```

Developer opens `http://localhost:5173` in browser.

#### Running Tests
```bash
./mvnw test
# Uses TestContainers (Docker Compose auto-skipped)
# Fully isolated, ephemeral database
```

#### Production Build
```bash
./mvnw clean package
java -jar target/aj-0.0.1-SNAPSHOT.jar
# Serves both API and frontend on port 8080
```

#### Alternative: Ephemeral Dev (No Docker Compose)
```bash
./mvnw spring-boot:test-run
# Uses TestAjApplication.java with TestContainers
# Ephemeral database on random port
```

#### Database Access (IDE/pgAdmin)
- Host: `localhost`, Port: `5432`, Database: `aj`, User: `aj`, Password: `aj`

**Confidence**: 95%

---

## 6. Concrete File List

### Files to Create

| # | File Path | Purpose |
|---|-----------|---------|
| 1 | `compose.yml` | Docker Compose PostgreSQL service |
| 2 | `src/main/java/pl/devstyle/aj/core/plugin/ExtensionPoint.java` | Extension point marker interface |
| 3 | `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptor.java` | Plugin metadata interface |
| 4 | `src/main/java/pl/devstyle/aj/core/plugin/Plugin.java` | Plugin lifecycle interface |
| 5 | `src/main/java/pl/devstyle/aj/core/plugin/PluginRegistry.java` | Plugin registry component |
| 6 | `src/main/java/pl/devstyle/aj/api/HealthController.java` | `/api/health` endpoint |
| 7 | `src/main/java/pl/devstyle/aj/api/SpaForwardController.java` | SPA routing controller |
| 8 | `src/main/frontend/` (entire directory) | React + Vite + TypeScript app (scaffolded) |
| 9 | `src/main/resources/db/changelog/db.changelog-master.yaml` | Liquibase master changelog |

### Files to Modify

| # | File Path | Change |
|---|-----------|--------|
| 1 | `pom.xml` | Add `spring-boot-docker-compose` dependency (optional); add `frontend-maven-plugin` |
| 2 | `.gitignore` | Add `src/main/resources/static/`, `src/main/frontend/node/`, `src/main/frontend/node_modules/` |
| 3 | `src/main/frontend/vite.config.ts` | Set `outDir` to `../resources/static`, add `/api` proxy (after scaffolding) |

### Files to Leave Unchanged

| File Path | Reason |
|-----------|--------|
| `src/main/java/pl/devstyle/aj/AjApplication.java` | Already correct |
| `src/main/resources/application.properties` | Docker Compose auto-configures datasource |
| `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java` | Already correct (optionally pin to `postgres:17`) |
| `src/test/java/pl/devstyle/aj/TestAjApplication.java` | Already correct |
| `src/test/java/pl/devstyle/aj/AjApplicationTests.java` | Already correct |

---

## 7. Recommended Implementation Order

The order is driven by dependencies: database first (everything depends on it), then backend structure, then frontend.

### Step 1: Docker Compose + Database Foundation
**Files**: `compose.yml`, `pom.xml` (add `spring-boot-docker-compose`), `db.changelog-master.yaml`
**Verification**: `./mvnw spring-boot:run` starts successfully, PostgreSQL container runs, Liquibase executes (empty changelog)
**Why first**: Everything else (health endpoint, tests, frontend API calls) needs a running database

### Step 2: Microkernel Core Interfaces
**Files**: `ExtensionPoint.java`, `PluginDescriptor.java`, `Plugin.java`, `PluginRegistry.java`
**Verification**: Application still starts; `PluginRegistry` bean is created with empty plugin list
**Why second**: Core architecture goes in before any API or frontend code

### Step 3: Backend API Layer
**Files**: `HealthController.java`, `SpaForwardController.java`
**Verification**: `curl http://localhost:8080/api/health` returns `{"status":"UP"}`
**Why third**: Provides the API endpoint the frontend will call; SPA controller needed for frontend routing

### Step 4: React Frontend Scaffolding
**Files**: `src/main/frontend/` (entire Vite scaffold), modified `vite.config.ts`
**Verification**: `cd src/main/frontend && npm run dev` serves React on port 5173; `/api/health` proxy works
**Why fourth**: Backend must be ready to proxy API calls to

### Step 5: Maven Build Integration
**Files**: `pom.xml` (add `frontend-maven-plugin`), `.gitignore` updates
**Verification**: `./mvnw clean package` produces single JAR; `java -jar target/aj-0.0.1-SNAPSHOT.jar` serves both frontend and API
**Why last**: Integrates everything into the production build; requires all other pieces in place

### Optional Step 6: Cleanup
- Pin TestContainers PostgreSQL to `postgres:17`
- Verify `./mvnw test` still passes
- Verify `./mvnw spring-boot:test-run` still works

---

## 8. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `frontend-maven-plugin` incompatibility with Maven 3.9.14 or Java 25 | Low | Medium | Plugin operates at Maven level, not Java compile level; fall back to `exec-maven-plugin` if issues |
| Spring Boot 4.0.5 changes to static content serving | Low | Low | Test with smoke test; `classpath:/static/` convention documented as unchanged |
| Node.js version specified in `pom.xml` becomes outdated | Low | Low | Pin to current LTS (22.x); update periodically |
| Docker Compose not installed on developer machine | Medium | Medium | Docker Desktop includes Compose; document prerequisite; `test-run` works without Compose |
| `PluginRegistry` with empty `List<Plugin>` causes injection error | Low | Low | Spring injects empty list for `List<T>` when no beans match; safe by default |
| Vite major version changes break config syntax | Low | Low | Pin Vite version in `package.json` (lockfile handles this) |

---

## 9. Conclusions

### Primary Conclusions

1. **The skeleton is straightforward to build.** All required patterns are well-documented, widely used, and compatible with the existing project. No novel integration challenges were identified. (Confidence: High)

2. **The existing project foundation is solid and requires no restructuring.** Spring Boot 4.0.5 with JPA, jOOQ, Liquibase, WebMVC, and TestContainers provides everything needed. Work is purely additive. (Confidence: High)

3. **The recommended approach is the minimal viable implementation for each area.** Spring DI for plugins (no PF4J), embedded frontend (no multi-module), Docker Compose with auto-configuration (no manual properties). Every choice has a clear upgrade path. (Confidence: High)

### Direct Answer to Research Question

To build the microkernel skeleton:
- **Create 9 new files** (4 plugin interfaces, 2 controllers, 1 compose file, 1 changelog, 1 React app)
- **Modify 3 existing files** (`pom.xml`, `.gitignore`, Vite config after scaffolding)
- **Leave 5 existing files unchanged** (application entry point, properties, all test files)
- **Follow the 5-step implementation order** starting with database, then backend structure, then frontend
- **The result**: A single `./mvnw clean package` produces a JAR serving React frontend + Spring Boot API + Liquibase-managed PostgreSQL, with Docker Compose for dev and TestContainers for tests

### Recommendations

| Priority | Recommendation | Effort |
|----------|---------------|--------|
| Required | Implement Steps 1-5 in order | ~2-4 hours |
| Recommended | Pin TestContainers to `postgres:17` | 5 minutes |
| Recommended | Add `watch` script to frontend `package.json` | 5 minutes |
| Deferred | Add Lombok when first JPA entity is created | Future task |
| Deferred | Configure jOOQ code generation when first complex query is needed | Future task |
| Deferred | Evaluate PF4J/JPMS when runtime plugin loading is needed | Future task |

---

## 10. Appendix: Sources

### Codebase Sources
- `pom.xml` -- Build configuration, dependencies, plugins
- `src/main/java/pl/devstyle/aj/AjApplication.java` -- Application entry point
- `src/main/resources/application.properties` -- Application configuration
- `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java` -- TestContainers setup
- `src/test/java/pl/devstyle/aj/TestAjApplication.java` -- Dev runner with TestContainers
- `.maister/docs/project/architecture.md` -- Architecture decisions
- `.maister/docs/project/tech-stack.md` -- Technology choices
- `.maister/docs/standards/global/minimal-implementation.md` -- Minimal implementation standard

### External Sources -- Spring Boot + React Integration
- [Bundling React (Vite) with Spring Boot](https://www.jessym.com/articles/bundling-react-vite-with-spring-boot)
- [spring-react-vite-template (GitHub)](https://github.com/seanpolid/spring-react-vite-template)
- [Including React in Spring Boot Maven build](https://medium.com/@itzgeoff/including-react-in-your-spring-boot-maven-build-ae3b8f8826e)
- [frontend-maven-plugin (GitHub)](https://github.com/eirslett/frontend-maven-plugin)
- [Vite Getting Started](https://vite.dev/guide/)
- [Vite Backend Integration](https://vite.dev/guide/backend-integration)

### External Sources -- Microkernel Architecture
- [Microkernel Architecture Design Pattern (DEV Community)](https://dev.to/kishalay_pandey_d5d0cae01f00/microkernel-architecture-design-pattern-n79)
- [Software Architecture Patterns -- Microkernel (Priyal Walpita)](https://priyalwalpita.medium.com/software-architecture-patterns-microkernel-architecture-97cee200264e)
- [What Is Microkernel Architecture Design? (Alibaba Cloud)](https://www.alibabacloud.com/blog/what-is-microkernel-architecture-design_597605)
- [Strategy + Registry Pattern in Spring Boot (Medium)](https://medium.com/@venkatsai0398/building-a-dynamic-rules-engine-in-spring-boot-with-the-strategy-registry-pattern-c8bafacc1031)
- [PF4J Official Site](https://pf4j.org/)
- [spring-projects/spring-plugin (GitHub)](https://github.com/spring-projects/spring-plugin)

### External Sources -- Docker Compose + PostgreSQL
- [Spring Boot Dev Services Documentation](https://docs.spring.io/spring-boot/reference/features/dev-services.html)
- [Spring Boot Docker Compose How-to](https://docs.spring.io/spring-boot/how-to/docker-compose.html)
- [Docker Compose Support in Spring Boot (Baeldung)](https://www.baeldung.com/docker-compose-support-spring-boot)
- [INNOQ: Containers for tests and local development](https://www.innoq.com/en/articles/2023/10/spring-boot-testcontainers-and-docker-compose/)
- [Docker Compose Health Checks](https://last9.io/blog/docker-compose-health-checks/)
- [PostgreSQL Docker Best Practices](https://sliplane.io/blog/best-practices-for-postgres-in-docker)
