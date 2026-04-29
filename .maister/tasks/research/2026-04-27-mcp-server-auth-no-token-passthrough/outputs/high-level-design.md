# High-Level Design: MCP Server Auth Compliance (2025-11-25)

**Project**: Spring Boot 4.0.5 / Java 25 MCP Server  
**Spec target**: MCP Authorization Specification 2025-11-25  
**Constraint**: No token passthrough — the MCP server acts as a full Authorization Server  
**Date**: 2026-04-27

---

## 1. C4 Component Diagram (Updated Architecture)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  MCP CLIENT (Claude Desktop / AI Agent)                                     │
│                                                                             │
│  1. Discovers AS via RFC 9728 PRM                                           │
│  2. Performs OAuth 2.1 Authorization Code + PKCE                            │
│  3. Presents access token on MCP requests                                   │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ HTTPS
                    ┌──────────────▼──────────────────────────────────────────┐
                    │  MCP SERVER  (Spring Boot 4.0.5 / Java 25)             │
                    │                                                         │
                    │  ┌─────────────────────────────────────────────────┐   │
                    │  │  SECURITY LAYER  (Servlet Filter Chain)         │   │
                    │  │                                                  │   │
                    │  │  JwtAuthenticationFilter                         │   │
                    │  │    └─ validates RS256 Bearer token via           │   │
                    │  │       JwtDecoder (spring-security-oauth2-rs)     │   │
                    │  │                                                  │   │
                    │  │  SecurityConfiguration                           │   │
                    │  │    └─ authenticationEntryPoint                   │   │
                    │  │         returns 401 + WWW-Authenticate header    │   │
                    │  │         pointing to /.well-known/oauth-          │   │
                    │  │         protected-resource                       │   │
                    │  └─────────────────────────────────────────────────┘   │
                    │                                                         │
                    │  ┌─────────────────────────────────────────────────┐   │
                    │  │  AUTHORIZATION SERVER (Embedded)                │   │
                    │  │                                                  │   │
                    │  │  OAuth2MetadataController          [UPDATED]    │   │
                    │  │    GET /.well-known/oauth-authorization-server   │   │
                    │  │    + jwks_uri, token_endpoint_auth_methods       │   │
                    │  │                                                  │   │
                    │  │  ProtectedResourceMetadataController  [NEW]     │   │
                    │  │    GET /.well-known/oauth-protected-resource     │   │
                    │  │                                                  │   │
                    │  │  JwksController                       [NEW]     │   │
                    │  │    GET /oauth2/jwks.json                         │   │
                    │  │                                                  │   │
                    │  │  OAuth2AuthorizationFilter         [UPDATED]    │   │
                    │  │    GET/POST /oauth2/authorize                    │   │
                    │  │    + stores resource param (RFC 8707)            │   │
                    │  │                                                  │   │
                    │  │  OAuth2TokenFilter                 [UPDATED]    │   │
                    │  │    POST /oauth2/token                            │   │
                    │  │    + auth_code grant: binds aud from resource    │   │
                    │  │    + token_exchange: issues Token-B with         │   │
                    │  │      requested_audience, not issuer              │   │
                    │  │                                                  │   │
                    │  │  OAuth2IntrospectionFilter         [unchanged]  │   │
                    │  │    POST /oauth2/introspect                       │   │
                    │  │                                                  │   │
                    │  │  PublicClientRegistrationFilter    [unchanged]  │   │
                    │  │    POST /oauth2/register (DCR)                   │   │
                    │  └─────────────────────────────────────────────────┘   │
                    │                                                         │
                    │  ┌─────────────────────────────────────────────────┐   │
                    │  │  KEY MANAGEMENT                                  │   │
                    │  │                                                  │   │
                    │  │  RsaKeyConfiguration              [NEW]         │   │
                    │  │    └─ loads/generates RSA KeyPair on startup     │   │
                    │  │    └─ exposes JWSSigner + public JWK             │   │
                    │  │                                                  │   │
                    │  │  JwtTokenProvider                 [UPDATED]     │   │
                    │  │    └─ signs with RSA private key (RS256)         │   │
                    │  │    └─ no longer holds symmetric secret           │   │
                    │  └─────────────────────────────────────────────────┘   │
                    │                                                         │
                    │  ┌─────────────────────────────────────────────────┐   │
                    │  │  DATA LAYER                                      │   │
                    │  │                                                  │   │
                    │  │  DatabaseRegisteredClientRepository [unchanged] │   │
                    │  │  AuthorizationCodeService          [UPDATED]    │   │
                    │  │    └─ stores resource URI alongside auth code    │   │
                    │  │  RefreshTokenService               [unchanged]  │   │
                    │  └─────────────────────────────────────────────────┘   │
                    └──────────────────────────────┬──────────────────────────┘
                                                   │ Token Exchange (RFC 8693)
                                                   │ POST /token  aud=upstream-svc
                    ┌──────────────────────────────▼──────────────────────────┐
                    │  UPSTREAM SERVICE  (e.g. external API / tool backend)  │
                    │  Validates Token-B (issued by MCP server's embedded AS) │
                    └─────────────────────────────────────────────────────────┘
```

---

## 2. Component Descriptions

### New Components

#### `ProtectedResourceMetadataController` (NEW)
- **Path**: `GET /.well-known/oauth-protected-resource`
- **Purpose**: Implements RFC 9728. Allows MCP clients to auto-discover which AS protects this resource without prior configuration.
- **Response**: JSON document containing `resource`, `authorization_servers[]`, `scopes_supported[]`, `bearer_methods_supported[]`.
- **No auth required** on this endpoint — it is a public discovery document.

#### `JwksController` (NEW)
- **Path**: `GET /oauth2/jwks.json`
- **Purpose**: Serves the public RSA key(s) in JWK Set format so that resource servers and clients can verify tokens without contacting the introspection endpoint.
- **No auth required** — standard public JWKS endpoint.

#### `RsaKeyConfiguration` (NEW)
- **Type**: Spring `@Configuration` bean.
- **Purpose**: Single source of truth for the server's signing key material.
- **Behaviour**: On startup, either loads a `KeyPair` from a configured PKCS#12 KeyStore (production) or generates an ephemeral RSA-2048 pair (development/test).
- **Exposes**: `RSAPrivateKey` for signing (to `JwtTokenProvider`), `RSAPublicKey` / `JWKSet` for verification (to `JwksController` and `JwtDecoder`).

### Updated Components

#### `JwtTokenProvider` (UPDATED)
- **Change**: Replace `MacSigner` / `MACVerifier` with `RSASSASigner` / `RSASSAVerifier` (Nimbus JOSE+JWT or Spring Security's `NimbusJwtEncoder`).
- **Key source**: Injected `RsaKeyConfiguration` bean.
- **Token shape**: `alg: RS256`, `kid` header matches the key ID published in JWKS.

#### `JwtAuthenticationFilter` (UPDATED)
- **Change**: Replace inline HMAC validation with Spring Security `JwtDecoder` (`NimbusJwtDecoder.withJwkSetUri(...)` or `NimbusJwtDecoder.withPublicKey(...)`).
- **Benefit**: Automatic key rotation support; standard Spring Security integration path.

#### `SecurityConfiguration` (UPDATED)
- **Change**: Register a custom `AuthenticationEntryPoint` that, on any 401, writes:
  ```
  WWW-Authenticate: Bearer resource_metadata="https://<host>/.well-known/oauth-protected-resource", scope="mcp:read mcp:write"
  ```
- This is required by MCP auth spec §4.3 and RFC 9728.

#### `OAuth2MetadataController` (UPDATED)
- **Change**: Add `jwks_uri` field pointing to `/oauth2/jwks.json`, and ensure `token_endpoint_auth_methods_supported` lists `none` (public clients via PKCE).

#### `OAuth2AuthorizationFilter` (UPDATED)
- **Change**: Extract the `resource` query parameter (RFC 8707) from the authorization request and pass it to `AuthorizationCodeService.createCode(...)` for storage.
- **Validation**: If present, `resource` must match the server's own configured URI; reject with `invalid_target` otherwise.

#### `OAuth2TokenFilter` (UPDATED)
- `handleAuthorizationCodeGrant`: Retrieve the stored `resource` value from the auth code record; set it as `aud` claim in the issued access token.
- `handleTokenExchangeGrant`: Read the optional `audience` request parameter; issue Token-B with `aud = requested_audience`. Fall back to a configured `mcp.upstream-service-uri` property if no `audience` parameter is present.

#### `AuthorizationCodeService` (UPDATED)
- **Change**: Add `resourceUri` column/field to the authorization code record (new Liquibase migration required). Store and retrieve alongside the code.

---

## 3. Sequence Diagram — MCP Auth Flow with RFC 9728 Discovery

```
MCP Client                MCP Server                  User (Browser)
    │                         │                              │
    │  GET /mcp/...           │                              │
    │────────────────────────►│                              │
    │                         │ No token → 401              │
    │◄────────────────────────│                              │
    │  WWW-Authenticate:      │                              │
    │  Bearer resource_metadata=                             │
    │  "https://server/.well-known/oauth-protected-resource" │
    │                         │                              │
    │  GET /.well-known/oauth-protected-resource             │
    │────────────────────────►│                              │
    │◄────────────────────────│                              │
    │  { resource, authorization_servers: ["https://server"],│
    │    scopes_supported, bearer_methods_supported }        │
    │                         │                              │
    │  GET /.well-known/oauth-authorization-server           │
    │────────────────────────►│                              │
    │◄────────────────────────│                              │
    │  { issuer, authorization_endpoint,                     │
    │    token_endpoint, jwks_uri, ... }                     │
    │                         │                              │
    │  Open browser → GET /oauth2/authorize                  │
    │  ?response_type=code&client_id=...                     │
    │  &code_challenge=...&resource=https://server/mcp       │
    │────────────────────────►│                              │
    │                         │  Redirect to login UI ───►  │
    │                         │◄──────────────────── POST credentials
    │                         │  store(code, resource)       │
    │                         │  Redirect to redirect_uri ──►│
    │◄──────────────────────────────────────── code=ABC      │
    │                         │                              │
    │  POST /oauth2/token     │                              │
    │  grant_type=authorization_code                         │
    │  code=ABC&code_verifier=...                            │
    │────────────────────────►│                              │
    │                         │  lookup(code=ABC)            │
    │                         │  → resource=https://server/mcp
    │                         │  issue Token-A              │
    │                         │  { sub, aud=["https://server/mcp"],
    │                         │    scope, alg=RS256 }        │
    │◄────────────────────────│                              │
    │  { access_token: Token-A, token_type: Bearer }         │
    │                         │                              │
    │  GET /mcp/...           │                              │
    │  Authorization: Bearer Token-A                         │
    │────────────────────────►│                              │
    │                         │  JwtDecoder.decode(Token-A)  │
    │                         │  aud check: server/mcp ✓     │
    │                         │  scope check ✓               │
    │◄────────────────────────│                              │
    │  200 OK + MCP response  │                              │
```

---

## 4. Sequence Diagram — Token Exchange Anti-Passthrough Flow

```
MCP Server (tool handler)       Embedded AS             Upstream Service
        │                           │                         │
        │  (Token-A validated,      │                         │
        │   user action requires    │                         │
        │   call to upstream)       │                         │
        │                           │                         │
        │  POST /oauth2/token       │                         │
        │  grant_type=              │                         │
        │    urn:ietf:params:oauth: │                         │
        │    grant-type:token-exchange                        │
        │  subject_token=Token-A    │                         │
        │  subject_token_type=      │                         │
        │    urn:ietf:params:oauth: │                         │
        │    token-type:access_token│                         │
        │  audience=https://upstream│                         │
        │──────────────────────────►│                         │
        │                           │  validate Token-A       │
        │                           │  (sig, expiry, scope)   │
        │                           │  mint Token-B:          │
        │                           │  { sub=<original sub>,  │
        │                           │    aud=["https://upstream"],
        │                           │    scope=<subset>,      │
        │                           │    alg=RS256,           │
        │                           │    iss=https://server } │
        │◄──────────────────────────│                         │
        │  { access_token: Token-B, │                         │
        │    issued_token_type:      │                         │
        │    .../access_token }      │                         │
        │                           │                         │
        │  GET /api/resource        │                         │
        │  Authorization: Bearer Token-B                      │
        │──────────────────────────────────────────────────►  │
        │                           │  validate Token-B:      │
        │                           │  sig via /oauth2/jwks.json
        │                           │  aud=https://upstream ✓ │
        │◄──────────────────────────────────────────────────  │
        │  200 OK + upstream data   │                         │
```

**Key anti-passthrough guarantee**: Token-A (scoped to the MCP server) is never forwarded to the upstream service. Token-B has a distinct, narrower audience and optionally a reduced scope. The upstream service can independently verify Token-B via the public JWKS endpoint.

---

## 5. Key Interface Contracts

### `GET /.well-known/oauth-protected-resource`

**Response** (HTTP 200, `application/json`):
```json
{
  "resource": "https://server/mcp",
  "authorization_servers": [
    "https://server"
  ],
  "scopes_supported": [
    "mcp:read",
    "mcp:write"
  ],
  "bearer_methods_supported": [
    "header"
  ]
}
```

### `GET /oauth2/jwks.json`

**Response** (HTTP 200, `application/json`):
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "mcp-server-key-2026",
      "n": "<base64url-modulus>",
      "e": "AQAB"
    }
  ]
}
```

### `GET /.well-known/oauth-authorization-server` (updated fields)

Added fields to existing response:
```json
{
  "jwks_uri": "https://server/oauth2/jwks.json",
  "token_endpoint_auth_methods_supported": ["none"],
  "token_endpoint_auth_signing_alg_values_supported": ["RS256"],
  "code_challenge_methods_supported": ["S256"]
}
```

### `401` Response Header (updated)

```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer resource_metadata="https://server/.well-known/oauth-protected-resource", scope="mcp:read"
```

### `POST /oauth2/token` — Authorization Code Grant (updated behaviour)

**Request** (unchanged form params, but `resource` now stored and honoured):
```
grant_type=authorization_code
code=ABC
redirect_uri=https://client/callback
code_verifier=<pkce-verifier>
client_id=<public-client-id>
```

**Token-A claims** (added/changed):
```json
{
  "iss": "https://server",
  "sub": "<user-id>",
  "aud": ["https://server/mcp"],
  "scope": "mcp:read mcp:write",
  "alg": "RS256",
  "kid": "mcp-server-key-2026"
}
```

### `POST /oauth2/token` — Token Exchange Grant (updated behaviour)

**Request**:
```
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
subject_token=<Token-A>
subject_token_type=urn:ietf:params:oauth:token-type:access_token
audience=https://upstream-service
scope=upstream:read
```

**Token-B claims**:
```json
{
  "iss": "https://server",
  "sub": "<original-sub>",
  "aud": ["https://upstream-service"],
  "scope": "upstream:read",
  "alg": "RS256"
}
```

**Error** (when `audience` is not in allowed list):
```json
{
  "error": "invalid_target",
  "error_description": "Requested audience is not permitted for token exchange"
}
```

---

## 6. Database Change

A single Liquibase migration is required to support RFC 8707 resource binding:

```sql
-- Add resource_uri to authorization codes table
ALTER TABLE authorization_codes
  ADD COLUMN resource_uri VARCHAR(2048);
```

This column is nullable for backward compatibility during rollout; the token issuance logic treats a null value as "no resource binding requested" (legacy behaviour).

---

## 7. Configuration Properties (new)

```yaml
mcp:
  server-uri: https://server/mcp          # Used as default 'aud' in Token-A
  upstream-service-uri: https://upstream  # Default audience for Token-B when not specified

security:
  jwt:
    key-store: classpath:keystore.p12     # Optional; if absent, ephemeral key is generated
    key-store-password: ${KEY_STORE_PASS}
    key-alias: mcp-server-key
    key-id: mcp-server-key-2026           # Published as 'kid' in JWKS
```
