## MCP Server Standards

### Registration via opencode.json
MCP servers are declared in the project-root `opencode.json` under an `"mcpServers"` key. Use the OpenCode config schema (`"$schema": "https://opencode.ai/config.json"`).

Each entry uses the server name as the key and an object with:
- `"type"`: `"local"` for a locally-spawned process, `"remote"` for an HTTP/SSE endpoint
- `"command"` (local only): array of executable + args
- `"url"` (remote only): full endpoint URL

Example — local process:
```json
"mcpServers": {
  "my-server": {
    "type": "local",
    "command": ["node", "path/to/server.js"]
  }
}
```

Example — remote endpoint:
```json
"mcpServers": {
  "my-remote": {
    "type": "remote",
    "url": "https://mcp.example.com/sse"
  }
}
```

### Tool / Resource / Prompt Naming
Use `snake_case`, verb-first names for all exposed MCP tools (e.g. `get_user`, `create_order`, `list_products`). Resource URIs use lowercase kebab-case. Prompt names follow the same `snake_case` verb-first convention.
