# Pragmatic Code Review: Custom Object Entity Binding

## Executive Summary

**Status**: Appropriate

**Project Scale**: Pre-alpha microkernel platform (scaffolding phase, no production users)

**Overall Complexity Assessment**: **Low** -- The implementation is well-proportioned for the feature scope. It adds entity binding and JSONB filtering to an existing entity using standard Spring Boot patterns (JPA Specification, query params, derived query methods). No unnecessary abstractions, no enterprise patterns, no heavy infrastructure.

**Key Findings**: 3 Medium, 1 Low

---

## Complexity Assessment

| Dimension | Rating | Notes |
|-----------|--------|-------|
| Abstraction layers | Appropriate | Controller -> Service -> Repository (standard 3-tier, no extras) |
| Infrastructure | Appropriate | No new infrastructure added. Builds on existing JPA + PostgreSQL JSONB |
| Pattern sophistication | Appropriate | JPA Specification is the right tool for dynamic JSONB filtering |
| Configuration | Appropriate | Single migration file, no new config files |
| New dependencies | None | Zero new dependencies added |

The implementation adds ~483 lines of Java across 8 files (including a 6-line enum), plus ~50 lines of TypeScript SDK updates and 12 integration tests. This is proportional to the 14 core requirements in the spec.

---

## Key Issues Found

### M1: Unused Repository Methods (Medium)

**Evidence**: `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectRepository.java` lines 15-21

Three Spring Data derived query methods are defined but never called from any service, controller, or test:

- `deleteByPluginIdAndObjectTypeAndObjectId` -- The service uses `findBy...` + `delete(entity)` instead
- `findByPluginIdAndObjectTypeAndEntityTypeAndEntityId` -- The service uses Specification-based queries
- `findByPluginIdAndEntityTypeAndEntityId` -- The service uses Specification-based queries

**Impact**: Dead code. Spring Data will generate implementations at startup that are never invoked. This contradicts the project's own `minimal-implementation.md` standard ("clear purpose for every method").

**Recommendation**: Remove all three unused methods. The service correctly uses `JpaSpecificationExecutor.findAll(spec)` for entity-filtered queries and `delete(entity)` for deletions. Only `findByPluginIdAndObjectType` and `findByPluginIdAndObjectTypeAndObjectId` are actually called.

---

### M2: Plugin SDK IIFE Bundle Out of Sync (Medium)

**Evidence**: `src/main/resources/static/assets/plugin-sdk.js`

The IIFE bundle does NOT contain any entity binding changes. Searching for `listByEntity`, `entityType`, or `entityId` in the bundle yields zero matches. The `objects.list` signature in the bundle is still `list(e){return a("objectsList",{objectType:e},m())}` -- the old 1-argument version with no options parameter.

The source module (`this-plugin.ts`) has the correct updated signatures, and the warehouse plugin `sdk.ts` type declarations are updated, but the actual runtime bundle that plugins load via `<script>` tag is stale.

**Impact**: Any plugin loading the SDK via the IIFE bundle (the standard pattern per `plugins/CLAUDE.md`) will NOT have access to entity binding or JSONB filtering. The `objects.list(type, options)`, `objects.listByEntity()`, and `objects.save(type, id, data, options)` methods will silently ignore the options parameter or not exist.

**Recommendation**: Regenerate `plugin-sdk.js` from the source module. The spec (Requirement 14) explicitly calls for this: "Update plugin-sdk.js to match the source module changes." This appears to have been missed.

---

### M3: Duplicated Filter Parsing Logic (Medium)

**Evidence**:
- `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectSpecification.java` (78 lines)
- `src/main/java/pl/devstyle/aj/product/PluginDataSpecification.java` (88 lines)

These two Specification classes share ~70% identical logic: same IDENTIFIER_PATTERN regex, same operator validation regex, same `toPredicate` switch structure with identical operator handling (eq, gt, lt, exists, bool), same SQL quote escaping pattern. The only difference is:
- PluginDataSpecification uses 4-segment format (`pluginId:jsonPath:operator:value`) targeting `plugin_data->'pluginId'->>'jsonPath'`
- PluginObjectSpecification uses 3-segment format (`jsonPath:operator:value`) targeting `data->>'jsonPath'`

**Impact**: At pre-alpha scale, this is acceptable and the spec acknowledges it ("adapt the parsing pattern"). However, as more entity types get JSONB filtering, this pattern will repeat. The duplication is noted but not critical at this stage.

**Recommendation**: For now, acceptable. If a third JSONB-filtered entity appears, extract the shared operator logic into a common utility (e.g., a `JsonbFilterOperator` enum or utility class that generates SQL fragments from a column expression + operator + value). Do not preemptively abstract today.

---

### L1: Plugin SDK Documentation Not Updated (Low)

**Evidence**: `plugins/CLAUDE.md` -- the SDK API section under "Custom objects" still shows the old signatures:

```typescript
await thisPlugin.objects.list(objectType)
await thisPlugin.objects.save(objectType, objectId, data)
```

These do not reflect the new optional `options` parameter or the `listByEntity` method.

**Impact**: Developers following the documentation will not discover entity binding capabilities. Low severity because this is internal developer documentation for a pre-alpha project.

**Recommendation**: Update the Custom Objects section in `plugins/CLAUDE.md` to show `options` parameters and the new `listByEntity` method.

