# Spec Audit — MCP Token Introspection & Exchange

**Spec**: `implementation/spec.md`  
**Audited**: 2026-04-27  
**Auditor**: maister-spec-auditor  
**Overall verdict**: `pass-with-concerns`

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 2 |
| Warning  | 8 |
| Info     | 5 |

The spec is well-written, implementation-ready for the vast majority of changes, and correctly captures the RFCs it cites. Two critical issues require resolution before implementation begins: a missing Maven dependency that would cause a compile error on `NimbusJwtDecoder`, and a token-logging security vulnerability that is already present in production code and is not addressed by the spec.

---

## Critical Issues

### C1 — `NimbusJwtDecoder` dependency not confirmed on classpath

**Section**: Appendix A / §3.2 / §3.6  
**Severity**: Critical

The spec states (Appendix A):
> `NimbusJwtDecoder` — Yes (spring-security-oauth2-resource-server)

However, `spring-security-oauth2-resource-server` is **not a direct dependency** in `pom.xml`. It is only a transitive dependency pulled in by `spring-security-oauth2-authorization-server:7.0.4`. Transitive dependencies should not be relied upon directly — the authorization-server version could change and drop it, or the transitive graph could be re-structured.

**Impact**: If the transitive dependency is removed or excluded in future, Step 10 (JwtAuthenticationFilter) and Step 11 (SecurityConfiguration) would fail to compile with no clear error message.

**Fix**: Add `spring-security-oauth2-resource-server` as an explicit dependency in `pom.xml`. This is a one-liner and zero-risk. The spec's implementation order (Step 12 covers `application.properties` but not `pom.xml`) should include this as Step 0 or part of Step 1.

---

### C2 — Token value logged in plaintext (pre-existing, not fixed by spec)

**Section**: §3.5.3 (handleTokenExchangeGrant), related to existing `OAuth2TokenFilter.sendTokenResponse`  
**Severity**: Critical (security)

The existing `OAuth2TokenFilter.sendTokenResponse` (line 330 of current code) logs the **full token response body**, including the raw `access_token` JWT value:

```java
log.info("OAuth2 token response body: {}", tokenResponseBody);
```

This means every issued access token is written to application logs in plaintext. An attacker with log access (CI/CD artifacts, log aggregators, Splunk, etc.) can replay any token until it expires. This is a pre-existing vulnerability not introduced by this spec, but the spec's `§3.5.3` adds a new `log.info` in `handleTokenExchangeGrant` that logs `tokenBAudience` and `mappedPermissions` — which is fine. However, the spec does not instruct the implementer to remove or mask the token body log in `sendTokenResponse`.

**Impact**: Bearer tokens logged in plaintext violate OAuth2 best practices (RFC 6819 §5.1.6) and common compliance requirements (PCI-DSS, SOC2).

**Fix**: The spec should explicitly instruct removal (or masking) of the `log.info("OAuth2 token response body: {}", tokenResponseBody)` line in `sendTokenResponse`. Replace with a safe log such as:

```java
log.info("OAuth2 token issued | client_id={} | user={} | scope={}", ...);
```

This is directly in scope for this PR because the code is being modified in §3.5.

---

## Warnings

### W1 — `JwksController.toString()` is not the safe Nimbus API; use `toPublicJWKSet()`

**Section**: §2.2  
**Severity**: Warning (security)

The spec uses `jwkSet.toString()` and notes: "toString() = public representation (no private material)". This is correct for a `JWKSet` built only from an `RSAKey` (public-only) using `RSAKey.Builder(rsaPublicKey)`. However, this assertion depends on Nimbus internals and is fragile. The explicit safe API is:

```java
jwkSet.toPublicJWKSet().toString();
```

`toPublicJWKSet()` strips any accidentally included private key material and makes the intent explicit in code. Given this is a JWKS endpoint serving key material publicly, the spec should prefer the explicit safe API over a comment assuring safety.

---

### W2 — `JwtAuthenticationFilter` dual-path logic has a subtle bug risk: both paths can run on the same token

**Section**: §3.2  
**Severity**: Warning

The proposed `doFilterInternal` logic is:

```java
tryAuthenticateWithPermissionsToken(token);   // jjwt
if (!isAuthenticated()) {
    tryAuthenticateWithOAuth2Token(token);     // NimbusJwtDecoder
}
```

The split is described as: "permissions claim present → user session token; scopes claim present → OAuth2 token." However, `tryAuthenticateWithPermissionsToken` uses `jwtTokenProvider.parseToken()`. Looking at the actual `parseToken` implementation (line 88–107 of `JwtTokenProvider.java`), it falls back from `permissions` to `scopes` if `permissions` is null:

```java
if (permissions == null) {
    permissions = claims.get("scopes", List.class);
}
```

This means an OAuth2 token (which has `scopes` but no `permissions`) will be **successfully parsed by `parseToken`** in path 1, return a `ParsedToken` with scopes renamed as permissions, and set authentication — so path 2 (`NimbusJwtDecoder` with `iss`/`aud` validation) will **never run** for OAuth2 tokens.

The spec's stated goal — "validate `iss` and `aud` claims via NimbusJwtDecoder for OAuth2 tokens" — will silently not be achieved. The distinction between the two paths must be made at the authentication step, not just based on claim presence.

**Fix**: Either:
1. Change `tryAuthenticateWithPermissionsToken` to authenticate only when `permissions` claim is present (not falling back to `scopes`), OR
2. Check the specific claim set on the already-parsed token before deciding which path to trust, OR
3. Clarify in the spec that `JwtTokenProvider.parseToken` must be updated to NOT fall back to `scopes` when called from `JwtAuthenticationFilter`.

The spec should explicitly address this `parseToken` fallback behaviour.

---

### W3 — `SecurityConfiguration.jwtDecoder()` called as method inside `securityFilterChain` — potential double-instantiation

**Section**: §3.6  
**Severity**: Warning

The spec shows:

```java
.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, jwtDecoder(), expectedIssuer),
        UsernamePasswordAuthenticationFilter.class)
```

`jwtDecoder()` here is a call to a `@Bean` method. In Spring, calling a `@Bean`-annotated method directly on a `@Configuration` class is intercepted by CGLIB and returns the singleton bean. However, this only works if `SecurityConfiguration` uses full `@Configuration` (not `@Configuration(proxyBeanMethods = false)`). The current `SecurityConfiguration` does not set `proxyBeanMethods`, so CGLIB proxying applies by default — this is safe. But the spec should note this assumption, as it's a subtle Spring wiring concern.

More importantly, there is a **circular dependency risk**: `securityFilterChain` calls `jwtDecoder()`, which is a `@Bean`. If `jwtDecoder` bean construction triggers any security auto-configuration path, it could create a circular dependency. The spec does not address this risk.

**Fix**: Define `NimbusJwtDecoder` as a method-injected parameter to `securityFilterChain(HttpSecurity http, NimbusJwtDecoder jwtDecoder)` rather than calling `jwtDecoder()` directly. This avoids circular dependency risk and is explicit:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, NimbusJwtDecoder jwtDecoder) throws Exception {
    ...
    .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, jwtDecoder, expectedIssuer), ...)
```

---

### W4 — `handleRefreshTokenGrant` not updated: Token-B from refresh has no `aud` binding

**Section**: §3.5 (missing)  
**Severity**: Warning

Section §3.5.2 updates `handleAuthorizationCodeGrant` to set Token-A `aud` from `resourceUri`. However, `handleRefreshTokenGrant` (line 190 of current code) also calls `jwtTokenProvider.generateOAuth2Token(data.username(), grantedScopes, issuer)` — the 3-argument overload with no audience. The spec does not address this path.

After the migration, a user who obtained Token-A with `aud = https://resource.example.com` then performs a refresh and receives a new token with **no audience claim**, breaking audience binding on the refreshed token.

**Fix**: The spec should address `handleRefreshTokenGrant`. Either:
- Store `resourceUri` in `RefreshTokenService` data (similar to `AuthorizationCodeData`) and use it when refreshing, OR
- Explicitly document the known limitation and defer it.

Currently the spec neither fixes it nor acknowledges it as a known gap.

---

### W5 — `OAuth2AuthorizationFilter` error response for `resource` validation does not use RFC error codes

