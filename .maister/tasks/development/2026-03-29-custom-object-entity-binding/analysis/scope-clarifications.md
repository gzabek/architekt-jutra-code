# Scope Clarifications — Phase 2

## Critical Decisions

### Save Mechanism for Entity Binding
- **Decision**: Query params (non-breaking)
- PUT /api/plugins/{pluginId}/objects/{objectType}/{objectId}?entityType=PRODUCT&entityId=123
- Body remains just the data payload
- entityType and entityId are optional params

### JSONB Filter Syntax
- **Decision**: Reuse colon-separated pattern from PluginDataSpecification
- Example: `?filter=quantity:gt:0` or `?filter=status:eq:active`
- Consistent with existing product pluginData filtering

## Important Decisions

### Unique Constraint
- **Decision**: Keep existing `(pluginId, objectType, objectId)` constraint unchanged
- Entity binding is additional metadata, not part of uniqueness

### Plugin SDK Updates
- **Decision**: Include in this task
- Update warehouse sdk.ts types and host-side plugin message handler
- Support entityType/entityId params in save/list SDK calls
