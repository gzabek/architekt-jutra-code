# Codebase Analysis Report

**Date**: 2026-03-28
**Task**: Full-stack ecommerce product management (Category + Product CRUD)
**Description**: Implement full-stack ecommerce product management (Category + Product CRUD) with Spring Boot backend (JPA entities, REST API, Liquibase migrations), React + Chakra UI frontend (routing, custom hooks, brand theming), and integration tests with TestContainers.
**Analyzer**: codebase-analyzer skill (3 Explore agents: File Discovery, Code Analysis, Pattern Mining)

---

## Summary

The project is a pre-alpha Spring Boot 4 + React 19 application with all infrastructure in place (JPA, Liquibase, jOOQ, TestContainers, Vite, SPA routing) but no business logic yet. The ecommerce product management feature will be the first domain implementation, requiring creation of foundational abstractions (BaseEntity, GlobalExceptionHandler, error response DTOs) alongside the Category and Product domain. Two new frontend dependencies (Chakra UI, React Router) are needed since no UI framework or routing library is currently installed.

---

## Files Identified

### Primary Files

**src/main/java/pl/devstyle/aj/api/HealthController.java** (17 lines)
- Template for REST controller structure (@RestController, @RequestMapping("/api"))
- Pattern to follow for new CategoryController and ProductController

**src/main/resources/db/changelog/db.changelog-master.yaml** (1 line)
- Empty changelog array -- all Liquibase migrations will be added here
- Entry point for category and product table migrations

**src/main/frontend/src/App.tsx** (20 lines)
- Current React entry point with basic fetch pattern
- Will need replacement with router-based layout and Chakra UI provider

**src/main/frontend/package.json** (30 lines)
- Only has react + react-dom; needs @chakra-ui/react, react-router-dom additions

**src/test/java/pl/devstyle/aj/IntegrationTests.java** (79 lines)
- Template for full-context integration tests with MockMvc + TestContainers
- Pattern to follow for CRUD endpoint integration tests

**pom.xml** (138 lines)
- All backend dependencies present (JPA, Liquibase, jOOQ, PostgreSQL, TestContainers)
- Missing: spring-boot-starter-validation (needed for @Valid, @NotBlank)

### Related Files

**src/main/java/pl/devstyle/aj/api/SpaForwardController.java** (18 lines)
- Forwards non-API routes to index.html for SPA routing
- Already supports nested paths -- React Router routes will work

**src/main/java/pl/devstyle/aj/core/plugin/Plugin.java** + **PluginDescriptor.java** + **PluginRegistry.java**
- Microkernel plugin scaffolding (core layer)
- Product management should NOT be a plugin -- it is standard domain CRUD

**src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java**
- PostgreSQL 18 container with @ServiceConnection
- Reused by all integration tests via @Import

**src/test/java/pl/devstyle/aj/ApiLayerTests.java**
- @WebMvcTest pattern for isolated controller tests with MockMvc
- Template for CategoryController and ProductController unit tests

**src/main/resources/application.properties**
- Minimal config (spring.application.name=aj only)
- May need @EnableJpaAuditing config for BaseEntity timestamps

**compose.yml**
- PostgreSQL 18 with health checks for local development

---

## Current Functionality

The application is a scaffolding shell with no business domain. It serves a health check endpoint and forwards SPA routes. The database exists but has no tables beyond Liquibase's own tracking tables.

### Key Components/Functions

- **HealthController**: GET /api/health returns {"status": "UP"} -- the only API endpoint
- **SpaForwardController**: Catches all non-file, non-API routes and forwards to index.html
- **PluginRegistry**: In-memory plugin registration -- not relevant to this task
- **App.tsx**: Fetches /api/health and displays raw JSON -- placeholder UI

### Data Flow

```
Browser -> Vite Dev Server (:5173) -> proxy /api -> Spring Boot (:8080)
Browser -> Vite Dev Server (:5173) -> SPA routes -> index.html -> React Router (to be added)
Spring Boot -> JPA -> Hibernate -> PostgreSQL (via Docker Compose or TestContainers)
Liquibase -> db.changelog-master.yaml -> PostgreSQL schema management
```

---

## Dependencies

### Imports (What This Depends On)

- **spring-boot-starter-data-jpa**: JPA + Hibernate ORM (present)
- **spring-boot-starter-jooq**: jOOQ for complex queries (present, not yet used)
- **spring-boot-starter-liquibase**: Database migration (present, empty changelog)
- **spring-boot-starter-webmvc**: REST controllers (present)
- **postgresql**: JDBC driver (present)
- **spring-boot-starter-validation**: Bean Validation (MISSING -- needs adding to pom.xml)
- **@chakra-ui/react**: UI component library (MISSING -- needs npm install)
- **react-router-dom**: Client-side routing (MISSING -- needs npm install)

### Consumers (What Depends On This)

- **IntegrationTests.java**: Tests API endpoints via MockMvc -- new endpoints need test coverage
- **ApiLayerTests.java**: Tests controllers in isolation -- new controllers need unit tests
- **App.tsx**: Currently hardcoded to /api/health -- will be restructured entirely
- **SpaForwardController**: Will serve new frontend routes (no changes needed)

**Consumer Count**: 4 files directly impacted
**Impact Scope**: Low - scaffolding has minimal consumers; this is greenfield development

---

## Test Coverage

### Test Files

- **IntegrationTests.java**: Full-context tests with TestContainers (7 tests) -- health, SPA forwarding, Liquibase
- **ApiLayerTests.java**: @WebMvcTest controller isolation tests
- **AjApplicationTests.java**: Context loading test

### Coverage Assessment

