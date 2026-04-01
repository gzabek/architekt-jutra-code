package pl.devstyle.aj.core.plugin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class PluginObjectEntityBindingTests {

    @Autowired
    private PluginObjectService pluginObjectService;

    @Autowired
    private PluginDescriptorRepository pluginDescriptorRepository;

    @Autowired
    private PluginObjectRepository pluginObjectRepository;

    private PluginDescriptor createAndSavePlugin(String id) {
        var plugin = new PluginDescriptor();
        plugin.setId(id);
        plugin.setName(id + " plugin");
        plugin.setVersion("1.0.0");
        plugin.setUrl("http://localhost:3001");
        plugin.setDescription("Test plugin");
        plugin.setEnabled(true);
        plugin.setManifest(Map.of("name", id + " plugin", "version", "1.0.0"));
        return pluginDescriptorRepository.saveAndFlush(plugin);
    }

    @Test
    void save_withEntityBinding_returnsEntityTypeAndEntityId() {
        var plugin = createAndSavePlugin("reviews");
        var data = Map.<String, Object>of("text", "Great!");

        var result = pluginObjectService.save(plugin.getId(), "review", "rev-1", data, EntityType.PRODUCT, 42L);

        var response = PluginObjectResponse.from(result);
        assertThat(response.entityType()).isEqualTo(EntityType.PRODUCT);
        assertThat(response.entityId()).isEqualTo(42L);
    }

    @Test
    void save_withoutEntityParams_returnsNullEntityFields() {
        var plugin = createAndSavePlugin("reviews");
        var data = Map.<String, Object>of("text", "Great!");

        var result = pluginObjectService.save(plugin.getId(), "review", "rev-1", data, null, null);

        var response = PluginObjectResponse.from(result);
        assertThat(response.entityType()).isNull();
        assertThat(response.entityId()).isNull();
    }

    @Test
    void save_withEntityBindingThenWithout_clearsBindingToNull() {
        var plugin = createAndSavePlugin("reviews");
        var data = Map.<String, Object>of("text", "Great!");

        pluginObjectService.save(plugin.getId(), "review", "rev-1", data, EntityType.PRODUCT, 42L);
        var result = pluginObjectService.save(plugin.getId(), "review", "rev-1", data, null, null);

        var response = PluginObjectResponse.from(result);
        assertThat(response.entityType()).isNull();
        assertThat(response.entityId()).isNull();
    }

    @Test
    void list_withEntityFilter_returnsOnlyMatchingObjects() {
        var plugin = createAndSavePlugin("reviews");

        pluginObjectService.save(plugin.getId(), "review", "rev-1", Map.of("text", "A"), EntityType.PRODUCT, 1L);
        pluginObjectService.save(plugin.getId(), "review", "rev-2", Map.of("text", "B"), EntityType.PRODUCT, 2L);
        pluginObjectService.save(plugin.getId(), "review", "rev-3", Map.of("text", "C"), null, null);

        var results = pluginObjectService.list(plugin.getId(), "review", EntityType.PRODUCT, 1L);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().objectId()).isEqualTo("rev-1");
    }

    @Test
    void list_withoutEntityFilter_returnsAllObjects() {
        var plugin = createAndSavePlugin("reviews");

        pluginObjectService.save(plugin.getId(), "review", "rev-1", Map.of("text", "A"), EntityType.PRODUCT, 1L);
        pluginObjectService.save(plugin.getId(), "review", "rev-2", Map.of("text", "B"), null, null);

        var results = pluginObjectService.list(plugin.getId(), "review");

        assertThat(results).hasSize(2);
    }

    @Test
    void list_entityIsolation_objectsBoundToProductANotReturnedForProductB() {
        var plugin = createAndSavePlugin("reviews");

        pluginObjectService.save(plugin.getId(), "review", "rev-1", Map.of("text", "For A"), EntityType.PRODUCT, 100L);
        pluginObjectService.save(plugin.getId(), "review", "rev-2", Map.of("text", "For B"), EntityType.PRODUCT, 200L);

        var resultsA = pluginObjectService.list(plugin.getId(), "review", EntityType.PRODUCT, 100L);
        var resultsB = pluginObjectService.list(plugin.getId(), "review", EntityType.PRODUCT, 200L);

        assertThat(resultsA).hasSize(1);
        assertThat(resultsA.getFirst().objectId()).isEqualTo("rev-1");
        assertThat(resultsB).hasSize(1);
        assertThat(resultsB.getFirst().objectId()).isEqualTo("rev-2");
    }
}
