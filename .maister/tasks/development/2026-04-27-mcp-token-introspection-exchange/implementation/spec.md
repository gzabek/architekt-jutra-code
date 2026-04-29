# MCP Token Introspection & Exchange — Technical Specification

**Status**: Implementation-Ready  
**Date**: 2026-04-27  
**Package root**: `pl.devstyle.aj`  
**Stack**: Java 25, Spring Boot 4.0.5

---

## 1. Overview

### What Is Being Built

The current OAuth2 implementation violates the MCP specification (2025-11-25), which explicitly forbids token passthrough — forwarding a client-facing Bearer token directly to upstream resource servers. Two specific bugs drive this work:

1. **Token exchange sets `aud = issuer URL`** instead of the upstream resource URI, making Token-B unusable for audience-restricted resource servers.
2. **No RFC 9728 Protected Resource Metadata (PRM) endpoint** exists, so MCP clients cannot discover the authorization server URL automatically.

Additionally, all tokens are signed with HMAC-SHA256 (symmetric secret), which cannot be verified by external parties without sharing the secret. Migrating to RS256 (asymmetric RSA) allows any resource server to verify token signatures using the public JWKS endpoint — a prerequisite for compliant token introspection.

### What Ships

All changes ship together as a single big-bang release:

| # | Change | RFC |
|---|--------|-----|
| 1 | RSA-2048 key pair generated at startup; replaces HMAC secret for token signing | ADR-001 |
| 2 | `GET /oauth2/jwks.json` — public JWKS endpoint | RFC 7517 |
| 3 | `GET /.well-known/oauth-protected-resource` — PRM endpoint | RFC 9728 / ADR-003 |
| 4 | `WWW-Authenticate: Bearer resource_metadata=…` on 401 | RFC 9728 |
| 5 | `resource` parameter in authorize flow binds audience to Token-A | RFC 8707 / ADR-004 |
| 6 | Token exchange reads `audience` param; validates against allowlist; sets Token-B `aud = audience` | RFC 8693 / ADR-002 |
| 7 | `JwtAuthenticationFilter` migrates to `NimbusJwtDecoder` (RSA public key) | ADR-001 |
| 8 | `OAuth2IntrospectionFilter` updated for RS256 tokens | ADR-001 |

### What Does NOT Change

- Auth code storage stays in-memory (`ConcurrentHashMap`) — no DB migration.
- RSA key is ephemeral (generated at startup, not persisted to KeyStore).
- `jjwt` library is retained for `parseToken` fallback paths that read `permissions` claim; Nimbus handles all new RS256 paths.
- Existing `PKCE`, `refresh_token`, `authorization_code` grant mechanics are untouched except the `aud` binding change.
- No new database tables or Liquibase migrations.

---

## 2. New Components

### 2.1 `RsaKeyConfiguration`

**File**: `src/main/java/pl/devstyle/aj/core/security/RsaKeyConfiguration.java`  
**Package**: `pl.devstyle.aj.core.security`

**Purpose**: Generates an ephemeral RSA-2048 key pair at application startup and exposes it as Spring beans. All components that need signing or verification inject these beans.

```java
@Configuration
public class RsaKeyConfiguration {

    /**
     * Generates a fresh RSA-2048 key pair on every application start.
     * The pair is intentionally ephemeral — tokens issued before a restart
     * will fail validation after restart (acceptable; tokens expire in 15 min).
     */
    @Bean
    public KeyPair rsaKeyPair() {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean
    public RSAPublicKey rsaPublicKey(KeyPair rsaKeyPair) {
        return (RSAPublicKey) rsaKeyPair.getPublic();
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey(KeyPair rsaKeyPair) {
        return (RSAPrivateKey) rsaKeyPair.getPrivate();
    }
}
```

**Imports required**:
- `java.security.KeyPair`, `java.security.KeyPairGenerator`
- `java.security.interfaces.RSAPublicKey`, `java.security.interfaces.RSAPrivateKey`
- `org.springframework.context.annotation.Bean`, `org.springframework.context.annotation.Configuration`

**Error handling**: `KeyPairGenerator.getInstance("RSA")` throws `NoSuchAlgorithmException` — wrap in `IllegalStateException` with message `"RSA algorithm not available — cannot start application"`.

---

### 2.2 `JwksController`

**File**: `src/main/java/pl/devstyle/aj/core/oauth2/JwksController.java`  
**Package**: `pl.devstyle.aj.core.oauth2`

**Purpose**: Serves the public RSA key in JWK Set format so external parties can verify RS256-signed tokens.

```java
@RestController
public class JwksController {

    private final String jwksJson;   // computed once at construction

    public JwksController(RSAPublicKey rsaPublicKey) {
        // Build JWK from public key using Nimbus
        RSAKey rsaJwk = new RSAKey.Builder(rsaPublicKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("aj-rsa-key-1")   // stable kid; single key, no rotation
                .build();
        JWKSet jwkSet = new JWKSet(rsaJwk);
        this.jwksJson = jwkSet.toString();   // toString() = public representation (no private material)
    }

    @GetMapping(
        value = "/oauth2/jwks.json",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> getJwks() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(jwksJson);
    }
}
```

**Imports required** (Nimbus via `spring-security-oauth2-authorization-server`):
- `com.nimbusds.jose.jwk.RSAKey`
- `com.nimbusds.jose.jwk.JWKSet`
- `com.nimbusds.jose.jwk.KeyUse`
- `com.nimbusds.jose.JWSAlgorithm`

