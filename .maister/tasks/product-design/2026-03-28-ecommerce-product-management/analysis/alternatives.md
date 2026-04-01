# Design Alternatives: Ecommerce Product Management

## Problem Reframing

### Research Question
How should the aj platform's first business feature (product management) be designed to establish clean, reusable patterns across the full stack while delivering a professional demo experience?

### How Might We Questions
1. How might we structure backend packages so the first feature establishes a scalable pattern for future modules?
2. How might we model the Category-Product relationship to balance simplicity with real-world usefulness?
3. How might we design the REST API to demonstrate best practices while keeping the scope minimal?
4. How might we architect the React frontend so it feels professional and sets component patterns for future features?
5. How might we establish a brand identity that makes the demo app feel polished without over-investing in design?

---

## Decision Area 1: Backend Architecture Pattern

### Alternative 1A: Feature-Sliced Packages (Vertical Slicing)

**Description**: Organize code by feature/domain. Each feature gets its own package containing controller, service, repository, DTOs, and entity. Shared infrastructure (BaseEntity, error handling, validation) lives in a `common` or `core` package.

```
pl.devstyle.aj
├── core/                    (plugin framework, BaseEntity, error handling)
├── product/
│   ├── Product.java
│   ├── ProductRepository.java
│   ├── ProductService.java
│   ├── ProductController.java
│   └── dto/
└── category/
    ├── Category.java
    ├── CategoryRepository.java
    ├── CategoryService.java
    ├── CategoryController.java
    └── dto/
```

**Pros**:
- High cohesion: everything related to a feature is co-located
- Easy to navigate: find a feature, find all its code
- Scales naturally: new features add new packages without touching existing ones
- Aligns with microkernel architecture vision (each feature = potential plugin boundary)
- Standard Spring Boot convention for modular apps

**Cons**:
- Cross-cutting concerns (shared DTOs, common query patterns) need a home
- Can lead to some duplication across feature packages
- Two small features (Product, Category) may feel like overkill for vertical slicing initially

**Best when**: The project expects multiple future modules and wants clear boundaries from day one.

### Alternative 1B: Layer-First Packages (Horizontal Slicing)

**Description**: Organize code by technical layer. All controllers in one package, all services in another, all entities in another.

```
pl.devstyle.aj
├── core/
├── api/         (all controllers)
├── service/     (all services)
├── domain/      (all entities)
├── repository/  (all repositories)
└── dto/         (all DTOs)
```

**Pros**:
- Familiar to most Spring Boot developers
- Simple and flat structure for small codebases
- Easy to apply cross-cutting patterns (e.g., all controllers follow the same pattern)

**Cons**:
- Becomes unwieldy as features grow (20 controllers in one package)
- Low cohesion: related classes are scattered across packages
- Does not align with microkernel/plugin architecture goals
- Harder to extract features into plugins later

**Best when**: The project will remain very small (2-3 entities) and never modularize.

### Alternative 1C: Hybrid -- Feature Packages with Shared Layer

**Description**: Primary organization by feature, but with a lightweight shared layer for cross-cutting infrastructure. Services use a thin facade pattern with Java records for DTOs.

```
pl.devstyle.aj
├── core/
│   ├── plugin/
│   ├── BaseEntity.java
│   └── error/               (GlobalExceptionHandler, ErrorResponse record)
├── product/
│   ├── Product.java
│   ├── ProductRepository.java
│   ├── ProductService.java
│   ├── ProductController.java
│   ├── ProductRequest.java   (Java record)
│   └── ProductResponse.java  (Java record)
└── category/
    ├── ...same pattern...
```

**Pros**:
- Feature cohesion with clean shared infrastructure
- Java records for DTOs eliminate boilerplate (no Lombok needed for DTOs)
- Flat within each feature package -- no unnecessary sub-packages for just 5-6 files
- Error handling centralized in core, not duplicated per feature
- Clear template: copy a feature package to start a new feature

**Cons**:
- Slightly more files than layer-first for two small features
- Requires discipline to keep the `core` package lean

**Best when**: You want feature cohesion, clean precedent, and minimal ceremony.

### Recommendation: Alternative 1C -- Hybrid Feature Packages

