# Codebase Analysis Report

**Date**: 2026-03-28
**Task**: Implement plugin system with iframe-isolated web apps communicating via postMessage RPC
**Description**: Implement a plugin system with iframe-isolated web apps communicating via postMessage RPC. Backend provides REST APIs for plugin registry (manifest-based), product plugin data (JSONB on products), and custom objects. Frontend provides iframe hosting, message handling, extension point rendering (menu items, product detail tabs, product list filters), and a plugin SDK built as standalone TypeScript bundle.
**Analyzer**: codebase-analyzer skill (4 Explore agents: File Discovery, Code Analysis, Pattern Mining, Context Discovery)

---

## Summary

The codebase is a pre-alpha Spring Boot 4 + React 19 application with clean CRUD patterns for products and categories. A plugin foundation already exists (`PluginRegistry`, `Plugin`, `PluginDescriptor` interfaces) but is empty and designed for Java-class plugins, not the iframe-based manifest-driven system specified. A detailed design document (`plugin-system-design.md`) provides comprehensive specifications for the target architecture, including database schema, REST APIs, communication protocol, SDK design, and extension points. The implementation requires significant new code across both backend and frontend, plus modifications to existing product entities and UI components.

---

## Files Identified

### Primary Files (directly modified or replaced)

**src/main/java/pl/devstyle/aj/core/plugin/PluginRegistry.java** (27 lines)
- Currently collects Java `Plugin` beans via Spring DI
- Will be replaced entirely by a database-backed plugin registry

**src/main/java/pl/devstyle/aj/core/plugin/Plugin.java** (9 lines)
- Interface with `onStart()`/`onStop()` lifecycle hooks
- Will be replaced by `PluginDescriptor` JPA entity

**src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptor.java** (11 lines)
- Interface with `getPluginId()`, `getName()`, `getVersion()`
- Will be replaced by JPA entity with same name

**src/main/java/pl/devstyle/aj/product/Product.java** (57 lines)
- Needs `pluginData` JSONB field added

**src/main/java/pl/devstyle/aj/product/ProductResponse.java** (33 lines)
- Needs `pluginData` field added to response DTO

**src/main/frontend/src/components/layout/Sidebar.tsx** (69 lines)
- Hardcoded nav items for Products and Categories
- Needs dynamic plugin menu items from `menu.main` extension point

**src/main/frontend/src/router.tsx** (30 lines)
- Static route definitions
- Needs dynamic plugin routes for full-page plugin iframes

### Related Files (templates/patterns to follow)

**src/main/java/pl/devstyle/aj/core/BaseEntity.java** (36 lines)
- `@MappedSuperclass` with id (SEQUENCE), createdAt, @Version updatedAt
- `PluginObject` entity will extend this

**src/main/java/pl/devstyle/aj/product/ProductController.java** (57 lines)
- Template for controller patterns (CRUD, status codes, validation)

**src/main/java/pl/devstyle/aj/product/ProductService.java** (127 lines)
- Template for service patterns (@Transactional, DTOs, exception handling)

**src/main/java/pl/devstyle/aj/core/error/GlobalExceptionHandler.java** (90 lines)
- Centralized exception handling; may need new exception types for plugin errors

**src/main/frontend/src/api/client.ts** (64 lines)
- Fetch wrapper used by host message handler to proxy plugin requests

**src/main/frontend/src/api/products.ts** (63 lines)
- Template for API module pattern (typed interfaces + functions)

**src/main/frontend/src/hooks/useProducts.ts** (74 lines)
- Template for React hook pattern (state + CRUD + refetch)

**src/main/frontend/src/pages/ProductListPage.tsx** (336 lines)
- Needs plugin filter extension point integration

**src/main/frontend/src/pages/ProductFormPage.tsx** (266 lines)
- May need product detail tabs extension point (or a new ProductDetailPage)

**src/main/resources/db/changelog/2026/002-create-products-table.yaml**
- Template for migration file structure (YAML format, sequences, constraints)

**.maister/docs/project/plugin-system-design.md** (878 lines)
- Comprehensive design document specifying the entire plugin system

### Test Files (templates for new tests)

