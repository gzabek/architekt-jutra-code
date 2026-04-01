# Feature Specification: Ecommerce Product Management

## Section 1: Data Models

### BaseEntity (shared abstract class)

@MappedSuperclass with JPA auditing:
- id: Long — @GeneratedValue(strategy=SEQUENCE), allocationSize=1
- createdAt: LocalDateTime — @CreatedDate, immutable
- updatedAt: LocalDateTime — @Version (optimistic locking + audit timestamp)

Each entity declares its own @SequenceGenerator.

### Category Entity

Table: `categories`
Sequence: `category_seq`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long | PK, auto-generated | From BaseEntity |
| name | String | NOT NULL, UNIQUE, max 100 chars | Business key for equals/hashCode |
| description | String | nullable, max 500 chars | Optional category description |
| createdAt | LocalDateTime | NOT NULL, immutable | From BaseEntity |
| updatedAt | LocalDateTime | NOT NULL, @Version | From BaseEntity |

Indexes: unique index on `name`
Business key: `name` (used for equals/hashCode)
Lombok: @Getter, @Setter, @NoArgsConstructor

### Product Entity

Table: `products`
Sequence: `product_seq`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long | PK, auto-generated | From BaseEntity |
| name | String | NOT NULL, max 255 chars | Product display name |
| description | String | nullable, max 2000 chars | Product description (longer text) |
| photoUrl | String | nullable, max 500 chars | URL to external product image |
| price | BigDecimal | NOT NULL, precision 19 scale 2 | Never use float/double for money |
| sku | String | NOT NULL, UNIQUE, max 50 chars | Stock Keeping Unit — business key |
| category | Category | NOT NULL, @ManyToOne(fetch=LAZY) | FK to categories.id |
| createdAt | LocalDateTime | NOT NULL, immutable | From BaseEntity |
| updatedAt | LocalDateTime | NOT NULL, @Version | From BaseEntity |

Indexes: unique index on `sku`, index on `category_id`
Business key: `sku` (used for equals/hashCode)
Lombok: @Getter, @Setter, @NoArgsConstructor

### Relationship
- Product -> Category: @ManyToOne(fetch=LAZY), @JoinColumn(name="category_id", nullable=false)
- No bidirectional mapping (Category does not have a products collection) — keeps it simple, avoids N+1 risk

---

## Section 2: REST API

### Category Endpoints

| Method | Path | Description | Request Body | Response | Status |
|--------|------|-------------|-------------|----------|--------|
| GET | /api/categories | List all categories | — | CategoryResponse[] | 200 |
| GET | /api/categories/{id} | Get category by ID | — | CategoryResponse | 200 / 404 |
| POST | /api/categories | Create category | CreateCategoryRequest | CategoryResponse | 201 |
| PUT | /api/categories/{id} | Update category | UpdateCategoryRequest | CategoryResponse | 200 / 404 |
| DELETE | /api/categories/{id} | Delete category | — | — | 204 / 404 / 409 |

DELETE returns 409 Conflict if products reference the category.

### Product Endpoints

| Method | Path | Description | Request Body | Response | Status |
|--------|------|-------------|-------------|----------|--------|
| GET | /api/products | List products | — | ProductResponse[] | 200 |
| GET | /api/products/{id} | Get product by ID | — | ProductResponse | 200 / 404 |
| POST | /api/products | Create product | CreateProductRequest | ProductResponse | 201 |
| PUT | /api/products/{id} | Update product | UpdateProductRequest | ProductResponse | 200 / 404 |
| DELETE | /api/products/{id} | Delete product | — | — | 204 / 404 |

Query parameters for GET /api/products:
- category={id} — filter by category ID
- search={term} — filter by name containing term (case-insensitive LIKE)
- sort={field},{direction} — sort by field (name, price, sku, createdAt), direction (asc/desc)

### Request/Response DTOs (Java Records)

CreateCategoryRequest:
  - name: String (required, max 100)
  - description: String (optional, max 500)

UpdateCategoryRequest:
  - name: String (required, max 100)
  - description: String (optional, max 500)

CategoryResponse:
  - id: Long
  - name: String
  - description: String
  - createdAt: LocalDateTime
  - updatedAt: LocalDateTime

CreateProductRequest:
  - name: String (required, max 255)
  - description: String (optional, max 2000)
  - photoUrl: String (optional, max 500)
  - price: BigDecimal (required, positive)
  - sku: String (required, max 50)
  - categoryId: Long (required)

UpdateProductRequest:
  - name: String (required, max 255)
  - description: String (optional, max 2000)
  - photoUrl: String (optional, max 500)
  - price: BigDecimal (required, positive)
  - sku: String (required, max 50)
  - categoryId: Long (required)

