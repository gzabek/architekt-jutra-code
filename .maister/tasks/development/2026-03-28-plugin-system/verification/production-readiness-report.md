# Production Readiness Report

**Date**: 2026-03-28
**Path**: `src/main/java/pl/devstyle/aj/core/plugin/`, `src/main/java/pl/devstyle/aj/product/PluginDataSpecification.java`, `src/main/frontend/src/plugins/`, `src/main/resources/db/changelog/2026/004-006`
**Target**: production (pre-alpha project -- assessed with full rigor, but findings contextualized for pre-alpha stage)
**Status**: Not Ready

## Executive Summary
- **Recommendation**: NO-GO (for production deployment)
- **Overall Readiness**: 35%
- **Deployment Risk**: High
- **Blockers**: 7  Concerns: 8  Recommendations: 4

The plugin system implementation is well-structured for a pre-alpha scaffold. The code follows project standards (BaseEntity, business key equals/hashCode, proper Liquibase migrations with rollbacks, centralized error handling). However, several blockers exist that would prevent safe production deployment: no input validation on any plugin API endpoint, SQL injection surface in PluginDataSpecification, no authentication/authorization, no request size limits on JSONB payloads, and the iframe sandbox includes `allow-same-origin` which weakens isolation. For a pre-alpha internal milestone, most of these are expected gaps -- but they must be resolved before any production or public-facing deployment.

## Category Breakdown
| Category | Score | Status |
|----------|-------|--------|
| Configuration | 50% | Concern |
| Monitoring | 40% | Concern |
| Resilience | 60% | Acceptable |
| Performance | 55% | Concern |
| Security | 15% | Critical |
| Deployment | 70% | Acceptable |

---

## Blockers (Must Fix)

### B1: No Input Validation on Plugin API Endpoints
**Location**: `PluginController.java:28-30`, `PluginDataController.java:31-34`, `PluginObjectController.java:44-48`
**Issue**: All three controllers accept `@RequestBody Map<String, Object>` with zero validation. No `@Valid`, no `@NotNull`, no size constraints. A client can submit arbitrarily large JSON payloads (manifest, plugin data, object data) causing memory exhaustion or storing unbounded JSONB in PostgreSQL.
**Fix**: Add request DTO classes with Jakarta Validation annotations (`@NotNull`, `@Size`). At minimum, enforce max payload size via `spring.servlet.multipart.max-request-size` and `server.tomcat.max-http-form-post-size` in `application.properties`. For JSONB fields, validate depth/size before persisting.

