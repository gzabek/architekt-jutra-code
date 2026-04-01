package pl.devstyle.aj.core.plugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record PluginResponse(
        String id,
        String name,
        String version,
        String url,
        String description,
        boolean enabled,
        List<Map<String, Object>> extensionPoints
) {

    @SuppressWarnings("unchecked")
    public static PluginResponse from(PluginDescriptor plugin) {
        List<Map<String, Object>> extensionPoints = Collections.emptyList();
        if (plugin.getManifest() != null && plugin.getManifest().containsKey("extensionPoints")) {
            var raw = plugin.getManifest().get("extensionPoints");
            if (raw instanceof List<?> list) {
                extensionPoints = (List<Map<String, Object>>) (List<?>) list;
            }
        }

        return new PluginResponse(
                plugin.getId(),
                plugin.getName(),
                plugin.getVersion(),
                plugin.getUrl(),
                plugin.getDescription(),
                plugin.isEnabled(),
                extensionPoints
        );
    }
}
