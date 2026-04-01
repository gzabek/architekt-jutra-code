# Gap Analysis: Plugin System Implementation

## Summary
- **Risk Level**: Medium
- **Estimated Effort**: High
- **Detected Characteristics**: creates_new_entities, modifies_existing_code, involves_data_operations, ui_heavy

## Task Characteristics
- Has reproducible defect: no
- Modifies existing code: yes
- Creates new entities: yes
- Involves data operations: yes
- UI heavy: yes

---

## Gaps Identified

### Missing Features (do not exist yet)

1. **PluginDescriptor JPA Entity**: No JPA entity exists. Current `PluginDescriptor.java` is a 3-method interface (`getPluginId()`, `getName()`, `getVersion()`). Needs complete replacement with a JPA entity mapped to a `plugins` table with String PK, JSONB `manifest` column, `url`, `description`, `enabled` fields, and `createdAt`/`updatedAt` timestamps.

2. **PluginObject JPA Entity**: No entity or table for custom plugin objects. Needs new entity extending `BaseEntity` with `pluginId`, `objectType`, `objectId` string fields and JSONB `data` column. Unique constraint on `(plugin_id, object_type, object_id)`.

3. **Plugin REST Controllers (3 new)**: No REST endpoints for plugins exist. Need:
   - `PluginController` — `GET /api/plugins`, `GET /api/plugins/{id}`, `PUT /api/plugins/{id}/manifest`, `DELETE /api/plugins/{id}`
   - `PluginDataController` — `GET/PUT/DELETE /api/plugins/{id}/products/{productId}/data`
   - `PluginObjectController` — CRUD at `/api/plugins/{id}/objects/{type}/{objectId}`

4. **Plugin Services (3 new)**: No service layer for plugins. Need:
   - `PluginDescriptorService` — manifest upsert, registry queries, plugin existence validation
   - `PluginDataService` — JSONB read/write on Product.pluginData namespaced by pluginId
   - `PluginObjectService` — CRUD for plugin_objects table

5. **Plugin Repositories (2 new)**: No JPA repositories. Need `PluginDescriptorRepository` and `PluginObjectRepository`.

6. **Database Migrations (3 new)**: Migrations 004, 005, 006 do not exist:
   - 004: `plugins` table (String PK, JSONB manifest)
   - 005: `plugin_data JSONB` column on `products` + GIN index
   - 006: `plugin_objects` table with unique constraint + indexes

7. **JSONB Support**: No JSONB usage anywhere in the codebase. `@JdbcTypeCode(SqlTypes.JSON)` annotation pattern not yet established. No additional Hibernate type dependencies in pom.xml — need to verify Hibernate 7 (Spring Boot 4) supports JSONB natively or if a library is needed.

8. **PluginFrame Component**: No iframe hosting component. Need React component that creates sandboxed iframe, sets `window.name` context before loading `src`, registers in iframe-to-plugin map.

9. **PluginMessageHandler**: No postMessage handling. Need message listener with `"aj.plugin."` prefix validation, origin validation, `event.source` to plugin resolution, and handler registry for each message type.

10. **Plugin SDK (6 TypeScript files)**: No SDK exists. Need standalone TypeScript bundle built via Vite library mode:
    - `index.ts`, `messaging.ts`, `context.ts`, `host-app.ts`, `this-plugin.ts`, `types.ts`
    - Separate Vite config for IIFE library build
    - Output as `plugin-sdk.js` served as static asset

11. **Extension Point Rendering**:
    - `menu.main`: Sidebar currently hardcoded — needs dynamic items from plugin manifests
    - `product.detail.tabs`: No product detail view page exists (only list and form pages) — needs a new page
    - `product.list.filters`: ProductListPage has native filters but no plugin filter integration

12. **Dynamic Plugin Routes**: `router.tsx` is fully static. Needs dynamic route generation for full-page plugin iframes (`/plugins/:pluginId/*`).

13. **Plugin API Module**: No `api/plugins.ts` frontend module for fetching plugin data from backend.

14. **Plugin React Hook/Context**: No `usePlugins` hook or context for sharing plugin state across components (Sidebar, router, product pages).

### Incomplete Features (partial implementation)

1. **Plugin Package (`core/plugin/`)**: Package exists with 3 files, but all are for a Java-class plugin model (Spring bean DI). All 3 files must be replaced:
   - `PluginRegistry.java` (27 lines) — currently collects `Plugin` beans via constructor injection
   - `Plugin.java` (9 lines) — interface with `onStart()`/`onStop()` lifecycle
   - `PluginDescriptor.java` (11 lines) — interface with `getPluginId()`, `getName()`, `getVersion()`

