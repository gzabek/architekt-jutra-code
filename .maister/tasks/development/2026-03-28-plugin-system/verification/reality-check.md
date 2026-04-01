# Reality Check: Plugin System

**Date**: 2026-03-28
**Status**: WARNING -- Issues Found

## Deployment Decision: NO-GO

The backend is solid and production-ready. The frontend infrastructure compiles and renders correctly. However, the plugin message handler is missing handlers for 10 out of 12 SDK message types, which means the demo Warehouse plugin **cannot actually communicate with the backend** in a real runtime. The implementation is structurally complete but functionally broken for the core SDK-to-host communication path.

---

## Test Execution Results

### Backend Tests
- **62 total, 61 pass, 1 failure**
- All 21 plugin-specific tests pass (PluginDatabaseTests: 4, PluginRegistryIntegrationTests: 6, PluginDataAndObjectsIntegrationTests: 6, PluginGapTests: 5)
- Failing test: `ProductIntegrationTests.listProducts_withSearch_returnsCaseInsensitiveMatches` -- **pre-existing bug**, not caused by plugin changes. The test creates products with "Wireless Mouse" and searches for "wireless" expecting 1 result, but sample data migration (003) inserts "KEF LS50 Wireless II Bookshelf Speakers" which also matches. This failure exists on the base commit and is unrelated to the plugin system.

### Frontend Tests
- **27 total, 27 pass** (5 files)
- Plugin-specific: plugins.test.tsx (6 tests), extension-points.test.tsx (6 tests), plugin-sdk.test.ts (tests present)
- Act() warnings in foundation.test.tsx from PluginProvider async state updates (cosmetic, not functional)

### Build Verification
- Maven build succeeds (compiles, frontend builds, SDK builds)
- Plugin SDK IIFE bundle produced at `/assets/plugin-sdk.js` (2.00 kB minified)
- All 3 Liquibase migrations apply cleanly on TestContainers PostgreSQL 18
- TypeScript compilation passes with no errors

---

## Reality vs Claims

### Claim: "All 52 implementation steps complete"
**Reality**: Structurally true. All files exist, all code compiles, all entities/controllers/services are wired.

### Claim: "57 feature tests (56 pass, 1 pre-existing flaky)"
**Reality**: Verified. 21 backend plugin tests pass. 27 frontend tests pass. The 1 failing test is genuinely pre-existing (sample data conflict, not flaky -- it fails deterministically).

### Claim: "Demo Warehouse plugin loads in iframe and communicates with host"
**Reality**: PARTIALLY FALSE. The Warehouse plugin code exists and is well-structured. It loads in an iframe. However, it **cannot communicate with the host** for any operation except `filterChange` (fire-and-forget) and `hostApp.fetch()` (generic proxy). All `thisPlugin.objects.*`, `thisPlugin.getData/setData/removeData`, `hostApp.getProducts/getProduct/getPlugins` calls will receive `"Unknown message type"` errors.

---

## Critical Gaps

### C1. PluginMessageHandler only implements 2 of 12 SDK message types [CRITICAL]

**Claim**: "PluginMessageHandler routes messages by type using a handler registry pattern" (implementation plan 4.5)

**Reality**: The message handler at `src/main/frontend/src/plugins/PluginMessageHandler.ts` handles exactly two message types:
- `filterChange` (fire-and-forget, line 113)
- `pluginFetch` (generic fetch proxy, line 119)

Lines 137-139 contain a comment explicitly stating:
```
// Future handlers: getProducts, getProduct, getPlugins, getData, setData, removeData,
// objectsList, objectsGet, objectsSave, objectsDelete
// These will be implemented as the plugin SDK matures.
```

All other message types fall through to `sendError(event.source, event.origin, requestId, "Unknown message type: ${type}")` at line 142.

**Evidence**: The SDK sends these message types (verified in `this-plugin.ts` and `host-app.ts`):
- `getProducts`, `getProduct`, `getPlugins` (host-app.ts)
- `getData`, `setData`, `removeData` (this-plugin.ts)
- `objectsList`, `objectsGet`, `objectsSave`, `objectsDelete` (this-plugin.ts)

None of these have handlers. The Warehouse plugin uses `objects.list`, `objects.save`, `objects.delete`, `getData`, `setData` -- all will fail.

**Impact**: The demo plugin is non-functional. The core value proposition of the plugin system -- iframe plugins communicating with the host backend -- does not work for any data operation.

**Severity**: Critical -- blocks all plugin-to-host data communication

### C2. Product list `pluginFilter` query parameter is not wired through the frontend [HIGH]

**Claim**: "ProductListPage... passes filter values to the product query (via the server-side JSONB filter parameter)" (spec req 16)