**Key ID**: Hard-coded `"aj-rsa-key-1"`. Must match `kid` header in tokens produced by `JwtTokenProvider`.

**Security rule**: This endpoint must be permit-all (no authentication required). See §7.

---

### 2.3 `ProtectedResourceMetadataController`

**File**: `src/main/java/pl/devstyle/aj/core/oauth2/ProtectedResourceMetadataController.java`  
**Package**: `pl.devstyle.aj.core.oauth2`

**Purpose**: Implements RFC 9728 §3 — enables MCP clients to discover the authorization server URL via the well-known endpoint.

```java
@RestController
public class ProtectedResourceMetadataController {

    @GetMapping(
        value = "/.well-known/oauth-protected-resource",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> getProtectedResourceMetadata(HttpServletRequest request) {
        String baseUrl = getBaseUrl(request);
        return Map.of(
                "resource",                     baseUrl,
                "authorization_servers",        new String[]{ baseUrl },
                "bearer_methods_supported",     new String[]{ "header" },
                "resource_documentation",       baseUrl + "/api/health"
        );
    }

    // getBaseUrl — identical to OAuth2MetadataController.getBaseUrl (copy verbatim)
    private String getBaseUrl(HttpServletRequest request) { ... }
}
```

**RFC 9728 §3 required fields**:
- `resource` — URI of the protected resource (= `baseUrl`)
- `authorization_servers` — array of AS issuer URLs; single entry = `baseUrl`

**Optional included fields**:
- `bearer_methods_supported` — `["header"]` (form body not supported)
- `resource_documentation` — informational URL

**Security rule**: Permit-all, no authentication. See §7.

**Note on `getBaseUrl`**: The implementation is identical to `OAuth2MetadataController.getBaseUrl`. Duplication is acceptable for now — extract to `BaseUrlHelper` utility in a future refactor if desired.

---

## 3. Modified Components

### 3.1 `JwtTokenProvider` — HS256 → RS256

**File**: `src/main/java/pl/devstyle/aj/core/security/JwtTokenProvider.java`

#### What changes

| Aspect | Before | After |
|--------|--------|-------|
| Signing key | `SecretKey` (HMAC-SHA256) | `RSAPrivateKey` (RS256) |
| Constructor params | `@Value("${app.jwt.secret}")` | `RSAPrivateKey rsaPrivateKey` injected |
| `generateToken` | signs with `secretKey` | signs with `rsaPrivateKey` using `RS256` |
| `generateOAuth2Token` | signs with `secretKey` | signs with `rsaPrivateKey`; adds `kid` header |
| `parseRawClaims` | verifies with `secretKey` | verifies with `rsaPublicKey` (also injected) |
| `parseToken` | verifies with `secretKey` | verifies with `rsaPublicKey` |
| `validateToken` | verifies with `secretKey` | verifies with `rsaPublicKey` |
| `getUsernameFromToken` | verifies with `secretKey` | verifies with `rsaPublicKey` |
| `getPermissionsFromToken` | verifies with `secretKey` | verifies with `rsaPublicKey` |

#### Updated constructor signature

```java
// REMOVE:
// @Value("${app.jwt.secret}") String secret
// @Value("${app.jwt.expiration-ms}") long expirationMs

// NEW constructor:
public JwtTokenProvider(RSAPrivateKey rsaPrivateKey,
                        RSAPublicKey rsaPublicKey,
                        @Value("${app.jwt.expiration-ms}") long expirationMs) {
    this.rsaPrivateKey = rsaPrivateKey;
    this.rsaPublicKey = rsaPublicKey;
    this.expirationMs = expirationMs;
}
```

#### Updated `generateOAuth2Token` — add `kid` header

```java
public String generateOAuth2Token(String username, Set<String> scopes,
                                  String issuer, String audience) {
    var now = new Date();
    var expiry = new Date(now.getTime() + OAUTH2_TOKEN_EXPIRATION_MS);
    var builder = Jwts.builder()
            .header().keyId("aj-rsa-key-1").and()   // kid must match JwksController
            .issuer(issuer)
            .subject(username)
            .claim("scopes", List.copyOf(scopes))
            .issuedAt(now)
            .expiration(expiry)
            .signWith(rsaPrivateKey, Jwts.SIG.RS256);
    if (audience != null) {
        builder.audience().add(audience);
    }
    return builder.compact();
}
```

#### Updated parser methods

All methods that currently call `Jwts.parser().verifyWith(secretKey)` must change to:

```java
Jwts.parser()
    .verifyWith(rsaPublicKey)   // RSAPublicKey implements PublicKey
    .build()
    .parseSignedClaims(token)
```

#### `app.jwt.secret` property

Remove `@Value("${app.jwt.secret}")` from constructor. The property itself can remain in `application.properties` for backward-compatibility (it will simply be unused). Preferred: remove it from `application.properties` and document the removal.

---

### 3.2 `JwtAuthenticationFilter` — inline HMAC → `NimbusJwtDecoder`

**File**: `src/main/java/pl/devstyle/aj/core/security/JwtAuthenticationFilter.java`

#### What changes

`JwtAuthenticationFilter` currently delegates to `JwtTokenProvider.parseToken(token)`. Because `JwtTokenProvider` will now use RSA, the filter gains RS256 support automatically. **However**, we must also add a parallel validation path using `NimbusJwtDecoder` to validate `iss` and `aud` claims — `jjwt` does not validate these by default.

Two validation paths:
1. **`permissions` claim present** → token is a user session token (no `iss`/`aud`); use `jwtTokenProvider.parseToken()` as today.
2. **`scopes` claim present** → token is an OAuth2 token; use `NimbusJwtDecoder` to validate `sig + exp + iss + aud`.

