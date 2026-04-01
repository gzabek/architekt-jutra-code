# Specification: Custom Object Entity Binding

## Goal
Add entity binding (entity_type + entity_id) to PluginObject so plugins can associate custom objects with main entities (Product, Category) and query them server-side, replacing the current client-side fetch-all-then-filter pattern.

## User Stories
- As a plugin developer, I want to bind custom objects to specific entities so that I can query "all stock objects for product X" without fetching everything and filtering client-side.
- As a plugin developer, I want to filter custom objects by JSONB data values so that I can query "stock entries where quantity > 0" at the database level.
- As a plugin developer, I want to list all custom objects for an entity regardless of object type so that I can see all plugin data associated with an entity in one call.

## Core Requirements

1. **EntityType enum**: Create a Java enum `EntityType` with values `PRODUCT` and `CATEGORY`, mapped with `@Enumerated(EnumType.STRING)`.
2. **Entity binding columns on PluginObject**: Add nullable `entityType` (EntityType enum) and `entityId` (Long) fields to PluginObject entity.
3. **Liquibase migration 007**: Add `entity_type VARCHAR(50)` and `entity_id BIGINT` nullable columns to `plugin_objects` table, plus composite index on `(plugin_id, object_type, entity_type, entity_id)`.
4. **PluginObjectResponse expansion**: Add `entityType` and `entityId` fields to the response record.
5. **Save with entity binding**: Extend `PUT /api/plugins/{pluginId}/objects/{objectType}/{objectId}` with optional query params `entityType` and `entityId`. When provided, set entity binding on the object. When absent, **clear to null** (explicit intent model — omitting params always resets binding). This means updates must re-pass entity params to preserve binding.
6. **List by object type with entity filter**: Extend `GET /api/plugins/{pluginId}/objects/{objectType}` with optional query params `entityType`, `entityId`, and `filter` for JSONB data filtering.
7. **Cross-type list by entity**: New endpoint `GET /api/plugins/{pluginId}/objects` with required `entityType` and `entityId` query params, optional `filter` param. Returns all objects for that entity regardless of objectType. When `entityType` or `entityId` is missing, return 400 Bad Request with clear error message.
8. **JSONB data filtering**: Support colon-separated filter syntax on PluginObject.data (`filter=fieldPath:operator:value`). Operators: eq, gt, lt, exists, bool. Reuse the parsing pattern from PluginDataSpecification.
9. **Plugin SDK `objects.list` update**: Accept optional options object `{ entityType?, entityId?, filter? }` as second parameter. Pass through as query params.
9b. **Plugin SDK `objects.listByEntity` method**: New method `objects.listByEntity(entityType, entityId, options?)` where options is `{ filter? }`. Calls the cross-type endpoint `GET /api/plugins/{pluginId}/objects?entityType=X&entityId=Y`. This provides SDK access to cross-type queries (audit finding H2).
10. **Plugin SDK `objects.save` update**: Accept optional options object `{ entityType?, entityId? }` as fourth parameter. Pass through as query params.
11. **Host message handler update**: Update `objectsList` and `objectsSave` handlers in PluginMessageHandler.ts to forward entity binding and filter params as query string parameters to the API.
12. **Warehouse plugin SDK types update**: Update `sdk.ts` type declarations to reflect new signatures and add `entityType`/`entityId` to PluginObject interface.
13. **Plugin SDK types update**: Add `entityType?` and `entityId?` to the `PluginObject` interface in `plugin-sdk/types.ts`.
14. **Plugin SDK IIFE bundle update**: Update `plugin-sdk.js` to match the source module changes (manual update matching current project pattern).

## Reusable Components

### Existing Code to Leverage

| Component | File Path | What It Provides |
|-----------|-----------|-----------------|
| PluginObject entity | `src/main/java/pl/devstyle/aj/core/plugin/PluginObject.java` | Base entity to extend with entityType/entityId fields |
| PluginObjectRepository | `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectRepository.java` | Repository pattern to add new query methods |
| PluginObjectService | `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectService.java` | Service layer with plugin validation pattern to extend |
| PluginObjectController | `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectController.java` | Controller with existing endpoint patterns to extend |
| PluginObjectResponse | `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectResponse.java` | Response record to expand with new fields |
| PluginDataSpecification | `src/main/java/pl/devstyle/aj/product/PluginDataSpecification.java` | Colon-separated filter parsing pattern and JSONB operator logic to adapt for PluginObject.data |
| BaseEntity | `src/main/java/pl/devstyle/aj/core/BaseEntity.java` | id, createdAt, @Version updatedAt pattern |
| Migration 006 | `src/main/resources/db/changelog/2026/006-create-plugin-objects-table.yaml` | Liquibase migration pattern (addColumn, createIndex) |
| this-plugin.ts | `src/main/frontend/src/plugin-sdk/this-plugin.ts` | SDK source to extend objects.list and objects.save signatures |
| PluginMessageHandler.ts | `src/main/frontend/src/plugins/PluginMessageHandler.ts` | Host-side handler to update objectsList/objectsSave cases |
| plugin-sdk/types.ts | `src/main/frontend/src/plugin-sdk/types.ts` | PluginObject interface to expand |
| warehouse sdk.ts | `plugins/warehouse/src/sdk.ts` | SDK type declarations to update |
| Integration tests | `src/test/java/pl/devstyle/aj/core/plugin/PluginDataAndObjectsIntegrationTests.java` | Test patterns (createAndSavePlugin, MockMvc, jsonPath assertions) |
| plugin-sdk.js | `src/main/resources/static/assets/plugin-sdk.js` | IIFE bundle to update manually |

