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
class ProductValidationMarkdownFenceTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @TestConfiguration
    static class MarkdownFencedLlmConfig {
        @Bean
        @Primary
        LlmClient llmClient() {
            return (systemPrompt, userMessage) -> """
                    ```json
                    {
                      "categoryValidation":    {"valid":true,"suggestion":"Category looks correct.","confidence":"HIGH","explanation":"The product fits well."},
                      "descriptionValidation": {"valid":true,"suggestion":"Description is accurate.","confidence":"HIGH","explanation":"Clear and detailed."},
                      "priceValidation":       {"valid":true,"suggestion":"Price is within market range.","confidence":"HIGH","explanation":"Comparable products cost similar amounts."}
                    }
                    ```""";
        }
    }

    @Test
    void validate_llmReturnsMarkdownFencedJson_returns200WithParsedResult() throws Exception {
        var product = createAndSaveProduct("Smart TV 55\"", "A premium OLED smart TV.", new BigDecimal("1299.99"));

        mockMvc.perform(post("/api/products/{id}/validate", product.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.categoryValidation.valid").value(true))
                .andExpect(jsonPath("$.categoryValidation.confidence").value("HIGH"))
                .andExpect(jsonPath("$.descriptionValidation.valid").value(true))
                .andExpect(jsonPath("$.priceValidation.valid").value(true));
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
