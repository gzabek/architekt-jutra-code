# Technology Stack

## Overview
This document describes the technology choices and rationale for **aj** — a plugin-based microkernel platform built on Spring Boot, with an AI-accessible MCP server and a built-in OAuth2 Authorization Server.

---

## Languages

### Java (25)
- **Usage**: All backend source code
- **Rationale**: Enterprise-grade language with strong ecosystem, JVM stability, and Spring Boot compatibility
- **Key Features Used**: Records, sealed classes, pattern matching for `switch`, `var` for local variables, virtual threads available

### TypeScript (~5.9.3)
- **Usage**: All frontend source code
- **Rationale**: Type-safety on top of React; strict mode enforced; errors caught at compile time
- **Key Features Used**: Strict mode, explicit public interfaces, no `any`

---

## Backend Frameworks & Libraries

### Spring Boot (4.0.5)
- **Parent POM**: `spring-boot-starter-parent:4.0.5`
- **Modules Used**:
  - `spring-boot-starter-webmvc` — REST API (blocking WebMVC, not reactive WebFlux)
  - `spring-boot-starter-data-jpa` — JPA/Hibernate ORM abstraction
  - `spring-boot-starter-jooq` — Type-safe SQL query builder (for complex reads)
  - `spring-boot-starter-liquibase` — Schema versioning and migrations
  - `spring-boot-starter-security` — Spring Security filter chain
  - `spring-boot-starter-actuator` — Health and info endpoints
  - `spring-boot-docker-compose` — Dev-time automatic Docker Compose startup

### Spring Security (managed by Boot 4.0.5)
- **Usage**: Stateless JWT authentication + centralized endpoint authorization
- **Pattern**: Custom `SecurityFilterChain`; no `@PreAuthorize` annotations
- **Password Encoding**: BCrypt (`BCryptPasswordEncoder`)
- **Session Management**: Stateless

### Spring Security OAuth2 Authorization Server (managed)
- **Usage**: Provides `RegisteredClient` and related types/interfaces only
- **Note**: The full OAuth2 AS logic is **custom-implemented** in `core/oauth2/` — this library is used for its data model, not its endpoint implementations

### JJWT (0.12.6)
- **Usage**: JWT token generation and parsing for both internal `aj` tokens and OAuth2 tokens
- **Algorithm**: HMAC-SHA (key from `app.jwt.secret`, Base64-encoded)
- **Token Types**:
  - Internal `aj` JWT — claims: `sub` (username), `permissions` (string list), `iat`, `exp`
  - OAuth2 JWT — claims: `sub`, `scopes` (string list), `client_id`, `iat`, `exp`

### Lombok (1.18.38)
- **Usage**: `@Getter`, `@Setter`, `@NoArgsConstructor` on JPA entities; builder patterns on DTOs
- **Restriction**: No `@Data` or `@EqualsAndHashCode` on JPA entities

---

## MCP Server (`mcp-server/`)

An **independent Spring Boot application** (`aj-mcp`) that exposes `aj` data as MCP (Model Context Protocol) tools for AI agents. It is a separate Maven project — built and run independently from the main `aj` application.

### MCP SDK (`io.modelcontextprotocol.sdk:mcp-spring-webmvc:0.18.1`)
- **Purpose**: MCP protocol implementation over Spring WebMVC
- **Pattern**: Spring `@Bean`-registered MCP tools that delegate to `AjApiClient`

### MCP Server Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `mcp-spring-webmvc` | 0.18.1 | MCP protocol (Spring WebMVC transport) |
| `spring-boot-starter-web` | 4.0.5 | REST/WebMVC runtime |
| `spring-boot-starter-security` | 4.0.5 | Security filter chain (token introspection + JWT filters) |
| `spring-boot-starter-actuator` | 4.0.5 | Health endpoint (port 9081) |
| `jackson-databind` + `jackson-datatype-jsr310` | managed | JSON serialization with Java date/time types |
| `json-schema-validator` | 2.0.0 | Test-scoped — JSON schema assertions |

### MCP Server Runtime
- **Port**: 8081 (main), 9081 (actuator)
- **MCP name**: `aj-mcp`, version `1.0.0`
- **Backend URL**: `${AJ_BACKEND_URL:http://localhost:8080}`
- **OAuth2 server URL**: `${AJ_OAUTH_SERVER_URL:http://localhost:8080}` (same host as backend)
- **OAuth2 client ID**: `mcp-server` (seeded by migration `010-seed-mcp-server-oauth2-client.yaml`)

---

## Frontend Framework