#### Updated class signature

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final NimbusJwtDecoder jwtDecoder;   // NEW — injected from SecurityConfiguration
    private final String expectedIssuer;          // NEW — from @Value("${app.jwt.expected-issuer:}")

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   NimbusJwtDecoder jwtDecoder,
                                   String expectedIssuer) { ... }
```

#### Updated `doFilterInternal` logic

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {
    var token = extractToken(request);

    if (token != null) {
        tryAuthenticateWithPermissionsToken(token);      // jjwt path (user session tokens)
        if (!isAuthenticated()) {
            tryAuthenticateWithOAuth2Token(token);       // NimbusJwtDecoder path (OAuth2 tokens)
        }
    }

    filterChain.doFilter(request, response);
}

private void tryAuthenticateWithPermissionsToken(String token) {
    jwtTokenProvider.parseToken(token).ifPresent(parsed -> {
        if (!parsed.permissions().isEmpty()) {
            var authorities = buildAuthorities(parsed.permissions());
            setAuthentication(parsed.username(), authorities);
        }
    });
}

private void tryAuthenticateWithOAuth2Token(String token) {
    try {
        var jwt = jwtDecoder.decode(token);
        var scopes = jwt.getClaimAsStringList("scopes");
        if (scopes == null) scopes = List.of();
        var authorities = buildAuthorities(Set.copyOf(scopes));
        var username = jwt.getSubject();
        setAuthentication(username, authorities);
    } catch (JwtException e) {
        // not a valid OAuth2 token — leave SecurityContext unauthenticated
    }
}

private List<SimpleGrantedAuthority> buildAuthorities(Collection<String> perms) {
    return perms.stream()
            .map(p -> new SimpleGrantedAuthority("PERMISSION_" + p))
            .toList();
}
```

**Note**: `NimbusJwtDecoder` is constructed in `SecurityConfiguration` with `RSAPublicKey` — see §3.5.

**`expectedIssuer`**: When provided, configure `NimbusJwtDecoder` with a `JwtValidators.createDefaultWithIssuer(expectedIssuer)` validator. When empty (local dev without reverse proxy), skip issuer validation. This is set via `app.jwt.expected-issuer` in `application.properties` (default: empty).

---

### 3.3 `AuthorizationCodeService` — add `resourceUri` field

**File**: `src/main/java/pl/devstyle/aj/core/oauth2/AuthorizationCodeService.java`

#### Change: add `resourceUri` to `AuthorizationCodeData` record

```java
// BEFORE:
public record AuthorizationCodeData(
        String clientId,
        String redirectUri,
        String scope,
        String codeChallenge,
        String codeChallengeMethod,
        String username,
        Set<String> permissions,
        Instant createdAt
) {}

// AFTER:
public record AuthorizationCodeData(
        String clientId,
        String redirectUri,
        String scope,
        String codeChallenge,
        String codeChallengeMethod,
        String username,
        Set<String> permissions,
        Instant createdAt,
        String resourceUri        // NEW — nullable; RFC 8707 resource param value
) {}
```

#### Change: update `storeAuthorizationCode` signature

```java
// BEFORE:
public void storeAuthorizationCode(String code, String clientId, String redirectUri, String scope,
                                   String codeChallenge, String codeChallengeMethod,
                                   String username, Set<String> permissions)

// AFTER:
public void storeAuthorizationCode(String code, String clientId, String redirectUri, String scope,
                                   String codeChallenge, String codeChallengeMethod,
                                   String username, Set<String> permissions,
                                   String resourceUri)   // NEW — pass null if not provided
```

The constructor call inside `storeAuthorizationCode` must append `resourceUri` as the last argument to `AuthorizationCodeData`.

---

### 3.4 `OAuth2AuthorizationFilter` — RFC 8707 `resource` param

**File**: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2AuthorizationFilter.java`

#### Change: read and store `resource` parameter

In `handleAuthorizationRequest`, extract the optional `resource` parameter and pass it through to `storeAuthorizationCode`:

```java
// After extracting other params:
String resourceUri = request.getParameter("resource");   // NEW — may be null

// RFC 8707 §2: if resource is provided, it MUST be an absolute URI
if (resourceUri != null && !isAbsoluteUri(resourceUri)) {
    throw new IllegalArgumentException("resource parameter must be an absolute URI");
}

// Update the storeAuthorizationCode call:
authorizationCodeService.storeAuthorizationCode(
        authorizationCode, clientId, redirectUri, scope,
        codeChallenge, codeChallengeMethod, username, permissions,
        resourceUri    // NEW
);
```

#### New private helper

```java
private boolean isAbsoluteUri(String uri) {
    try {
        var parsed = URI.create(uri);
        return parsed.isAbsolute();
    } catch (IllegalArgumentException e) {
        return false;
    }
}
```

#### Log update

Add `resource={}` to the existing `log.info("Authorization code issued …")` call.

---

### 3.5 `OAuth2TokenFilter` — hardened token exchange + Token-A audience

**File**: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2TokenFilter.java`

#### 3.5.1 New constructor parameter: allowlist

```java
// NEW field:
private final Set<String> allowedExchangeAudiences;

// Updated constructor:
public OAuth2TokenFilter(RegisteredClientRepository registeredClientRepository,
                         AuthorizationCodeService authorizationCodeService,
                         RefreshTokenService refreshTokenService,
                         JwtTokenProvider jwtTokenProvider,
                         PasswordEncoder passwordEncoder,
                         OAuth2ClientAuthenticator clientAuthenticator,
                         Set<String> allowedExchangeAudiences) {   // NEW
    ...
    this.allowedExchangeAudiences = Set.copyOf(allowedExchangeAudiences);
}
```

