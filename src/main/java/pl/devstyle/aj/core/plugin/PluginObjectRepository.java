package pl.devstyle.aj.core.plugin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PluginObjectRepository extends JpaRepository<PluginObject, Long> {

    List<PluginObject> findByPluginIdAndObjectType(String pluginId, String objectType);

    Optional<PluginObject> findByPluginIdAndObjectTypeAndObjectId(String pluginId, String objectType, String objectId);

    void deleteByPluginIdAndObjectTypeAndObjectId(String pluginId, String objectType, String objectId);
}
