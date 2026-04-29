# Research Report: MCP Server Authentication Without Token Passthrough

**Research Question**: How should our MCP server handle authentication without token passthrough, per MCP spec?
**Date**: 2026-04-27
**Confidence**: High
**Sources**: MCP spec (2025-11-25), MCP tutorials, OAuth 2.1/RFC 8707/RFC 9728/RFC 8693, codebase

---

## Executive Summary

The MCP spec (latest: 2025-11-25) **explicitly forbids token passthrough** at the protocol level. The MCP server MUST act as an OAuth 2.1 Resource Server that validates tokens issued specifically for it — and MUST NOT forward those tokens to any upstream service. When the MCP server needs to call upstream APIs, it must obtain separate tokens using its own credentials, entirely independent of the MCP client's token.

**This project's existing token exchange implementation is fundamentally correct.** The primary remaining gap is adding the Required Protected Resource Metadata (RFC 9728) endpoint.

---

## 1. What the MCP Spec Requires (MUST)

### 1.1 The MCP Server is a Resource Server, Not a Proxy

Per MCP spec 2025-11-25, Roles section:
> "A protected MCP server acts as an OAuth 2.1 resource server, capable of accepting and responding to protected resource requests using access tokens."

This means the MCP server:
- Accepts Bearer tokens in `Authorization` headers
- Validates those tokens locally (signature, expiry, audience, scopes)
- Does NOT forward those tokens anywhere

### 1.2 Explicit Token Passthrough Prohibition

Direct quotes from the spec (Access Token Privilege Restriction section):

> "The MCP server MUST NOT pass through the token it received from the MCP client."
> "MCP servers MUST NOT accept or transit any other tokens."
> "MCP clients MUST NOT send tokens to the MCP server other than ones issued by the MCP server's authorization server."

### 1.3 Audience Binding is the Enforcement Mechanism

The spec requires:
- MCP servers MUST validate that access tokens were issued **specifically for them** (RFC 8707 audience check)
- MCP clients MUST include `resource=<mcp-server-uri>` in both authorization and token requests
- This ensures the AS mints tokens with `aud = mcp-server-uri`
- A token with `aud = mcp-server-uri` is cryptographically worthless at any other API

### 1.4 Protected Resource Metadata (RFC 9728) — REQUIRED

The latest spec mandates:
- MCP servers MUST implement RFC 9728 Protected Resource Metadata
- Must serve it at `/.well-known/oauth-protected-resource`
- Must include `resource_metadata` URL in `WWW-Authenticate` header on 401 responses
- Clients MUST use PRM to discover authorization servers (not AS metadata directly)

### 1.5 Other Hard Requirements
- PKCE with S256 is REQUIRED (already implemented)
- HTTPS for all auth endpoints in production (already enforced)
- HTTP 401 for invalid tokens, HTTP 403 for insufficient scopes
- `code_challenge_methods_supported: ["S256"]` in AS metadata (already present)

---

## 2. How the Architecture Works (The Full Correct Flow)

### For MCP Client Authentication (User-Facing)

```
Step 1: Client requests MCP endpoint without token
  → Server responds: HTTP 401
  → Header: WWW-Authenticate: Bearer resource_metadata="https://server/.well-known/oauth-protected-resource", scope="mcp:read"

Step 2: Client fetches PRM (/.well-known/oauth-protected-resource)
  → Server returns: { resource: "https://server", authorization_servers: ["https://server"], scopes_supported: ["mcp:read","mcp:edit"] }

Step 3: Client fetches AS metadata (/.well-known/oauth-authorization-server)
  → Server returns: { authorization_endpoint, token_endpoint, code_challenge_methods_supported: ["S256"], ... }

Step 4: Client registers (DCR or Client ID Metadata Doc)

Step 5: Client opens browser to /oauth2/authorize?
  client_id=...&redirect_uri=...&scope=mcp:read&
  code_challenge=...&code_challenge_method=S256&
  resource=https://server  ← RFC 8707 resource indicator

Step 6: User authenticates, consents

Step 7: Client exchanges code at /oauth2/token
  POST: code=...&code_verifier=...&resource=https://server
  ← Server validates PKCE, issues access_token with aud="https://server"

Step 8: Client makes MCP requests
  Authorization: Bearer <access_token>
  ← Server validates: signature, expiry, aud="https://server", scopes
```

### For Upstream API Calls (Server-to-Service)

The MCP server NEVER uses the client's token for upstream calls. Instead:

**Option A — Client Credentials (recommended for machine-to-machine):**
```
MCP Server → Upstream AS: POST /token
  grant_type=client_credentials
  client_id=mcp-server-app-id
  client_secret=<server-secret>
Upstream AS → MCP Server: access_token (aud = upstream service)
MCP Server → Upstream API: Bearer <upstream-token>
```

**Option B — Token Exchange RFC 8693 (already in this project):**
```
MCP Client → MCP Server: Token-A (aud = MCP server)
MCP Server validates Token-A
MCP Server: internally exchanges → Token-B (aud = downstream service)
MCP Server → Downstream API: Bearer Token-B
Token-A is never forwarded, consumed by the server itself
```

---

## 3. Existing Implementation Assessment

### What the Project Gets Right ✅

