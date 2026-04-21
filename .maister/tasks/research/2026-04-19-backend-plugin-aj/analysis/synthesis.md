# Research Synthesis: Backend Plugin Development in aj

**Research Question**: How do you write a backend plugin in the aj application?
**Date**: 2026-04-19
**Confidence Level**: High — multiple independent sources confirm all major findings; integration tests verify runtime behavior.

---

## 1. Pattern Analysis

### 1.1 The Core Architectural Pattern

The fundamental insight is that aj's plugin system is **not a code extension system**. There is no Java SPI, no `@ComponentScan` of external JARs, no OSGi, no Spring plugin mechanism. Instead, plugins are:

- **Out-of-process services** (separate apps, separate repos, separate deployments)
- **Registered at runtime** via a single `PUT /api/plugins/{pluginId}/manifest` HTTP call
- **Communicating through REST** — the host exposes a stable REST API; plugins call it using forwarded JWTs
- **Isolated in storage** — each plugin's data is namespaced by `pluginId` in JSONB columns or in the `plugin_objects` table

This is a **micro-frontend / backend-for-frontend** architecture pattern, not a traditional plugin framework.

### 1.2 Two Distinct "Plugin" Concepts

Findings reveal two separate concepts that share the word "plugin":

| Concept | What it means | Where it lives |
|---------|--------------|----------------|
| **External Plugin** | A separate application (Next.js, etc.) that registers with aj | Outside the monolith |
| **New Domain in aj** | New Java packages added directly to the monolith | Inside `pl.devstyle.aj.<domain>/` |

The "backend plugin" question therefore has two valid answers:
1. Build a standalone server that calls aj's REST API (the intended pattern)
2. Add a new domain package to the monolith itself (for data that must live in the host DB)

Both patterns are well-documented in the codebase.

### 1.3 Storage Mechanism Duality

Two storage mechanisms exist with distinct use cases:

**Plugin Data** (`products.plugin_data` JSONB column):
- Key: `plugin_data->>'pluginId'`
- Bound to a specific Product entity
- Simple get/set semantics
- Best for: per-product configuration, computed metadata, enrichment data

**Plugin Objects** (`plugin_objects` table):
- Independent table with `(pluginId, objectType, objectId)` identity
- Optional `entityType/entityId` binding to Product or Category
- Rich JSONB filter support (6 operators: eq, gt, lt, exists, bool, plus path notation)
- Best for: plugin-owned domain objects, collections, anything with query needs

### 1.4 Security Model

Security is JWT-based pass-through. The host application issues JWTs. When a user loads a plugin iframe, the frontend SDK (`thisPlugin`) can retrieve the JWT via `sdk.hostApp.getToken()`. For server-side plugin routes, `server-sdk.ts` extracts this token from the request and forwards it to aj REST calls. Three relevant permissions are in play:
- `PERMISSION_PLUGIN_MANAGEMENT` — admin-only (register/delete plugins)
- `PERMISSION_READ` — read plugin objects and data
- `PERMISSION_EDIT` — write plugin objects and data

### 1.5 Extension Points as UI Contracts

Extension points are declared in `manifest.json` as an array. They define **where in the host UI** a plugin's iframe appears. They are not Java extension points — they are routing contracts. The host frontend reads the manifest and renders iframes at the declared slots.

---

## 2. Cross-Reference Map

```
manifest.json
    ↓ PUT /api/plugins/{pluginId}/manifest
PluginDescriptor (plugins table)
    ↓ findEnabledOrThrow() — security gateway
    ↙                    ↘
PluginObjectService         PluginDataService
(plugin_objects table)      (products.plugin_data JSONB)
    ↓                           ↓
/api/plugins/{id}/objects   /api/plugins/{id}/products/{pid}/data
    ↑                           ↑
Frontend SDK (thisPlugin.objects.*)  Frontend SDK (thisPlugin.getData/setData)
    ↑                           ↑
server-sdk.ts (Next.js API routes — forwards JWT)
```

The `findEnabledOrThrow` call is the critical security invariant: every data service method checks that the plugin is registered and enabled before any operation. This prevents orphaned data access.

---

## 3. Gaps and Uncertainties

### 3.1 Confirmed Gaps (Low Confidence Areas)

| Gap | Evidence | Impact |
|-----|----------|--------|
| No Category plugin data endpoint | `PluginDataService` only handles Products | Plugin data on categories requires Plugin Objects workaround |
| No plugin-to-plugin communication | No evidence of inter-plugin API | Plugins must each communicate with host directly |
| No versioning/migration for plugin manifests | Manifest is full upsert, no diff | Manifest changes are atomic replacements |
| `server-sdk.ts` path unclear | Referenced in docs, single usage in ai-description plugin | May be in `plugins/` directory, not compiled into aj |

### 3.2 Pre-Alpha Uncertainty

The project is explicitly pre-alpha. The plugin framework is complete but:
- No production plugins exist yet in the monolith
- The `warehouse` and `box-size` reference plugins are the only known complete examples
- The `server-sdk.ts` has limited usage — patterns may evolve

### 3.3 Plugin Object Filter Syntax

The JSONB filter syntax (`{jsonPath}:{op}:{value}`) is documented in tests (`PluginObjectGapTests`, `DbPluginObjectQueryService`) but no formal OpenAPI spec was found. Relying on test files as the authoritative reference for filter behavior.

---

## 4. Confidence Assessment Per Finding

| Finding | Source | Confidence | Notes |
|---------|--------|------------|-------|
| Plugin framework is REST-only, no Java SPI | Code + tests | **High** | Verified by `@SpringBootApplication` scan config |
| PluginDescriptor/Object/Data model | Code inspection | **High** | Schema matches Liquibase migrations |
| 4 extension point types | `plugins/CLAUDE.md` | **High** | Confirmed by reference plugin manifests |
| warehouse/box-size as templates | Docs + code | **High** | Both plugins exist with full implementations |
| server-sdk.ts for Next.js | Docs + one usage | **Medium** | Single usage; API may change |
| New domain package = Spring auto-discovery | Code | **High** | `@SpringBootApplication` scans full `pl.devstyle.aj` tree |
| JSONB filter operators | Tests + jOOQ service | **High** | 7 test classes verify behavior |
| Permission constants | Code | **High** | Used in controller security annotations |
