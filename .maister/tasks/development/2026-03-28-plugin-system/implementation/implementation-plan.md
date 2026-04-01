# Implementation Plan: Plugin System

## Overview
Total Steps: 52
Task Groups: 7
Expected Tests: 24-42

## Implementation Steps

### Task Group 1: Database Layer
**Dependencies:** None
**Estimated Steps:** 7

- [x] 1.0 Complete database layer
  - [x] 1.1 Write 4 integration tests for database migrations and JSONB support
    - Test PluginDescriptor entity round-trip (create with JSONB manifest, read back, verify fields)
    - Test Product.pluginData JSONB round-trip (set Map, persist, read back)
    - Test PluginObject entity round-trip with JSONB data column
    - Test PluginObject unique constraint on (pluginId, objectType, objectId) returns conflict
  - [x] 1.2 Delete old plugin files and create migration 004: plugins table
    - Delete `Plugin.java`, `PluginDescriptor.java` (interfaces), `PluginRegistry.java`
    - Delete `PluginRegistryTests.java`
    - Create `src/main/resources/db/changelog/2026/004-create-plugins-table.yaml`
    - Columns: `id` VARCHAR PK, `name` VARCHAR NOT NULL, `version` VARCHAR, `url` VARCHAR, `description` TEXT, `enabled` BOOLEAN DEFAULT true, `manifest` JSONB NOT NULL, `created_at` TIMESTAMP NOT NULL, `updated_at` TIMESTAMP NOT NULL
    - Rollback block included
  - [x] 1.3 Create migration 005: add pluginData JSONB column to products
    - `src/main/resources/db/changelog/2026/005-add-plugin-data-to-products.yaml`
    - Add nullable `plugin_data` JSONB column to `products` table
    - Add GIN index on `plugin_data` for JSONB query performance
    - Rollback: drop column
  - [x] 1.4 Create migration 006: plugin_objects table
    - `src/main/resources/db/changelog/2026/006-create-plugin-objects-table.yaml`
    - Sequence: `plugin_object_seq` (allocationSize 1)
    - Columns: `id` BIGINT PK, `plugin_id` VARCHAR FK to plugins, `object_type` VARCHAR NOT NULL, `object_id` VARCHAR NOT NULL, `data` JSONB NOT NULL, `created_at` TIMESTAMP NOT NULL, `updated_at` TIMESTAMP NOT NULL
    - Unique constraint on `(plugin_id, object_type, object_id)`
    - Indexes on `plugin_id`, and composite `(plugin_id, object_type)`
    - Rollback block included
  - [x] 1.5 Create PluginDescriptor JPA entity
    - Package: `pl.devstyle.aj.core.plugin`
    - String `@Id` (no BaseEntity), manual `createdAt`/`updatedAt`
    - `@JdbcTypeCode(SqlTypes.JSON)` for `manifest` column (`Map<String, Object>`)
    - Extracted fields: `name`, `version`, `url`, `description`, `enabled`
    - Business key equals/hashCode on `id` (pluginId)
    - `@Getter @Setter @NoArgsConstructor` (no @Data)
  - [x] 1.6 Create PluginObject JPA entity and modify Product entity
    - `PluginObject` extends `BaseEntity`, `@SequenceGenerator` for `plugin_object_seq`
    - Fields: `pluginId` VARCHAR, `objectType` VARCHAR, `objectId` VARCHAR, `data` JSONB (`Map<String, Object>`)
    - `@Table` with unique constraint annotation matching migration
    - Business key equals/hashCode on `(pluginId, objectType, objectId)`
    - Add nullable `pluginData` field (`Map<String, Object>` with `@JdbcTypeCode(SqlTypes.JSON)`) to `Product` entity
    - Add `pluginData` to `ProductResponse` record and its `from()` factory method
  - [x] 1.7 Create repositories and verify database layer tests pass
    - `PluginDescriptorRepository extends JpaRepository<PluginDescriptor, String>`
    - `PluginObjectRepository extends JpaRepository<PluginObject, Long>` with custom query methods: `findByPluginIdAndObjectType`, `findByPluginIdAndObjectTypeAndObjectId`, `deleteByPluginIdAndObjectTypeAndObjectId`
    - Run ONLY the 4 tests written in 1.1

