# Research Plan

## Research Question
How should our MCP server handle authentication without token passthrough, per MCP spec?

## Research Type
Mixed (technical + literature)

## Methodology
1. Direct source analysis of the official MCP specification authorization page (latest spec 2025-11-25)
2. MCP documentation tutorials and extension guides
3. Codebase analysis of existing OAuth2 implementation in this project

## Gathering Strategy
| Category | Sources |
|----------|---------|
| MCP Spec | https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization |
| MCP Tutorials | https://modelcontextprotocol.io/docs/tutorials/security/authorization |
| MCP Extensions | enterprise-managed-authorization, oauth-client-credentials |
| Codebase | pl.devstyle.aj.core.oauth2.*, pl.devstyle.aj.core.security.* |

## Key Questions to Answer
1. What does the MCP spec REQUIRE vs RECOMMEND for auth?
2. What is the anti-passthrough requirement exactly?
3. What flows avoid passthrough while staying spec-compliant?
4. What does the existing codebase already implement?
5. What gaps remain vs spec?