**Rationale**: This approach gives feature cohesion (aligning with the microkernel vision) while keeping the structure flat and approachable for a first feature. Java records for DTOs are idiomatic Java 25 and eliminate Lombok dependency for data transfer objects. The `core` package provides a natural home for BaseEntity and error handling without creating unnecessary abstraction layers.

**Key trade-off accepted**: Slightly more files than a minimal layer-first approach, but this pays for itself as soon as a third feature is added.

---

## Decision Area 2: Category-Product Relationship

### Alternative 2A: Simple ManyToOne (Flat Categories)

**Description**: Category is a standalone entity with name (and optionally description). Product has a `@ManyToOne` relationship to Category. Categories are flat -- no hierarchy, no nesting.

```java
@Entity @Table(name = "categories")
public class Category extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;
}

@Entity @Table(name = "products")
public class Product extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    // ...other fields
}
```

**Pros**:
- Simple to implement and understand
- Matches the stated requirement ("dynamic, admin-managed categories")
- Clean JPA modeling following all established standards (LAZY fetch, business key equals/hashCode)
- Category CRUD is straightforward
- Filtering products by category is a simple WHERE clause
- Follows models.md guidance: "prefer primitives over entities when possible" -- but category needs its own lifecycle, so entity is justified

**Cons**:
- No category hierarchy (Electronics > Phones > Smartphones)
- Deleting a category with products requires a decision (block, reassign, or cascade)
- Single category per product (no multi-category tagging)

**Best when**: Categories are a simple organizational tool, not a complex taxonomy.

### Alternative 2B: Hierarchical Categories (Self-Referencing)

**Description**: Category has an optional parent reference, enabling tree structures. Uses an adjacency list pattern.

```java
@Entity @Table(name = "categories")
public class Category extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent")
    private Set<Category> children = new HashSet<>();
}
```

**Pros**:
- Enables rich category trees (Electronics > Phones > Smartphones)
- Common real-world pattern for ecommerce

**Cons**:
- Significantly more complex: tree traversal, breadcrumb generation, recursive queries
- UI complexity: tree picker, drag-and-drop reordering
- Scope creep: the problem statement says "dynamic, admin-managed" -- not "hierarchical"
- Adjacency list has poor performance for deep tree queries without materialized paths or nested sets
- Overkill for a demo/showcase feature

**Best when**: The product catalog is large and requires multi-level taxonomy (not this project).

### Alternative 2C: Optional Category (Nullable ManyToOne)

**Description**: Same as 2A but the category relationship is optional. Products can exist without a category.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")  // nullable
private Category category;
```

**Pros**:
- Flexible: products can be uncategorized
- Avoids forcing users to create categories before products
- More forgiving UX for demo purposes

**Cons**:
- Nullable foreign key adds null-checking throughout the code
- "Uncategorized" products complicate filtering logic
- The problem statement implies categories are required ("product has... category")
- Weaker data model: less enforced structure

**Best when**: The workflow needs to support products without categories.

### Recommendation: Alternative 2A -- Simple ManyToOne (Flat Categories)

**Rationale**: The problem statement explicitly scopes two entities with a required category. Flat categories are sufficient for a demo feature and keep the JPA relationship modeling clean and standard-compliant. The relationship demonstrates ManyToOne with LAZY fetch, business key equality, and proper foreign key constraints -- exactly the patterns this feature should establish.

**Delete behavior**: When deleting a category, the API should return 409 Conflict if products still reference it. This enforces referential integrity and teaches the admin to reassign products first -- a clean, predictable behavior.

**Key trade-off accepted**: No hierarchical categories. If hierarchy is needed later, it can be added as a non-breaking migration (add nullable `parent_id` column).

---

## Decision Area 3: API Design

### Alternative 3A: Flat Resource Endpoints

**Description**: Products and categories are top-level resources. Category filtering on products uses query parameters.

```
GET    /api/categories
POST   /api/categories
GET    /api/categories/{id}
PUT    /api/categories/{id}
DELETE /api/categories/{id}

