# Implementation Plan: Custom Object Entity Binding

## Overview
Total Steps: 28
Task Groups: 4
Expected Tests: 18-30

## Implementation Steps

### Task Group 1: Database & Entity Layer
**Dependencies:** None
**Estimated Steps:** 7

- [x] 1.0 Complete database and entity layer
  - [x] 1.1 Write 4-6 focused integration tests for entity binding persistence
    - Test: save object with entityType+entityId, verify response includes both fields
    - Test: save object without entity params, verify entityType/entityId are null (backward compat)
    - Test: save object with entity binding then save again without entity params, verify binding cleared to null (explicit intent model)
    - Test: list objects filtered by entityType+entityId returns only matching objects
    - Test: list objects without entity filter returns all objects (backward compat)
    - Test: entity isolation -- objects bound to product A not returned when querying product B
  - [x] 1.2 Create Liquibase migration 007-add-entity-binding-to-plugin-objects.yaml
    - Add `entity_type VARCHAR(50)` nullable column to `plugin_objects`
    - Add `entity_id BIGINT` nullable column to `plugin_objects`
    - Add composite index `idx_plugin_objects_entity_binding` on `(plugin_id, object_type, entity_type, entity_id)`
    - Rollback: dropIndex + dropColumn x2
    - File: `src/main/resources/db/changelog/2026/007-add-entity-binding-to-plugin-objects.yaml`
  - [x] 1.3 Create EntityType enum
    - Values: PRODUCT, CATEGORY
    - Package: `pl.devstyle.aj.core.plugin`
    - File: `src/main/java/pl/devstyle/aj/core/plugin/EntityType.java`
  - [x] 1.4 Add entity binding fields to PluginObject entity
    - Add `@Enumerated(EnumType.STRING) @Column(name = "entity_type", length = 50)` entityType field (nullable)
    - Add `@Column(name = "entity_id")` entityId field (Long, nullable)
    - Do NOT change equals/hashCode -- entity binding is metadata, not identity
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObject.java`
  - [x] 1.5 Expand PluginObjectResponse with entity fields
    - Add `EntityType entityType` and `Long entityId` to record fields
    - Update `from()` factory to map new fields
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectResponse.java`
  - [x] 1.6 Add repository query methods for entity binding
    - `findByPluginIdAndObjectTypeAndEntityTypeAndEntityId(pluginId, objectType, entityType, entityId)` -- returns List
    - `findByPluginIdAndEntityTypeAndEntityId(pluginId, entityType, entityId)` -- for cross-type queries
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectRepository.java`
  - [x] 1.7 Extend service save method with entity binding + extend list with entity filter
    - Update `save(pluginId, objectType, objectId, data)` to accept optional `EntityType entityType, Long entityId` params
    - Always set entityType/entityId on the object (null when not provided = clears binding)
    - Add `list(pluginId, objectType, entityType, entityId)` overload that delegates to appropriate repository method
    - Add `listByEntity(pluginId, entityType, entityId)` for cross-type queries
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectService.java`
  - [x] 1.n Ensure database & entity layer tests pass
    - Run ONLY the 4-6 tests written in 1.1
    - Do NOT run entire test suite

**Acceptance Criteria:**
- The 4-6 tests pass
- Migration applies cleanly (TestContainers)
- PluginObject can be saved with and without entity binding
- Entity-filtered list returns correct subset
- Save without entity params clears any existing binding
- Response includes entityType and entityId fields

---

### Task Group 2: API & JSONB Filter Layer
**Dependencies:** Group 1
**Estimated Steps:** 7

