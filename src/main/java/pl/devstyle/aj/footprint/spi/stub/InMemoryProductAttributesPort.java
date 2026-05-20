package pl.devstyle.aj.footprint.spi.stub;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.devstyle.aj.footprint.internal.ports.ProductAttributesPort;

/**
 * Dev/test stub. Enabled by {@code app.footprint.adapters=in-memory}. In production a real
 * Product-domain-backed adapter must be provided; this class must not be on the active classpath then.
 */
@Component
@ConditionalOnProperty(name = "app.footprint.adapters", havingValue = "in-memory", matchIfMissing = false)
class InMemoryProductAttributesPort implements ProductAttributesPort {

    private final Map<String, ProductAttributes> catalog;

    InMemoryProductAttributesPort() {
        this.catalog = Map.of(
                "OFB-330", new ProductAttributes(
                        "OFB-330",
                        new BigDecimal("0.5"),
                        new BigDecimal("2800"),
                        new BigDecimal("570"),
                        new BigDecimal("15"),
                        true),
                "CAW-042", new ProductAttributes(
                        "CAW-042",
                        new BigDecimal("0.25"),
                        new BigDecimal("800"),
                        new BigDecimal("120"),
                        new BigDecimal("5"),
                        false)
        );
    }

    @Override
    public Optional<ProductAttributes> findById(String productId) {
        return Optional.ofNullable(catalog.get(productId));
    }
}
