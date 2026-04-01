# Gap Analysis: Ecommerce Product Management

## Summary
- **Risk Level**: Low-Medium
- **Estimated Effort**: High (volume, not complexity -- ~25 new files across full stack)
- **Detected Characteristics**: creates_new_entities, involves_data_operations, ui_heavy

## Task Characteristics
- Has reproducible defect: no
- Modifies existing code: yes (minor -- pom.xml, package.json, App.tsx/main.tsx, application.properties, changelog)
- Creates new entities: yes (this is the primary characteristic -- greenfield domain feature)
- Involves data operations: yes (full CRUD for Category and Product)
- UI heavy: yes (4 pages, layout shell, custom theme, routing)

## Gaps Identified

### Missing Features (Everything Below Must Be Created)

#### Backend Foundation (core/)
1. **BaseEntity.java** -- `@MappedSuperclass` with id (SEQUENCE), createdAt (`@CreatedDate`), updatedAt (`@Version`). Does not exist. Required by both Category and Product entities.
2. **JPA Auditing Configuration** -- `@EnableJpaAuditing` on a `@Configuration` class. Not present anywhere. Required for `@CreatedDate` to work.
3. **GlobalExceptionHandler.java** -- `@RestControllerAdvice` for consistent error responses. Does not exist.
4. **ErrorResponse.java** -- Java record for error response body. Does not exist.

#### Backend Dependencies
5. **spring-boot-starter-validation** -- Not in pom.xml. Required for `@Valid`, `@NotBlank`, `@Size`, `@Positive` on DTOs.
6. **Lombok** -- Not in pom.xml. Required for `@Getter`, `@Setter`, `@NoArgsConstructor` on entities per standards.

#### Category Domain (category/)
7. **Category.java** -- JPA entity with name (unique), description. Does not exist.
8. **CategoryRepository.java** -- Spring Data JPA repository. Does not exist.
9. **CategoryService.java** -- Service with CRUD business logic. Does not exist.
10. **CategoryController.java** -- REST controller at /api/categories. Does not exist.
11. **CreateCategoryRequest.java** -- Request record with validation. Does not exist.
12. **UpdateCategoryRequest.java** -- Request record with validation. Does not exist.
13. **CategoryResponse.java** -- Response record with `from(Category)` factory. Does not exist.

#### Product Domain (product/)
14. **Product.java** -- JPA entity with name, description, photoUrl, price (BigDecimal), sku (unique), category (ManyToOne LAZY). Does not exist.
15. **ProductRepository.java** -- Spring Data JPA repository with query methods for filtering/search. Does not exist.
16. **ProductService.java** -- Service with CRUD + filtering/search/sort logic. Does not exist.
17. **ProductController.java** -- REST controller at /api/products with query params. Does not exist.
18. **CreateProductRequest.java** -- Request record with validation. Does not exist.
19. **UpdateProductRequest.java** -- Request record with validation. Does not exist.
20. **ProductResponse.java** -- Response record with nested CategoryResponse. Does not exist.

#### Database Migrations
21. **Changeset 001: categories table** -- Sequence + table + unique constraint. Changelog is empty (`databaseChangeLog: []`).
22. **Changeset 002: products table** -- Sequence + table + FK + indexes. Changelog is empty.

#### Frontend Dependencies
23. **@chakra-ui/react** -- Not in package.json. UI component library.
24. **react-router-dom** -- Not in package.json. Client-side routing.

#### Frontend Foundation
25. **theme/index.ts** -- Chakra UI custom theme (Deep Teal + Warm Amber brand colors). Does not exist.
26. **api/client.ts** -- Base fetch wrapper with error handling. Does not exist.
27. **router.tsx** -- React Router route definitions. Does not exist.
28. **components/layout/AppShell.tsx** -- Sidebar + header + content area layout. Does not exist.
29. **components/layout/Sidebar.tsx** -- Navigation links (Products, Categories). Does not exist.
30. **components/layout/Header.tsx** -- App title, breadcrumbs. Does not exist.
31. **components/shared/ConfirmDialog.tsx** -- Delete confirmation modal. Does not exist.
32. **components/shared/EmptyState.tsx** -- Empty state display. Does not exist.

