# Production Readiness Report

**Date**: 2026-03-29
**Path**: `.maister/tasks/development/2026-03-29-custom-object-entity-binding`
**Target**: production (pre-alpha platform -- adjusted expectations)
**Status**: With Concerns

## Executive Summary
- **Recommendation**: GO WITH MITIGATIONS
- **Overall Readiness**: 72%
- **Deployment Risk**: Medium
- **Blockers**: 0  Concerns: 6  Recommendations: 4

This implementation adds entity binding to PluginObject, JSONB data filtering, cross-type entity query endpoint, and plugin SDK updates. The code is well-structured, follows project standards, has good test coverage (21 new tests, all passing), and uses proper transactional boundaries. No blockers were identified. The concerns are primarily platform-level infrastructure gaps that predate this feature and are expected for a pre-alpha project.

## Category Breakdown
| Category | Score | Status |
|----------|-------|--------|
| Configuration | 80% | Adequate |
| Monitoring | 60% | Needs Attention |
| Resilience | 75% | Adequate |
| Performance | 65% | Needs Attention |
| Security | 70% | Adequate |
| Deployment | 85% | Good |

## Blockers (Must Fix)

None identified. All checks passed at a level appropriate for pre-alpha deployment.

## Concerns (Should Fix)

### C1: No Pagination on List Endpoints
- **Location**: `PluginObjectController.java` (lines 27-51), `PluginObjectService.java`
- **Issue**: Both `listByEntity()` and `list()` endpoints return unbounded `List<PluginObjectResponse>`. With no pagination, a plugin with thousands of objects for a given entity could return very large payloads.
- **Category**: Performance
- **Recommendation**: Add `Pageable` support to list endpoints. At minimum, add a default result limit (e.g., 1000) to prevent unbounded queries. This becomes important as plugin adoption grows.

### C2: No Input Length Validation on Path Variables
- **Location**: `PluginObjectController.java` -- `pluginId`, `objectType`, `objectId` are all unbounded `String` path variables
- **Issue**: No `@Size` or length validation on path variables. Extremely long strings could be passed to database queries.
- **Category**: Security
- **Recommendation**: Add `@Size(max=255)` or equivalent validation on path variable parameters to match the column length constraints defined in the entity (`length = 255`).

### C3: JSONB Filter SQL Uses String Interpolation
- **Location**: `PluginObjectSpecification.java` (lines 55-74)
- **Issue**: While the `jsonPath` is validated via `IDENTIFIER_PATTERN` regex and values are escaped with single-quote replacement, the SQL fragments are built via `String.format()` inline concatenation rather than parameterized queries. The `IDENTIFIER_PATTERN` regex (`^[a-zA-Z0-9_.-]+$`) provides reasonable protection, and the existing `PluginDataSpecification.java` uses the same pattern, so this is consistent with the codebase. However, parameterized queries would be safer.
- **Category**: Security
- **Recommendation**: Consider migrating to parameterized native queries or Hibernate `function()` calls in a future iteration to eliminate string interpolation entirely. This is a shared concern with `PluginDataSpecification.java`.

### C4: Missing .env.example / Configuration Documentation
- **Location**: Project root -- no `.env.example` file exists
- **Issue**: No documented environment variables. `application.properties` contains only `spring.application.name=aj`. Database connection is presumably auto-configured via `compose.yml` or Spring Boot defaults but this is not documented.
- **Category**: Configuration
- **Recommendation**: Create a `.env.example` or document expected environment variables (database URL, credentials, etc.) for deployment environments beyond Docker Compose.

### C5: Health Check Does Not Verify Dependencies
- **Location**: `HealthController.java`
- **Issue**: The `/api/health` endpoint returns a static `{"status": "UP"}` without checking database connectivity or other dependencies. This means the health check will report UP even if the database is down.
- **Category**: Monitoring
- **Recommendation**: Use Spring Boot Actuator's built-in health indicators or add a database ping to the health check. This is a platform-level concern, not specific to this feature.

