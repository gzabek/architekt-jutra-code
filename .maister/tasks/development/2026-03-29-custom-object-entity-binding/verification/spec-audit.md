# Specification Audit: Custom Object Entity Binding

**Audit Date**: 2026-03-29
**Auditor**: Spec Auditor (Independent)
**Specification**: `.maister/tasks/development/2026-03-29-custom-object-entity-binding/implementation/spec.md`
**Compliance Status**: Mostly Compliant

---

## Summary

The specification is well-structured, thorough, and implementable. It correctly identifies all layers requiring change (database, backend, host frontend, plugin SDK), provides accurate file references, and the technical approach is sound. The analysis artifacts (requirements, gap-analysis, clarifications, scope-clarifications) are consistent with each other and with the final spec.

I identified **0 critical issues**, **2 high-severity findings**, **4 medium-severity findings**, and **2 low-severity items**. The high findings are a route collision risk and a missing detail about the cross-type list endpoint's handler in the SDK/message layer. All are resolvable without architectural changes.

---

## High Severity Findings

### H1: Cross-Type List Endpoint Route Collision with Existing `/{objectType}` Route

**Spec Reference**: Requirement 7 -- "New endpoint `GET /api/plugins/{pluginId}/objects` with required `entityType` and `entityId` query params."

**Evidence**:
- `PluginObjectController.java` has `@RequestMapping("/api/plugins/{pluginId}/objects")` at the class level (line 17)
- Existing list endpoint is `@GetMapping("/{objectType}")` (line 26)
- The proposed new endpoint would be `@GetMapping` with no path suffix (base path)

**Issue**: Spring MVC does not have a route collision here -- `@GetMapping` (no path) maps to `/api/plugins/{pluginId}/objects` and `@GetMapping("/{objectType}")` maps to `/api/plugins/{pluginId}/objects/{objectType}`. These are distinct routes and will not collide. However, the spec does not address what happens if someone calls `GET /api/plugins/{pluginId}/objects` **without** the required `entityType` and `entityId` query params. The spec says these are "required" but does not specify error handling for missing params.

**Category**: Incomplete

**Severity**: High -- Without specifying validation behavior for missing required params, implementors may either return an empty list (silently wrong) or throw an unformatted 500 error. This is the only new endpoint (not an extension of an existing one), so clear error behavior matters.

**Recommendation**: Add to spec: "When `entityType` or `entityId` are missing from the cross-type list endpoint, return HTTP 400 with a descriptive error message." Also clarify: should `@RequestParam(required = true)` be used (Spring auto-validates), or should the controller handle validation explicitly?

---

### H2: Cross-Type List Endpoint Missing from SDK and Message Handler Specification

**Spec Reference**: Requirements 9, 11 -- SDK `objects.list` update and host message handler update.

**Evidence**:
- Requirement 9 specifies `objects.list(type, options?)` -- the first parameter `type` (objectType) is still required
- Requirement 7 defines a cross-type list endpoint `GET /api/plugins/{pluginId}/objects` that does NOT take objectType
- `this-plugin.ts` line 49: current `list(type: string)` signature requires objectType
- `PluginMessageHandler.ts` line 121: `objectsList` handler builds URL as `/plugins/${pluginId}/objects/${payload.objectType}`

**Issue**: The spec defines a new cross-type endpoint (Requirement 7) that lists objects across all types for a given entity, but the SDK update (Requirement 9) only extends the existing `objects.list(type, options?)` where `type` is still required. There is no SDK method or message handler case defined for calling the cross-type endpoint without an objectType.

How does a plugin developer call `GET /api/plugins/{pluginId}/objects?entityType=PRODUCT&entityId=123` through the SDK? The spec does not define this. Options include:
- (A) Allow `type` to be optional in `objects.list(type?, options?)` -- when omitted, call the cross-type endpoint
- (B) Add a new method like `objects.listByEntity(options)`
- (C) Allow `type` to be `null` or empty string as a sentinel

User story 3 explicitly states: "As a plugin developer, I want to list all custom objects for an entity regardless of object type." But the SDK specification does not provide a way to do this.

