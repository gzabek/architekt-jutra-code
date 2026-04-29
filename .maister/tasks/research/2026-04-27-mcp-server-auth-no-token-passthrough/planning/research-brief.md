# Research Brief

## Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec?

## Research Type
Mixed — combines technical (MCP spec, OAuth flows) and literature (best practices, security guidance)

## Scope

### Included
- MCP specification authentication requirements (official spec, 2024/2025)
- OAuth 2.0 Authorization Framework as required by MCP
- Authorization server metadata discovery (RFC 8414)
- Dynamic client registration (RFC 7591)
- Token exchange patterns where the MCP server acts as a proper OAuth resource server or authorization server — without forwarding user tokens to upstream services
- Implementation patterns for Spring Boot MCP servers
- Security guidance for MCP auth flows

### Excluded
- Token passthrough patterns (explicitly not desired per user)
- Non-MCP protocols or alternative RPC frameworks

### Constraints
- Must align with the official MCP specification
- Project tech stack: Spring Boot 4.0.5, Java 25

## Success Criteria
1. Clear understanding of what the MCP spec mandates for authentication
2. Concrete OAuth flows that avoid token passthrough
3. Implementation guidance applicable to this Spring Boot project
4. Security trade-offs documented