- [x] 2.0 Complete API and JSONB filter layer
  - [x] 2.1 Write 4-6 focused integration tests for API endpoints and JSONB filtering
    - Test: PUT with entityType+entityId query params, verify response
    - Test: GET /{objectType}?entityType=PRODUCT&entityId=123 returns filtered results
    - Test: GET /objects?entityType=PRODUCT&entityId=123 (cross-type) returns objects of all types for that entity
    - Test: GET /objects without entityType or entityId returns 400 Bad Request
    - Test: GET /{objectType}?filter=quantity:gt:0 returns only matching objects
    - Test: Combined filter -- entity binding + JSONB filter together
  - [x] 2.2 Create PluginObjectSpecification for JSONB data filtering
    - Adapt parsing pattern from PluginDataSpecification: 3-segment format `jsonPath:operator:value`
    - Operators: eq, gt, lt, exists, bool (same set)
    - SQL targets `data->>'jsonPath'` (flat JSONB, no plugin namespace)
    - Validate jsonPath with same `IDENTIFIER_PATTERN` regex
    - Use `Specification<PluginObject>` with HibernateCriteriaBuilder.sql()
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectSpecification.java`
  - [x] 2.3 Make PluginObjectRepository extend JpaSpecificationExecutor
    - Add `JpaSpecificationExecutor<PluginObject>` to interface
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectRepository.java`
  - [x] 2.4 Extend service layer with JSONB filter support
    - Update list methods to accept optional `String filter` param
    - When filter is present, build Specification combining entity binding predicates + JSONB filter
    - When filter is absent and entity params are present, use repository query methods
    - When both absent, use existing `findByPluginIdAndObjectType`
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectService.java`
  - [x] 2.5 Extend existing list endpoint with query params
    - Add `@RequestParam(required = false) EntityType entityType`
    - Add `@RequestParam(required = false) Long entityId`
    - Add `@RequestParam(required = false) String filter`
    - Pass through to service
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectController.java`
  - [x] 2.6 Add cross-type list endpoint
    - `GET /api/plugins/{pluginId}/objects` with required entityType + entityId query params
    - Optional `filter` param for JSONB filtering
    - Return 400 Bad Request when entityType or entityId is missing
    - Must not conflict with existing `GET /{objectType}` path (this endpoint has no path variable after /objects)
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectController.java`
  - [x] 2.7 Extend save endpoint with entity binding query params
    - Add `@RequestParam(required = false) EntityType entityType`
    - Add `@RequestParam(required = false) Long entityId`
    - Pass through to service
    - File: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectController.java`
  - [x] 2.n Ensure API & JSONB filter tests pass
    - Run ONLY the 4-6 tests written in 2.1
    - Do NOT run entire test suite

**Acceptance Criteria:**
- The 4-6 tests pass
- Entity-filtered list endpoint works with query params
- Cross-type endpoint returns all object types for an entity
- Cross-type endpoint returns 400 when required params missing
- JSONB filter works with eq, gt, lt, exists, bool operators
- Combined entity + JSONB filtering works

---

### Task Group 3: Plugin SDK & Host Message Handler
**Dependencies:** Group 2
**Estimated Steps:** 7

- [x] 3.0 Complete SDK and message handler updates
  - [x] 3.1 Write 2-4 focused review checks for SDK changes (manual verification -- no automated frontend tests for SDK)
    - Check: objects.list(type) without options still works (backward compat)
    - Check: objects.list(type, { entityType, entityId }) passes query params
    - Check: objects.listByEntity(entityType, entityId) calls cross-type endpoint
    - Check: objects.save(type, id, data, { entityType, entityId }) passes query params
  - [x] 3.2 Update plugin-sdk types.ts with entity binding fields
    - Add `entityType?: string` and `entityId?: string` to `PluginObject` interface
    - File: `src/main/frontend/src/plugin-sdk/types.ts`
  - [x] 3.3 Update this-plugin.ts SDK source
    - `objects.list(type, options?)` -- options: `{ entityType?, entityId?, filter? }`, pass in message payload
    - `objects.listByEntity(entityType, entityId, options?)` -- new method, sends `objectsListByEntity` message with entityType, entityId, and optional filter
    - `objects.save(type, id, data, options?)` -- options: `{ entityType?, entityId? }`, pass in message payload
    - File: `src/main/frontend/src/plugin-sdk/this-plugin.ts`
  - [x] 3.4 Update PluginMessageHandler.ts
    - `objectsList`: read entityType, entityId, filter from payload, build query string, append to URL
    - `objectsListByEntity`: new case, calls `GET /plugins/${pluginId}/objects?entityType=X&entityId=Y` with optional filter
    - `objectsSave`: read entityType, entityId from payload, build query string, append to PUT URL
    - File: `src/main/frontend/src/plugins/PluginMessageHandler.ts`
  - [x] 3.5 Update warehouse plugin sdk.ts type declarations
    - Add `entityType?: string` and `entityId?: string` to local `PluginObject` interface
    - Update `PluginSDKType.thisPlugin.objects.list` signature to accept optional options
    - Add `listByEntity(entityType: string, entityId: string, options?: { filter?: string }): Promise<PluginObject[]>` method
    - Update `PluginSDKType.thisPlugin.objects.save` signature to accept optional options
    - File: `plugins/warehouse/src/sdk.ts`
  - [x] 3.6 Update plugin-sdk.js IIFE bundle
    - Update `objects.list` to accept optional second param (options object), include in message payload
    - Add `objects.listByEntity` method sending `objectsListByEntity` message
    - Update `objects.save` to accept optional fourth param (options object), include in message payload
    - Manual update matching current project pattern (no build pipeline)
    - File: `src/main/resources/static/assets/plugin-sdk.js`
  - [x] 3.n Verify SDK changes are consistent across all files
    - Confirm this-plugin.ts, PluginMessageHandler.ts, plugin-sdk.js, types.ts, and warehouse sdk.ts all have matching signatures
    - Confirm backward compatibility: existing calls without options still work

