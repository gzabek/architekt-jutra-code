# Scope Clarifications

## Critical Decisions

### 1. ProductDetailPage
**Decision:** Create new ProductDetailPage (read-only view with plugin tabs)
**Rationale:** Design specifies read-only product detail view. Keeps view/edit concerns separate.

### 2. PluginDescriptor PK Strategy
**Decision:** String PK as designed — pluginId (e.g., "warehouse") is the primary key
**Rationale:** Natural PK simplifies URLs, FK references, and all downstream code. No BaseEntity inheritance — manual timestamps.

## Important Decisions

### 3. JSONB Hibernate Support
**Decision:** Verify during implementation (establish pattern on Product.pluginData first)

### 4. SDK Build Configuration
**Decision:** Separate vite.sdk.config.ts (reverted from multi-entry per spec audit finding H1 — Vite library mode replaces app output format, cannot share config)

### 5. Plugin Filter Querying
**Decision:** Server-side JSONB filtering with JPA Specification (user override from client-side default)

### 6. Old Plugin Interface Files
**Decision:** Delete Plugin.java, PluginDescriptor.java, PluginRegistry.java and recreate from scratch
