package pl.devstyle.aj.core.plugin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.category.Category;
import pl.devstyle.aj.category.CategoryRepository;
import pl.devstyle.aj.product.Product;
import pl.devstyle.aj.product.ProductRepository;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class PluginDatabaseTests {

    @Autowired
    private PluginDescriptorRepository pluginDescriptorRepository;

    @Autowired
    private PluginObjectRepository pluginObjectRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void savePluginDescriptor_withJsonbManifest_roundTripsCorrectly() {
        var descriptor = new PluginDescriptor();
        descriptor.setId("test-plugin-1");
        descriptor.setName("Test Plugin");
        descriptor.setVersion("1.0.0");
        descriptor.setUrl("https://example.com/plugin");
        descriptor.setDescription("A test plugin");
        descriptor.setEnabled(true);
        descriptor.setManifest(Map.of(
                "extensionPoints", Map.of("type", "product-tab"),
                "config", Map.of("key", "value")
        ));

        pluginDescriptorRepository.saveAndFlush(descriptor);
        pluginDescriptorRepository.flush();

        var found = pluginDescriptorRepository.findById("test-plugin-1").orElseThrow();
        assertThat(found.getName()).isEqualTo("Test Plugin");
        assertThat(found.getVersion()).isEqualTo("1.0.0");
        assertThat(found.getUrl()).isEqualTo("https://example.com/plugin");
        assertThat(found.getDescription()).isEqualTo("A test plugin");
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getManifest()).containsKey("extensionPoints");
        assertThat(found.getManifest()).containsKey("config");
    }

    @Test
    void saveProduct_withPluginDataJsonb_roundTripsCorrectly() {
        var category = createAndSaveCategory("Test Category");
        var product = new Product();
        product.setName("Test Product");
        product.setDescription("A product with plugin data");
        product.setPrice(new BigDecimal("29.99"));
        product.setSku("PLG-001");
        product.setCategory(category);
        product.setPluginData(Map.of(
                "seo-plugin", Map.of("metaTitle", "Great Product", "metaDescription", "Buy this"),
                "analytics-plugin", Map.of("trackingId", "UA-12345")
        ));

        productRepository.saveAndFlush(product);

        var found = productRepository.findById(product.getId()).orElseThrow();
        assertThat(found.getPluginData()).containsKey("seo-plugin");
        assertThat(found.getPluginData()).containsKey("analytics-plugin");
    }

    @Test
    void savePluginObject_withJsonbData_roundTripsCorrectly() {
        var descriptor = createAndSavePluginDescriptor("object-plugin");

        var pluginObject = new PluginObject();
        pluginObject.setPluginId(descriptor.getId());
        pluginObject.setObjectType("seo-metadata");
        pluginObject.setObjectId("product-42");
        pluginObject.setData(Map.of(
                "title", "SEO Title",
                "keywords", "test, plugin"
        ));

        pluginObjectRepository.saveAndFlush(pluginObject);

        var found = pluginObjectRepository.findByPluginIdAndObjectTypeAndObjectId(
                "object-plugin", "seo-metadata", "product-42"
        ).orElseThrow();
        assertThat(found.getData()).containsEntry("title", "SEO Title");
        assertThat(found.getData()).containsEntry("keywords", "test, plugin");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void savePluginObject_withDuplicateCompositeKey_throwsConflict() {
        var descriptor = createAndSavePluginDescriptor("dup-plugin");

        var first = new PluginObject();
        first.setPluginId(descriptor.getId());
        first.setObjectType("config");
        first.setObjectId("item-1");
        first.setData(Map.of("key", "value1"));
        pluginObjectRepository.saveAndFlush(first);

        var duplicate = new PluginObject();
        duplicate.setPluginId(descriptor.getId());
        duplicate.setObjectType("config");
        duplicate.setObjectId("item-1");
        duplicate.setData(Map.of("key", "value2"));

        assertThatThrownBy(() -> pluginObjectRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Category createAndSaveCategory(String name) {
        var category = new Category();
        category.setName(name);
        category.setDescription("Test category");
        return categoryRepository.saveAndFlush(category);
    }

    private PluginDescriptor createAndSavePluginDescriptor(String pluginId) {
        var descriptor = new PluginDescriptor();
        descriptor.setId(pluginId);
        descriptor.setName(pluginId + " name");
        descriptor.setVersion("1.0.0");
        descriptor.setEnabled(true);
        descriptor.setManifest(Map.of("type", "test"));
        return pluginDescriptorRepository.saveAndFlush(descriptor);
    }
}
