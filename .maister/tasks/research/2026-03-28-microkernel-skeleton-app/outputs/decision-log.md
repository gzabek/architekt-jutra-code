# Decision Log

## ADR-001: Monorepo Structure

### Status
Accepted

### Context
The aj project is a Spring Boot 4.0.5 application that needs a React frontend added. The project is currently a single Maven module. The team needs to decide how to structure the frontend code relative to the backend: embedded in the same module, as a separate Maven module, or in a separate repository.

### Decision Drivers
- Project is already a single Maven module -- restructuring has a cost
- Single team developing both frontend and backend
- Skeleton phase prioritizes simplicity over separation of concerns
- Project's "minimal implementation" standard favors the simplest viable approach

### Considered Options
1. **Single module with embedded frontend** (`src/main/frontend/`) -- frontend lives inside the existing Maven module, built via plugin during `generate-resources` phase
2. **Maven multi-module** (parent POM + `backend/` + `frontend/` modules) -- frontend module produces a JAR of static resources, backend depends on it
3. **Separate repositories** -- frontend and backend in independent repos with independent build pipelines

### Decision Outcome
Chosen option: **Option 1 (Single module with embedded frontend)**, because it requires zero restructuring of the existing project, produces a single build artifact, and is the most documented pattern for single-team Spring Boot + React projects. The skeleton does not need the separation boundaries that multi-module provides.

### Consequences

#### Good
- No project restructuring needed -- work is purely additive
- Single `pom.xml` to maintain
- Single `./mvnw clean package` produces everything
- Simplest mental model for developers

#### Bad
- Frontend and backend cannot be built independently via Maven (must build together)
- If the team grows and splits into frontend/backend specialists, multi-module may become more appropriate
- Frontend directory inside `src/main/` is unconventional for frontend developers accustomed to root-level projects

---

## ADR-002: Frontend Tooling

### Status
Accepted

### Context
The skeleton needs a React frontend with TypeScript. The team must choose a build tool and scaffolding approach. Create React App (CRA) was historically the default but is now deprecated. Modern alternatives include Vite, Next.js, and Remix.

### Decision Drivers
- Need fast developer feedback loop (HMR)
- TypeScript support is required
- Must integrate with Maven build via plugin
- Client-side SPA (no SSR requirements for skeleton)
- Build output must be static files deployable to `classpath:/static/`

### Considered Options
1. **Vite + React 19 + TypeScript** -- modern build tool with native ES module serving, fast HMR, and static output
2. **Create React App (CRA)** -- legacy scaffolding tool, officially deprecated
3. **Next.js** -- React meta-framework with SSR/SSG capabilities
4. **Remix** -- Full-stack React framework

### Decision Outcome
Chosen option: **Option 1 (Vite + React 19 + TypeScript)**, because CRA is deprecated (early 2025), Vite is the industry standard replacement with 40x faster builds, native TypeScript support, and reliable HMR. Next.js and Remix are meta-frameworks designed for SSR/SSG use cases that add unnecessary complexity for a client-side SPA skeleton.

### Consequences

#### Good
- Sub-second HMR during development
- Industry standard -- large ecosystem, active maintenance
- Clean static output suitable for Spring Boot's `classpath:/static/` serving
- Built-in proxy configuration for API forwarding during development
- First-class TypeScript support without additional configuration

