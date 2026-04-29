# Project Vision

## What is aj?

**aj** is a plugin-based microkernel platform built on Spring Boot — a foundation for building extensible, AI-accessible business applications. The platform separates a minimal, stable core from domain-specific plugins that can be developed, deployed, and updated independently.

## Purpose

aj exists to demonstrate and validate an architecture for enterprise applications that are:

- **Extensible by design**: New capabilities arrive as plugins, not as modifications to the core
- **AI-native**: The platform exposes its data and actions to AI agents via MCP (Model Context Protocol), making it a first-class citizen in AI-assisted workflows
- **Security-first**: A custom OAuth2 Authorization Server with token exchange (RFC 8693) ensures that AI agents operate with properly scoped, user-identity-preserving tokens — not bypassed auth or raw credentials

## Core Goals

1. **Plugin architecture that works in practice**: Real sandboxing via iframes + postMessage, a typed Plugin SDK, and a plugin data storage model that doesn't pollute domain entities

2. **AI agent access done right**: AI agents (opencode, Claude Desktop, etc.) connect via MCP. The MCP server validates agent tokens via RFC 7662 introspection, then exchanges them for scoped internal tokens via RFC 8693 — preserving user identity end-to-end without token passthrough anti-patterns

3. **LLM-powered features in the core**: Not a bolt-on — AI features like `productvalidation` are first-class plugins, routed through LiteLLM with LangFuse observability and Presidio PII protection

4. **Developer experience**: The local dev environment spins up the complete stack (PostgreSQL, LiteLLM, LangFuse, Presidio) via a single `docker compose up -d`

## Target Users

- **Developers** building domain plugins on top of the aj platform
- **AI agents** (opencode, Claude Desktop) consuming aj data and actions via MCP tools
- **End users** interacting with the React SPA and embedded plugins

## Non-Goals (Current Phase)

- Multi-tenant data isolation (planned, not implemented)
- Production deployment configuration (no Dockerfile, no CI/CD yet)
- Plugin hot-reload or dynamic classloading (plugins are currently embedded, not externally loaded JARs)

---

*Last Updated*: 2026-04-26