2. **PluginRegistryTests.java**: 3 tests exist for the current bean-based registry. Must be completely rewritten for database-backed registry.

### Behavioral Changes Needed

1. **Product Entity**: Add nullable `pluginData` field of type `Map<String, Object>` with JSONB mapping.

2. **ProductResponse**: Add `pluginData` field (Map) and update `from()` factory method.

3. **Sidebar.tsx**: Change from hardcoded-only nav items to hardcoded + dynamic plugin menu items.

4. **router.tsx**: Add catch-all plugin route(s) alongside static routes.

5. **ProductListPage.tsx**: Integrate plugin filter extension point iframes into the filter bar.

6. **Vite Config**: Add separate build configuration for plugin SDK IIFE bundle.

---

## New Capability Analysis

### Integration Points
- **Backend**: 3 new REST controller prefixes (`/api/plugins`, `/api/plugins/{id}/products/...`, `/api/plugins/{id}/objects/...`)
- **Frontend Routes**: New `/plugins/:pluginId` route pattern for full-page plugin iframes
- **Sidebar Navigation**: Dynamic menu items from `menu.main` extension point
- **Product Detail Page**: New page (does not exist) for `product.detail.tabs`
- **Product List Page**: Plugin filter iframes in filter bar
- **Static Assets**: `/assets/plugin-sdk.js` served by Spring Boot

### Patterns to Follow
- **Entity pattern**: `BaseEntity` with `@MappedSuperclass`, SEQUENCE PK, `@Version updatedAt` — applies to `PluginObject`. `PluginDescriptor` diverges (String PK, no BaseEntity).
- **Controller pattern**: `ProductController` — constructor injection, `@RequestMapping`, status codes
- **Service pattern**: `ProductService` — `@Transactional`, record DTOs, exception handling
- **DTO pattern**: Java records with `static from()` factory methods
- **Frontend API pattern**: `api/products.ts` — typed interfaces + api client wrapper functions
- **Hook pattern**: `useProducts.ts` — useState + useCallback + useEffect
- **Migration pattern**: YAML Liquibase changesets with sequences and constraints

### Architectural Impact
- **New files**: ~25-30 (12 backend Java, 6 SDK TypeScript, 6-8 frontend React/TS, 3 migrations)
- **Modified files**: ~10 (Product entity/response, Sidebar, router, ProductListPage, Vite config, changelog master, possibly pom.xml)
- **New patterns introduced**: JSONB column mapping, postMessage communication, iframe sandbox hosting, Vite library mode build

---

## Data Lifecycle Analysis

### Entity: PluginDescriptor (plugins table)

| Operation | Backend | UI Component | User Access | Status |
|-----------|---------|--------------|-------------|--------|
| CREATE | PUT manifest endpoint (design doc) | No admin UI specified | Via manifest upload (API-only) | Partial -- no UI |
| READ | GET /api/plugins (design doc) | Sidebar renders menu items, plugin frames | Dynamic nav items, plugin routes | Needs implementation |
| UPDATE | PUT manifest endpoint (upsert) | No admin UI | Via manifest re-upload | Partial -- no UI |
| DELETE | DELETE /api/plugins/{id} (design doc) | No admin UI | API-only | Partial -- no UI |

**Completeness**: 50% — Backend CRUD is designed, but CREATE/UPDATE/DELETE are API-only (no admin UI for plugin management). READ is consumed by extension point rendering.

**Assessment**: This is BY DESIGN. The design document explicitly states manifests are uploaded via API (like CI pipeline in DevSkiller). No admin UI is a non-goal for now.

### Entity: Product Plugin Data (JSONB on products)

| Operation | Backend | UI Component | User Access | Status |
|-----------|---------|--------------|-------------|--------|
| CREATE | PUT /api/plugins/{id}/products/{pid}/data | Plugin iframe (via SDK) | Plugin calls thisPlugin.setData() | Needs implementation |
| READ | GET /api/plugins/{id}/products/{pid}/data + GET /api/products/{id} returns pluginData | Plugin iframe + product detail tabs | Plugin reads via SDK, host shows in tabs | Needs implementation |
| UPDATE | PUT (same as CREATE, replaces) | Plugin iframe | Plugin calls thisPlugin.setData() | Needs implementation |
| DELETE | DELETE /api/plugins/{id}/products/{pid}/data | Plugin iframe | Plugin calls thisPlugin.removeData() | Needs implementation |

**Completeness**: 0% (nothing implemented yet) — but design is complete for all operations.

### Entity: PluginObject (plugin_objects table)

