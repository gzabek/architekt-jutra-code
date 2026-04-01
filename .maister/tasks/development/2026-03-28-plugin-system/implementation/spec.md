# Specification: Plugin System

## Goal
Implement an iframe-isolated plugin system where plugins are standalone web apps that communicate with the host via postMessage RPC, with backend REST APIs for plugin registry (manifest-based), product plugin data (JSONB), and custom objects, frontend iframe hosting with extension point rendering, a plugin SDK built as IIFE bundle, and a demo Warehouse plugin.

## User Stories
- As an admin, I want to upload a plugin manifest via API so that the plugin is registered and its extension points appear automatically in the host app.
- As a user, I want to see plugin-contributed menu items in the sidebar so that I can navigate to plugin pages.
- As a user, I want to see plugin tabs on the product detail page so that I can view plugin-specific product information (e.g., stock levels).
- As a user, I want plugin-contributed filters on the product list page so that I can filter products by plugin data (e.g., "in stock").
- As a plugin developer, I want a lightweight SDK that provides hostApp and thisPlugin facades so that I can interact with the host app from within an iframe.

## Core Requirements

### Backend
1. **Plugin manifest upload** — `PUT /api/plugins/{pluginId}/manifest` that creates or updates a plugin entry by extracting `name`, `version`, `url`, `description` from the body and storing the full manifest as JSONB.
2. **Plugin registry endpoints** — `GET /api/plugins` (list all enabled), `GET /api/plugins/{pluginId}` (single), `DELETE /api/plugins/{pluginId}` (unregister), `PATCH /api/plugins/{pluginId}/enabled` with body `{ "enabled": false }` (toggle enabled state). Response includes `extensionPoints` extracted from the stored manifest.
3. **Product plugin data CRUD** — `GET/PUT/DELETE /api/plugins/{pluginId}/products/{productId}/data`. PUT replaces the entire plugin node in the product's `pluginData` JSONB column (namespaced by pluginId). GET returns only this plugin's data node.
4. **Plugin objects CRUD** — `GET /api/plugins/{pluginId}/objects/{objectType}` (list), `GET .../objects/{objectType}/{objectId}` (single), `PUT .../objects/{objectType}/{objectId}` (create/replace), `DELETE .../objects/{objectType}/{objectId}`. Unique constraint on `(pluginId, objectType, objectId)`.
5. **Product entity modification** — Add nullable `pluginData` JSONB column (`Map<String, Object>`) to `Product` entity. Include in `ProductResponse`.
6. **Server-side JSONB filtering** — Add JPA Specification-based filtering on `GET /api/products` to query products by plugin data content. Full filter flow contract:
    - Plugin filter iframe sends `filterChange` message: `{ type: "filterChange", requestId: "...", payload: { value: true } }`
    - Host message handler receives it, does NOT send a response (fire-and-forget — see Req 11b), and stores `{ pluginId, extensionLabel, value }` in filter state
    - Host constructs query param: `GET /api/products?pluginFilter={pluginId}:{jsonPath}:{operator}:{value}` (e.g., `pluginFilter=warehouse:stock:gt:0`)
    - Backend parses `pluginFilter` param into a JPA Specification that translates to: `plugin_data->'{pluginId}'->'{jsonPath}' {operator} '{value}'`
    - Supported operators: `eq` (equals), `gt` (greater than), `lt` (less than), `exists` (key exists and is not null), `bool` (boolean check)
    - The filter extension point entry in the manifest includes a `filterKey` field that the host uses to map the plugin's filter value to the correct JSONB path: `{ "label": "In Stock", "path": "/filter-stock", "type": "boolean", "filterKey": "stock" }`
7. **PluginId validation** — All `/api/plugins/{pluginId}/...` endpoints validate that the pluginId exists and the plugin is enabled; return 404 otherwise.
8. **Database migrations** — Three new Liquibase YAML migrations:
   - 004: `plugins` table (VARCHAR PK, JSONB manifest, extracted columns)
   - 005: `plugin_data JSONB` column on `products` table + GIN index
   - 006: `plugin_objects` table with sequence, unique constraint, indexes, FK to plugins