GET    /api/products?category={id}&search={term}&sort={field}&page={n}&size={n}
POST   /api/products
GET    /api/products/{id}
PUT    /api/products/{id}
DELETE /api/products/{id}
```

**Pros**:
- Simple, flat URL structure following api.md standards
- Query parameters for filtering/sorting/pagination as recommended
- Each resource is independently addressable
- Easy to cache (flat URLs are cache-friendly)
- Standard Spring Boot @RestController pattern

**Cons**:
- No URL-level indication of category-product relationship
- Client must know to use query parameter for category filtering

**Best when**: Resources are relatively independent and filtering is query-parameter-based.

### Alternative 3B: Nested Resource Endpoints

**Description**: Products are nested under categories to express the relationship in the URL.

```
GET    /api/categories
GET    /api/categories/{id}
GET    /api/categories/{id}/products
POST   /api/categories/{id}/products
GET    /api/products/{id}          (direct access for convenience)
PUT    /api/products/{id}
DELETE /api/products/{id}
```

**Pros**:
- URL structure expresses the relationship clearly
- Browsing products by category feels natural

**Cons**:
- Mixed nesting: some product operations are nested, others are flat (inconsistent)
- Creating a product requires knowing the category URL, not just the ID
- Violates api.md guidance: "limited nesting to 2-3 levels" -- while within limits, it adds unnecessary complexity
- Makes the frontend routing harder (two paths to the same resource)
- Harder to add cross-category product operations later

**Best when**: The relationship is truly hierarchical and products are always accessed through categories.

### Alternative 3C: Flat Resources with Pagination via Spring Data Page

**Description**: Flat endpoints (like 3A) but with a standardized pagination response envelope using Spring Data's Page abstraction.

```
GET /api/products?category=3&search=laptop&sort=name,asc&page=0&size=20

Response:
{
  "content": [...],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 47,
    "totalPages": 3
  }
}
```

Error responses use a consistent envelope:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Cannot delete category with 5 associated products",
  "timestamp": "2026-03-28T14:30:00Z"
}
```

**Pros**:
- All benefits of 3A plus standardized pagination
- Spring Data Page integrates natively with Spring Boot
- Consistent error response format establishes a pattern for all future features
- Frontend can build generic pagination components from the standard shape
- Sort parameter follows Spring Data conventions (`sort=name,asc`)

**Cons**:
- Page metadata adds payload size for simple lists (categories may not need pagination)
- Spring Data's page numbering is 0-based (common source of off-by-one bugs in UI)

**Best when**: You want a production-quality API pattern that handles real datasets and establishes pagination conventions.

### Recommendation: Alternative 3C -- Flat Resources with Pagination

**Rationale**: Flat endpoints follow api.md standards and keep URLs simple. Adding Spring Data Page pagination for products establishes the right pattern from day one. Categories can return a simple list (unlikely to need pagination). The consistent error response format (via @RestControllerAdvice) sets a precedent every future feature will follow.

**Key trade-off accepted**: Slightly more infrastructure code for pagination and error handling, but this is exactly the kind of pattern the first feature should establish.

---

## Decision Area 4: Frontend Architecture

### Alternative 4A: Page-Based with React Router + TanStack Query

**Description**: Each screen is a "page" component. React Router handles navigation. TanStack Query (React Query) manages server state (fetching, caching, mutations). Local component state only for UI concerns (form inputs, modal open/close).

```
frontend/src/
├── pages/
│   ├── ProductListPage.tsx
│   ├── ProductFormPage.tsx
│   ├── CategoryListPage.tsx
│   └── CategoryFormPage.tsx
├── components/
│   ├── layout/              (AppShell, Sidebar, Header)
│   ├── shared/              (DataTable, ConfirmDialog, SearchInput)
│   └── product/             (ProductCard -- if needed)
├── api/
│   ├── products.ts          (API functions: getProducts, createProduct, etc.)
│   └── categories.ts
├── hooks/
│   ├── useProducts.ts       (TanStack Query hooks wrapping API calls)
│   └── useCategories.ts
└── theme/
    └── index.ts             (Chakra UI theme customization)
```

**Pros**:
- TanStack Query handles caching, refetching, loading/error states automatically
- Clean separation: API layer -> hooks -> pages -> components
- No global state management library needed (TanStack Query IS the server state manager)
- Optimistic updates for mutations are built-in
- React Router v7 with loaders/actions could be added later
- Industry standard pattern for data-driven admin apps

**Cons**:
- Adds TanStack Query as a dependency
- Learning curve for developers unfamiliar with TanStack Query patterns
- May feel like overkill for two simple CRUD screens

**Best when**: The app will grow beyond two screens and needs robust data fetching patterns.

### Alternative 4B: Simple useState + fetch

