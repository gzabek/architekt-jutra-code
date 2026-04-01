package pl.devstyle.aj.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "Photo URL must start with http:// or https://") String photoUrl,
        @NotNull @Positive BigDecimal price,
        @NotBlank @Size(max = 50) String sku,
        @NotNull Long categoryId
) {
}