9. **EntityNotFoundException overload** — Add a String-based constructor to `EntityNotFoundException` for plugin lookups by String pluginId.

### Frontend
10. **PluginFrame component** — React component that renders a sandboxed `<iframe>` with `allow-scripts allow-same-origin allow-forms allow-popups allow-modals allow-downloads`. Sets `iframe.name` to context string before setting `src`. Registers iframe in a plugin-to-iframe registry. Cleans up on unmount. Preserves iframe across re-renders (no destroy/recreate on tab switch). Context format per extension point:
    - `menu.main`: `RENDER{"pluginId":"warehouse","pluginName":"Warehouse Management","hostOrigin":"http://localhost:8080"}`
    - `product.detail.tabs`: `PRODUCT_DETAIL{"pluginId":"warehouse","pluginName":"Warehouse Management","hostOrigin":"http://localhost:8080","productId":42}`
    - `product.list.filters`: `FILTER{"pluginId":"warehouse","pluginName":"Warehouse Management","hostOrigin":"http://localhost:8080"}`
11. **PluginMessageHandler** — Global `window.addEventListener("message", ...)` handler that validates `"aj.plugin."` prefix on requestId, validates origin against registered plugin URLs, resolves pluginId by matching `event.source` to known iframes, routes by message type using a handler registry pattern (not monolithic switch), and sends response/error back via `postMessage`.
    11b. **filterChange is fire-and-forget** — The `filterChange` message type is an exception to the request/response pattern. The host does NOT send a response. The plugin SDK should use a `sendFireAndForget()` method for filterChange (no pending promise, no timeout). The host handler updates filter state and triggers a product list refetch.
    11c. **pluginFetch security** — The host-side fetch handler strips `credentials`, `mode`, `signal` from the request options. Rejects URLs containing `..` (path traversal). Adds `X-Requested-With: plugin-sdk` header. Only returns CORS-safelisted response headers to the plugin (content-type, content-length, cache-control).
12. **Plugin context provider** — React context that fetches plugins from `GET /api/plugins` on mount and provides plugin list to Sidebar, router, and product pages. Avoids prop drilling and redundant fetches.
13. **Dynamic sidebar menu items** — Sidebar renders hardcoded nav items (Products, Categories, Plugins) first, then appends plugin-contributed `menu.main` items sorted by priority below a separator. Each item links to `/plugins/{pluginId}{path}`.
14. **Plugin page route** — Add `/plugins/:pluginId/*` catch-all route that renders a full-page `PluginFrame` with the matched plugin's URL + path suffix.
15. **ProductDetailPage (new)** — Read-only product detail view at `/products/:id`. Shows product info (name, description, price, SKU, category, photo). Renders `product.detail.tabs` extension point as tab navigation — each tab loads a `PluginFrame` with `PRODUCT_DETAIL{...productId}` context. First tab is "Details" (product info), subsequent tabs from plugins. Product list page must link to this view — each product row/card becomes clickable, navigating to `/products/:id`.
16. **Product list filter extension point** — ProductListPage renders inline `PluginFrame` iframes for each `product.list.filters` contribution in the filter bar. Listens for `filterChange` messages from filter iframes and passes filter values to the product query (via the server-side JSONB filter parameter).
17. **Plugin API module** — `api/plugins.ts` with typed interfaces and functions: `getPlugins()`, `getPlugin(id)`, `uploadManifest(id, manifest)`, `deletePlugin(id)`, `setPluginEnabled(id, enabled)`.
    17b. **PluginListPage** — Admin page at `/plugins` listing all plugins (including disabled) in a table with columns: name, pluginId, version, URL, enabled status. Each row links to the plugin detail page. "Add Plugin" button opens the form page.
    17c. **PluginDetailPage** — Read-only view at `/plugins/:pluginId` showing plugin name, version, URL, description, enabled toggle, and the full manifest rendered as formatted JSON. Edit button navigates to the form page.
    17d. **PluginFormPage** — Form at `/plugins/new` (create) and `/plugins/:pluginId/edit` (edit). Fields: pluginId (text, required, immutable on edit), manifest (JSON textarea). On submit, calls `PUT /api/plugins/{pluginId}/manifest` with the manifest body. Shows validation errors.
    17e. **Sidebar nav item** — Add "Plugins" nav item to the sidebar (hardcoded, below Categories).