**src/test/java/pl/devstyle/aj/IntegrationTests.java**
- Base class for integration tests (TestContainers + MockMvc)

**src/test/java/pl/devstyle/aj/product/ProductIntegrationTests.java**
- Template for CRUD integration tests

**src/test/java/pl/devstyle/aj/core/plugin/PluginRegistryTests.java**
- Existing tests for current PluginRegistry; will need complete rewrite

---

## Current Functionality

### Backend

The application provides full CRUD for products and categories with:
- RESTful controllers at `/api/products` and `/api/categories`
- Service layer with `@Transactional` read/write separation
- Java record DTOs for requests and responses
- Jakarta Bean Validation on inputs
- Global exception handling (EntityNotFoundException, BusinessConflictException, validation errors)
- Liquibase migrations in YAML format
- No JSONB usage anywhere currently -- all data is normalized relational
- No CORS configuration
- No security/authentication

The existing plugin foundation (`core/plugin/`) consists of three files designed for a Java-class plugin model (Spring bean collection). This is incompatible with the target iframe-based manifest-driven model and will be replaced.

### Frontend

A React 19 SPA with:
- Vite 8 build, Chakra UI 3 component library
- `AppShell` layout with `Sidebar` (hardcoded nav), `Header`, and content area via `Outlet`
- `createBrowserRouter` with static route definitions
- Fetch-based API client (`api/client.ts`) with typed error handling
- Custom hooks for data fetching (useState + useCallback + useEffect pattern)
- `SpaForwardController` on backend forwards all non-dotted paths to `index.html`

### Key Components/Functions

- **BaseEntity**: Abstract `@MappedSuperclass` with SEQUENCE id, `createdAt`, `@Version updatedAt`
- **ProductController**: Full CRUD + filtering (categoryId, search, sort)
- **ProductService**: Business logic, sort field validation, `@Transactional`
- **GlobalExceptionHandler**: `@RestControllerAdvice` for 400/404/409/500 responses
- **api/client.ts**: `api.get/post/put/delete` with `/api` prefix, `ApiError` class
- **Sidebar NavItem**: Hardcoded links with active state detection via `useLocation()`

### Data Flow

```
Frontend (React) --fetch--> /api/* --> Controller --> Service --> Repository --> PostgreSQL
                                          |
                                  DTO conversion (record.from())
```

Plugin system will add a parallel path:
```
Plugin iframe --postMessage--> Host Message Handler --fetch--> /api/plugins/* --> Backend
```

---

## Dependencies

### Backend Imports (What This Depends On)

- **Spring Boot 4.0.5**: WebMVC, JPA, Validation, Docker Compose
- **PostgreSQL**: Primary database (via TestContainers in tests)
- **Liquibase**: Database migrations
- **jOOQ**: Available but not yet used (complex queries)
- **Lombok**: @Getter, @Setter, @NoArgsConstructor on entities
- **Jackson**: JSON serialization (implicit via Spring Boot) -- needed for JSONB mapping
- **Hibernate**: JPA provider -- `@JdbcTypeCode(SqlTypes.JSON)` needed for JSONB columns

### Frontend Imports (What This Depends On)

- **React 19.2.4**: UI framework
- **React Router 7.13.2**: Client-side routing
- **Chakra UI 3.34.0**: Component library
- **Vite 8.0.1**: Build tool -- library mode needed for SDK bundle
- **TypeScript 5.9.3**: Type safety

### Consumers (What Depends On This)

- **ProductResponse**: Used by ProductListPage, ProductFormPage, useProducts hook -- adding `pluginData` field affects all consumers
- **Sidebar.tsx**: Used by AppShell.tsx -- changes to nav items affect the entire layout
- **router.tsx**: Used by main.tsx -- adding routes affects the entire app
- **api/client.ts**: Used by products.ts, categories.ts -- host message handler will reuse this

**Consumer Count**: 8-10 files will need modifications
**Impact Scope**: Medium - changes touch product responses (used widely), layout (Sidebar), and routing, but patterns are well-established

---

## Test Coverage

### Existing Test Files