The `allowedExchangeAudiences` set is populated from `app.oauth2.allowed-exchange-audiences` in `SecurityConfiguration`. See §3.6 for injection details.

#### 3.5.2 `handleAuthorizationCodeGrant` — set Token-A `aud` from `resourceUri`

```java
// BEFORE:
String accessToken = jwtTokenProvider.generateOAuth2Token(data.username(), grantedScopes, issuer);

// AFTER:
String accessToken = jwtTokenProvider.generateOAuth2Token(
        data.username(), grantedScopes, issuer, data.resourceUri());   // resourceUri may be null → no aud claim
```

No other changes to the authorization code grant flow.

#### 3.5.3 `handleTokenExchangeGrant` — hardened audience logic

Full replacement of the audience section in `handleTokenExchangeGrant`:

```java
private void handleTokenExchangeGrant(HttpServletRequest request, HttpServletResponse response)
        throws IOException {

    // ... (existing client auth + subject_token validation unchanged) ...

    // NEW: Read explicit audience parameter
    String requestedAudience = request.getParameter("audience");

    // Determine Token-B audience:
    // - If "audience" param provided → use it (after allowlist check)
    // - Otherwise fall back to issuer (existing behaviour for non-audience-constrained callers)
    String tokenBAudience;
    if (requestedAudience != null && !requestedAudience.isBlank()) {
        // Allowlist check — prevent arbitrary token minting
        if (!allowedExchangeAudiences.isEmpty()
                && !allowedExchangeAudiences.contains(requestedAudience)) {
            log.warn("Token exchange rejected | reason=audience not in allowlist | audience={}",
                    requestedAudience);
            sendError(response, OAuth2Error.INVALID_TARGET,
                    "The requested audience is not permitted: " + requestedAudience);
            return;
        }
        tokenBAudience = requestedAudience;
    } else {
        // No audience param; fall back to issuer
        tokenBAudience = issuer;
    }

    // Generate Token-B with correct audience
    String tokenB = jwtTokenProvider.generateOAuth2Token(
            parsedToken.get().username(), mappedPermissions, issuer, tokenBAudience);

    sendTokenExchangeResponse(response, tokenB);

    log.info("Token exchange completed | sub={} | client_id={} | audience={} | mapped_permissions={}",
            parsedToken.get().username(), credentials.get().clientId(),
            tokenBAudience, mappedPermissions);
}
```

**Key behaviour**:
- `audience` param provided + in allowlist → Token-B `aud = audience` ✓
- `audience` param provided + NOT in allowlist → `400 invalid_target` ✓
- No `audience` param → Token-B `aud = issuer` (backward-compatible fallback) ✓
- Empty allowlist (`app.oauth2.allowed-exchange-audiences` not set) → all audiences permitted (open mode, log warning at startup)

---

### 3.6 `SecurityConfiguration` — wiring + new permit-all rules + WWW-Authenticate

**File**: `src/main/java/pl/devstyle/aj/core/security/SecurityConfiguration.java`

#### New constructor parameters

```java
// ADD to constructor (injected by Spring):
private final RSAPublicKey rsaPublicKey;

@Value("${app.oauth2.allowed-exchange-audiences:}")
private List<String> allowedExchangeAudiences;

@Value("${app.jwt.expected-issuer:}")
private String expectedIssuer;
```

#### Construct `NimbusJwtDecoder` bean

```java
@Bean
public NimbusJwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey) {
    var decoder = NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
    if (expectedIssuer != null && !expectedIssuer.isBlank()) {
        decoder.setJwtValidator(
            JwtValidators.createDefaultWithIssuer(expectedIssuer));
    }
    return decoder;
}
```

#### Update `JwtAuthenticationFilter` construction

```java
// BEFORE:
.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
        UsernamePasswordAuthenticationFilter.class)

// AFTER:
.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, jwtDecoder(), expectedIssuer),
        UsernamePasswordAuthenticationFilter.class)
```

#### Update `OAuth2TokenFilter` construction

```java
// AFTER:
.addFilterAfter(new OAuth2TokenFilter(
        registeredClientRepository, authorizationCodeService,
        refreshTokenService, jwtTokenProvider, passwordEncoder(),
        new OAuth2ClientAuthenticator(registeredClientRepository, passwordEncoder()),
        new HashSet<>(allowedExchangeAudiences)),   // NEW allowlist
        OAuth2AuthorizationFilter.class)
```

#### Add new permit-all rules

```java
// Add inside .authorizeHttpRequests(auth -> auth …):
.requestMatchers(HttpMethod.GET, "/oauth2/jwks.json").permitAll()
.requestMatchers(HttpMethod.GET, "/.well-known/oauth-protected-resource").permitAll()
```

Place these immediately after the existing `.well-known/oauth-authorization-server` permit-all rule.

#### Add `WWW-Authenticate` header on 401

Update the `authenticationEntryPoint` lambda to add the `WWW-Authenticate` header. The header value requires the base URL, which must be derived from the request:

```java
.authenticationEntryPoint((request, response, authException) -> {
    String baseUrl = deriveBaseUrl(request);   // same logic as getBaseUrl() in controllers
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader("WWW-Authenticate",
            "Bearer resource_metadata=\"" + baseUrl
            + "/.well-known/oauth-protected-resource\"");
    var errorResponse = new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "Authentication required",
            null,
            LocalDateTime.now()
    );
    objectMapper.writeValue(response.getOutputStream(), errorResponse);
})
```

