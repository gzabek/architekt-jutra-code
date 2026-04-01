# Synthesis: Microkernel Skeleton Application

## Research Question
How to build a microkernel architecture skeleton app with Spring Boot backend, React frontend, and containerized PostgreSQL -- all technology integrated and running, no auth, no business features?

## Executive Summary

The four research streams converge on a clear, low-risk implementation path. The existing `aj` project provides a solid Spring Boot 4.0.5 foundation with JPA, jOOQ, Liquibase, and TestContainers already wired. The gaps -- React frontend, Docker Compose PostgreSQL, microkernel interfaces, and application configuration -- are well-understood and can be filled using established, widely-documented patterns. No novel integration challenges were identified. The primary risk is over-engineering; all sources emphasize keeping the skeleton minimal.

---

## 1. Cross-Source Analysis

### 1.1 Validated Findings (Confirmed by Multiple Sources)

**Single-module monorepo is the right structure** (Confidence: High)
- Codebase analysis confirms the project is already a single Maven module
- Spring-React integration research recommends Option A (embedded frontend in `src/main/frontend/`) as simplest for single-team projects
- Microkernel findings confirm the plugin architecture can be implemented within a single module using Spring DI
- All three findings align: no multi-module restructuring needed

**Spring Boot auto-configuration handles database wiring** (Confidence: High)
- Docker-Postgres findings show `spring-boot-docker-compose` auto-creates `ServiceConnection` beans from `compose.yml`
- Codebase analysis confirms TestContainers already uses `@ServiceConnection` for tests
- Both mechanisms use the same Spring Boot `ConnectionDetails` abstraction -- they are complementary, not conflicting
- `spring.docker.compose.skip.in-tests=true` (default) prevents overlap

**Vite + React + TypeScript is the uncontested frontend choice** (Confidence: High)
- Spring-React integration research confirms CRA is deprecated, Vite is the standard
- No conflicting sources found
- `frontend-maven-plugin` is the dominant Maven integration tool

**Spring DI-based plugin pattern is the right microkernel approach for skeleton** (Confidence: High)
- Microkernel findings compared 6 approaches; Spring DI + Strategy/Registry scored highest for skeleton suitability
- Aligns with project's "minimal implementation" standard
- Zero additional dependencies
- Clear evolution path to PF4J or JPMS when runtime loading is actually needed

### 1.2 Cross-References Between Findings

| Connection | Source A | Source B | Insight |
|------------|----------|----------|---------|
| Database auto-config symmetry | Docker-Postgres: `spring-boot-docker-compose` uses `ServiceConnection` | Codebase: TestContainers uses `@ServiceConnection` | Same abstraction for dev and test -- clean separation by dependency scope |
| Frontend output target | Spring-React: Vite `outDir` points to `src/main/resources/static/` | Codebase: `static/` directory already exists (empty) | Existing directory serves as Vite build output -- no structural changes needed |
| Package structure alignment | Microkernel: recommends `core/plugin/`, `api/` packages | Codebase: `architecture.md` plans `core/`, `plugin/`, `api/`, `domain/`, `config/` | Microkernel recommendation is a subset of the planned structure |
| Maven plugin gap | Codebase: no `frontend-maven-plugin` in `pom.xml` | Spring-React: `frontend-maven-plugin` needed for build integration | Known gap with clear solution |
| Liquibase readiness | Codebase: `db/changelog/` exists but empty | Docker-Postgres: Liquibase auto-runs on startup when datasource is available | Need a master changelog file to avoid startup errors |
| PostgreSQL version mismatch | Codebase: TestContainers uses `postgres:latest` | Docker-Postgres: recommends `postgres:17` pinned | Should align both to `postgres:17` for consistency |

### 1.3 Contradictions and Tensions

**Tension 1: Minimal implementation vs. microkernel interfaces**
- The project standard says "No Future Stubs" and "No Speculative Abstractions"
- The architecture is explicitly microkernel, requiring Plugin/ExtensionPoint/Registry interfaces
- **Resolution**: Microkernel findings resolve this well -- core plugin interfaces are foundational architecture, not speculation. The skeleton should define ONLY core infrastructure interfaces (Plugin, ExtensionPoint, PluginRegistry), not domain-specific extension points.

**Tension 2: Docker Compose vs. TestContainers for development**
- The codebase already has `TestAjApplication.java` for running with TestContainers
- Docker-Postgres findings recommend Docker Compose as the primary dev workflow
- **Resolution**: Both coexist cleanly. Docker Compose is primary (persistent data, fixed port for IDE tools). TestContainers via `test-run` remains as a lightweight alternative. Tests always use TestContainers.

