# Requirements — Phase 5

## Initial Description
Modify custom objects (PluginObject) to support entity binding, query by entity ID, and add plugin_data to Product for warehouse availability.

## Q&A Summary

### Phase 1 Clarifications
1. **Entity binding type**: ENUM for entity_type + BIGINT for entity_id (generic binding)
2. **Query API**: Extend existing GET endpoint with query params for entity binding + JSONB data filtering
3. **Plugin data on Product**: Use existing PluginDataService — no backend changes needed

### Phase 2 Scope Decisions
1. **Save mechanism**: Query params on existing PUT endpoint (non-breaking)
2. **Filter syntax**: Reuse colon-separated pattern from PluginDataSpecification (e.g., `filter=quantity:gt:0`)
3. **Unique constraint**: Keep existing `(pluginId, objectType, objectId)` unchanged
4. **Plugin SDK**: Include SDK type updates in this task

### Phase 5 Requirements
1. **EntityType enum values**: PRODUCT and CATEGORY initially
2. **Cross-type query**: YES — allow listing all plugin objects for an entity regardless of objectType via `GET /api/plugins/{pluginId}/objects?entityType=PRODUCT&entityId=123`
3. **SDK save signature**: Options object — `thisPlugin.objects.save('stock', id, data, { entityType: 'PRODUCT', entityId: 123 })`

## Functional Requirements

### FR1: Entity Binding on PluginObject
- Add `entity_type` column (VARCHAR mapped to Java enum EntityType with @Enumerated(EnumType.STRING))
- Add `entity_id` column (BIGINT, nullable)
- Both columns nullable (entity binding is optional)
- EntityType enum: PRODUCT, CATEGORY (extensible)
- Liquibase migration to add columns + index on (plugin_id, entity_type, entity_id)

### FR2: Query by Entity Binding
- Extend GET /api/plugins/{pluginId}/objects/{objectType} with optional query params: entityType, entityId
- New endpoint: GET /api/plugins/{pluginId}/objects?entityType=PRODUCT&entityId=123 (cross-type)
- Both support optional `filter` param for JSONB data filtering using colon syntax

### FR3: JSONB Data Filtering
- Reuse PluginDataSpecification colon-separated pattern
- Format: `filter=fieldPath:operator:value`
- Operators: eq, gt, lt, exists, bool (same as existing)
- Applied to PluginObject.data JSONB column

### FR4: Save with Entity Binding
- Extend PUT /api/plugins/{pluginId}/objects/{objectType}/{objectId} with optional query params: entityType, entityId
- When provided, set entity binding on the PluginObject
- When not provided, leave entity binding null (backward compatible)

### FR5: Plugin SDK Updates
- Update SDK type declarations to support entityType/entityId in save options
- Update host-side message handler to pass entity params through to backend
- Save signature: `objects.save(type, id, data, options?)` where options = `{ entityType?, entityId? }`
- List signature: `objects.list(type, options?)` where options = `{ entityType?, entityId?, filter? }`

### FR6: Warehouse Plugin Usage (No Backend Changes)
- Warehouse plugin uses existing `thisPlugin.setData(productId, { available: true/false })` for product-level availability
- This works via existing PluginDataService — no new code needed
- Document this as the recommended pattern for entity-level plugin data

## Scope Boundaries

### In Scope
- PluginObject entity modification (entity_type, entity_id)
- EntityType enum
- Liquibase migration
- Repository query methods
- Service layer extensions
- Controller endpoint modifications
- Plugin SDK type updates (sdk.ts, host message handler)
- Integration tests

### Out of Scope
- Entity existence validation (no FK constraint to products/categories — string enum + bigint only)
- Cascading deletes (if product deleted, plugin objects remain — orphan cleanup is separate concern)
- Frontend warehouse plugin code changes (it already uses setData for availability)
- New PluginDataService functionality

## Reusability Opportunities
- PluginDataSpecification filter pattern reused for PluginObject JSONB filtering
- Existing controller/service/repository layering pattern
- BaseEntity inheritance