---

## Developer Experience Assessment

**Overall DX**: Good

| Dimension | Rating | Notes |
|-----------|--------|-------|
| API discoverability | Good | Query params are intuitive (`entityType`, `entityId`, `filter`) |
| Backward compatibility | Excellent | All existing calls continue to work without changes |
| Error messages | Good | Cross-type endpoint returns clear 400 error when params missing |
| Test coverage | Good | 12 focused tests covering save, list, entity isolation, JSONB filter, combined filter, 400 validation |
| Pattern consistency | Good | Follows existing service/controller/response patterns exactly |

**One friction point**: The "explicit intent model" for entity binding (save without params clears binding) is well-documented in the spec and tested, but could surprise developers who expect PATCH-like semantics. This is a design decision, not an implementation issue.

---

## Requirements Alignment

All 14 core requirements from the spec are addressed:

| Req | Status | Notes |
|-----|--------|-------|
| 1. EntityType enum | Done | PRODUCT, CATEGORY |
| 2. Entity binding columns | Done | Nullable entityType + entityId on PluginObject |
| 3. Liquibase migration 007 | Done | Columns + composite index + rollback |
| 4. PluginObjectResponse expansion | Done | entityType + entityId fields |
| 5. Save with entity binding | Done | Query params, clear-to-null semantics |
| 6. List with entity filter | Done | Optional query params |
| 7. Cross-type list by entity | Done | GET /api/plugins/{pluginId}/objects with 400 validation |
| 8. JSONB data filtering | Done | 3-segment parsing, 5 operators |
| 9. SDK objects.list update | Done | Options parameter in this-plugin.ts |
| 9b. SDK objects.listByEntity | Done | New method in this-plugin.ts |
| 10. SDK objects.save update | Done | Options parameter in this-plugin.ts |
| 11. Host message handler | Done | Query string building for all 3 message types |
| 12. Warehouse SDK types | Done | Updated sdk.ts |
| 13. Plugin SDK types | Done | entityType/entityId on PluginObject interface |
| 14. IIFE bundle update | **NOT DONE** | plugin-sdk.js is stale (see M2) |

**No requirement inflation detected.** The implementation does exactly what was specified, nothing more.

---

## Context Consistency

**Contradictory patterns**: None detected. The implementation consistently uses Specification-based queries for dynamic filtering and derived query methods for simple lookups.

**Dead code**: 3 unused repository methods (see M1). These appear to have been created as "helpful derived queries" but the service took a different (better) approach using Specifications.

**Abandoned patterns**: None.

**Inconsistent error handling**: None. The cross-type endpoint correctly throws `IllegalArgumentException` which maps to 400 via the existing `GlobalExceptionHandler`.

---

## Recommended Simplifications (Priority Order)

### 1. Remove unused repository methods (Effort: 5 minutes)

**Before**: 3 unused derived query methods in PluginObjectRepository
**After**: Only the 2 methods actually called remain
**Impact**: Removes dead code, aligns with minimal-implementation standard

### 2. Update plugin-sdk.js IIFE bundle (Effort: 15 minutes)

**Before**: Bundle missing all entity binding features
**After**: Bundle matches source module, plugins can use entity binding
**Impact**: Feature is actually usable by plugins loaded via script tag

### 3. Update plugins/CLAUDE.md SDK documentation (Effort: 10 minutes)

**Before**: Old signatures without options params
**After**: Current signatures with entity binding examples
**Impact**: Developer discoverability

---

## Summary Statistics

| Metric | Value |
|--------|-------|
| New Java files | 3 (EntityType.java, PluginObjectSpecification.java, 2 test files) |
| Modified Java files | 4 (PluginObject, Repository, Service, Controller, Response) |
| New/modified TS files | 5 (types.ts, this-plugin.ts, PluginMessageHandler.ts, warehouse sdk.ts, plugin-sdk.js) |
| Total new Java LOC | ~483 (production) + ~275 (tests) |
| New integration tests | 12 |
| New dependencies | 0 |
| New config files | 1 (migration YAML) |
| Unused code found | 3 repository methods |
| Critical issues | 0 |
| Over-engineering patterns | 0 |

---

## Conclusion

This is a well-proportioned implementation for a pre-alpha platform. The code is simple, follows existing patterns, introduces no unnecessary abstractions, and solves exactly the stated requirements. The JPA Specification pattern is the right tool for dynamic JSONB filtering -- it avoids both string concatenation SQL and over-abstracted query builders.

**Action items** (in priority order):

1. **Fix M2**: Update `plugin-sdk.js` IIFE bundle -- this is a functional gap where the feature is implemented server-side and in the source module but not in the runtime bundle that plugins actually load. Estimated effort: 15 minutes.
2. **Fix M1**: Remove 3 unused repository methods (`deleteByPluginIdAndObjectTypeAndObjectId`, `findByPluginIdAndObjectTypeAndEntityTypeAndEntityId`, `findByPluginIdAndEntityTypeAndEntityId`). Estimated effort: 5 minutes.
3. **Fix L1**: Update `plugins/CLAUDE.md` SDK documentation to reflect new signatures. Estimated effort: 10 minutes.
4. **Defer M3**: The duplicated filter parsing logic is acceptable at pre-alpha scale. Revisit if a third JSONB-filtered entity appears.