**Acceptance Criteria:**
- The 4 database tests pass
- All 3 migrations apply cleanly with TestContainers PostgreSQL
- JSONB round-trips work for all 3 entities (PluginDescriptor.manifest, Product.pluginData, PluginObject.data)
- Existing product tests still pass (pluginData: null is additive)

---

### Task Group 2: Plugin Registry API
**Dependencies:** Group 1
**Estimated Steps:** 8

- [x] 2.0 Complete plugin registry API layer
  - [x] 2.1 Write 6 integration tests for plugin registry endpoints
    - `uploadManifest_createsNewPlugin_returns200` — PUT manifest, verify fields extracted
    - `uploadManifest_updatesExistingPlugin_returns200` — PUT twice, verify upsert
    - `getPlugins_returnsOnlyEnabled` — create 2 plugins (1 disabled), GET list
    - `getPlugin_returnsPluginWithExtensionPoints` — verify extensionPoints from manifest
    - `deletePlugin_removesPlugin_returns204` — DELETE, verify gone
    - `setPluginEnabled_togglesState_returns200` — PATCH enabled to false, verify
  - [x] 2.2 Add String-based constructor to EntityNotFoundException
    - Overload in `core/error/EntityNotFoundException.java` for `EntityNotFoundException(String entityName, String id)` to support String pluginId lookups
  - [x] 2.3 Create PluginDescriptorService
    - `uploadManifest(String pluginId, Map<String, Object> manifest)` — extract name, version, url, description; upsert PluginDescriptor
    - `findAllEnabled()` — return enabled plugins
    - `findById(String pluginId)` — return single plugin (any state, for admin)
    - `findEnabledOrThrow(String pluginId)` — validate plugin exists and enabled, throw EntityNotFoundException otherwise (reused by other services)
    - `delete(String pluginId)` — unregister
    - `setEnabled(String pluginId, boolean enabled)` — toggle
    - Reuse: Follow `ProductService` pattern (constructor injection, `@Transactional` read/write)
  - [x] 2.4 Create PluginResponse and PluginController
    - `PluginResponse` Java record with `from()` factory: id, name, version, url, description, enabled, extensionPoints (extracted from manifest)
    - `PluginController` at `@RequestMapping("/api/plugins")`
    - PUT `/{pluginId}/manifest` (body = raw manifest JSON map)
    - GET `/` (list enabled)
    - GET `/{pluginId}` (single)
    - DELETE `/{pluginId}` (unregister, 204)
    - PATCH `/{pluginId}/enabled` (body with `enabled` boolean)
    - Reuse: Follow `ProductController` pattern
  - [x] 2.5 Ensure plugin registry tests pass
    - Run ONLY the 6 tests written in 2.1

**Acceptance Criteria:**
- The 6 plugin registry tests pass
- Manifest upload creates plugin with extracted fields and stored JSONB manifest
- Extension points are extracted from manifest and included in responses
- PluginId validation returns 404 for missing/disabled plugins

---

### Task Group 3: Plugin Data and Objects API
**Dependencies:** Group 2
**Estimated Steps:** 8

