# Pragmatic Review: Plugin System Implementation

**Status**: Appropriate
**Overall Complexity**: Low-Medium (well-matched to pre-alpha stage)
**Date**: 2026-03-28

---

## Executive Summary

The plugin system implementation is **appropriately scoped** for a pre-alpha project. The architecture follows a proven pattern (iframe isolation + postMessage RPC, modeled after DevSkiller's production system) and the code is straightforward Spring Boot CRUD with a lightweight React integration layer. There is no over-engineering. The main concerns are minor: some SDK methods are declared but not yet wired on the host side, and there is a small amount of type duplication. These are normal for an in-progress feature.

| Severity | Count |
|----------|-------|
| Critical | 0 |
| High | 0 |
| Medium | 2 |
| Low | 3 |

---

## 1. Complexity Assessment

**Project Scale**: Pre-alpha, scaffolding phase, 2 developers, Product/Category CRUD as baseline
**Plugin System Scale**: ~583 LOC backend (13 Java files), ~350 LOC frontend (4 host files + 6 SDK files), 1 demo plugin

### Verdict: Complexity is proportional to the problem

The plugin system needs to solve a genuinely hard problem -- safe iframe isolation, cross-origin messaging with request/response correlation, and generic data storage for arbitrary plugin data. The implementation uses the minimum number of components needed:

- **Backend**: 2 entities, 2 repositories, 3 services, 3 controllers, 2 records (response DTOs), 1 request record. Standard Spring Boot layering -- no extra abstraction.
- **Frontend host**: 4 files -- context provider, iframe component, iframe registry, message handler. Each has a single clear responsibility.
- **Plugin SDK**: 6 files -- types, context parsing, messaging core, two facades (hostApp/thisPlugin), barrel export. Clean and minimal.
- **Demo plugin**: 3 pages, plain React, no extra libraries beyond react-router-dom.

No factories, no strategy patterns, no abstract base classes, no dependency injection frameworks beyond Spring's built-in. The code reads top-to-bottom without indirection.

---

## 2. Over-Engineering Patterns

### None detected at Critical or High severity

The implementation avoids the common pre-alpha traps:
- No Redis, Kafka, or message queues -- uses direct DB queries
- No custom plugin classloader or OSGi -- uses simple iframes
- No schema validation framework for plugin objects -- schemaless JSONB (explicitly a non-goal per design doc)
- No authentication/authorization layer -- deferred per design doc
- No plugin marketplace, versioning system, or dependency resolution
- No multi-tenant partitioning
- Database migrations are simple CREATE TABLE statements with appropriate indexes

---

## 3. Key Issues Found

### Medium Severity

#### M1. SDK host-side message handlers are not wired (incomplete bridge)

**Evidence**: `src/main/frontend/src/plugins/PluginMessageHandler.ts:137-139`
```
// Future handlers: getProducts, getProduct, getPlugins, getData, setData, removeData,
// objectsList, objectsGet, objectsSave, objectsDelete
// These will be implemented as the plugin SDK matures.
```

**Problem**: The plugin SDK (`this-plugin.ts`, `host-app.ts`) exposes methods like `thisPlugin.getData()`, `thisPlugin.objects.list()`, `hostApp.getProducts()`, etc. These send postMessage requests to the host. But the host's `PluginMessageHandler` only handles `pluginFetch` and `filterChange`. All other message types return "Unknown message type" errors.

This means the demo plugin's `WarehousePage` (which calls `sdk.thisPlugin.objects.list("warehouse-item")`) and `ProductStockTab` (which calls `sdk.thisPlugin.getData(productId)`) will silently fail at runtime. The demo plugin has defensive `catch` blocks that swallow these errors, masking the gap.

**Impact**: The demo plugin does not actually work end-to-end. The SDK advertises capabilities the host cannot fulfill.

**Recommendation**: Either wire the remaining handlers in `PluginMessageHandler.ts` (each is a simple `fetch()` to the backend API), or remove the unimplemented SDK methods and add them when the host-side handlers exist. The `pluginFetch` handler already works as a generic escape hatch -- plugins can use `hostApp.fetch("/api/plugins/warehouse/objects/warehouse-item")` directly in the interim.

---

#### M2. Type declarations duplicated between SDK and demo plugin

**Evidence**:
- `src/main/frontend/src/plugin-sdk/types.ts` -- defines `PluginContext`, `PluginObject`
- `plugins/warehouse/src/sdk.ts` -- re-declares identical interfaces (`PluginContext`, `PluginObject`, `PluginSDKType`)

**Problem**: The warehouse plugin manually duplicates all SDK type definitions. If the SDK API changes, the plugin types will drift out of sync.

**Impact**: Low-medium. Acceptable for a single demo plugin, but will become a maintenance issue with multiple plugins.

**Recommendation**: For now, this is fine -- the design doc explicitly says "copy `types.ts` or import from a shared path" and defers npm packaging. Just be aware of drift risk. When a second plugin is added, extract a shared `@aj/plugin-sdk-types` package or use a symlinked `types.ts`.

---

### Low Severity

#### L1. `PluginDataService` is coupled to `Product` entity

**Evidence**: `src/main/java/pl/devstyle/aj/core/plugin/PluginDataService.java:6` -- imports `ProductRepository`

**Problem**: The plugin data service lives in `core.plugin` but depends on `product.ProductRepository`. This creates a circular dependency direction (core depends on a domain module). If other entities need plugin data in the future, this pattern would need to be rethought.

**Impact**: Minimal for now -- there is only one entity type with plugin data. This is the pragmatic choice for a pre-alpha.

**Recommendation**: No action needed now. If a second entity type needs plugin data, consider moving the JSONB read/write logic closer to the entity that owns it, or introduce a generic approach.

---

#### L2. `PluginDescriptor` uses `id`-based `equals`/`hashCode` instead of business key

**Evidence**: `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptor.java:62-71`

**Problem**: The project's own modeling standard (`standards/backend/models.md`) specifies "business key equals/hashCode (never entity id)". `PluginDescriptor` uses `id` for both. However, since `id` is a user-provided String (the plugin slug, e.g. "warehouse") and serves as the natural business key, this is debatable -- it IS the business key, it just happens to also be the PK.

**Impact**: Negligible. The `id` is stable and user-defined, not auto-generated.

**Recommendation**: No action needed. Document that for `PluginDescriptor`, the PK is intentionally the business key.

---

#### L3. `PluginFrame` effect dependency array may cause stale closures

**Evidence**: `src/main/frontend/src/plugins/PluginFrame.tsx:57` -- the `useEffect` depends on all props but uses `initializedRef` to skip re-runs.

**Problem**: The `initializedRef` guard means the effect runs once and never re-runs even if props change. This is intentional (iframes should not be recreated on prop changes), but the dependency array suggests it should re-run. A developer reading this code might be confused.

**Impact**: Cosmetic. The behavior is correct -- iframes should be stable.

**Recommendation**: Add a brief comment explaining the intentional skip, or use `// eslint-disable-next-line react-hooks/exhaustive-deps` with an empty dependency array to make the "run once" intent explicit.

---

## 4. Developer Experience Assessment

**Overall DX**: Good

**Strengths**:
- Clear file organization -- plugin backend is one flat package, frontend has two clear directories (host infrastructure vs SDK)
- Standard Spring Boot patterns -- any Spring developer can read this immediately
- Demo plugin is a standalone Vite app with zero special tooling
- Design document (`plugin-system-design.md`) is thorough and explains every decision with DevSkiller comparisons
- Tests exist for all backend endpoints and frontend components

**Minor friction points**:
- No script or Makefile to register the demo plugin (developer must manually `curl PUT /api/plugins/warehouse/manifest` with the manifest JSON)
- The SDK is not actually built/served as a static asset yet -- the demo plugin declares types locally but there is no Vite library build config in the host frontend
- The warehouse plugin has `node_modules/` committed or present in the glob results -- should be gitignored

---

## 5. Requirements Alignment

**Design doc**: `.maister/docs/project/plugin-system-design.md`

| Requirement | Status | Notes |
|-------------|--------|-------|
| Iframe isolation | Implemented | Sandbox attributes, origin validation |
| PostMessage RPC | Partially | Only `pluginFetch` and `filterChange` handlers wired |
| REST API for plugins | Implemented | Registry, data, objects -- all CRUD |
| Product plugin data (JSONB) | Implemented | Read/write/delete via API |
| Custom objects | Implemented | Full CRUD via API |
| Manifest-based registration | Implemented | Upsert via PUT |
| Extension points (menu, tabs, filters) | Implemented | Frontend resolves and renders |
| No auth | Correct | Explicitly excluded per design |
| No plugin-to-plugin comms | Correct | Not implemented, as specified |

**Requirement inflation**: None detected. The implementation matches the spec without gold-plating.

---

## 6. Context Consistency

**Contradictory patterns**: None detected.

**Unused code analysis**:
- `hostApp.getProducts()`, `hostApp.getProduct()`, `hostApp.getPlugins()` in `host-app.ts` -- declared but the host message handler does not route these message types. The SDK promises functionality the host cannot deliver. (Covered in M1 above.)
- `getPendingCount()` in `messaging.ts:79` -- exported but only used in testing. Acceptable.
- `getProductListFilters` in `PluginContext.tsx` -- declared, used in `ProductListPage.tsx`, renders filter iframes correctly. The filter iframes send `filterChange` messages which are handled. Working correctly.

**Pattern consistency**: The backend follows the same Controller-Service-Repository pattern as the existing Product and Category modules. The plugin module does not introduce any new patterns. Good.

---

## 7. Recommended Simplifications

### Priority 1: Wire remaining SDK message handlers or remove dead SDK methods

**Current state**: SDK declares 10+ message types, host handles 2.
**Recommended action**: Add handlers for the 4-5 most useful types (`getData`, `setData`, `objectsList`, `objectsSave`, `objectsDelete`) in `PluginMessageHandler.ts`. Each handler is ~5 lines (fetch to backend, return result).
**Impact**: Makes the demo plugin actually work. Estimated effort: 1-2 hours.

### Priority 2: Add a plugin registration script

**Current state**: Developer must manually construct a curl command to register the demo plugin.
**Recommended action**: Add a `register.sh` or npm script in `plugins/warehouse/` that reads `manifest.json` and POSTs it.
**Impact**: Reduces onboarding friction. Estimated effort: 15 minutes.

### Priority 3: Add SDK build configuration

**Current state**: The design doc specifies Vite library mode for building `plugin-sdk.js`, but no build config exists. The demo plugin uses local type declarations instead.
**Recommended action**: Add a Vite library build config for `src/plugin-sdk/index.ts` that outputs an IIFE bundle. Serve it from the host's static assets.
**Impact**: Enables plugins to load the SDK via `<script>` tag as designed. Estimated effort: 1 hour.

---

## 8. Summary Statistics

| Metric | Current | Notes |
|--------|---------|-------|
| Backend plugin LOC | 583 | 13 files, straightforward CRUD |
| Frontend host LOC | ~200 | 4 files |
| Plugin SDK LOC | ~150 | 6 files |
| Demo plugin LOC | ~150 | 3 pages + types |
| DB migrations | 3 | plugins table, plugin_data column, plugin_objects table |
| Abstraction layers | 3 | Controller -> Service -> Repository (standard Spring) |
| External dependencies added | 0 | Uses only existing Spring Boot + React stack |
| Enterprise patterns used | 0 | No factories, strategies, observers, etc. |

---

## 9. Conclusion

This plugin system is **well-scoped for a pre-alpha**. It implements a proven architecture pattern (iframe + postMessage, based on DevSkiller's production system) with minimal code and no unnecessary abstractions. The backend is clean CRUD, the frontend integration is lightweight, and the design document clearly separates current scope from future work.

The only substantive issue is the gap between what the SDK promises and what the host-side message handler delivers (M1). This should be addressed before demonstrating the system to stakeholders, as the demo plugin currently fails silently.

**Action items**:
1. Wire remaining message handlers in `PluginMessageHandler.ts` (~2 hours)
2. Add plugin registration script (~15 min)
3. Add SDK Vite build config (~1 hour)

No simplification is needed -- the code is already simple.