**Category**: Incomplete

**Severity**: High -- A user story has no SDK path to fulfillment. The backend endpoint exists in spec but is unreachable from plugin code.

**Recommendation**: Extend Requirement 9 to define how the cross-type list is exposed in the SDK. Also extend Requirement 11 to define the corresponding message handler case. Simplest approach: make `type` optional in `objects.list(type?, options?)` and when `type` is omitted/null, have the message handler call the base endpoint.

---

## Medium Severity Findings

### M1: Migration Index Inconsistency Between Spec and Requirements

**Spec Reference**: Requirement 3 -- "composite index on `(plugin_id, object_type, entity_type, entity_id)`"
**Requirements Reference**: FR1 -- "index on `(plugin_id, entity_type, entity_id)`"

**Evidence**:
- Spec requirement 3 specifies a 4-column index: `(plugin_id, object_type, entity_type, entity_id)`
- Requirements FR1 specifies a 3-column index: `(plugin_id, entity_type, entity_id)`
- Gap analysis recommendation 3 specifies the 4-column index and notes the existing `idx_plugin_objects_plugin_type` on `(plugin_id, object_type)` (migration 006, line 74-80)

**Issue**: The spec and requirements document disagree on the index composition. The 4-column index covers both the per-type entity query (`GET .../objects/{objectType}?entityType=...`) and the cross-type entity query (`GET .../objects?entityType=...`). The 3-column index only covers cross-type queries. Given that both query patterns exist, the 4-column index is the better choice (the existing 2-column index on `(plugin_id, object_type)` is a prefix), but this should be explicitly stated.

**Category**: Ambiguous

**Severity**: Medium -- Wrong index choice would cause slow queries but not functional failure.

**Recommendation**: Align to the 4-column index from the spec. The existing `idx_plugin_objects_plugin_type` on `(plugin_id, object_type)` is a prefix of the new 4-column index; the spec should state whether to drop the redundant 2-column index or keep it.

---

### M2: JSONB Filter -- PluginObjectRepository Does Not Extend JpaSpecificationExecutor

**Spec Reference**: Technical Approach / Query Strategy -- "use JPA Specification or `@Query` with native SQL"

**Evidence**:
- `PluginObjectRepository.java` (line 8): `extends JpaRepository<PluginObject, Long>` -- does NOT extend `JpaSpecificationExecutor<PluginObject>`
- `ProductRepository.java` extends `JpaSpecificationExecutor` (confirmed via grep)
- PluginDataSpecification implements `Specification<Product>`, demonstrating the pattern

**Issue**: To use JPA Specifications for JSONB filtering on PluginObject, the repository must extend `JpaSpecificationExecutor<PluginObject>`. The spec mentions this as a possible approach but does not explicitly call it out as a required change. An implementor following only the "New Components Required" table might miss this.

**Category**: Incomplete

**Severity**: Medium -- An implementor would discover this quickly, but it should be specified to avoid ambiguity about the query approach.

**Recommendation**: Add to Requirement 3 or the Query Strategy section: "PluginObjectRepository must extend `JpaSpecificationExecutor<PluginObject>` to support Specification-based JSONB filtering." Alternatively, if `@Query` with native SQL is preferred, state that explicitly and specify the query method signatures.

---

### M3: Save Behavior When Entity Binding Changes on Existing Object

**Spec Reference**: Requirement 5 -- "When provided, set entity binding on the object. When absent, leave null."

**Evidence**:
- `PluginObjectService.save()` (line 36-48): upsert logic fetches existing or creates new, then sets data
- Unique constraint is `(pluginId, objectType, objectId)` -- entity binding is NOT part of identity

**Issue**: The spec is ambiguous about what happens in these scenarios:
1. Object exists with `entityType=PRODUCT, entityId=1`. Save is called without entity params. Does entity binding get cleared to null or preserved?
2. Object exists with `entityType=PRODUCT, entityId=1`. Save is called with `entityType=CATEGORY, entityId=5`. Does entity binding get updated?

