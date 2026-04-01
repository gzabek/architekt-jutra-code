# Code Review Report

**Date**: 2026-03-28
**Path**: /Users/kuba/Projects/dna_ai/code (microkernel skeleton application)
**Scope**: all (quality, security, performance, best practices)
**Status**: Warning -- Issues Found

## Summary
- **Critical**: 1 issue
- **Warnings**: 5 issues
- **Info**: 4 issues

---

## Critical Issues

### C1. Hardcoded database credentials in compose.yml

- **Location**: `compose.yml:8-9`
- **Category**: Security
- **Description**: Database username and password are hardcoded as plaintext values (`POSTGRES_USER: aj`, `POSTGRES_PASSWORD: aj`). While this is a development-only Docker Compose file, the trivially simple password ("aj") sets a poor precedent. If this compose file is ever used in a staging or production-like environment, it exposes the database with weak credentials.
- **Risk**: Credential leakage if compose.yml is reused beyond local development. Weak password is trivially guessable.
- **Recommendation**: Use environment variable interpolation (`${POSTGRES_PASSWORD:-aj}`) so the default is only used in local dev, and document that production must override. Alternatively, use a `.env` file (already gitignored) for credentials.
- **Fixable**: true

---

## Warnings

### W1. Potential NullPointerException in PluginRegistry.findById

- **Location**: `src/main/java/pl/devstyle/aj/core/plugin/PluginRegistry.java:23`
- **Category**: Quality / Best Practices
- **Description**: The `findById` method calls `p.getPluginId().equals(pluginId)` which will throw a NullPointerException if either `getPluginId()` returns null or if `pluginId` parameter is null. The method should be defensive against null inputs.
- **Recommendation**: Use `Objects.equals(p.getPluginId(), pluginId)` or reverse the comparison to `pluginId.equals(p.getPluginId())` with a null guard on the parameter. Consider adding a fail-fast null check per the project's error-handling standard.
- **Fixable**: true

### W2. SPA forwarding regex may intercept API paths or static assets unexpectedly

- **Location**: `src/main/java/pl/devstyle/aj/api/SpaForwardController.java:9-17`
- **Category**: Quality / Security
- **Description**: The SPA forwarding controller uses broad patterns (`/{path:[^\\.]*}` and `/**/{path:[^\\.]*}`) that match any path without a dot. This relies on Spring MVC's path matching order to ensure `/api/**` routes take precedence. While tests verify this works today, the pattern is fragile: adding new API endpoints in different controllers could cause routing conflicts, and the `/**` nested pattern is extremely permissive. There is no explicit exclusion of `/api/**` paths.
- **Recommendation**: Add explicit exclusion for `/api` paths in the SPA controller patterns, or use a more targeted approach such as a `WebMvcConfigurer` with a `ResourceHandlerRegistry` or a filter-based SPA fallback. This makes the routing intent explicit rather than relying on implicit ordering.
- **Fixable**: true

### W3. Raw type usage for PostgreSQLContainer in test configuration

- **Location**: `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java:14`
- **Category**: Quality
- **Description**: `PostgreSQLContainer` is used as a raw type without its generic parameter (`PostgreSQLContainer<?>` or `PostgreSQLContainer<? extends PostgreSQLContainer<?>>`). This produces a compiler warning and is considered poor practice with Testcontainers APIs.
- **Recommendation**: Use `PostgreSQLContainer<?>` as the return type to suppress raw type warnings.
- **Fixable**: true

### W4. Missing null-safety validation on PluginRegistry constructor input

- **Location**: `src/main/java/pl/devstyle/aj/core/plugin/PluginRegistry.java:14`
- **Category**: Quality
- **Description**: The constructor accepts `List<Plugin> plugins` without any null check. Per the project's fail-fast validation standard (`.maister/docs/standards/global/validation.md` and `error-handling.md`), inputs should be validated early. If Spring injects null (unlikely but possible with configuration errors), the registry will fail later with an unclear NPE.
- **Recommendation**: Add `Objects.requireNonNull(plugins, "plugins must not be null")` in the constructor.
- **Fixable**: true

### W5. getExtensions method is a stub returning hardcoded empty list

