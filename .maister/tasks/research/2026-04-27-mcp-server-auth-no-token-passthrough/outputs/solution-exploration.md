# Solution Exploration: MCP Server Authentication (No Token Passthrough)

## Decision Area 1: Where to Host the Authorization Server

### Alternative 1: Embedded AS (Current Approach — MCP Server is both RS and AS)

The MCP server continues to implement the full OAuth 2.1 Authorization Server stack internally: Authorization Code + PKCE, Dynamic Client Registration, token issuance, and the RFC 8414 metadata endpoint. The existing `handleTokenExchangeGrant` and scope-mapping logic remain in place.

**Pros:**
- Zero external dependencies — single deployable artifact, no Keycloak/Auth0 licensing or ops overhead
- Full control over token claims, `aud` binding (RFC 8707), and DCR policies
- Existing implementation already covers most gaps; remaining work is additive (PRM endpoint, `aud` enforcement)
- Shortest path to spec compliance for a self-contained project

**Cons:**
- Conflation of concerns: AS and RS security boundaries are blurred in the same process; a bug in one can affect the other
- Does not compose well if multiple MCP servers or other resource servers later need to share the same AS
- Maintaining a spec-compliant AS (key rotation, token revocation, PKCE, refresh token rotation) is ongoing operational burden
- Hard to audit: the server both issues and validates its own tokens

**Recommendation signal:** Best for early-stage / single-server deployments where simplicity and fast iteration outweigh long-term extensibility.

---

### Alternative 2: External AS (MCP Server is RS-only)

The MCP server delegates all token issuance to an external Authorization Server (Keycloak, Auth0, Azure Entra ID, or a lightweight self-hosted option such as Spring Authorization Server as a separate process). The MCP server validates JWTs using the AS's published JWKS and enforces `aud`, scope, and expiry locally.

**Pros:**
- Clean separation of concerns: the MCP server is a pure Resource Server — simpler, smaller, easier to audit
- Enterprise-grade AS features (token revocation, session management, MFA, federation) come for free
- Scales naturally when multiple MCP servers or other APIs share the same identity platform
- Keycloak/Entra support RFC 8707 `resource` parameter natively, removing the need to build audience-binding from scratch

**Cons:**
- Introduces an external runtime dependency; AS availability directly affects MCP server availability
- Dynamic Client Registration (DCR) semantics vary across AS vendors — may require custom extensions or workarounds
- Operational complexity: the AS must be deployed, monitored, and kept in sync with MCP scopes/clients
- Token Exchange (RFC 8693) support is inconsistent across AS vendors (Keycloak supports it; Auth0 does not natively)

**Recommendation signal:** Best when the project will grow beyond a single MCP server, or when the organisation already operates an identity platform.

---

### Alternative 3: Hybrid — Keep DCR + Metadata Here, Delegate Token Signing to External JWKS

The MCP server retains its DCR endpoint and RFC 8414 metadata document (pointing clients to the right endpoints) but delegates cryptographic trust to an externally managed key set. Token issuance still happens locally, but the signing keys are fetched from an external JWKS URI (e.g., a managed secrets service or a minimal Spring Authorization Server sidecar). The MCP server validates inbound tokens against that same JWKS.

**Pros:**
- Key rotation is decoupled from the MCP server — rotated centrally without redeployment
- Keeps DCR logic local (avoiding vendor DCR quirks) while externalising the hardest part (key management)
- Gradual migration path: can evolve toward Alternative 2 without a full rewrite

**Cons:**
- Highest accidental complexity: two systems must stay in sync (key versions, algorithm negotiation)
- Still maintains AS-like code locally (token issuance, grant handling) — does not reduce maintenance burden as much as Alternative 2
- JWKS endpoint availability becomes a new failure mode; caching strategy required

**Recommendation signal:** Useful only as a transitional architecture. Not recommended as a target state.

---

**Area 1 Recommendation:** **Alternative 1 (Embedded AS)** for this project's current stage. The gaps are well-defined and additive (PRM endpoint, `aud` enforcement, WWW-Authenticate header). Migrating to Alternative 2 should be a future milestone once the project matures beyond a single server.

---

## Decision Area 2: Token Validation Strategy

### Alternative 1: Local JWT Validation with HMAC (Current Approach)

