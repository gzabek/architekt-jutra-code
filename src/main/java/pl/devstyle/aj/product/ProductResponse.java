package pl.devstyle.aj.product;

import pl.devstyle.aj.category.CategoryResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String photoUrl,
        BigDecimal price,
        String sku,
        CategoryResponse category,
        Map<String, Object> pluginData,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPhotoUrl(),
                product.getPrice(),
                product.getSku(),
                CategoryResponse.from(product.getCategory()),
                product.getPluginData(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
