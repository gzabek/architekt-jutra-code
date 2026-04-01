# Implementation Plan: Ecommerce Product Management

## Overview
Total Steps: 32
Task Groups: 6
Expected Tests: 17-34 (8 category + 9 product integration tests from groups, up to 10 additional from test review)

## Implementation Steps

### Task Group 1: Backend Foundation & Dependencies
**Dependencies:** None
**Estimated Steps:** 6

- [x] 1.0 Complete backend foundation layer
  - [x] 1.1 Add backend dependencies to pom.xml
    - Add `spring-boot-starter-validation` for @Valid, @NotBlank, @Size, @Positive
    - Add `org.projectlombok:lombok` with `<scope>provided</scope>`
    - Configure Lombok annotation processor in `maven-compiler-plugin` `<annotationProcessorPaths>`
  - [x] 1.2 Create BaseEntity abstract class
    - File: `src/main/java/pl/devstyle/aj/core/BaseEntity.java`
    - @MappedSuperclass, @EntityListeners(AuditingEntityListener.class)
    - id: Long, @GeneratedValue(strategy=SEQUENCE, generator="base_seq"), allocationSize=1
    - createdAt: LocalDateTime, @CreatedDate, @Column(updatable=false)
    - updatedAt: LocalDateTime, @Version
    - Lombok: @Getter, @Setter, @NoArgsConstructor (no @Data, no @EqualsAndHashCode)
    - Do NOT define equals/hashCode here -- subclasses define business key
  - [x] 1.3 Create JPA Auditing configuration
    - File: `src/main/java/pl/devstyle/aj/core/JpaAuditingConfig.java`
    - @Configuration @EnableJpaAuditing -- enables @CreatedDate on BaseEntity
  - [x] 1.4 Create ErrorResponse record
    - File: `src/main/java/pl/devstyle/aj/core/error/ErrorResponse.java`
    - Java record with: status (int), error (String), message (String), fieldErrors (Map<String, String>, nullable), timestamp (LocalDateTime)
    - fieldErrors is populated only for 400 validation errors (audit finding resolved)
  - [x] 1.5 Create EntityNotFoundException
    - File: `src/main/java/pl/devstyle/aj/core/error/EntityNotFoundException.java`
    - Custom RuntimeException in core/error/ package (not JPA's)
    - Constructor takes entity type name and ID for descriptive message
  - [x] 1.6 Create GlobalExceptionHandler
    - File: `src/main/java/pl/devstyle/aj/core/error/GlobalExceptionHandler.java`
    - @RestControllerAdvice
    - Handle EntityNotFoundException -> 404
    - Handle DataIntegrityViolationException -> 409
    - Handle MethodArgumentNotValidException -> 400 with fieldErrors map (per-field errors from BindingResult)
    - Handle CategoryDeleteException (or similar) -> 409 with descriptive message

**Acceptance Criteria:**
- pom.xml compiles with `mvn compile` (Lombok + validation resolved)
- BaseEntity, JpaAuditingConfig, ErrorResponse, EntityNotFoundException, GlobalExceptionHandler all compile
- Foundation classes follow standards in `.maister/docs/standards/backend/models.md` and `.maister/docs/standards/global/error-handling.md`

---

### Task Group 2: Database Migrations & Category Backend
**Dependencies:** Group 1
**Estimated Steps:** 7

- [x] 2.0 Complete database migrations and category backend
  - [x] 2.1 Write 8 integration tests for Category CRUD
    - File: `src/test/java/pl/devstyle/aj/category/CategoryIntegrationTests.java`
    - @SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration.class) + @Transactional
    - Reuse: TestcontainersConfiguration.java, IntegrationTests.java pattern
    - 8 test cases:
      1. Create category -> 201, returns CategoryResponse with id, name, description, timestamps
      2. List categories -> 200, returns array
      3. Get category by ID -> 200
      4. Get non-existent category -> 404 with ErrorResponse
      5. Update category -> 200 with updated fields
      6. Delete category -> 204
      7. Delete category with products -> 409 (this test will set up product data)
      8. Create duplicate name -> 409
    - Inject CategoryRepository (and ProductRepository for test 7) directly for setup
    - Use MockMvc for HTTP assertions with JSON path matchers
  - [x] 2.2 Create Liquibase changesets
    - File: `src/main/resources/db/changelog/001-create-categories-table.yaml`
      - Create sequence: category_seq (start=1, increment=1)
      - Create table: categories (id BIGINT PK, name VARCHAR(100) NOT NULL, description VARCHAR(500), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)
      - Add unique constraint on name
      - Rollback: drop table, drop sequence
    - File: `src/main/resources/db/changelog/002-create-products-table.yaml`
      - Create sequence: product_seq (start=1, increment=1)
      - Create table: products (id BIGINT PK, name VARCHAR(255) NOT NULL, description VARCHAR(2000), photo_url VARCHAR(500), price DECIMAL(19,2) NOT NULL, sku VARCHAR(50) NOT NULL, category_id BIGINT NOT NULL FK->categories(id), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)
      - Add unique constraint on sku, index on category_id
      - Rollback: drop table, drop sequence
    - Update `db.changelog-master.yaml` to include both changesets (categories first, products second)
  - [x] 2.3 Create Category entity
    - File: `src/main/java/pl/devstyle/aj/category/Category.java`
    - Extends BaseEntity, @Entity, @Table(name="categories")
    - @SequenceGenerator(name="category_seq", sequenceName="category_seq", allocationSize=1)
    - Fields: name (String, @Column nullable=false, length=100), description (String, @Column length=500)
    - Business key equals/hashCode on `name`
    - Lombok: @Getter, @Setter, @NoArgsConstructor
  - [x] 2.4 Create Category DTOs
    - CreateCategoryRequest: record with @NotBlank @Size(max=100) name, @Size(max=500) description
    - UpdateCategoryRequest: record with @NotBlank @Size(max=100) name, @Size(max=500) description
    - CategoryResponse: record with id, name, description, createdAt, updatedAt + static `from(Category)` factory
  - [x] 2.5 Create CategoryRepository
    - File: `src/main/java/pl/devstyle/aj/category/CategoryRepository.java`
    - Extends JpaRepository<Category, Long>
    - Default findAll with Sort parameter for ordering (default createdAt DESC per audit)
  - [x] 2.6 Create CategoryService
    - File: `src/main/java/pl/devstyle/aj/category/CategoryService.java`
    - @Service, constructor injection (final CategoryRepository, final reference to check products)
    - findAll(): returns List<CategoryResponse>, sorted by createdAt DESC
    - findById(Long): returns CategoryResponse, throws EntityNotFoundException if not found
    - create(CreateCategoryRequest): @Transactional, returns CategoryResponse (201)
    - update(Long, UpdateCategoryRequest): @Transactional, returns CategoryResponse
    - delete(Long): @Transactional, checks for associated products before delete, throws 409 if found
    - Cross-package: injects ProductRepository to check product count for delete protection
  - [x] 2.7 Create CategoryController
    - File: `src/main/java/pl/devstyle/aj/category/CategoryController.java`
    - @RestController @RequestMapping("/api/categories")
    - GET / -> list, GET /{id} -> getById, POST / -> create (201), PUT /{id} -> update, DELETE /{id} -> delete (204)
    - @Valid on request body params
    - Thin delegation to CategoryService
  - [x] 2.8 Ensure Category integration tests pass
    - Run ONLY CategoryIntegrationTests (8 tests)
    - Do NOT run entire test suite

