# Problem Statement: Ecommerce Product Management

## Problem Statement

The aj platform needs its first business feature to showcase the technology stack and architecture patterns. A product management module serves as the ideal demo feature — it exercises the full stack (JPA entities with relationships, REST API, Liquibase migrations, React + Chakra UI) while remaining focused and well-understood.

## Scope

- Two entities: **Product** (name, description, photo URL, price, SKU, category) and **Category** (dynamic, admin-managed)
- Full CRUD for both entities via REST API
- Admin-facing UI with Chakra UI: product table with category filtering, search, sorting; category management
- Photo handled as URL reference (no file upload)
- Hard delete (no soft delete pattern)
- No cart, checkout, or customer-facing storefront

## Constraints

- Must follow all established standards (models.md, api.md, migrations.md)
- Must establish reusable patterns (BaseEntity, DTO records, error handling) as precedent for future features
- Chakra UI with custom brand colors for the frontend
- Pre-alpha codebase — this is the first domain feature

## Success Criteria

- Clean, standards-compliant implementation that serves as a template
- Working end-to-end: create a category, create a product in that category, view/edit/delete from the UI
- Professional-looking UI with Chakra UI brand theming
- Comprehensive test coverage using existing TestContainers infrastructure

## Key Assumptions

- This is a tech showcase / demo app — business requirements are secondary to demonstrating clean architecture
- Single admin user context (no authentication/authorization needed for MVP)
- Product photos are external URLs, not uploaded files
- Categories are a separate dynamic entity with their own CRUD
- Hard delete for both products and categories