ProductResponse:
  - id: Long
  - name: String
  - description: String
  - photoUrl: String
  - price: BigDecimal
  - sku: String
  - category: CategoryResponse (nested, eager-loaded for response)
  - createdAt: LocalDateTime
  - updatedAt: LocalDateTime

### Error Handling

Global @RestControllerAdvice with consistent error response:

ErrorResponse record:
  - status: int (HTTP status code)
  - error: String (HTTP status text)
  - message: String (human-readable message)
  - timestamp: LocalDateTime

Handled exceptions:
  - EntityNotFoundException → 404
  - DataIntegrityViolationException (unique constraint) → 409
  - MethodArgumentNotValidException (validation) → 400 with field errors
  - Category delete with products → 409 with descriptive message

### Validation

Jakarta Bean Validation on request records:
  - @NotBlank for required strings
  - @Size(max=N) for length limits
  - @Positive for price
  - @NotNull for categoryId

---

## Section 3: Backend Architecture

### Package Structure

```
pl.devstyle.aj/
├── core/
│   ├── plugin/                      (existing plugin framework)
│   ├── BaseEntity.java              (shared @MappedSuperclass)
│   └── error/
│       ├── GlobalExceptionHandler.java  (@RestControllerAdvice)
│       └── ErrorResponse.java           (error response record)
├── product/
│   ├── Product.java                 (JPA entity)
│   ├── ProductRepository.java       (Spring Data JPA)
│   ├── ProductService.java          (@Service, business logic)
│   ├── ProductController.java       (@RestController)
│   ├── CreateProductRequest.java    (Java record + validation)
│   ├── UpdateProductRequest.java    (Java record + validation)
│   └── ProductResponse.java         (Java record)
├── category/
│   ├── Category.java
│   ├── CategoryRepository.java
│   ├── CategoryService.java
│   ├── CategoryController.java
│   ├── CreateCategoryRequest.java
│   ├── UpdateCategoryRequest.java
│   └── CategoryResponse.java
└── api/
    ├── HealthController.java        (existing)
    └── SpaForwardController.java    (existing)
```

### Service Layer Pattern

Each service:
- @Service annotation
- Constructor injection (final fields, single constructor — Spring auto-wires)
- @Transactional on write methods
- Receives request records, returns response records
- Throws typed exceptions (EntityNotFoundException for 404 cases)
- Validates business rules (e.g., check category exists before creating product)

### Controller Layer Rules

**Controllers MUST return DTOs (response records), never JPA entities.** This is a hard rule:
- All controller methods return `ResponseEntity<XxxResponse>` or `ResponseEntity<List<XxxResponse>>`
- Mapping from entity to response record happens in the service layer
- Request bodies use request records with Jakarta validation annotations
- Controllers are thin: validate input (@Valid), delegate to service, return response

### Entity-to-Response Mapping

Simple static factory methods on the response records:
- ProductResponse.from(Product product) — maps entity to response
- CategoryResponse.from(Category category)

No external mapping library (MapStruct, ModelMapper). Java records with static factory methods are sufficient for this scale.

### Configuration

- @EnableJpaAuditing on a @Configuration class (enables @CreatedDate on BaseEntity)
- No other custom configuration needed — Spring Boot auto-configures JPA, Liquibase, PostgreSQL

---

## Section 4: Frontend Architecture

### Technology Stack
- React 19 + TypeScript
- Chakra UI (to be added via npm)
- React Router v7 (to be added via npm)
- Vite (existing build tool)
- fetch API for HTTP calls (no axios/tanstack-query)

### Directory Structure

```
frontend/src/
├── main.tsx                    (entry point, ChakraProvider + RouterProvider)
├── theme/
│   └── index.ts               (Chakra UI theme: brand colors, component overrides)
├── api/
│   ├── client.ts              (base fetch wrapper with error handling)
│   ├── products.ts            (getProducts, getProduct, createProduct, updateProduct, deleteProduct)
│   └── categories.ts          (getCategories, getCategory, createCategory, updateCategory, deleteCategory)
├── hooks/
│   ├── useProducts.ts         (useState/useEffect hooks wrapping API calls)
│   └── useCategories.ts
├── pages/
│   ├── ProductListPage.tsx    (table with filtering, search, sort)
│   ├── ProductFormPage.tsx    (create/edit form — reused for both)
│   ├── CategoryListPage.tsx   (table with CRUD)
│   └── CategoryFormPage.tsx   (create/edit form)
├── components/
│   ├── layout/
│   │   ├── AppShell.tsx       (sidebar + header + content area)
│   │   ├── Sidebar.tsx        (navigation links)
│   │   └── Header.tsx         (app title, breadcrumbs)
│   └── shared/
│       ├── ConfirmDialog.tsx  (delete confirmation modal)
│       └── EmptyState.tsx     (shown when no data)
└── router.tsx                 (React Router route definitions)
```

### Routing