**Description**: Each page component manages its own state with useState/useEffect. API calls use fetch directly. No additional libraries beyond React and Chakra UI.

```
frontend/src/
├── pages/
│   ├── Products.tsx          (list + inline form)
│   └── Categories.tsx
├── components/
│   └── Layout.tsx
└── theme.ts
```

**Pros**:
- Zero additional dependencies
- Minimal code, fast to implement
- Easy to understand for any React developer

**Cons**:
- No caching: every navigation refetches
- Loading/error states must be manually managed in every component
- Duplicated fetch logic across pages
- No automatic refetch on mutation (must manually invalidate)
- Does not establish reusable patterns for future features
- Scales poorly as the app grows

**Best when**: This is a throwaway prototype, not a template for future development.

### Alternative 4C: Page-Based with Custom Hooks + Axios

**Description**: Similar to 4A but uses custom hooks with Axios instead of TanStack Query. Custom hooks encapsulate fetch logic with loading/error state management.

```
frontend/src/
├── pages/
│   ├── ProductListPage.tsx
│   └── ...
├── components/...
├── api/
│   ├── client.ts            (Axios instance with base URL, interceptors)
│   └── products.ts
├── hooks/
│   ├── useApi.ts            (generic fetch hook with loading/error)
│   ├── useProducts.ts
│   └── useCategories.ts
└── theme/...
```

**Pros**:
- Axios interceptors provide centralized error handling, auth headers
- Custom hooks give control over caching strategy
- Lighter than TanStack Query if caching is not needed

**Cons**:
- Reinvents what TanStack Query does better (caching, deduplication, background refetch)
- More custom code to maintain
- Axios adds a dependency too, but with less functionality than TanStack Query
- The generic useApi hook often becomes a poor man's React Query

**Best when**: You specifically need Axios interceptors and do not want query caching.

### Recommendation: Alternative 4A -- Page-Based with TanStack Query

**Rationale**: This is a template-setting feature. TanStack Query is the industry standard for server state in React apps and eliminates an entire class of bugs (stale data, loading races, cache invalidation). The pattern it establishes (API layer -> query hooks -> pages) is exactly what future features should follow. The extra dependency is lightweight and well worth it.

**Key trade-off accepted**: Adding TanStack Query as a dependency. The alternative (manually managing fetch state) creates more code and worse UX.

---

## Decision Area 5: Brand Identity and Theming

### Alternative 5A: Deep Teal + Warm Accent (Professional SaaS)

**Description**: A deep teal/cyan primary palette with warm amber accents. Clean, modern, and professional -- inspired by tools like Linear and Notion.

