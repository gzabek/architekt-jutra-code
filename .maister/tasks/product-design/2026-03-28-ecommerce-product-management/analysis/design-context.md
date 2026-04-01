# Design Context: Ecommerce Product Management

**Date**: 2026-03-28
**Task**: Add product management to the aj platform

---

## Task Overview

Add ecommerce product management to the existing aj microkernel platform. Each product has: name, description, photo, price, SKU, and category. This is a full-stack feature requiring backend (entity, API, persistence) and frontend (Chakra UI-based product management screens).

### User Preferences
- **UI Framework**: Chakra UI (to be added to the React 19 + TypeScript + Vite frontend)
- **Brand Colors**: Design cool brand colors (no specific color preferences — creative freedom)

---

## Codebase Summary

### Platform: aj — Plugin-Based Microkernel
- **Backend**: Java 25, Spring Boot 4.0.5 (WebMVC, JPA, jOOQ, Liquibase), PostgreSQL 18
- **Frontend**: React 19 + TypeScript + Vite, builds to `src/main/resources/static/`
- **Testing**: JUnit 5, TestContainers (PostgreSQL), MockMvc
- **Build**: Maven with frontend-maven-plugin
- **State**: Pre-alpha scaffolding — core infrastructure in place, zero business domain logic

### What Exists
- Health endpoint (`GET /api/health`)
- SPA forwarding controller for React Router
- Plugin framework (Plugin interface, PluginRegistry)
- Empty Liquibase master changelog (ready for migrations)
- TestContainers + MockMvc test infrastructure
- Frontend with Vite proxy to backend

### What Does NOT Exist Yet
- No domain entities (BaseEntity must be created)
- No CRUD implementations anywhere
- No error handling infrastructure (@RestControllerAdvice)
- No validation infrastructure
- No DTO patterns established
- No frontend routing (just a single App component)
- No Chakra UI (needs to be added)

### Precedent-Setting Nature
Product management will be the **first business feature** in this codebase. Every pattern established here becomes the template for all future features. This makes design decisions particularly important.

---

## Established Patterns to Follow

### Entity Modeling (from models.md)
- BaseEntity with @MappedSuperclass: id (SEQUENCE), createdAt, @Version updatedAt
- @Enumerated(EnumType.STRING) for enums
- BigDecimal for money
- LAZY fetch default for all relationships
- Set-based collections
- Business key equals/hashCode (not entity id)
- Lombok: @Getter, @Setter, @NoArgsConstructor (no @Data on entities)

### API Design (from api.md)
- RESTful with plural resource names: `/api/products`
- Proper HTTP methods and status codes
- Query parameters for filtering/sorting/pagination
- Max 2-3 levels of URL nesting

### Database Migrations (from migrations.md)
- Reversible YAML changesets in Liquibase
- Small, focused changes
- Separate schema from data migrations

### Testing
- @SpringBootTest + @Import(TestcontainersConfiguration.class) for integration
- @WebMvcTest for isolated controller tests
- MockMvc for HTTP assertions

---

## Key Design Decisions Needed

1. **Product category**: Fixed enum vs. dynamic entity (user-managed categories)
2. **Photo storage**: URL reference, filesystem, S3/object storage, or database
3. **SKU handling**: Auto-generated vs. user-provided, uniqueness enforcement
4. **Soft delete**: Whether products should be soft-deleted (deleted_at flag)
5. **Pagination**: How product listing handles large datasets
6. **Chakra UI setup**: Theme configuration, brand colors, component patterns

---

## Implications for Design

- **Full-stack scope**: Backend API + frontend UI, both need design
- **Infrastructure creation**: BaseEntity, error handling, validation patterns must be established
- **Brand identity**: Chakra UI theming with custom brand colors affects all future UI
- **Template quality**: Every pattern here propagates — worth getting right
- **No breaking risk**: Entirely additive, nothing to break
- **Moderate complexity**: ~8-10 new backend files, frontend components, migrations