- **Test count**: ~10 existing tests (none for product domain -- does not exist yet)
- **Gaps**: No domain tests exist. Need Category CRUD tests, Product CRUD tests, entity validation tests, relationship tests, error handling tests.

---

## Coding Patterns

### Naming Conventions

- **Entities**: Singular PascalCase (e.g., Category, Product) with @Table plural (categories, products)
- **Controllers**: *Controller with @RequestMapping("/api") prefix
- **Services**: *Service with @Transactional on write methods
- **Repositories**: *Repository extending JpaRepository
- **DTOs**: Java records with static factory from() methods
- **Files (backend)**: Match class name
- **Files (frontend)**: PascalCase .tsx for components, camelCase .ts for utilities
- **Packages**: pl.devstyle.aj.[layer].[domain]

### Architecture Patterns

- **Style**: Layered (api -> service -> repository -> entity)
- **State Management**: None yet (useState only in App.tsx)
- **ORM Strategy**: JPA for CRUD operations, jOOQ reserved for complex reads
- **Testing**: @SpringBootTest + TestContainers for integration, @WebMvcTest for controller isolation
- **Frontend Build**: Vite outputs to src/main/resources/static/, served by Spring Boot
- **Injection**: Constructor injection (final fields, single constructor -- no @Autowired on constructor)

---

## Complexity Assessment

| Factor | Value | Level |
|--------|-------|-------|
| File Count | 2 new entities + 2 controllers + 2 services + 2 repos + migrations + DTOs + frontend pages (~20-25 new files) | High |
| Dependencies | 3 new deps (validation, chakra, router) + existing stack | Low |
| Consumers | 4 existing files impacted | Low |
| Test Coverage | 0% for new domain (all new tests needed) | N/A (greenfield) |

### Overall: Moderate

The task is broad (full-stack CRUD for two related entities with UI) but architecturally straightforward. Complexity comes from volume (many files to create) rather than technical difficulty. The infrastructure is already in place. The main risk is ensuring all foundational abstractions (BaseEntity, error handling, API client) are built correctly since they will be reused by future domains.

---

## Key Findings

### Strengths
- All backend dependencies already present in pom.xml (except validation starter)
- Test infrastructure fully operational (TestContainers, MockMvc, both integration and unit test patterns)
- SPA forwarding already supports nested routes -- React Router will work immediately
- Clear existing patterns to follow (HealthController, IntegrationTests)
- Comprehensive coding standards documented in .maister/docs/standards/

### Concerns
- No BaseEntity exists yet -- must be created as the first foundational piece
- No GlobalExceptionHandler or error response structure -- needed before building controllers
- No @EnableJpaAuditing configured -- required for @CreatedDate/@LastModifiedDate on BaseEntity
- spring-boot-starter-validation not in pom.xml -- needed for @Valid, @NotBlank, @Size on DTOs
- Frontend has zero libraries beyond React -- Chakra UI and React Router need to be added and configured
- No API client utility on frontend -- raw fetch() pattern will not scale

### Opportunities
- This is the first domain feature -- foundational patterns established here will set the standard for all future domains
- Category-Product relationship is a good first test of the JPA standards (Set collections, LAZY fetch, bidirectional helpers)
- Can establish the DTO pattern (Java records with from() factory) that all future APIs will follow

---

## Impact Assessment

- **Primary changes**: New files -- Category entity, Product entity, BaseEntity, CategoryController, ProductController, CategoryService, ProductService, CategoryRepository, ProductRepository, DTOs, Liquibase migrations, frontend pages/components/hooks
- **Related changes**: pom.xml (add validation starter), package.json (add chakra + router), App.tsx (restructure with router + chakra provider), application.properties (JPA auditing config)
- **Test updates**: New integration tests for both CRUD APIs, new @WebMvcTest controller tests, entity validation tests

### Risk Level: Low-Medium

Low risk because this is greenfield development with no existing business logic to break. Medium risk factor comes from the breadth of the task (full-stack, two entities, foundational abstractions) and the fact that patterns established here become the template for all future development. Getting the BaseEntity, error handling, and DTO patterns right is important.

---

## Recommendations

### Implementation Strategy

Since this is creating a new capability on an empty scaffold, the recommended approach is:

1. **Foundation first**: Create BaseEntity, enable JPA auditing, add validation starter, create GlobalExceptionHandler + ErrorResponse
2. **Backend domain**: Category entity + migration + repository + service + DTO + controller, then Product (depends on Category via foreign key)
3. **Backend tests**: Integration tests for both CRUD APIs using the existing TestContainers setup
4. **Frontend foundation**: Install Chakra UI + React Router, set up provider/router in App.tsx, create API client utility
5. **Frontend pages**: Category list/form pages, Product list/form pages with hooks for data fetching
6. **End-to-end verification**: Ensure full flow works with Docker Compose PostgreSQL

### Patterns to Follow

- **Entities**: Follow .maister/docs/standards/backend/models.md strictly (BaseEntity with @MappedSuperclass, SEQUENCE generation, LAZY fetch, Set collections, business key equals/hashCode)
- **Controllers**: Mirror HealthController pattern with @RestController + @RequestMapping("/api/categories" and "/api/products")
- **DTOs**: Java records with validation annotations, static from(Entity) factory methods
- **Tests**: Mirror IntegrationTests.java pattern for CRUD endpoint tests
- **Migrations**: One changeset per table, reversible, snake_case column names

### Dependencies to Add

- **Backend**: `spring-boot-starter-validation` in pom.xml
- **Frontend**: `@chakra-ui/react`, `react-router-dom` via npm

---

## Next Steps

Invoke gap-analyzer to identify specific gaps between the current codebase state and the target implementation, producing a detailed gap list for specification and planning phases.
