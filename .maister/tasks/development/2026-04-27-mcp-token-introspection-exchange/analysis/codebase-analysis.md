# Codebase Analysis — MCP Token Introspection & Exchange

## Executive Summary

The codebase has a hand-rolled OAuth 2.1 AS+RS built on Spring Boot 4.0.5. All token signing is HMAC-SHA256 (HS256) with a single shared secret. The MCP spec 2025-11-25 explicitly forbids token passthrough, requires RSA (RS256) signing with a public JWKS endpoint, RFC 9728 PRM discovery, RFC 8707 audience binding, and a hardened RFC 8693 token exchange. None of these are present in the current code.

## Key Files

| File | Role | Status |
|------|------|--------|
| `core/security/JwtTokenProvider.java` | Signs/validates all JWTs | Needs full RSA refactor |
| `core/security/JwtAuthenticationFilter.java` | Validates Bearer tokens | Needs JwtDecoder integration |
| `core/security/SecurityConfiguration.java` | Filter chain + auth rules | Needs WWW-Authenticate entry point, PRM permit |
| `core/oauth2/OAuth2TokenFilter.java` | `/oauth2/token` handler | Needs aud binding, audience param on exchange |
| `core/oauth2/OAuth2IntrospectionFilter.java` | RFC 7662 introspection | Mostly correct, minor gaps |
| `core/oauth2/OAuth2AuthorizationFilter.java` | Auth code issuance | Needs resource param extraction |
| `core/oauth2/AuthorizationCodeService.java` | In-memory auth code store | Needs `resourceUri` field |
| `core/oauth2/OAuth2MetadataController.java` | AS metadata endpoint | Needs `jwks_uri` field |

## Critical Gaps (vs RFC 7662 / RFC 8693 / RFC 9728 / RFC 8707)

1. **HMAC → RSA**: Entire signing infrastructure is HS256. No `RsaKeyConfiguration`, no `RSASSASigner`, no JWKS endpoint.
2. **No `/.well-known/oauth-protected-resource`**: RFC 9728 PRM endpoint completely absent.
3. **No `WWW-Authenticate` header**: 401 responses have no `resource_metadata` hint.
4. **No audience binding (RFC 8707)**: `resource` param not read in authorize flow; no `aud` on auth-code tokens.
5. **Token exchange `aud` bug**: Token-B has `aud = issuer` (AS URL) instead of `aud = requested_audience`.
6. **No audience allowlist**: Token exchange will mint tokens for any `audience` value — security hole.
7. **Missing `jwks_uri` in AS metadata**: No JWKS = metadata is incomplete.
8. **`AuthorizationCodeData` missing `resourceUri`**: Cannot propagate resource to token.

## In-Scope Components

- `JwtTokenProvider` — replace `MacSigner`/HMAC with `RSASSASigner`/RS256
- `JwtAuthenticationFilter` — replace inline HMAC parse with `NimbusJwtDecoder`
- `SecurityConfiguration` — add PRM permit + `WWW-Authenticate` entry point
- `OAuth2MetadataController` — add `jwks_uri`; new `ProtectedResourceMetadataController`
- New: `JwksController` (GET /oauth2/jwks.json)
- New: `RsaKeyConfiguration` (@Configuration bean)
- `OAuth2AuthorizationFilter` — extract + validate `resource` param
- `AuthorizationCodeService` — add `resourceUri` field
- `OAuth2TokenFilter.handleAuthorizationCodeGrant` — bind `aud` from stored resource
- `OAuth2TokenFilter.handleTokenExchangeGrant` — bind `aud` to `audience` param; validate against allowlist
- New Liquibase migration — nothing needed (auth codes still in-memory per design)
- `application.properties` — add `mcp.server-uri`, `mcp.allowed-exchange-audiences`

## Test Files

- `OAuth2IntegrationTests.java` — extend with RFC 8707 resource param tests
- `OAuth2IntrospectionTests.java` — extend with RSA-signed token tests
- `TokenExchangeIntegrationTests.java` — extend with audience binding + allowlist tests
- New: `ProtectedResourceMetadataControllerTests.java`
- New: `JwksControllerTests.java`
- `JwtTokenProviderTests.java` — update for RSA

## Technology

- Spring Boot 4.0.5, Java 25
- `spring-security-oauth2-authorization-server` on classpath (Nimbus JOSE+JWT available)
- `io.jsonwebtoken` (jjwt) 0.12.6 also on classpath
- Auth codes: in-memory `ConcurrentHashMap` (no DB persistence — by design)