### React (19.2.4)
- **Purpose**: SPA built into the Spring Boot fat JAR and served as static resources
- **Router**: react-router-dom v7.13.2
- **Build**: Vite 8.0.1

### Chakra UI (^3.34.0)
- **Purpose**: UI component library; primary source for all UI primitives
- **Rationale**: Accessible, composable, consistent theming via `ChakraProvider`

### Vite (8.0.1)
- **Purpose**: Build tool for both the SPA (`vite build`) and the Plugin SDK library (`vite build -c vite.sdk.config.ts`)
- **Integration**: Bundled into the Spring Boot JAR via `frontend-maven-plugin`

### Plugin SDK (library build)
- **Source**: `src/main/frontend/src/plugin-sdk/`
- **Built as**: ES library via `vite.sdk.config.ts`
- **Purpose**: Exported SDK for external plugins — provides `context`, `host-app`, `messaging`, `this-plugin` APIs

---

## Database

### PostgreSQL (18)
- **Type**: Relational RDBMS
- **Dev instance**: Docker Compose service `postgres`; DB/user/password all `aj`; port `5432`
- **JSONB**: Used for flexible plugin data storage in `plugin_objects.data`

### Spring Data JPA + Hibernate
- **Usage**: CRUD repositories, entity lifecycle, relationship management
- **Provider**: Hibernate (managed by Spring Boot JPA starter)

### jOOQ
- **Usage**: Complex reads, aggregations, authorization queries — never for CRUD
- **Code Generation**: `testcontainers-jooq-codegen-maven-plugin:0.0.4` — spins up a real `postgres:18` container, applies Liquibase migrations, generates DSL to `pl.devstyle.aj.jooq`
- **Pattern**: Read services named `Db*QueryService` (e.g., `DbProductQueryService`)
- **Skip flag**: `-Dskip.jooq.generation=true`

### Liquibase
- **Purpose**: Database schema versioning
- **Changelog root**: `src/main/resources/db/changelog/db.changelog-master.yaml`
- **Strategy**: `includeAll` from `./2026/` — sequential numbered YAML changelogs

### Schema Overview (12 migrations, 10 tables)

| Table | Purpose |
|---|---|
| `categories` | Product categories |
| `products` | Product catalog |
| `plugins` | Registered plugin descriptors |
| `plugin_objects` | Plugin-attached data per entity |
| `users` | User accounts |
| `user_permissions` | User permission assignments |
| `oauth2_registered_clients` | OAuth2 client registry |

---

## Security & Authentication

### Custom JWT Authentication
- **Filter**: `JwtAuthenticationFilter` — `OncePerRequestFilter`, validates Bearer token, sets `SecurityContext`
- **Claims**: `sub` (username), `permissions` (list of `PERMISSION_*` strings)
- **Key source**: `app.jwt.secret` (Base64-encoded HMAC-SHA key in `application.properties`)
- **Expiry**: `app.jwt.expiration-ms`

### Custom OAuth2 Authorization Server
- **Location**: `pl.devstyle.aj.core.oauth2`
- **Supported grant types**: `authorization_code`, `refresh_token`, `urn:ietf:params:oauth:grant-type:token-exchange`
- **Supported scopes**: `mcp:read`, `mcp:edit`
- **PKCE**: S256 code challenge method supported
- **Client storage**: `RegisteredClientEntity` JPA entity → `oauth2_registered_clients` table
- **Key endpoints**:

| Endpoint | Standard | Purpose |
|---|---|---|
| `GET /.well-known/oauth-authorization-server` | RFC 8414 | Server metadata discovery |
| `POST /oauth2/register` | Dynamic client reg | Client registration |
| `GET /oauth2/authorize` | OAuth2 | Authorization code flow |
| `POST /oauth2/token` | OAuth2 + RFC 8693 | Token issuance and token exchange |
| `POST /oauth2/introspect` | RFC 7662 | Token introspection |

### MCP Server Token Flow
1. AI client presents OAuth2 Bearer token (Token-A, issued by `aj` AS)
2. `McpIntrospectionFilter` calls `POST /oauth2/introspect` on main backend — validates Token-A
3. `TokenExchangeClient` exchanges Token-A for an internal `aj` JWT (Token-B) via RFC 8693
4. Token-B (with `permissions` claims) is used by `AjApiClient` to call the backend REST API
5. Scope mapping: `mcp:read` → `PERMISSION_READ`, `mcp:edit` → `PERMISSION_EDIT`

---

## AI / LLM Integration

