# Design Decisions

## Convergence Summary

| Decision Area | Chosen Approach |
|--------------|----------------|
| Authorization Server Location | Alt 1: Embedded AS (keep current) |
| Token Validation Strategy | Alt 2: RSA/EC asymmetric keys |
| Upstream Token Acquisition | Alt 2: Token Exchange RFC 8693 (keep current) |
| RFC 9728 PRM Integration | Alt 3: Spring Security built-in OAuth2 RS support |

## Rationale

### AS Location: Embedded (current)
Fastest path to compliance. The existing custom filter implementation is already 90% spec-compliant. Adding PRM and RFC 8707 resource binding brings it to full compliance without new infrastructure.

### Token Validation: RSA/EC asymmetric
Switching from HMAC HS256 to RSA/EC (e.g., RS256) enables:
- A `/oauth2/jwks.json` JWKS URI endpoint — standard for AS metadata advertisement
- External validation by any standard library without sharing the secret key
- Future-proofing for external AS migration (clients can validate tokens without introspection)
- Trade-off: requires managing a key pair vs a symmetric secret; manageable with Spring's `KeyPair` API

### Upstream Tokens: Token Exchange RFC 8693 (current)
Already implemented. Correctly satisfies anti-passthrough requirement. Preserves user identity in downstream Token-B. The main improvement needed is parameterizing the target audience so Token-B can have `aud = upstream-service-uri` rather than always `aud = MCP server issuer`.

### PRM: Spring Security built-in OAuth2 RS support
Using `spring-security-oauth2-resource-server` for the token validation layer provides:
- Standard `JwtDecoder` with JWKS validation
- Standard `BearerTokenAuthenticationFilter` replacing the custom `JwtAuthenticationFilter`
- Automatic `WWW-Authenticate` header on 401 responses with `resource_metadata` can be configured
- PRM endpoint can be added via a `@RestController`
- This pairs well with the RSA/EC key decision (the JWKS URI is already a first-class concept in Spring Security's resource server configuration)
- Trade-off: requires refactoring the current filter chain; significant but one-time effort

## Trade-offs Accepted
- Spring Security RS refactor is non-trivial (replaces `JwtAuthenticationFilter` + parts of `SecurityConfiguration`), but positions the project correctly for long-term maintainability
- RSA key management adds operational complexity (key rotation) but is the industry standard
