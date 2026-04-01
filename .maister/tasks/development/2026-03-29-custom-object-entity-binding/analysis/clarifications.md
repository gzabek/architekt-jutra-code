# Clarifications — Phase 1

## Entity Binding Approach
- Use **ENUM** for `entity_type` (not VARCHAR) + **BIGINT** for `entity_id`
- Java enum mapping to DB enum or EnumType.STRING
- Generic binding — supports Product now, extensible to other entity types via enum values

## Query API
- Extend existing endpoint: `GET /api/plugins/{pluginId}/objects/{objectType}?productId=123&quantity>0`
- Query params filter by entity binding (e.g., `productId=123`) AND by JSONB data values (e.g., `quantity>0`)
- No new endpoint paths needed — enhance existing list endpoint with query/filter support

## Plugin Data on Product
- Use existing `PluginDataService.setData(pluginId, productId, data)` for warehouse availability
- No backend changes needed for this — warehouse plugin calls `thisPlugin.setData(productId, { available: true })`
- This is purely a frontend/plugin usage pattern