- **IntegrationTests.java**: Base class with TestContainers + MockMvc
- **ProductIntegrationTests.java**: Product CRUD integration tests
- **ProductValidationTests.java**: Product input validation tests
- **CategoryIntegrationTests.java**: Category CRUD integration tests
- **CategoryValidationTests.java**: Category input validation tests
- **PluginRegistryTests.java**: Tests for current (soon-replaced) PluginRegistry
- **ApiLayerTests.java**: API layer tests
- **AjApplicationTests.java**: Application context loads

### Coverage Assessment

- **Existing test count**: 8 test files covering current CRUD functionality
- **Pattern**: Integration-first with TestContainers, MockMvc + jsonPath assertions
- **Gaps for plugin system**: All new code (plugin registry, plugin data, plugin objects, message handler, SDK) will need new tests
- **Estimated new test files**: 5-7 (PluginIntegrationTests, PluginDataIntegrationTests, PluginObjectIntegrationTests, PluginValidationTests, plus frontend tests)

---

## Coding Patterns

### Naming Conventions

- **Entities**: Singular noun, PascalCase (Product, Category)
- **Controllers**: `{Entity}Controller` with `@RequestMapping("/api/{entities}")`
- **Services**: `{Entity}Service`
- **Repositories**: `{Entity}Repository`
- **DTOs**: `Create{Entity}Request`, `Update{Entity}Request`, `{Entity}Response` (Java records)
- **Tests**: `{Entity}IntegrationTests`, `{Entity}ValidationTests`
- **Frontend API**: `{entities}.ts` with typed interfaces
- **Frontend hooks**: `use{Entities}.ts`
- **Frontend pages**: `{Entity}ListPage.tsx`, `{Entity}FormPage.tsx`

### Architecture Patterns

- **Style**: Layered architecture -- Controller > Service > Repository
- **DTOs**: Java records with static `from()` factory methods
- **Validation**: Jakarta Bean Validation on request records
- **Error handling**: Typed exceptions caught by `@RestControllerAdvice`
- **Database**: Liquibase YAML migrations, SEQUENCE-based IDs
- **Frontend state**: Local state in custom hooks (no global state management)
- **Frontend routing**: React Router with layout wrapper pattern

### Backend Entity Pattern

New entities should follow:
1. Extend `BaseEntity` (or use custom ID strategy like `PluginDescriptor` with String PK)
2. `@SequenceGenerator` for auto-generated IDs
3. Business key `equals()`/`hashCode()` (never entity ID)
4. `@Getter @Setter @NoArgsConstructor` (no `@Data`)
5. Explicit fetch types (LAZY default)

---

## Complexity Assessment

| Factor | Value | Level |
|--------|-------|-------|
| Files to create | ~20 new files (backend + frontend + SDK) | High |
| Files to modify | ~10 existing files | Medium |
| Backend dependencies | Spring JPA, JSONB/Hibernate types, Jackson | Medium |
| Frontend dependencies | postMessage API, iframe sandbox, Vite library mode | Medium |
| Consumers affected | 8-10 files (product response, layout, routing) | Medium |
| Test coverage needed | 5-7 new test files | Medium |
| Migration files | 3 new migrations (004, 005, 006) | Low |

### Overall: Complex

This is a multi-layered feature spanning backend entities/APIs, database migrations, frontend components, a communication protocol, and a standalone SDK bundle. The design document is thorough, which reduces ambiguity, but the implementation surface is large. The lack of existing JSONB usage means the JSONB pattern needs to be established from scratch.

---

## Key Findings

### Strengths
- Comprehensive design document (`plugin-system-design.md`) with detailed specifications for every component
- Clean, consistent codebase patterns that new code can follow reliably
- Existing plugin foundation (`core/plugin/` package) provides the right package location even though the implementation will change
- Well-structured test infrastructure with TestContainers ready for integration testing
- DevSkiller reference implementations available in adjacent repositories for cross-referencing

### Concerns
- The existing `PluginRegistry`, `Plugin`, and `PluginDescriptor` interfaces must be replaced, not extended -- this is a full rewrite of `core/plugin/`
- No JSONB usage exists in the codebase yet -- the `@JdbcTypeCode(SqlTypes.JSON)` + Hibernate pattern needs to be established and verified with PostgreSQL
- `PluginDescriptor` entity uses a String PK (`id = pluginId`), which breaks from the `BaseEntity` SEQUENCE pattern -- needs careful handling
- No CORS configuration exists -- iframe cross-origin communication may require it depending on plugin hosting
- The product detail tabs extension point requires a product detail view page that does not currently exist (only list and form pages exist)
- Vite library mode for SDK bundle is a separate build configuration that needs integration with the Maven frontend plugin

