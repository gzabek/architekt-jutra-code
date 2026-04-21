package pl.devstyle.aj.productvalidation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import pl.devstyle.aj.SecurityMockMvcConfiguration;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.WithMockEditUser;
import pl.devstyle.aj.category.Category;
import pl.devstyle.aj.category.CategoryRepository;
import pl.devstyle.aj.product.Product;
import pl.devstyle.aj.product.ProductRepository;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Transactional
@WithMockEditUser
class ProductValidationLlmUnavailableTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @TestConfiguration
    static class ThrowingLlmConfig {
        @Bean
        @Primary
        LlmClient llmClient() {
            return (systemPrompt, userMessage) -> {
                throw new RestClientException("LLM service unavailable");
            };
        }
    }

    @Test
    void validate_llmUnavailable_returns200WithDegradedResult() throws Exception {
        var product = createAndSaveProduct("Tablet Z", "A versatile tablet.", new BigDecimal("299.00"));

        mockMvc.perform(post("/api/products/{id}/validate", product.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.categoryValidation.valid").value(false))
                .andExpect(jsonPath("$.categoryValidation.suggestion").value("LLM unavailable"))
                .andExpect(jsonPath("$.categoryValidation.confidence").value("LOW"))
                .andExpect(jsonPath("$.descriptionValidation.valid").value(false))
                .andExpect(jsonPath("$.priceValidation.valid").value(false));
    }

    // -------------------------------------------------------------------------

    private Product createAndSaveProduct(String name, String description, BigDecimal price) {
        var category = new Category();
        category.setName("Electronics-" + System.nanoTime());
        category.setDescription("Electronic devices");
        categoryRepository.saveAndFlush(category);

        var product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setSku("SKU-" + System.nanoTime());
        product.setCategory(category);
        return productRepository.saveAndFlush(product);
    }
}