The spec says "When absent, leave null" which could mean "set to null" or "leave unchanged." For a new object, both interpretations are the same (null). For an existing object, they are very different.

**Category**: Ambiguous

**Severity**: Medium -- Affects data integrity. If "leave null" means "clear to null," then every save without entity params would strip entity binding from existing objects, which is almost certainly not the desired behavior.

**Recommendation**: Clarify: "When entity params are absent on save, preserve existing entity binding (do not overwrite). When entity params are provided, update entity binding to the provided values." If clearing entity binding is a valid use case, define an explicit mechanism (e.g., `entityType=NONE` or a dedicated endpoint).

---

### M4: Filter Param Name Inconsistency

**Spec Reference**: Requirement 6 uses `filter`, Requirements doc FR3 uses `filter`, but Clarifications Phase 1 uses inline syntax like `quantity>0` as a query param.

**Evidence**:
- Spec Requirement 6: `filter` param name
- Spec Requirement 8: `filter=fieldPath:operator:value` format
- Clarifications Phase 1: `GET ...?productId=123&quantity>0` -- uses raw expression as query param
- Scope Clarifications: `?filter=quantity:gt:0`

**Issue**: The Phase 1 clarifications use a different syntax (`quantity>0` as a raw query param) than the final spec (`filter=quantity:gt:0` as a named param). This is likely just a clarifications-era draft that was superseded, but the discrepancy between artifacts could confuse someone reading them sequentially.

**Category**: Ambiguous (cross-document)

**Severity**: Medium -- The spec itself is consistent; the discrepancy is only in supporting documents. An implementor reading all artifacts might be confused.

**Recommendation**: No spec change needed, but consider adding a note in the clarifications document that the syntax was refined in later phases, or simply note in the spec that the `filter` param name is the final decision superseding earlier discussion.

---

## Low Severity Findings

### L1: Response DTO Does Not Include createdAt/updatedAt in SDK Types

**Spec Reference**: Requirement 4 -- "Add `entityType` and `entityId` fields to the response record."
**Evidence**:
- `PluginObjectResponse.java` (line 6-12): includes `createdAt` and `updatedAt` in the Java response record
- `plugin-sdk/types.ts` `PluginObject` interface (line 18-24): does NOT include `createdAt` or `updatedAt`
- `plugins/warehouse/src/sdk.ts` `PluginObject` interface (line 14-20): does NOT include `createdAt` or `updatedAt`

**Issue**: The backend response includes timestamps, but the TypeScript types do not declare them. This is a pre-existing discrepancy, not introduced by this task. However, since the spec is adding `entityType`/`entityId` to both the Java response and the TS types (Requirements 4, 12, 13), this is an opportunity to fix the existing gap.

**Category**: Extra (pre-existing discrepancy, not task-related)

**Severity**: Low -- Not in scope for this task, but worth noting.

**Recommendation**: Consider adding `createdAt` and `updatedAt` to the TS PluginObject interface while updating it for entityType/entityId. This is optional and out of scope.

---

### L2: EntityType Enum Placement Not Specified

**Spec Reference**: Requirement 1 -- "Create a Java enum `EntityType`"

**Evidence**:
- No package specified for where EntityType should live
- PluginObject is in `pl.devstyle.aj.core.plugin`
- Product is in `pl.devstyle.aj.product`, Category is in `pl.devstyle.aj.category`

**Issue**: The spec does not specify which package the `EntityType` enum should be created in. Since it references domain entities (Product, Category) from different packages but is used by the core plugin package, placement matters for dependency direction.

**Category**: Incomplete (minor)

**Severity**: Low -- Implementor can make a reasonable choice (likely `pl.devstyle.aj.core.plugin` since that is where it is used), but it would be cleaner to specify.

**Recommendation**: Add: "Create `EntityType` enum in `pl.devstyle.aj.core.plugin` package (or `pl.devstyle.aj.core` if it will be reused outside the plugin module)."

---

## Clarification Questions

### CQ1: Entity Binding Preservation on Save (relates to M3)
When an existing PluginObject has entity binding set (e.g., `entityType=PRODUCT, entityId=1`) and a save request is made WITHOUT entity params, should the existing entity binding be:
- (A) Preserved (recommended)
- (B) Cleared to null

