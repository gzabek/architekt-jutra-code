# Requirements — MCP Token Introspection & Token Exchange

## Task Description

Replace the token passthrough anti-pattern in the MCP server with:
- **RFC 7662 Token Introspection** — already present in `OAuth2IntrospectionFilter`; must be updated to work with RS256 tokens
- **RFC 8693 Token Exchange** — already partially present in `OAuth2TokenFilter.handleTokenExchangeGrant`; must be hardened with proper audience binding and allowlist
- **RFC 9728 PRM** — new `/.well-known/oauth-protected-resource` endpoint required by MCP spec
- **RFC 8707 Audience Binding** — `resource` param through authorize flow to `aud` claim
- **RS256 Key Infrastructure** — migrate HS256 → RS256; add JWKS endpoint

Big bang implementation: all mechanisms ship together.

## Q&A

**Q: RSA key loading strategy?**
A: Ephemeral only — generate RSA-2048 key pair in-memory at startup. Tokens are 15-min short-lived; restart invalidation acceptable.

**Q: Audience allowlist mechanism for token exchange?**
A: Static `application.properties` list via `app.oauth2.allowed-exchange-audiences`. Token exchange with unlisted `audience` returns `error=invalid_target`.

**Q: Is DB migration needed?**
A: No. Research confirmed auth codes stay in-memory (`ConcurrentHashMap`). The `AuthorizationCodeData` record gets a new `resourceUri` field in Java, but no Liquibase migration needed.

**Q: Are all architectural decisions already made?**
A: Yes — ADR-001 through ADR-004 from the research phase are all accepted:
  - ADR-001: RS256 (RSA) over HS256 (HMAC)
  - ADR-002: RFC 8693 Token Exchange with proper audience binding
  - ADR-003: Dedicated ProtectedResourceMetadataController
  - ADR-004: RFC 8707 audience binding via `resource` param

**Q: User journey / personas?**
A: This is pure backend infrastructure. No UI changes. The "user" is the MCP client (Claude Desktop / AI agent) performing OAuth 2.1 authorization code + PKCE flow with auto-discovery via RFC 9728.

**Q: Existing code to reuse?**
A: Yes — Nimbus JOSE+JWT is already on classpath via `spring-security-oauth2-authorization-server`. Use `com.nimbusds.jose.crypto.RSASSASigner`, `com.nimbusds.jose.jwk.RSAKey`, `NimbusJwtDecoder`.

## Functional Requirements

1. **RSA Key Infrastructure**
   - `RsaKeyConfiguration` generates RSA-2048 `KeyPair` at startup (ephemeral)
   - Exposes `RSAPrivateKey` and `RSAPublicKey` as Spring beans
   - `JwtTokenProvider` uses `RSASSASigner` / RS256 algorithm

2. **JWKS Endpoint (RFC 7517)**
   - `GET /oauth2/jwks.json` → public RSA key as JWK Set
   - No authentication required
   - AS metadata includes `jwks_uri`

3. **RFC 9728 Protected Resource Metadata**
   - `GET /.well-known/oauth-protected-resource` → JSON document
   - Fields: `resource`, `authorization_servers`, `bearer_methods_supported`, `scopes_supported`, `resource_signing_alg_values_supported`
   - No authentication required
   - 401 responses include `WWW-Authenticate: Bearer resource_metadata="...", scope="mcp:read"`

4. **RFC 8707 Audience Binding**
   - `OAuth2AuthorizationFilter` reads `resource` request param; validates = server URI; stores in `AuthorizationCodeData`
   - `OAuth2TokenFilter.handleAuthorizationCodeGrant` reads stored `resource`; sets `aud = [resource]` on Token-A
   - If `resource` absent in authorize request → default `aud = [mcp.server-uri]` (backward compat)

5. **RFC 8693 Token Exchange (hardened)**
   - `handleTokenExchangeGrant` reads `resource` / `audience` param from exchange request
   - Validates `audience` against `app.oauth2.allowed-exchange-audiences` allowlist
   - Issues Token-B with `aud = [requested_audience]` (not issuer URL)
   - Rejects if `audience` not in allowlist → `error=invalid_target`

6. **JWT Validation (updated)**
   - `JwtAuthenticationFilter` uses `NimbusJwtDecoder` with RSA public key
   - Validates: signature, `exp`, `iss`, `aud` (must contain server URI)

## Non-Functional Requirements

- All existing integration tests must pass after the migration
- New endpoints (`/oauth2/jwks.json`, `/.well-known/oauth-protected-resource`) must have integration tests
- Token exchange allowlist validation must have tests (valid audience + invalid_target rejection)
- RFC 8707 resource binding must have tests (Token-A aud claim verified)
- No token logging (remove or redact any `log.info` with raw token values)

## Scope Boundaries

**In scope:**
- RSA key infrastructure
- JWKS endpoint
- PRM endpoint (RFC 9728)
- WWW-Authenticate header on 401
- RFC 8707 resource param through authorize → token
- RFC 8693 token exchange audience hardening
- JWT validation migration to NimbusJwtDecoder
- AS metadata `jwks_uri` field

**Out of scope:**
- Production KeyStore/PKCS#12 loading
- Token revocation endpoint
- Spring Authorization Server migration
- Client ID Metadata Documents
- Any frontend changes
