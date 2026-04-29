# Implementation Plan: RFC 7662 Token Introspection + RFC 8693 Token Exchange

**Task**: Replace token passthrough anti-pattern with proper RSA-signed JWTs, RFC 7517 JWKS endpoint,
RFC 8707 Resource Indicators, RFC 8693 Token Exchange audience hardening, dual-path JWT validation,
and Protected Resource Metadata endpoint.

**Strategy**: Big bang — all mechanisms ship together in 4 task groups.

**Test command**: `mvnw.cmd test` (from repo root)

---

## Standards Compliance

| Standard | Area | Key Rules |
|----------|------|-----------|
| AGENTS.md §Backend | Java 25 + Spring Boot | `var` locals, records for DTOs, no `@Data` on JPA entities |
| AGENTS.md §Tests | Integration tests | Testcontainers + real PostgreSQL, `@Transactional`, `*IntegrationTests` / `*Tests` naming |
| AGENTS.md §REST | HTTP | Plural nouns, correct status codes, `@Valid` on request bodies |
| AGENTS.md §Errors | Exception handling | Typed exceptions, `GlobalExceptionHandler`, consistent `ErrorResponse` |

---

## Group 1: RSA Key Infrastructure

> Steps 1–3: `RsaKeyConfiguration`, `JwtTokenProvider` (RS256), `JwksController`

### 1.1 Write tests (TDD Red)

**File**: `src/test/java/pl/devstyle/aj/core/security/RsaKeyConfigurationTest.java`
- [ ] Create package-private class `RsaKeyConfigurationTest` — standard unit test, no Spring context needed
  - [ ] Test method: `rsaKeyPair_beansExist_returnsNonNullPublicAndPrivateKey`
    - Instantiate `RsaKeyConfiguration` directly; call `rsaKeyPair()`; assert `RSAPublicKey` and `RSAPrivateKey` beans are non-null
  - [ ] Test method: `rsaKeyPair_keysAreRsa2048_algorithmIsRSA`
    - Assert `getAlgorithm()` == `"RSA"` for both keys; assert `RSAPublicKey.getModulus().bitLength()` == 2048
  - [ ] Test method: `rsaPublicKeyBean_returnsSameKeyAsKeyPair`
    - Call both `rsaPublicKey(rsaKeyPair())` and `rsaKeyPair().getPublic()`; assert they are the same key object

**File**: `src/test/java/pl/devstyle/aj/core/security/JwtTokenProviderTests.java` (update existing)
- [ ] Add test method: `generateToken_withRsaProvider_producesRS256SignedJwt`
  - Build a `JwtTokenProvider` using an `RsaKeyConfiguration` key pair; generate a user session token; decode header (Base64url); assert `"alg":"RS256"`
- [ ] Add test method: `generateOAuth2Token_withRsaProvider_hasKidHeader`
  - Generate an OAuth2 token; decode JOSE header; assert `"kid":"aj-rsa-key-1"` is present
- [ ] Add test method: `parseToken_rsaSignedPermissionsToken_extractsPermissionsCorrectly`
  - Generate a user session token (RS256) with permissions `["READ", "EDIT"]`; call `parseToken()`; assert permissions set equals `{READ, EDIT}`
- [ ] Add test method: `parseToken_rsaSignedOAuth2Token_returnsEmptyBecauseNoPermissionsClaim`
  - Generate an OAuth2 token (RS256, `scopes` claim only); call `parseToken()`; assert `Optional.empty()` is returned (OAuth2 tokens must NOT be parsed via `parseToken`)
- [ ] Add test method: `validateToken_tampered_returnsFalse`
  - Take a valid RS256 token; corrupt the signature; assert `validateToken()` returns false
- [ ] **Compile check**: Run `mvnw.cmd test-compile` — expect failure because `RsaKeyConfiguration` and RS256 `JwtTokenProvider` do not exist yet

**File**: `src/test/java/pl/devstyle/aj/core/security/JwksControllerIntegrationTests.java` (new)
- [ ] Create package-private class `JwksControllerIntegrationTests` with standard integration test annotations:
  `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest(webEnvironment = MOCK)`, `@AutoConfigureMockMvc`, `@Transactional`
  - [ ] Test method: `getJwks_returnsRfc7517JwkSetWithRsaKey`
    - `GET /oauth2/jwks.json`; assert HTTP 200; `jsonPath("$.keys").isArray()`; `jsonPath("$.keys[0].kty").value("RSA")`; `jsonPath("$.keys[0].kid").value("aj-rsa-key-1")`; `jsonPath("$.keys[0].use").value("sig")`; `jsonPath("$.keys[0].alg").value("RS256")`
  - [ ] Test method: `getJwks_returnsPublicKeyFields_nAndE`
    - `GET /oauth2/jwks.json`; assert `$.keys[0].n` is non-null and non-empty; assert `$.keys[0].e` is non-null
  - [ ] Test method: `getJwks_doesNotRequireAuthentication_permitAll`
    - Perform `GET /oauth2/jwks.json` with no credentials; assert HTTP 200 (not 401)

---

### 1.2 `RsaKeyConfiguration`
**File**: `src/main/java/pl/devstyle/aj/core/security/RsaKeyConfiguration.java` *(new)*

- [ ] Create `@Configuration` class `RsaKeyConfiguration` in package `pl.devstyle.aj.core.security`
- [ ] Add `@Bean` method `rsaKeyPair()` returning `KeyPair`:
  - Use `KeyPairGenerator.getInstance("RSA")`; `generator.initialize(2048)`; return `generator.generateKeyPair()`
  - Annotate with `@Bean` — ephemeral key pair generated once per application start
