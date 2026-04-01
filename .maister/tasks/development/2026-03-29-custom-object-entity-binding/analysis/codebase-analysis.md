# Codebase Analysis Report

**Date**: 2026-03-29
**Task**: Add entity binding to PluginObject, query custom objects by entity ID, and add plugin_data to Product for warehouse availability
**Description**: Modify custom objects (PluginObject) to support entity binding (connect custom objects to main entities like Product), query custom objects by entity ID (e.g., stock by productId), and add plugin_data directly to Product level for warehouse availability (true/false).
**Analyzer**: codebase-analyzer skill (3 Explore agents: File Discovery, Code Analysis, Pattern Mining)

---

## Summary

The codebase has a two-tier plugin data storage system: (1) product-level `pluginData` JSONB for lightweight per-entity data and (2) a separate `plugin_objects` table for standalone plugin collections. The `plugin_objects` table currently has no concept of entity binding -- objects are identified only by `(pluginId, objectType, objectId)`. The warehouse plugin works around this by embedding `productId` inside the JSONB `data` field and filtering client-side, which is inefficient and prevents server-side querying. The task requires adding `entity_type` and `entity_id` columns to `plugin_objects` to enable native entity binding and server-side filtering, plus using the existing `pluginData` mechanism to store warehouse availability (inStock boolean) directly on Products.

---

## Files Identified

### Primary Files

**`src/main/java/pl/devstyle/aj/core/plugin/PluginObject.java`** (55 lines)
- JPA entity for plugin custom objects with pluginId, objectType, objectId, JSONB data
- Must be modified to add entityType and entityId fields
- Business key equality on (pluginId, objectType, objectId)

**`src/main/java/pl/devstyle/aj/core/plugin/PluginObjectRepository.java`** (15 lines)
- Spring Data JPA repository with find/delete by (pluginId, objectType, objectId)
- Must add query methods for entity binding (findByPluginIdAndObjectTypeAndEntityTypeAndEntityId)

**`src/main/java/pl/devstyle/aj/core/plugin/PluginObjectService.java`** (57 lines)
- Service layer with list, get, save, delete operations; validates plugin is enabled
- Must add entity-scoped methods (listByEntity, saveWithEntity)

**`src/main/java/pl/devstyle/aj/core/plugin/PluginObjectController.java`** (60 lines)
- REST controller at /api/plugins/{pluginId}/objects
- Must add endpoint for querying objects by entity (e.g., query param or nested path)

**`src/main/java/pl/devstyle/aj/core/plugin/PluginObjectResponse.java`** (27 lines)
- Record DTO mapping PluginObject to API response
- Must include entityType and entityId in response

**`src/main/resources/db/changelog/2026/006-create-plugin-objects-table.yaml`** (84 lines)
- Liquibase migration that created the plugin_objects table
- A new migration (007) must add entity_type, entity_id columns and index

### Related Files

**`src/main/java/pl/devstyle/aj/core/plugin/PluginDataService.java`** (71 lines)
- Manages per-product plugin data in Product.pluginData JSONB
- Already supports the pattern needed for warehouse availability (setData/getData)
- No changes needed -- warehouse plugin can use this as-is for inStock flag

**`src/main/java/pl/devstyle/aj/core/plugin/PluginDataController.java`** (44 lines)
- REST endpoints at /api/plugins/{pluginId}/products/{productId}/data
- Already supports GET/PUT/DELETE for product-level plugin data

**`src/main/java/pl/devstyle/aj/product/Product.java`** (64 lines)
- Product entity with pluginData JSONB column already present
- No changes needed -- pluginData column exists and supports namespaced plugin data

**`src/main/java/pl/devstyle/aj/product/PluginDataSpecification.java`** (88 lines)
- JPA Specification for filtering products by JSONB plugin data values
- Already supports bool operator -- warehouse inStock filtering works out of the box

