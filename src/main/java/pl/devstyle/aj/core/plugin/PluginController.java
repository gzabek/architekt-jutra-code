package pl.devstyle.aj.core.plugin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private final PluginDescriptorService pluginDescriptorService;

    public PluginController(PluginDescriptorService pluginDescriptorService) {
        this.pluginDescriptorService = pluginDescriptorService;
    }

    @PutMapping("/{pluginId}/manifest")
    public PluginResponse uploadManifest(
            @PathVariable String pluginId,
            @RequestBody Map<String, Object> manifest) {
        var plugin = pluginDescriptorService.uploadManifest(pluginId, manifest);
        return PluginResponse.from(plugin);
    }

    @GetMapping
    public List<PluginResponse> list() {
        return pluginDescriptorService.findAllEnabled().stream()
                .map(PluginResponse::from)
                .toList();
    }

    @GetMapping("/{pluginId}")
    public PluginResponse getById(@PathVariable String pluginId) {
        var plugin = pluginDescriptorService.findById(pluginId);
        return PluginResponse.from(plugin);
    }

    @DeleteMapping("/{pluginId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String pluginId) {
        pluginDescriptorService.delete(pluginId);
    }

    @PatchMapping("/{pluginId}/enabled")
    public PluginResponse setEnabled(
            @PathVariable String pluginId,
            @RequestBody SetEnabledRequest request) {
        var plugin = pluginDescriptorService.setEnabled(pluginId, request.enabled());
        return PluginResponse.from(plugin);
    }
}
