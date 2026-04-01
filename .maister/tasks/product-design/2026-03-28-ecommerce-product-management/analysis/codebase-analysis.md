# Codebase Analysis Report

**Date**: 2026-03-28
**Task**: Adding ecommerce product management to an existing Spring Boot microkernel platform
**Description**: Products have name, description, photo, price, SKU, category. Need to understand existing entity models, API endpoint patterns, database migrations, frontend setup, and overall architecture.
**Analyzer**: codebase-analyzer skill (3 Explore agents: File Discovery, Code Analysis, Pattern Mining)

---

## Summary

The aj platform is a pre-alpha Spring Boot 4.0.5 microkernel application with Java 25, PostgreSQL, and a React 19 + TypeScript frontend. The core infrastructure (database migrations via Liquibase, test containers, API routing, SPA forwarding, plugin framework) is in place but contains zero business domain logic. Product management will be the first CRUD implementation, establishing foundational patterns (BaseEntity, service layer, DTO records, repository conventions) that all future features will follow.

---

## Files Identified

### Primary Files

**`src/main/java/pl/devstyle/aj/api/HealthController.java`** (17 lines)
- Only existing REST controller; serves as the template for ProductController
- Demonstrates `@RestController` + `@RequestMapping("/api")` pattern

**`src/main/resources/db/changelog/db.changelog-master.yaml`** (1 line)
- Empty Liquibase master changelog (`databaseChangeLog: []`)
- Product table migration will be the first changeset added here

**`src/main/frontend/src/App.tsx`** (20 lines)
- Root React component; currently only fetches /api/health
- Entry point for adding product management UI components

**`.maister/docs/standards/backend/models.md`** (348 lines)
- Comprehensive JPA entity modeling standards including BaseEntity pattern, sequence generation, enum handling, Lombok usage, soft delete, and collection types
- Directly governs how the Product entity must be structured

**`.maister/docs/standards/backend/api.md`** (28 lines)
- API design standards: plural nouns, RESTful methods, versioning, query parameters for filtering/sorting/pagination

### Related Files

**`src/main/java/pl/devstyle/aj/core/plugin/Plugin.java`** (9 lines)
- Plugin interface with onStart()/onStop() lifecycle hooks; product management could optionally be wrapped as a plugin

**`src/main/java/pl/devstyle/aj/core/plugin/PluginRegistry.java`** (27 lines)
- Constructor injection pattern to follow for service classes

**`src/main/java/pl/devstyle/aj/api/SpaForwardController.java`** (18 lines)
- Forwards non-API routes to React SPA; product frontend routes will be handled by this

**`src/test/java/pl/devstyle/aj/IntegrationTests.java`** (79 lines)
- Integration test template using @SpringBootTest + TestContainers + MockMvc
- Template for product integration tests

**`src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java`** (18 lines)
- PostgreSQL test container configuration imported by integration tests

**`src/test/java/pl/devstyle/aj/api/ApiLayerTests.java`** (45 lines)
- API layer test template for @WebMvcTest-style unit tests

**`src/main/frontend/vite.config.ts`** (18 lines)
- Vite config with API proxy to localhost:8080; no changes needed

**`.maister/docs/standards/backend/migrations.md`**
- Migration standards: reversible, small focused changes, zero-downtime awareness

**`.maister/docs/standards/backend/jooq.md`**
- jOOQ query standards for complex read operations (product listing, filtering, search)

---

## Current Functionality

There is no existing product management or any business domain logic. The codebase provides:

1. **Health endpoint** (`GET /api/health`) returning `{"status": "UP"}`
2. **SPA forwarding** for React Router client-side routes
3. **Plugin framework** with registry, descriptor, and lifecycle interface
4. **Empty database schema** with Liquibase infrastructure ready
5. **Test infrastructure** with TestContainers PostgreSQL and MockMvc

### Key Components/Functions

- **HealthController.health()**: Returns health status JSON; template for REST endpoints
- **PluginRegistry**: Spring-managed bean using constructor injection; pattern for service classes
- **Plugin interface**: Lifecycle hooks (onStart/onStop); optional integration point for product module
- **TestcontainersConfiguration**: PostgreSQL container setup for integration tests

### Data Flow

Current: `Client -> /api/* -> HealthController -> JSON response`
Current: `Client -> /non-api-path -> SpaForwardController -> index.html -> React SPA`

Target for products:
```
Client -> /api/products -> ProductController -> ProductService -> ProductRepository -> PostgreSQL
Client -> /products/* -> SpaForwardController -> React SPA -> fetch /api/products
```

---

## Dependencies

### Imports (What Products Will Depend On)