### Opportunities
- Establish the JSONB pattern once (on Product.pluginData) and reuse for PluginObject.data and PluginDescriptor.manifest
- The design document's handler registry pattern (from DevSkiller reference) promotes clean message routing over a monolithic switch
- Plugin system infrastructure (message handler, iframe registry) can be designed as reusable React context/hooks

---

## Impact Assessment

- **Primary changes**:
  - Replace `core/plugin/` (3 files: PluginRegistry, Plugin, PluginDescriptor)
  - New backend: PluginDescriptor entity, PluginObject entity, 3 controllers, 3 services, 2 repositories (~12 new files)
  - New frontend: PluginFrame, PluginMessageHandler, PluginRegistry context, extension point components (~6 new files)
  - New plugin SDK: 6 TypeScript files + Vite build config
  - New migrations: 3 YAML files (004, 005, 006)
- **Modified files**:
  - Product.java (add pluginData field)
  - ProductResponse.java (add pluginData field)
  - Sidebar.tsx (dynamic plugin menu items)
  - router.tsx (dynamic plugin routes)
  - ProductListPage.tsx (plugin filter extension point)
  - Vite config (SDK library build)
  - Liquibase changelog master (include new migrations)
- **Test updates**:
  - Rewrite PluginRegistryTests.java
  - New: PluginIntegrationTests, PluginDataIntegrationTests, PluginObjectIntegrationTests
  - New: PluginValidationTests
  - Update ProductIntegrationTests if pluginData changes response shape

### Risk Level: Medium

The design is well-specified which significantly reduces implementation risk. However, the scope is broad (backend + frontend + SDK + protocol), JSONB is a new pattern for this codebase, and the iframe/postMessage communication layer is inherently complex to test. The `PluginDescriptor` entity's String PK diverges from `BaseEntity`, requiring careful design. No security concerns since auth is explicitly a non-goal.

---

## Recommendations

### Implementation Strategy

This is a new capability build on an existing codebase with a thorough design document. Recommended approach:

1. **Backend first, bottom-up**: Start with database migrations and entities to establish the JSONB pattern early. Order: (a) plugins table + PluginDescriptor entity, (b) plugin_data column on products, (c) plugin_objects table + entity.

2. **API layer second**: Build controllers and services for plugin registry (manifest upload), plugin data (product JSONB operations), and custom objects. Each follows established CRUD patterns.

3. **Frontend infrastructure third**: Build the iframe hosting (`PluginFrame`), message handler, and iframe registry as reusable components. Integrate with existing layout.

4. **Extension points fourth**: Add dynamic menu items to Sidebar, plugin routes to router, and filter/tab extension points to product pages.

5. **Plugin SDK last**: Build as standalone TypeScript bundle via Vite library mode. This depends on the message protocol being stable.

### Backward Compatibility

- Adding `pluginData` (nullable) to Product entity and response is additive -- existing consumers will see a `null` field
- Sidebar changes should render existing hardcoded items first, then append plugin menu items
- Route changes should preserve existing routes and add plugin routes alongside them

### Testing Strategy

- Integration tests with TestContainers for all backend APIs (following existing pattern)
- Test JSONB read/write round-trips explicitly (new pattern for codebase)
- Frontend: test message handler with mock MessageEvent objects
- Consider an end-to-end smoke test with a minimal test plugin iframe

### Key Design Decisions to Confirm

- `PluginDescriptor` entity with String PK vs. extending BaseEntity with a separate `pluginId` unique column
- Whether to create a new `ProductDetailPage` for the product detail tabs extension point
- SDK build integration: separate Vite config (`vite.sdk.config.ts`) vs. multi-entry in main config

---

## Next Steps

Proceed to gap analysis to identify specific implementation gaps between the current codebase state and the target design, then create a detailed specification and implementation plan.