- **Location**: `src/main/java/pl/devstyle/aj/core/plugin/PluginRegistry.java:28-30`
- **Category**: Quality
- **Description**: The `getExtensions(Class<T> type)` method always returns `List.of()` regardless of input. While this is expected for a skeleton application, the method provides no indication that it is unimplemented. Per the minimal-implementation standard, every method should have a clear purpose -- a permanent stub risks being forgotten.
- **Recommendation**: Either remove the method until it is needed (YAGNI), or add a brief comment indicating it is a deliberate placeholder for the next implementation phase. The test (`getExtensionsReturnsEmptyListForAnyExtensionPointType`) currently tests stub behavior rather than real logic.
- **Fixable**: false (design decision)

---

## Informational

### I1. Health endpoint does not use Spring Boot Actuator

- **Location**: `src/main/java/pl/devstyle/aj/api/HealthController.java:13-16`
- **Category**: Best Practices
- **Description**: A custom `/api/health` endpoint returns a hardcoded `{"status": "UP"}` map. Spring Boot Actuator provides a robust health endpoint with auto-configured health indicators (database, disk space, etc.) out of the box. The current implementation will always report "UP" even if the database is down.
- **Suggestion**: This is likely intentional for the skeleton phase to avoid adding Actuator as a dependency. When the project matures, consider replacing this with Spring Boot Actuator's `/actuator/health` which will automatically check database connectivity, disk space, and other health indicators.

### I2. Empty Liquibase changelog

- **Location**: `src/main/resources/db/changelog/db.changelog-master.yaml:1`
- **Category**: Best Practices
- **Description**: The changelog file contains only `databaseChangeLog: []` (an empty list). This is appropriate for the skeleton phase, but Liquibase will still run on startup and create its tracking tables (DATABASECHANGELOG, DATABASECHANGELOGLOCK), which is validated by `IntegrationTests.liquibaseChangesWereApplied()`.
- **Suggestion**: No action needed now. This is correctly set up for incremental migrations.

### I3. Frontend App.tsx lacks error boundary and accessibility attributes

- **Location**: `src/main/frontend/src/App.tsx:14-17`
- **Category**: Best Practices / Accessibility
- **Description**: The App component renders raw `<pre>` and `<p>` tags without semantic structure, ARIA attributes, or an error boundary. The error state renders `<pre>Error: {error}</pre>` which could display unexpected content from network errors. Per the frontend accessibility standard, semantic HTML and screen reader considerations should be applied.
- **Suggestion**: For a skeleton app this is acceptable. As the frontend grows, wrap with an ErrorBoundary component and add a proper layout with semantic HTML (`<main>`, `<header>`, etc.).

### I4. Duplicate test coverage for health endpoint across test classes

- **Location**: `src/test/java/pl/devstyle/aj/api/ApiLayerTests.java` and `src/test/java/pl/devstyle/aj/IntegrationTests.java`
- **Category**: Quality
- **Description**: The health endpoint is tested in both `ApiLayerTests` (WebMvcTest slice) and `IntegrationTests` (full SpringBootTest). Additionally, `IntegrationTests.apiPathsReturnJsonNotHtmlForward` is nearly identical to `IntegrationTests.healthEndpointReturnsCorrectJsonInFullContext`. While having both slice and integration tests is good practice, the duplicate assertions within `IntegrationTests` add no value.
- **Suggestion**: Consider removing `apiPathsReturnJsonNotHtmlForward` from IntegrationTests since it tests the exact same thing as `healthEndpointReturnsCorrectJsonInFullContext` -- same URL, same assertions, different name.

---

## Metrics

- **Files analyzed**: 18 (6 production Java, 1 application config, 6 test Java, 1 compose, 1 tsx, 1 vite config, 1 pom.xml, 1 .gitignore)
- **Max function length**: 7 lines (IntegrationTests.liquibaseChangesWereApplied)
- **Max nesting depth**: 1 level
- **Potential vulnerabilities**: 1 (hardcoded credentials)
- **N+1 query risks**: 0 (no database queries in application code yet)
- **Code complexity**: Very low -- all methods are short and focused

---

## Prioritized Recommendations

1. **Use environment variable interpolation for database credentials in compose.yml** -- prevents credential leakage if the file is reused beyond local development
2. **Add null-safety to PluginRegistry.findById** -- prevents NPE on null pluginId input, aligns with fail-fast validation standard
3. **Add null-check to PluginRegistry constructor** -- defensive programming per project error-handling standards
4. **Fix raw type warning on PostgreSQLContainer** -- clean compiler output
5. **Decide on getExtensions stub** -- either remove until needed or document as intentional placeholder
6. **Remove duplicate integration test** -- apiPathsReturnJsonNotHtmlForward duplicates healthEndpointReturnsCorrectJsonInFullContext