Inbound tokens are validated locally using a shared HMAC secret (`HS256`/`HS512`). The MCP server holds the secret and verifies signature, expiry, issuer, and — after the gap fix — `aud` claim.

**Pros:**
- Already implemented; fastest path to compliance by adding `aud` validation on top
- No network calls at validation time — low latency, no external dependency
- Simple key material: a single secret in application config or a secrets manager

**Cons:**
- HMAC is symmetric: the same key that signs tokens can also forge them. If the key leaks, tokens can be minted by an attacker
- Cannot be validated by any external party (e.g., an API gateway, another microservice) without sharing the secret — poor composability
- Key rotation requires restarting the server or hot-reloading config; no standard JWKS distribution mechanism
- Incompatible with RFC 8414/RFC 9728 expectations that the AS publishes a JWKS URI for independent validation

**Recommendation signal:** Acceptable short-term, but plan for migration.

---

### Alternative 2: Local JWT Validation with RSA / EC Asymmetric Keys

Replace HMAC with an RSA-2048 (or EC P-256) key pair. The AS signs tokens with the private key; the MCP server (and any other party) validates with the public key published at the JWKS URI. `aud`, `iss`, `exp`, and scope claims are validated locally after signature verification.

**Pros:**
- Industry standard: aligns with how every major AS works; public key can be shared freely
- Enables independent validation by API gateways, other services, and debugging tools (jwt.io) without sharing secrets
- Key rotation via JWKS URI: publish new key with a new `kid`, old tokens remain valid until expiry
- Required for credible RFC 8414 + RFC 9728 compliance (the JWKS URI in the metadata document becomes meaningful)
- Spring Security's `NimbusJwtDecoder.withJwkSetUri(...)` or `withPublicKey(...)` supports this natively

**Cons:**
- Requires generating and securely storing an RSA/EC key pair (vs. a simple string secret)
- Slightly more CPU at signing time (negligible for 15-min tokens at typical MCP request rates)
- Refactoring effort: replace `HmacTokenService` with `JwtEncoder` (Spring Security) backed by `RSAKey` or `ECKey` from Nimbus JOSE

**Recommendation signal:** Strong recommendation — this is the correct production approach and unblocks JWKS URI in metadata.

---

### Alternative 3: Token Introspection (RFC 7662) — Live AS Validation

Instead of local JWT validation, the MCP server calls the AS's introspection endpoint (`POST /oauth2/introspect`) on every request, receiving a live active/inactive verdict plus claims. Spring Security's `OpaqueTokenIntrospector` supports this directly.

**Pros:**
- Tokens can be revoked immediately (no waiting for expiry) — strongest security guarantee
- No local key material needed; the RS does not need to know the signing algorithm
- Useful if tokens are opaque (non-JWT) rather than self-contained JWTs

**Cons:**
- Every MCP tool invocation requires a synchronous HTTP call to the introspection endpoint — significant latency hit and availability dependency
- Introspection endpoint becomes a bottleneck and single point of failure; requires caching with short TTLs, which partially defeats revocation benefits
- With 15-min short-lived tokens already in use, the revocation benefit is marginal
- The project already uses self-contained JWTs — introspection adds complexity without meaningful gain

**Recommendation signal:** Not recommended for this project. The short token lifetime already limits exposure; local RSA validation (Alternative 2) is preferable.

---

**Area 2 Recommendation:** **Alternative 2 (RSA/EC asymmetric local validation)**. Migrate from HMAC to an RSA-2048 or EC P-256 key pair. This fixes the JWKS URI gap in RFC 8414/RFC 9728 metadata, enables external validation, and is the standard approach for self-contained JWTs. The refactoring cost is modest given Spring Security's built-in `JwtEncoder`/`JwtDecoder` support.

---

## Decision Area 3: How the MCP Server Obtains Tokens for Upstream API Calls

### Alternative 1: Client Credentials Grant (Server-to-Server, No User Context)

The MCP server authenticates to upstream APIs using its own client identity (`client_credentials` grant). It presents its own `client_id` and `client_secret` (or a client assertion JWT) to the AS and receives a token scoped to the upstream API. The end-user identity is not propagated downstream.

**Pros:**
- Simplest to implement and reason about: one client identity, one token, cached and reused until near-expiry
- No dependency on the inbound user token structure — robust to changes in how the MCP client authenticates
- Well-supported by all AS vendors and by Spring Security's `OAuth2AuthorizedClientManager`
- Appropriate when upstream APIs do not need to know which end-user triggered the call