Add `private String deriveBaseUrl(HttpServletRequest request)` as a private method in `SecurityConfiguration` — identical implementation to `getBaseUrl()` in the controllers.

---

### 3.7 `OAuth2MetadataController` — add `jwks_uri`

**File**: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2MetadataController.java`

Add `jwks_uri` entry to the authorization server metadata response:

```java
// ADD to the Map.ofEntries(...) in getAuthorizationServerMetadata:
entry("jwks_uri", baseUrl + "/oauth2/jwks.json"),
```

The metadata document already contains 9 entries; `Map.ofEntries` supports up to 10 (uses varargs) — no structural change needed. After adding `jwks_uri` there will be 10 entries total.

---

### 3.8 `OAuth2IntrospectionFilter` — RS256 token support

**File**: `src/main/java/pl/devstyle\aj\core\oauth2\OAuth2IntrospectionFilter.java`

`OAuth2IntrospectionFilter` calls `jwtTokenProvider.parseRawClaims(token)`. Because `JwtTokenProvider.parseRawClaims` will now use `rsaPublicKey` (after §3.1 changes), this filter gains RS256 support automatically with **no changes** needed.

**Verify**: After §3.1 changes are applied, confirm that `parseRawClaims` correctly parses RS256 tokens (the `Claims` structure is identical between HS256 and RS256 in jjwt).

---

### 3.9 `OAuth2Error` enum — add `INVALID_TARGET`

**File**: `src/main/java/pl/devstyle/aj/core/oauth2/OAuth2Error.java`

Add a new error code for RFC 8693 §2.2.2 audience rejection:

```java
// ADD after UNSUPPORTED_GRANT_TYPE:
INVALID_TARGET("invalid_target",
    "The server is unwilling or unable to issue a token for the indicated resource");
```

Update `sendError` status mapping in `OAuth2TokenFilter` to handle `INVALID_TARGET`:

```java
// In OAuth2TokenFilter.sendError switch:
case INVALID_TARGET -> HttpServletResponse.SC_BAD_REQUEST;
```

---

## 4. API Contracts

### 4.1 `GET /oauth2/jwks.json`

**Authentication**: None (permit-all)

**Response `200 OK`**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "aj-rsa-key-1",
      "n":   "<base64url-encoded modulus>",
      "e":   "AQAB"
    }
  ]
}
```

**Headers**: `Cache-Control: public, max-age=3600`

**Error cases**: None — the key is generated at startup; if the application is running, this endpoint always succeeds.

---

### 4.2 `GET /.well-known/oauth-protected-resource`

**Authentication**: None (permit-all)

**Response `200 OK`**:
```json
{
  "resource":                 "https://example.com",
  "authorization_servers":    ["https://example.com"],
  "bearer_methods_supported": ["header"],
  "resource_documentation":   "https://example.com/api/health"
}
```

---

### 4.3 `GET /.well-known/oauth-authorization-server` (modified)

**New field added**:
```json
{
  "issuer": "https://example.com",
  "authorization_endpoint": "https://example.com/oauth2/authorize",
  "token_endpoint": "https://example.com/oauth2/token",
  "jwks_uri": "https://example.com/oauth2/jwks.json",
  "registration_endpoint": "https://example.com/oauth2/register",
  "introspection_endpoint": "https://example.com/oauth2/introspect",
  "grant_types_supported": ["authorization_code","refresh_token","urn:ietf:params:oauth:grant-type:token-exchange"],
  "response_types_supported": ["code"],
  "code_challenge_methods_supported": ["S256"],
  "token_endpoint_auth_methods_supported": ["client_secret_post","client_secret_basic","none"],
  "scopes_supported": ["mcp:read","mcp:edit"]
}
```

---

### 4.4 `POST /oauth2/authorize` (modified — `resource` param)

**New optional parameter**: `resource` (string, absolute URI)

| Parameter | Required | Description |
|-----------|----------|-------------|
| `client_id` | Yes | OAuth2 client ID |
| `redirect_uri` | Yes | Pre-registered redirect URI |
| `response_type` | Yes | Must be `code` |
| `scope` | No | Space-separated scopes |
| `state` | No | CSRF nonce |
| `code_challenge` | Conditional | Required for public clients |
| `code_challenge_method` | Conditional | Must be `S256` |
| `resource` | No | **NEW** — RFC 8707 resource indicator; absolute URI of the protected resource |

**Error response when `resource` is not an absolute URI**:
```json
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_request",
  "error_description": "resource parameter must be an absolute URI"
}
```

---

### 4.5 `POST /oauth2/token` — token exchange (modified)

**New optional parameter**: `audience`

**Full request for token exchange**:
```
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <client_id:client_secret base64>

grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token=<Token-A JWT>
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&scope=mcp:read
&audience=https://mcp-server.example.com   ← NEW
```

**Successful response `200 OK`** (unchanged structure):
```json
{
  "access_token": "<Token-B JWT with aud=https://mcp-server.example.com>",
  "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "token_type": "Bearer",
  "expires_in": 900
}
```

**Error — audience not in allowlist `400 Bad Request`**:
```json
{
  "error": "invalid_target",
  "error_description": "The requested audience is not permitted: https://unknown.example.com"
}
```

**Error — subject token invalid/expired `400 Bad Request`**:
```json
{
  "error": "invalid_grant",
  "error_description": "Subject token is invalid or expired"
}
```

