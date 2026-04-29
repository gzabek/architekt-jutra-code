# Development Roadmap

## Status: Pre-Alpha — Active Scaffolding

The core platform infrastructure is in place. The current focus is completing the plugin framework and expanding MCP/AI capabilities.

---

## Completed

### Core Infrastructure
- [x] Spring Boot 4.0.5 + Java 25 baseline
- [x] PostgreSQL 18 with Liquibase migrations (10 tables)
- [x] JPA + jOOQ dual-stack persistence
- [x] Liquibase code generation via Testcontainers
- [x] React 19 + TypeScript SPA served from Spring Boot JAR
- [x] Chakra UI component library integration
- [x] `frontend-maven-plugin` build pipeline (Node.js v22.14.0)

### Domain Logic
- [x] Category CRUD (`/api/categories`)
- [x] Product CRUD (`/api/products`) with jOOQ read service
- [x] User entity with permission model (`READ`, `EDIT`, `PLUGIN_MANAGEMENT`)

### Security
- [x] Custom JWT authentication (`JwtAuthenticationFilter`, JJWT 0.12.6)
- [x] Centralized `SecurityConfiguration` (no `@PreAuthorize`)
- [x] Custom OAuth2 Authorization Server (`core/oauth2/`)
  - [x] Authorization code flow with PKCE (S256)
  - [x] Refresh token grant
  - [x] RFC 7662 token introspection (`POST /oauth2/introspect`)
  - [x] RFC 8693 token exchange (`urn:ietf:params:oauth:grant-type:token-exchange`)
  - [x] RFC 8414 server metadata (`/.well-known/oauth-authorization-server`)
  - [x] Dynamic client registration
  - [x] `oauth2_registered_clients` table + `DatabaseRegisteredClientRepository`

### MCP Server
- [x] Independent `aj-mcp` Spring Boot application (port 8081)
- [x] MCP SDK 0.18.1 integration (Spring WebMVC transport)
- [x] Category and Product MCP tools
- [x] RFC 7662 introspection filter (`McpIntrospectionFilter`)
- [x] RFC 8693 token exchange client (`TokenExchangeClient`)
- [x] Scope mapping: `mcp:read` → `PERMISSION_READ`, `mcp:edit` → `PERMISSION_EDIT`
- [x] Registered in `opencode.json` as remote MCP server

### Plugin Architecture
- [x] `PluginDescriptor` entity + registry (`plugins` table)
- [x] `PluginObject` entity + API (`plugin_objects` table)
- [x] Frontend plugin embedding via iframes (`PluginFrame`, `PluginContext`)
- [x] `postMessage`-based Plugin SDK (`plugin-sdk/` built as library)
- [x] Plugin authentication standard (host app token forwarding)

### AI / LLM
- [x] `productvalidation` package — LLM-powered product validation plugin
- [x] LiteLLM proxy integration (port 4000, model `claude-haiku-4.5`)
- [x] Docker Compose AI stack: LiteLLM, LangFuse, Presidio, ClickHouse, MinIO, Redis

### Testing
- [x] Testcontainers integration test infrastructure (real PostgreSQL 18)
- [x] `SecurityMockMvcConfiguration` for Spring Boot 4 / Security 7 test integration
- [x] Custom security test annotations (`@WithMockAdminUser`, `@WithMockEditUser`)
- [x] 30 integration test files across all packages
- [x] Vitest + Testing Library frontend test setup

---

## In Progress / Near-Term

### Plugin Framework Maturity
- [ ] External plugin loading (plugins as independently deployable JS bundles from `plugins/` directory)
- [ ] Plugin lifecycle management (install, enable, disable, uninstall)
- [ ] Plugin permission scoping (plugins request specific permissions at registration)

### MCP Expansion
- [ ] Additional MCP tools (users, plugins, plugin objects)
- [ ] MCP tool for product validation (LLM action exposed to AI agents)
- [ ] MCP resource endpoints (read-only data as MCP resources)

### Security Hardening
- [ ] OAuth2 PKCE enforcement on all public clients
- [ ] Token rotation on refresh
- [ ] Client secret rotation endpoint

---

## Future / Planned

### Deployment
- [ ] Dockerfile for main application
- [ ] Dockerfile for `mcp-server`
- [ ] Docker Compose production profile (without dev AI stack)
- [ ] GitHub Actions CI pipeline (build, test, push image)

### Multi-Tenancy
- [ ] Tenant isolation strategy (schema-per-tenant or row-level security)
- [ ] Plugin data scoping per tenant

### Plugin Ecosystem
- [ ] Plugin marketplace / discovery
- [ ] Plugin SDK npm package (public distribution)
- [ ] Plugin sandboxing improvements (CSP, permissions policy)

### Observability
- [ ] Structured logging with correlation IDs
- [ ] Distributed tracing (OpenTelemetry)
- [ ] Metrics endpoint expansion

---

*Last Updated*: 2026-04-26