### Plugin SDK
18. **SDK source structure** — TypeScript source in `frontend/src/plugin-sdk/` with files: `index.ts` (entry, exports `PluginSDK` on window), `messaging.ts` (sendMessageAndWait, pending map, response listener), `context.ts` (window.name parsing), `host-app.ts` (hostApp facade), `this-plugin.ts` (thisPlugin facade), `types.ts` (PluginContext, Product, PluginObject types).
19. **hostApp facade** — `getProducts(params?)`, `getProduct(productId)`, `getPlugins()`, `fetch(url, options?)` (generic proxy).
20. **thisPlugin facade** — `getContext()`, `getData(productId)`, `setData(productId, data)`, `removeData(productId)`, `objects.list(type)`, `objects.get(type, id)`, `objects.save(type, id, data)`, `objects.delete(type, id)`.
21. **Messaging core** — Request/response correlation using `"aj.plugin."` prefix + `crypto.randomUUID()`. Timeout after 10 seconds. Pending requests stored in `Map<string, {resolve, reject}>`. Additionally, `sendFireAndForget(type, payload)` for one-way messages like `filterChange` — posts message without creating a pending promise.
22. **Context initialization** — Parse `window.name` to extract extensionPoint (prefix before `{`) and JSON metadata (pluginId, hostOrigin, and optional productId for product detail tabs).
23. **SDK build** — Separate `vite.sdk.config.ts` for library mode build (library mode replaces app output format, so it cannot share a config with the main app). Entry `src/plugin-sdk/index.ts`, name `PluginSDK`, format `iife`, output to `dist/assets/plugin-sdk.js`. Add `"build:sdk"` npm script. Main build script runs both: `"build": "vite build && vite build -c vite.sdk.config.ts"`. Served by Spring Boot at `/assets/plugin-sdk.js`.

### Demo Warehouse Plugin
24. **Standalone React app** — Minimal React app (separate project or subdirectory) running at `http://localhost:3001`. Loads the plugin SDK from the host at `http://localhost:8080/assets/plugin-sdk.js`.
25. **Manifest** — Declares `menu.main` (label: "Warehouse", icon: "warehouse", path: "/"), `product.detail.tabs` (label: "Stock Info", path: "/product-stock"), `product.list.filters` (label: "In Stock", path: "/filter-stock", type: "boolean").
26. **Main page** (at `/`) — Shows a list of warehouses (stored as custom objects via `thisPlugin.objects`). Demonstrates CRUD on custom objects.
27. **Product stock tab** (at `/product-stock`) — Reads `productId` from SDK context, displays and allows editing stock info via `thisPlugin.getData/setData`.
28. **Stock filter** (at `/filter-stock`) — Renders a simple boolean toggle. Sends `filterChange` message when toggled.

## Reusable Components

### Existing Code to Leverage
- **`BaseEntity`** (`src/main/java/pl/devstyle/aj/core/BaseEntity.java`) — `PluginObject` entity extends this for id/createdAt/updatedAt pattern.
- **`ProductController`** (`src/main/java/pl/devstyle/aj/product/ProductController.java`) — Pattern for all 3 new controllers: constructor injection, `@RequestMapping`, `@ResponseStatus`, `@PathVariable`/`@RequestParam` conventions.
- **`ProductService`** (`src/main/java/pl/devstyle/aj/product/ProductService.java`) — Pattern for service layer: `@Transactional` read/write, `EntityNotFoundException` throws, record DTO mapping.
- **`ProductResponse`** (`src/main/java/pl/devstyle/aj/product/ProductResponse.java`) — Java record DTO pattern with `static from()` factory method. All new response records follow this.
- **`GlobalExceptionHandler`** (`src/main/java/pl/devstyle/aj/core/error/GlobalExceptionHandler.java`) — Already handles `EntityNotFoundException`, `BusinessConflictException`, validation errors. New plugin endpoints use same exception types (need to add String-based constructor to `EntityNotFoundException`).
- **`api/client.ts`** (`src/main/frontend/src/api/client.ts`) — Fetch wrapper reused by PluginMessageHandler to proxy plugin requests to backend. Also reused by new `api/plugins.ts` module.
- **`api/products.ts`** (`src/main/frontend/src/api/products.ts`) — Pattern for `api/plugins.ts`: typed interfaces + api client wrapper functions.
- **`useProducts` hook** (`src/main/frontend/src/hooks/useProducts.ts`) — Pattern for `usePlugins` hook: useState + useCallback + useEffect, data/loading/error state.
- **`Sidebar` NavItem component** (`src/main/frontend/src/components/layout/Sidebar.tsx`) — Reuse `NavItem` component directly for plugin menu items (already accepts `to`, `label`, `icon` props).
- **Migration YAML format** (`src/main/resources/db/changelog/2026/002-create-products-table.yaml`) — Template for all 3 new migrations: changeset structure, sequences, constraints, indexes, rollback.
- **`db.changelog-master.yaml`** — Uses `includeAll` for `./2026` directory, so new migration files in that directory are automatically included.

