# Finding: Codebase Analysis — Current Implementation Status

**Source**: pl.devstyle.aj.core.oauth2.*, pl.devstyle.aj.core.security.*
**Confidence**: High (direct code reading)

## What Is Already Implemented

### OAuth2 Infrastructure (Good)
The project has a substantial custom OAuth2 authorization server implementation:

| Component | File | Status |
|-----------|------|--------|
| AS Metadata endpoint | `OAuth2MetadataController` | ✅ Implemented (`/.well-known/oauth-authorization-server`) |
| Client registry (JPA) | `DatabaseRegisteredClientRepository` | ✅ Implemented |
| Dynamic Client Registration | `PublicClientRegistrationFilter` | ✅ Implemented (`/oauth2/register`) |
| Authorization Code flow | `OAuth2AuthorizationFilter` | ✅ Implemented (`/oauth2/authorize`) |
| PKCE S256 validation | `OAuth2TokenFilter` | ✅ Implemented |
| Token issuance (HMAC JWT) | `JwtTokenProvider` | ✅ Implemented |
| Refresh token rotation | `RefreshTokenService` | ✅ Implemented |
| Token introspection | `OAuth2IntrospectionFilter` | ✅ Implemented (`/oauth2/introspect`) |
| Token Exchange (RFC 8693) | `OAuth2TokenFilter.handleTokenExchangeGrant` | ✅ Implemented |
| Bearer auth + scope enforcement | `SecurityConfiguration` | ✅ Implemented |
| Audience claim in OAuth2 tokens | `JwtTokenProvider.generateOAuth2Token(audience)` | ✅ Supported |

### Scopes
`mcp:read` → maps to `PERMISSION_READ`
`mcp:edit` → maps to `PERMISSION_EDIT`

### MCP Scope Mapping (in token exchange)
`MCP_SCOPE_MAPPING = { "mcp:read" → "READ", "mcp:edit" → "EDIT" }` — used in token exchange to generate a downstream token with backend permissions.

### Token Exchange Anti-Passthrough
`handleTokenExchangeGrant()` in `OAuth2TokenFilter`:
1. Authenticates the calling client
2. Validates the subject token (Token-A) — MUST be a valid JWT signed by this server
3. Maps MCP scopes → backend permissions
4. Issues Token-B with `aud = issuer URL` — a different token with different audience

This correctly prevents passthrough: Token-A is consumed and Token-B is issued. Token-A is never forwarded.

## Gaps vs Latest MCP Spec (2025-11-25)

### 1. Missing Protected Resource Metadata (PRM) — RFC 9728 — HIGH PRIORITY
The spec (2025-11-25) REQUIRES MCP servers to implement RFC 9728 Protected Resource Metadata.
- The project has `/.well-known/oauth-authorization-server` (RFC 8414 — AS metadata)
- But is MISSING `/.well-known/oauth-protected-resource` (RFC 9728 — Resource Server metadata)
- PRM must include: `resource` URI, `authorization_servers` array, `scopes_supported`
- Clients look for PRM first (from `WWW-Authenticate` header or well-known URI), not AS metadata directly

### 2. Missing `WWW-Authenticate` challenge on 401
The 401 response from `SecurityConfiguration` does not include `WWW-Authenticate: Bearer resource_metadata="..."` header.
The spec says clients MUST parse this header to discover the PRM URL.

### 3. Missing `resource` parameter validation
When the MCP client sends `resource=<mcp-server-uri>` in the auth/token requests (required by RFC 8707), the current `OAuth2AuthorizationFilter` and `OAuth2TokenFilter` do not appear to validate or use this parameter to bind the issued token to the requested resource.

### 4. Audience not always set
`JwtTokenProvider.generateOAuth2Token(username, scopes, issuer)` — the one-argument version does NOT set an audience. This means some issued access tokens have no `aud` claim. The spec requires audience validation on the resource server side.

### 5. `code_challenge_methods_supported` missing from token validation gate
The AS metadata correctly advertises `code_challenge_methods_supported: ["S256"]`. But the code does not gate authorization on verifying that the client is aware of PKCE support (this is a client-side responsibility, but worth noting).

### 6. Client ID Metadata Documents not supported
The 2025-11-25 spec promotes Client ID Metadata Documents as the preferred registration mechanism. Only DCR is currently supported. This is a SHOULD, not a MUST.

### 7. Token logging risk
`OAuth2TokenFilter` logs the full token response body including access tokens:
`log.info("OAuth2 token response body: {}", tokenResponseBody);` — this should be removed or redacted in production.