- [ ] Add `@Bean` method `rsaPublicKey(KeyPair rsaKeyPair)` returning `RSAPublicKey`:
  - Return `(RSAPublicKey) rsaKeyPair.getPublic()`
- [ ] Add `@Bean` method `rsaPrivateKey(KeyPair rsaKeyPair)` returning `RSAPrivateKey`:
  - Return `(RSAPrivateKey) rsaKeyPair.getPrivate()`
- [ ] Add required imports: `java.security.KeyPair`, `java.security.KeyPairGenerator`, `java.security.interfaces.RSAPrivateKey`, `java.security.interfaces.RSAPublicKey`

---

### 1.3 `JwtTokenProvider` — replace HS256 with RS256
**File**: `src/main/java/pl/devstyle/aj/core/security/JwtTokenProvider.java` *(replace)*

- [ ] Remove field `SecretKey secretKey`; remove `@Value("${app.jwt.secret}")` constructor parameter; remove `Keys.hmacShaKeyFor(...)` line
- [ ] Add field `RSAPrivateKey privateKey` and field `RSAPublicKey publicKey`
- [ ] Change constructor signature to:
  `JwtTokenProvider(RSAPrivateKey privateKey, RSAPublicKey publicKey, @Value("${app.jwt.expiration-ms}") long expirationMs)`
- [ ] In constructor body: assign `this.privateKey = privateKey` and `this.publicKey = publicKey`
- [ ] Update `generateToken(String username, Set<String> permissions)`:
  - Replace `.signWith(secretKey)` → `.header().keyId("aj-rsa-key-1").and().signWith(privateKey)`
  - Remove `Base64` import if no longer used
- [ ] Update `generateTokenWithExpiration(String username, Set<String> permissions, long expirationMs)`:
  - Same `.header().keyId("aj-rsa-key-1").and().signWith(privateKey)` replacement
- [ ] Update `generateOAuth2Token(String username, Set<String> scopes, String issuer)` (no-audience overload):
  - Replace `.signWith(secretKey)` → `.header().keyId("aj-rsa-key-1").and().signWith(privateKey)`
- [ ] Update `generateOAuth2Token(String username, Set<String> scopes, String issuer, String audience)` (main overload):
  - Replace `.signWith(secretKey)` → `.header().keyId("aj-rsa-key-1").and().signWith(privateKey)`
- [ ] Update `parseRawClaims(String token)`:
  - Replace `.verifyWith(secretKey)` → `.verifyWith(publicKey)`
- [ ] Update `parseToken(String token)`:
  - Replace `.verifyWith(secretKey)` → `.verifyWith(publicKey)`
  - **CRITICAL (W2 fix)**: this method must only handle user-session tokens. Add check: after `parseSignedClaims`, if the payload has **no** `permissions` claim (i.e., `claims.get("permissions", List.class) == null`), return `Optional.empty()` immediately — OAuth2 tokens carry `scopes`, not `permissions`, and must not be validated here
- [ ] Update `getUsernameFromToken(String token)`:
  - Replace `.verifyWith(secretKey)` → `.verifyWith(publicKey)`
- [ ] Update `getPermissionsFromToken(String token)`:
  - Replace `.verifyWith(secretKey)` → `.verifyWith(publicKey)`
- [ ] Update `validateToken(String token)`:
  - Replace `.verifyWith(secretKey)` → `.verifyWith(publicKey)`
- [ ] Add imports: `java.security.interfaces.RSAPrivateKey`, `java.security.interfaces.RSAPublicKey`
- [ ] Remove imports: `javax.crypto.SecretKey`, `io.jsonwebtoken.security.Keys`, `java.util.Base64`

---

### 1.4 `JwksController`
**File**: `src/main/java/pl/devstyle/aj/core/security/JwksController.java` *(new)*

- [ ] Create `@RestController` class `JwksController` in package `pl.devstyle.aj.core.security`
- [ ] Inject `RSAPublicKey rsaPublicKey` via constructor
- [ ] Add `@GetMapping(value = "/oauth2/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)` method `getJwks()` returning `Map<String, Object>`:
  - Extract Base64url-encoded modulus: `Base64.getUrlEncoder().withoutPadding().encodeToString(rsaPublicKey.getModulus().toByteArray())`  
    **Note**: `BigInteger.toByteArray()` may produce a leading zero byte for positive numbers; strip it if `bytes[0] == 0` and `bytes.length > 1`
  - Extract Base64url-encoded public exponent: same encoding of `rsaPublicKey.getPublicExponent().toByteArray()`
  - Return: `Map.of("keys", List.of(Map.of("kty","RSA","use","sig","alg","RS256","kid","aj-rsa-key-1","n", <modulus>,"e", <exponent>)))`
- [ ] Add imports: `java.math.BigInteger`, `java.security.interfaces.RSAPublicKey`, `java.util.Base64`, `java.util.List`, `java.util.Map`

---

### 1.5 Run Group 1 tests
- [ ] Run: `mvnw.cmd test -Dtest=RsaKeyConfigurationTest,JwtTokenProviderTests,JwksControllerIntegrationTests`
- [ ] All tests in these 3 classes pass (including the new RS256 tests)
- [ ] Verify existing `TokenExchangeIntegrationTests` and `OAuth2IntrospectionTests` still compile (they inject `JwtTokenProvider` — constructor signature changed, but Spring wiring via `@Autowired` is unaffected)