**Cons:**
- User identity is lost downstream: audit logs on upstream APIs show the MCP server's identity, not the end-user's
- Cannot enforce per-user authorisation on upstream APIs (e.g., "user A may not access resource X")
- Violates the principle of least privilege if the MCP server's credentials grant broader access than the inbound user's scope

**Recommendation signal:** Use when upstream APIs are internal services that trust the MCP server as a proxy and do not require user-level authorisation.

---

### Alternative 2: Token Exchange RFC 8693 (User Identity Preserved — Current Approach)

The MCP server exchanges the inbound user token (Token-A, audience-bound to the MCP server) for a new downstream token (Token-B, audience-bound to the upstream API) using the `urn:ietf:params:oauth:grant-type:token-exchange` grant. The resulting Token-B carries the original user's `sub` and delegation metadata (`act` claim).

**Pros:**
- User identity and delegation chain are preserved end-to-end — upstream APIs can enforce per-user policies and produce accurate audit logs
- Satisfies the MCP spec's no-passthrough requirement: Token-A is consumed by the AS and a fresh Token-B is issued for the upstream resource
- Already partially implemented (`handleTokenExchangeGrant`) — the gap is ensuring `actor` and `aud` claims are correctly set on Token-B
- RFC 8693 `may_act` / `act` claims provide a verifiable delegation chain

**Cons:**
- Requires the AS to support RFC 8693 — the existing `handleTokenExchangeGrant` covers this, but it must be hardened (validate `actor`, bind `aud` to upstream resource URI)
- Each upstream call that requires a different audience requires a separate exchange; caching by `(user, resource)` key is needed to avoid redundant round-trips
- Token exchange adds one AS round-trip per unique (user, upstream resource) combination on cold cache

**Recommendation signal:** Strongly preferred when user identity must flow to upstream services. This is the correct MCP-spec-aligned approach and is already partially built.

---

### Alternative 3: Pre-Fetched Service Tokens with Aggressive Caching

A background process (scheduled task or lazy initialiser) pre-fetches upstream service tokens (via Client Credentials) at startup or on first use and caches them in memory with a TTL slightly shorter than the token's expiry. Requests use the cached token directly without any per-request grant call.

**Pros:**
- Zero per-request latency for token acquisition — the cached token is used immediately
- Simple to implement: a `@Scheduled` task plus a `ConcurrentHashMap` cache
- Appropriate for high-throughput scenarios where the latency of Token Exchange is unacceptable

**Cons:**
- User identity is completely absent (same fundamental limitation as Alternative 1, but worse — the token is shared across all users and requests)
- Cache invalidation on revocation requires active monitoring of token expiry; a stale token causes a burst of 401s until the cache is refreshed
- Not suitable if upstream APIs enforce per-user authorisation
- The "pre-fetch" pattern gives the illusion of solving a performance problem that Token Exchange with caching (Alternative 2 + `(user, resource)` cache) also solves

**Recommendation signal:** Only viable for fire-and-forget background jobs calling internal APIs that have no user context. Not appropriate for MCP tool invocations triggered by end-users.

---

**Area 3 Recommendation:** **Alternative 2 (Token Exchange RFC 8693)**. It is already partially implemented and is the only approach that satisfies both the MCP spec's no-passthrough requirement and the need to preserve user identity downstream. Harden the existing `handleTokenExchangeGrant` by: (a) validating the `actor` claim, (b) binding Token-B's `aud` to the upstream resource URI, and (c) adding a `(subject, targetResource)` → `cachedToken` cache to avoid redundant exchange calls.

---

## Decision Area 4: PRM (RFC 9728) Integration Strategy

### Alternative 1: Add PRM Endpoint to Existing `OAuth2MetadataController`

Extend the existing `OAuth2MetadataController` (which serves `/.well-known/oauth-authorization-server`) with a second `@GetMapping` for `/.well-known/oauth-protected-resource`. Both endpoints share the same controller class and can reuse any common configuration beans (issuer URI, JWKS URI, scope definitions).

**Pros:**
- Minimal change: one new method in an existing class, no new Spring bean, no new test class (extend existing integration test)
- Co-location makes it easy to keep AS metadata and PRM metadata in sync (e.g., shared issuer URI constant)
- Least code churn — reduces review surface area

