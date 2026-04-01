package pl.devstyle.aj.core.plugin;

import java.time.LocalDateTime;
import java.util.Map;

public record PluginObjectResponse(
        Long id,
        String pluginId,
        String objectType,
        String objectId,
        Map<String, Object> data,
        EntityType entityType,
        Long entityId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PluginObjectResponse from(PluginObject pluginObject) {
        return new PluginObjectResponse(
                pluginObject.getId(),
                pluginObject.getPluginId(),
                pluginObject.getObjectType(),
                pluginObject.getObjectId(),
                pluginObject.getData(),
                pluginObject.getEntityType(),
                pluginObject.getEntityId(),
                pluginObject.getCreatedAt(),
                pluginObject.getUpdatedAt()
        );
    }
}