---

## Group 2: Auth Code + Error Foundation

> Steps 4–6: `OAuth2Error` (INVALID_TARGET), `AuthorizationCodeService` (`resourceUri`), `OAuth2AuthorizationFilter` (RFC 8707)

### 2.1 Write tests (TDD Red)

**File**: `src/test/java/pl/devstyle/aj/core/oauth2/OAuth2AuthorizationFilterIntegrationTests.java` *(new)*
- [ ] Create package-private class `OAuth2AuthorizationFilterIntegrationTests` with standard integration test annotations
- [ ] Add helper `loginAndGetToken(String username, String password)` (same pattern as existing tests)
- [ ] Add helper `registerPublicClient(String redirectUri, String scope)` returning `clientId`
- [ ] Add test method: `authorize_withValidResourceParam_storesResourceUri`
  - Register client; login; POST `/oauth2/authorize` with `resource=http://localhost/api`; assert HTTP 302 (redirect with code)
- [ ] Add test method: `authorize_withInvalidResourceParam_nonAbsoluteUri_returns400`
  - POST `/oauth2/authorize` with `resource=not-a-uri`; assert HTTP 400; `jsonPath("$.error").value("invalid_target")`
- [ ] Add test method: `authorize_withoutResourceParam_succeeds_resourceUriIsNull`
  - POST `/oauth2/authorize` without `resource` param; assert HTTP 302 (backward compatibility preserved)
- [ ] Add test method: `authorize_resourceUri_propagatesToAccessToken_audClaim`
  - Full cycle: authorize with `resource=http://localhost/api`; exchange code for token; assert the access token's `aud` claim contains `http://localhost/api`
  - Parse the access token JWT header+payload manually (Base64url decode the payload part; parse JSON; assert `aud` field)

---

### 2.2 `OAuth2Error` — add `INVALID_TARGET`
**File**: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2Error.java` *(update)*

- [ ] Add new enum constant after `INVALID_CLIENT_METADATA`:
  ```java
  INVALID_TARGET("invalid_target", "The requested resource is invalid or the authorization server does not support the requested resource URI")
  ```
- [ ] Verify `sendError` switch in `OAuth2TokenFilter` and `writeError` in `OAuth2AuthorizationFilter` compile cleanly — both use `default` branches, no exhaustive switch, no change required

---

### 2.3 `AuthorizationCodeService` — add `resourceUri` to `AuthorizationCodeData`
**File**: `src/main/java/pl/devstyle/aj/core/oauth2/AuthorizationCodeService.java` *(update)*

- [ ] Add `resourceUri` parameter (type `String`, nullable) to the `AuthorizationCodeData` record at the end, after `createdAt`:
  ```java
  public record AuthorizationCodeData(
      String clientId, String redirectUri, String scope,
      String codeChallenge, String codeChallengeMethod,
      String username, Set<String> permissions,
      Instant createdAt,
      String resourceUri   // RFC 8707 — may be null
  ) {}
  ```
- [ ] Update `storeAuthorizationCode` signature to add `String resourceUri` as the last parameter (after `permissions`)
- [ ] Update the `AuthorizationCodeData` constructor call inside `storeAuthorizationCode` to include `resourceUri` as the last argument
- [ ] Compile check: `OAuth2AuthorizationFilter` calls `storeAuthorizationCode` — must be updated in step 2.4 below

---

### 2.4 `OAuth2AuthorizationFilter` — read `resource` param (RFC 8707)
**File**: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2AuthorizationFilter.java` *(update)*

- [ ] In `handleAuthorizationRequest`, read `resource` parameter: `String resource = request.getParameter("resource");`
- [ ] After the existing `validatePkceParameters(...)` call, add `resource` validation:
  ```java
  if (resource != null && !resource.isBlank()) {
      try {
          var uri = new java.net.URI(resource);
          if (!uri.isAbsolute()) {
              throw new IllegalArgumentException("resource parameter must be an absolute URI");
          }
      } catch (java.net.URISyntaxException e) {
          throw new IllegalArgumentException("resource parameter is not a valid URI");
      }
  }
  ```
- [ ] Update `authorizationCodeService.storeAuthorizationCode(...)` call to pass `resource` (the validated value, possibly null) as the last argument
- [ ] Update `determineError` method to handle the resource error message:
  ```java
  if (message.contains("resource")) return OAuth2Error.INVALID_TARGET;
  ```
  — add this **before** the existing `if (message.contains("redirect_uri"))` line so it takes priority
- [ ] Add import `java.net.URI`, `java.net.URISyntaxException` (or use fully qualified names inline as shown above)

---

### 2.5 Run Group 2 tests
- [ ] Run: `mvnw.cmd test -Dtest=OAuth2AuthorizationFilterIntegrationTests`
- [ ] All 4 tests in this class pass
- [ ] Run: `mvnw.cmd test -Dtest=OAuth2IntegrationTests` — ensure existing authorization flow tests still pass (backward compatibility — `resource` param was added as optional)

---

## Group 3: Token Issuance Hardening

> Steps 7–9: `OAuth2TokenFilter` hardening, `ProtectedResourceMetadataController`, `OAuth2MetadataController` (`jwks_uri`)

### 3.1 Write tests (TDD Red)