| Path | Page | Description |
|------|------|-------------|
| / | redirect to /products | Default landing |
| /products | ProductListPage | Product table with filters |
| /products/new | ProductFormPage | Create new product |
| /products/:id/edit | ProductFormPage | Edit existing product |
| /categories | CategoryListPage | Category table |
| /categories/new | CategoryFormPage | Create new category |
| /categories/:id/edit | CategoryFormPage | Edit existing category |

### API Client Pattern

Base fetch wrapper in api/client.ts:
- Prepends /api/ base path
- Sets Content-Type: application/json
- Parses JSON response
- Throws on non-OK status with error message from response body

Feature API modules (api/products.ts, api/categories.ts):
- Export typed async functions (getProducts, createProduct, etc.)
- Accept typed parameters, return typed responses
- Use the base fetch wrapper

### Custom Hook Pattern

Each hook (useProducts, useCategories):
- Manages loading, error, and data state via useState
- Provides fetch/refetch function
- Provides mutation functions (create, update, delete) that refetch after success
- Returns { data, loading, error, refetch, create, update, remove }

### Chakra UI Theme

Custom theme in theme/index.ts:
- Brand color scale: brand.50 through brand.900 (Deep Teal range)
- Accent color scale: accent.50 through accent.900 (Warm Amber range)
- Component overrides: Button (brand primary), Input focus ring (brand.500), Table header (brand.50 bg)
- Global styles: body background (#FAFAF9), font family (system)
- Color mode: light only (no dark mode toggle for MVP)

### Layout

AppShell with:
- Left sidebar (collapsible on mobile): nav links to Products, Categories
- Top header: app name "aj Product Manager", breadcrumbs
- Main content area: renders current page
- Responsive: sidebar collapses to hamburger menu on small screens

---

## Section 5: Database Migrations

### Migration Strategy

All migrations in Liquibase YAML format. Two changesets in separate logical groups within the master changelog. Categories table first (Product depends on it).

### Changeset 1: Create categories table

changeSet id: 001-create-categories-table
- Create sequence: category_seq (start=1, increment=1)
- Create table: categories
  - id: BIGINT, PK, NOT NULL
  - name: VARCHAR(100), NOT NULL
  - description: VARCHAR(500), nullable
  - created_at: TIMESTAMP, NOT NULL
  - updated_at: TIMESTAMP, NOT NULL
- Add unique constraint on name
- Rollback: drop table, drop sequence

### Changeset 2: Create products table

changeSet id: 002-create-products-table
- Create sequence: product_seq (start=1, increment=1)
- Create table: products
  - id: BIGINT, PK, NOT NULL
  - name: VARCHAR(255), NOT NULL
  - description: VARCHAR(2000), nullable
  - photo_url: VARCHAR(500), nullable
  - price: DECIMAL(19,2), NOT NULL
  - sku: VARCHAR(50), NOT NULL
  - category_id: BIGINT, NOT NULL, FK → categories(id)
  - created_at: TIMESTAMP, NOT NULL
  - updated_at: TIMESTAMP, NOT NULL
- Add unique constraint on sku
- Add index on category_id
- Rollback: drop table, drop sequence

### Master Changelog Update

db.changelog-master.yaml includes both changesets (inline or via include directives). Order matters: categories before products due to FK dependency.

---

## Section 6: Testing Strategy

### Test Infrastructure
- Use existing TestcontainersConfiguration (PostgreSQL 18 container)
- @SpringBootTest + @AutoConfigureMockMvc for integration tests
- @WebMvcTest for isolated controller unit tests
- MockMvc for all HTTP assertions

### Integration Tests (per feature)

CategoryIntegrationTests (@SpringBootTest + TestContainers):
- Create category → 201, returns CategoryResponse
- List categories → 200, returns array
- Get category by ID → 200
- Get non-existent category → 404
- Update category → 200
- Delete category → 204
- Delete category with products → 409
- Create duplicate name → 409

ProductIntegrationTests (@SpringBootTest + TestContainers):
- Create product with valid category → 201
- Create product with non-existent category → 404
- List products → 200
- List products filtered by category → 200, filtered results
- List products with search term → 200, matching results
- Get product by ID → 200, includes nested category
- Update product → 200
- Delete product → 204
- Create product with duplicate SKU → 409

### Controller Unit Tests (optional, lower priority)

@WebMvcTest with @MockBean for services:
- Verify request validation (missing required fields → 400)
- Verify correct HTTP methods and paths
- Verify response serialization

### What NOT to Test
- Don't test JPA repository methods that are Spring Data auto-generated
- Don't test entity getters/setters
- Don't mock the database — use TestContainers for real PostgreSQL
- Don't test private methods

### Test Data Strategy
- Each integration test creates its own data (no shared fixtures)
- Use @Transactional on test class for automatic rollback (or @DirtiesContext if needed)
- Inject repositories directly for test setup, use MockMvc for assertions