#### Bad
- Vite major version updates may require config migration (mitigated by pinning in `package.json`)
- Developers unfamiliar with Vite need brief orientation (minimal -- config is simpler than CRA's webpack)

---

## ADR-003: Microkernel Pattern

### Status
Accepted

### Context
The aj project is architecturally defined as a "plugin-based microkernel platform." The skeleton must establish the core plugin infrastructure: interfaces for plugins and extension points, and a registry for discovering them. Six approaches were evaluated ranging from Spring DI to OSGi.

### Decision Drivers
- Project's "minimal implementation" standard -- no speculative abstractions, no future stubs
- Zero additional dependencies preferred for skeleton phase
- Interfaces must remain stable when upgrading to a heavier framework later
- Spring Boot is the application framework -- native Spring patterns preferred
- No runtime plugin loading needed yet (all plugins on classpath at compile time)

### Considered Options
1. **Spring DI + Strategy/Registry pattern** -- `Plugin` interface + `PluginRegistry` component using `List<Plugin>` constructor injection
2. **Java SPI (ServiceLoader)** -- JDK-native `META-INF/services/` discovery, framework-agnostic
3. **Spring Plugin (spring-plugin-core)** -- Official Spring project with `Plugin<S>` and `PluginRegistry<T,S>`
4. **PF4J** -- Lightweight plugin framework (~100KB) with classloader isolation and runtime JAR/ZIP loading
5. **JPMS (Java Module System)** -- `module-info.java` with `provides...with` directives for strong encapsulation
6. **OSGi** -- Enterprise-grade module system with full lifecycle and hot deployment

### Decision Outcome
Chosen option: **Option 1 (Spring DI + Strategy/Registry)**, because it provides the core microkernel abstractions (Plugin, ExtensionPoint, PluginRegistry) with zero additional dependencies, uses pure Spring idioms the team already knows, and the interfaces remain unchanged when migrating to PF4J or JPMS later. The skeleton's plugin interfaces ARE the architecture -- they are not speculative abstractions.

### Consequences

#### Good
- Zero new dependencies
- Pure Spring idiom -- consistent with the rest of the application
- Compile-time type safety
- Any `@Component` implementing `Plugin` is auto-discovered
- Interface contracts remain stable across future framework upgrades
- Satisfies both the microkernel architecture requirement and the minimal-implementation standard

#### Bad
- No runtime plugin loading (plugins must be on classpath at startup)
- No classloader isolation between plugins
- No hot deployment or hot reloading of plugins
- `getExtensions(Class)` method has a placeholder implementation in skeleton

---

## ADR-004: Database Development Setup

### Status
Accepted

### Context
The skeleton needs PostgreSQL for development and testing. The project already has TestContainers fully configured for tests. The team must decide how to provision the database for regular development (running the app locally, connecting from IDE tools like pgAdmin).

### Decision Drivers
- Developer needs persistent data across application restarts during development
- IDE database tools need a stable connection (fixed host, port, credentials)
- Tests must remain isolated with ephemeral databases
- Spring Boot 4.0.5 has native `spring-boot-docker-compose` support
- Existing TestContainers setup must continue working for tests

### Considered Options
1. **Docker Compose (dev) + TestContainers (tests)** -- `compose.yml` with `spring-boot-docker-compose` for development; existing TestContainers for tests
2. **TestContainers only** -- use `TestAjApplication.java` pattern for both dev and tests
3. **Manual PostgreSQL installation** -- developer installs PostgreSQL locally
4. **H2 in-memory for dev, PostgreSQL for tests** -- different databases per environment

### Decision Outcome
Chosen option: **Option 1 (Docker Compose + TestContainers)**, because Docker Compose provides persistent data and fixed connection parameters (localhost:5432, aj/aj) that IDE tools need, while TestContainers provides ephemeral isolation for tests. Both use Spring Boot's `ServiceConnection` abstraction, so no explicit `spring.datasource.*` properties are needed. The `spring.docker.compose.skip.in-tests=true` default cleanly separates the two mechanisms.

### Consequences

#### Good
- Persistent data across dev restarts (named Docker volume)
- Fixed, predictable connection parameters for IDE tools (pgAdmin, IntelliJ DB console)
- Zero explicit database configuration in `application.properties`
- Clean separation: Docker Compose for dev, TestContainers for tests
- `TestAjApplication.java` preserved as alternative ephemeral dev workflow
- PostgreSQL version pinned to 17 in both environments for consistency

#### Bad
- Requires Docker Desktop installed on developer machine
- Docker Compose adds a container that consumes resources even when not actively developing
- Two database provisioning mechanisms to understand (though they use the same Spring abstraction)

---

## ADR-005: Maven Frontend Build Integration

### Status
Accepted

### Context
The React frontend (Vite + TypeScript) must be built as part of the Maven lifecycle so that `./mvnw clean package` produces a single JAR containing both backend and frontend. Two Maven plugins can execute npm commands during the build.

### Decision Drivers
- Reproducible builds -- same Node version across all developers and CI
- Minimal prerequisites -- ideally only JDK + Maven wrapper needed
- Align with Maven Wrapper philosophy (self-contained build)
- Industry adoption and maintenance status of the plugin

### Considered Options
1. **frontend-maven-plugin** (com.github.eirslett) -- downloads and installs Node.js locally to the project; pins Node version in `pom.xml`
2. **exec-maven-plugin** (org.codehaus.mojo) -- executes system-installed `npm` commands; requires Node.js pre-installed globally

### Decision Outcome
Chosen option: **Option 1 (frontend-maven-plugin)**, because it pins the Node.js version in `pom.xml` ensuring identical builds across all environments, requires no global Node installation (downloads to `src/main/frontend/node/`), and aligns with the project's use of Maven Wrapper for reproducible, self-contained builds. The build machine only needs JDK + Maven.

### Consequences

#### Good
- Node version pinned in `pom.xml` -- reproducible across developers and CI
- No global Node.js installation required
- Self-contained: `./mvnw clean package` works on a fresh machine with only JDK
- Widely adopted -- well-maintained with extensive documentation
- Supports npm, yarn, and pnpm if package manager changes later

#### Bad
- First build downloads Node.js (~25MB one-time cost)
- Adds `node/` directory inside `src/main/frontend/` (must be git-ignored)
- Plugin version must be maintained (currently 1.15.1)
- Slightly slower first build compared to using system Node