**Section**: §3.4 / §4.4  
**Severity**: Warning

The spec says:

```java
throw new IllegalArgumentException("resource parameter must be an absolute URI");
```

The existing `determineError()` method in `OAuth2AuthorizationFilter` (line 176–184) maps exception messages to error codes using string matching. The message "resource parameter must be an absolute URI" does not match any of the current cases (`redirect_uri`, `Scope not allowed`, `response_type`, `client_id`, `PKCE`, `authenticated`) — so it falls through to the `default` of `INVALID_REQUEST`, which is correct per §4.4.

However, this is fragile: the error code is determined by a string-matching `if-else` chain that does not explicitly handle the new `resource` error case. A developer following the spec literally could miss this and the test in §9.7 only checks the HTTP status + error code at the HTTP response level, not the path through `determineError`.

**Fix**: Add an explicit check for `resource` in `determineError`, or (better) document this dependency explicitly in §3.4.

---

### W6 — `getBaseUrl` appears in four places after this change; spec does not extract it

**Section**: §2.3, §3.6  
**Severity**: Warning

After this change, `getBaseUrl` logic exists in:
1. `OAuth2MetadataController`
2. `OAuth2TokenFilter`
3. `ProtectedResourceMetadataController` (new — copy verbatim per spec)
4. `SecurityConfiguration` as `deriveBaseUrl` (new — identical per spec)

The spec acknowledges the `ProtectedResourceMetadataController` duplication in a note but dismisses it: "Duplication is acceptable for now — extract to `BaseUrlHelper` utility in a future refactor." However, it then **also** adds a fourth copy in `SecurityConfiguration` (`deriveBaseUrl`) without noting this. Four identical implementations is a maintenance hazard; a future bug fix in one copy will be missed in the others.

