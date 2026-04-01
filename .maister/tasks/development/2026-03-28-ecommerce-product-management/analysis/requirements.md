# Requirements: Ecommerce Product Management

## Source
Based on completed product design at `.maister/tasks/product-design/2026-03-28-ecommerce-product-management/`

## Initial Description
Implement full-stack ecommerce product management: Category and Product CRUD with Spring Boot backend (JPA entities, REST API, Liquibase migrations), React + Chakra UI frontend (routing, custom hooks, brand theming), and integration tests with TestContainers.

## Q&A Summary

### Phase 1 Clarifications
- Follow product design feature spec exactly as written
- Add spring-boot-starter-validation to pom.xml
- Use Lombok (@Getter, @Setter, @NoArgsConstructor) — configured in IDE

### Phase 2 Scope Decisions
- Chakra UI v3 (latest, simpler deps)
- @Transactional on integration tests for fast auto-rollback
- Lombok in pom.xml (verify present, add if needed)

### Phase 5 Requirements
- Mobile sidebar: Hamburger drawer pattern (hidden on mobile, overlay drawer on tap)
- No external references needed — feature spec and mockups are sufficient

## Feature Spec Reference
Full specification in: `.maister/tasks/product-design/2026-03-28-ecommerce-product-management/analysis/feature-spec.md`

### Data Models
- BaseEntity (@MappedSuperclass): id (Long, SEQUENCE), createdAt (@CreatedDate), updatedAt (@Version)
- Category: name (unique, max 100), description (nullable, max 500). Business key: name
- Product: name (max 255), description (nullable, max 2000), photoUrl (nullable, max 500), price (BigDecimal 19,2), sku (unique, max 50), category (ManyToOne LAZY, required). Business key: sku

### REST API
- /api/categories: GET list, GET by id, POST create, PUT update, DELETE (409 if products exist)
- /api/products: GET list (with ?category, ?search, ?sort params), GET by id, POST create, PUT update, DELETE
- Java record DTOs with Jakarta Bean Validation
- ErrorResponse record via @RestControllerAdvice

### Backend Architecture
- Hybrid feature packages: core/ (BaseEntity, error/), product/ (all product files), category/ (all category files)
- Service layer: @Service, constructor injection, @Transactional on writes
- Controllers return DTOs (never entities), thin delegation to services
- Static factory from() methods on response records

### Frontend Architecture
- React 19 + TypeScript + Chakra UI v3 + React Router v7
- Custom theme: Deep Teal (#0D9488) + Warm Amber (#F59E0B), light mode only
- AppShell: sidebar (hamburger drawer on mobile) + header + content area
- 4 pages: ProductListPage, ProductFormPage, CategoryListPage, CategoryFormPage
- API client: base fetch wrapper in api/client.ts
- Custom hooks: useProducts, useCategories (useState + useEffect)

### Database Migrations
- Liquibase YAML format
- Changeset 001: categories table + category_seq + unique name index
- Changeset 002: products table + product_seq + unique sku index + category_id index + FK

### Testing Strategy
- Integration tests: @SpringBootTest + @Import(TestcontainersConfiguration.class) + @AutoConfigureMockMvc + @Transactional
- Category tests: 8 cases (CRUD + duplicate name + delete with products)
- Product tests: 9 cases (CRUD + filtering + search + duplicate SKU + non-existent category)
- Optional @WebMvcTest unit tests for validation

## Visual Assets
- 4 HTML mockups in product design: product-list-page.html, product-form-page.html, category-list-page.html, category-form-page.html

## Scope Boundaries
- IN: Full CRUD for categories and products, REST API, Chakra UI frontend, Liquibase migrations, integration tests
- OUT: Cart, checkout, authentication, pagination, file upload, dark mode, soft delete

## Reusability Opportunities
- BaseEntity: reusable for all future domain entities
- GlobalExceptionHandler + ErrorResponse: reusable error handling pattern
- API client (fetch wrapper): reusable for all future frontend API calls
- AppShell layout: reusable for all future pages
- Custom hooks pattern: reusable data fetching template