---

### 4.6 Any protected API endpoint — 401 with `WWW-Authenticate`

```
HTTP/1.1 401 Unauthorized
Content-Type: application/json
WWW-Authenticate: Bearer resource_metadata="https://example.com/.well-known/oauth-protected-resource"

{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required",
  "path": null,
  "timestamp": "2026-04-27T12:00:00"
}
```

---

## 5. Data Model Changes

### `AuthorizationCodeData` record

**Location**: `AuthorizationCodeService.java` (nested record)

```java
// BEFORE (8 fields):
public record AuthorizationCodeData(
        String clientId,
        String redirectUri,
        String scope,
        String codeChallenge,
        String codeChallengeMethod,
        String username,
        Set<String> permissions,
        Instant createdAt
) {}

// AFTER (9 fields):
public record AuthorizationCodeData(
        String clientId,
        String redirectUri,
        String scope,
        String codeChallenge,
        String codeChallengeMethod,
        String username,
        Set<String> permissions,
        Instant createdAt,
        String resourceUri    // nullable; null when client did not send resource param
) {}
```

**No migration needed** — data is stored in-memory only; a server restart clears all codes.

**All callers** of `storeAuthorizationCode` must be updated to pass `resourceUri`. Current callers:
- `OAuth2AuthorizationFilter.handleAuthorizationRequest` (§3.4)

All consumers of `AuthorizationCodeData` that read the record:
- `OAuth2TokenFilter.handleAuthorizationCodeGrant` — reads `data.resourceUri()` to set Token-A `aud`; handles null gracefully (no `aud` claim).

---

## 6. Configuration

### `application.properties` changes

```properties
# ─── REMOVE (no longer used after RS256 migration) ───────────────────────────
# app.jwt.secret=<base64-encoded HMAC secret>

# ─── KEEP (unchanged) ─────────────────────────────────────────────────────────
app.jwt.expiration-ms=86400000

# ─── NEW ──────────────────────────────────────────────────────────────────────

# RFC 8693 Token Exchange audience allowlist.
# Comma-separated list of permitted audience URIs for token exchange.
# When empty, all audiences are permitted (open mode — only for local dev).
# Example: app.oauth2.allowed-exchange-audiences=https://mcp.example.com,https://other.example.com
app.oauth2.allowed-exchange-audiences=

# Expected JWT issuer for NimbusJwtDecoder validation.
# Set to the public base URL of this server (e.g. https://app.example.com).
# When empty, issuer validation is skipped (local dev only).
app.jwt.expected-issuer=
```

### Startup warning

In `SecurityConfiguration` (or a dedicated `@EventListener ApplicationReadyEvent`), log a warning if `allowedExchangeAudiences` is empty:

```
WARN: app.oauth2.allowed-exchange-audiences is not configured — token exchange audience
      validation is disabled. This is unsafe in production. Set the property to a
      comma-separated list of permitted audience URIs.
```

---

## 7. Security Rules

### 7.1 New permit-all rules in `SecurityConfiguration`

Add the following two matchers immediately after the existing `/.well-known/oauth-authorization-server` rule:

```java
.requestMatchers(HttpMethod.GET, "/oauth2/jwks.json").permitAll()
.requestMatchers(HttpMethod.GET, "/.well-known/oauth-protected-resource").permitAll()
```

### 7.2 `WWW-Authenticate` header format

The header value follows RFC 9728 §7:

```
WWW-Authenticate: Bearer resource_metadata="<url>"
```

Where `<url>` is the full URL of the PRM endpoint, e.g.:
```
Bearer resource_metadata="https://example.com/.well-known/oauth-protected-resource"
```

This header MUST be added on every 401 response from the `authenticationEntryPoint`. It does NOT apply to 403 (access denied) responses.

### 7.3 Token claims summary after migration

| Token Type | `iss` | `aud` | `sub` | Claim | Algorithm |
|------------|-------|-------|-------|-------|-----------|
| User session (login) | — | — | username | `permissions: [...]` | RS256 |
| OAuth2 Token-A (auth code) | baseUrl | resourceUri or absent | username | `scopes: [...]` | RS256 |
| OAuth2 Token-B (exchange) | baseUrl | audience param or issuer | username | `scopes: [...] (mapped)` | RS256 |
| Refresh token | n/a | n/a | n/a | opaque string | — |

---

## 8. Error Cases

### 8.1 `POST /oauth2/authorize` errors

| Condition | Error | HTTP Status |
|-----------|-------|-------------|
| `resource` param is not absolute URI | `invalid_request` | 400 |

All other error cases are unchanged.

### 8.2 `POST /oauth2/token` — token exchange errors

| Condition | Error | HTTP Status |
|-----------|-------|-------------|
| `audience` param provided, not in allowlist | `invalid_target` | 400 |
| `subject_token` invalid or expired | `invalid_grant` | 400 |
| `subject_token_type` is not access_token URN | `invalid_request` | 400 |
| Client authentication failed | `invalid_client` | 401 |
| Missing `subject_token` | `invalid_request` | 400 |

### 8.3 Error response format (`OAuth2ErrorResponse`)

The existing `OAuth2ErrorResponse.of(error, description)` format is unchanged:
```json
{
  "error": "invalid_target",
  "error_description": "The requested audience is not permitted: https://unknown.example.com"
}
```

### 8.4 JWKS endpoint errors

None expected. The key is generated at startup; any `KeyPairGenerator` failure causes application startup failure (appropriate — the application cannot issue tokens without a key).

### 8.5 JwtAuthenticationFilter — token validation failure