**Acceptance Criteria:**
- All 8 CategoryIntegrationTests pass
- Liquibase migrations apply cleanly
- All CRUD operations return correct status codes (200, 201, 204, 404, 409)
- Validation errors return 400 with fieldErrors map
- Category with products returns 409 on delete attempt

---

### Task Group 3: Product Backend
**Dependencies:** Group 2
**Estimated Steps:** 6

- [x] 3.0 Complete product backend layer
  - [x] 3.1 Write 9 integration tests for Product CRUD
    - File: `src/test/java/pl/devstyle/aj/product/ProductIntegrationTests.java`
    - Same annotations as CategoryIntegrationTests
    - 9 test cases:
      1. Create product with valid category -> 201, response includes nested CategoryResponse
      2. Create product with non-existent category -> 404
      3. List products -> 200, returns array
      4. List products filtered by category (?category={id}) -> 200, filtered results
      5. List products with search (?search=term) -> 200, matching results (case-insensitive)
      6. Get product by ID -> 200, includes nested category
      7. Update product -> 200
      8. Delete product -> 204
      9. Create duplicate SKU -> 409
    - Setup: inject CategoryRepository and ProductRepository for test data creation
  - [x] 3.2 Create Product entity
    - File: `src/main/java/pl/devstyle/aj/product/Product.java`
    - Extends BaseEntity, @Entity, @Table(name="products")
    - @SequenceGenerator(name="product_seq", sequenceName="product_seq", allocationSize=1)
    - Fields: name (max 255, NOT NULL), description (max 2000), photoUrl (max 500), price (BigDecimal, NOT NULL, @Column precision=19 scale=2), sku (max 50, NOT NULL), category (@ManyToOne(fetch=LAZY), @JoinColumn(name="category_id", nullable=false))
    - Business key equals/hashCode on `sku`
    - Lombok: @Getter, @Setter, @NoArgsConstructor
  - [x] 3.3 Create Product DTOs
    - CreateProductRequest: record with @NotBlank name, @Size(max=2000) description, @Size(max=500) photoUrl, @NotNull @Positive price, @NotBlank @Size(max=50) sku, @NotNull categoryId
    - UpdateProductRequest: same validation as create
    - ProductResponse: record with all fields + nested CategoryResponse + static `from(Product)` factory
  - [x] 3.4 Create ProductRepository
    - File: `src/main/java/pl/devstyle/aj/product/ProductRepository.java`
    - Extends JpaRepository<Product, Long>
    - Custom query methods: findByCategoryId(Long), findByNameContainingIgnoreCase(String)
    - Combined filtering method or use Specification/JPQL for category + search + sort
    - countByCategoryId(Long) for delete protection check in CategoryService
  - [x] 3.5 Create ProductService
    - File: `src/main/java/pl/devstyle/aj/product/ProductService.java`
    - @Service, constructor injection (ProductRepository, CategoryRepository)
    - findAll(Long categoryId, String search, String sort): returns List<ProductResponse>, default sort createdAt DESC
    - findById(Long): returns ProductResponse with category loaded (JOIN FETCH or EntityGraph)
    - create(CreateProductRequest): @Transactional, validates category exists, returns ProductResponse
    - update(Long, UpdateProductRequest): @Transactional, validates category exists
    - delete(Long): @Transactional
  - [x] 3.6 Create ProductController
    - File: `src/main/java/pl/devstyle/aj/product/ProductController.java`
    - @RestController @RequestMapping("/api/products")
    - GET / with @RequestParam(required=false) for category, search, sort
    - GET /{id}, POST /, PUT /{id}, DELETE /{id}
    - @Valid on request bodies
  - [x] 3.7 Ensure Product integration tests pass
    - Run ONLY ProductIntegrationTests (9 tests)
    - Do NOT run entire test suite

