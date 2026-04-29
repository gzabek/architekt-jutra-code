# Scope Clarifications

## RSA Key Loading Strategy
**Decision**: Ephemeral only — generate fresh RSA-2048 key pair in memory at startup.
- No production key store management overhead.
- Tokens are 15-min short-lived; restart invalidation is acceptable.
- `RsaKeyConfiguration` generates key pair in `@Bean` method, no file I/O needed.

## Audience Allowlist
**Decision**: `application.properties` static list — `app.oauth2.allowed-exchange-audiences=...`
- Explicit, auditable, zero DB schema change.
- `OAuth2TokenFilter.handleTokenExchangeGrant` reads the property at startup.
- Token exchange request with `audience` not in list → `error=invalid_target`.