**Reality**: The `ProductListPage` renders plugin filter iframes and receives `filterChange` callbacks (via PluginMessageHandler's `onFilterChange`). However:

1. The `useProducts` hook does not accept or pass a `pluginFilter` parameter (verified in `hooks/useProducts.ts` -- params are `categoryId`, `search`, `sortField` only)
2. The `getProducts` API function does not include `pluginFilter` in the query string (verified in `api/products.ts` -- only `category`, `search`, `sort` are passed)
3. The `ProductListPage` does not store filter state or construct the `pluginFilter` query parameter

The backend correctly accepts and processes `pluginFilter` (verified by passing integration tests for `gt`, `exists` operators). But the frontend never sends it.

**Impact**: The "In Stock" filter toggle in the Warehouse plugin will fire the message, but the product list will never actually filter. The end-to-end filter flow is broken.

**Severity**: High -- the filter feature appears to work (toggle renders, message fires) but does nothing

---

## Quality Gaps

### Q1. PluginDataSpecification uses string interpolation for SQL [MEDIUM]

**File**: `src/main/java/pl/devstyle/aj/product/PluginDataSpecification.java`

The JSONB filter specification constructs SQL via `String.format()` with single-quote escaping (`replace("'", "''")`) instead of using parameterized queries. While the `parse()` method validates the operator against a fixed allowlist (`eq|gt|lt|exists|bool`), the `pluginId`, `jsonPath`, and `value` fields are user-controlled strings from the query parameter. The single-quote escaping is a basic defense but is not equivalent to parameterized query safety.

The work-log notes a bug fix where HibernateCriteriaBuilder positional parameters conflicted with JSONB `?` operators, which led to this approach. This is a reasonable pragmatic decision for pre-alpha, but should be documented as a known security debt.

**Severity**: Medium -- pre-alpha acceptable, but needs parameterized approach before any real deployment

### Q2. PluginFrame initializedRef prevents re-initialization on prop changes [LOW]

**File**: `src/main/frontend/src/plugins/PluginFrame.tsx`

The `initializedRef` flag (line 36) prevents the iframe from being re-initialized if props change after the first render. This means if a `PluginFrame` receives new `contextData` (e.g., a different `productId`), the iframe will not update. The current implementation preserves iframes across re-renders (a spec requirement for tab switches), but the trade-off is that prop changes after mount are silently ignored.

This is likely intentional for the "preserve across tab switches" requirement but should be documented.

**Severity**: Low -- correct for current use cases, edge case for future

### Q3. Tests do not cover the end-to-end SDK message flow [MEDIUM]

The frontend tests for PluginMessageHandler test `pluginFetch` and `filterChange` correctly. The SDK tests test context parsing and message correlation. But **no test verifies that an SDK call like `thisPlugin.objects.list()` actually reaches the backend and returns data**. This is exactly the gap that allowed C1 to go undetected.

**Severity**: Medium -- the test suite gives false confidence about integration completeness

---

## What Actually Works

1. **Backend REST APIs** -- All plugin endpoints work correctly: manifest upload, plugin CRUD, plugin data CRUD, plugin objects CRUD, JSONB filtering. Verified by 21 passing integration tests with real PostgreSQL.

2. **Database layer** -- Migrations apply cleanly. JSONB round-trips work for all 3 entity types. GIN index on plugin_data. Unique constraints enforced on plugin_objects.

3. **Plugin SDK build** -- IIFE bundle correctly produced, contains all facades, exports `window.PluginSDK`. Context parsing works. Message correlation with timeout works. Fire-and-forget for filterChange works.

4. **Frontend rendering** -- Sidebar shows plugin menu items. ProductDetailPage renders plugin tabs. ProductListPage renders plugin filter iframes. Plugin management pages (list/detail/form) work. Routes are correctly configured.

5. **PluginFrame** -- Correctly sets `window.name` context, sandbox attributes, iframe registry registration/cleanup.

6. **filterChange** -- Fire-and-forget works end-to-end from SDK through message handler (no timeout, no response).

7. **hostApp.fetch()** -- Generic proxy with security stripping (path traversal, credentials, response header filtering) works.

8. **Demo Warehouse plugin** -- Code is well-structured, loads SDK from host, has all 3 extension point pages. Would work if the message handlers existed.

9. **No regressions** -- Existing product/category CRUD unaffected. pluginData: null is additive to ProductResponse.

---

## Integration Issues

### I1. SDK-to-host message routing is the only integration point that fails
All other integration points work: frontend fetches plugins from backend, sidebar renders extension points, iframe loads plugin URL, plugin SDK reads context from window.name. The single broken link is the message handler routing SDK data operations to backend API calls.

### I2. Filter state management is disconnected
The `PluginMessageHandler` accepts a `filterChange` callback via `options.onFilterChange`, but the `ProductListPage` does not implement the full chain: receive filter change -> update state -> include `pluginFilter` param in API call -> refetch products.

---

## Pragmatic Action Plan

### Priority 1: Implement missing message handlers [Critical, ~2 hours]

**Task**: Add handlers in `PluginMessageHandler.ts` for all 10 missing message types. Each handler should proxy to the appropriate API endpoint via `api/client.ts`:

- `getProducts` -> `GET /api/products`
- `getProduct` -> `GET /api/products/{productId}`
- `getPlugins` -> `GET /api/plugins`
- `getData` -> `GET /api/plugins/{pluginId}/products/{productId}/data`
- `setData` -> `PUT /api/plugins/{pluginId}/products/{productId}/data`
- `removeData` -> `DELETE /api/plugins/{pluginId}/products/{productId}/data`
- `objectsList` -> `GET /api/plugins/{pluginId}/objects/{objectType}`
- `objectsGet` -> `GET /api/plugins/{pluginId}/objects/{objectType}/{objectId}`
- `objectsSave` -> `PUT /api/plugins/{pluginId}/objects/{objectType}/{objectId}`
- `objectsDelete` -> `DELETE /api/plugins/{pluginId}/objects/{objectType}/{objectId}`

**Success criteria**: Warehouse plugin can list/create/delete warehouse items, read/write stock data on products.

### Priority 2: Wire pluginFilter through frontend [High, ~1 hour]

**Task**:
1. Add `pluginFilter?: string` to `UseProductsParams` and `ProductSearchParams`
2. Pass `pluginFilter` through `useProducts` -> `getProducts` -> query string
3. In `ProductListPage`, store filter state from `onFilterChange` callback
4. Construct `pluginFilter` param from stored filter state (format: `{pluginId}:{filterKey}:{operator}:{value}`)
5. Pass to `useProducts`

**Success criteria**: Toggling "In Stock" in the Warehouse filter iframe causes the product list to filter to products with stock data.

### Priority 3: Add integration test for SDK message round-trip [Medium, ~30 min]

**Task**: Add a test that simulates an SDK message (e.g., `objectsList`) through the message handler and verifies it calls the correct API endpoint.

**Success criteria**: Test proves message routing works for at least one data operation type.

---

## Functional Completeness Assessment

| Requirement | Status | Notes |
|-------------|--------|-------|
| Plugin manifest upload via PUT | WORKS | Tested, verified |
| Plugin registry CRUD | WORKS | All endpoints tested |
| Product plugin data CRUD | WORKS (backend) | Backend tested; frontend SDK->host path broken |
| Plugin objects CRUD | WORKS (backend) | Backend tested; frontend SDK->host path broken |
| JSONB filtering on GET /api/products | WORKS (backend) | Backend tested; frontend does not send param |
| PluginFrame with sandbox + context | WORKS | Verified in tests |
| Plugin message handler | PARTIAL | 2/12 types implemented |
| Plugin context provider | WORKS | Tested |
| Dynamic sidebar menu items | WORKS | Tested |
| ProductDetailPage with plugin tabs | RENDERS | Tabs display but plugin iframe cannot fetch data |
| Product list filter extension point | RENDERS | Filter renders but toggling has no effect |
| Plugin SDK IIFE bundle | WORKS | Builds, exports correctly |
| Plugin management pages | WORKS | List/detail/form pages functional |
| Demo Warehouse plugin | BROKEN | Cannot communicate with host for data ops |
| pluginId validation (404) | WORKS | Tested across endpoint groups |

**Overall functional completeness**: ~70%

The backend is ~100% complete. The frontend rendering/structure is ~95% complete. The SDK-to-host communication bridge is ~15% complete (2/12 handlers). The end-to-end filter flow is ~60% complete (backend + SDK + message fire work; frontend state management + query param missing).

---

## Summary

The plugin system has a solid backend, good frontend structure, and a working SDK build. The critical gap is in the **message handler bridge** between the SDK and the host -- 10 of 12 message types are unimplemented, with a comment saying "These will be implemented as the plugin SDK matures." This means the demo Warehouse plugin is decorative: it renders in iframes but cannot actually read or write data.

The second gap is the filter flow: the backend JSONB filtering works, but the frontend never sends the `pluginFilter` query parameter.

Fixing these two gaps (estimated ~3 hours total) would bring the system to a genuinely functional state where plugins can communicate with the host, read/write data, and filter products. Until then, the system demonstrates the architecture but does not deliver the working functionality described in the spec's success criteria.
