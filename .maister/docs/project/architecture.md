# System Architecture

## Overview
**aj** is a plugin-based microkernel platform built on Spring Boot 4.0.5 with Java 25. The system implements a microkernel pattern where a minimal core hosts externally-loaded domain plugins. It includes a custom OAuth2 Authorization Server, a standalone MCP server for AI agent access, and an LLM-powered plugin for AI-assisted features.

---

## Architecture Pattern: Microkernel (Plugin-Based)

The system separates a minimal **core** (lifecycle, auth, plugin registry, shared infrastructure) from **plugins** that deliver domain-specific features. Plugins run as sandboxed iframes in the frontend and communicate with the host app via the Plugin SDK over `postMessage`.

```
┌────────────────────────────────────────────────────────────────┐
│                      aj Core (Spring Boot)                     │
│                                                                │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │   REST API  │  │  OAuth2 AS   │  │  Plugin Registry   │   │
│  │ /api/...    │  │ /oauth2/...  │  │  /api/plugins/...  │   │
│  └─────────────┘  └──────────────┘  └────────────────────┘   │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                   Domain Packages                        │  │
│  │   category/   product/   user/   productvalidation/     │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌───────────────────────────────────────────────────────┐    │
│  │              PostgreSQL 18 (JPA + jOOQ)               │    │
│  └───────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────┘
         ▲                              ▲
         │ REST (JWT Bearer)            │ MCP protocol
         │                             │ (OAuth2 Bearer → Token Exchange)
  ┌──────┴──────┐               ┌──────┴──────┐
  │   Browser   │               │  MCP Server │
  │  React SPA  │               │  aj-mcp     │
  │  + Plugins  │               │  port 8081  │
  └─────────────┘               └─────────────┘
                                       ▲
                                       │ MCP protocol
                                 AI Agent / opencode
```

---

## Application Core

### Entry Point
- **File**: `src/main/java/pl/devstyle/aj/AjApplication.java`
- **Port**: 8080

### Package Structure (actual)

```
pl.devstyle.aj
├── AjApplication.java
├── api/
│   ├── AuthController.java          — POST /api/auth/login (JWT issuance)
│   ├── HealthController.java        — GET /api/health
│   └── SpaForwardController.java    — forwards non-API paths to index.html
├── core/
│   ├── BaseEntity.java              — @MappedSuperclass (id, createdAt, @Version updatedAt)
│   ├── error/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── EntityNotFoundException.java
│   │   ├── BusinessConflictException.java
│   │   └── ErrorResponse.java
│   ├── security/
│   │   ├── SecurityConfiguration.java   — StatelessFilterChain, BCrypt, centralized rules
│   │   ├── JwtTokenProvider.java        — JJWT 0.12.6 token generation + parsing
│   │   ├── JwtAuthenticationFilter.java — OncePerRequestFilter, sets SecurityContext
│   │   └── CustomUserDetailsService.java
│   ├── oauth2/                      — Custom OAuth2 Authorization Server (see below)
│   └── plugin/                      — Plugin microkernel core (see below)
├── user/
│   ├── User.java                    — JPA entity with @ElementCollection permissions
│   └── UserRepository.java
├── category/                        — Category bounded context (CRUD)
├── product/                         — Product bounded context (CRUD + jOOQ)
└── productvalidation/               — LLM-powered product validation plugin
```

---

## Security Architecture

### Authentication: Custom JWT

All main application endpoints are protected by a stateless JWT filter chain.

```
Request
  └─► JwtAuthenticationFilter
        └─► Validates Bearer token (HMAC-SHA, JJWT 0.12.6)
              └─► Reads "permissions" claim
                    └─► Sets SecurityContext with GrantedAuthorities
```

**Permission model** (`Permission` enum on `User`):

| Permission | Controls |
|---|---|
| `PERMISSION_READ` | `GET /api/categories/**`, `GET /api/products/**` |
| `PERMISSION_EDIT` | `POST/PUT/DELETE /api/categories/**`, `POST/PUT/DELETE /api/products/**` |
| `PERMISSION_PLUGIN_MANAGEMENT` | `PUT/PATCH/DELETE /api/plugins/**` |
| `PERMISSION_mcp:read` | `GET` reads (from MCP token exchange) |
| `PERMISSION_mcp:edit` | Writes (from MCP token exchange) |