### C6: No Error Tracking Integration (Sentry/Bugsnag)
- **Location**: Platform-wide
- **Issue**: No error tracking service is integrated. The `GlobalExceptionHandler` logs unexpected errors via SLF4J but there is no external error aggregation.
- **Category**: Monitoring
- **Recommendation**: Add Sentry or similar error tracking before production use. Acceptable for pre-alpha.

## Recommendations (Nice to Have)

### R1: Add Structured Logging for Entity Binding Operations
- **Location**: `PluginObjectService.java`
- **Issue**: No logging for save/list operations with entity binding. When debugging cross-plugin data issues, logs showing which entities are being queried/bound would help.
- **Recommendation**: Add debug-level structured logging for entity binding save and cross-type list operations.

### R2: Consider Validation for entityType Without entityId
- **Location**: `PluginObjectController.java` save endpoint (line 61-70)
- **Issue**: The save endpoint accepts `entityType` without `entityId` and vice versa. The service layer sets both fields as-is, allowing partial entity binding (e.g., entityType=PRODUCT with entityId=null). The cross-type list endpoint validates both are present, but save does not.
- **Recommendation**: Consider adding validation that if either `entityType` or `entityId` is provided, both must be provided. This prevents inconsistent data.

### R3: Document the Explicit Intent Model for Entity Binding
- **Location**: API documentation / Plugin SDK documentation
- **Issue**: The "save without entity params clears binding" behavior is a design choice that could surprise developers. It is documented in the spec but not in user-facing SDK docs.
- **Recommendation**: Add a note to `plugins/CLAUDE.md` SDK documentation explaining the explicit intent model.

### R4: Consider Index on (entity_type, entity_id) Without plugin_id
- **Location**: Migration `007-add-entity-binding-to-plugin-objects.yaml`
- **Issue**: The composite index is on `(plugin_id, object_type, entity_type, entity_id)`. Queries that search across all plugins for a given entity would not benefit from this index. Currently no such query exists, so this is future-proofing.
- **Recommendation**: Only add if cross-plugin entity queries become a requirement.

## Feature-Specific Assessment

### Entity Binding Implementation
- EntityType enum with `@Enumerated(EnumType.STRING)` -- follows standards
- Nullable columns for backward compatibility -- correct
- Business key equality unchanged (pluginId, objectType, objectId) -- correct design decision
- Explicit intent model (save without params clears binding) -- well-tested

### JSONB Filtering
- 3-segment parse format adapted from 4-segment PluginDataSpecification -- consistent
- Input validation via IDENTIFIER_PATTERN regex -- adequate
- Operator set (eq, gt, lt, exists, bool) -- complete and matching existing pattern
- SQL fragment construction via string interpolation -- see Concern C3

### Cross-Type Endpoint
- 400 Bad Request when entityType/entityId missing -- correct
- Uses GlobalExceptionHandler IllegalArgumentException mapping -- proper pattern
- No path conflict with existing `/{objectType}` endpoint -- verified

### SDK Consistency
- `this-plugin.ts`, `PluginMessageHandler.ts`, `plugin-sdk.js`, `types.ts`, `warehouse/sdk.ts` all have matching signatures -- verified
- Backward compatible: existing calls without options continue to work -- verified
- New `objectsListByEntity` message type properly routed -- verified

### Test Coverage
- 21 new tests covering entity binding, API endpoints, JSONB filtering, edge cases
- 6 existing regression tests passing
- Tests cover: save with/without binding, entity isolation, cross-type queries, JSONB operators (eq, gt, lt, exists, bool), invalid filter format, combined filters

### Migration
- Reversible with explicit rollback (dropIndex + dropColumn) -- follows standards
- Nullable columns only -- zero-downtime compatible
- Composite index on frequently queried columns -- appropriate

## Next Steps

1. **Before first real production deployment** (not needed for pre-alpha):
   - Add pagination to list endpoints (C1)
   - Add path variable length validation (C2)
   - Enhance health check with dependency verification (C5)
   - Add error tracking integration (C6)

2. **Before plugin developer adoption**:
   - Document explicit intent model in SDK docs (R3)
   - Validate partial entity binding on save (R2)

3. **Future iteration**:
   - Migrate JSONB filter to parameterized queries (C3)
   - Add structured logging for entity operations (R1)
