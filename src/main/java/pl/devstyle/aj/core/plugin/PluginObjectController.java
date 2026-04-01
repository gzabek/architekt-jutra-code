package pl.devstyle.aj.core.plugin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plugins/{pluginId}/objects")
public class PluginObjectController {

    private final PluginObjectService pluginObjectService;

    public PluginObjectController(PluginObjectService pluginObjectService) {
        this.pluginObjectService = pluginObjectService;
    }

    private static final int DEFAULT_LIMIT = 1000;

    @GetMapping
    public List<PluginObjectResponse> listByEntity(
            @PathVariable String pluginId,
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1000") int limit) {
        if (entityType == null || entityId == null) {
            throw new IllegalArgumentException("Both entityType and entityId are required for cross-type listing");
        }
        return pluginObjectService.listByEntity(pluginId, entityType, entityId, filter, Math.min(limit, DEFAULT_LIMIT));
    }

    @GetMapping("/{objectType}")
    public List<PluginObjectResponse> list(
            @PathVariable String pluginId,
            @PathVariable String objectType,
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1000") int limit) {
        if ((entityType == null) != (entityId == null)) {
            throw new IllegalArgumentException("Both entityType and entityId must be provided together or both absent");
        }
        return pluginObjectService.list(pluginId, objectType, entityType, entityId, filter, Math.min(limit, DEFAULT_LIMIT));
    }

    @GetMapping("/{objectType}/{objectId}")
    public PluginObjectResponse get(
            @PathVariable String pluginId,
            @PathVariable String objectType,
            @PathVariable String objectId) {
        return PluginObjectResponse.from(pluginObjectService.get(pluginId, objectType, objectId));
    }

    @PutMapping("/{objectType}/{objectId}")
    public PluginObjectResponse save(
            @PathVariable String pluginId,
            @PathVariable String objectType,
            @PathVariable String objectId,
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) Long entityId) {
        if ((entityType == null) != (entityId == null)) {
            throw new IllegalArgumentException("Both entityType and entityId must be provided together or both absent");
        }
        return PluginObjectResponse.from(pluginObjectService.save(pluginId, objectType, objectId, data, entityType, entityId));
    }

    @DeleteMapping("/{objectType}/{objectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String pluginId,
            @PathVariable String objectType,
            @PathVariable String objectId) {
        pluginObjectService.delete(pluginId, objectType, objectId);
    }
}