**Acceptance Criteria:**
- All 9 ProductIntegrationTests pass
- Product CRUD works with all status codes (200, 201, 204, 404, 409)
- Filtering by category, search by name, and sort all work correctly
- ProductResponse includes nested CategoryResponse
- Default sort is createdAt DESC

---

### Task Group 4: Frontend Foundation
**Dependencies:** Group 1 (no backend dependency -- can start once foundation patterns are clear)
**Estimated Steps:** 5

- [x] 4.0 Complete frontend foundation layer
  - [x] 4.1 Write 3 smoke tests for frontend foundation
    - Verify theme renders without errors (ChakraProvider mounts)
    - Verify router renders default route (redirect to /products)
    - Verify AppShell layout renders sidebar and content area
    - Use lightweight approach: test that components render without throwing
  - [x] 4.2 Install frontend dependencies and configure theme
    - Run `npm install @chakra-ui/react react-router-dom` in `src/main/frontend/`
    - Check Chakra UI v3 peer dependencies and install any required (e.g., @emotion/react if needed)
    - Create `src/main/frontend/src/theme/index.ts`:
      - Brand color scale (Deep Teal #0D9488 range: brand.50-brand.900)
      - Accent color scale (Warm Amber #F59E0B range: accent.50-accent.900)
      - Component overrides: Button, Input focus ring, Table header
      - Global styles: body background #FAFAF9, system font family
      - Light mode only
  - [x] 4.3 Create API client and feature API modules
    - `src/main/frontend/src/api/client.ts`: base fetch wrapper (prepends /api/, JSON content type, error parsing)
    - `src/main/frontend/src/api/categories.ts`: getCategories, getCategory, createCategory, updateCategory, deleteCategory (typed)
    - `src/main/frontend/src/api/products.ts`: getProducts (with category/search/sort params), getProduct, createProduct, updateProduct, deleteProduct (typed)
    - Define TypeScript interfaces for all request/response types
  - [x] 4.4 Create layout components
    - `src/main/frontend/src/components/layout/AppShell.tsx`: sidebar (220px dark teal #134E4A) + header + content area, responsive (sidebar collapses to hamburger drawer on mobile)
    - `src/main/frontend/src/components/layout/Sidebar.tsx`: nav links for Products and Categories with icons, active state (amber right border + white text), "aj" logo with amber accent
    - `src/main/frontend/src/components/layout/Header.tsx`: breadcrumbs based on current route
    - `src/main/frontend/src/components/shared/ConfirmDialog.tsx`: delete confirmation modal (reusable)
    - `src/main/frontend/src/components/shared/EmptyState.tsx`: empty state display component
    - Reference mockups: `.maister/tasks/product-design/2026-03-28-ecommerce-product-management/analysis/mockups/`
  - [x] 4.5 Restructure entry point and create router
    - `src/main/frontend/src/router.tsx`: React Router v7 route definitions (/, /products, /products/new, /products/:id/edit, /categories, /categories/new, /categories/:id/edit), root redirects to /products, AppShell as layout route
    - `src/main/frontend/src/main.tsx`: wrap with ChakraProvider (custom theme) + RouterProvider
    - Remove or replace App.tsx placeholder content
  - [x] 4.6 Ensure frontend foundation tests pass
    - Run ONLY the 3 smoke tests from 4.1
    - Verify `npm run build` completes without errors

**Acceptance Criteria:**
- Frontend builds without errors
- Theme applies Deep Teal + Warm Amber colors
- AppShell renders with sidebar, header, and content area
- Router navigates between routes
- Sidebar collapses to hamburger drawer on mobile viewport
- API client modules export typed functions

---

### Task Group 5: Frontend Pages & Hooks
**Dependencies:** Group 3 (backend API must exist), Group 4 (frontend foundation)
**Estimated Steps:** 5

- [x] 5.0 Complete frontend pages and data hooks
  - [x] 5.1 Write 4 component tests for pages
    - CategoryListPage renders table with category data (mock API response)
    - ProductListPage renders table with product data and filter controls
    - CategoryFormPage renders form fields (name, description)
    - ProductFormPage renders form fields (name, SKU, price, category dropdown, description, photo URL)
  - [x] 5.2 Create custom hooks
    - `src/main/frontend/src/hooks/useCategories.ts`: manages loading/error/data state, provides fetch/create/update/remove, returns { data, loading, error, refetch, create, update, remove }
    - `src/main/frontend/src/hooks/useProducts.ts`: same pattern, additionally supports category filter, search term, sort field parameters
  - [x] 5.3 Create Category pages
    - `src/main/frontend/src/pages/CategoryListPage.tsx`: data table with color dot + name, truncated description, product count column (client-side count from products if available, or skip per out-of-scope note), date, edit/delete actions, amber warning banner about delete protection
    - `src/main/frontend/src/pages/CategoryFormPage.tsx`: single-column stack (max-width 600px), name input with uniqueness hint, description textarea, breadcrumb nav, reused for create and edit (detect via URL param)
    - Reference mockups: category-list-page.html, category-form-page.html
  - [x] 5.4 Create Product pages
    - `src/main/frontend/src/pages/ProductListPage.tsx`: data table with photo thumbnail (40x40, placeholder for null), name (bold), monospace SKU, teal price, colored category badge, date, edit/delete actions; search input, category filter dropdown, sortable column headers
    - `src/main/frontend/src/pages/ProductFormPage.tsx`: two-column grid (name + SKU row, price + category row), full-width description textarea, full-width photo URL with preview placeholder, breadcrumb nav, category dropdown populated from API, reused for create and edit
    - Reference mockups: product-list-page.html, product-form-page.html
  - [x] 5.5 Ensure frontend page tests pass
    - Run ONLY the 4 tests from 5.1
    - Verify full navigation flow manually: create category -> create product -> list -> edit -> delete

**Acceptance Criteria:**
- All 4 page component tests pass
- Category CRUD flow works end-to-end through the UI
- Product CRUD flow works end-to-end through the UI
- Search, category filter, and sort work on product list
- Delete confirmation dialog appears before deletion
- Form validation feedback displayed for invalid inputs
- UI matches mockup layout structure (approximate fidelity)

---

### Task Group 6: Test Review & Gap Analysis
**Dependencies:** All previous groups (1-5)
**Estimated Steps:** 4

- [x] 6.0 Review and fill critical test gaps
  - [x] 6.1 Review tests from previous groups
    - Group 2: 8 CategoryIntegrationTests
    - Group 3: 9 ProductIntegrationTests
    - Group 4: 3 frontend smoke tests
    - Group 5: 4 frontend page tests
    - Total existing: ~24 tests
  - [x] 6.2 Analyze gaps for this feature only
    - Check: validation edge cases not covered (e.g., blank name, negative price, null categoryId)
    - Check: sort parameter variations (name,asc / price,desc / createdAt,desc)
    - Check: search edge case (empty string, special characters)
    - Check: error response shape (fieldErrors map populated correctly for 400)
    - Check: concurrent edit scenario (@Version optimistic locking)
  - [x] 6.3 Write up to 10 additional strategic tests
    - Fill highest-priority gaps identified in 6.2
    - Focus on backend integration tests (they provide most confidence)
    - Do NOT exceed 10 additional tests
  - [x] 6.4 Run feature-specific tests only
    - Run CategoryIntegrationTests + ProductIntegrationTests + any new test classes
    - Expect 17-34 total backend tests passing
    - Do NOT run entire project test suite

**Acceptance Criteria:**
- All feature tests pass (expected 24-34 total)
- No more than 10 additional tests added
- Critical validation and error paths have coverage
- No regressions in existing tests

---

## Execution Order

1. **Group 1: Backend Foundation & Dependencies** (6 steps) -- no dependencies
2. **Group 2: Database Migrations & Category Backend** (8 steps, depends on 1)
3. **Group 3: Product Backend** (7 steps, depends on 2)
4. **Group 4: Frontend Foundation** (6 steps, depends on 1 -- can run in parallel with Groups 2-3)
5. **Group 5: Frontend Pages & Hooks** (5 steps, depends on 3 and 4)
6. **Group 6: Test Review & Gap Analysis** (4 steps, depends on all previous)

### Parallelism Note
Groups 2-3 (backend) and Group 4 (frontend foundation) can execute in parallel after Group 1 completes. Group 5 requires both backend (for API) and frontend foundation to be done. In practice, sequential execution is simpler and recommended.

## Standards Compliance

Follow standards from `.maister/docs/standards/`:

### Always Applicable (global/)
- `error-handling.md` -- Clear user messages, fail-fast validation, typed exceptions, centralized handling
- `validation.md` -- Server-side validation, specific error messages, allowlists
- `conventions.md` -- Predictable file structure, clean version control
- `coding-style.md` -- Naming consistency, descriptive names, focused functions
- `commenting.md` -- Let code speak, comment sparingly
- `minimal-implementation.md` -- Build only what is needed, no future stubs

### Backend Specific
- `backend/models.md` -- BaseEntity @MappedSuperclass, SEQUENCE generation (allocationSize=1), LAZY fetch, business key equals/hashCode, Lombok (@Getter/@Setter/@NoArgsConstructor, no @Data), @Version optimistic locking, Set-based collections
- `backend/api.md` -- RESTful principles, plural nouns, query params for filtering/sorting, proper status codes
- `backend/migrations.md` -- Reversible migrations, small focused changes, descriptive names
- `backend/queries.md` -- Parameterized queries, N+1 avoidance (JOIN FETCH for ProductResponse with category)

### Frontend Specific
- `frontend/components.md` -- Single responsibility, reusability, clear interfaces
- `frontend/css.md` -- Work with Chakra UI framework, design tokens
- `frontend/accessibility.md` -- Semantic HTML, keyboard navigation, labels
- `frontend/responsive.md` -- Mobile-first, hamburger drawer pattern

## Audit Resolutions Applied

These findings from the spec audit are incorporated into this plan:

1. **Per-field validation errors**: ErrorResponse includes `fieldErrors: Map<String, String>` (Finding 5.2 resolved)
2. **Product count in category list**: Skipped per out-of-scope note; UI may show placeholder or omit column (Finding 3.1.3)
3. **Default sort**: createdAt DESC for both entities (Clarification Question 2 resolved)
4. **JPA Auditing config**: Explicit `core/JpaAuditingConfig.java` class (Finding 5.1 resolved)
5. **Lombok annotation processor**: Explicit maven-compiler-plugin config (Finding 6.1.1 resolved)

## Notes

- **Test-Driven**: Each group starts with tests (X.1), then implementation, then verify (X.n)
- **Run Incrementally**: Only run new tests after each group, not entire suite
- **Mark Progress**: Check off steps as completed in this file
- **Reuse First**: Prioritize existing components from spec (TestcontainersConfiguration, IntegrationTests pattern, HealthController pattern, SpaForwardController)
- **Pattern Precedent**: This is the first domain feature -- all patterns established here will be followed by future features. Get BaseEntity, ErrorResponse, DTO records, API client, and AppShell right.
- **Approximate Fidelity**: Match mockup layout structure and color scheme; exact pixel measurements are guidelines, not hard requirements. Use Chakra UI components to achieve equivalent visual results.