**`src/test/java/pl/devstyle/aj/core/plugin/PluginDataAndObjectsIntegrationTests.java`** (206 lines)
- Integration tests covering plugin data CRUD and plugin object CRUD
- Must be extended with entity binding tests

**`plugins/warehouse/src/pages/ProductStockTab.tsx`** (88 lines)
- Frontend component that currently fetches ALL stock objects then filters client-side by productId
- Will benefit from server-side entity binding query (fetch stock objects for a specific product)

**`plugins/warehouse/src/sdk.ts`** (89 lines)
- TypeScript SDK type declarations
- objects.list currently takes only objectType; may need optional entityId parameter

**`src/main/java/pl/devstyle/aj/core/BaseEntity.java`** (36 lines)
- Base class with id, createdAt, @Version updatedAt
- Template for entity patterns

**`src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptorService.java`** -- Plugin enablement validation used by all plugin services

---

## Current Functionality

### Two-Tier Plugin Storage

The platform provides two complementary data storage mechanisms for plugins:

**Tier 1 -- Entity Plugin Data** (`Product.pluginData` JSONB): Lightweight key-value data attached directly to entities. Namespaced per plugin (e.g., `{"warehouse": {"inStock": true}}`). Already fully implemented via `PluginDataService`/`PluginDataController`. Products can be filtered by this data via `PluginDataSpecification` (supports eq, gt, lt, exists, bool operators).

**Tier 2 -- Custom Objects** (`plugin_objects` table): Independent plugin-managed collections stored in a separate table. Each object has (pluginId, objectType, objectId, JSONB data). No relationship to main entities -- objects exist in isolation.

### Key Components/Functions

- **PluginObjectService.list(pluginId, objectType)**: Returns all objects of a type for a plugin -- no entity filtering
- **PluginObjectService.save(pluginId, objectType, objectId, data)**: Upsert by natural key (pluginId + objectType + objectId)
- **PluginDataService.setData(pluginId, productId, data)**: Sets plugin namespace in product's pluginData JSONB
- **PluginDataSpecification.parse(filter)**: Parses `pluginId:jsonPath:operator:value` filter strings for product queries

### Data Flow (Current Warehouse Plugin)

1. Plugin saves stock entries as custom objects: `objects.save("stock", "{productId}-{warehouseId}", {productId, warehouseId, quantity})`
2. ProductStockTab loads ALL stock objects: `objects.list("stock")`
3. Client-side filtering: `.filter(e => e.productId === Number(productId))`
4. No product-level pluginData is being set for warehouse availability

### Problem

The warehouse plugin stores `productId` inside JSONB data and uses composite objectIds (`{productId}-{warehouseId}`), then filters client-side. This approach:
- Fetches all stock objects regardless of which product is being viewed
- Cannot leverage database indexes for entity queries
- Scales poorly as stock entries grow
- Cannot support server-side "list stock for product X" queries

---

## Dependencies

### Imports (What PluginObject Depends On)

- `pl.devstyle.aj.core.BaseEntity`: id, createdAt, updatedAt fields
- `pl.devstyle.aj.core.plugin.PluginDescriptorService`: plugin enablement validation
- `pl.devstyle.aj.core.error.EntityNotFoundException`: standard error handling
- `jakarta.persistence.*`: JPA annotations
- `org.hibernate.annotations.JdbcTypeCode`: JSONB type mapping

### Consumers (What Depends On PluginObject)

- **PluginObjectService**: CRUD operations on PluginObject
- **PluginObjectController**: REST API layer
- **PluginObjectResponse**: DTO mapping
- **PluginDataAndObjectsIntegrationTests**: Test coverage
- **Warehouse plugin (frontend)**: Uses SDK `objects.*` methods which call PluginObjectController
- **Plugin SDK (plugin-sdk.js)**: JavaScript bridge that calls the REST API

**Consumer Count**: 6 files/components
**Impact Scope**: Medium -- changes to the entity schema affect the database, API response shape, SDK types, and all plugins that use custom objects. However, the new fields (entityType, entityId) are nullable/optional, so existing functionality remains backward compatible.