### B2: SQL Injection Surface in PluginDataSpecification
**Location**: `PluginDataSpecification.java:48-78`
**Issue**: The `toPredicate` method constructs raw SQL fragments using string interpolation (`String.format`). While single quotes are escaped via `.replace("'", "''")`, the `pluginId` and `jsonPath` values come from user input (the filter query parameter) and are NOT validated against a restrictive pattern. The comment on line 50 claims "pluginId and jsonPath are validated via parse() regex" but `parse()` only validates the operator -- there is no regex constraint on pluginId or jsonPath content. Characters like `\`, null bytes, or PostgreSQL-specific escape sequences could bypass single-quote escaping.
**Fix**: Add a strict allowlist regex in `parse()` for pluginId and jsonPath, e.g., `^[a-zA-Z0-9._-]+$`. Better yet, use parameterized queries with bind variables instead of string interpolation for all user-supplied values.

### B3: No Authentication or Authorization
**Location**: All controllers in `pl.devstyle.aj.core.plugin`
**Issue**: No Spring Security configuration exists in the project. All plugin management endpoints (upload manifest, delete plugin, enable/disable, read/write plugin data, CRUD plugin objects) are completely open. Any client can register arbitrary plugins, inject data, or delete plugins.
**Fix**: Add Spring Security with at minimum basic authentication. Plugin management endpoints (PUT/DELETE/PATCH) should require admin role. Plugin data read endpoints could be more permissive depending on requirements.

### B4: No Request Size Limits
**Location**: `application.properties` (line 1-2, only `spring.application.name=aj`)
**Issue**: No `server.tomcat.max-http-form-post-size`, no `spring.servlet.multipart.max-request-size`, no `spring.codec.max-in-memory-size`. JSONB payloads for manifests, plugin data, and plugin objects are unbounded. A single PUT request could contain gigabytes of JSON.
**Fix**: Configure request size limits in `application.properties`. Recommended: `server.tomcat.max-http-form-post-size=2MB`, add custom Jackson deserialization limits.

### B5: iframe sandbox includes allow-same-origin
**Location**: `PluginFrame.tsx:31`
**Issue**: The sandbox attribute is `"allow-scripts allow-same-origin allow-forms allow-popups allow-modals allow-downloads"`. The combination of `allow-scripts` and `allow-same-origin` effectively negates the sandbox -- a malicious plugin can remove the sandbox attribute from its own iframe and gain full access to the host page's DOM and cookies. This is a well-documented browser security issue.
**Fix**: If plugins are loaded from the same origin, they MUST be served from a separate origin (subdomain) to make `allow-same-origin` safe. If plugins are always cross-origin, document this requirement and add origin validation before loading. If same-origin plugins are needed, remove `allow-same-origin` and use `postMessage` exclusively for communication (which the code already supports).

### B6: No CORS Configuration
**Location**: Project-wide (no `WebMvcConfigurer` or `@CrossOrigin` or security config found)
**Issue**: No explicit CORS configuration exists. Spring Boot's default is to deny all cross-origin requests, but with no Spring Security the behavior depends on the deployment. Plugin iframes making fetch requests back to the host API will fail or behave unpredictably without explicit CORS policy.
**Fix**: Add explicit CORS configuration via `WebMvcConfigurer.addCorsMappings()` or Spring Security CORS. Define allowed origins precisely -- never use wildcard (`*`) with credentials.

### B7: pluginFetch Proxy Has No URL Allowlist
**Location**: `PluginMessageHandler.ts:42-85`
**Issue**: The `handlePluginFetch` function proxies arbitrary HTTP requests on behalf of plugins. The only validation is rejecting URLs containing `..` (path traversal). A malicious plugin can use this to make requests to internal services, cloud metadata endpoints (e.g., `http://169.254.169.254/`), or localhost services -- a classic Server-Side Request Forgery (SSRF) vector. The fetch runs with the host application's cookies and network access.
**Fix**: Implement a URL allowlist. At minimum: reject private/internal IP ranges, require HTTPS, and restrict to known plugin API base URLs. Consider requiring the URL to match the plugin's registered `pluginUrl` origin.

---

## Concerns (Should Fix)

### C1: No pluginId Format Validation Anywhere
**Location**: `PluginController.java:28`, `PluginDescriptorService.java:20`
**Issue**: The `pluginId` is used as a primary key (VARCHAR 255) and as a JSONB key in product.plugin_data. It arrives as a raw `@PathVariable String` with no validation. Special characters, empty strings, or excessively long IDs would be accepted.
**Fix**: Add `@Pattern(regexp = "^[a-z][a-z0-9._-]{1,100}$")` constraint on pluginId path variables or validate in the service layer.

### C2: Manifest Content Not Validated
**Location**: `PluginDescriptorService.java:20-35`
**Issue**: The `uploadManifest` method blindly casts manifest fields (`name`, `version`, `url`, `description`) from the raw Map. A manifest with `name: 123` (integer) would cause a ClassCastException at runtime. The `url` field is stored without URL format validation -- a `javascript:` or `data:` URL could be loaded in the iframe.
**Fix**: Create a `ManifestRequest` DTO with typed, validated fields. Validate the `url` field is a proper HTTP(S) URL.

### C3: No Health Check Endpoint
**Location**: `application.properties`
**Issue**: Spring Boot Actuator is not included in the dependencies (checked pom.xml). No `/health` or `/healthz` endpoint exists. Container orchestrators (Kubernetes, ECS) cannot determine application health.
**Fix**: Add `spring-boot-starter-actuator` dependency and expose health endpoint. For pre-alpha this is low priority but becomes a blocker before any containerized deployment.

