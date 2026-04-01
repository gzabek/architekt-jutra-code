# Codebase Analysis Report

**Date**: 2026-03-28
**Task**: Build microkernel skeleton app with Spring Boot backend, React frontend, and containerized PostgreSQL
**Description**: Research findings suggest 9 new files + 3 modifications needed. Key areas: pom.xml dependencies, existing Java sources, TestContainers setup, application.properties, directory structure, .gitignore.
**Analyzer**: codebase-analyzer skill (2 Explore agents: File Discovery + Code Analysis, Pattern Mining)

---

## Summary

The project is a pre-alpha Spring Boot 4.0.5 scaffold with Java 25, containing only the boot entry point, TestContainers configuration, and empty resource directories. All backend dependencies (JPA, jOOQ, Liquibase, WebMVC, PostgreSQL, TestContainers) are already in pom.xml. The task is purely additive -- creating new files for microkernel interfaces, Docker Compose, React frontend, Liquibase changelog, API controllers, and build tooling, plus minor modifications to pom.xml, application.properties, and .gitignore.

---

## Files Identified

### Primary Files

**pom.xml** (101 lines)
- Maven build descriptor with Spring Boot 4.0.5 parent, Java 25
- Needs modification: add spring-boot-docker-compose and frontend-maven-plugin dependencies/plugins

**src/main/java/pl/devstyle/aj/AjApplication.java** (13 lines)
- Spring Boot entry point with @SpringBootApplication
- No modifications needed

**src/main/resources/application.properties** (1 line)
- Only contains `spring.application.name=aj`
- Needs modification: add JPA, Liquibase, and datasource configuration properties

**.gitignore** (34 lines)
- Standard Spring Boot + IDE patterns
- Needs modification: add frontend entries (node_modules/, src/main/frontend/dist/, etc.)

### Related Files

**src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java** (18 lines)
- PostgreSQL TestContainers setup with @ServiceConnection
- Uses `postgres:latest` (unpinned -- potential flakiness risk but acceptable for pre-alpha)

**src/test/java/pl/devstyle/aj/AjApplicationTests.java** (15 lines)
- Context loading test importing TestcontainersConfiguration
- No modifications needed

**src/test/java/pl/devstyle/aj/TestAjApplication.java** (10 lines)
- Dev-mode runner using TestContainers (SpringApplication.from().with().run())
- No modifications needed

---

## Current Functionality

The application currently boots as an empty Spring Boot shell. It has no controllers, no entities, no database schema, no frontend, and no microkernel abstractions. The TestContainers setup allows running the app and tests against an ephemeral PostgreSQL container.

### Key Components/Functions

- **AjApplication.main()**: Standard Spring Boot entry point
- **TestcontainersConfiguration.postgresContainer()**: Creates PostgreSQL container with @ServiceConnection auto-configuration
- **TestAjApplication.main()**: Allows running the full app backed by TestContainers (for manual dev without Docker Compose)
- **AjApplicationTests.contextLoads()**: Verifies Spring context starts successfully

### Data Flow

Currently minimal: Spring Boot starts, connects to PostgreSQL (via TestContainers in test), and serves nothing. No HTTP endpoints, no database tables, no frontend.

---

## Dependencies

### Imports (What This Depends On)

- **spring-boot-starter-webmvc**: REST API layer (Spring MVC)
- **spring-boot-starter-data-jpa**: JPA/Hibernate ORM
- **spring-boot-starter-jooq**: jOOQ query DSL
- **spring-boot-starter-liquibase**: Database migration engine
- **postgresql**: JDBC driver (runtime scope)
- **spring-boot-testcontainers**: TestContainers Spring integration (test)
- **testcontainers-junit-jupiter**: JUnit 5 TestContainers support (test)
- **testcontainers-postgresql**: PostgreSQL container module (test)
- **spring-boot-starter-*-test** (4 modules): Test starters for webmvc, jpa, jooq, liquibase

### Consumers (What Depends On This)

No external consumers -- this is a standalone application in pre-alpha.

**Consumer Count**: 0 files (internal only)
**Impact Scope**: Low -- no downstream systems depend on this code

---

## Test Coverage

### Test Files

- **AjApplicationTests.java**: Context loading smoke test
- **TestAjApplication.java**: Dev runner (not a test, but test-scoped runner)
- **TestcontainersConfiguration.java**: Test infrastructure (shared config)

### Coverage Assessment