#### Frontend API Layer
33. **api/categories.ts** -- Typed API functions for category CRUD. Does not exist.
34. **api/products.ts** -- Typed API functions for product CRUD. Does not exist.

#### Frontend Hooks
35. **hooks/useCategories.ts** -- State management hook for categories. Does not exist.
36. **hooks/useProducts.ts** -- State management hook for products. Does not exist.

#### Frontend Pages
37. **pages/ProductListPage.tsx** -- Table with search, category filter, sortable columns. Does not exist.
38. **pages/ProductFormPage.tsx** -- Create/edit form. Does not exist.
39. **pages/CategoryListPage.tsx** -- Category table. Does not exist.
40. **pages/CategoryFormPage.tsx** -- Create/edit form. Does not exist.

#### Testing
41. **CategoryIntegrationTests.java** -- Full CRUD integration tests (8 test cases). Does not exist.
42. **ProductIntegrationTests.java** -- Full CRUD + filtering integration tests (9 test cases). Does not exist.

### Files Requiring Modification

| File | Change | Impact |
|------|--------|--------|
| pom.xml | Add spring-boot-starter-validation + Lombok dependencies | Low -- additive only |
| package.json | Add @chakra-ui/react + react-router-dom | Low -- additive only |
| main.tsx | Wrap with ChakraProvider + RouterProvider | Medium -- restructure entry point |
| App.tsx | Replace entirely with router outlet or remove | Medium -- current content is placeholder |
| db.changelog-master.yaml | Add changeset includes/entries | Low -- currently empty |
| application.properties | Possibly no change needed (auditing via @Configuration class) | Low |

## New Capability Analysis

### Integration Points
- **Routes**: /api/categories (6 endpoints), /api/products (6 endpoints) -- new REST API surface
- **Navigation**: Sidebar links to /products and /categories -- new frontend routes
- **Database**: 2 new tables (categories, products) with FK relationship
- **Entry point**: main.tsx restructured with ChakraProvider + RouterProvider

### Patterns to Follow
- **Controller pattern**: HealthController.java provides @RestController + @RequestMapping template
- **Integration test pattern**: IntegrationTests.java provides @SpringBootTest + MockMvc + TestContainers template
- **Unit test pattern**: ApiLayerTests.java provides @WebMvcTest template
- **SPA routing**: SpaForwardController.java already forwards non-API routes to index.html

### Architectural Impact: Medium
- New package structure: core/, category/, product/ alongside existing api/ and core/plugin/
- ~25 new backend files, ~15 new frontend files
- First domain feature -- establishes patterns all future features will follow
- No existing business logic to break

## Data Lifecycle Analysis

### Entity: Category

| Operation | Backend | UI Component | User Access | Status |
|-----------|---------|--------------|-------------|--------|
| CREATE | MISSING -- no POST /api/categories | MISSING -- no CategoryFormPage | MISSING -- no /categories/new route | Needs creation |
| READ | MISSING -- no GET /api/categories | MISSING -- no CategoryListPage | MISSING -- no /categories route | Needs creation |
| UPDATE | MISSING -- no PUT /api/categories/{id} | MISSING -- no CategoryFormPage (edit mode) | MISSING -- no /categories/:id/edit route | Needs creation |
| DELETE | MISSING -- no DELETE /api/categories/{id} | MISSING -- no delete button/ConfirmDialog | MISSING -- no delete action in list | Needs creation |

**Completeness**: 0% (greenfield -- all operations need creation)
**Orphaned Operations**: N/A (nothing exists yet)

### Entity: Product

| Operation | Backend | UI Component | User Access | Status |
|-----------|---------|--------------|-------------|--------|
| CREATE | MISSING | MISSING | MISSING | Needs creation |
| READ | MISSING | MISSING | MISSING | Needs creation |
| UPDATE | MISSING | MISSING | MISSING | Needs creation |
| DELETE | MISSING | MISSING | MISSING | Needs creation |