### New Components Required

| Component | Why New Code Is Needed |
|-----------|----------------------|
| EntityType enum | No entity type enum exists in the codebase. This is the first `@Enumerated` field. |
| PluginObject JSONB filter infrastructure | PluginDataSpecification is tightly coupled to Product entity (uses `plugin_data` column with pluginId namespace). PluginObject.data has a different structure (flat JSONB, no plugin namespace) and uses a different table. Need a new Specification or query builder for `plugin_objects.data` column. The parsing/validation logic from PluginDataSpecification should be adapted (not duplicated), but the SQL generation differs. |

## Technical Approach

### Entity Binding
- Add `entityType` and `entityId` as nullable fields on PluginObject. Keep existing business key equality on `(pluginId, objectType, objectId)` unchanged per clarification decisions.
- Entity binding is metadata, not identity. The unique constraint remains `(pluginId, objectType, objectId)`.
- No FK constraint to products/categories. The `entity_id` is a BIGINT with no referential integrity (by design -- polymorphic binding via enum type).

### Query Strategy
- Repository-level: Add Spring Data JPA query methods for entity-filtered lookups. For JSONB filtering, use JPA Specification or `@Query` with native SQL, following the pattern in PluginDataSpecification.
- Service-level: Extend `list()` to accept optional entity binding params and filter string. When params are absent, delegate to existing `findByPluginIdAndObjectType`.
- Controller-level: Add `@RequestParam(required = false)` for entityType, entityId, and filter on existing list endpoint. Add new list-all endpoint at `GET /api/plugins/{pluginId}/objects`.

### JSONB Filtering
- Adapt the colon-separated parsing logic from PluginDataSpecification for PluginObject.data.
- Key difference: PluginDataSpecification parses `pluginId:jsonPath:operator:value` (4 segments) because product pluginData is namespaced. PluginObject.data is not namespaced, so format is `jsonPath:operator:value` (3 segments).
- SQL targets `data->>'jsonPath'` instead of `plugin_data->'pluginId'->>'jsonPath'`.
- Same operator set: eq, gt, lt, exists, bool.

### SDK Changes
- `objects.list(type, options?)` -- options is `{ entityType?, entityId?, filter? }`. When provided, pass as query params.
- `objects.listByEntity(entityType, entityId, options?)` -- options is `{ filter? }`. Calls cross-type endpoint.
- `objects.save(type, id, data, options?)` -- options is `{ entityType?, entityId? }`. When provided, pass as query params. When absent, entity binding is cleared to null.
- PluginMessageHandler builds URL query string from payload params before calling api.get/api.put.
- All changes are backward compatible -- existing calls without options continue to work.

### Data Flow (After)
1. Plugin saves stock with entity binding: `objects.save("stock", "{productId}-{warehouseId}", data, { entityType: "PRODUCT", entityId: productId })`
2. SDK sends `objectsSave` message with `entityType` and `entityId` in payload
3. PluginMessageHandler appends `?entityType=PRODUCT&entityId=123` to PUT URL
4. Controller receives query params, passes to service, service sets fields on entity
5. Query: `objects.list("stock", { entityType: "PRODUCT", entityId: productId })` fetches only relevant objects

## Implementation Guidance

### Testing Approach
- 2-8 focused tests per implementation step group
- Test verification runs only new tests, not entire suite
- Key test scenarios:
  - Save object with entity binding, verify response includes entityType/entityId
  - List objects filtered by entity, verify only matching objects returned
  - Entity isolation: objects bound to product A not returned when querying product B
  - List without entity filter returns all objects (backward compatibility)
  - Save without entity binding still works (backward compatibility)
  - Cross-type list: objects of different types for same entity returned together
  - JSONB data filter: filter by numeric comparison (quantity:gt:0)
  - Combined filter: entity binding + JSONB data filter together

### Standards Compliance
- **JPA Entity Modeling** (`standards/backend/models.md`): @Enumerated(EnumType.STRING), LAZY fetch default, business key equals/hashCode, Lombok annotations
- **Database Migrations** (`standards/backend/migrations.md`): Reversible migration, small focused change, zero-downtime (nullable columns only)
- **API Design** (`standards/backend/api.md`): RESTful query params for filtering, consistent naming
- **Backend Testing** (`standards/testing/backend-testing.md`): Integration-first, MockMvc + jsonPath, action_condition_expectedResult naming, 2-8 tests per feature
- **Minimal Implementation** (`standards/global/minimal-implementation.md`): No speculative abstractions, clear purpose for every method

## Out of Scope
- Entity existence validation (no FK to products/categories table)
- Cascading deletes (orphan cleanup when entity is deleted)
- Frontend warehouse plugin code changes (ProductStockTab.tsx) -- it already works with setData for availability
- New PluginDataService functionality
- Changing the unique constraint on plugin_objects
- Build pipeline for plugin-sdk.js (manual update matches current pattern)

## Success Criteria
- Existing plugin object CRUD continues to work without any parameter changes (backward compatible)
- Objects saved with entity binding can be queried by entity type + entity ID
- Cross-type entity query returns all object types for a given entity
- JSONB data filtering works with eq, gt, lt, exists, bool operators
- SDK `objects.list` and `objects.save` accept optional entity binding parameters
- All new integration tests pass
- Response includes entityType and entityId fields (null when not set)
- Save without entity params clears existing binding (explicit intent model)
- Cross-type endpoint returns 400 when entityType or entityId missing
- SDK `objects.listByEntity()` method provides access to cross-type queries
