# Finding: MCP Spec Authorization Requirements

**Source**: https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization
**Confidence**: High (official spec)

## Role Assignment

The MCP spec explicitly assigns OAuth 2.1 roles:
- **MCP server** = OAuth 2.1 Resource Server (validates and accepts access tokens)
- **MCP client** = OAuth 2.1 client (makes requests on behalf of a resource owner)
- **Authorization server** = issues tokens; may be co-located with MCP server or external

## Hard Requirements (MUST)

### Resource Server Behavior
1. MCP servers MUST implement OAuth 2.0 Protected Resource Metadata (RFC 9728)
2. MCP servers MUST serve PRM at `/.well-known/oauth-protected-resource` or via `WWW-Authenticate` header
3. MCP servers MUST validate access tokens before processing requests (OAuth 2.1 Section 5.2)
4. MCP servers MUST validate tokens were issued **specifically for them** (audience check)
5. MCP servers MUST only accept tokens intended for themselves; MUST reject tokens without correct `aud` claim
6. **MCP servers MUST NOT pass through the token received from the MCP client to upstream APIs**
7. Invalid/expired tokens MUST receive HTTP 401

### Token Passthrough Prohibition (Critical)
> "If the MCP server makes requests to upstream APIs, it may act as an OAuth client to them. The access token used at the upstream API is a separate token, issued by the upstream authorization server. **The MCP server MUST NOT pass through the token it received from the MCP client.**"

> "MCP servers MUST NOT accept or transit any other tokens."

### Client Requirements (for our server to enforce)
- Clients MUST use `Authorization: Bearer <token>` header (not query string)
- Clients MUST include `resource` parameter (RFC 8707) in both auth and token requests
- PKCE with S256 is REQUIRED; servers MUST expose `code_challenge_methods_supported: ["S256"]`
- All authorization endpoints MUST be served over HTTPS (localhost exempted in dev)

## SHOULD Recommendations
- Support OAuth 2.0 Authorization Server Metadata (RFC 8414) — *this project does this*
- Support Dynamic Client Registration (RFC 7591) — *this project does this*
- Support Client ID Metadata Documents — *not yet in this project*
- Enforce token expiration and rotation
- Issue short-lived access tokens (project uses 15min — good)
- Rotate refresh tokens (project does this — good)
- Include `scope` in WWW-Authenticate 401 challenges
- Validate redirect URIs exactly

## Discovery Flow (latest spec, 2025-11-25)
1. Client makes unauthenticated MCP request
2. Server responds 401 with `WWW-Authenticate: Bearer resource_metadata="..."` pointing to PRM doc
3. Client fetches PRM doc to find authorization server URL(s)
4. Client fetches AS metadata (OAuth 2.0 or OIDC discovery)
5. Client registers (Client ID Metadata Doc > DCR > pre-registered > manual)
6. Client runs OAuth 2.1 Auth Code + PKCE flow, with `resource` parameter
7. Client uses resulting Bearer token for all MCP requests

## Token Audience Validation (Anti-Passthrough Mechanism)
The spec ties anti-passthrough to **audience binding via RFC 8707 resource indicators**:
- The `resource` parameter in auth requests ensures the AS mints a token **bound to the MCP server URI**
- The MCP server validates the `aud` claim matches itself
- This means the token is cryptographically tied to the MCP server and worthless at any other API
- When the MCP server then calls upstream APIs, it MUST obtain a **separate token** from the upstream's own AS

## What Happens With Upstream API Calls
The spec is explicit: the MCP server is an **OAuth client** to upstream services. It must:
1. Have its own client credentials registered with upstream services' authorization servers
2. Obtain tokens for upstream APIs independently (client credentials or token exchange)
3. Never forward the MCP client's token to any upstream service