### New Components Required
- **`PluginDescriptor` JPA entity** — Cannot reuse `BaseEntity` because the design specifies String PK (pluginId is the natural key), not SEQUENCE Long. Manual `createdAt`/`updatedAt` management. JSONB `manifest` column is the first JSONB usage in the codebase.
- **`PluginObject` JPA entity** — Extends `BaseEntity` (reuse), but adds JSONB `data` column and multi-column unique constraint — new patterns.
- **`PluginDataService`** — JSONB node manipulation (read/write/delete a namespaced key within Product.pluginData) has no precedent in the codebase. Requires `@JdbcTypeCode(SqlTypes.JSON)` annotation and verified Hibernate JSONB support.
- **`PluginFrame` React component** — Iframe hosting with sandbox attributes, context via `window.name`, and iframe registry. No iframe usage exists in the frontend.
- **`PluginMessageHandler`** — postMessage communication layer. No postMessage handling exists. Handler registry pattern with type-based routing.
- **Plugin SDK** — Standalone TypeScript library compiled to IIFE. No library-mode Vite build exists. Six new files implementing the messaging protocol.
- **`ProductDetailPage`** — New page. No product detail (read-only) view exists; only list and form pages.
- **JPA Specification for JSONB filtering** — No JPA Specification usage exists. Needed for server-side filtering of products by plugin data content.

## Technical Approach

### Backend Architecture
- **Package**: All plugin backend code in `pl.devstyle.aj.core.plugin` (replacing the 3 existing files).
- **Entity design**: `PluginDescriptor` with String `@Id`, no `BaseEntity`. `PluginObject` extends `BaseEntity` with `@SequenceGenerator`. Both use `@JdbcTypeCode(SqlTypes.JSON)` for JSONB columns.
- **Service pattern**: `PluginDescriptorService` handles manifest upsert (extract fields from manifest body, store full manifest in JSONB). `PluginDataService` handles JSONB node operations on Product. `PluginObjectService` handles CRUD with unique constraint enforcement.
- **PluginId validation**: Each service method that takes pluginId first calls `PluginDescriptorService.findEnabledOrThrow(pluginId)` to validate existence and enabled status.
- **Product integration**: `Product` entity gains a nullable `pluginData` field. `ProductResponse` includes it. Existing product endpoints return it as-is (null when no plugin data).

### Frontend Architecture
- **Plugin context**: `PluginProvider` at app root wraps children with plugin state. Fetches plugins once, provides via React context. Components consume via `usePluginContext()` hook.
- **Iframe registry**: `Map<HTMLIFrameElement, PluginInfo>` maintained by `PluginFrame` components. Used by message handler to resolve plugin identity from `event.source`.
- **Message handler**: Registered once at app root (inside `PluginProvider`). Uses handler registry pattern — each handler object declares supported message types and implements `handle(type, payload, plugin)`. Handlers proxy requests to backend via `api/client.ts`.
- **Extension point resolution**: Plugin context provides helper functions like `getMenuItems()`, `getProductDetailTabs()`, `getProductListFilters()` that collect contributions from all enabled plugins, sorted by priority.
- **Routing**: Add `/products/:id` route for ProductDetailPage and `/plugins/:pluginId/*` catch-all for plugin pages.

