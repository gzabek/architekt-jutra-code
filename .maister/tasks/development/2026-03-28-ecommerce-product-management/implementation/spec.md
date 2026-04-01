# Specification: Ecommerce Product Management

## Goal

Implement the first domain feature for the aj platform: a full-stack product catalog management system with Category and Product CRUD, establishing foundational patterns (BaseEntity, error handling, DTO records, API client, layout shell) that all future features will follow.

## User Stories

1. As an admin, I want to create and manage product categories so that I can organize the product catalog.
2. As an admin, I want to create and manage products with name, SKU, price, description, photo URL, and category so that I can maintain a product catalog.
3. As an admin, I want to search, filter by category, and sort the product list so that I can find products quickly.
4. As an admin, I want to be prevented from deleting a category that has products so that I do not accidentally orphan product data.

## Core Requirements

1. **Category CRUD** -- Create, list, view, edit, and delete categories with name (required, unique, max 100) and description (optional, max 500)
2. **Product CRUD** -- Create, list, view, edit, and delete products with name, SKU (unique), price (BigDecimal), category (required ManyToOne), optional description and photo URL
3. **Product list filtering** -- Filter by category (query param), search by name (case-insensitive LIKE), sort by name/price/sku/createdAt
4. **Category delete protection** -- Return 409 Conflict when deleting a category that has associated products
5. **Validation** -- Jakarta Bean Validation on all request bodies; return 400 with field errors for invalid input
6. **Consistent error responses** -- Global exception handler returning ErrorResponse record for 400, 404, 409 errors
7. **REST API** -- /api/categories and /api/products endpoints following RESTful conventions
8. **Database migrations** -- Liquibase YAML changesets for categories and products tables with sequences, constraints, and indexes
9. **Frontend UI** -- React + Chakra UI v3 with AppShell layout (sidebar + header + content), 4 pages (product list, product form, category list, category form)
10. **Custom theme** -- Deep Teal (#0D9488) primary + Warm Amber (#F59E0B) accent, light mode only
11. **Mobile responsive** -- Sidebar collapses to hamburger drawer on small screens
12. **Integration tests** -- @SpringBootTest + TestContainers + MockMvc for full CRUD coverage of both entities

## Visual Design

### Mockup References
- Product List: `.maister/tasks/product-design/2026-03-28-ecommerce-product-management/analysis/mockups/product-list-page.html`
- Product Form: `.maister/tasks/product-design/2026-03-28-ecommerce-product-management/analysis/mockups/product-form-page.html`
- Category List: `.maister/tasks/product-design/2026-03-28-ecommerce-product-management/analysis/mockups/category-list-page.html`
- Category Form: `.maister/tasks/product-design/2026-03-28-ecommerce-product-management/analysis/mockups/category-form-page.html`

### Key UI Elements (from mockups)
- **AppShell**: Dark teal sidebar (#134E4A) with amber "aj" logo, white content area on #FAFAF9 background
- **Sidebar nav**: Icon + text links for Products and Categories; active state has amber right border + white text
- **Product list table**: Photo thumbnail (40x40, placeholder icon for null), name (bold), monospace SKU, teal price, colored category badge, date, edit/delete actions
- **Product form**: Two-column grid layout (name + SKU row, price + category row), full-width description textarea, full-width photo URL with preview placeholder, breadcrumb navigation
- **Category list table**: Color dot + name, truncated description, product count column, date, edit/delete actions, amber warning banner about delete protection
- **Category form**: Single-column stack layout, narrower max-width (600px), name input with uniqueness hint, description textarea, breadcrumb navigation
- **Shared patterns**: Teal focus ring on inputs, rounded 8px borders, card-style forms with 12px border-radius, teal primary buttons, secondary outline buttons

### Fidelity Level
Approximate -- match the layout structure, color scheme, and component patterns from mockups. Exact pixel measurements are guidelines, not hard requirements. Use Chakra UI components to achieve equivalent visual results.

## Reusable Components

### Existing Code to Leverage

| Component | File Path | What It Provides |
|-----------|-----------|-----------------|
| HealthController | `src/main/java/pl/devstyle/aj/api/HealthController.java` | @RestController + @RequestMapping("/api") pattern template |
| SpaForwardController | `src/main/java/pl/devstyle/aj/api/SpaForwardController.java` | Already forwards all non-API/non-file routes to index.html -- React Router routes work automatically |
| TestcontainersConfiguration | `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java` | PostgreSQL 18 container with @ServiceConnection -- reuse via @Import for all integration tests |
| IntegrationTests | `src/test/java/pl/devstyle/aj/IntegrationTests.java` | @SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration.class) pattern template |
| Vite config | `src/main/frontend/vite.config.ts` | Build output to resources/static, /api proxy to localhost:8080 -- no changes needed |
| Liquibase master changelog | `src/main/resources/db/changelog/db.changelog-master.yaml` | Entry point for migrations -- currently empty, add changesets here |
| pom.xml | `pom.xml` | All core dependencies present (JPA, Liquibase, jOOQ, WebMVC, TestContainers, PostgreSQL) |
| package.json | `src/main/frontend/package.json` | React 19, Vite 8, TypeScript 5.9 -- add Chakra UI and React Router as new deps |

### New Components Required

| Component | Why New Code Is Needed |
|-----------|----------------------|
| BaseEntity.java | No abstract entity superclass exists; required as @MappedSuperclass for id, createdAt, updatedAt fields shared by all domain entities |
| JPA Auditing @Configuration | @EnableJpaAuditing not configured anywhere; required for @CreatedDate to work on BaseEntity |
| GlobalExceptionHandler + ErrorResponse | No centralized error handling exists; required for consistent 400/404/409 responses |
| Category entity + repository + service + controller + DTOs (7 files) | No business domain exists; greenfield feature |
| Product entity + repository + service + controller + DTOs (7 files) | No business domain exists; greenfield feature |
| Liquibase changesets (2) | No database tables exist (changelog is empty) |
| Chakra UI theme | No UI framework configured; Chakra UI not installed |
| API client (fetch wrapper) | No HTTP client utility exists; raw fetch in App.tsx is placeholder |
| Router config | No routing exists; React Router not installed |
| AppShell + Sidebar + Header | No layout components exist |
| ConfirmDialog + EmptyState | No shared UI components exist |
| 4 page components | No pages exist |
| 2 custom hooks + 2 API modules | No data fetching layer exists |
| 2 integration test classes | No domain tests exist |

## Technical Approach

### Backend

**Package structure**: Hybrid feature packages under `pl.devstyle.aj`:
- `core/` -- BaseEntity, `error/` subdirectory for GlobalExceptionHandler + ErrorResponse
- `category/` -- all Category files (entity, repository, service, controller, 3 DTOs)
- `product/` -- all Product files (entity, repository, service, controller, 3 DTOs)
- Existing `api/` (HealthController, SpaForwardController) and `core/plugin/` remain untouched

**Entity design** (per `models.md` standard):
- BaseEntity: @MappedSuperclass, @EntityListeners(AuditingEntityListener.class), id (Long, SEQUENCE, allocationSize=1), createdAt (@CreatedDate, updatable=false), updatedAt (@Version LocalDateTime)
- Each entity declares its own @SequenceGenerator at class level (overrides base_seq name)
- Business key equals/hashCode: Category uses `name`, Product uses `sku`
- Lombok: @Getter, @Setter, @NoArgsConstructor on entities (no @Data, no @EqualsAndHashCode)
- Product->Category: @ManyToOne(fetch=LAZY), no bidirectional mapping

**Service layer**:
- Constructor injection (final fields, single constructor)
- @Transactional on write methods only
- Receives request records, returns response records
- Throws EntityNotFoundException (custom, not JPA's) for 404 cases
- CategoryService checks for associated products before delete (409 if found)
- ProductService validates category exists before create/update

**Controller layer**:
- Returns ResponseEntity<XxxResponse> or ResponseEntity<List<XxxResponse>>
- Uses @Valid on request body parameters
- Thin delegation to service -- no business logic in controllers

**DTO pattern**:
- Java records with Jakarta validation annotations on request records
- Static `from(Entity)` factory method on response records
- ProductResponse nests a CategoryResponse (category loaded via JOIN FETCH or entity graph in service)

**Error handling**:
- @RestControllerAdvice GlobalExceptionHandler
- ErrorResponse record with status, error, message, timestamp
- Handles: EntityNotFoundException -> 404, DataIntegrityViolationException -> 409, MethodArgumentNotValidException -> 400, explicit 409 for category-with-products delete

### Database

**Migration strategy**:
- Liquibase YAML format in `src/main/resources/db/changelog/`
- Changeset 001: category_seq sequence + categories table + unique index on name
- Changeset 002: product_seq sequence + products table + unique index on sku + index on category_id + FK to categories(id)
- Both include rollback instructions (drop table, drop sequence)
- Add to db.changelog-master.yaml (currently empty array)

### Frontend

**Dependencies to add**: @chakra-ui/react (v3), react-router-dom (v7)

**Entry point restructure**: main.tsx wraps app with ChakraProvider (custom theme) + RouterProvider. App.tsx is replaced or removed.

**API client**: Base fetch wrapper in api/client.ts that prepends /api/, sets JSON content type, parses responses, throws typed errors. Feature modules (api/products.ts, api/categories.ts) export typed async functions.

**Custom hooks**: useProducts and useCategories manage loading/error/data state via useState + useEffect. Provide fetch, create, update, delete functions. Return { data, loading, error, refetch, create, update, remove }.

**Routing**: React Router v7 with routes for /, /products, /products/new, /products/:id/edit, /categories, /categories/new, /categories/:id/edit. Root redirects to /products.

**Layout**: AppShell component with Sidebar (220px, dark teal, collapsible to hamburger drawer on mobile) + Header (breadcrumbs) + content area. Sidebar shows Products and Categories nav links with icons.

**Pages**:
- ProductListPage: data table with search input, category filter dropdown, sortable column headers, photo thumbnails, category badges, edit/delete actions
- ProductFormPage: reused for create and edit (detect via URL param), two-column form grid, category dropdown populated from API, photo URL with preview placeholder
- CategoryListPage: data table with name, description, product count, edit/delete actions, delete-protection warning
- CategoryFormPage: reused for create and edit, single-column form, name + description fields

**Shared components**: ConfirmDialog (delete confirmation modal), EmptyState (shown when lists are empty)

### Testing

**Integration tests**: Two test classes extending the existing TestContainers pattern:
- CategoryIntegrationTests: @SpringBootTest + @Import(TestcontainersConfiguration.class) + @AutoConfigureMockMvc + @Transactional
  - 8 tests: create, list, get by ID, get non-existent (404), update, delete, delete with products (409), create duplicate name (409)
- ProductIntegrationTests: same annotations
  - 9 tests: create with valid category, create with non-existent category (404), list, list filtered by category, list with search, get by ID (includes nested category), update, delete, create duplicate SKU (409)
- Tests inject repositories directly for setup, use MockMvc for HTTP assertions
- @Transactional on test class for automatic rollback (fast, no context restart)

### Dependencies to Add

**Backend (pom.xml)**:
- `spring-boot-starter-validation` -- for @Valid, @NotBlank, @Size, @Positive
- `org.projectlombok:lombok` -- for @Getter, @Setter, @NoArgsConstructor on entities

**Frontend (package.json)**:
- `@chakra-ui/react` -- UI component library (v3)
- `react-router-dom` -- client-side routing (v7)

## Implementation Guidance

### Testing Approach
- 2-8 focused tests per implementation step group
- Category integration tests: 8 test cases covering all CRUD operations and edge cases (duplicate name, delete with products)
- Product integration tests: 9 test cases covering all CRUD operations, filtering, search, and edge cases (duplicate SKU, non-existent category)
- Test verification runs only new tests, not entire suite
- Use @Transactional on test classes for fast auto-rollback

### Standards Compliance

| Standard | Location | Relevance |
|----------|----------|-----------|
| JPA Entity Modeling | `.maister/docs/standards/backend/models.md` | BaseEntity pattern, SEQUENCE generation, LAZY fetch, business key equals/hashCode, Lombok usage, @Version for optimistic locking |
| API Design | `.maister/docs/standards/backend/api.md` | RESTful principles, plural nouns, query parameters for filtering/sorting, proper status codes |
| Database Migrations | `.maister/docs/standards/backend/migrations.md` | Reversible migrations, small focused changes, descriptive names |
| Error Handling | `.maister/docs/standards/global/error-handling.md` | Clear user messages, fail fast validation, typed exceptions, centralized handling |
| Minimal Implementation | `.maister/docs/standards/global/minimal-implementation.md` | Build only what is needed, no future stubs, every method has a caller |
| Coding Style | `.maister/docs/standards/global/coding-style.md` | Naming consistency, descriptive names, focused functions |
| Commenting | `.maister/docs/standards/global/commenting.md` | Let code speak through structure, comment sparingly for non-obvious logic |

## Out of Scope

- Cart, checkout, or customer-facing storefront
- Authentication or authorization
- Pagination (simple list responses for MVP)
- File upload (photo is URL reference only)
- Dark mode (light mode only for MVP)
- Soft delete (hard delete only)
- jOOQ queries (JPA only for this feature -- jOOQ reserved for future complex reads)
- @WebMvcTest controller unit tests (optional, lower priority -- integration tests are primary)
- Product count in category list response (UI can show it if backend provides it, but not a hard requirement)

## Success Criteria

1. **End-to-end flow works**: Create a category, create a product in that category, view/edit/delete from the UI
2. **All REST endpoints respond correctly**: Proper status codes (200, 201, 204, 400, 404, 409) for all operations
3. **Validation enforced**: Invalid requests return 400 with specific field errors
4. **Category delete protection**: Attempting to delete a category with products returns 409 with descriptive message
5. **Search and filter work**: Product list filters by category, searches by name, sorts by column
6. **UI matches mockup structure**: AppShell layout with teal sidebar, branded theme colors, data tables, forms with validation feedback
7. **Mobile responsive**: Sidebar collapses to hamburger drawer on small screens
8. **All integration tests pass**: 8 category tests + 9 product tests green against TestContainers PostgreSQL
9. **Database migrations execute cleanly**: Liquibase applies both changesets without errors
10. **Patterns are reusable**: BaseEntity, GlobalExceptionHandler, ErrorResponse, API client, AppShell, and custom hooks are structured for reuse by future features