- [x] 3.0 Complete plugin data and objects API layer
  - [x] 3.1 Write 6 integration tests for plugin data and plugin objects
    - `setProductPluginData_storesNamespacedData` — PUT data, GET product, verify pluginData contains namespace
    - `getProductPluginData_returnsOnlyPluginNode` — PUT data for 2 plugins, GET for one, verify isolation
    - `deleteProductPluginData_removesOnlyPluginNode` — DELETE, verify other plugin data intact
    - `savePluginObject_createsAndReturns` — PUT object, verify stored
    - `listPluginObjects_filtersByType` — create objects of 2 types, list one type
    - `savePluginObject_duplicateKey_updatesExisting` — PUT same key twice, verify upsert
  - [x] 3.2 Create PluginDataService
    - `getData(String pluginId, Long productId)` — read pluginData->{pluginId} node from Product
    - `setData(String pluginId, Long productId, Map<String, Object> data)` — write/replace pluginData->{pluginId} node
    - `removeData(String pluginId, Long productId)` — remove pluginData->{pluginId} node
    - Each method calls `pluginDescriptorService.findEnabledOrThrow(pluginId)` first
    - Uses `ProductRepository` to load Product, manipulates the `pluginData` map, saves
  - [x] 3.3 Create PluginDataController
    - `@RequestMapping("/api/plugins/{pluginId}/products/{productId}/data")`
    - GET — returns plugin's data node (or empty object)
    - PUT — replaces plugin's data node (200)
    - DELETE — removes plugin's data node (204)
    - Reuse: Follow `ProductController` pattern
  - [x] 3.4 Create PluginObjectService
    - `list(String pluginId, String objectType)` — find all by pluginId + objectType
    - `get(String pluginId, String objectType, String objectId)` — find one or throw
    - `save(String pluginId, String objectType, String objectId, Map<String, Object> data)` — upsert by unique key
    - `delete(String pluginId, String objectType, String objectId)` — remove or throw
    - Each method calls `pluginDescriptorService.findEnabledOrThrow(pluginId)` first
  - [x] 3.5 Create PluginObjectResponse and PluginObjectController
    - `PluginObjectResponse` record: id, pluginId, objectType, objectId, data, createdAt, updatedAt
    - `PluginObjectController` at `@RequestMapping("/api/plugins/{pluginId}/objects")`
    - GET `/{objectType}` — list
    - GET `/{objectType}/{objectId}` — single
    - PUT `/{objectType}/{objectId}` — upsert (200)
    - DELETE `/{objectType}/{objectId}` — remove (204)
  - [x] 3.6 Implement server-side JSONB filtering on GET /api/products
    - Add `pluginFilter` query parameter to `ProductController.list()`
    - Parse format: `{pluginId}:{jsonPath}:{operator}:{value}`
    - Create `PluginDataSpecification` implementing JPA `Specification<Product>`
    - Translate to native JSONB query: `plugin_data->'{pluginId}'->'{jsonPath}' {operator} '{value}'`
    - Supported operators: `eq`, `gt`, `lt`, `exists`, `bool`
    - Integrate with existing product listing (compose with existing category/search filters)
  - [x] 3.7 Ensure plugin data and objects tests pass
    - Run ONLY the 6 tests written in 3.1

**Acceptance Criteria:**
- The 6 plugin data/objects tests pass
- Plugin data is namespaced per pluginId within Product.pluginData JSONB
- Plugin objects respect the unique constraint (pluginId, objectType, objectId)
- JSONB filtering on GET /api/products works with pluginFilter parameter
- All endpoints validate pluginId exists and is enabled (404 otherwise)

---

### Task Group 4: Frontend Infrastructure (Plugin Context, Iframe, Messaging)
**Dependencies:** Group 2
**Estimated Steps:** 8