**File**: `src/test/java/pl/devstyle/aj/core/oauth2/TokenExchangeIntegrationTests.java` *(update existing)*
- [ ] Add test method: `authorizationCodeGrant_withResourceUri_accessTokenHasAudClaim`
  - Full authorization code flow with `resource=http://localhost/api`; exchange code; Base64url-decode access token payload JSON; assert `aud` field equals `["http://localhost/api"]` or the string `"http://localhost/api"`
- [ ] Add test method: `refreshTokenGrant_preservesAudienceFromOriginalToken`
  - Full authorization code flow with `resource=http://localhost/api`; exchange for access token + refresh token; use refresh token to get new access token; decode new access token payload; assert `aud` field is still `http://localhost/api`
- [ ] Add test method: `tokenExchange_tokenBHasAudEqualToAudienceParam`
  - Token exchange (RFC 8693) with `audience=http://localhost/api`; assert the returned Token-B's JWT payload has `aud=http://localhost/api`
- [ ] Add test method: `tokenExchange_tokenBWithoutAudienceParam_audFallsBackToIssuer`
  - Token exchange without `audience` param; assert Token-B `aud` equals issuer URL (extracted from `iss` claim or request base URL)
- [ ] Add test method: `tokenResponse_doesNotContainTokenInLogOutput` *(compile-only guard — relies on absence of logging in production code)*
  - This is a sentinel: ensure `sendTokenResponse` does NOT include `log.info("OAuth2 token response body: {}", tokenResponseBody)` — validate by checking that the log line was removed (test asserts the endpoint returns HTTP 200 with a valid body; no runtime assertion for log output needed here — the real guard is code review)

**File**: `src/test/java/pl/devstyle/aj/core/oauth2/ProtectedResourceMetadataControllerIntegrationTests.java` *(new)*
- [ ] Create package-private class `ProtectedResourceMetadataControllerIntegrationTests` with standard integration test annotations
  - [ ] Test method: `getPrm_returnsRfc9728Fields`
    - `GET /.well-known/oauth-protected-resource`; assert HTTP 200; `jsonPath("$.resource").isString()`; `jsonPath("$.authorization_servers").isArray()`; `jsonPath("$.jwks_uri").value(containsString("/oauth2/jwks.json"))`; `jsonPath("$.bearer_methods_supported").value(hasItem("header"))`
  - [ ] Test method: `getPrm_doesNotRequireAuthentication`
    - `GET /.well-known/oauth-protected-resource` with no credentials; assert HTTP 200
  - [ ] Test method: `getPrm_resourceFieldMatchesServerBaseUrl`
    - Assert `$.resource` is a non-empty string starting with `http`

**File**: `src/test/java/pl/devstyle/aj/core/oauth2/OAuth2MetadataControllerIntegrationTests.java` *(new)*
- [ ] Create package-private class `OAuth2MetadataControllerIntegrationTests` with standard integration test annotations
  - [ ] Test method: `getAuthorizationServerMetadata_includesJwksUri`
    - `GET /.well-known/oauth-authorization-server`; assert `jsonPath("$.jwks_uri").value(containsString("/oauth2/jwks.json"))`
  - [ ] Test method: `getAuthorizationServerMetadata_jwksUriIsAccessible`
    - `GET /.well-known/oauth-authorization-server`; extract `jwks_uri`; strip base URL prefix; perform `GET` on the path portion; assert HTTP 200

---

