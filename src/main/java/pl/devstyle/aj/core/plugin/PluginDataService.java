package pl.devstyle.aj.core.plugin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.core.error.EntityNotFoundException;
import pl.devstyle.aj.product.ProductRepository;

import java.util.HashMap;
import java.util.Map;

@Service
public class PluginDataService {

    private final PluginDescriptorService pluginDescriptorService;
    private final ProductRepository productRepository;

    public PluginDataService(PluginDescriptorService pluginDescriptorService, ProductRepository productRepository) {
        this.pluginDescriptorService = pluginDescriptorService;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> getData(String pluginId, Long productId) {
        pluginDescriptorService.findEnabledOrThrow(pluginId);
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        if (product.getPluginData() == null) {
            return Map.of();
        }

        var node = product.getPluginData().get(pluginId);
        if (node == null) {
            return Map.of();
        }
        return (Map<String, Object>) node;
    }

    @Transactional
    public void setData(String pluginId, Long productId, Map<String, Object> data) {
        pluginDescriptorService.findEnabledOrThrow(pluginId);
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        var pluginData = product.getPluginData();
        if (pluginData == null) {
            pluginData = new HashMap<>();
        } else {
            pluginData = new HashMap<>(pluginData);
        }
        pluginData.put(pluginId, data);
        product.setPluginData(pluginData);
        productRepository.saveAndFlush(product);
    }

    @Transactional
    public void removeData(String pluginId, Long productId) {
        pluginDescriptorService.findEnabledOrThrow(pluginId);
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        var pluginData = product.getPluginData();
        if (pluginData != null) {
            pluginData = new HashMap<>(pluginData);
            pluginData.remove(pluginId);
            product.setPluginData(pluginData);
            productRepository.saveAndFlush(product);
        }
    }
}
