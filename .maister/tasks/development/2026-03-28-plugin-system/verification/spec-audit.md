# Specification Audit: Plugin System

**Auditor**: Independent specification review (pre-implementation)
**Date**: 2026-03-28
**Specification**: `.maister/tasks/development/2026-03-28-plugin-system/implementation/spec.md`
**Design Reference**: `.maister/docs/project/plugin-system-design.md`

## Compliance Status: Mostly Compliant

The specification is detailed, well-structured, and closely aligned with the design document. It is implementable as written. However, there are several gaps, ambiguities, and inconsistencies that should be resolved before implementation begins.

---

## Critical Issues

### C1. PluginFrame `window.name` context format is underspecified for the general case

**Spec Reference**: Requirement 10 (PluginFrame component) -- "Sets `iframe.name` to context string (format: `EXTENSION_POINT{JSON}`) before setting `src`"

**Design Reference**: Design doc shows `RENDER{"pluginId":"warehouse","pluginName":"Warehouse Management","hostOrigin":"http://localhost:8080"}` for general rendering and `PRODUCT_DETAIL{"pluginId":"warehouse","hostOrigin":"...","productId":42}` for product detail tabs.

**Gap**: The spec mentions `PRODUCT_DETAIL{...productId}` context for product detail tabs (requirement 15) but never specifies what extension point prefix and JSON fields are used for:
- `menu.main` full-page plugin rendering (the design doc says `RENDER{...}` but the spec does not)
- `product.list.filters` inline filter iframes

Without this, the implementer must guess or reference the design doc directly. The spec should be self-contained for implementation.

**Category**: Incomplete
**Severity**: Critical -- Every PluginFrame instance needs to know what context string to construct. Three distinct extension point types require three distinct context formats.

**Recommendation**: Explicitly enumerate the `window.name` format for each extension point type:
- `menu.main` full-page: `RENDER{"pluginId":"...","hostOrigin":"..."}`
- `product.detail.tabs`: `PRODUCT_DETAIL{"pluginId":"...","hostOrigin":"...","productId":N}`
- `product.list.filters`: `FILTER{"pluginId":"...","hostOrigin":"..."}` (or whatever is intended)

### C2. JSONB filter query parameter format is unspecified

**Spec Reference**: Requirement 6 -- "Add JPA Specification-based filtering on `GET /api/products` to query products by plugin data content (e.g., `plugin_data->'warehouse'->>'stock' > '0'`). Support a query parameter like `pluginFilter` for the host to pass filter criteria."

**Scope Clarifications**: Decision 5 says "Server-side JSONB filtering with JPA Specification (user override from client-side default)".

**Gap**: The spec says "a query parameter like `pluginFilter`" with an example SQL expression but never defines:
1. The exact query parameter name
2. The format/syntax of the parameter value (how does the frontend encode "warehouse stock > 0" as a query string?)
3. What operators are supported (equality? comparison? existence?)
4. How multiple plugin filters combine (AND? OR?)
5. How the host translates a `filterChange` message from a plugin iframe into this query parameter

This is the most underspecified requirement in the entire spec. The `filterChange` message payload is `{ value: any }` -- the host needs to know how to translate `{ value: true }` from the warehouse "In Stock" filter into a backend query.

**Category**: Incomplete
**Severity**: Critical -- This is a full vertical feature (plugin iframe sends filter -> host translates -> backend queries JSONB) with no defined contract between any of the layers.

**Recommendation**: Define the complete filter flow:
1. What `filterChange` payload structure the warehouse plugin sends (e.g., `{ value: true }` means "stock > 0")
2. How the host maps (pluginId + filter extension point metadata + filter value) to a backend query parameter
3. The exact query parameter format for `GET /api/products` (e.g., `?pluginFilter=warehouse.stock.gt.0`)
4. How the JPA Specification parses this into a JSONB predicate

---

## High Severity Issues

### H1. SDK build strategy contradicts between spec and scope clarifications

**Spec Reference**: Requirement 23 -- "Multi-entry in main `vite.config.ts` with library mode"

**Scope Clarifications**: Decision 4 -- "Multi-entry in main vite.config.ts (user override from separate config default)"

**Gap Analysis**: Decision 4 notes this was a "user override" from the original default of a separate `vite.sdk.config.ts`. The spec correctly reflects the user's decision. However, the spec provides no guidance on how to configure multi-entry in a single `vite.config.ts` when the main app and the SDK have fundamentally different output requirements:
- Main app: React SPA, output to `../resources/static`, includes React/Chakra
- SDK: IIFE library, output to `dist/assets/plugin-sdk.js`, no React, exposes `window.PluginSDK`