### LiteLLM Proxy (port 4000)
- **Purpose**: Unified LLM proxy — routes requests to configured AI providers
- **Usage**: Called by `productvalidation` package at `localhost:4000` via OpenAI-compatible `/v1/chat/completions`
- **Model**: `claude-haiku-4.5`
- **Config**: `litellm/config.yaml` + `litellm/.env`

### LangFuse (port 3100)
- **Purpose**: LLM observability and tracing
- **Credentials (dev)**: `admin@aj.local` / `admin123`
- **Backend**: ClickHouse (analytics) + PostgreSQL (port 5433) + Redis (queue) + MinIO (storage)

### Microsoft Presidio
- **Purpose**: PII detection and anonymization in LLM inputs/outputs
- **Services**: `presidio-analyzer` (port 5002), `presidio-anonymizer` (port 5001)

---

## Build Tools

### Maven (with Maven Wrapper)
- **Wrapper**: `mvnw` / `mvnw.cmd` — version-locked builds
- **Key plugins**:

| Plugin | Purpose |
|---|---|
| `spring-boot-maven-plugin` | Fat JAR packaging |
| `frontend-maven-plugin:1.15.1` | Node.js v22.14.0 install + frontend build during `generate-resources` |
| `testcontainers-jooq-codegen-maven-plugin:0.0.4` | jOOQ code generation against real PostgreSQL |
| `maven-compiler-plugin` | Lombok annotation processor |
| `build-helper-maven-plugin` | Adds `target/generated-sources/jooq` to compile path |

### npm (Node.js v22.14.0)
- **Working directory**: `src/main/frontend/`
- **Key scripts**: `dev`, `build`, `lint`, `test`, `test:watch`

---

## Infrastructure

### Docker Compose (`compose.yml`)
Full AI dev stack. Only `postgres` is auto-integrated by Spring Boot; all other services are opt-in.

| Service | Image | Port | Purpose |
|---|---|---|---|
| `postgres` | `postgres:18` | 5432 | Main app database |
| `langfuse-db` | `postgres:18` | 5433 | LangFuse DB |
| `clickhouse` | `clickhouse/clickhouse-server` | 8123/9000 | LangFuse analytics |
| `minio` | `minio/minio` | 9010/9011 | S3 storage for LangFuse |
| `redis` | `redis:7` | 6379 | LangFuse queue |
| `langfuse` | `langfuse/langfuse` | 3100 | LLM observability UI |
| `presidio-analyzer` | `mcr.microsoft.com/presidio-analyzer` | 5002 | PII detection |
| `presidio-anonymizer` | `mcr.microsoft.com/presidio-anonymizer` | 5001 | PII anonymization |
| `litellm-db` | `postgres:18` | 5434 | LiteLLM DB |
| `litellm` | `ghcr.io/berriai/litellm` | 4000 | LLM proxy |

### CI/CD
- Not yet configured (no GitHub Actions, no Dockerfile)

### Hosting
- Not yet determined

---

## Testing Infrastructure

### Backend
- **JUnit 5** (Jupiter) via Spring Boot test starters
- **Spring MockMvc** + **Spring Security Test** — HTTP-level integration tests
- **TestContainers** (`testcontainers-postgresql`) — always real PostgreSQL 18, never mocked
- **AssertJ** — non-HTTP assertions; **Hamcrest** + `jsonPath()` — HTTP response assertions

### Frontend
- **Vitest (^3.2.4)** — test runner (globals mode, jsdom environment)
- **@testing-library/react (^16.3.2)** + **@testing-library/jest-dom (^6.9.1)**
- **ESLint 9.39.4** + typescript-eslint 8.57.0 + react-hooks + react-refresh plugins

---

## Key Dependencies Summary

| Dependency | Version | Purpose |
|---|---|---|
| `spring-boot-starter-parent` | 4.0.5 | BOM + build parent |
| `spring-boot-starter-webmvc` | 4.0.5 | REST API |
| `spring-boot-starter-data-jpa` | 4.0.5 | ORM |
| `spring-boot-starter-jooq` | 4.0.5 | Type-safe SQL |
| `spring-boot-starter-liquibase` | 4.0.5 | DB migrations |
| `spring-boot-starter-security` | 4.0.5 | Security |
| `spring-security-oauth2-authorization-server` | managed | OAuth2 types |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 | JWT tokens |
| `lombok` | 1.18.38 | Code generation |
| `mcp-spring-webmvc` | 0.18.1 | MCP server (aj-mcp only) |
| `postgresql` | runtime | DB driver |
| `spring-boot-testcontainers` | 4.0.5 | Test infra |
| `testcontainers-postgresql` | managed | Test DB container |

---

*Last Updated*: 2026-04-26
