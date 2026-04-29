# Finding: Approved Auth Flows Without Token Passthrough

**Source**: MCP spec, MCP tutorials, extension docs
**Confidence**: High

## Flow 1: MCP Server as Authorization Server (Self-Hosted)

The MCP server itself acts as both the Resource Server and Authorization Server.
Clients complete OAuth 2.1 Authorization Code + PKCE flow directly against the MCP server.

```
Client → MCP Server: GET /mcp  (no token)
MCP Server → Client: 401, WWW-Authenticate: Bearer resource_metadata="https://server/.well-known/oauth-protected-resource"
Client → MCP Server: GET /.well-known/oauth-protected-resource
MCP Server → Client: { authorization_servers: ["https://server"], scopes_supported: [...] }
Client → MCP Server: GET /.well-known/oauth-authorization-server
MCP Server → Client: { authorization_endpoint, token_endpoint, ... code_challenge_methods_supported: ["S256"] }
[DCR or Client ID Metadata Doc registration if needed]
Client → Browser: open /authorize?...&code_challenge=...&resource=https://mcp-server
Browser → MCP Server: /authorize (user logs in, consents)
MCP Server → Client: auth code via redirect
Client → MCP Server: POST /oauth2/token (code + code_verifier + resource)
MCP Server → Client: { access_token, refresh_token, ... }  (aud = MCP server URI)
Client → MCP Server: GET /mcp, Authorization: Bearer <token>
MCP Server → Client: validates aud, processes MCP request
```

**This is the current project's approach.** The project implements this with custom servlet filters.

**Anti-passthrough**: Token has `aud` = MCP server URI. The MCP server issues this token itself and knows it should NEVER forward it anywhere.

## Flow 2: Delegated Auth via Third-Party Authorization Server

MCP server acts as a Resource Server only; auth is handled by an external AS (Keycloak, Auth0, Entra, etc.).

```
Client → MCP Server: 401, resource_metadata pointing to external AS
Client → Keycloak: discover, register, auth-code + PKCE with resource=<mcp-server-uri>
Keycloak → Client: access_token (aud = mcp-server-uri)
Client → MCP Server: Bearer token
MCP Server → Keycloak: validate (JWT local validation or introspection endpoint)
  - validate signature (JWKS)
  - validate aud = this server's URI
  - validate scopes
```

**Anti-passthrough**: Same — token is audience-bound to the MCP server. MCP server only does validation, not forwarding.

If the MCP server needs to call upstream APIs (e.g., a database API, internal service), it must:
- Have its own service account / client credentials for those upstream services
- Call the upstream AS independently using `client_credentials` grant
- Use that separate token for upstream calls

## Flow 3: Token Exchange (RFC 8693) — What This Project Does

The project implements token exchange (`urn:ietf:params:oauth:grant-type:token-exchange`). This is a valid anti-passthrough pattern:

1. MCP client presents Token-A (issued by MCP server's AS for the MCP server)
2. MCP server validates Token-A (audience check, signature, scopes)
3. MCP server exchanges Token-A for Token-B (different audience = upstream service)
4. Token-B is used for upstream API calls, never Token-A

This satisfies the spec requirement: the original client token is NOT forwarded. The server mints a new token with a different audience.

**Current implementation**: `OAuth2TokenFilter.handleTokenExchangeGrant()` maps MCP scopes to backend permissions and issues a new token with `aud = issuer URL`.

## Flow 4: Client Credentials (Machine-to-Machine)

For automated pipelines without a human user:
```
Client → AS: POST /token (client_id + client_secret OR JWT assertion)
AS → Client: access_token
Client → MCP Server: Bearer token
MCP Server: validates token (aud, scopes, signature)
```

This is an official MCP extension (`io.modelcontextprotocol/oauth-client-credentials`). Suitable for CI/CD and background services.

## Flow 5: Enterprise-Managed (ID-JAG)

For enterprise SSO:
```
User authenticates via enterprise IdP (Okta, Entra)
Client obtains Identity Assertion (ID Token or SAML)
Client exchanges ID assertion for ID-JAG from IdP
Client presents ID-JAG to MCP Authorization Server
AS validates ID-JAG, issues MCP-specific access token
Client uses MCP access token for MCP requests
```

No token passthrough: the IdP's token is NOT forwarded. The MCP AS issues its own scoped token.

## Summary: All Compliant Flows Share One Pattern

**In every compliant flow:**
1. The token the MCP server accepts has `aud` = the MCP server itself
2. The MCP server validates the audience claim before processing
3. If the MCP server calls upstream APIs, it obtains **a different token** with `aud` = the upstream service
4. The MCP client's token is NEVER forwarded