**Cons:**
- Conflates two conceptually distinct metadata documents in one controller: AS metadata (about token issuance) and PRM (about resource protection). As the project grows, this controller becomes a catch-all
- The `OAuth2MetadataController` name no longer accurately describes its responsibilities
- If the project ever separates the AS from the RS, the PRM endpoint will need to be extracted anyway

**Recommendation signal:** Acceptable as a fast tactical fix; plan to extract later if the AS/RS boundary is ever separated.

---

### Alternative 2: Dedicated `ProtectedResourceMetadataController`

Create a new `ProtectedResourceMetadataController` with a single `@GetMapping("/.well-known/oauth-protected-resource")`. It returns an `ProtectedResourceMetadata` record (or a `Map<String, Object>`) containing: `resource` (the MCP server's URI), `authorization_servers` (array with the AS issuer URI), `bearer_methods_supported`, `scopes_supported`, and `resource_signing_alg_values_supported`.

**Pros:**
- Clear single responsibility: one controller, one endpoint, one purpose
- Naming and package placement are unambiguous — easy to find and audit
- The `ProtectedResourceMetadata` response object can be a Java record, making it immutable and testable in isolation
- Aligns with the project's existing pattern: `OAuth2MetadataController` for AS metadata, `ProtectedResourceMetadataController` for PRM
- Easier to evolve independently (e.g., add `introspection_endpoint`, `token_formats_supported` later)

**Cons:**
- One more class and one more integration test to maintain
- Slight duplication of configuration references (issuer URI, JWKS URI) that must be kept in sync with `OAuth2MetadataController` — mitigated by injecting a shared `AuthServerProperties` bean

**Recommendation signal:** Recommended. Matches the project's existing controller-per-concern pattern and cleanly satisfies the RFC 9728 MUST.

---

### Alternative 3: Spring Security's Built-in OAuth2 Resource Server Support

Migrate token validation to Spring Security's `spring-security-oauth2-resource-server` auto-configuration (`spring.security.oauth2.resourceserver.jwt.*`). Spring Security's `BearerTokenAuthenticationEntryPoint` automatically emits `WWW-Authenticate: Bearer` headers (partially satisfying the 401 header gap), and community extensions exist for RFC 9728 PRM.

**Pros:**
- Significant reduction in hand-rolled security code: Spring Security handles JWT decoding, `aud` validation, scope extraction, and 401 responses with correct `WWW-Authenticate` headers
- `BearerTokenAuthenticationEntryPoint` automatically adds the `WWW-Authenticate: Bearer error="..."` header — fixes gap #2 with no custom code
- Future-proof: Spring Security tracks OAuth/OIDC spec updates; upgrading the library pulls in fixes
- `NimbusJwtDecoder` supports both local RSA public key and JWKS URI — compatible with Area 2 Alternative 2

**Cons:**
- The existing custom servlet filter implementation conflicts with Spring Security's filter chain — significant refactoring required to remove or adapt it
- Spring Security's auto-configuration assumes a standard AS; the embedded AS (Area 1 Alternative 1) requires careful configuration to avoid circular dependencies (the RS and AS share the same application context)
- The PRM endpoint (`/.well-known/oauth-protected-resource`) is not provided out-of-the-box by Spring Security as of Spring Boot 4.x — still requires a custom controller (Alternative 2 above), making this alternative a complement rather than a replacement for Area 4's decision
- Higher migration risk: touching the security filter chain can introduce regressions across all authenticated endpoints

**Recommendation signal:** Valuable as a medium-term investment (especially for `WWW-Authenticate` header handling and `aud` enforcement via `JwtDecoder` configuration), but not a short-term drop-in solution for the PRM endpoint specifically. Combine with Alternative 2: add the dedicated controller now, and plan a Spring Security migration for the filter chain separately.

---

**Area 4 Recommendation:** **Alternative 2 (Dedicated `ProtectedResourceMetadataController`)** for the PRM endpoint itself — it is the cleanest and least risky change. In parallel, adopt **Alternative 3's `BearerTokenAuthenticationEntryPoint`** selectively to fix the `WWW-Authenticate` header gap, without a full filter-chain migration. The full Spring Security resource server migration (Alternative 3) is worthwhile but should be a separate, planned task.