- **Spring Web** (`@RestController`, `@RequestMapping`): REST endpoint infrastructure
- **Spring Data JPA** (`JpaRepository`, `@Entity`): ORM and repository
- **Liquibase**: Database migration management
- **PostgreSQL**: Primary datastore
- **jOOQ** (optional): Complex product queries (filtering, search, pagination)
- **Jakarta Validation** (`@NotBlank`, `@Positive`): Request validation
- **Lombok** (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@Builder`): Entity boilerplate reduction

### Consumers (What Will Depend On Products)

- **Frontend (React)**: Will consume product REST API
- **Future modules**: Orders, inventory, cart will reference products

**Consumer Count**: 1 file currently (frontend App.tsx will be modified), plus new files
**Impact Scope**: Low - This is greenfield; no existing consumers to break

---

## Test Coverage

### Test Files

- **IntegrationTests.java**: 7 tests covering health endpoint, plugin registry, SPA forwarding, Liquibase; template for product integration tests
- **ApiLayerTests.java**: API layer test template; template for ProductController unit tests

### Coverage Assessment

- **Test count**: 0 tests for products (new feature)
- **Existing patterns**: Strong test infrastructure with TestContainers and MockMvc
- **Gaps**: No testing standards document initialized yet; product tests will establish the testing patterns

---

## Coding Patterns

### Naming Conventions

- **Entities**: Singular PascalCase (`Product`, `Customer`)
- **Tables**: Plural lowercase (`products`, `customers`)
- **Controllers**: `{Entity}Controller` in `api/` package
- **Services**: `{Entity}Service` in `services/` package (projected)
- **Repositories**: `{Entity}Repository` in `domain/repositories/` (projected)
- **DTOs**: Java records (`CreateProductRequest`, `ProductResponse`)
- **Endpoints**: Plural nouns (`/api/products`)
- **Packages**: lowercase dot-separated (`pl.devstyle.aj.api`)

### Architecture Patterns

- **Style**: Spring Boot monolith with plugin-based extensibility planned
- **Dependency Injection**: Constructor injection (see PluginRegistry)
- **Entity Model**: BaseEntity @MappedSuperclass with SEQUENCE IDs, @Version optimistic locking
- **Database**: JPA for CRUD, jOOQ for complex reads
- **Frontend**: React 19 + TypeScript + Vite, fetches JSON from /api/*
- **Testing**: @SpringBootTest + TestContainers for integration, @WebMvcTest for unit

---

## Complexity Assessment

| Factor | Value | Level |
|--------|-------|-------|
| Files to Create | ~8-10 new files (entity, repo, service, controller, DTOs, migration, tests) | Medium |
| Files to Modify | 2-3 existing files (changelog, App.tsx, possibly routes) | Low |
| Dependencies | 5-7 Spring/JPA imports | Medium |
| Consumers | 0 existing (greenfield) | Low |
| Test Coverage | 0 existing, need to create from scratch | Medium |

### Overall: Moderate

This is the first domain feature in a greenfield codebase. The complexity is moderate because: (1) there are no existing patterns to follow -- every pattern created here becomes the standard; (2) BaseEntity and auditing infrastructure must be created first; (3) both backend and frontend work is required; (4) the Liquibase migration will be the first real schema change. However, risk is reduced because there is nothing to break and the standards documents provide clear guidance.

---

## Key Findings

### Strengths
- Comprehensive coding standards already documented (models.md is 348 lines of detailed JPA guidance)
- Strong test infrastructure with TestContainers and MockMvc ready to use
- Clean separation of concerns in the existing code
- Liquibase migration infrastructure is configured and verified by existing tests
- Frontend build pipeline integrated via frontend-maven-plugin

### Concerns
- No existing CRUD implementation to reference -- every pattern must be established carefully as a precedent
- BaseEntity does not exist yet and must be created as a prerequisite
- No testing standards document exists yet
- No error handling infrastructure (exception handlers, error DTOs) exists
- No validation infrastructure exists
- Photo/image storage strategy is undefined (filesystem, S3, database BLOB)

### Opportunities
- Establish the canonical CRUD pattern that all future features follow
- Set up Spring Data JPA auditing configuration (@EnableJpaAuditing)
- Create shared infrastructure (BaseEntity, error handling, validation) that benefits all future modules
- Establish DTO pattern with Java records
- Define product category as an enum or a separate entity depending on business requirements

---

## Impact Assessment

- **Primary changes**: New files -- Product entity, ProductRepository, ProductService, ProductController, DTOs, Liquibase migration, BaseEntity
- **Related changes**: Liquibase master changelog (add changeset references), Spring configuration (enable JPA auditing), frontend (product components and API calls)
- **Test updates**: New integration tests for product CRUD, new API layer tests for ProductController

### Risk Level: Low-Medium

The risk is low-medium because this is entirely additive (no existing functionality to break) and well-guided by comprehensive standards. The medium aspect comes from the precedent-setting nature: patterns established here will propagate to all future features, so getting them right matters. Additionally, the photo/image storage approach needs a design decision before implementation.

---

## Recommendations

### Implementation Strategy

Since this is creating new capability in a greenfield codebase:

1. **Infrastructure first**: Create BaseEntity, enable @EnableJpaAuditing, set up global exception handling (@RestControllerAdvice)
2. **Entity and migration**: Create Product entity following models.md standards, add Liquibase changeset for products table with sequences
3. **Repository and service**: ProductRepository (JpaRepository), ProductService with @Transactional
4. **DTOs**: Java records for CreateProductRequest, UpdateProductRequest, ProductResponse with Jakarta Validation
5. **Controller**: ProductController with full CRUD endpoints at /api/products
6. **Tests**: Integration tests with TestContainers, API layer tests with MockMvc
7. **Frontend**: Product listing and management components in React

### Design Decisions Needed

- **Product category**: Enum (fixed set) vs. entity (dynamic, user-managed)
- **Photo storage**: URL reference (external), file system, S3/object storage, or database BLOB
- **SKU uniqueness**: Should SKU be the business key for equals/hashCode?
- **Soft delete**: Should products use the soft delete pattern from models.md?
- **Pagination**: Spring Data Pageable vs. custom jOOQ pagination for product listing

### Patterns to Follow

- BaseEntity with @MappedSuperclass, SEQUENCE generation, @Version
- @Enumerated(EnumType.STRING) for category (if enum)
- BigDecimal for price (never float/double)
- Lombok: @Getter, @Setter, @NoArgsConstructor, @Builder (no @Data on entities)
- Business key equals/hashCode (likely SKU)
- Constructor injection throughout
- Java records for DTOs

---

## Next Steps

The orchestrator should proceed to gap analysis to identify specific design decisions that need resolution (category modeling, photo storage, SKU handling) before creating a detailed implementation specification.