### 3.2 `OAuth2TokenFilter` — audience hardening + remove token body logging (C2)
**File**: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2TokenFilter.java` *(update)*

**Constructor change (W4 + allowlist)**:
- [ ] Add field `Set<String> allowedExchangeAudiences` of type `Set<String>`
- [ ] Add `Set<String> allowedExchangeAudiences` as last constructor parameter
- [ ] In constructor body: `this.allowedExchangeAudiences = allowedExchangeAudiences != null ? Set.copyOf(allowedExchangeAudiences) : Set.of()`
- [ ] Note: `SecurityConfiguration` must pass this in step 4.5

**`handleAuthorizationCodeGrant` — Token-A audience from `resourceUri` (W4)**:
- [ ] Retrieve `resourceUri` from consumed authorization code data: `String resourceUri = data.resourceUri();`
- [ ] Pass `resourceUri` as the audience to `jwtTokenProvider.generateOAuth2Token(data.username(), grantedScopes, issuer, resourceUri)` — replaces the existing call that omits the audience
- [ ] Update `refreshTokenService.issueToken(...)` call: pass `resourceUri` (so refresh token rotation can preserve it):
  - Check `RefreshTokenData` record — add `resourceUri` field there (step 3.3 below); update call: `refreshTokenService.issueToken(data.username(), grantedScopes, data.scope(), resourceUri)`
- [ ] **Remove** line: `log.info("OAuth2 token issued | ... | access_token={} | refresh_token={}`, ...)` — replace with a version that does NOT include token values:
  `log.info("OAuth2 token issued | client_id={} | user={} | scope={}", data.clientId(), data.username(), data.scope());`

**`handleRefreshTokenGrant` — preserve audience from stored refresh token (W4)**:
- [ ] After `var data = rotationResult.originalData()`, extract `String resourceUri = data.resourceUri();`
- [ ] Replace `jwtTokenProvider.generateOAuth2Token(data.username(), grantedScopes, issuer)` → `jwtTokenProvider.generateOAuth2Token(data.username(), grantedScopes, issuer, resourceUri)`

**`handleTokenExchangeGrant` — Token-B audience from `audience` param (spec step 7)**:
- [ ] Read `audience` param: `String audienceParam = request.getParameter("audience");`
- [ ] Determine effective audience:
  ```java
  String tokenBAudience = (audienceParam != null && !audienceParam.isBlank()) ? audienceParam : issuer;
  ```
- [ ] If `allowedExchangeAudiences` is non-empty, validate:
  ```java
  if (!allowedExchangeAudiences.isEmpty() && !allowedExchangeAudiences.contains(tokenBAudience)) {
      sendError(response, OAuth2Error.INVALID_TARGET, "Audience not in allowed list: " + tokenBAudience);
      return;
  }
  ```
- [ ] Replace `jwtTokenProvider.generateOAuth2Token(parsedToken.get().username(), mappedPermissions, issuer, issuer)` → `jwtTokenProvider.generateOAuth2Token(parsedToken.get().username(), mappedPermissions, issuer, tokenBAudience)`

**`sendTokenResponse` — remove token body logging (C2)**:
- [ ] Delete the line: `log.info("OAuth2 token response body: {}", tokenResponseBody);`
- [ ] The method should write the response body to the output stream without logging the token values

**`sendError` switch — add `INVALID_TARGET` case**:
- [ ] Add `case INVALID_TARGET -> HttpServletResponse.SC_BAD_REQUEST;` in the switch expression (before or after `default`)

---

### 3.3 `RefreshTokenService` — add `resourceUri` to `RefreshTokenData` (W4)
**File**: `src/main/java/pl/devstyle/aj/core/oauth2/RefreshTokenService.java` *(update)*

- [ ] Add `resourceUri` (type `String`, nullable) as last field in `RefreshTokenData` record:
  ```java
  public record RefreshTokenData(
      String username, Set<String> permissions, String scope,
      Instant createdAt, String resourceUri
  ) {}
  ```
- [ ] Update `issueToken` signature to accept `String resourceUri` as last parameter
- [ ] Update `RefreshTokenData` constructor call inside `issueToken` to include `resourceUri`
- [ ] Update `consumeAndRotate`: when building `newData` for the rotated token, preserve `resourceUri`:
  `var newData = new RefreshTokenData(data.username(), data.permissions(), data.scope(), Instant.now(), data.resourceUri());`

---

### 3.4 `ProtectedResourceMetadataController`
**File**: `src/main/java/pl/devstyle/aj/core/oauth2/ProtectedResourceMetadataController.java` *(new)*

- [ ] Create `@RestController` class `ProtectedResourceMetadataController` in package `pl.devstyle.aj.core.oauth2`
- [ ] Add `@GetMapping(value = "/.well-known/oauth-protected-resource", produces = MediaType.APPLICATION_JSON_VALUE)` method `getProtectedResourceMetadata(HttpServletRequest request)` returning `Map<String, Object>`:
  - Compute base URL using the same `getBaseUrl(HttpServletRequest)` helper (copy from `OAuth2MetadataController` or extract a shared utility — copy for now to avoid coupling)
  - Return:
    ```java
    Map.ofEntries(
        entry("resource", baseUrl),
        entry("authorization_servers", new String[]{ baseUrl + "/.well-known/oauth-authorization-server" }),
        entry("jwks_uri", baseUrl + "/oauth2/jwks.json"),
        entry("bearer_methods_supported", new String[]{ "header" }),
        entry("resource_documentation", baseUrl + "/api/health")
    )
    ```
- [ ] Add imports: `jakarta.servlet.http.HttpServletRequest`, `org.springframework.http.MediaType`, `java.util.Map`, `static java.util.Map.entry`

---

### 3.5 `OAuth2MetadataController` — add `jwks_uri`
**File**: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2MetadataController.java` *(update)*

- [ ] In `getAuthorizationServerMetadata`, add `entry("jwks_uri", baseUrl + "/oauth2/jwks.json")` to the `Map.ofEntries(...)` return value
- [ ] Note: `Map.ofEntries` supports up to any number of entries; the existing map has 9 entries, adding one more to make 10 is fine

---

### 3.6 Run Group 3 tests
- [ ] Run: `mvnw.cmd test -Dtest=TokenExchangeIntegrationTests,ProtectedResourceMetadataControllerIntegrationTests,OAuth2MetadataControllerIntegrationTests,OAuth2IntrospectionTests`
- [ ] All tests in these 4 classes pass
- [ ] Run: `mvnw.cmd test -Dtest=OAuth2IntegrationTests` — ensure existing authorization flow tests still pass

---

## Group 4: Filter Chain + Configuration

> Steps 10–13: `JwtAuthenticationFilter` dual-path, `SecurityConfiguration` wiring, `application.properties`, `pom.xml`, full suite

### 4.1 Write tests (TDD Red)

**File**: `src/test/java/pl/devstyle/aj/core/security/SecurityConfigurationIntegrationTests.java` *(new)*
- [ ] Create package-private class `SecurityConfigurationIntegrationTests` with standard integration test annotations
  - [ ] Test method: `jwksEndpoint_isPublic_noAuthRequired`
    - `GET /oauth2/jwks.json` without credentials; assert HTTP 200
  - [ ] Test method: `prmEndpoint_isPublic_noAuthRequired`
    - `GET /.well-known/oauth-protected-resource` without credentials; assert HTTP 200
  - [ ] Test method: `protectedEndpoint_with401_hasWwwAuthenticateHeader`
    - `GET /api/categories` without credentials; assert HTTP 401; assert response header `WWW-Authenticate` contains `Bearer`
  - [ ] Test method: `oauth2TokenB_canAccessProtectedEndpoint`
    - Perform full token exchange to obtain Token-B; use Token-B as `Authorization: Bearer <token>`; `GET /api/categories`; assert HTTP 200 (NimbusJwtDecoder path validates the RS256 OAuth2 token, sets `scopes`-based authorities)
  - [ ] Test method: `userSessionToken_canAccessProtectedEndpoint`
    - Login to get a user session JWT; use it as `Authorization: Bearer <token>`; `GET /api/categories`; assert HTTP 200 (jjwt `permissions` path via `parseToken`)
  - [ ] Test method: `oauth2TokenA_withMcpScopes_canAccessEndpoint`
    - Generate Token-A (OAuth2, `mcp:read` scope, RS256); use as `Authorization: Bearer <token>`; `GET /api/categories`; assert HTTP 200

**File**: `src/test/java/pl/devstyle/aj/core/security/JwtAuthenticationFilterDualPathTests.java` *(new)*
- [ ] Create package-private class `JwtAuthenticationFilterDualPathTests` — unit test, no Spring context
  - [ ] Test method: `filter_tokenWithPermissionsClaim_usesJjwtPath_setsPermissionAuthorities`
    - Build a `JwtTokenProvider` using `RsaKeyConfiguration`; generate a user session token (has `permissions` claim); call `doFilterInternal` on a mock request/response; assert `SecurityContextHolder` contains authorities prefixed with `PERMISSION_`
  - [ ] Test method: `filter_tokenWithKidAjRsaKey1_noPermissionsClaim_usesNimbusPath`
    - Build `JwtAuthenticationFilter` with a `NimbusJwtDecoder` configured with the test RSA public key; present an OAuth2 token (`kid=aj-rsa-key-1`, `scopes` claim); assert `SecurityContextHolder` contains authorities prefixed with `SCOPE_`
  - [ ] Test method: `filter_noToken_proceedsWithoutAuthentication`
    - Present request with no Authorization header; assert filter chain proceeds; `SecurityContextHolder.getContext().getAuthentication()` is null

---

### 4.2 `JwtAuthenticationFilter` — dual-path validation (W2)
**File**: `src/main/java/pl/devstyle/aj/core/security/JwtAuthenticationFilter.java` *(replace)*

- [ ] Add field `NimbusJwtDecoder nimbusJwtDecoder` alongside `JwtTokenProvider jwtTokenProvider`
- [ ] Update constructor to accept both:
  ```java
  public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, NimbusJwtDecoder nimbusJwtDecoder) {
      this.jwtTokenProvider = jwtTokenProvider;
      this.nimbusJwtDecoder = nimbusJwtDecoder;
  }
  ```
- [ ] In `doFilterInternal`, after extracting `token`, implement dual-path logic:
  ```java
  if (token != null) {
      if (isOAuth2Token(token)) {
          // NimbusJwtDecoder path — validates RS256, checks issuer, sets SCOPE_ authorities
          handleOAuth2Token(token, response);
      } else {
          // jjwt parseToken path — validates RS256, requires permissions claim
          jwtTokenProvider.parseToken(token).ifPresent(parsed -> {
              var authorities = parsed.permissions().stream()
                      .map(p -> new SimpleGrantedAuthority("PERMISSION_" + p))
                      .toList();
              var auth = new UsernamePasswordAuthenticationToken(parsed.username(), null, authorities);
              SecurityContextHolder.getContext().setAuthentication(auth);
          });
      }
  }
  filterChain.doFilter(request, response);
  ```
- [ ] Add private method `isOAuth2Token(String token)`:
  - Decode the JOSE header (split by `.`, Base64url-decode index 0, parse JSON): check if `kid` field equals `"aj-rsa-key-1"` — if yes, it's an OAuth2 token
  - Use `java.util.Base64` URL decoder + simple string check to avoid full JSON parse:
    ```java
    private boolean isOAuth2Token(String token) {
        try {
            var parts = token.split("\\.");
            if (parts.length < 2) return false;
            var headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            return headerJson.contains("\"kid\":\"aj-rsa-key-1\"");
        } catch (Exception e) {
            return false;
        }
    }
    ```
- [ ] Add private method `handleOAuth2Token(String token, HttpServletResponse response)`:
  - Wrap `nimbusJwtDecoder.decode(token)` in try-catch (`JwtException`)
  - On success: extract `scopes` claim (type `List<String>` or `String`); map each scope to `SimpleGrantedAuthority("SCOPE_" + scope)`; get subject from `jwt.getSubject()`; set `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`
  - On failure: do NOT set authentication; let the request proceed unauthenticated (downstream security rules will reject if endpoint requires auth) — **do NOT fall through to jjwt path**
- [ ] Add imports: `org.springframework.security.oauth2.jwt.Jwt`, `org.springframework.security.oauth2.jwt.JwtException`, `org.springframework.security.oauth2.jwt.NimbusJwtDecoder`

---

### 4.3 `pom.xml` — add `spring-security-oauth2-resource-server` dependency (C1)
**File**: `pom.xml` *(update)*

- [ ] Add the following dependency inside `<dependencies>` (after the existing `spring-security-oauth2-authorization-server` dependency):
  ```xml
  <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-oauth2-resource-server</artifactId>
  </dependency>
  ```
  — no version needed; Spring Boot BOM manages it
- [ ] Compile check: `mvnw.cmd test-compile -DskipTests` — verify `NimbusJwtDecoder` resolves from classpath

---

### 4.4 `application.properties` — update configuration
**File**: `src/main/resources/application.properties` *(update)*

- [ ] Remove line: `app.jwt.secret=${APP_JWT_SECRET:i/WZnrbvFqiPfShuZjGmc5kC7IXxRZfpueJEdgCzGFc=}`  
  — RSA key pair is now generated at startup; no secret needed
- [ ] Add line: `app.oauth2.allowed-exchange-audiences=`  
  — empty by default means allowlist is disabled (all audiences accepted); operators can set comma-separated URIs in production
- [ ] Add line: `app.jwt.expected-issuer=`  
  — empty by default; `NimbusJwtDecoder` issuer validation is skipped when blank; operators set to their base URL in production (e.g. `https://myapp.example.com`)