Vite's `build.lib` mode replaces the normal app build. Multi-entry does not mean "build two different formats in one pass." This likely requires either:
- A custom Vite plugin or multiple build passes
- Two separate npm scripts (`build:app` and `build:sdk`) using the same config with environment variables
- Actually using a separate config file despite the stated decision

**Category**: Ambiguous
**Severity**: High -- If the build approach is wrong, the SDK won't be produced or the main app build will break.

**Recommendation**: Clarify whether "multi-entry in main vite.config.ts" means (a) conditional logic in one file based on env var, (b) two separate build commands using one config, or (c) a Vite plugin for multi-output. The simplest working approach may be two npm scripts: `build` for the app and `build:sdk` for the SDK, both referencing a shared or separate config.

### H2. `PluginDescriptor` entity lacks `enabled` field behavior specification

**Spec Reference**: Requirements 7 and 24 -- "validate that the pluginId exists and the plugin is enabled; return 404 otherwise" and manifest declares plugin fields.

**Design Reference**: Design doc `plugins` table has `enabled BOOLEAN NOT NULL DEFAULT true`.

**Gap**: The spec mentions validation that a plugin is "enabled" in requirement 7, and the design includes an `enabled` field. But the spec never defines:
1. How a plugin gets disabled (no `PATCH /api/plugins/{id}` endpoint, no enable/disable endpoint)
2. Whether disabled plugins still appear in `GET /api/plugins` (affects sidebar rendering)
3. Whether the manifest upload sets `enabled = true` always, or preserves existing state on update

Since there is no admin UI and no disable endpoint, the `enabled` field has no way to be set to `false`. It exists in the schema but is functionally dead code.

**Category**: Ambiguous
**Severity**: High -- The validation logic references `enabled` but there is no mechanism to change it. Implementer will build validation for a state that can never be reached.

**Recommendation**: Either (a) add a `DELETE /api/plugins/{id}` soft-disable behavior (set enabled=false instead of hard delete), (b) add a `PATCH /api/plugins/{id}/enabled` endpoint, or (c) defer `enabled` to a future iteration and remove it from validation requirements. Option (c) is most aligned with the minimal implementation standard.

### H3. ProductDetailPage navigation path not specified

**Spec Reference**: Requirement 15 -- "Read-only product detail view at `/products/:id`"

**Gap**: The spec defines what the page shows but not how users reach it. The current ProductListPage has "Edit" and "Delete" actions per row but no "View" link. The spec does not mention:
1. Adding a clickable product name/row in the list to navigate to `/products/:id`
2. Whether the existing "Edit" link should remain at `/products/:id/edit`
3. How the URL pattern `/products/:id` (detail) coexists with `/products/:id/edit` (form) in the router

The router currently has `products/:id/edit` for the form page. Adding `products/:id` for detail is fine at the routing level, but users need a way to get there.

**Category**: Incomplete
**Severity**: High -- A page with no navigation path to it is effectively unreachable.

**Recommendation**: Specify that the product name in the list table becomes a clickable link to `/products/:id`. Alternatively, add a "View" action button alongside "Edit" and "Delete".

### H4. `filterChange` message is one-way but spec implies request/response

**Spec Reference**: Requirement 16 -- "Listens for `filterChange` messages from filter iframes"

**Design Reference**: Message types table includes `filterChange` with direction "plugin -> host" and no response defined.

**Gap**: The design document's message format requires every message to have a `requestId` for correlation. The `filterChange` message has `requestId` in the example but the host never sends a response. The SDK's `sendMessageAndWait()` function will create a pending promise that times out after 10 seconds since no response arrives.

This means either:
1. The SDK needs a `sendMessage()` (fire-and-forget, no response expected) in addition to `sendMessageAndWait()`
2. The host should send a response to `filterChange` (e.g., `{ responseId, payload: { applied: true } }`)
3. The filter iframe sends `filterChange` via raw `window.parent.postMessage` instead of the SDK

**Category**: Incomplete
**Severity**: High -- Without resolution, every filter change will generate a 10-second timeout error in the plugin.

**Recommendation**: Add a `sendMessage()` (fire-and-forget) function to the SDK for event-style messages like `filterChange`. Or specify that the host responds with an acknowledgment.