| Feature | Implementation |
|---------|---------------|
| Authorization Code + PKCE S256 | `OAuth2AuthorizationFilter` + `OAuth2TokenFilter` |
| PKCE validation (S256 only) | `OAuth2TokenFilter.validatePkceCodeVerifier()` |
| AS Metadata endpoint (RFC 8414) | `OAuth2MetadataController` |
| Dynamic Client Registration | `PublicClientRegistrationFilter` |
| Token introspection | `OAuth2IntrospectionFilter` |
| Refresh token rotation | `RefreshTokenService` |
| Short-lived access tokens (15 min) | `JwtTokenProvider.OAUTH2_TOKEN_EXPIRATION_MS = 900_000` |
| Token Exchange RFC 8693 | `OAuth2TokenFilter.handleTokenExchangeGrant()` |
| Scope enforcement via Spring Security | `SecurityConfiguration` — PERMISSION_mcp:read, PERMISSION_mcp:edit |
| Audience support in token generation | `JwtTokenProvider.generateOAuth2Token(username, scopes, issuer, audience)` |

### Gaps to Address

| Gap | Priority | Spec Requirement |
|-----|----------|-----------------|
| Missing `/.well-known/oauth-protected-resource` (RFC 9728 PRM) | **HIGH** | MUST (spec 2025-11-25) |
| Missing `WWW-Authenticate: Bearer resource_metadata="..."` on 401 responses | **HIGH** | MUST (RFC 9728 §5.1) |
| `resource` parameter not validated/used in token issuance (RFC 8707) | **HIGH** | MUST (clients MUST send it; server SHOULD bind token) |
| Audience claim missing from auth-code grant tokens (one-arg `generateOAuth2Token`) | **HIGH** | MUST validate `aud` to prevent accepting tokens meant for others |
| Token body logged including raw access token | **MEDIUM** | Security best practice |
| Client ID Metadata Documents not supported | **LOW** | SHOULD (preferred in latest spec) |

---

## 4. Recommended Implementation for RFC 9728 PRM

Add a new endpoint (alongside the existing AS metadata controller):

```java
// In OAuth2MetadataController (or a new ProtectedResourceMetadataController)

@GetMapping(value = "/.well-known/oauth-protected-resource", produces = MediaType.APPLICATION_JSON_VALUE)
public Map<String, Object> getProtectedResourceMetadata(HttpServletRequest request) {
    var baseUrl = getBaseUrl(request);
    return Map.of(
        "resource", baseUrl,
        "authorization_servers", List.of(baseUrl),
        "scopes_supported", List.of("mcp:read", "mcp:edit"),
        "bearer_methods_supported", List.of("header"),
        "resource_documentation", baseUrl + "/api/docs"
    );
}
```

And update the 401 authentication entry point in `SecurityConfiguration` to include the header:

```java
.authenticationEntryPoint((request, response, authException) -> {
    var baseUrl = getBaseUrl(request);
    response.setHeader("WWW-Authenticate",
        "Bearer realm=\"mcp\", " +
        "resource_metadata=\"" + baseUrl + "/.well-known/oauth-protected-resource\", " +
        "scope=\"mcp:read\"");
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    // ... write error body
})
```

---

## 5. Token Exchange — Why This Project's Approach Is Correct

The token exchange implementation (`handleTokenExchangeGrant`) correctly prevents passthrough:

1. **Receives Token-A** — the MCP client's access token (audience = MCP server)
2. **Validates Token-A** — signature, expiry, parsed claims
3. **Maps scopes** — `mcp:read` → `READ`, `mcp:edit` → `EDIT`
4. **Issues Token-B** — fresh JWT, `aud = issuer URL`, subject = original user, backend permissions
5. **Returns Token-B** — the caller (an internal service or another system) uses this

Token-A is NEVER forwarded. This is exactly what the spec requires.

**Note**: Currently `handleTokenExchangeGrant` issues Token-B with `aud = issuer URL` (the same server). If Token-B is intended for a different upstream service, its audience should be the upstream service URI, not the MCP server URI. This would need to be parameterized via the `audience` parameter in the token exchange request.

---

## 6. Security Checklist for Production

- [ ] Add `/.well-known/oauth-protected-resource` endpoint (RFC 9728)
- [ ] Add `WWW-Authenticate` with `resource_metadata` on all 401 responses
- [ ] Validate `resource` parameter in authorization and token requests; use it to set token `aud`
- [ ] Ensure all auth-code-grant tokens have `aud` claim (fix the call to `generateOAuth2Token` that omits audience)
- [ ] Remove or redact token logging (`log.info("OAuth2 token response body: {}", ...)`)
- [ ] HTTPS enforced in production (all auth endpoints)
- [ ] Redirect URIs validated exactly (already done)
- [ ] Never log `Authorization` headers or token values

---

## 7. References

| Standard | Purpose |
|----------|---------|
| [MCP Auth Spec 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization) | Primary spec — roles, flows, token passthrough prohibition |
| [OAuth 2.1 (draft-ietf-oauth-v2-1-13)](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1-13) | Core auth framework |
| [RFC 9728](https://datatracker.ietf.org/doc/html/rfc9728) | OAuth 2.0 Protected Resource Metadata — REQUIRED by latest MCP spec |
| [RFC 8707](https://www.rfc-editor.org/rfc/rfc8707.html) | Resource Indicators — `resource` parameter for audience binding |
| [RFC 8693](https://datatracker.ietf.org/doc/html/rfc8693) | OAuth 2.0 Token Exchange — the formal spec for what the project implements |
| [RFC 8414](https://datatracker.ietf.org/doc/html/rfc8414) | AS Metadata discovery — already implemented |
| [RFC 7591](https://datatracker.ietf.org/doc/html/rfc7591) | Dynamic Client Registration — already implemented |