**Tension 3: Lombok referenced in standards but not in dependencies**
- Codebase analysis flags Lombok as missing from `pom.xml`
- Backend standards (`models.md`) reference `@Getter`, `@Setter`, `@NoArgsConstructor`
- **Resolution**: Not relevant for skeleton phase. No JPA entities are being created yet. Add Lombok when the first entity is implemented.

**No significant contradictions found between sources.**

### 1.4 Confidence Assessment by Area

| Area | Confidence | Rationale |
|------|-----------|-----------|
| Project structure (monorepo layout) | High (95%) | Single approach validated by all sources |
| React + Vite setup | High (100%) | Industry consensus, no alternatives worth considering |
| Maven frontend integration | High (90%) | `frontend-maven-plugin` is dominant; `exec-maven-plugin` is viable alternative |
| Vite-to-Spring build output | High (95%) | Consistent across all sources |
| Docker Compose PostgreSQL | High (95%) | Standard pattern, Spring Boot has native support |
| Spring Boot Docker Compose module | High (90%) | Stable since Spring Boot 3.1, confirmed in 4.0.x API docs |
| Microkernel core interfaces | High (90%) | Spring DI approach well-documented; interface design is straightforward |
| TestContainers + Docker Compose coexistence | High (90%) | Dependency scoping and `skip.in-tests` default handle separation |
| SPA routing | High (90%) | Controller-based forwarding is simpler; two approaches available |
| Development workflow | High (95%) | Two-server dev (Vite + Spring Boot) is industry standard |
| Production build (single JAR) | High (95%) | Standard Maven lifecycle with frontend-maven-plugin |

---

## 2. Patterns and Themes

### 2.1 Pattern: Spring Boot Auto-Configuration as Integration Glue
- **Description**: Spring Boot's auto-configuration handles database connections, static content serving, and Docker Compose lifecycle -- reducing explicit configuration to near zero
- **Evidence**: No `spring.datasource.*` properties needed (Docker Compose and TestContainers both provide `ServiceConnection`); static content served from `classpath:/static/` by default; Liquibase auto-runs when dependency + datasource present
- **Prevalence**: Pervasive across all four findings
- **Quality**: Mature, well-documented, stable API since Spring Boot 3.1+

### 2.2 Pattern: Convention over Configuration for Minimal Setup
- **Description**: The skeleton benefits from Spring Boot defaults rather than explicit configuration
- **Evidence**: Default port 8080, default static content path, default Liquibase changelog path, default Docker Compose file discovery
- **Prevalence**: High -- every finding area relies on defaults
- **Quality**: High -- reduces maintenance burden and error surface

### 2.3 Pattern: Compile-Time Plugin Discovery via Spring DI
- **Description**: Using `List<T>` injection and `@PostConstruct` indexing as a plugin registry
- **Evidence**: Microkernel findings Section 2.2, Strategy + Registry pattern in Spring Boot
- **Prevalence**: Common in Spring applications needing extensibility
- **Quality**: Mature, zero-dependency, type-safe

### 2.4 Pattern: Dual Development Paths (Docker Compose + TestContainers)
- **Description**: Docker Compose for regular dev (persistent, predictable), TestContainers for tests and ephemeral dev
- **Evidence**: Docker-Postgres findings Section 3 and 5; existing `TestAjApplication.java`
- **Prevalence**: Recommended by Spring Boot documentation and INNOQ article
- **Quality**: Clean separation via dependency scoping; no configuration conflicts

### 2.5 Pattern: Frontend-as-Static-Resources
- **Description**: React app builds into Spring Boot's `classpath:/static/` and is served as static content from the same origin
- **Evidence**: Spring-React findings Section 4, 6, 7
- **Prevalence**: Dominant pattern for Spring Boot + SPA monorepos
- **Quality**: Simple, no CORS needed, single deployment artifact

---

## 3. Gaps and Uncertainties

### 3.1 Information Gaps

| Gap | Impact | Mitigation |
|-----|--------|------------|
| `frontend-maven-plugin` compatibility with Spring Boot 4.0.5 | Low -- plugin is Maven-level, independent of Spring Boot version | Test during implementation; fall back to `exec-maven-plugin` |
| Spring Boot 4.0.5 static content serving changes | Low -- `classpath:/static/` convention documented as unchanged | Verify with smoke test after setup |
| Exact Node.js LTS version for `frontend-maven-plugin` | Minimal -- any Node 22.x LTS works | Check current LTS at implementation time |
| jOOQ code generation plugin setup | Medium -- jOOQ dependency exists but codegen not configured | Out of scope for skeleton; document as future work |

### 3.2 Unverified Claims