### H5. Missing specification for how `pluginFetch` (generic proxy) works on the host side

**Spec Reference**: Requirement 19 -- `hostApp.fetch(url, options?)` (generic proxy)

**Design Reference**: Message type `pluginFetch` with payload `[url, { method?, body?, headers? }]`

**Gap**: The spec defines the SDK facade for `hostApp.fetch()` but does not specify:
1. How the host message handler processes `pluginFetch` -- does it call the backend API using `api/client.ts`? Or does it use raw `fetch()`?
2. Security considerations from the design doc: strip `credentials`, `mode`, `signal`; add `X-Requested-With: plugin-sdk` header; filter response headers
3. URL validation -- the design doc mentions rejecting paths with `..` to prevent directory traversal

The design doc (from DevSkiller reference) has significant detail on this, but the spec omits all of it. Without these, a plugin could craft a `pluginFetch` to any URL, including external services, with arbitrary credentials.

**Category**: Incomplete
**Severity**: High -- Security-relevant behavior is unspecified.

**Recommendation**: Add explicit specification for `pluginFetch` handling: (a) only allow relative URLs starting with `/api/`, (b) strip dangerous request properties, (c) add identifying header, (d) reject `..` in paths.

---

## Medium Severity Issues

### M1. Sidebar icon mapping for plugin menu items is unspecified

**Spec Reference**: Requirement 13 -- "appends plugin-contributed `menu.main` items sorted by priority"

**Design Reference**: `menu.main` declares `"icon": "warehouse"` (string identifier).

**Evidence**: The current Sidebar uses custom SVG components (`<ProductsIcon />`, `<CategoriesIcon />`) passed as React nodes to `NavItem`. The `NavItem` component accepts `icon: React.ReactNode`.

**Gap**: Plugin manifests declare icons as strings (e.g., `"warehouse"`). The spec does not define how to map a string icon identifier to a React icon component. Options include:
1. A lookup table mapping known icon strings to SVG components
2. Ignoring the icon field (render items without icons)
3. Using a generic icon for all plugins

**Category**: Incomplete
**Severity**: Medium -- Sidebar will render but may look broken without icons.

**Recommendation**: Define a simple icon lookup map (string -> component) with a fallback generic icon for unknown values.

### M2. Product plugin data PUT response inconsistency

**Spec Reference**: Requirement 3 -- "PUT replaces the entire plugin node... GET returns only this plugin's data node"

**Design Reference**: `PUT /api/plugins/{pluginId}/products/{productId}/data` returns `204 No Content`

**Gap**: The spec says "PUT replaces the entire plugin node" but does not specify the response status code. The design doc says `204 No Content`. This is fine, but requirement 3's phrasing groups GET/PUT/DELETE together without explicitly stating each status code. The implementer must reference the design doc for response codes.

**Category**: Ambiguous
**Severity**: Medium -- Could be implemented with 200 + body instead of 204.

**Recommendation**: Explicitly state response codes: `PUT -> 204 No Content`, `DELETE -> 204 No Content`, `GET -> 200 with JSON body`.

### M3. PluginObject response format missing `id` field

**Spec Reference**: Requirement 4 -- Response includes `objectId, data, createdAt, updatedAt`

**Design Reference**: Same response format

**Gap**: The PluginObject entity extends BaseEntity which has a Long `id`. The response format includes `objectId` (the business key) but not the internal `id`. This is fine from a design perspective (plugins should use objectId), but the spec should explicitly state that the internal Long `id` is NOT exposed in the API response.

**Category**: Ambiguous
**Severity**: Medium -- Implementer might include `id` in response following the ProductResponse pattern.

**Recommendation**: Explicitly note that `PluginObjectResponse` excludes the internal `Long id` from BaseEntity.

### M4. Missing specification for `PluginProvider` placement in component tree

**Spec Reference**: Requirement 12 -- "React context that fetches plugins from `GET /api/plugins` on mount"

**Technical Approach**: "PluginProvider at app root wraps children with plugin state"

**Evidence**: Current `main.tsx` renders `<ChakraProvider><RouterProvider /></ChakraProvider>`. The router creates the `<Layout>` (AppShell) inside route definitions.

**Gap**: The spec says "at app root" but `RouterProvider` uses `createBrowserRouter` which does not support wrapping with arbitrary providers easily. The `PluginProvider` needs to be either:
1. Inside the `Layout` component (after `AppShell`)
2. Wrapping `RouterProvider` in `main.tsx`
3. Inside `AppShell`