---

## Test Coverage

### Test Files

- **PluginDataAndObjectsIntegrationTests.java** (206 lines): 6 tests covering plugin data CRUD (set, get, delete with namespace isolation) and plugin object CRUD (save, list by type, upsert on duplicate key)

### Coverage Assessment

- **Test count**: 6 integration tests
- **Covered**: Plugin data set/get/delete with multi-plugin isolation, plugin object save/list/upsert
- **Gaps**: No tests for entity-bound objects (does not exist yet), no tests for querying objects by entity, no tests for product-level warehouse availability filtering, no delete test for plugin objects

---

## Coding Patterns

### Naming Conventions

- **Entities**: PascalCase singular nouns (Product, PluginObject, Category)
- **Services**: `{Entity}Service` with `@Transactional` methods
- **Controllers**: `{Entity}Controller` with `@RestController`
- **Repositories**: `{Entity}Repository` extending `JpaRepository`
- **DTOs**: `{Entity}Response` as Java records with static `from()` factory
- **Tests**: `{Feature}IntegrationTests` (plural), package-private class
- **Test methods**: `action_condition_expectedResult` pattern
- **Migrations**: Sequential numbering `{NNN}-{description}.yaml`

### Architecture Patterns

- **Style**: Layered MVC (Controller -> Service -> Repository -> Entity)
- **State Management**: JPA/Hibernate with `@Transactional`
- **Base Entity**: `@MappedSuperclass` with `SEQUENCE` generation (allocationSize=1)
- **JSONB**: Hibernate `@JdbcTypeCode(SqlTypes.JSON)` with `Map<String, Object>`
- **Validation**: Plugin enablement checked at service layer via `findEnabledOrThrow`
- **Error Handling**: Centralized `GlobalExceptionHandler`, typed `EntityNotFoundException`
- **Tests**: SpringBootTest + AutoConfigureMockMvc + Transactional + TestContainers
- **Test Data**: Private `createAndSave*()` helper methods with `saveAndFlush()`

---

## Complexity Assessment

| Factor | Value | Level |
|--------|-------|-------|
| Primary Files | 6 files to modify | Medium |
| Related Files | 7 files affected/referenced | Medium |
| Dependencies | 5 imports in PluginObjectService | Medium |
| Consumers | 6 consumers | Medium |
| Test Coverage | 6 tests, gaps in entity binding | Medium |

### Overall: Moderate

The core change (adding two nullable columns to PluginObject) is straightforward, but it touches the full stack: database migration, entity, repository, service, controller, response DTO, integration tests, SDK types, and frontend plugin. The existing patterns are well-established and consistent, which reduces implementation risk. Backward compatibility is preserved since the new columns are nullable.

---

## Key Findings

### Strengths
- Clean, consistent layered architecture with clear conventions
- Two-tier plugin data design already anticipates the need (pluginData for lightweight entity data, custom objects for collections)
- Product.pluginData and PluginDataSpecification already support warehouse availability (inStock boolean) -- no new product-level changes needed
- PluginObject uses business key equality, making the new fields a natural extension
- Existing upsert pattern in PluginObjectService is straightforward to extend
- Good test infrastructure (TestContainers, MockMvc) ready for new test cases

### Concerns
- The warehouse plugin currently fetches ALL stock objects and filters client-side -- this is a performance bottleneck that will worsen with scale
- The unique constraint on (pluginId, objectType, objectId) may need updating if entity binding changes the identity semantics
- The plugin SDK JavaScript code (plugin-sdk.js) needs to be updated to support entity parameters in objects.list -- this file was not found in exploration but must exist
- Foreign key from plugin_objects.plugin_id to plugins.id means entityId cannot use a FK to products (since entity_type is polymorphic) -- rely on application-level integrity