- [x] 4.0 Complete frontend plugin infrastructure
  - [x] 4.1 Write 5 tests for frontend plugin infrastructure
    - Test PluginContext provides plugins after fetch
    - Test extension point resolution: getMenuItems(), getProductDetailTabs(), getProductListFilters()
    - Test PluginMessageHandler validates "aj.plugin." prefix and rejects invalid
    - Test PluginMessageHandler routes pluginFetch to api client and returns response
    - Test PluginMessageHandler handles filterChange as fire-and-forget (no response sent)
  - [x] 4.2 Create plugin API module
    - `src/main/frontend/src/api/plugins.ts`
    - Interfaces: `PluginResponse`, `ExtensionPoint`, `ManifestPayload`
    - Functions: `getPlugins()`, `getPlugin(id)`, `uploadManifest(id, manifest)`, `deletePlugin(id)`, `setPluginEnabled(id, enabled)`
    - Reuse: Follow `api/products.ts` pattern with `api` client wrapper
  - [x] 4.3 Create PluginContext and PluginProvider
    - `src/main/frontend/src/plugins/PluginContext.tsx`
    - React context with: plugins list, loading state, refetch function
    - Helper functions: `getMenuItems()`, `getProductDetailTabs(pluginId?)`, `getProductListFilters()`
    - Extension point resolution: collect from all enabled plugins' manifests, sort by priority
    - `usePluginContext()` hook for consumers
    - `PluginProvider` wraps app root, fetches plugins on mount
  - [x] 4.4 Create iframe registry and PluginFrame component
    - `src/main/frontend/src/plugins/iframeRegistry.ts` — `Map<HTMLIFrameElement, PluginInfo>` with register/unregister/findBySource helpers
    - `src/main/frontend/src/plugins/PluginFrame.tsx`
    - Props: pluginId, pluginUrl, contextType, contextData (productId etc.)
    - Renders sandboxed iframe: `allow-scripts allow-same-origin allow-forms allow-popups allow-modals allow-downloads`
    - Sets `iframe.name` to context string before setting `src` (format per extension point type)
    - Registers iframe in registry on mount, unregisters on unmount
    - Preserves iframe across re-renders (ref-based, no destroy/recreate on tab switch)
  - [x] 4.5 Create PluginMessageHandler
    - `src/main/frontend/src/plugins/PluginMessageHandler.ts`
    - Global `window.addEventListener("message", ...)` — registered once at app root (inside PluginProvider)
    - Validates `"aj.plugin."` prefix on requestId
    - Validates origin against registered plugin URLs
    - Resolves pluginId by matching `event.source` to known iframes via registry
    - Handler registry pattern: each handler declares supported message types
    - Handlers: `pluginFetch` (proxy to backend via api client with security stripping per Req 11c), `getProducts`, `getProduct`, `getPlugins`, `getData`, `setData`, `removeData`, `objectsList`, `objectsGet`, `objectsSave`, `objectsDelete`
    - `filterChange` is fire-and-forget (no response sent, updates filter state, triggers refetch)
    - Sends response/error back via `postMessage` with `responseId` matching `requestId`
  - [x] 4.6 Ensure frontend plugin infrastructure tests pass
    - Run ONLY the 5 tests written in 4.1

**Acceptance Criteria:**
- The 5 frontend infrastructure tests pass
- PluginContext fetches and provides plugins to consumers
- Extension point resolution correctly collects and sorts contributions from all plugins
- PluginFrame renders sandboxed iframe with correct context string
- PluginMessageHandler routes messages by type and validates origins
- pluginFetch strips credentials/mode/signal and restricts response headers (Req 11c)
- filterChange is fire-and-forget (no response)

---

### Task Group 5: Frontend Extension Points and Pages
**Dependencies:** Groups 3 and 4
**Estimated Steps:** 8