Option 2 works but means the plugin context is available before routes mount. Option 1/3 means it re-fetches on every route change unless properly memoized. The spec should specify placement.

**Category**: Ambiguous
**Severity**: Medium -- Incorrect placement could cause re-fetching or unavailability in route components.

**Recommendation**: Specify that `PluginProvider` wraps `RouterProvider` in `main.tsx`, ensuring plugin data is available to all route components including dynamic plugin routes.

### M5. PluginDescriptor `equals`/`hashCode` implementation not specified

**Spec Reference**: Standards Compliance section -- "Business key equals/hashCode on both entities"

**Backend Standards**: `standards/backend/models.md` -- "Use business key (natural ID) for `equals()` and `hashCode()`. Don't use `@Id` field."

**Gap**: For `PluginDescriptor`, the `@Id` IS the business key (the pluginId string). The standard says "don't use @Id field" but in this case the @Id is the natural business key. The spec should clarify that for `PluginDescriptor`, using `id` (which is the pluginId) for equals/hashCode is correct despite the general standard against using @Id.

For `PluginObject`, the business key is `(pluginId, objectType, objectId)`. The spec does not explicitly state this.

**Category**: Ambiguous
**Severity**: Medium -- Standards conflict for string PK entity.

**Recommendation**: Explicitly state: `PluginDescriptor` uses `id` (the pluginId string) for equals/hashCode. `PluginObject` uses `(pluginId, objectType, objectId)` composite business key.

### M6. No specification for handling duplicate unique constraint violations on PluginObject

**Spec Reference**: Requirement 4 -- "PUT .../objects/{objectType}/{objectId} (create/replace)"

**Gap**: The PUT endpoint is an upsert (create/replace). The unique constraint on `(pluginId, objectType, objectId)` means the service must check for an existing row and update it, or create a new one. The spec says "create/replace" but does not specify:
1. Whether this is a database-level UPSERT or an application-level find-or-create
2. What happens on a race condition (two concurrent PUTs for the same key)

**Category**: Incomplete
**Severity**: Medium -- Default JPA behavior would throw a constraint violation on concurrent creates.

**Recommendation**: Specify application-level find-then-save pattern: `repository.findByPluginIdAndObjectTypeAndObjectId(...).orElse(new PluginObject())`, then set fields and save.

---

## Low Severity Issues

### L1. Spec references `pluginName` in context but SDK context parsing does not extract it

**Spec Reference**: Requirement 22 -- "Parse `window.name` to extract extensionPoint (prefix before `{`) and JSON metadata (pluginId, hostOrigin, and optional productId)"

**Design Reference**: Context example includes `"pluginName":"Warehouse Management"` in the JSON.

**Gap**: The design doc includes `pluginName` in the context JSON, but the spec's requirement 22 only lists `pluginId`, `hostOrigin`, and `productId` as parsed fields. Minor inconsistency.

**Category**: Inconsistency
**Severity**: Low

### L2. No specification for iframe loading/error states

**Spec Reference**: Requirement 10 (PluginFrame)

**Gap**: The spec defines the iframe setup but not what happens when a plugin URL fails to load (e.g., plugin server is down). No loading indicator, error boundary, or timeout is specified.

**Category**: Incomplete
**Severity**: Low -- Pre-alpha, this is acceptable as a future enhancement.

### L3. Migration numbering assumes no concurrent development

**Spec Reference**: Requirement 8 -- "004: plugins table, 005: pluginData on products, 006: plugin_objects table"

**Gap**: The migration files use sequential numbers (004, 005, 006). If another feature branch adds a migration before this one is merged, there will be a conflict. The `includeAll` directive processes files alphabetically, so ordering matters.

**Category**: Incomplete
**Severity**: Low -- Pre-alpha single-developer project, unlikely to conflict.

### L4. Demo Warehouse plugin project structure unspecified

**Spec Reference**: Requirement 24 -- "Minimal React app (separate project or subdirectory) running at `http://localhost:3001`"

**Gap**: The spec says "separate project or subdirectory" but does not specify:
1. Exact directory path (e.g., `plugins/warehouse/`)
2. Whether it has its own `package.json`
3. Whether it's included in the Maven build or run separately

**Category**: Incomplete
**Severity**: Low -- Implementer can make reasonable choices here.

---

## Clarification Questions

### Q1. What is the intended behavior when the host receives a `pluginFetch` for a non-`/api/` URL?

