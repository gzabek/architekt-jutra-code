# Decision Log — MCP Server Auth Compliance (2025-11-25)

**Project**: Spring Boot 4.0.5 / Java 25 MCP Server  
**Spec target**: MCP Authorization Specification 2025-11-25  
**Date**: 2026-04-27

---

## ADR-001: RSA/EC over HMAC for Token Signing

### Status

Accepted

### Context

The MCP server currently issues JWT access tokens signed with HMAC-SHA256 (HS256). The shared secret is held only by this server, so token validation requires either:

1. Introspection (network call to the AS on every request), or
2. Access to the same shared secret (not distributable to external upstream services).

The MCP auth specification 2025-11-25 requires that resource servers be able to validate tokens independently. With token exchange (ADR-002), the upstream service must validate Token-B without phoning home to the MCP server on the hot path. This is impossible with a symmetric secret.

Additionally, the spec mandates publication of a `jwks_uri` in the AS metadata, which is meaningless for symmetric keys.

### Decision

Migrate token signing from HS256 (HMAC-SHA256) to RS256 (RSA-SHA256).

**Key management approach**:
- On startup, `RsaKeyConfiguration` attempts to load a `KeyPair` from a PKCS#12 KeyStore configured via `security.jwt.key-store`.
- If no KeyStore is configured (development / test), an ephemeral RSA-2048 key pair is generated in memory. Tokens issued this way do not survive server restarts — acceptable for non-production.
- The public key is published at `/oauth2/jwks.json` as a standard JWK Set.
- The `kid` header in every issued JWT references the key ID from the KeyStore alias (or a fixed string for ephemeral keys), enabling future key rotation.

**Implementation**:
- Replace `MacSigner` / `MACVerifier` (Nimbus) with `RSASSASigner` / `RSASSAVerifier`.
- Replace inline HMAC verification in `JwtAuthenticationFilter` with Spring Security's `NimbusJwtDecoder` configured with the local public key. This removes a custom verification loop and gains standard clock-skew handling and claim validation.

### Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Keep HS256, require introspection by upstream | Adds network latency on every upstream call; upstream service must be trusted with introspect credentials; tightly couples upstream to MCP server availability |
| ES256 (EC P-256) instead of RS256 | Smaller key material and faster signing; however RSA has wider ecosystem support in Java (standard `java.security.KeyStore`, JCA providers). EC can be adopted later as a second key in the JWKS set |
| Use Spring Authorization Server for key management | Would replace all custom filters; too large a migration scope for this task. Isolated `RsaKeyConfiguration` bean achieves the same outcome with minimal disruption |

### Consequences

- **Positive**: Upstream services can validate Token-B offline using the public JWKS endpoint. Key rotation becomes possible via `kid` without changing token consumers.
- **Positive**: `JwtAuthenticationFilter` becomes thinner — standard `JwtDecoder` handles parsing, signature verification, and basic claim validation.
- **Negative**: KeyStore management is a new operational concern in production deployments. Must document and provide a startup check that fails fast if the KeyStore is misconfigured.
- **Neutral**: Token size increases slightly due to the RS256 signature being larger than HS256.

---

## ADR-002: Token Exchange (RFC 8693) for Upstream Calls

### Status

Accepted

### Context

When an MCP tool handler needs to call an upstream service on behalf of the authenticated user, it needs a credential to present. Three approaches are possible:

1. **Token passthrough**: Forward the user's Token-A directly to the upstream service.
2. **Service account**: Use a static server-to-server credential unrelated to the user.
3. **Token exchange (RFC 8693)**: Present Token-A to the local AS and receive a new Token-B scoped and audience-bound to the upstream service.

The MCP auth specification 2025-11-25 explicitly prohibits token passthrough. Token-A is issued with `aud` set to the MCP server URI (via RFC 8707, see ADR-004); any upstream service that checks the `aud` claim will reject it.

A service account credential loses the user identity, which is required for auditing and for upstream services that enforce per-user authorisation.

### Decision

