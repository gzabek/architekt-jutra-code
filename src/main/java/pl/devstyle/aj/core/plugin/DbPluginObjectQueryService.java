package pl.devstyle.aj.core.plugin;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static pl.devstyle.aj.jooq.tables.PluginObjects.PLUGIN_OBJECTS;

/**
 * jOOQ query service for plugin object JSONB filtering with proper bind parameters.
 * Follows Db*QueryService naming convention per jOOQ standards.
 */
@Service
public class DbPluginObjectQueryService {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public DbPluginObjectQueryService(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PluginObjectResponse> findByFilter(String pluginId, String objectType,
                                                    EntityType entityType, Long entityId,
                                                    String filter, int limit) {
        var conditions = new ArrayList<Condition>();
        conditions.add(PLUGIN_OBJECTS.PLUGIN_ID.eq(pluginId));

        if (objectType != null) {
            conditions.add(PLUGIN_OBJECTS.OBJECT_TYPE.eq(objectType));
        }
        if (entityType != null) {
            conditions.add(PLUGIN_OBJECTS.ENTITY_TYPE.eq(entityType.name()));
        }
        if (entityId != null) {
            conditions.add(PLUGIN_OBJECTS.ENTITY_ID.eq(entityId));
        }
        if (filter != null) {
            conditions.add(parseFilter(filter));
        }

        return dsl.select(
                        PLUGIN_OBJECTS.ID, PLUGIN_OBJECTS.PLUGIN_ID,
                        PLUGIN_OBJECTS.OBJECT_TYPE, PLUGIN_OBJECTS.OBJECT_ID,
                        PLUGIN_OBJECTS.DATA, PLUGIN_OBJECTS.ENTITY_TYPE, PLUGIN_OBJECTS.ENTITY_ID,
                        PLUGIN_OBJECTS.CREATED_AT, PLUGIN_OBJECTS.UPDATED_AT)
                .from(PLUGIN_OBJECTS)
                .where(conditions)
                .limit(limit)
                .fetch(r -> {
                    Map<String, Object> data = null;
                    var jsonb = r.get(PLUGIN_OBJECTS.DATA);
                    if (jsonb != null) {
                        try {
                            data = objectMapper.readValue(jsonb.data(), Map.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to deserialize plugin object data", e);
                        }
                    }

                    EntityType et = null;
                    var entityTypeStr = r.get(PLUGIN_OBJECTS.ENTITY_TYPE);
                    if (entityTypeStr != null) {
                        et = EntityType.valueOf(entityTypeStr);
                    }

                    return new PluginObjectResponse(
                            r.get(PLUGIN_OBJECTS.ID),
                            r.get(PLUGIN_OBJECTS.PLUGIN_ID),
                            r.get(PLUGIN_OBJECTS.OBJECT_TYPE),
                            r.get(PLUGIN_OBJECTS.OBJECT_ID),
                            data,
                            et,
                            r.get(PLUGIN_OBJECTS.ENTITY_ID),
                            r.get(PLUGIN_OBJECTS.CREATED_AT),
                            r.get(PLUGIN_OBJECTS.UPDATED_AT)
                    );
                });
    }

    static Condition parseFilter(String filter) {
        var parts = filter.split(":", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid filter format. Expected: {jsonPath}:{operator}:{value}");
        }

        var jsonPath = parts[0];
        var op = parts[1];
        var val = parts.length > 2 ? parts[2] : null;

        if (!jsonPath.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Invalid jsonPath: must contain only alphanumeric characters, underscores, dots, or hyphens");
        }
        if (!op.matches("eq|gt|lt|exists|bool")) {
            throw new IllegalArgumentException("Unsupported operator: " + op + ". Supported: eq, gt, lt, exists, bool");
        }
        if (val == null && !op.equals("exists")) {
            throw new IllegalArgumentException("Operator '" + op + "' requires a value");
        }

        // jsonPath is validated by regex — safe to use in SQL expression.
        // All values use jOOQ bind parameters.
        var jsonExtract = DSL.field("data->>'" + jsonPath + "'", String.class);

        return switch (op) {
            case "eq" -> jsonExtract.eq(val);
            case "gt" -> {
                try {
                    yield jsonExtract.cast(Double.class).gt(Double.parseDouble(val));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value must be numeric for 'gt' operator: " + val);
                }
            }
            case "lt" -> {
                try {
                    yield jsonExtract.cast(Double.class).lt(Double.parseDouble(val));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value must be numeric for 'lt' operator: " + val);
                }
            }
            case "exists" -> DSL.condition("jsonb_exists(data, ?)", jsonPath);
            case "bool" -> jsonExtract.cast(Boolean.class).eq(Boolean.parseBoolean(val));
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        };
    }
}
