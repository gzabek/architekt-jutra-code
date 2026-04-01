package pl.devstyle.aj.core.plugin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.core.error.EntityNotFoundException;

import java.util.List;
import java.util.Map;

@Service
public class PluginDescriptorService {

    private final PluginDescriptorRepository pluginDescriptorRepository;

    public PluginDescriptorService(PluginDescriptorRepository pluginDescriptorRepository) {
        this.pluginDescriptorRepository = pluginDescriptorRepository;
    }

    private static final String PLUGIN_ID_PATTERN = "^[a-zA-Z0-9_-]+$";

    @Transactional
    public PluginDescriptor uploadManifest(String pluginId, Map<String, Object> manifest) {
        if (!pluginId.matches(PLUGIN_ID_PATTERN)) {
            throw new IllegalArgumentException("pluginId must contain only alphanumeric characters, underscores, or hyphens");
        }

        var name = manifest.get("name");
        if (!(name instanceof String) || ((String) name).isBlank()) {
            throw new IllegalArgumentException("Manifest must contain a non-blank 'name' field");
        }

        var url = manifest.get("url");
        if (url instanceof String urlStr) {
            if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                throw new IllegalArgumentException("Manifest 'url' must be an HTTP(S) URL");
            }
        }

        var plugin = pluginDescriptorRepository.findById(pluginId)
                .orElseGet(() -> {
                    var newPlugin = new PluginDescriptor();
                    newPlugin.setId(pluginId);
                    newPlugin.setEnabled(true);
                    return newPlugin;
                });

        plugin.setName((String) name);
        plugin.setVersion((String) manifest.get("version"));
        plugin.setUrl((String) url);
        plugin.setDescription((String) manifest.get("description"));
        plugin.setManifest(manifest);

        return pluginDescriptorRepository.saveAndFlush(plugin);
    }

    @Transactional(readOnly = true)
    public List<PluginDescriptor> findAllEnabled() {
        return pluginDescriptorRepository.findByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public PluginDescriptor findById(String pluginId) {
        return pluginDescriptorRepository.findById(pluginId)
                .orElseThrow(() -> new EntityNotFoundException("Plugin", pluginId));
    }

    @Transactional(readOnly = true)
    public PluginDescriptor findEnabledOrThrow(String pluginId) {
        var plugin = findById(pluginId);
        if (!plugin.isEnabled()) {
            throw new EntityNotFoundException("Plugin", pluginId);
        }
        return plugin;
    }

    @Transactional
    public void delete(String pluginId) {
        var plugin = findById(pluginId);
        pluginDescriptorRepository.delete(plugin);
    }

    @Transactional
    public PluginDescriptor setEnabled(String pluginId, boolean enabled) {
        var plugin = findById(pluginId);
        plugin.setEnabled(enabled);
        return pluginDescriptorRepository.saveAndFlush(plugin);
    }
}
