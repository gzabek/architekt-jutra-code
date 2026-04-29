# Spec: aud Claim Validation in JwtAuthenticationFilter

## Problem
`JwtAuthenticationFilter.parseToken()` accepts any JWT with a valid RS256 signature, regardless
of the `aud` claim.  A Token-B issued for `aud=https://upstream-service` can currently authenticate
requests against this MCP server — defeating the anti-passthrough guarantee at the validation layer.

## Scope

### In
1. Validate `aud` claim in `JwtAuthenticationFilter` using `app.oauth2.resource-uri`.
2. Remove token-value logging in `OAuth2TokenFilter` (lines 73-76 log all request params, which
   includes `subject_token` and `refresh_token` values in plain text).
3. Add `JwtAuthenticationFilterAudValidationTests` — aud-mismatch → 401, matching aud → 200,
   no aud claim → 200 (backward-compatible).

### Out
- No changes to any other filter, controller, or test class.
- No database migrations.
- No frontend changes.

## Requirements

### R1 — aud Validation
- `JwtAuthenticationFilter` is constructed with the server's own resource URI
  (from `app.oauth2.resource-uri`).
- When a JWT contains an `aud` claim:
  - If `aud` is a JSON array: accepted when the array contains the server URI.
  - If `aud` is a plain string: accepted when it equals the server URI.
  - If `aud` does not contain the server URI: token is treated as invalid (not authenticated).
- When a JWT has **no** `aud` claim: accepted (backward-compatible; user tokens never carry `aud`).
- Invalid tokens result in the request proceeding unauthenticated (Spring Security returns 401
  for protected endpoints via the existing `authenticationEntryPoint`).

### R2 — Logging hygiene in OAuth2TokenFilter
- Replace the per-request `INFO` log that emits raw parameter map (line 73-76) with a safe
  log that only emits `grant_type`.

### R3 — Tests
- `JwtAuthenticationFilterAudValidationTests` (package-private, `@Transactional`):
  - `request_withNoAudClaim_isAuthenticated` — user token (no aud) → 200 on `/api/products`
  - `request_withMatchingAud_isAuthenticated` — OAuth2 token with `aud=[resourceUri]` → 200
  - `request_withMismatchedAud_returns401` — OAuth2 token with `aud=https://other` → 401

## Design

### JwtAuthenticationFilter constructor change
```java
public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, String resourceUri)
```
`resourceUri` is injected from `SecurityConfiguration` which already holds
`@Value("${app.oauth2.resource-uri}")`.

### Validation logic (in doFilterInternal, after parseToken)
`JwtTokenProvider.parseToken()` already returns a `ParsedToken` record with a `permissions` set
and a `scopes` set. To carry the raw `aud` claim we extend `ParsedToken` with an `audience`
field (`List<String>`, empty = not present).

Validation pseudocode:
```
if parsedToken.audience() is non-empty:
    if not parsedToken.audience().contains(resourceUri):
        skip authentication (do not set SecurityContext)
```

### JwtTokenProvider.parseToken — aud extraction
jjwt `Claims.get("aud")` returns either a `String` or a `List<String>` depending on the JWT.
We normalise to `List<String>` in `parseToken`.

## Acceptance Criteria
- All 3 new tests pass.
- All 49 pre-existing auth/oauth2 tests continue to pass.
- Token param log line removed from `OAuth2TokenFilter`.