**Acceptance Criteria:**
- SDK source (this-plugin.ts) accepts optional entity binding parameters
- Host message handler forwards entity params as query string to API
- IIFE bundle (plugin-sdk.js) updated to match source module
- Warehouse sdk.ts types reflect new signatures
- All existing SDK calls continue working without changes

---

### Task Group 4: Test Review & Gap Analysis
**Dependencies:** Groups 1, 2, 3

- [x] 4.0 Review and fill critical gaps
  - [x] 4.1 Review tests from previous groups (8-12 existing tests)
  - [x] 4.2 Analyze gaps for entity binding feature only
    - Check: edge cases for JSONB filter parsing (invalid format, unsupported operator)
    - Check: save with entityType but no entityId (should that work?)
    - Check: cross-type endpoint with filter param
    - Check: response shape includes all expected fields
  - [x] 4.3 Write up to 10 additional strategic tests
    - JSONB filter with `bool` operator
    - JSONB filter with `exists` operator
    - Invalid filter format returns appropriate error
    - Save with only entityType (no entityId) -- verify behavior
    - Cross-type list with JSONB filter combined
    - Verify existing plugin object tests still pass (regression)
  - [x] 4.4 Run feature-specific tests only (expect 18-22 total)

**Acceptance Criteria:**
- All feature tests pass (18-22 total)
- No more than 10 additional tests added
- Edge cases for JSONB filtering covered
- Backward compatibility verified via existing tests still passing

---

## Execution Order

1. Group 1: Database & Entity Layer (7 steps, no dependencies)
2. Group 2: API & JSONB Filter Layer (7 steps, depends on Group 1)
3. Group 3: Plugin SDK & Host Message Handler (7 steps, depends on Group 2)
4. Group 4: Test Review & Gap Analysis (4 steps, depends on Groups 1-3)

## Standards Compliance

Follow standards from `.maister/docs/standards/`:
- **global/** - Always applicable (minimal-implementation, coding-style, conventions)
- **backend/models.md** - @Enumerated(EnumType.STRING), LAZY fetch default, business key equals/hashCode, Lombok annotations (@Getter/@Setter/@NoArgsConstructor)
- **backend/migrations.md** - Reversible migration, small focused change, zero-downtime (nullable columns)
- **backend/api.md** - RESTful query params for filtering, consistent naming, plural nouns
- **backend/queries.md** - Parameterized queries, strategic indexing
- **testing/backend-testing.md** - Integration-first, MockMvc + jsonPath, action_condition_expectedResult naming, createAndSave* helpers, @Transactional rollback

## Notes

- Test-Driven: Each group starts with tests before implementation
- Run Incrementally: Only new tests after each group
- Mark Progress: Check off steps as completed
- Reuse First: Adapt PluginDataSpecification parsing pattern for JSONB filter (do not duplicate)
- Explicit Intent Model: Save without entity params always clears binding to null
- No FK Constraint: entity_id is polymorphic (BIGINT with no referential integrity by design)
- IIFE Manual Update: plugin-sdk.js has no build pipeline -- update manually to match this-plugin.ts