Use RFC 8693 OAuth 2.0 Token Exchange, implemented in the existing `OAuth2TokenFilter.handleTokenExchangeGrant` method, with improved audience binding.

**Flow**:
1. The MCP tool handler (acting as a confidential client of the embedded AS) posts to `/oauth2/token` with `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, presenting Token-A as the `subject_token`.
2. The AS validates Token-A (signature, expiry, issuer, scope).
3. The AS issues Token-B with:
   - `sub` inherited from Token-A (user identity preserved).
   - `aud` set to the value of the `audience` request parameter, or — if absent — to the configured `mcp.upstream-service-uri` property.
   - `scope` set to the intersection of requested scope and the scopes granted in Token-A.
   - Signed with the same RSA private key (RS256), verifiable by upstream via JWKS.
4. The tool handler presents Token-B to the upstream service.

**Allowed audience allowlist**: Token exchange requests whose `audience` value is not in a configured allowlist are rejected with `error=invalid_target`. This prevents the AS from being used as an unrestricted token minting service.

### Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Token passthrough | Explicitly prohibited by MCP auth spec 2025-11-25 |
| Service account (client_credentials) | Loses user identity; prevents per-user upstream authorisation |
| Phantom token / introspection at upstream | Requires upstream to support introspection; adds MCP server availability dependency on upstream call path |
| Separate upstream AS that the MCP server federates to | Out of scope; significantly increases operational complexity |

### Consequences

- **Positive**: User identity is preserved in Token-B; upstream can enforce per-user policies.
- **Positive**: Token-A is never exposed to upstream services; compromise of upstream does not expose the original user token.
- **Positive**: Token-B can have a shorter lifetime and narrower scope than Token-A.
- **Negative**: The tool handler must be a registered OAuth client of the embedded AS, adding configuration overhead.
- **Negative**: Token exchange adds one local HTTP round-trip per upstream call. This is mitigated by short-lived Token-B caching in the tool handler (keyed by Token-A hash + audience).

---

## ADR-003: RFC 9728 Protected Resource Metadata as Mandatory Addition

### Status

Accepted

### Context

The MCP auth specification 2025-11-25 (§3.2) requires that MCP servers support RFC 9728 (OAuth 2.0 Protected Resource Metadata). Without this endpoint, MCP clients cannot perform zero-configuration discovery of which AS protects the resource. Clients would instead need the AS URL to be manually configured — contrary to the plug-and-play model that the MCP ecosystem targets.

The current implementation has AS metadata (`/.well-known/oauth-authorization-server`) but no protected resource metadata document. Clients that follow the RFC 9728 discovery flow will fail at step 1.

The `WWW-Authenticate` response on 401 is the entry point for this discovery flow. Currently the server returns a bare `401` with no `WWW-Authenticate` header, which is non-compliant with RFC 6750 and the MCP spec.

### Decision

Add `ProtectedResourceMetadataController` and update `SecurityConfiguration` authenticationEntryPoint as described in the high-level design.

**`/.well-known/oauth-protected-resource` document**:
```json
{
  "resource": "<mcp.server-uri>",
  "authorization_servers": ["<issuer>"],
  "scopes_supported": ["mcp:read", "mcp:write"],
  "bearer_methods_supported": ["header"]
}
```

**`WWW-Authenticate` header on 401**:
```
Bearer resource_metadata="<host>/.well-known/oauth-protected-resource", scope="mcp:read"
```

Both are derived from application properties (`mcp.server-uri`, `mcp.scopes`) so they require no code changes as the server's URI changes between environments.

The PRM endpoint requires **no authentication** — it is intentionally public, analogous to `/.well-known/openid-configuration`.

### Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Skip RFC 9728, rely on client-side AS URL configuration | Non-compliant with MCP auth spec 2025-11-25; prevents zero-configuration client setup |
| Use Spring Security OAuth2 Resource Server's built-in PRM support | Spring Security 6.x does not yet provide a ready-made PRM controller; implementing it directly is one controller class and is simpler than pulling in a not-yet-stable module |
| Combine PRM and AS metadata into one document | The two documents serve different roles (resource vs. AS); RFC 9728 and RFC 8414 define distinct discovery URLs. Combining them is non-standard |

### Consequences

- **Positive**: MCP clients implementing RFC 9728 discovery (Claude Desktop, etc.) will work with zero manual AS configuration.
- **Positive**: `WWW-Authenticate` header on 401 is now RFC 6750-compliant, which is a requirement of the broader OAuth 2.0 Bearer Token spec.
- **Positive**: One additional public endpoint with no state — trivially cacheable by clients.
- **Negative**: The `resource` URI in the PRM document must exactly match the `aud` claim in issued tokens (see ADR-004). A mismatch silently breaks clients. Mitigation: a startup self-check that compares `mcp.server-uri` against the `aud` in a synthetic token.

---

## ADR-004: Audience Binding via RFC 8707 Resource Parameter

### Status

Accepted

### Context

Token-A is currently issued without an `aud` (audience) claim. This means any service that obtains a Token-A — legitimately or through theft — could present it to any resource server. This violates the principle of least privilege and makes token replay attacks across services possible.

The MCP auth spec 2025-11-25 requires that access tokens be bound to the specific resource server for which they were issued. RFC 8707 (Resource Indicators for OAuth 2.0) defines the standard mechanism: a `resource` parameter in the authorisation request that is propagated through to the token's `aud` claim.

Without audience binding, the token exchange (ADR-002) anti-passthrough guarantee is also weaker: an upstream service cannot distinguish between a Token-A that was legitimately exchanged versus one that was forwarded directly, because neither carries a meaningful audience restriction.

### Decision

Implement RFC 8707 audience binding throughout the authorization code flow:

1. `OAuth2AuthorizationFilter`: Accept and validate the `resource` query parameter. The value must match the configured `mcp.server-uri`. Store it alongside the auth code in `AuthorizationCodeService`.
2. `OAuth2TokenFilter.handleAuthorizationCodeGrant`: Retrieve the stored `resource` value when redeeming the auth code. Set `aud = [resource]` in Token-A.
3. `JwtAuthenticationFilter` / `JwtDecoder`: Add `aud` validation — the decoder must assert that `aud` contains the configured server URI. Spring Security's `JwtDecoder` supports this via `JwtValidators.createDefaultWithIssuer(issuer)` combined with an `AudienceValidator`.

**Resource parameter handling**:
- If the `resource` parameter is absent from the authorisation request, the server defaults to `aud = [mcp.server-uri]` (the server's own URI). This preserves backward compatibility with clients that do not yet send `resource`.
- If the `resource` parameter is present but does not match `mcp.server-uri`, the AS returns `error=invalid_target` per RFC 8707 §2.

### Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| No audience claim (current state) | Non-compliant; any service could accept the token; token exchange anti-passthrough is unenforceable |
| Static `aud` hardcoded in `JwtTokenProvider` | Does not follow RFC 8707; does not allow a single AS to issue tokens for multiple resource servers in future |
| Use `azp` (authorised party) instead of `aud` | `azp` identifies the client, not the resource server; semantically wrong; `aud` is the standard claim for this purpose |
| Require `resource` parameter (no default) | Would break existing clients during rollout; defaulting to `mcp.server-uri` is safe and spec-compliant (RFC 8707 §2 permits server-side defaults) |

### Consequences

- **Positive**: Token-A can only be used at the MCP server; any upstream service with correct `aud` validation will reject it, enforcing the anti-passthrough guarantee.
- **Positive**: Aligns with the RFC 8707 standard, enabling future multi-resource scenarios (one AS, multiple resource servers).
- **Positive**: Token replay attacks across resource servers are blocked at the token validation layer.
- **Negative**: Requires a Liquibase migration to store `resource_uri` in the `authorization_codes` table. Migration must be backward-compatible (nullable column).
- **Negative**: Clients that do not send a `resource` parameter will receive tokens with a server-default `aud`. If such clients later move to a multi-resource deployment, they will need to be updated.
