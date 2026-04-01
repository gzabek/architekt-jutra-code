package pl.devstyle.aj.core.plugin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.core.error.EntityNotFoundException;

import java.util.List;
import java.util.Map;

@Service
public class PluginObjectService {

    private final PluginDescriptorService pluginDescriptorService;
    private final PluginObjectRepository pluginObjectRepository;
    private final DbPluginObjectQueryService dbPluginObjectQueryService;

    public PluginObjectService(PluginDescriptorService pluginDescriptorService,
                               PluginObjectRepository pluginObjectRepository,
                               DbPluginObjectQueryService dbPluginObjectQueryService) {
        this.pluginDescriptorService = pluginDescriptorService;
        this.pluginObjectRepository = pluginObjectRepository;
        this.dbPluginObjectQueryService = dbPluginObjectQueryService;
    }

    @Transactional(readOnly = true)
    public List<PluginObjectResponse> list(String pluginId, String objectType) {
        return list(pluginId, objectType, null, null, null, 1000);
    }

    @Transactional(readOnly = true)
    public List<PluginObjectResponse> list(String pluginId, String objectType, EntityType entityType, Long entityId) {
        return list(pluginId, objectType, entityType, entityId, null, 1000);
    }

    @Transactional(readOnly = true)
    public List<PluginObjectResponse> list(String pluginId, String objectType,
                                            EntityType entityType, Long entityId, String filter, int limit) {
        pluginDescriptorService.findEnabledOrThrow(pluginId);
        return dbPluginObjectQueryService.findByFilter(pluginId, objectType, entityType, entityId, filter, limit);
    }

    @Transactional(readOnly = true)
    public List<PluginObjectResponse> listByEntity(String pluginId, EntityType entityType, Long entityId) {
        return listByEntity(pluginId, entityType, entityId, null, 1000);
    }

    @Transactional(readOnly = true)
    public List<PluginObjectResponse> listByEntity(String pluginId, EntityType entityType, Long entityId, String filter) {
        return listByEntity(pluginId, entityType, entityId, filter, 1000);
    }

    @Transactional(readOnly = true)
    public List<PluginObjectResponse> listByEntity(String pluginId, EntityType entityType, Long entityId, String filter, int limit) {
        pluginDescriptorService.findEnabledOrThrow(pluginId);
        return dbPluginObjectQueryService.findByFilter(pluginId, null, entityType, entityId, filter, limit);
    }

    @Transactional(readOnly = true)
    public PluginObject get(String pluginId, String objectType, String objectId) {
        pluginDescriptorService.findEnabledOrThrow(pluginId);
        return pluginObjectRepository.findByPluginIdAndObjectTypeAndObjectId(pluginId, objectType, objectId)
                .orElseThrow(() -> new EntityNotFoundException("PluginObject", objectId));
    }

    @Transactional
    public PluginObject save(String pluginId, String objectType, String objectId, Map<String, Object> data) {
        return save(pluginId, objectType, objectId, data, null, null);
    }

    @Transactional
    public PluginObject save(String pluginId, String objectType, String objectId, Map<String, Object> data,
                             EntityType entityType, Long entityId) {
        pluginDescriptorService.findEnabledOrThrow(pluginId);
        var pluginObject = pluginObjectRepository.findByPluginIdAndObjectTypeAndObjectId(pluginId, objectType, objectId)
                .orElseGet(() -> {
                    var obj = new PluginObject();
                    obj.setPluginId(pluginId);
                    obj.setObjectType(objectType);
                    obj.setObjectId(objectId);
                    return obj;
                });
        pluginObject.setData(data);
        pluginObject.setEntityType(entityType);
        pluginObject.setEntityId(entityId);
        return pluginObjectRepository.saveAndFlush(pluginObject);
    }

    @Transactional
    public void delete(String pluginId, String objectType, String objectId) {
        pluginDescriptorService.findEnabledOrThrow(pluginId);
        var pluginObject = pluginObjectRepository.findByPluginIdAndObjectTypeAndObjectId(pluginId, objectType, objectId)
                .orElseThrow(() -> new EntityNotFoundException("PluginObject", objectId));
        pluginObjectRepository.delete(pluginObject);
    }
}