- [ ] Keep line: `app.jwt.expiration-ms=86400000` — unchanged

---

### 4.5 `SecurityConfiguration` — wire everything together (W3)
**File**: `src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java` *(update)*

**New fields and constructor parameters**:
- [ ] Add field `RSAPublicKey rsaPublicKey`
- [ ] Add field `List<String> allowedExchangeAudiences` (injected from `@Value("${app.oauth2.allowed-exchange-audiences:}")`)
- [ ] Add field `String expectedIssuer` (injected from `@Value("${app.jwt.expected-issuer:}")`)
- [ ] Remove field `@Value("${app.jwt.secret}")` if any (already gone after Group 1)
- [ ] Update constructor to add: `RSAPublicKey rsaPublicKey`, `@Value("${app.oauth2.allowed-exchange-audiences:}") List<String> allowedExchangeAudiences`, `@Value("${app.jwt.expected-issuer:}") String expectedIssuer`
- [ ] Assign new fields in constructor body

**Add `nimbusJwtDecoder` `@Bean` (W3 — avoid self-call circular dependency)**:
- [ ] Add a new `@Bean` method `nimbusJwtDecoder(RSAPublicKey rsaPublicKey)` returning `NimbusJwtDecoder`:
  ```java
  @Bean
  public NimbusJwtDecoder nimbusJwtDecoder(RSAPublicKey rsaPublicKey) {
      var decoder = NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
      // Issuer validation is optional — skip if expectedIssuer is blank
      if (expectedIssuer != null && !expectedIssuer.isBlank()) {
          decoder.setJwtValidator(
              JwtValidators.createDefaultWithIssuer(expectedIssuer));
      }
      return decoder;
  }
  ```
