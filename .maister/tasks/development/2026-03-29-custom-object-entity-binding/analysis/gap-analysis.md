# Gap Analysis: Custom Object Entity Binding

## Summary
- **Risk Level**: Low-Medium
- **Estimated Effort**: Medium
- **Detected Characteristics**: modifies_existing_code, creates_new_entities, involves_data_operations

## Task Characteristics
- Has reproducible defect: no
- Modifies existing code: yes
- Creates new entities: yes (new enum, new migration, new query infrastructure)
- Involves data operations: yes (entity binding is a data relationship pattern)
- UI heavy: no (frontend changes are minimal SDK/type updates, not new UI)

---

## Gaps Identified

### Missing Features

1. **Entity type enum**: No Java enum exists for entity types. The codebase has zero `@Enumerated` annotations anywhere -- this will be the first enum in the project. Need to create an `EntityType` enum (e.g., with `PRODUCT` as initial value). Per project standards: `@Enumerated(EnumType.STRING)` required.

2. **Entity binding columns on plugin_objects**: `entity_type` and `entity_id` columns do not exist. New Liquibase migration (007) needed to add nullable `entity_type VARCHAR` + `entity_id BIGINT` columns plus composite index.

3. **Entity-filtered list query**: `PluginObjectRepository` has `findByPluginIdAndObjectType` but no method to filter by entity binding. Need a new query method or JPA Specification for filtering by `entityType` + `entityId`.

4. **JSONB data value filtering on plugin_objects**: The clarification specifies query params like `quantity>0` to filter by JSONB data values. `PluginDataSpecification` exists for Product's pluginData but nothing equivalent exists for filtering `plugin_objects.data` JSONB. This is new query infrastructure.

5. **SDK `objects.list` entity params**: The plugin SDK `objects.list(type)` signature accepts only `objectType`. It needs to support optional filter parameters (`entityType`, `entityId`, and JSONB data filters). This affects 4 layers:
   - `plugin-sdk/this-plugin.ts` (source module) -- `list()` sends only `{objectType}` in payload
   - `plugin-sdk.js` (built/minified bundle) -- same, `objectsList` message only carries `objectType`
   - `PluginMessageHandler.ts` (host-side) -- `objectsList` handler calls `api.get()` with no query params
   - `plugins/warehouse/src/sdk.ts` (type declarations) -- `list(type: string)` signature

### Incomplete Features

1. **PluginObjectResponse**: Currently maps `pluginId, objectType, objectId, data, createdAt, updatedAt`. Missing `entityType` and `entityId` fields that need to be added to the response record.

2. **Save endpoint entity binding**: `PUT /api/plugins/{pluginId}/objects/{objectType}/{objectId}` currently accepts only `data` (Map) in request body. No mechanism to set `entityType`/`entityId` on save. The save flow needs to accept these either as query params, path segments, or request body fields.

### Behavioral Changes Needed

1. **List endpoint filtering**: `GET /api/plugins/{pluginId}/objects/{objectType}` currently returns ALL objects of that type. Must support optional query params for entity binding and JSONB filtering while remaining backward-compatible (no params = existing behavior).

2. **ProductStockTab client-side filtering**: Currently fetches all stock objects and filters with `.filter(e => e.productId === Number(productId))`. After entity binding, this should use server-side filtering via the SDK's updated `objects.list()`.

---

## Existing Feature Analysis

### Change Type: Additive

All new columns are nullable. Existing API behavior preserved when no filter params provided. Existing SDK calls without entity params continue working. No data migration needed.

### Compatibility Requirements: Strict

The plugin SDK is a public contract consumed by all plugins. Changes must be backward-compatible:
- `objects.list("stock")` without params must still return all objects of that type
- `objects.save("stock", "id", data)` without entity params must still work
- Response shape expansion (adding `entityType`/`entityId` fields) is backward-compatible

### User Journey Impact

| Dimension | Current | After | Assessment |
|-----------|---------|-------|------------|
| Reachability | ProductStockTab loads all stock, filters client-side | Same tab, server-side filtered | no change in UX path |
| Discoverability | N/A (backend/API feature) | N/A | N/A |
| Flow Integration | Fetch-all-filter-client pattern works but is slow | Server-side filter, same UX | positive (performance) |

---

## Data Lifecycle Analysis

### Entity: PluginObject (with entity binding)