The design document's DevSkiller reference strips dangerous properties from fetch requests and filters response headers. The spec's `hostApp.fetch(url, options?)` has no restrictions stated. Should the host:
- Only allow `/api/` prefixed URLs?
- Allow any relative URL served by Spring Boot (including `/assets/`)?
- Allow absolute external URLs?

### Q2. Should disabled plugins be returned by `GET /api/plugins`?

The frontend plugin context fetches all plugins to render extension points. If a disabled plugin is returned, its menu items and tabs would appear but its validation would fail (404 on plugin-scoped endpoints). Should the list endpoint filter to only enabled plugins?

### Q3. How should the host handle multiple `product.list.filters` from different plugins?

If two plugins contribute filters, and one sends `filterChange: true` and the other sends `filterChange: false`, how do these combine in the backend query? The spec mentions "passes filter values to the product query" but the aggregation logic is unspecified.

### Q4. Should the `PluginMessageHandler` be registered as a global singleton or per-PluginProvider instance?

The spec says "Global `window.addEventListener("message", ...)`" (requirement 11) and also says it is "Registered once at app root (inside `PluginProvider`)" (Technical Approach). If the PluginProvider unmounts and remounts (e.g., during hot reload), should the handler be re-registered? Should there be cleanup logic?

---

## Extra Features (in spec but not in design)

### E1. JPA Specification for JSONB filtering

**Spec Reference**: Requirement 6 -- "JPA Specification-based filtering"

**Design Reference**: The design doc mentions "backend supports JSONB filtering via a query parameter on GET /api/products" but does not prescribe JPA Specifications. The scope clarifications record this as a user override decision.

**Assessment**: This is a valid scope decision, not a discrepancy. Noted for completeness.

---

## Recommendations

### Before Implementation

1. **Resolve C1 and C2 immediately** -- These are the two areas where an implementer would be forced to guess. The `window.name` context format for each extension point and the JSONB filter query parameter contract are essential to building the vertical slices.

2. **Decide on H2 (enabled field)** -- Either add a mechanism to disable plugins or remove `enabled` from validation requirements to avoid dead code.

3. **Decide on H4 (filterChange response)** -- Choose fire-and-forget or ack-response pattern for `filterChange` messages.

### During Implementation

4. **Verify Hibernate 7 JSONB support early** -- As noted in the gap analysis, `@JdbcTypeCode(SqlTypes.JSON)` with PostgreSQL is untested in this codebase. Establish the pattern on `Product.pluginData` before building all three JSONB entities.

5. **Build the filter flow end-to-end in one pass** -- The filter feature spans plugin SDK, iframe message, host handler, backend query parameter, JPA Specification, and database JSONB query. Implementing it piecemeal will leave integration gaps.

6. **Test `pluginFetch` security boundaries** -- Even in pre-alpha, establish URL validation to prevent a plugin from fetching arbitrary endpoints.

---

## Summary Table

| ID | Finding | Category | Severity |
|----|---------|----------|----------|
| C1 | `window.name` context format underspecified per extension point | Incomplete | Critical |
| C2 | JSONB filter query parameter format and flow unspecified | Incomplete | Critical |
| H1 | SDK build strategy (multi-entry Vite) technically ambiguous | Ambiguous | High |
| H2 | `enabled` field has no setter mechanism | Ambiguous | High |
| H3 | ProductDetailPage has no navigation path from list | Incomplete | High |
| H4 | `filterChange` one-way message will cause SDK timeout | Incomplete | High |
| H5 | `pluginFetch` proxy security behavior unspecified | Incomplete | High |
| M1 | Sidebar icon mapping for plugin strings unspecified | Incomplete | Medium |
| M2 | Product plugin data PUT response code not explicit | Ambiguous | Medium |
| M3 | PluginObject response should explicitly exclude internal id | Ambiguous | Medium |
| M4 | PluginProvider placement in React component tree unspecified | Ambiguous | Medium |
| M5 | equals/hashCode business key not explicit for new entities | Ambiguous | Medium |
| M6 | PluginObject upsert concurrency handling unspecified | Incomplete | Medium |
| L1 | `pluginName` in context JSON inconsistency | Inconsistency | Low |
| L2 | No iframe loading/error states | Incomplete | Low |
| L3 | Migration numbering fragile for concurrent development | Incomplete | Low |
| L4 | Demo plugin project structure unspecified | Incomplete | Low |