On RS256 signature verification failure, `NimbusJwtDecoder.decode()` throws `JwtException`. The filter catches this and leaves `SecurityContextHolder` unauthenticated. The request proceeds through the filter chain and hits `authenticationEntryPoint` (if the endpoint requires authentication), returning `401` with `WWW-Authenticate`.

---

## 9. Testing Requirements

### 9.1 New test: `RsaKeyConfigurationTest`

**File**: `src/test/java/pl/devstyle/aj/core/security/RsaKeyConfigurationTest.java`  
**Class type**: Unit test (no Spring context)

```java
class RsaKeyConfigurationTest {

    @Test
    void rsaKeyPair_generatesValidRsa2048KeyPair() {
        var config = new RsaKeyConfiguration();
        var keyPair = config.rsaKeyPair();
        assertThat(keyPair.getPublic()).isInstanceOf(RSAPublicKey.class);
        assertThat(keyPair.getPrivate()).isInstanceOf(RSAPrivateKey.class);
        assertThat(((RSAPublicKey) keyPair.getPublic()).getModulus().bitLength()).isEqualTo(2048);
    }

    @Test
    void rsaKeyPair_isDifferentOnEachCall() {
        var config = new RsaKeyConfiguration();
        var pair1 = config.rsaKeyPair();
        var pair2 = config.rsaKeyPair();
        assertThat(pair1.getPublic()).isNotEqualTo(pair2.getPublic());
    }
}
```

---

### 9.2 New test: `JwksControllerIntegrationTests`

**File**: `src/test/java/pl/devstyle/aj/core/oauth2/JwksControllerIntegrationTests.java`  
**Annotations**: `@SpringBootTest(webEnvironment = MOCK)`, `@AutoConfigureMockMvc`, `@Transactional`, `@Import(TestcontainersConfiguration.class)`

```java
class JwksControllerIntegrationTests {

    @Test
    void getJwks_returnsRsaPublicKey_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/oauth2/jwks.json"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.keys").isArray())
               .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
               .andExpect(jsonPath("$.keys[0].use").value("sig"))
               .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
               .andExpect(jsonPath("$.keys[0].kid").value("aj-rsa-key-1"))
               .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
               .andExpect(jsonPath("$.keys[0].e").value("AQAB"))
               .andExpect(header().string("Cache-Control", containsString("max-age=3600")));
    }

    @Test
    void getJwks_doesNotExposePivateKeyMaterial() throws Exception {
        var result = mockMvc.perform(get("/oauth2/jwks.json"))
                .andExpect(status().isOk())
                .andReturn();
        var body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"d\"");    // private exponent
        assertThat(body).doesNotContain("\"p\"");    // prime p
        assertThat(body).doesNotContain("\"q\"");    // prime q
    }
}
```

---

### 9.3 New test: `ProtectedResourceMetadataControllerIntegrationTests`

**File**: `src/test/java/pl/devstyle/aj/core/oauth2/ProtectedResourceMetadataControllerIntegrationTests.java`  
**Annotations**: same as above

```java
class ProtectedResourceMetadataControllerIntegrationTests {

    @Test
    void getPrm_returnsMetadata_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.resource").isNotEmpty())
               .andExpect(jsonPath("$.authorization_servers").isArray())
               .andExpect(jsonPath("$.authorization_servers[0]").isNotEmpty())
               .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"));
    }

    @Test
    void getPrm_resourceAndAuthServerMatch() throws Exception {
        var result = mockMvc.perform(get("/.well-known/oauth-protected-resource"))
               .andExpect(status().isOk()).andReturn();
        var body = new ObjectMapper().readValue(result.getResponse().getContentAsString(), Map.class);
        var resource = (String) body.get("resource");
        var authServers = (List<String>) body.get("authorization_servers");
        assertThat(authServers).containsExactly(resource);
    }
}
```

---

### 9.4 Updated test: `OAuth2MetadataControllerIntegrationTests` (if exists)

Add assertion for new `jwks_uri` field:

```java
@Test
void getAuthorizationServerMetadata_includesJwksUri() throws Exception {
    mockMvc.perform(get("/.well-known/oauth-authorization-server"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.jwks_uri").value(containsString("/oauth2/jwks.json")));
}
```

---

### 9.5 Updated test: `SecurityConfigurationIntegrationTests` (new or existing)

```java
class SecurityConfigurationIntegrationTests {

    @Test
    void unauthenticatedRequest_returns401_withWwwAuthenticateHeader() throws Exception {
        mockMvc.perform(get("/api/categories"))
               .andExpect(status().isUnauthorized())
               .andExpect(header().string("WWW-Authenticate",
                       containsString("Bearer resource_metadata=")))
               .andExpect(header().string("WWW-Authenticate",
                       containsString("/.well-known/oauth-protected-resource")));
    }
}
```

---

### 9.6 New test: `OAuth2TokenExchangeIntegrationTests` (extended)

Extend or create tests for the hardened token exchange:

```java
class OAuth2TokenExchangeIntegrationTests {

    // Test: audience param in allowlist → Token-B aud = audience
    @Test
    void tokenExchange_withAudienceInAllowlist_setsAudOnTokenB() { ... }

    // Test: audience param not in allowlist → 400 invalid_target
    @Test
    void tokenExchange_withAudienceNotInAllowlist_returns400InvalidTarget() throws Exception {
        // Perform token exchange with audience=https://unknown.example.com
        mockMvc.perform(post("/oauth2/token")
                       .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                       .param("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                       .param("subject_token", validToken)
                       .param("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                       .param("audience", "https://not-allowed.example.com")
                       .header("Authorization", "Basic " + encodedClientCredentials))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("invalid_target"));
    }

    // Test: no audience param → Token-B aud = issuer (backward-compatible)
    @Test
    void tokenExchange_withoutAudienceParam_usesIssuerAsAudience() { ... }
}
```