- **Test count**: 1 test (contextLoads)
- **Gaps**: No unit tests, no integration tests, no API tests -- only a context loading check. This is expected for a scaffold; tests will be added alongside new functionality.

---

## Coding Patterns

### Naming Conventions

- **Classes**: PascalCase (AjApplication, TestcontainersConfiguration)
- **Methods**: camelCase (contextLoads, postgresContainer)
- **Packages**: lowercase dot-separated (pl.devstyle.aj)
- **Files**: Match class name exactly

### Architecture Patterns

- **Style**: Spring Boot convention-over-configuration
- **Test Configuration**: @TestConfiguration(proxyBeanMethods = false) with @ServiceConnection
- **Test Import**: @Import on test classes rather than @Testcontainers annotation
- **Dev Runner**: TestAjApplication pattern for running with TestContainers outside of tests

### Build Patterns

- **Version Management**: Parent POM manages all dependency versions -- no version tags in dependency declarations
- **Plugin Configuration**: Minimal -- only spring-boot-maven-plugin with defaults
- **Maven Wrapper**: Pinned to 3.9.14, jar excluded from git

---

## Complexity Assessment

| Factor | Value | Level |
|--------|-------|-------|
| File Count | 4 source + 1 config | Low |
| Dependencies | 8 runtime + 7 test | Medium |
| Consumers | 0 external | Low |
| Test Coverage | 1 test | Low |

### Overall: Simple

The codebase is a minimal scaffold. All changes are additive (new files) with only 3 minor modifications to existing files. The technology stack is well-understood (Spring Boot, React/Vite, Docker Compose, Liquibase) and the research phase has already produced detailed ADRs.

---

## Key Findings

### Strengths
- Clean scaffold with no technical debt to work around
- All backend dependencies already declared in pom.xml
- TestContainers infrastructure already working with @ServiceConnection pattern
- Spring Boot 4.0.5 parent manages all versions -- no version conflicts to resolve
- TestAjApplication dev runner pattern already established

### Concerns
- TestContainers uses `postgres:latest` rather than a pinned version -- acceptable for now but should be pinned before production
- Lombok is referenced in coding standards (entity modeling) but NOT declared in pom.xml -- will need to be added if entity modeling is in scope
- No Liquibase master changelog exists -- app will fail to start against a real database without one
- Empty `db/changelog/`, `static/`, and `templates/` directories exist but contain nothing

### Opportunities
- TestContainers and Docker Compose can coexist cleanly: tests use TestContainers, dev uses Docker Compose
- Spring Boot auto-serves static content from `src/main/resources/static/` -- React build output goes here naturally
- The scaffold is clean enough that microkernel interfaces can be introduced without refactoring

---

## Impact Assessment

- **Primary changes**: pom.xml (add dependencies + plugins), application.properties (add JPA/Liquibase config), .gitignore (add frontend entries)
- **New files (~9)**: Docker Compose, Liquibase master changelog, microkernel interfaces (Plugin, ExtensionPoint, PluginRegistry), HealthController, SpaForwardController, React frontend scaffold (package.json, vite.config.ts, App.tsx, etc.)
- **Test updates**: Existing tests should continue passing without modification. New tests for HealthController and plugin registry.

### Risk Level: Low

All changes are additive to a minimal scaffold. No existing behavior is modified -- the 3 file modifications (pom.xml, application.properties, .gitignore) only add new content. The technology choices are standard and well-documented in the research ADRs.

---

## Recommendations

**Task type: Creating new capability (no existing implementation)**

1. **Recommended architecture**: Follow the planned package structure from architecture.md -- `core/`, `plugin/`, `api/`, `domain/`, `config/` under `pl.devstyle.aj`
2. **Integration approach**: Add Docker Compose and Liquibase first (database layer), then microkernel interfaces (core domain), then API controllers, then React frontend last
3. **Patterns to follow**:
   - Match existing @TestConfiguration/@ServiceConnection patterns for any new test infrastructure
   - Use parent POM version management -- do not add explicit version tags
   - Follow the Spring Boot convention for static content serving (build React to `src/main/resources/static/`)
4. **Build integration**: frontend-maven-plugin should install Node, run npm install, and run npm build during Maven package phase
5. **Verification**: HealthController at `/api/health` provides end-to-end smoke test covering backend + database connectivity

---

## Next Steps

Proceed to gap analysis to compare this baseline against the research findings and produce a detailed scope with specific file-by-file changes needed.