**Fix**: The spec should either extract `BaseUrlHelper` as part of this PR (it's a 15-line utility with no risk), or at minimum acknowledge all four occurrences in the note. Adding a `// TODO: extract to BaseUrlHelper` comment in all four locations as part of this implementation would reduce future confusion.

---

### W7 — `Map.ofEntries` 10-entry limit claim is incorrect

**Section**: §3.7  
**Severity**: Warning (minor correctness)

The spec states: "The metadata document already contains 9 entries; `Map.ofEntries` supports up to 10 (uses varargs) — no structural change needed."

This is incorrect. `Map.ofEntries(Entry<K,V>... entries)` uses **varargs**, not an overloaded fixed-arity method. It supports any number of entries (bounded only by JVM varargs limits — tens of thousands). The 10-entry limit applies to `Map.of(k1,v1, k2,v2, ... k10,v10)`, not `Map.ofEntries`. Looking at the actual code, `getAuthorizationServerMetadata` uses `Map.ofEntries(entry(...), ...)` — which is correct and has no 10-entry limit. The note is misleading but does not affect implementation.

---

### W8 — Test §9.6 `tokenExchange_withAudienceInAllowlist_setsAudOnTokenB` is a stub; allowlist wiring for tests not specified

**Section**: §9.6  
**Severity**: Warning

The test spec for §9.6 includes three test methods, two of which are stubs (`{ ... }`). The non-stub test (`tokenExchange_withAudienceNotInAllowlist_returns400InvalidTarget`) references `validToken` and `encodedClientCredentials` as if they are already declared fields — but they are not shown. More importantly, to test allowlist enforcement the test context needs `app.oauth2.allowed-exchange-audiences` set to a specific value. The spec does not explain how to configure this for integration tests (e.g., `@TestPropertySource`, `@SpringBootTest(properties = {...})`).

**Fix**: Flesh out the two stub tests and add a `@TestPropertySource(properties = "app.oauth2.allowed-exchange-audiences=https://allowed.example.com")` annotation or equivalent.

---

## Info

### I1 — RFC 9728 §7 — `WWW-Authenticate` format is correct but uses non-standard attribute quoting

**Section**: §7.2  
**Severity**: Info

RFC 9728 §7 specifies the `resource_metadata` attribute. The spec's format `Bearer resource_metadata="<url>"` is correct per the RFC. The double-quote escaping of the URL value is standard bearer token challenge format per RFC 6750. No issue, but worth noting that if the base URL contains `"` characters (which it cannot for a valid HTTPS URL), the header would be malformed. This is a theoretical non-issue.

---

### I2 — `RsaKeyConfiguration` `NoSuchAlgorithmException` handling note

**Section**: §2.1  
**Severity**: Info

The spec says to wrap `NoSuchAlgorithmException` in `IllegalStateException`. The code example shows `KeyPairGenerator.getInstance("RSA")` without a try-catch. The spec mentions the handling in a note but does not show it in the code snippet. A developer implementing exactly what is shown in the snippet would produce code that does not compile (checked exception not handled). The note should be shown inline in the code snippet, not just in the error-handling note below.

---

### I3 — `OAuth2AuthorizationFilter` endpoint: spec says POST but RFC 8707 targets GET/redirect flows

**Section**: §3.4  
**Severity**: Info

The current implementation uses `POST /oauth2/authorize` with a form-based `_token` parameter for the authenticated user. RFC 8707 defines `resource` as a parameter of the authorization request, which is typically a GET. This is an existing design decision, not introduced by this spec. Noted for awareness — the spec correctly targets the existing POST flow.

---

### I4 — Ephemeral key restart behavior should be called out in the 401 `WWW-Authenticate` path

**Section**: §1 (What Does NOT Change) / §8.5  
**Severity**: Info

The spec correctly notes tokens are ephemeral and will fail after restart. Section §8.5 describes what happens when `NimbusJwtDecoder.decode()` fails (`JwtException` → leaves unauthenticated → 401 with `WWW-Authenticate`). This is the correct path. However, clients that cached a token across a server restart will see a 401 with `WWW-Authenticate: Bearer resource_metadata=...` pointing them to rediscover the AS — which is exactly the correct behavior. This chain is implicit; a brief note in §8.5 connecting "post-restart token invalidation" → "401 with WWW-Authenticate" → "client re-authorizes" would help implementers understand the full flow.

---

### I5 — Typo in test name: `getJwks_doesNotExposePivateKeyMaterial`

**Section**: §9.2  
**Severity**: Info

The test method name has a typo: `Pivate` should be `Private`. Should be `getJwks_doesNotExposePrivateKeyMaterial`. Minor but worth fixing to follow the naming standard.

---

## RFC Correctness Assessment

| RFC | Claim | Assessment |
|-----|-------|------------|
| RFC 7517 (JWKS) | `kty`, `use`, `alg`, `kid`, `n`, `e` fields | **Correct**. The Nimbus `RSAKey.Builder` produces compliant JWK. |
| RFC 9728 §3 (PRM) | `resource` + `authorization_servers` required | **Correct**. Both required fields present. |
| RFC 9728 §7 (WWW-Authenticate) | `Bearer resource_metadata="<url>"` | **Correct** format. |
| RFC 8707 §2 (resource param) | Absolute URI validation | **Correct**. Validated before storage. |
| RFC 8693 §2.1 (token exchange) | `audience` parameter, `invalid_target` error | **Correct**. Allowlist enforcement + fallback to issuer. |
| RFC 8693 §2.2.2 (INVALID_TARGET) | HTTP 400 + `invalid_target` error code | **Correct**. |
| RFC 6749 §5.2 (error response) | `error` + `error_description` JSON | **Correct**. Existing `OAuth2ErrorResponse` format. |

---

## Security Assessment

| Concern | Status |
|---------|--------|
| Private key material exposed in JWKS | **Safe** — `RSAKey.Builder(rsaPublicKey)` builds public-only JWK. Minor: use `toPublicJWKSet()` explicitly (W1). |
| Private key in Spring beans | **Acceptable** — ephemeral, in-memory only, not serialized. |
| `INVALID_TARGET` error path | **Complete** — allowlist check, 400 response, log warning. |
| Token logging | **Critical gap** — plaintext `access_token` in `sendTokenResponse` log (C2). |
| `iss`/`aud` validation bypass | **Risk** — `parseToken` fallback means OAuth2 tokens bypass `NimbusJwtDecoder` (W2). |
| Open audience mode (empty allowlist) | **Acceptable for dev** — startup warning specified. |
| X-Forwarded-* header trust | **Pre-existing** — no proxy allowlisting; out of scope for this PR. |

---

## Consistency Assessment

| Check | Status |
|-------|--------|
| `kid` value `"aj-rsa-key-1"` — JwksController vs JwtTokenProvider | **Consistent**. Both §2.2 and §3.1 use `"aj-rsa-key-1"`. Appendix C confirms. |
| `getBaseUrl` duplication noted | **Partially** — §2.3 notes the duplication with `OAuth2MetadataController` but misses `OAuth2TokenFilter` and `SecurityConfiguration` (W6). |
| `OAUTH2_TOKEN_EXPIRATION_MS` / `expires_in: 900` | **Consistent**. 900s = 15 min matches constant in `JwtTokenProvider` line 45. |
| `allowedExchangeAudiences` wiring | **Consistent** — §3.5.1 constructor, §3.6 injection, §6 properties all aligned. |
| Error code `invalid_target` — enum, switch, log | **Consistent** — §3.9 adds to enum, §3.5.3 uses `OAuth2Error.INVALID_TARGET`, §3.9 adds switch case. |

---

## Implementation Order Assessment

The 13-step order is sound. No circular dependency issues identified. One observation:

- **Step 2 (JwtTokenProvider)** modifies `parseToken`. As noted in W2, the `parseToken` fallback from `permissions` to `scopes` interacts with Step 10 (`JwtAuthenticationFilter`). This interaction is not called out in the step ordering. Add a note at Step 2 indicating that `parseToken`'s fallback behavior must be reconsidered for Step 10 compatibility.

- **Steps 3 and 9** (JwksController + OAuth2MetadataController) are independent of each other and could be done in either order; the spec's order is fine.

- **Missing Step 0**: Add `pom.xml` explicit dependency for `spring-security-oauth2-resource-server` (see C1).

---

## Backward Compatibility Assessment

| Concern | Spec Coverage |
|---------|--------------|
| HS256 → RS256 token invalidation at restart | **Adequate** — §1 explicitly states ephemeral key, tokens expire in 15 min. |
| Existing `JwtTokenProviderTests` hard-coded test secret | **Addressed** — §9.8 explicitly says update all tests to use `RsaKeyConfiguration`. |
| `app.jwt.secret` property removal | **Adequate** — §6 and Appendix B cover this. |
| Refresh token audience binding gap | **Not addressed** — see W4. |
| Pre-existing `TokenExchangeIntegrationTests` | **Not mentioned** — the existing tests in `TokenExchangeIntegrationTests.java` use `jwtTokenProvider.generateOAuth2Token(...)` directly. After the RS256 migration, these tests will still work (the method signature is unchanged) but will now use RSA signing. The test class should also have `generateTokenA()` verified still produces a valid parseable token under RS256. This is implicitly covered by §9.8 but should be explicit. |

---

## Top Findings Summary

| # | Severity | Finding | Section |
|---|----------|---------|---------|
| 1 | Critical | Plaintext token value logged in `sendTokenResponse` — pre-existing security hole, not fixed by spec | §3.5 |
| 2 | Critical | `NimbusJwtDecoder` relied on as transitive dep; should be explicit in `pom.xml` | Appendix A |
| 3 | Warning | `parseToken` fallback to `scopes` means NimbusJwtDecoder `iss`/`aud` validation silently skipped for OAuth2 tokens | §3.2 |
| 4 | Warning | Refreshed tokens lose `aud` binding — `handleRefreshTokenGrant` not updated | §3.5 (gap) |
| 5 | Warning | `toPublicJWKSet()` should replace `toString()` in JwksController for explicit safety | §2.2 |
| 6 | Warning | `NimbusJwtDecoder` bean method-call in filter constructor risks circular dependency | §3.6 |
| 7 | Warning | Four copies of `getBaseUrl` after change; spec only acknowledges one duplication | §2.3, §3.6 |
| 8 | Warning | Map.ofEntries 10-entry limit claim is factually wrong | §3.7 |
| 9 | Warning | Test stubs + missing test property config for allowlist integration tests | §9.6 |
| 10 | Warning | `determineError()` does not explicitly handle `resource` validation error message | §3.4 |
