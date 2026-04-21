package pl.devstyle.aj.productvalidation;

import tools.jackson.databind.ObjectMapper;
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
import pl.devstyle.aj.SecurityMockMvcConfiguration;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.WithMockEditUser;
import pl.devstyle.aj.category.Category;
import pl.devstyle.aj.category.CategoryRepository;
import pl.devstyle.aj.core.plugin.PluginDataService;
import pl.devstyle.aj.product.Product;
import pl.devstyle.aj.product.ProductRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Transactional
@WithMockEditUser
class ProductValidationIntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    PluginDataService pluginDataService;

    @TestConfiguration
    static class MockLlmConfig {
        @Bean
        @Primary
        LlmClient llmClient() {
            return (systemPrompt, userMessage) -> """
                    {
                      "categoryValidation":    {"valid":true,"suggestion":"Category looks correct.","confidence":"HIGH","explanation":"The product fits well."},
                      "descriptionValidation": {"valid":true,"suggestion":"Description is accurate.","confidence":"HIGH","explanation":"Clear and detailed."},
                      "priceValidation":       {"valid":true,"suggestion":"Price is within market range.","confidence":"HIGH","explanation":"Comparable products cost similar amounts."}
                    }
                    """;
        }
    }

    @Test
    void validate_existingProduct_returns200WithValidationResult() throws Exception {
        var product = createAndSaveProduct("Laptop Pro", "A high-performance laptop for professionals.", new BigDecimal("999.99"));

        mockMvc.perform(post("/api/products/{id}/validate", product.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.categoryValidation.valid").value(true))
                .andExpect(jsonPath("$.categoryValidation.confidence").value("HIGH"))
                .andExpect(jsonPath("$.descriptionValidation").exists())
                .andExpect(jsonPath("$.priceValidation").exists());
    }

    @Test
    void validate_existingProduct_savesPluginData() throws Exception {
        var product = createAndSaveProduct("Smartphone X", "A feature-rich smartphone.", new BigDecimal("599.00"));

        mockMvc.perform(post("/api/products/{id}/validate", product.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        var data = pluginDataService.getData("product-validator", product.getId());
        assertThat(data).containsKey("categoryValidation");
    }

    @Test
    void validate_nonExistentProduct_returns404() throws Exception {
        mockMvc.perform(post("/api/products/999999/validate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
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
