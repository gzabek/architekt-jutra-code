# Production Readiness Report

**Date**: 2026-03-28
**Path**: /Users/kuba/Projects/dna_ai/code
**Target**: production (evaluated as pre-alpha skeleton)
**Status**: Not Ready

## Executive Summary
- **Recommendation**: NO-GO (expected for pre-alpha skeleton)
- **Overall Readiness**: 15%
- **Deployment Risk**: Critical
- **Blockers**: 10  Concerns: 8  Recommendations: 4

This is a pre-alpha microkernel skeleton application. Production deployment is explicitly out of scope per project documentation. The application has foundational elements in place (health endpoint, database migration framework, test infrastructure) but lacks the operational infrastructure required for production. This assessment documents what exists and what would need to be added before any production deployment.

## Category Breakdown
| Category | Score | Status |
|----------|-------|--------|
| Configuration | 20% | Not Ready |
| Monitoring | 20% | Not Ready |
| Resilience | 10% | Not Ready |
| Performance | 10% | Not Ready |
| Security | 5% | Not Ready |
| Deployment | 25% | Not Ready |

---

## Blockers (Must Fix Before Production)

### B1. No environment variable documentation
- **Location**: Project root (missing `.env.example`)
- **Issue**: No `.env.example` or equivalent documenting required environment variables. Database credentials are hardcoded in `compose.yml` (user: `aj`, password: `aj`).
- **Fix**: Create `.env.example` listing all required variables (DB host, port, credentials, Spring profiles, etc.)
- **Fixable**: true

### B2. No secrets externalization
- **Location**: `compose.yml` lines 7-9
- **Issue**: Database credentials (`POSTGRES_USER: aj`, `POSTGRES_PASSWORD: aj`) are hardcoded. `application.properties` has no datasource configuration at all -- relies entirely on Docker Compose auto-configuration.
- **Fix**: Use environment variables or Spring Cloud Config / Vault for secrets management. Add `spring.datasource.*` properties referencing `${DB_*}` environment variables.
- **Fixable**: true

### B3. No error tracking integration
- **Location**: `pom.xml`, application-wide
- **Issue**: No Sentry, Bugsnag, or equivalent error tracking dependency or configuration.
- **Fix**: Add error tracking dependency and configure DSN via environment variable.
- **Fixable**: true

### B4. No graceful shutdown configuration
- **Location**: `src/main/resources/application.properties`
- **Issue**: `server.shutdown=graceful` is not configured. Spring Boot supports this natively but it must be explicitly enabled. Plugin lifecycle (`onStart`/`onStop`) has no shutdown hook integration.
- **Fix**: Add `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s` to application properties.
- **Fixable**: true

### B5. No connection pool configuration
- **Location**: `src/main/resources/application.properties`
- **Issue**: No HikariCP pool size configuration. Spring Boot defaults apply (max pool size 10) but this is not explicitly tuned or documented.
- **Fix**: Add `spring.datasource.hikari.maximum-pool-size`, `minimum-idle`, and `connection-timeout` properties.
- **Fixable**: true

### B6. No rate limiting
- **Location**: Application-wide
- **Issue**: No rate limiting on any endpoint. The `/api/health` endpoint and all SPA routes are unprotected.
- **Fix**: Add Spring Boot rate limiting (e.g., Bucket4j, resilience4j rate limiter, or API gateway).
- **Fixable**: false (architecture decision needed)

### B7. No request timeouts configured
- **Location**: `src/main/resources/application.properties`
- **Issue**: No explicit server timeouts, connection timeouts, or read timeouts configured.
- **Fix**: Configure `server.tomcat.connection-timeout`, `spring.mvc.async.request-timeout`, and client-side timeouts for any future external calls.
- **Fixable**: true

### B8. No CORS configuration
- **Location**: Application-wide
- **Issue**: No CORS configuration present. In production with a separate frontend origin, this would need explicit allowlisting. Currently the SPA is served from the same origin, but any API consumers would be blocked or -- worse -- if defaults change, a wildcard could be exposed.
- **Fix**: Add explicit `WebMvcConfigurer.addCorsMappings()` with allowed origins from environment variable.
- **Fixable**: true

### B9. No dependency audit / security scanning
- **Location**: `pom.xml`
- **Issue**: No OWASP dependency-check plugin or equivalent configured. No evidence of vulnerability scanning.
- **Fix**: Add `org.owasp:dependency-check-maven` plugin to build.
- **Fixable**: true

### B10. No HTTPS enforcement
- **Location**: Application-wide
- **Issue**: No Spring Security dependency, no HTTPS redirect, no HSTS headers. Application serves plain HTTP.
- **Fix**: Add `spring-boot-starter-security`, configure HTTPS redirect and security headers.
- **Fixable**: false (requires infrastructure decisions -- TLS termination at load balancer vs application)

---

## Concerns (Should Fix)

### C1. Minimal application.properties
- **Location**: `src/main/resources/application.properties`
- **Issue**: Contains only `spring.application.name=aj`. No logging configuration, no profile-specific properties, no server port declaration.
- **Recommendation**: Add profile-specific configs (`application-prod.properties`), structured logging format, explicit port binding.