| Operation | Backend | UI Component | User Access | Status |
|-----------|---------|--------------|-------------|--------|
| CREATE | PUT /api/plugins/{id}/objects/{type}/{oid} | Plugin iframe (via SDK) | Plugin calls thisPlugin.objects.save() | Needs implementation |
| READ | GET list and single endpoints | Plugin iframe | Plugin calls thisPlugin.objects.list/get() | Needs implementation |
| UPDATE | PUT (same as CREATE, upsert by unique key) | Plugin iframe | Plugin calls thisPlugin.objects.save() | Needs implementation |
| DELETE | DELETE /api/plugins/{id}/objects/{type}/{oid} | Plugin iframe | Plugin calls thisPlugin.objects.delete() | Needs implementation |

**Completeness**: 0% (nothing implemented yet) — but design is complete for all operations.

**Orphaned Operations**: None by design — all data entities have complete CRUD lifecycles specified. Plugin data and objects are created/consumed by plugins through the SDK, not by host UI.

---

## User Journey Impact Assessment

### Extension Point: menu.main (Sidebar)

| Dimension | Current | After | Assessment |
|-----------|---------|-------|------------|
| Reachability | N/A (no plugins) | Top-level sidebar nav | Will be immediately visible |
| Discoverability | N/A | 9/10 — same level as Products/Categories | Excellent |
| Flow Integration | N/A | Seamless — matches existing nav pattern | Positive |

### Extension Point: product.detail.tabs

| Dimension | Current | After | Assessment |
|-----------|---------|-------|------------|
| Reachability | N/A — no product detail page exists | Requires new page creation | Needs new page |
| Discoverability | N/A | 7/10 — tab pattern is standard but requires navigating to detail first | Good |
| Flow Integration | N/A | Requires a navigation path from product list to detail view | Needs route + link |

### Extension Point: product.list.filters

| Dimension | Current | After | Assessment |
|-----------|---------|-------|------------|
| Reachability | N/A | Inline on product list page | Immediately visible |
| Discoverability | N/A | 8/10 — inline with existing filter controls | Good |
| Flow Integration | ProductListPage has category/search/sort filters | Plugin filters added alongside | Additive, non-disruptive |

---

## Issues Requiring Decisions

### Critical (Must Decide Before Proceeding)

1. **Product Detail Page Does Not Exist**
   - **id**: `scope-product-detail-page`
   - **Issue**: The `product.detail.tabs` extension point requires a read-only product detail page, but only ProductListPage and ProductFormPage exist. There is no page where product detail tabs can be rendered.
   - **Options**:
     - A) Create a new `ProductDetailPage` with tabs (read-only product view + plugin tab iframes)
     - B) Add tabs to the existing `ProductFormPage` (conflates edit and view concerns)
     - C) Defer `product.detail.tabs` extension point to a future iteration
   - **Recommendation**: Option A — create a new ProductDetailPage. It follows the design document, maintains separation between view and edit, and provides a natural location for plugin tabs.
   - **Rationale**: The design explicitly specifies "product detail (read-only) view" for this extension point. Mixing it into the form page would create UX confusion.

2. **PluginDescriptor Entity: String PK vs BaseEntity**
   - **id**: `entity-pk-strategy`
   - **Issue**: The design specifies `PluginDescriptor` with a String PK (`id = pluginId` like "warehouse"). This diverges from `BaseEntity` which uses SEQUENCE Long PK. The codebase has no precedent for String PKs.
   - **Options**:
     - A) String PK as designed — `@Id private String id` (no BaseEntity, manual timestamps)
     - B) Extend BaseEntity with auto Long PK + unique `pluginId` column (follows existing pattern but wastes the Long PK)
   - **Recommendation**: Option A — use String PK as designed. The plugin ID IS the natural primary key, matching the URL path (`/api/plugins/warehouse`). Adding a surrogate Long PK adds no value and complicates foreign keys in `plugin_objects`.
   - **Rationale**: Design document is explicit, and the pluginId-as-PK pattern simplifies all downstream code (controllers, services, FK references).

### Important (Should Decide)

1. **JSONB Hibernate Support Verification**
   - **id**: `jsonb-hibernate-support`
   - **Issue**: No JSONB usage exists in the codebase. Hibernate 7 (Spring Boot 4) should support `@JdbcTypeCode(SqlTypes.JSON)` natively, but this needs verification before implementation. If not supported out of the box, may need `hypersistence-utils` or similar dependency.
   - **Options**:
     - A) Verify during implementation (establish pattern on Product.pluginData first, then replicate)
     - B) Add `hypersistence-utils` dependency preemptively
   - **Default**: Option A — verify natively first, Hibernate 7+ should handle it
   - **Rationale**: Modern Hibernate versions support JSONB natively with `@JdbcTypeCode`. Adding unnecessary dependencies contradicts the minimal-implementation standard.