### Plugin SDK Architecture
- **IIFE bundle**: Vite library mode compiles SDK to a single self-executing bundle that exposes `window.PluginSDK = { hostApp, thisPlugin }`.
- **Context flow**: Plugin loads SDK, SDK reads `window.name`, parses context (extensionPoint, pluginId, hostOrigin, optional productId). All subsequent messages are sent to `hostOrigin`.
- **Request correlation**: Each SDK method call generates a unique requestId (`"aj.plugin." + crypto.randomUUID()`), stores a pending promise, and sends via `window.parent.postMessage`. Response listener matches `responseId` to pending promises.

### Demo Plugin Architecture
- **Separate Vite React app** in a subdirectory (e.g., `plugins/warehouse/`). Minimal: index.html, one main component with route-based rendering for `/`, `/product-stock`, `/filter-stock`.
- **No shared code** with host — loads SDK via script tag from host URL.

## Implementation Guidance

### Testing Approach
- 2-8 focused tests per implementation step group
- Test verification runs only new tests, not entire suite
- **Backend tests**: Integration tests with TestContainers following `ProductIntegrationTests` pattern. Test JSONB round-trips explicitly (new pattern). Test manifest upsert (create + update), plugin data CRUD, plugin object CRUD with unique constraint, pluginId validation (404 for missing/disabled plugins).
- **Frontend tests**: Test message handler with mock `MessageEvent` objects. Test plugin context loading. Test extension point resolution logic.
- **SDK**: Unit tests for context parsing and message correlation.

### Standards Compliance
- **Entity modeling** (`standards/backend/models.md`) — `PluginObject` follows BaseEntity pattern. `PluginDescriptor` diverges (String PK) with documented justification. Business key equals/hashCode on both entities. `@Getter @Setter @NoArgsConstructor` (no `@Data`). LAZY fetch default.
- **API design** (`standards/backend/api.md`) — RESTful endpoints with plural nouns, consistent naming, proper HTTP methods and status codes.
- **Database migrations** (`standards/backend/migrations.md`) — Reversible migrations with rollback blocks, focused changesets, proper indexing.
- **Error handling** (`standards/global/error-handling.md`) — Reuse existing `EntityNotFoundException` and `GlobalExceptionHandler`. Fail-fast validation on pluginId.
- **Minimal implementation** (`standards/global/minimal-implementation.md`) — No speculative features, no schema validation on custom objects (explicitly a non-goal), no plugin management UI.
- **Backend testing** (`standards/testing/backend-testing.md`) — Integration-first with TestContainers, MockMvc + jsonPath assertions, `*Tests` suffix, `action_condition_expectedResult` naming.
- **Frontend components** (`standards/frontend/components.md`) — Single responsibility (PluginFrame, PluginMessageHandler are separate concerns), clear interfaces.

## Out of Scope
- Authentication and authorization
- Plugin-to-plugin communication
- Server-side plugin code (plugins are purely client-side)
- Plugin marketplace or dynamic installation UI
- Custom object schema validation (schemaless for now)
- Advanced plugin management (beyond basic list/detail/form — no plugin versioning history, no rollback, no approval workflows)
- FormData/File transfer via postMessage (not needed initially)
- Cursor-based pagination for custom objects
- CORS configuration (plugins communicate via postMessage, not direct HTTP to backend)

## Success Criteria
1. A plugin manifest uploaded via `PUT /api/plugins/warehouse/manifest` creates a plugin entry and its extension points appear in the host UI (sidebar menu item, product detail tab, product list filter).
2. The Warehouse demo plugin renders in an iframe, can read products via the SDK, can read/write plugin data on products, and can CRUD custom objects.
3. Product detail page shows plugin-contributed tabs with working iframes.
4. Product list page shows plugin-contributed filter iframes; toggling "In Stock" filter filters the product list via server-side JSONB query.
5. Plugin SDK is served at `/assets/plugin-sdk.js` and works correctly when loaded by external plugin apps.
6. All existing functionality (product CRUD, category CRUD, navigation) continues working unchanged.
7. Adding `pluginData: null` to product responses does not break existing consumers.
8. Integration tests pass for all backend endpoints with TestContainers.