| Operation | Backend | UI Component | User Access | Status |
|-----------|---------|--------------|-------------|--------|
| CREATE with entity binding | MISSING -- save() has no entityType/entityId params | MISSING -- SDK save() has no entity params | Plugin calls SDK save | needs implementation |
| READ by entity binding | MISSING -- no query method | MISSING -- SDK list() has no entity filter | Plugin calls SDK list | needs implementation |
| READ by JSONB data values | MISSING -- no specification/query | MISSING -- SDK list() has no data filter | Plugin calls SDK list | needs implementation |
| UPDATE | EXISTS -- upsert via save() | EXISTS -- SDK save() | Plugin calls SDK save | will work once CREATE works |
| DELETE | EXISTS -- delete by natural key | EXISTS -- SDK delete() | Plugin calls SDK delete | existing, unchanged |

**Completeness**: 40% -- CREATE and READ with entity binding are the core task; both are fully missing across all layers.

**Orphaned Operations**: None post-implementation (CREATE and READ are being added together).

### Entity: Product pluginData (warehouse availability)

| Operation | Backend | UI Component | User Access | Status |
|-----------|---------|--------------|-------------|--------|
| CREATE (setData) | EXISTS -- PluginDataService.setData() | EXISTS -- SDK thisPlugin.setData() | Plugin calls SDK | existing |
| READ (getData) | EXISTS -- PluginDataService.getData() | EXISTS -- SDK thisPlugin.getData() | Plugin calls SDK | existing |
| READ (filter products) | EXISTS -- PluginDataSpecification with bool operator | EXISTS -- product list filter extension point | Filter UI in host | existing |

**Completeness**: 100% -- No backend changes needed. Warehouse plugin just needs to call `thisPlugin.setData(productId, { available: true })` at the right time (frontend usage pattern only).

---

## Full Stack Change Map

The entity binding feature requires synchronized changes across 4 layers. Here is the complete chain:

### Layer 1: Database
- **New migration 007**: Add `entity_type VARCHAR(50)` + `entity_id BIGINT` nullable columns to `plugin_objects`
- **New index**: Composite index on `(plugin_id, object_type, entity_type, entity_id)` for the primary query pattern
- **Decision point**: Whether `entity_type` should be a PostgreSQL enum type or VARCHAR with EnumType.STRING

### Layer 2: Backend (Java/Spring)
- **PluginObject.java**: Add `entityType` (enum) + `entityId` (Long) fields
- **New enum**: `EntityType` enum (initially with `PRODUCT`)
- **PluginObjectRepository.java**: Add query method(s) for entity-filtered listing
- **New**: Query infrastructure for JSONB data filtering on plugin_objects (Specification or native query)
- **PluginObjectService.java**: Extend `list()` to accept optional entity + data filters; extend `save()` to accept entity binding
- **PluginObjectController.java**: Add query params to list endpoint; accept entity binding on save
- **PluginObjectResponse.java**: Add `entityType` and `entityId` fields

### Layer 3: Host Frontend (message handler)
- **PluginMessageHandler.ts**: Update `objectsList` handler to pass query params from payload to API call
- **PluginMessageHandler.ts**: Update `objectsSave` handler to pass entity binding params

### Layer 4: Plugin SDK + Plugins
- **plugin-sdk/this-plugin.ts**: Update `objects.list()` to accept optional filter params
- **plugin-sdk/this-plugin.ts**: Update `objects.save()` to accept optional entity binding
- **plugin-sdk.js**: Rebuild minified bundle (or update manually)
- **plugins/warehouse/src/sdk.ts**: Update type declarations
- **plugins/warehouse/src/pages/ProductStockTab.tsx**: Use entity-filtered query instead of client-side filter

---

## Issues Requiring Decisions

### Critical (Must Decide Before Proceeding)

1. **Save endpoint entity binding mechanism**
   - **Issue**: How should entity binding be specified when saving a PluginObject? The save endpoint is `PUT /api/plugins/{pluginId}/objects/{objectType}/{objectId}` with just `data` in the body.
   - **Options**:
     - (A) **Query params on save**: `PUT .../{objectId}?entityType=PRODUCT&entityId=123` with data in body
     - (B) **Wrapper request body**: `{ "entityType": "PRODUCT", "entityId": 123, "data": {...} }` (changes body shape -- breaking change for existing plugins that send raw data)
     - (C) **Separate endpoint for entity-bound saves**: New path segment like `PUT .../{objectId}/bind?entityType=PRODUCT&entityId=123`
   - **Recommendation**: (A) Query params. Backward-compatible because missing params means no binding. Consistent with the list endpoint using query params for filtering.
   - **Rationale**: Option B is a breaking change (existing plugins send `Map<String, Object>` directly as body). Option C adds unnecessary API surface. Query params keep the body contract unchanged.