### C4: No Structured Logging Configuration
**Location**: Project-wide
**Issue**: No `logback-spring.xml` or logging configuration beyond defaults. Production deployments typically need JSON-formatted logs for log aggregation (ELK, Datadog, etc.).
**Fix**: Add `logback-spring.xml` with JSON encoder for production profile.

### C5: Missing IllegalArgumentException Handler in GlobalExceptionHandler
**Location**: `GlobalExceptionHandler.java`
**Issue**: `PluginDataSpecification.parse()` throws `IllegalArgumentException` for invalid filter formats, but `GlobalExceptionHandler` does not handle `IllegalArgumentException`. This will result in a 500 Internal Server Error with the generic "unexpected error" message instead of a 400 Bad Request.
**Fix**: Add an `@ExceptionHandler(IllegalArgumentException.class)` returning 400 Bad Request.

### C6: GIN Index on products.plugin_data May Be Premature
**Location**: `005-add-plugin-data-to-products.yaml:13`
**Issue**: A GIN index on the entire `plugin_data` JSONB column is created. For the current use case (keyed access by pluginId), a `jsonb_path_ops` GIN index would be more efficient and smaller. The generic GIN index supports all operators but has higher write overhead.
**Fix**: Consider `CREATE INDEX idx_products_plugin_data_gin ON products USING gin (plugin_data jsonb_path_ops)` if only containment queries (`@>`) are needed. Evaluate actual query patterns before changing.

### C7: No Pagination on List Endpoints
**Location**: `PluginObjectController.java:27-33`, `PluginController.java:36-39`
**Issue**: Both `list()` endpoints return all results without pagination. A plugin with thousands of objects would return them all in a single response.
**Fix**: Add `Pageable` parameter support with sensible defaults (e.g., page size 50, max 200).

### C8: Frontend Message Handler Not Installed at App Level
**Location**: `PluginMessageHandler.ts`
**Issue**: The `createMessageHandler` factory is exported but there is no evidence of it being installed as a global `window.addEventListener("message", ...)` listener. Without this, no plugin messages will be processed. This may be handled in code outside the analyzed path.
**Fix**: Verify the message handler is registered at the application root level (e.g., in App.tsx or a top-level provider).

---

## Recommendations (Nice to Have)

### R1: Add JSONB Schema Validation
Consider validating JSONB content against a schema defined in the plugin manifest. This prevents plugins from storing arbitrary or malformed data.

### R2: Add Plugin Object Data Size Limit at Database Level
Add a `CHECK` constraint on `plugin_objects.data` to limit JSONB size, e.g., `CHECK (pg_column_size(data) < 1048576)` (1 MB). This provides defense-in-depth against oversized payloads.

### R3: Rate Limit Plugin Registration
Plugin upload/manifest endpoints should be rate-limited to prevent abuse, especially once authentication is added.

### R4: Add Database Migration Execution Order Tests
With `includeAll` in the changelog master, migration ordering depends on alphabetical file naming. Add an integration test that verifies all migrations apply cleanly to a fresh database.

---

## Next Steps

**Priority 1 (Security Blockers -- must fix before any non-local deployment)**:
1. Fix SQL injection surface in `PluginDataSpecification` -- add strict regex for pluginId/jsonPath or switch to parameterized queries (B2)
2. Add URL allowlist to `pluginFetch` proxy to prevent SSRF (B7)
3. Resolve `allow-same-origin` + `allow-scripts` iframe sandbox issue (B5)

**Priority 2 (Input Validation -- must fix before staging)**:
4. Add input validation on all plugin API endpoints (B1)
5. Add request size limits in application.properties (B4)
6. Validate pluginId format (C1) and manifest content (C2)

**Priority 3 (Infrastructure -- must fix before production)**:
7. Add Spring Security with authentication/authorization (B3)
8. Add CORS configuration (B6)
9. Add health check endpoint (C3)
10. Add IllegalArgumentException handler (C5)
11. Add pagination to list endpoints (C7)

**Priority 4 (Operational -- before scaling)**:
12. Configure structured logging (C4)
13. Evaluate GIN index strategy (C6)