### CQ2: Cross-Type List SDK Access (relates to H2)
How should a plugin developer invoke the cross-type list endpoint (`GET /api/plugins/{pluginId}/objects?entityType=PRODUCT&entityId=123`) through the SDK?
- (A) Make `type` optional in `objects.list(type?, options?)`
- (B) Add a new SDK method like `objects.listByEntity(options)`
- (C) Other

### CQ3: Error Handling for Missing Required Params (relates to H1)
For the cross-type list endpoint, when `entityType` or `entityId` query params are missing, what should happen?
- (A) Return HTTP 400 Bad Request with descriptive message
- (B) Return empty list
- (C) Treat as "list all objects for this plugin" (no filtering)

---

## Verification of Spec Accuracy Against Codebase

The following spec claims were independently verified as accurate:

| Claim | Verified | Evidence |
|-------|----------|----------|
| PluginObject entity exists with pluginId, objectType, objectId, data fields | Yes | `PluginObject.java` lines 29-40 |
| Business key equals/hashCode on (pluginId, objectType, objectId) | Yes | `PluginObject.java` lines 42-54 |
| Unique constraint on (pluginId, objectType, objectId) | Yes | `PluginObject.java` line 20-21, migration 006 line 63-65 |
| PluginObjectRepository has findByPluginIdAndObjectType | Yes | `PluginObjectRepository.java` line 10 |
| PluginObjectService.save() is upsert pattern | Yes | `PluginObjectService.java` lines 36-48 |
| Controller base path is /api/plugins/{pluginId}/objects | Yes | `PluginObjectController.java` line 17 |
| PluginObjectResponse is a record with from() factory | Yes | `PluginObjectResponse.java` lines 6-27 |
| PluginDataSpecification uses 4-segment colon format | Yes | `PluginDataSpecification.java` lines 31-53 |
| PluginDataSpecification operators: eq, gt, lt, exists, bool | Yes | `PluginDataSpecification.java` line 48 |
| SDK objects.list sends objectsList message with objectType | Yes | `this-plugin.ts` line 50 |
| PluginMessageHandler objectsList calls api.get with no query params | Yes | `PluginMessageHandler.ts` line 121 |
| plugin-sdk.js is manually maintained IIFE bundle | Yes | `plugin-sdk.js` -- minified IIFE, no build script found |
| Migration 006 is the latest; 007 does not exist | Yes | Glob confirms only 001-006 exist |
| No @Enumerated usage exists in codebase | Yes | Grep for `@Enumerated` returns zero results |
| BaseEntity provides id, createdAt, @Version updatedAt | Yes | `BaseEntity.java` lines 23-36 |

---

## Extra Features (Not in Spec, Present in Analysis)

1. **Gap analysis mentions ProductStockTab.tsx changes** (line 127-128, "Use entity-filtered query instead of client-side filter") -- the spec correctly marks this as Out of Scope. No conflict.

2. **Gap analysis mentions extracting shared filter parsing utility** (recommendation 2) -- the spec's "New Components Required" table mentions adapting but not duplicating parsing logic. The spec does not mandate a shared utility extraction. This is an implementation detail left to the developer, which is appropriate.

---

## Recommendations Summary

| ID | Severity | Action |
|----|----------|--------|
| H1 | High | Define error handling for missing required params on cross-type list endpoint |
| H2 | High | Define SDK method and message handler for cross-type list (objectType-less query) |
| M1 | Medium | Align index composition between spec and requirements; decide on dropping redundant index |
| M2 | Medium | Explicitly state that PluginObjectRepository must extend JpaSpecificationExecutor |
| M3 | Medium | Clarify entity binding preservation vs clearing on save without entity params |
| M4 | Medium | Note in spec that filter syntax supersedes Phase 1 clarification draft |
| L1 | Low | Consider adding createdAt/updatedAt to TS types (optional, pre-existing gap) |
| L2 | Low | Specify EntityType enum package placement |