2. **SDK Build Configuration**
   - **id**: `sdk-build-config`
   - **Issue**: The SDK needs a separate Vite IIFE build. The design mentions either a dedicated `vite.sdk.config.ts` or multi-entry in the main config. The current Vite config is minimal (react plugin, output to `../resources/static`, dev proxy).
   - **Options**:
     - A) Separate `vite.sdk.config.ts` with dedicated build script
     - B) Multi-entry in main `vite.config.ts`
   - **Default**: Option A — separate config file with a dedicated npm script (`build:sdk`)
   - **Rationale**: Keeps the main app build clean. SDK has fundamentally different output requirements (IIFE, no React, different outDir). Separate config is clearer and avoids build conflicts.

3. **Plugin Filter Mechanism for JSONB Queries**
   - **id**: `jsonb-filter-backend`
   - **Issue**: The `product.list.filters` extension point sends filter values to the host, which must query products by JSONB content (`plugin_data->'{pluginId}'` matching criteria). The current `ProductController.list()` supports `category`, `search`, and `sort` params but has no JSONB filtering capability. The design mentions "backend supports JSONB filtering via a query parameter on `GET /api/products`" but does not specify the query parameter format.
   - **Options**:
     - A) Add a generic `pluginFilter` query param to GET /api/products (e.g., `?pluginFilter=warehouse:stock>0`)
     - B) Add a dedicated filtering endpoint or use JPA Specification for dynamic JSONB criteria
     - C) Handle filtering client-side in the host (fetch all products, filter by pluginData in JS)
   - **Default**: Option C for initial implementation — simplest, works at small scale
   - **Rationale**: Server-side JSONB filtering is complex to design generically and the app is pre-alpha with small data sets. Client-side filtering gets the feature working; server-side can be optimized later.

4. **Existing Plugin Interface/Class Removal**
   - **id**: `remove-old-plugin-interfaces`
   - **Issue**: `Plugin.java` (interface) and `PluginDescriptor.java` (interface) must be removed/replaced. `PluginRegistry.java` must be rewritten. The existing `PluginRegistryTests.java` tests the current bean-based registry. This is a clean break, not an evolution.
   - **Options**:
     - A) Delete all 3 files and create new ones from scratch
     - B) Rename old files (e.g., `Plugin.java.bak`) for reference during implementation
   - **Default**: Option A — clean deletion and recreation
   - **Rationale**: The old code is 47 lines total across 3 files and provides no reusable logic. The design document is the reference, not the old code.

---

## Recommendations

1. **Establish JSONB pattern early**: Implement migration 005 (pluginData on products) and verify `@JdbcTypeCode(SqlTypes.JSON)` works with PostgreSQL before building all three JSONB-dependent entities. This de-risks the entire backend.

2. **Implementation order**: Migrations -> Entities -> Repositories -> Services -> Controllers -> Frontend infrastructure -> Extension points -> SDK. Backend-first, bottom-up.

3. **Create ProductDetailPage**: This is required for `product.detail.tabs` and adds value even without plugins (read-only product view is standard UX). Add a route at `/products/:id` between list and edit routes.

4. **Reuse existing patterns strictly**: Follow `ProductController`/`ProductService` patterns exactly for all new controllers/services. Use Java records for all request/response DTOs. Follow the `from()` factory method pattern.

5. **Plugin context as React Context**: Create a `PluginProvider` at the app root that fetches plugins on mount and provides them to Sidebar (menu items), router (dynamic routes), and product pages (tabs/filters). Avoids prop drilling and redundant fetches.

6. **Message handler registry pattern**: Follow DevSkiller's handler registry pattern (each handler declares supported types) rather than a monolithic switch statement, as recommended in the design document.

---

## Risk Assessment

- **Complexity Risk**: HIGH — ~30 new files spanning backend entities, REST API, database migrations, frontend components, iframe communication, and a standalone SDK bundle. However, the design document is exceptionally detailed, reducing ambiguity.
- **Integration Risk**: MEDIUM — JSONB is a new pattern for this codebase; iframe/postMessage communication is inherently harder to test than HTTP. Plugin filter JSONB queries add backend complexity.
- **Regression Risk**: LOW — Changes to existing files are additive (nullable field on Product, new items in Sidebar, new routes in router). No existing behavior is removed or altered.