- **Primary**: Deep Teal (#0D9488 to #134E4A range) -- trustworthy, modern
- **Accent**: Warm Amber (#F59E0B) -- for CTAs, badges, highlights
- **Neutrals**: Slate gray scale (#0F172A to #F8FAFC)
- **Semantic**: Standard green/red/yellow for success/error/warning
- **Background**: Light mode with subtle warm gray (#FAFAF9)

**Pros**:
- Professional and calming -- appropriate for an admin/management tool
- Teal is distinctive without being trendy
- Warm accent provides strong contrast for interactive elements
- Works well in both light and dark modes
- Accessible: teal/amber combination meets WCAG AA contrast ratios

**Cons**:
- Teal is common in SaaS tools (may not feel unique)
- Amber accent requires care to not overuse

**Best when**: You want a polished, professional look that ages well.

### Alternative 5B: Indigo + Emerald (Bold Modern)

**Description**: A rich indigo primary with emerald green accents. Bolder and more distinctive.

- **Primary**: Indigo (#4F46E5 to #312E81 range)
- **Accent**: Emerald (#10B981)
- **Neutrals**: Cool gray (#111827 to #F9FAFB)
- **Background**: Clean white with indigo-tinted grays

**Pros**:
- Visually striking and memorable
- Indigo conveys innovation and tech sophistication
- Strong brand identity right from the start

**Cons**:
- Purple/indigo can feel heavy in data-dense admin interfaces
- Emerald green accent can clash with semantic "success" green
- Requires more careful design work to not feel overwhelming

**Best when**: You want the demo to make a strong visual impression.

### Alternative 5C: Slate + Blue (Minimal Enterprise)

**Description**: Predominantly neutral with a restrained blue accent. Think GitHub, Stripe.

- **Primary**: Blue (#3B82F6 to #1E40AF range)
- **Accent**: Same blue (no separate accent color)
- **Neutrals**: Pure gray (#111827 to #F9FAFB)
- **Background**: White with very subtle gray sections

**Pros**:
- Extremely clean and professional
- Lets content (products, data) take center stage
- Easiest to implement well -- fewer color decisions
- Universally acceptable, never looks bad

**Cons**:
- Generic -- does not establish a distinctive brand
- Can feel cold and corporate
- Blue is the most common SaaS color; does not differentiate

**Best when**: Design is not a priority and you want a safe, clean default.

### Recommendation: Alternative 5A -- Deep Teal + Warm Accent

**Rationale**: This strikes the right balance between professional and distinctive. The teal primary is modern without being trendy, and the warm amber accent provides visual energy for interactive elements. As a demo/showcase app, it should look polished and intentional -- not generic blue. The color system is simple enough to implement in Chakra UI's theme tokens without extensive design work.

**Chakra UI implementation**: Define custom color scales (brand.50 through brand.900) in the theme, override component defaults (Button, Input focus rings, Table header backgrounds), and set the global color mode to light.

---

## Trade-Off Analysis

| Decision Area | Technical Feasibility | User Impact | Simplicity | Risk | Scalability |
|---|---|---|---|---|---|
| **1C: Hybrid Feature Packages** | High -- standard Spring Boot | N/A (developer UX) | High -- flat, clear | Low -- proven pattern | High -- new features = new packages |
| **2A: Simple ManyToOne** | High -- basic JPA | High -- clean category filtering | High -- straightforward | Low -- well-understood | Medium -- no hierarchy, but extensible |
| **3C: Flat + Pagination** | High -- Spring Data native | High -- proper pagination UX | Medium -- more infrastructure | Low -- Spring conventions | High -- handles growth |
| **4A: TanStack Query** | High -- mature library | High -- caching, loading states | Medium -- extra abstraction | Low -- industry standard | High -- built for scaling |
| **5A: Deep Teal + Amber** | High -- Chakra tokens | High -- polished, professional | High -- simple palette | Low -- safe color choices | High -- extensible theme |

### Cross-Cutting Observations

All recommendations score **High** on technical feasibility and **Low** on risk. This is intentional -- as the first feature, it should establish reliable, proven patterns rather than experiment with novel approaches. The main trade-off is **simplicity vs. scalability**: each recommendation adds slightly more structure than the absolute minimum, investing in patterns that will pay off as the platform grows.

---

## Deferred Ideas

1. **Hierarchical categories** -- Worth considering if the product catalog grows, but out of scope for the initial demo feature.
2. **Product image upload** -- The problem statement specifies URL references. File upload (with S3/object storage) is a separate feature.
3. **Bulk operations** -- Import/export of products, bulk category reassignment. Useful but not part of initial CRUD.
4. **Product variants** -- Size, color, material as attributes. Would require a more complex data model (EAV or dedicated variant entity).
5. **Dark mode** -- Chakra UI supports it natively. Worth adding after the light theme is solid, but not in initial scope.
6. **Search with full-text index** -- PostgreSQL `tsvector` for proper full-text search. Simple LIKE/ILIKE is sufficient for the demo.

---

## Summary of Recommendations

| Decision Area | Recommended | Confidence |
|---|---|---|
| Backend Architecture | 1C: Hybrid Feature Packages | High |
| Category-Product Relationship | 2A: Simple ManyToOne (Flat) | High |
| API Design | 3C: Flat Resources + Pagination | High |
| Frontend Architecture | 4A: TanStack Query + Page-Based | High |
| Brand Identity | 5A: Deep Teal + Warm Amber | Medium |

**Overall confidence: High** -- All recommendations follow established patterns, align with the project's standards, and are well-suited to a template-setting first feature. The brand identity recommendation is medium confidence because color preferences are subjective.

### Key Assumptions
1. The project will grow beyond this first feature (justifying the investment in patterns)
2. A single developer or small team will maintain the codebase (favoring simplicity over abstraction)
3. The demo needs to look professional but not pixel-perfect (favoring Chakra defaults with custom tokens)
4. Performance at scale is not a concern for the initial implementation (but patterns should not preclude it)