### C2. No structured logging
- **Location**: Application-wide
- **Issue**: No logback/log4j2 configuration for JSON-formatted output. Default Spring Boot console logging is not suitable for production log aggregation.
- **Recommendation**: Add `logback-spring.xml` with JSON encoder for production profile.

### C3. No metrics instrumentation
- **Location**: `pom.xml`
- **Issue**: No Spring Boot Actuator, no Micrometer, no Prometheus/StatsD/Datadog dependencies.
- **Recommendation**: Add `spring-boot-starter-actuator` with Micrometer registry.

### C4. Health endpoint is custom, not Actuator-based
- **Location**: `src/main/java/pl/devstyle/aj/api/HealthController.java`
- **Issue**: Custom `/api/health` endpoint returns `{"status": "UP"}` but does not check database connectivity or other dependencies. Spring Boot Actuator provides production-grade health checks with dependency awareness out of the box.
- **Recommendation**: Replace with or supplement using Spring Boot Actuator `/actuator/health` with database and disk space indicators.

### C5. No global exception handler
- **Location**: Application-wide
- **Issue**: No `@ControllerAdvice` or `@ExceptionHandler`. Unhandled exceptions will produce Spring Boot's default error page/JSON, potentially leaking stack traces.
- **Recommendation**: Add a global `@RestControllerAdvice` for API error responses.

### C6. No security headers
- **Location**: Application-wide
- **Issue**: No Spring Security means no default security headers (X-Frame-Options, X-Content-Type-Options, Content-Security-Policy, etc.).
- **Recommendation**: Add Spring Security or manual filter for security headers.

### C7. No request size limits
- **Location**: Application-wide
- **Issue**: No explicit `server.tomcat.max-http-form-post-size` or `spring.servlet.multipart.max-file-size` configuration.
- **Recommendation**: Add explicit request/upload size limits.

### C8. Empty Liquibase changelog
- **Location**: `src/main/resources/db/changelog/db.changelog-master.yaml`
- **Issue**: Changelog is empty (`databaseChangeLog: []`). No schema migrations exist. For skeleton this is expected, but for production there would be no rollback migrations either.
- **Recommendation**: Establish migration patterns (including rollback) as schema evolves.

---

## Recommendations (Nice to Have)

### R1. Add Spring Boot Actuator
Adding `spring-boot-starter-actuator` would provide health checks, metrics, environment info, and readiness/liveness probes in a single dependency -- addressing C3, C4, and partially B5 (pool metrics).

### R2. Add circuit breaker infrastructure
As plugins may call external services, consider adding `resilience4j` early to establish the pattern for circuit breakers, retries, and bulkheads.

### R3. Plugin lifecycle shutdown integration
`Plugin.onStop()` exists but is never called. When graceful shutdown is added, wire `PluginRegistry` into Spring's `@PreDestroy` or `SmartLifecycle` to invoke `onStop()` on registered plugins.

### R4. Docker/container deployment preparation
No Dockerfile exists. When production deployment approaches, add multi-stage Dockerfile, `.dockerignore`, and health check instruction.

---

## What Is In Place (Skeleton Strengths)

These elements provide a solid foundation for future production readiness:

1. **Health endpoint exists** (`/api/health`) -- basic but functional
2. **Database migration framework** (Liquibase) is wired and tested
3. **TestContainers integration** -- tests run against real PostgreSQL
4. **Comprehensive test coverage** for current scope (unit, slice, integration tests)
5. **Plugin lifecycle hooks** (`onStart`/`onStop`) -- designed for graceful behavior
6. **Immutable collections** in `PluginRegistry` -- defensive programming
7. **SPA forwarding** correctly excludes static assets and API paths
8. **Docker Compose** with PostgreSQL health check for local development

---

## Next Steps

Prioritized action items for moving toward production readiness (when appropriate):

1. **Add Spring Boot Actuator** -- single dependency that addresses health checks, metrics, and readiness probes (high value, low effort)
2. **Configure graceful shutdown** -- two lines in `application.properties` (high value, trivial effort)
3. **Add `@RestControllerAdvice`** -- prevent stack trace leakage (high value, low effort)
4. **Externalize configuration** -- create `.env.example`, add profile-specific properties (medium effort)
5. **Add Spring Security** -- HTTPS enforcement, security headers, CORS (medium effort, requires architecture decisions)
6. **Add structured logging** -- `logback-spring.xml` with JSON encoder (low effort)
7. **Add OWASP dependency-check** -- Maven plugin for vulnerability scanning (low effort)
8. **Add error tracking** -- Sentry or equivalent (low effort once chosen)
9. **Configure connection pool and timeouts** -- explicit HikariCP and Tomcat settings (low effort)
10. **Add rate limiting** -- requires architecture decision on approach (medium effort)

---

*This report reflects the expected state of a pre-alpha skeleton. The NO-GO recommendation is not a criticism but an accurate assessment that production infrastructure has not yet been built -- which aligns with the project's current phase.*