### Authorization Server: Custom OAuth2 AS

`pl.devstyle.aj.core.oauth2` implements a full OAuth2 Authorization Server (not delegated to Spring Authorization Server framework):

| Endpoint | Standard | Handler |
|---|---|---|
| `GET /.well-known/oauth-authorization-server` | RFC 8414 | `OAuth2MetadataController` |
| `POST /oauth2/register` | Dynamic client reg | `PublicClientRegistrationFilter` |
| `GET /oauth2/authorize` | OAuth2 auth code | `OAuth2AuthorizationFilter` |
| `POST /oauth2/token` | OAuth2 + RFC 8693 | `OAuth2TokenFilter` |
| `POST /oauth2/introspect` | RFC 7662 | `OAuth2IntrospectionFilter` |
| `GET /api/oauth2/client-info` | Internal | `OAuth2MetadataController` |

**Grant types**: `authorization_code`, `refresh_token`, `urn:ietf:params:oauth:grant-type:token-exchange`
**Scopes**: `mcp:read`, `mcp:edit`
**PKCE**: S256
**Client storage**: `oauth2_registered_clients` table via `DatabaseRegisteredClientRepository`

---

## MCP Server (`mcp-server/`)

An **independent Spring Boot application** (`aj-mcp`, port 8081) that bridges AI agents to the `aj` platform via the Model Context Protocol.

### Purpose
- Exposes `aj` data (categories, products) as MCP tools
- Registered in `opencode.json` as a remote MCP server at `http://localhost:8081`
- AI agents (e.g., opencode, Claude Desktop) call the MCP tools; the server translates them to `aj` REST API calls

### MCP Server Package Structure

```
pl.devstyle.aj.mcp
├── AjMcpApplication.java
├── client/
│   ├── AjApiClient.java             — REST client → main backend (uses Token-B)
│   ├── CategoryResponse.java
│   ├── ProductResponse.java
│   └── CreateProductRequest.java
├── config/
│   ├── JacksonConfig.java
│   ├── RestClientConfig.java
│   └── SecurityConfig.java
├── controller/
│   └── WellKnownController.java     — serves /.well-known/ OAuth2 metadata
├── exception/
│   └── McpToolException.java
├── security/
│   ├── AccessTokenHolder.java
│   ├── ExchangedTokenHolder.java
│   ├── McpAuthenticationEntryPoint.java
│   ├── McpIntrospectionFilter.java  — RFC 7662: validates incoming Bearer token
│   ├── McpJwtFilter.java
│   └── TokenExchangeClient.java     — RFC 8693: exchanges token for aj JWT
└── service/
    ├── CategoryService.java         — MCP tool implementations
    └── ProductService.java
```

### Token Authentication Flow

```
AI Agent
  └─► presents Token-A (OAuth2 Bearer, issued by aj AS)
        └─► McpIntrospectionFilter
              └─► POST /oauth2/introspect → aj backend (RFC 7662)
                    └─► Token-A valid?
                          └─► TokenExchangeClient
                                └─► POST /oauth2/token (grant=token-exchange, RFC 8693)
                                      └─► receives Token-B (internal aj JWT)
                                            └─► AjApiClient uses Token-B for API calls
                                                  └─► mcp:read → PERMISSION_READ
                                                       mcp:edit → PERMISSION_EDIT
```

---

## Plugin Architecture

### Plugin Microkernel Core (`core/plugin/`)

| Component | Purpose |
|---|---|
| `PluginDescriptor` entity | JPA entity — `plugins` table; stores plugin metadata |
| `PluginObject` entity | JPA entity — `plugin_objects` table; binds arbitrary data to domain entities |
| `PluginDataController` | REST endpoints for plugin data reads |
| `PluginObjectController` | REST CRUD for plugin objects |
| `PluginObjectService` | Plugin object business logic |
| `DbPluginObjectQueryService` | jOOQ read service for complex plugin queries |

### Frontend Plugin Embedding

Plugins run as sandboxed iframes and communicate with the host app over `postMessage`:

```
React Host App
  └─► PluginContext.tsx        — plugin context provider
       └─► PluginFrame.tsx     — <iframe> wrapper for each plugin
             └─► PluginMessageHandler.ts  — postMessage dispatcher
                   └─► iframeRegistry.ts — active iframe registry
                         └─► extensionPoints — plugin mounting points

Plugin (external JS)
  └─► Plugin SDK (vite.sdk.config.ts build)
       ├── context.ts     — plugin context access
       ├── host-app.ts    — host app API calls
       ├── messaging.ts   — postMessage abstraction
       └── this-plugin.ts — plugin self-reference
```

---

## LLM Integration (`productvalidation/`)

An internal plugin that validates product data using an LLM.

```
ProductValidationController
  └─► ProductValidationService
        └─► LiteLLM proxy (localhost:4000)
              └─► /v1/chat/completions (OpenAI-compatible)
                    └─► model: claude-haiku-4.5
```

**Observability**: LangFuse traces all LLM calls (port 3100).
**PII protection**: Presidio analyzer/anonymizer available on the network.

---

## Data Architecture

### Dual ORM Strategy
- **JPA (Hibernate)**: CRUD operations, entity lifecycle, relationship management
- **jOOQ**: Complex reads, aggregations, authorization queries — never for CRUD
- **Rule**: Use JPA for writes; use jOOQ for reads where N+1, projection, or aggregation is needed

### BaseEntity Pattern
```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "base_seq")
    private Long id;

    @CreatedDate
    private LocalDateTime createdAt;

    @Version  // optimistic locking
    private LocalDateTime updatedAt;
}
```

### jOOQ Code Generation
- Plugin: `testcontainers-jooq-codegen-maven-plugin:0.0.4`
- Spins up real `postgres:18` via Testcontainers
- Applies all Liquibase migrations
- Generates DSL to `pl.devstyle.aj.jooq` in `target/generated-sources/jooq`
- Skip: `-Dskip.jooq.generation=true`

---

## Frontend Architecture

### SPA served by Spring Boot
The React SPA is built by `frontend-maven-plugin` during `generate-resources` and included in the fat JAR. `SpaForwardController` forwards all non-API, non-asset paths to `index.html` for client-side routing.

### Frontend Package Structure

```
src/main/frontend/src/
├── api/           — API client modules (fetch wrappers, one per domain)
├── auth/          — Auth guard + auth context
├── components/    — Shared components (AppShell, etc.)
├── hooks/         — Custom hooks
├── pages/         — 11 page components (Login, Categories, Products, Plugins, OAuth2Authorize)
├── plugin-sdk/    — Plugin SDK source (built as library via vite.sdk.config.ts)
├── plugins/       — Core plugin embedding (PluginFrame, PluginContext, PluginMessageHandler)
├── test/          — All test files
├── theme/         — Chakra UI theme customization
├── utils/         — Utility functions
└── router.tsx     — React Router v7 route definitions
```

---

## Request / Data Flow

### Typical Web Client Request
```
Browser
  └─► GET /api/products (JWT Bearer)
        └─► JwtAuthenticationFilter (validates token, reads permissions)
              └─► SecurityConfiguration (checks PERMISSION_READ)
                    └─► ProductController
                          └─► ProductService
                                └─► DbProductQueryService (jOOQ) or ProductRepository (JPA)
                                      └─► PostgreSQL 18
```

### MCP Tool Call
```
AI Agent (opencode)
  └─► MCP tool call (products/list)
        └─► aj-mcp port 8081
              └─► McpIntrospectionFilter → POST /oauth2/introspect (aj AS, RFC 7662)
                    └─► TokenExchangeClient → POST /oauth2/token (aj AS, RFC 8693)
                          └─► AjApiClient → GET /api/products (Token-B, aj JWT)
                                └─► aj core port 8080
```

---

## Deployment Architecture

- **Main app**: Spring Boot fat JAR, port 8080
- **MCP server**: Separate Spring Boot JAR (`mcp-server/`), port 8081
- **Database**: PostgreSQL 18, port 5432
- **AI stack**: Docker Compose (`compose.yml`) — LiteLLM (4000), LangFuse (3100), Presidio (5001/5002)
- **CI/CD**: Not yet configured
- **Containerization**: No production Dockerfile yet; TestContainers for test isolation

---

*Last Updated*: 2026-04-26