- [ ] Add imports: `org.springframework.security.oauth2.jwt.NimbusJwtDecoder`, `org.springframework.security.oauth2.jwt.JwtValidators`

**Update `securityFilterChain` signature (W3)**:
- [ ] Change method signature to inject `NimbusJwtDecoder nimbusJwtDecoder` as a parameter:
  ```java
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, NimbusJwtDecoder nimbusJwtDecoder) throws Exception {
  ```
  — Spring will inject the `NimbusJwtDecoder` bean from the `@Bean` method above; no self-call risk

**Update filter instantiations**:
- [ ] Replace `new JwtAuthenticationFilter(jwtTokenProvider)` → `new JwtAuthenticationFilter(jwtTokenProvider, nimbusJwtDecoder)`
- [ ] Pass `allowedExchangeAudiences` (as `Set.copyOf(allowedExchangeAudiences)`) to `OAuth2TokenFilter` constructor:
  - Update the `new OAuth2TokenFilter(...)` call to add `new java.util.HashSet<>(allowedExchangeAudiences)` as last argument
  - (Note: the `allowedExchangeAudiences` field is a `List<String>` due to Spring's multi-value injection; `OAuth2TokenFilter` accepts `Set<String>`)

**Add permit-all for new public endpoints**:
- [ ] In the `authorizeHttpRequests` chain, add before the existing OAuth2 server endpoint permits:
  ```java
  .requestMatchers(HttpMethod.GET, "/oauth2/jwks.json").permitAll()
  .requestMatchers(HttpMethod.GET, "/.well-known/oauth-protected-resource").permitAll()
  ```

**Add `WWW-Authenticate` header on 401**:
- [ ] In the `authenticationEntryPoint` lambda, after `response.setStatus(HttpStatus.UNAUTHORIZED.value())`, add:
  ```java
  response.setHeader("WWW-Authenticate", "Bearer realm=\"aj\", error=\"unauthorized\"");
  ```

**Remove `app.jwt.secret` value reference**:
- [ ] Remove `@Value("${app.jwt.secret}")` annotation/field if it existed as a `SecurityConfiguration` field (it did not — it was in `JwtTokenProvider`; verify no lingering reference in this class)

---

### 4.6 Update existing tests broken by constructor and API changes

**File**: `src/test/java/pl/devstyle/aj/core/oauth2/TokenExchangeIntegrationTests.java` *(update)*
- [ ] The `generateTokenA()` helper calls `jwtTokenProvider.generateOAuth2Token("admin", Set.of("mcp:read", "mcp:edit"), "http://localhost")` — this still works (no-audience overload unchanged); **no change required** if the overload is preserved
- [ ] The `tokenExchange_scopeMapping_mapsKnownScopesAndDropsUnknown` test calls `jwtTokenProvider.parseToken(tokenB)` to verify Token-B permissions — Token-B has `permissions` claim (mapped from MCP scopes), so this still works with updated `parseToken`; **verify logic is correct**: Token-B has `permissions` claim → `parseToken` returns a result → test passes

**File**: `src/test/java/pl/devstyle/aj/core/oauth2/OAuth2IntrospectionTests.java` *(update)*
- [ ] The `endToEnd_introspectThenExchange_producesTokenBWithMappedPermissions` test calls `jwtTokenProvider.parseToken(tokenB)` — same reasoning as above; Token-B has `permissions` claim → parseToken works; **no change required**

**File**: `src/test/java/pl/devstyle/aj/core/security/JwtTokenProviderTests.java` *(update)*
- [ ] Remove the old `private final JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS)` field — constructor signature changed
- [ ] Replace with:
  ```java
  private final RsaKeyConfiguration rsaKeyConfig = new RsaKeyConfiguration();
  private final java.security.KeyPair keyPair = rsaKeyConfig.rsaKeyPair();
  private final JwtTokenProvider provider = new JwtTokenProvider(
      (java.security.interfaces.RSAPrivateKey) keyPair.getPrivate(),
      (java.security.interfaces.RSAPublicKey) keyPair.getPublic(),
      86400000L);
  ```
- [ ] Remove `TEST_SECRET` constant (no longer needed)
- [ ] Keep the existing 4 tests; they already test RS256 behavior once the provider is RS256-backed
- [ ] The new tests added in step 1.1 (`generateOAuth2Token_withRsaProvider_hasKidHeader`, etc.) should now pass

---

### 4.7 Run Group 4 tests + full suite
- [ ] Run: `mvnw.cmd test -Dtest=SecurityConfigurationIntegrationTests,JwtAuthenticationFilterDualPathTests,JwtTokenProviderTests`
- [ ] All tests in these 3 classes pass

**Full suite**:
- [ ] Run: `mvnw.cmd test`
- [ ] All tests pass — including:
  - `RsaKeyConfigurationTest`
  - `JwtTokenProviderTests`
  - `JwksControllerIntegrationTests`
  - `OAuth2AuthorizationFilterIntegrationTests`
  - `TokenExchangeIntegrationTests`
  - `OAuth2IntrospectionTests`
  - `ProtectedResourceMetadataControllerIntegrationTests`
  - `OAuth2MetadataControllerIntegrationTests`
  - `SecurityConfigurationIntegrationTests`
  - `JwtAuthenticationFilterDualPathTests`
  - `OAuth2IntegrationTests` (existing — regression guard)
  - `AuthIntegrationTests` (existing — user-session JWT path regression guard)
  - All other existing test classes

---

## Summary of Files Changed

| File | Action |
|------|--------|
| `pom.xml` | Add `spring-security-oauth2-resource-server` dependency |
| `src/main/resources/application.properties` | Remove `app.jwt.secret`; add `app.oauth2.allowed-exchange-audiences` + `app.jwt.expected-issuer` |
| `core/security/RsaKeyConfiguration.java` | **New** — ephemeral RSA-2048 key pair beans |
| `core/security/JwtTokenProvider.java` | Replace HS256/SecretKey with RS256/RSA; fix `parseToken` to require `permissions` claim |
| `core/security/JwksController.java` | **New** — `GET /oauth2/jwks.json` RFC 7517 JWK Set |
| `core/security/JwtAuthenticationFilter.java` | Dual-path: `kid=aj-rsa-key-1` → NimbusJwtDecoder; else → jjwt `parseToken` |
| `core/security/SecurityConfiguration.java` | Inject `NimbusJwtDecoder` `@Bean`; inject `RSAPublicKey`; inject allowlist; permit JWKS+PRM; WWW-Authenticate header; pass `NimbusJwtDecoder` to `JwtAuthenticationFilter` |
| `core/oauth2/OAuth2Error.java` | Add `INVALID_TARGET` enum value |
| `core/oauth2/AuthorizationCodeService.java` | Add `resourceUri` field to `AuthorizationCodeData` record and `storeAuthorizationCode` signature |
| `core/oauth2/OAuth2AuthorizationFilter.java` | Read + validate `resource` param (RFC 8707); pass to service; map to `INVALID_TARGET` error |
| `core/oauth2/RefreshTokenService.java` | Add `resourceUri` field to `RefreshTokenData`; preserve through rotation |
| `core/oauth2/OAuth2TokenFilter.java` | Fix Token-B aud (audience param or issuer fallback); allowlist validation; Token-A aud from resourceUri; refresh preserves aud; remove token body logging |
| `core/oauth2/ProtectedResourceMetadataController.java` | **New** — `GET /.well-known/oauth-protected-resource` |
| `core/oauth2/OAuth2MetadataController.java` | Add `jwks_uri` field to authorization server metadata |

| Test File | Action |
|-----------|--------|
| `core/security/RsaKeyConfigurationTest.java` | **New** |
| `core/security/JwtTokenProviderTests.java` | Update — RS256 constructor; add kid + OAuth2 path tests |
| `core/security/JwksControllerIntegrationTests.java` | **New** |
| `core/security/JwtAuthenticationFilterDualPathTests.java` | **New** |
| `core/security/SecurityConfigurationIntegrationTests.java` | **New** |
| `core/oauth2/OAuth2AuthorizationFilterIntegrationTests.java` | **New** |
| `core/oauth2/TokenExchangeIntegrationTests.java` | Update — add audience + refresh aud tests |
| `core/oauth2/ProtectedResourceMetadataControllerIntegrationTests.java` | **New** |
| `core/oauth2/OAuth2MetadataControllerIntegrationTests.java` | **New** |
