# Product Brief: Ecommerce Product Management

## Problem Statement

The aj platform needs its first business feature to showcase the technology stack and architecture patterns. A product management module exercises the full stack (JPA entities with relationships, REST API, Liquibase migrations, React + Chakra UI) while remaining focused and well-understood. This is a tech showcase / demo app — an internal admin tool for managing a product catalog without cart/checkout.

## Target Users

Single admin user managing a product catalog. No authentication/authorization needed for MVP. The primary audience is developers evaluating the platform's architecture and patterns.

## Feature Overview

Two-entity CRUD system:
- **Category** — dynamic, admin-managed categories with name and description
- **Product** — products with name, description, photo URL, price, SKU, and required category reference

Full-stack implementation:
- **Backend**: Spring Boot 4.0.5, JPA entities extending BaseEntity, Spring Data repositories, service layer with Java records for DTOs, REST controllers
- **Frontend**: React 19 + TypeScript + Chakra UI + React Router, custom hooks for data fetching
- **Database**: PostgreSQL with Liquibase YAML migrations
- **Testing**: Integration tests with TestContainers, MockMvc for API assertions

## Constraints

- Must follow all established standards (models.md, api.md, migrations.md)
- Must establish reusable patterns (BaseEntity, DTO records, error handling) as precedent for future features
- Chakra UI with Deep Teal (#0D9488) + Warm Amber (#F59E0B) brand colors
- Pre-alpha codebase — this is the first domain feature
- Photo handled as URL reference (no file upload)
- Hard delete (no soft delete pattern)
- No cart, checkout, or customer-facing storefront

## Success Criteria

- Clean, standards-compliant implementation that serves as a template for all future features
- Working end-to-end: create a category, create a product in that category, view/edit/delete from the UI
- Professional-looking UI with Chakra UI brand theming
- Comprehensive test coverage using existing TestContainers infrastructure

## Acceptance Criteria

### Category Management
- Admin can create a category with name (required, unique) and description (optional)
- Admin can list all categories
- Admin can edit a category's name and description
- Admin can delete a category only if no products reference it (409 Conflict otherwise)

### Product Management
- Admin can create a product with name, SKU (unique), price, category (required), and optional description/photo URL
- Admin can list all products in a table with search, category filter, and sortable columns
- Admin can edit any product field
- Admin can delete a product (hard delete)
- Product list shows category as a badge, photo as thumbnail (placeholder for null URL)

### API
- REST endpoints at /api/products and /api/categories with full CRUD
- Consistent error responses via @RestControllerAdvice
- Jakarta Bean Validation on all request bodies
- 409 Conflict for unique constraint violations and category delete with products

### Infrastructure
- BaseEntity with @MappedSuperclass (id, createdAt, @Version updatedAt)
- Liquibase migrations for both tables with rollback support
- Integration tests covering all CRUD operations and edge cases

---

## Design Decisions

| Area | Decision | Trade-off |
|------|----------|-----------|
| Backend Architecture | Hybrid Feature Packages — `product/`, `category/`, shared `core/` | Slightly more files, but clean template for future features |
| Category-Product Relationship | Simple ManyToOne, flat, required | No hierarchy; extensible later |
| API Design | Flat resources, simple lists | No pagination envelope; can be added when needed |
| Frontend Architecture | React Router + custom hooks (no TanStack Query) | No automatic caching; simpler dependency footprint |
| Brand Identity | Deep Teal + Warm Amber | Professional and distinctive without being trendy |

Full alternatives analysis: `analysis/alternatives.md`
Full decision rationale: `analysis/design-decisions.md`

---

## Mockup References

4 UI screens rendered via visual companion (saved in `analysis/mockups/`):

| Screen | File | Description |
|--------|------|-------------|
| Product List | `product-list-page.html` | Data table with search, category filter, sortable columns, photo thumbnails |
| Product Form | `product-form-page.html` | Create/edit form with all fields, photo URL preview, breadcrumb navigation |
| Category List | `category-list-page.html` | Category table with product counts, delete warning for referenced categories |
| Category Form | `category-form-page.html` | Simple create/edit form for categories |

---

## References

| Document | Path | Content |
|----------|------|---------|
| Design Context | `analysis/design-context.md` | Codebase summary, tech stack, existing patterns |
| Codebase Analysis | `analysis/codebase-analysis.md` | Full codebase analysis from 3 Explore agents |
| Problem Statement | `analysis/problem-statement.md` | Refined problem, constraints, success criteria |
| Design Alternatives | `analysis/alternatives.md` | 5 decision areas, 15 alternatives, trade-off analysis |
| Design Decisions | `analysis/design-decisions.md` | Selected approach per area with rationale |
| Feature Specification | `analysis/feature-spec.md` | 6-section detailed spec (data models, API, backend, frontend, migrations, testing) |
| UI Mockups | `analysis/mockups/` | 4 HTML/CSS wireframes |
