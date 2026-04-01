package pl.devstyle.aj.core.plugin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/plugins/{pluginId}/products/{productId}/data")
public class PluginDataController {

    private final PluginDataService pluginDataService;

    public PluginDataController(PluginDataService pluginDataService) {
        this.pluginDataService = pluginDataService;
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable String pluginId, @PathVariable Long productId) {
        return pluginDataService.getData(pluginId, productId);
    }

    @PutMapping
    public Map<String, Object> put(
            @PathVariable String pluginId,
            @PathVariable Long productId,
            @RequestBody Map<String, Object> data) {
        pluginDataService.setData(pluginId, productId, data);
        return data;
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String pluginId, @PathVariable Long productId) {
        pluginDataService.removeData(pluginId, productId);
    }
}