---

### 9.7 Updated test: `OAuth2AuthorizationFilterIntegrationTests`

```java
// Test: resource param is stored and used for Token-A aud
@Test
void authorize_withResourceParam_setsAudOnTokenA() { ... }

// Test: resource param that is not absolute URI → 400
@Test
void authorize_withNonAbsoluteResourceParam_returns400() throws Exception {
    mockMvc.perform(post("/oauth2/authorize")
                   .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                   .param("client_id", validClientId)
                   .param("redirect_uri", validRedirectUri)
                   .param("response_type", "code")
                   .param("resource", "not-a-uri")   // invalid
                   .param("_token", validUserToken))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("invalid_request"));
}
```

---

### 9.8 Updated test: `JwtTokenProviderTest`

Update all existing tests to work with RS256:
- Remove `app.jwt.secret` injection.
- Use `RsaKeyConfiguration` to produce a test key pair.
- Verify that tokens produced by `generateOAuth2Token` contain `"alg": "RS256"` and `"kid": "aj-rsa-key-1"` in their header (decode base64url the header).
- Verify that `parseToken` correctly validates RS256 tokens produced by `generateToken`.

---

## 10. Implementation Order

The following order avoids circular dependency issues and lets each step be independently compilable and testable.

```
Step 1  RsaKeyConfiguration
        — Adds RSAPublicKey + RSAPrivateKey beans.
        — No dependencies on other changed files.

Step 2  JwtTokenProvider (RS256 migration)
        — Depends on Step 1 (injects RSAPrivateKey + RSAPublicKey).
        — Remove SecretKey, update all sign/verify calls.

Step 3  JwksController (new file)
        — Depends on Step 1 (injects RSAPublicKey).

Step 4  OAuth2Error (add INVALID_TARGET)
        — Isolated enum change, no dependencies.

Step 5  AuthorizationCodeService (add resourceUri field)
        — Pure record change; causes compile errors in callers → fix immediately.

Step 6  OAuth2AuthorizationFilter (read resource param)
        — Depends on Step 5 (new storeAuthorizationCode signature).

Step 7  OAuth2TokenFilter (hardened exchange + Token-A aud)
        — Depends on Steps 4, 5 (INVALID_TARGET + resourceUri).
        — Depends on Step 2 (RS256 token generation).

Step 8  ProtectedResourceMetadataController (new file)
        — No dependencies on changed files.

Step 9  OAuth2MetadataController (add jwks_uri)
        — Minor one-line addition; depends on JwksController URL path (Step 3).

Step 10 JwtAuthenticationFilter (NimbusJwtDecoder)
        — Depends on Step 2 (RS256 parseToken).
        — NimbusJwtDecoder constructed in SecurityConfiguration (Step 11).

Step 11 SecurityConfiguration (wiring + permit-all + WWW-Authenticate)
        — Depends on Steps 1–10 (new beans + updated filter constructors).
        — Injects RSAPublicKey, constructs NimbusJwtDecoder, passes allowlist to OAuth2TokenFilter.

Step 12 application.properties cleanup
        — Remove app.jwt.secret; add app.oauth2.allowed-exchange-audiences and app.jwt.expected-issuer.

Step 13 Tests (add/update as per §9)
        — Run full test suite: ./mvnw test
```

**Compile-check milestone after Step 7**: All filter and service classes should compile. Steps 8–11 are additive and do not break earlier work.

**Integration test milestone after Step 11**: All new endpoints reachable, 401 carries `WWW-Authenticate`, JWKS returns RSA public key, token exchange rejects unlisted audiences.

---

## Appendix A: Key Dependencies (classpath)

| Library | Used for | Already present |
|---------|----------|-----------------|
| `com.nimbusds.jose.jwk.RSAKey` | Build JWK from RSAPublicKey in `JwksController` | Yes (via spring-security-oauth2-authorization-server) |
| `com.nimbusds.jose.jwk.JWKSet` | Serialize JWK Set to JSON | Yes |
| `com.nimbusds.jose.JWSAlgorithm` | Specify RS256 algorithm on JWK | Yes |
| `org.springframework.security.oauth2.jwt.NimbusJwtDecoder` | RS256 JWT validation in filter | Yes (spring-security-oauth2-resource-server) |
| `org.springframework.security.oauth2.jwt.JwtValidators` | Configure issuer validator | Yes |
| `io.jsonwebtoken.Jwts` (jjwt 0.12.6) | Token generation + `parseToken` path | Yes |
| `java.security.KeyPairGenerator` | RSA-2048 key generation | JDK built-in |

No new Maven dependencies are required.

---

## Appendix B: Removed Configuration

| Key | Removed in Step | Reason |
|-----|-----------------|--------|
| `app.jwt.secret` | Step 12 | HMAC secret no longer used; RSA key is ephemeral |

---

## Appendix C: Token Header Before / After

**Before (HS256)**:
```json
{ "alg": "HS256" }
```

**After (RS256)**:
```json
{ "alg": "RS256", "kid": "aj-rsa-key-1" }
```

The `kid` value `"aj-rsa-key-1"` is a stable constant used across `JwtTokenProvider` and `JwksController`. If key rotation is added in future, increment this value and expose both keys in JWKS during a transition window.