- [x] 5.0 Complete frontend extension points and pages
  - [x] 5.1 Write 5 tests for extension point rendering and pages
    - Test Sidebar renders hardcoded items then plugin menu items below separator
    - Test ProductDetailPage renders product info in Details tab and plugin tabs
    - Test ProductListPage renders plugin filter iframes in filter bar
    - Test plugin page route renders full-page PluginFrame for /plugins/:pluginId/*
    - Test PluginListPage renders table of plugins with name, version, URL, enabled columns
  - [x] 5.2 Integrate PluginProvider into app and update Sidebar
    - Wrap app root with `PluginProvider` in main.tsx or App component
    - Update `Sidebar.tsx`: render hardcoded nav items (Products, Categories, Plugins) first, then a separator, then plugin-contributed `menu.main` items sorted by priority
    - Each plugin menu item links to `/plugins/{pluginId}{path}`
    - Reuse: existing `NavItem` component (accepts `to`, `label`, `icon` props)
  - [x] 5.3 Create ProductDetailPage
    - `src/main/frontend/src/pages/ProductDetailPage.tsx`
    - Route: `/products/:id` (read-only view)
    - Shows product info: name, description, price, SKU, category, photo
    - Tab navigation: first tab is "Details" (product info), subsequent tabs from plugins (`product.detail.tabs`)
    - Each plugin tab renders a `PluginFrame` with `PRODUCT_DETAIL{...productId}` context
    - Preserve iframe across tab switches (render all, show/hide with CSS, not conditional rendering)
  - [x] 5.4 Update ProductListPage with plugin filter extension point
    - Render inline `PluginFrame` iframes for each `product.list.filters` contribution in the filter bar area
    - Listen for `filterChange` messages from filter iframes (via PluginMessageHandler callback)
    - Store filter state: `{ pluginId, extensionLabel, value }`
    - Construct `pluginFilter` query param and pass to product query
    - Update product list link: each product row/card becomes clickable, navigating to `/products/:id`
  - [x] 5.5 Add plugin page route and create management pages
    - Update `router.tsx`: add `/products/:id` route (ProductDetailPage), `/plugins` route (PluginListPage), `/plugins/new` route (PluginFormPage), `/plugins/:pluginId/edit` route (PluginFormPage), `/plugins/:pluginId/*` catch-all route (PluginFrame full page)
    - Create `PluginListPage.tsx` — table of all plugins (including disabled): name, pluginId, version, URL, enabled status. Each row links to detail page. "Add Plugin" button links to form page.
    - Create `PluginDetailPage.tsx` — read-only: plugin name, version, URL, description, enabled toggle, formatted JSON manifest. Edit button navigates to form page.
    - Create `PluginFormPage.tsx` — form at `/plugins/new` (create) and `/plugins/:pluginId/edit` (edit). Fields: pluginId (text, required, immutable on edit), manifest (JSON textarea). On submit calls `PUT /api/plugins/{pluginId}/manifest`. Shows validation errors.
  - [x] 5.6 Ensure frontend extension point tests pass
    - Run ONLY the 5 tests written in 5.1

**Acceptance Criteria:**
- The 5 frontend extension point tests pass
- Sidebar shows plugin menu items below hardcoded items
- ProductDetailPage shows product info with plugin tabs as PluginFrame iframes
- ProductListPage shows plugin filter iframes and filters products via pluginFilter param
- Plugin page route renders full-page PluginFrame
- Plugin management pages (list/detail/form) work end-to-end
- Product list items link to product detail page

---

### Task Group 6: Plugin SDK and Demo Plugin
**Dependencies:** Groups 4 and 5
**Estimated Steps:** 8

- [x] 6.0 Complete plugin SDK and demo Warehouse plugin
  - [x] 6.1 Write 4 tests for SDK core functionality
    - Test context parsing from window.name: extracts extensionPoint + JSON metadata
    - Test sendMessageAndWait correlates response by requestId
    - Test sendMessageAndWait times out after 10 seconds
    - Test sendFireAndForget posts message without creating pending promise
  - [x] 6.2 Create SDK types and context module
    - `src/main/frontend/src/plugin-sdk/types.ts` — PluginContext, Product, PluginObject interfaces
    - `src/main/frontend/src/plugin-sdk/context.ts` — parse `window.name` to extract extensionPoint (prefix before `{`) and JSON metadata (pluginId, hostOrigin, optional productId)
  - [x] 6.3 Create SDK messaging core
    - `src/main/frontend/src/plugin-sdk/messaging.ts`
    - `sendMessageAndWait(type, payload, hostOrigin)` — generates `"aj.plugin." + crypto.randomUUID()` requestId, posts to `window.parent`, stores pending promise in `Map<string, {resolve, reject}>`, timeout 10s
    - `sendFireAndForget(type, payload, hostOrigin)` — posts message without creating pending promise (for filterChange)
    - Response listener matches `responseId` to pending promises
  - [x] 6.4 Create SDK facades
    - `src/main/frontend/src/plugin-sdk/host-app.ts` — `hostApp` facade: `getProducts(params?)`, `getProduct(productId)`, `getPlugins()`, `fetch(url, options?)`
    - `src/main/frontend/src/plugin-sdk/this-plugin.ts` — `thisPlugin` facade: `getContext()`, `getData(productId)`, `setData(productId, data)`, `removeData(productId)`, `objects.list(type)`, `objects.get(type, id)`, `objects.save(type, id, data)`, `objects.delete(type, id)`
    - filterChange uses `sendFireAndForget`
  - [x] 6.5 Create SDK entry point and Vite build config
    - `src/main/frontend/src/plugin-sdk/index.ts` — exports `PluginSDK` on `window`: `{ hostApp, thisPlugin }`
    - `src/main/frontend/vite.sdk.config.ts` — library mode: entry `src/plugin-sdk/index.ts`, name `PluginSDK`, format `iife`, output to `dist/assets/plugin-sdk.js`
    - Add `"build:sdk"` npm script
    - Update main build script: `"build": "vite build && vite build -c vite.sdk.config.ts"`
    - SDK served by Spring Boot at `/assets/plugin-sdk.js`
  - [x] 6.6 Create demo Warehouse plugin
    - Separate directory: `plugins/warehouse/`
    - Minimal React app (Vite) running at `http://localhost:3001`
    - Loads SDK from host at `http://localhost:8080/assets/plugin-sdk.js`
    - Manifest: declares `menu.main` (Warehouse, icon warehouse, path /), `product.detail.tabs` (Stock Info, path /product-stock), `product.list.filters` (In Stock, path /filter-stock, type boolean, filterKey stock)
    - Main page `/` — warehouse list using `thisPlugin.objects` CRUD
    - Product stock tab `/product-stock` — reads productId from context, displays/edits stock info via `thisPlugin.getData/setData`
    - Stock filter `/filter-stock` — boolean toggle, sends `filterChange` via `sendFireAndForget`
  - [x] 6.7 Ensure SDK tests pass
    - Run ONLY the 4 tests written in 6.1

**Acceptance Criteria:**
- The 4 SDK tests pass
- SDK builds to IIFE bundle at `dist/assets/plugin-sdk.js`
- SDK correctly parses window.name context for all 3 extension point types
- Request/response correlation works with timeout
- filterChange uses fire-and-forget (no pending promise)
- Demo Warehouse plugin loads in iframe and communicates with host
- Warehouse manifest includes all 3 extension point types

---

### Task Group 7: Test Review and Gap Analysis
**Dependencies:** All previous groups (1-6)
**Estimated Steps:** 5

- [x] 7.0 Review and fill critical test gaps
  - [x] 7.1 Review tests from previous groups (30 existing tests)
    - Group 1: 4 database tests
    - Group 2: 6 registry API tests
    - Group 3: 6 data/objects API tests
    - Group 4: 5 frontend infrastructure tests
    - Group 5: 5 frontend extension point tests
    - Group 6: 4 SDK tests
  - [x] 7.2 Analyze gaps for plugin system feature only
    - Check: pluginId validation (404 for disabled/missing) across all endpoint groups
    - Check: pluginFilter JSONB query operators (eq, gt, lt, exists, bool)
    - Check: existing product tests still pass with added pluginData field
    - Check: ProductDetailPage with no plugins (only Details tab)
    - Check: Sidebar with no plugins (renders hardcoded items only)
    - Check: SDK timeout and error handling paths
  - [x] 7.3 Write up to 10 additional strategic tests
    - Backend: pluginFilter with `gt` operator filters products correctly
    - Backend: pluginFilter with `exists` operator works
    - Backend: GET plugin data for non-existent product returns 404
    - Backend: DELETE plugin then access plugin data returns 404
    - Backend: PATCH enabled false then GET plugins list excludes it
    - Frontend: PluginFrame sets iframe.name correctly for each context type
    - Frontend: PluginMessageHandler rejects message with invalid origin
    - Frontend: ProductDetailPage with no plugins shows only Details tab
    - SDK: hostApp.fetch strips credentials and mode from options
    - SDK: thisPlugin.getContext returns parsed context with productId
  - [x] 7.4 Run feature-specific tests only (expect 30-40 total)
    - All backend plugin tests (PluginIntegrationTests, PluginDataIntegrationTests, PluginObjectIntegrationTests)
    - All frontend plugin tests
    - All SDK tests
    - Existing product tests (verify no regression from pluginData addition)

**Acceptance Criteria:**
- All feature tests pass (30-40 total)
- No more than 10 additional tests added
- No regressions in existing product/category tests
- All JSONB filter operators tested
- PluginId validation tested across endpoint groups

---

## Execution Order

1. **Group 1: Database Layer** (7 steps) — migrations, entities, repositories
2. **Group 2: Plugin Registry API** (8 steps, depends on 1) — registry service, controller, manifest upload
3. **Group 3: Plugin Data and Objects API** (8 steps, depends on 2) — JSONB data ops, custom objects, JSONB filtering
4. **Group 4: Frontend Infrastructure** (8 steps, depends on 2) — plugin context, iframe, messaging
5. **Group 5: Frontend Extension Points and Pages** (8 steps, depends on 3 and 4) — sidebar, product detail, filters, routes, management pages
6. **Group 6: Plugin SDK and Demo Plugin** (8 steps, depends on 4 and 5) — SDK build, facades, warehouse plugin
7. **Group 7: Test Review and Gap Analysis** (5 steps, depends on all) — coverage review, gap filling

## Standards Compliance

Follow standards from `.maister/docs/standards/`:
- `global/` — Error handling, validation, coding style, minimal implementation, conventions, commenting
- `backend/models.md` — BaseEntity pattern for PluginObject, String PK divergence for PluginDescriptor, business key equals/hashCode, Lombok annotations
- `backend/api.md` — RESTful endpoints, plural nouns, consistent naming, proper status codes
- `backend/migrations.md` — Reversible migrations with rollback, focused changesets, proper indexing
- `backend/queries.md` — Parameterized queries for JSONB filtering, proper indexing
- `testing/backend-testing.md` — Integration-first with TestContainers, MockMvc + jsonPath, *Tests suffix, action_condition_expectedResult naming
- `testing/frontend-testing.md` — Vitest, @testing-library/react, per-file renderWithProviders(), vi.mock()
- `frontend/components.md` — Single responsibility (PluginFrame, PluginMessageHandler separate concerns)

## Notes

- **Test-Driven**: Each group starts with 2-8 tests before implementation
- **Run Incrementally**: Only new tests after each group, not entire suite
- **Mark Progress**: Check off steps as completed
- **Reuse First**: Prioritize existing patterns (ProductController, ProductService, api/products.ts, useProducts hook, NavItem, migration YAML format)
- **JSONB Pattern**: Establish on Product.pluginData first (Group 1), then replicate to PluginDescriptor.manifest and PluginObject.data
- **Old Plugin Files**: Delete Plugin.java, PluginDescriptor.java (interfaces), PluginRegistry.java in Group 1 before creating new entities
- **ProductDetailPage**: New page required for product.detail.tabs extension point — does not exist in codebase
- **Separate SDK Build**: vite.sdk.config.ts is mandatory — Vite library mode replaces app output format, cannot share config
