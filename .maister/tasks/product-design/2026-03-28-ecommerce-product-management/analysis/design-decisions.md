# Design Decisions: Ecommerce Product Management

## Selected Approach

Hybrid feature-packaged Spring Boot backend with flat REST API, React Router + Chakra UI frontend with custom hooks, and a Deep Teal + Warm Amber brand identity.

## Decisions by Area

### 1. Backend Architecture: Hybrid Feature Packages
- Feature packages (`product/`, `category/`) with shared `core/` for BaseEntity and error handling
- Java records for DTOs (no Lombok needed for DTOs)
- Flat structure within each feature package (no sub-packages for 5-6 files)
- **Trade-off**: Slightly more files than layer-first, but establishes clean template for future features

### 2. Category-Product Relationship: Simple ManyToOne (Flat)
- Category is a standalone entity with name and description
- Product has required `@ManyToOne(fetch=LAZY)` to Category
- Deleting a category with associated products returns 409 Conflict
- Flat categories (no hierarchy)
- **Trade-off**: No hierarchical categories; can be added later as non-breaking migration

### 3. API Design: Flat Resources (Simple)
- Top-level endpoints: `/api/products` and `/api/categories`
- Category filtering via query parameters on product listing
- Simple list responses (no pagination envelope)
- Consistent error responses via @RestControllerAdvice
- **Trade-off**: No standardized pagination; can be added when needed

### 4. Frontend Architecture: React Router + Custom Hooks
- React Router for page-based navigation
- Custom hooks with fetch for data fetching (no TanStack Query)
- Pages, components, API layer, hooks structure
- Chakra UI for component library
- **Trade-off**: No automatic caching/refetch; simpler dependency footprint

### 5. Brand Identity: Deep Teal + Warm Amber
- Primary: Deep Teal (#0D9488 to #134E4A range)
- Accent: Warm Amber (#F59E0B)
- Neutrals: Slate gray scale (#0F172A to #F8FAFC)
- Background: Light mode with subtle warm gray (#FAFAF9)
- Semantic: Standard green/red/yellow for success/error/warning
- **Trade-off**: Teal is common in SaaS but professional and accessible

## Alternatives Considered

Full detail in `analysis/alternatives.md`. Each area had 3 alternatives evaluated on technical feasibility, simplicity, risk, and scalability.