2. **JSONB data filtering syntax on plugin_objects**
   - **Issue**: The clarification mentions `quantity>0` as a query param for filtering by JSONB data values. What syntax should the API use for this?
   - **Options**:
     - (A) **Reuse PluginDataSpecification pattern**: `dataFilter=quantity:gt:0` (colon-separated, matching existing `pluginFilter` format)
     - (B) **Custom operator syntax**: `data.quantity>0` or `quantity=gt:0` as query params
     - (C) **Simple key-value with operator prefix**: `data[quantity]=gt:0`
   - **Recommendation**: (A) Reuse the existing `pluginId:jsonPath:operator:value` pattern from PluginDataSpecification, adapted for plugin_objects where pluginId is already in the path: `dataFilter=quantity:gt:0`
   - **Rationale**: Consistent with existing PluginDataSpecification. Developers learn one filter syntax for both Product pluginData and PluginObject data. Parsing logic can be shared.

### Important (Should Decide)

1. **Entity binding and unique constraint interaction**
   - **Issue**: Current unique constraint is on `(pluginId, objectType, objectId)`. If entity binding is added, should the same `objectId` be reusable across different entities? E.g., can plugin "warehouse" have two "stock" objects with objectId "item-1" -- one bound to Product 1 and one to Product 2?
   - **Options**:
     - (A) **Keep existing constraint**: `(pluginId, objectType, objectId)` remains unique. Entity binding is metadata on existing objects. ObjectId must be globally unique within plugin+type.
     - (B) **Expand constraint**: Change unique constraint to `(pluginId, objectType, objectId, entityType, entityId)` allowing the same objectId for different entities.
   - **Default**: (A) Keep existing constraint
   - **Rationale**: The warehouse plugin already uses composite objectIds like `{productId}-{warehouseId}` which embed entity identity. Changing the constraint would be a more invasive migration and could break existing data semantics. Entity binding adds queryability, not a new identity dimension.

2. **Entity binding on equals/hashCode**
   - **Issue**: PluginObject's equals/hashCode uses `(pluginId, objectType, objectId)`. Should `entityType`/`entityId` be part of entity identity?
   - **Options**:
     - (A) **Keep current identity**: Business key remains `(pluginId, objectType, objectId)`. Entity binding is supplemental metadata.
     - (B) **Expand identity**: Include entity binding in equals/hashCode.
   - **Default**: (A) Keep current identity
   - **Rationale**: Follows from unique constraint decision. If constraint stays on `(pluginId, objectType, objectId)`, identity should match.

3. **plugin-sdk.js build process**
   - **Issue**: `plugin-sdk.js` is a minified IIFE bundle in `src/main/resources/static/assets/`. The source modules are in `src/main/frontend/src/plugin-sdk/`. There is no visible build step that generates the minified bundle from the source modules. Changes need to be made in both places, or a build step needs to be established.
   - **Options**:
     - (A) **Update both manually**: Edit source modules AND the minified bundle
     - (B) **Create build step**: Add a build script that compiles plugin-sdk source to the IIFE bundle
   - **Default**: (A) Update both manually (matching current project pattern)
   - **Rationale**: Establishing a build step is out of scope for this task. Match existing project conventions.

---

## Recommendations

1. **Implementation order**: Database migration first, then entity + repository + service + controller (backend), then host message handler, then plugin SDK + types, then warehouse plugin usage. Each layer can be tested independently.

2. **JSONB filtering reuse**: Extract the operator parsing logic from `PluginDataSpecification` into a shared utility, then use it for both Product pluginData filtering and PluginObject data filtering. This prevents duplicating the parsing/validation logic.

3. **Index strategy**: Use composite index `(plugin_id, object_type, entity_type, entity_id)` which covers the most common query: "list all objects of type X for entity Y within plugin Z." The existing `idx_plugin_objects_plugin_type` index on `(plugin_id, object_type)` is a prefix of this, so consider whether it should be kept or dropped.

4. **Test coverage**: Add integration tests for: (a) save with entity binding, (b) list filtered by entity, (c) list filtered by JSONB data values, (d) backward compatibility -- list without filters still returns all objects, (e) save without entity binding still works.

---

## Risk Assessment
- **Complexity Risk**: Low -- additive changes, well-established patterns to follow
- **Integration Risk**: Medium -- changes span 4 layers (DB, backend, host frontend, plugin SDK) that must be synchronized
- **Regression Risk**: Low -- nullable columns, optional query params, backward-compatible SDK signatures
- **Performance Risk**: Positive -- moves from client-side fetch-all-filter to server-side indexed queries