**Completeness**: 0% (greenfield)
**Orphaned Operations**: N/A (nothing exists yet)

**Risk Note**: Since both entities are created together with full CRUD across all 3 layers (backend, UI component, user access), the spec correctly ensures no orphaned operations will exist post-implementation.

## UI Impact Analysis

### Navigation Paths
- / --> redirect to /products (default landing page)
- Sidebar: Products link --> /products (ProductListPage)
- Sidebar: Categories link --> /categories (CategoryListPage)
- ProductListPage: "Add Product" button --> /products/new
- ProductListPage: row click/edit --> /products/:id/edit
- CategoryListPage: "Add Category" button --> /categories/new
- CategoryListPage: row click/edit --> /categories/:id/edit

### Discoverability Score: 9/10
- Primary navigation via sidebar -- immediately visible
- Standard CRUD patterns (list -> add/edit buttons) -- highly discoverable
- Breadcrumbs in header for orientation

### Persona Impact
- Single admin user (no auth required for MVP)
- All features equally accessible -- no role-based concerns

## Issues Requiring Decisions

### Critical (Must Decide Before Proceeding)

None. The feature spec is comprehensive and the task is greenfield with no ambiguity about scope.

### Important (Should Decide)

1. **Lombok dependency missing from pom.xml**
   - The feature spec calls for `@Getter`, `@Setter`, `@NoArgsConstructor` on entities, and the models.md standard shows Lombok usage
   - Lombok is NOT currently in pom.xml
   - Options: [Add Lombok to pom.xml as specified] [Write getters/setters manually]
   - Recommendation: Add Lombok -- it is referenced in the models.md standard and the feature spec explicitly requires it
   - Default: Add Lombok

2. **Chakra UI version selection**
   - The spec says "Chakra UI" but does not specify v2 or v3
   - Chakra UI v3 (latest) has a different API than v2 (uses `@chakra-ui/react` + `@emotion/react` + `@emotion/styled` for v2, vs `@chakra-ui/react` only for v3)
   - Options: [Chakra UI v3 (latest, simpler deps)] [Chakra UI v2 (more examples/docs available)]
   - Recommendation: Chakra UI v3 (latest) -- simpler dependency footprint, actively maintained
   - Default: Chakra UI v3

3. **Test isolation strategy**
   - Feature spec mentions both `@Transactional` on test class (auto-rollback) and `@DirtiesContext`
   - Options: [`@Transactional` for auto-rollback (faster)] [`@DirtiesContext` (slower but more realistic)] [Neither -- manual cleanup]
   - Recommendation: `@Transactional` for auto-rollback -- faster test execution, standard Spring practice
   - Default: `@Transactional`

## Recommendations

1. **Build order matters**: Foundation (BaseEntity, error handling, JPA config, dependencies) must come before domain entities. Categories before Products (FK dependency). Backend before frontend (API must exist for frontend to consume).

2. **Lombok must be added**: The models.md standard explicitly shows Lombok annotations. Add to pom.xml with annotation processor configuration.

3. **Verify Chakra UI v3 compatibility**: The mockups were designed with Chakra UI patterns. Ensure the selected version matches the component API used in mockups.

4. **Establish the DTO pattern carefully**: The `from()` factory method pattern on Java records will be the template for all future features. Getting this right is important.

5. **Test the Category-delete-with-products scenario**: The 409 Conflict on category delete when products reference it is a critical business rule that must have test coverage.

## Risk Assessment

- **Complexity Risk**: Low -- standard CRUD patterns, well-documented spec, clear architecture
- **Integration Risk**: Low -- greenfield with no existing business logic to conflict
- **Regression Risk**: Low -- only modifying scaffold files (pom.xml, package.json, entry points)
- **Volume Risk**: Medium -- ~40 new files across full stack, but each is straightforward
- **Pattern Risk**: Medium -- first domain feature sets precedent; mistakes propagate to future features
