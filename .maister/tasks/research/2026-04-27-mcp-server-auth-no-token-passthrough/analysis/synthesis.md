# Synthesis

## Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec?

## Cross-Reference Analysis

The three findings converge on a single architectural conclusion:

**Token passthrough is prohibited by the MCP spec at two levels:**
1. **Structural prohibition**: The MCP server is an OAuth 2.1 Resource Server. Resource servers accept and validate tokens; they do not forward them. This is a fundamental OAuth 2.1 role distinction.
2. **Explicit prohibition**: The spec states verbatim: "The MCP server MUST NOT pass through the token it received from the MCP client." (Section: Access Token Privilege Restriction)

The mechanism that enforces this is **audience binding**:
- Tokens issued for the MCP server carry `aud = mcp-server-uri`
- Such a token is cryptographically worthless at any other API
- The resource indicator (`resource` parameter per RFC 8707) ensures the AS mints audience-bound tokens
- The MCP server validates the `aud` claim, ensuring it only processes tokens meant for it

## Pattern: How to Handle Upstream API Calls Without Passthrough

When the MCP server needs to call an upstream service (e.g., a business API, database API), it has three clean options:

### Option A: Client Credentials (Machine-to-Machine)
The MCP server holds its own `client_id` + `client_secret` (or private key JWT) for the upstream service. It uses the `client_credentials` grant to obtain a token from the upstream service's AS. This is independent of the MCP client's token entirely.

### Option B: Token Exchange (RFC 8693) — Already In This Project
The MCP server accepts Token-A from the MCP client (audience-bound to the MCP server), then issues Token-B with a different audience for downstream use. The exchange is mediated by the MCP server acting as an AS for this purpose. Token-A is never forwarded; Token-B is freshly minted.

**This project already does this** in `OAuth2TokenFilter.handleTokenExchangeGrant()`.

### Option C: On-Behalf-Of (OBO) via Third-Party AS
Used with external AS (Entra, Keycloak). The MCP server presents Token-A to the external AS's token exchange endpoint; the AS validates delegation consent and returns Token-B for the downstream service. The MCP server only ever sends Token-B to downstream APIs.

## Current Project Status

**Well-positioned but needs RFC 9728 PRM endpoint.**

The project already implements the core anti-passthrough pattern correctly (Token Exchange). The main gap for MCP spec 2025-11-25 compliance is the Protected Resource Metadata endpoint (RFC 9728) and the corresponding `WWW-Authenticate` header with `resource_metadata` on 401 responses.

## Confidence Assessment
- Spec requirements: HIGH — read directly from official spec
- Current implementation gaps: HIGH — read directly from source code
- Best practices for upstream calls: HIGH — derived from spec + tutorial examples