### Opportunities
- Entity binding enables server-side filtering, eliminating the client-side fetch-all-then-filter anti-pattern
- The PluginDataSpecification `bool` operator means warehouse availability filtering on product lists works immediately once setData is called
- Optional entity binding (nullable columns) means existing non-entity-bound objects continue working unchanged
- Composite index on (plugin_id, entity_type, entity_id) will make entity queries performant

---

## Impact Assessment

- **Primary changes**: PluginObject.java (add fields), PluginObjectRepository.java (add query methods), PluginObjectService.java (add entity methods), PluginObjectController.java (add entity endpoint), PluginObjectResponse.java (add fields), new Liquibase migration (007)
- **Related changes**: Plugin SDK JavaScript (objects.list with entity params), warehouse plugin SDK types (sdk.ts), ProductStockTab.tsx (use entity-bound query), warehouse plugin logic (call setData for inStock)
- **Test updates**: Extend PluginDataAndObjectsIntegrationTests with entity binding tests (save with entity, list by entity, entity isolation)

### Risk Level: Low-Medium

The new columns are nullable, preserving backward compatibility for all existing custom objects. The database migration is additive (columns + index, no data migration). The API changes are additive (new optional query parameters or new endpoint). The main risk is ensuring the plugin SDK JavaScript bridge correctly passes entity parameters through the iframe postMessage protocol, as this code was not directly examined.

---

## Recommendations

### Implementation Strategy

**Backend (Java/Spring)**:
1. **New Liquibase migration** (007): Add `entity_type VARCHAR(50)` and `entity_id BIGINT` nullable columns to `plugin_objects`. Add composite index on `(plugin_id, entity_type, entity_id)`. Consider adding index on `(plugin_id, object_type, entity_type, entity_id)` for the most common query pattern.
2. **PluginObject entity**: Add `entityType` and `entityId` fields with column annotations. Keep nullable -- not all objects are entity-bound. Update business key equals/hashCode if entity binding changes identity semantics (evaluate whether (pluginId, objectType, objectId) remains sufficient or should include entity fields).
3. **PluginObjectRepository**: Add `findByPluginIdAndObjectTypeAndEntityTypeAndEntityId(pluginId, objectType, entityType, entityId)` query method.
4. **PluginObjectService**: Add `listByEntity(pluginId, objectType, entityType, entityId)` method. Extend `save()` to accept optional entityType/entityId parameters.
5. **PluginObjectController**: Add query parameters to the list endpoint (e.g., `GET /api/plugins/{pluginId}/objects/{objectType}?entityType=product&entityId=42`) or add a nested endpoint. Query parameters are more RESTful and backward compatible.
6. **PluginObjectResponse**: Add entityType and entityId fields.

**Frontend (SDK + Warehouse Plugin)**:
7. **Plugin SDK (plugin-sdk.js)**: Extend `objects.list(type)` to accept optional `{entityType, entityId}` parameters, passed as query string to the API.
8. **SDK types (sdk.ts)**: Update `objects.list` signature to accept optional filter params.
9. **ProductStockTab.tsx**: Use entity-bound query `objects.list("stock", {entityType: "product", entityId: productId})` instead of fetching all and filtering.
10. **Warehouse availability**: When saving/updating stock, call `thisPlugin.setData(productId, {inStock: totalQuantity > 0})` to set product-level availability. This enables the existing `PluginDataSpecification` bool filter to work for product list filtering.

### Backward Compatibility
- All new columns are nullable -- existing objects unaffected
- Existing API endpoints continue working (new query params are optional)
- SDK objects.list without entity params returns all objects as before
- No data migration needed for existing records

### Testing Strategy
- Add integration tests for: save object with entity binding, list objects by entity, verify entity isolation (objects for product A not returned when querying product B), save object without entity binding still works, response includes entity fields when present
- Test warehouse availability flow end-to-end: save stock -> set inStock on product -> filter products by inStock

---

## Next Steps

Invoke gap-analyzer to identify any remaining unknowns, then proceed to specification and implementation planning.
