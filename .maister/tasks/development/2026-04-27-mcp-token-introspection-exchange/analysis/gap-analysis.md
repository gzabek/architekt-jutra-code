# Gap Analysis — MCP Token Introspection & Token Exchange

## Current vs Desired State

| Component | Current | Desired |
|-----------|---------|---------|
| JwtTokenProvider | HS256/HMAC symmetric | RS256/RSA — private key signs, public key via JWKS |
| JwtAuthenticationFilter | Inline HMAC validation | NimbusJwtDecoder + aud claim validation |
| SecurityConfiguration | No WWW-Authenticate on 401 | BearerTokenAuthenticationEntryPoint with resource_metadata |
| OAuth2TokenFilter (exchange) | Token-B aud=issuer (bug) | Token-B aud=requested_resource param; allowlist enforced |
| OAuth2AuthorizationFilter | resource param ignored | resource param read + stored |
| AuthorizationCodeService | No resourceUri field | resourceUri stored per code |
| OAuth2MetadataController | Missing jwks_uri | jwks_uri present; PRM URL referenced |
| RsaKeyConfiguration | Does not exist | New @Configuration — RSA key pair management |
| JwksController | Does not exist | New GET /oauth2/jwks.json |
| ProtectedResourceMetadataController | Does not exist | New GET /.well-known/oauth-protected-resource |

## Task Characteristics

- has_reproducible_defect: true (Token-B aud=issuer bug)
- modifies_existing_code: true
- creates_new_entities: true
- involves_data_operations: false (auth codes stay in-memory)
- ui_heavy: false

## Risk Level: high

Touches the full auth critical path. Signing key migration invalidates all in-flight tokens at cutover. Any regression breaks entire MCP server auth.

## Scope Decisions Made

1. RSA Key Strategy: ephemeral key in dev (generated at startup); PEM from env var for prod
2. Audience Allowlist: configured via application.yml property (`app.oauth2.allowed-resource-uris`)

## Implementation Scope

New: RsaKeyConfiguration, JwksController, ProtectedResourceMetadataController
Modified: JwtTokenProvider, JwtAuthenticationFilter, SecurityConfiguration,
          OAuth2AuthorizationFilter, AuthorizationCodeService, OAuth2TokenFilter,
          OAuth2MetadataController, application.properties/yml