| Claim | Source | Risk |
|-------|--------|------|
| `spring-boot-docker-compose` available in Spring Boot 4.0.5 | Docker-Postgres findings cite 4.0.2 API docs | Low -- 4.0.2 to 4.0.5 is a patch release, unlikely to remove a module |
| `@RestController` takes precedence over `@Controller` for SPA routing | Spring-React findings Section 7 | Low -- well-established Spring MVC behavior |
| `spring.docker.compose.skip.in-tests=true` default | Docker-Postgres findings cite Spring Boot docs | Low -- documented behavior; verify at implementation |

### 3.3 Decisions Deferred (Intentionally)

| Decision | Reason for Deferral |
|----------|-------------------|
| JPA vs jOOQ responsibility split | No domain entities in skeleton; decide when first feature is implemented |
| Plugin framework selection (PF4J/JPMS) | Spring DI sufficient for skeleton; decide when runtime loading is needed |
| Multi-tenant strategy | Out of scope for skeleton |
| CI/CD pipeline | Out of scope |
| Production Dockerfile | Out of scope -- single JAR is sufficient |

---

## 4. Synthesis by Framework (Mixed Research Framework)

### Current State Analysis
- Spring Boot 4.0.5 project with comprehensive backend dependencies (JPA, jOOQ, Liquibase, WebMVC)
- TestContainers fully configured for integration testing
- No frontend, no Docker Compose, no microkernel interfaces, no application configuration
- Empty directories prepared (`static/`, `db/changelog/`, `templates/`)

**Strengths**: Solid dependency foundation, modern Spring Boot version, TestContainers already working.
**Weaknesses**: No runnable application (missing database config), no frontend, no package structure beyond root.

### Best Practices Comparison
- Monorepo with embedded frontend: matches industry practice for single-team Spring Boot + React projects
- `frontend-maven-plugin`: dominant tool for Maven-based frontend builds
- Docker Compose + `spring-boot-docker-compose`: officially supported by Spring Boot since 3.1
- Spring DI plugin pattern: most pragmatic microkernel approach for skeleton phase

### Trade-Off Summary

| Decision Point | Chosen | Alternative | Trade-off |
|---------------|--------|-------------|-----------|
| Monorepo structure | Single module, `src/main/frontend/` | Multi-module Maven | Simplicity vs. separation; simplicity wins for skeleton |
| Maven frontend integration | `frontend-maven-plugin` | `exec-maven-plugin` | Reproducibility vs. simplicity; reproducibility wins |
| Microkernel approach | Spring DI + interfaces | PF4J, JPMS, Spring Plugin | Minimal dependencies vs. runtime loading; minimal wins for skeleton |
| Dev database | Docker Compose (primary) + TestContainers (secondary) | TestContainers only | Persistence + IDE tools vs. simplicity; persistence wins |
| SPA routing | Controller-based forwarding | `WebMvcConfigurer` + `PathResourceResolver` | Explicit vs. flexible; explicit wins for skeleton |

### Applicability Assessment
All recommended patterns fit this project context because:
1. Single team, single repo, early-stage project
2. "Minimal implementation" standard favors the simplest viable approach in each area
3. Every chosen pattern has a documented evolution path to more complex alternatives
4. No pattern requires technology not already in the stack (except Node.js for frontend, which is expected)

---

## 5. Conclusions

### Primary Conclusions

1. **The skeleton is achievable with minimal changes to the existing project.** The core Spring Boot foundation is solid. The work is additive -- creating new files and adding dependencies, not restructuring.

2. **Four work streams can be executed largely independently**: (a) Docker Compose + database config, (b) React frontend scaffolding, (c) microkernel core interfaces, (d) integration wiring (SPA routing, health endpoint, Liquibase changelog). Dependencies between streams are minimal.

3. **The recommended approach minimizes new dependencies.** Only `spring-boot-docker-compose` (optional, Spring Boot native) and `frontend-maven-plugin` (Maven plugin) are new. The microkernel pattern uses zero additional libraries.

### Secondary Conclusions

4. **PostgreSQL version should be pinned to 17 in both Docker Compose and TestContainers** for consistency between dev and test environments.

5. **The `TestAjApplication.java` pattern should be preserved** as an alternative dev workflow -- it provides value for quick ephemeral database usage without Docker Compose.

6. **Lombok, jOOQ codegen, and Spring profiles are not needed for the skeleton** but are natural additions when the first domain features are implemented.

---

*Synthesized: 2026-03-28*
*Sources: 4 findings files (codebase-analysis, spring-react-integration, microkernel-patterns, docker-postgres-setup)*
*Overall Confidence: High (90-95%) across all major recommendations*
