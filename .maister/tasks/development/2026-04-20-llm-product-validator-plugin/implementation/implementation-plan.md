# Implementation Plan: LLM Product Validator Plugin

## Standards Compliance

- Follow Java 25 + Spring Boot 4.0.5 conventions
- Use `var` for local variables where type is obvious
- Use `tools.jackson.databind.ObjectMapper` (Spring Boot 4.x repackaged Jackson)
- No `@Data` / `@EqualsAndHashCode` on JPA entities
- Constructor injection only — no `@Autowired` fields
- `@Transactional` from `org.springframework.transaction.annotation.Transactional`
- Test classes: package-private, `@Transactional`, no `public` on test methods
- Test naming: `action_condition_expectedResult`

---

## Group 1: Value Objects and DTOs

### 1.1 — Write skeleton integration test (TDD Red)

Create `src/test/java/pl/devstyle/aj/productvalidation/ProductValidationIntegrationTests.java` with:
- Class-level annotations: `@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})`, `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)`, `@AutoConfigureMockMvc`, `@Transactional`
- `@Autowired MockMvc mockMvc`
- One placeholder test `validate_placeholder_failsUntilImplemented` that calls `POST /api/products/1/validate` and asserts `status().isNotFound()` (product 1 won't exist yet — confirms the endpoint will exist)
- Leave `@TestConfiguration static class MockLlmConfig` stub commented out for now
- Run `mvnw.cmd test -Dtest=ProductValidationIntegrationTests` — expect compilation failure (classes don't exist yet; this confirms the test is wired)

### 1.2 — Create `Confidence.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/Confidence.java`

```java
package pl.devstyle.aj.productvalidation;

public enum Confidence {
    HIGH, MEDIUM, LOW
}
```

### 1.3 — Create `CheckResult.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/CheckResult.java`

```java
package pl.devstyle.aj.productvalidation;

public record CheckResult(
    boolean    valid,
    String     suggestion,
    Confidence confidence,
    String     explanation
) {
    static CheckResult unavailable() {
        return new CheckResult(false, "LLM unavailable", Confidence.LOW,
                               "Could not reach the LLM service.");
    }
}
```

### 1.4 — Create `ValidationResult.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/ValidationResult.java`

```java
package pl.devstyle.aj.productvalidation;

public record ValidationResult(
    Long        productId,
    CheckResult categoryValidation,
    CheckResult descriptionValidation,
    CheckResult priceValidation
) {}
```

### 1.5 — Create `LlmRequest.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/LlmRequest.java`

```java
package pl.devstyle.aj.productvalidation;

import java.util.List;

public record LlmRequest(
    String        model,
    List<Message> messages
) {
    public record Message(String role, String content) {}
}
```

### 1.6 — Create `LlmResponse.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/LlmResponse.java`

```java
package pl.devstyle.aj.productvalidation;

import java.util.List;

public record LlmResponse(
    List<Choice> choices
) {
    public record Choice(Message message) {}
    public record Message(String content) {}
}
```

### 1.7 — Create `LlmValidationOutput.java` and `LlmCheckOutput.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/LlmValidationOutput.java`

```java
package pl.devstyle.aj.productvalidation;

record LlmValidationOutput(
    LlmCheckOutput categoryValidation,
    LlmCheckOutput descriptionValidation,
    LlmCheckOutput priceValidation
) {}
```

File: `src/main/java/pl/devstyle/aj/productvalidation/LlmCheckOutput.java`

```java
package pl.devstyle.aj.productvalidation;

record LlmCheckOutput(
    boolean valid,
    String  suggestion,
    String  confidence,
    String  explanation
) {}
```

### 1.8 — Verify compilation of Group 1

Run `mvnw.cmd package -DskipTests -Dskip.jooq.generation=true` and confirm the new package compiles without errors.

---

## Group 2: Configuration

### 2.1 — Write test for configuration loading

Add a test to `ProductValidationIntegrationTests` (or a dedicated `ProductValidationConfigTests` if preferred):
- `@SpringBootTest` loads context; assert `LlmProperties` bean is available and `baseUrl` defaults to `"http://localhost:4000"` when no `aj.llm` section is in test properties.
- This test will fail until `LlmProperties` and `ProductValidationConfig` exist.

### 2.2 — Create `LlmProperties.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/LlmProperties.java`

```java
package pl.devstyle.aj.productvalidation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "aj.llm")
public record LlmProperties(
    @DefaultValue("http://localhost:4000") String baseUrl,
    @DefaultValue("gpt-4o-mini")          String model,
    String                                 apiKey
) {}
```

### 2.3 — Create `ProductValidationConfig.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/ProductValidationConfig.java`

```java
package pl.devstyle.aj.productvalidation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class ProductValidationConfig {

    @Bean
    public LlmClient llmClient(LlmProperties props, ObjectMapper objectMapper) {
        var restClient = RestClient.builder()
            .baseUrl(props.baseUrl())
            .defaultHeader("Content-Type", "application/json")
            .build();
        return new LlmClient(restClient, props, objectMapper);
    }
}
```

### 2.4 — Update `application.properties` with LLM configuration

Append to `src/main/resources/application.properties`:

```properties
# LLM configuration (LiteLLM proxy)
aj.llm.base-url=http://localhost:4000
aj.llm.model=gpt-4o-mini
# aj.llm.api-key=sk-...
```

### 2.5 — Verify configuration compilation

Run `mvnw.cmd package -DskipTests -Dskip.jooq.generation=true` and confirm the configuration classes compile cleanly.

---

## Group 3: LLM Integration

### 3.1 — Write LlmClient unit test (compile-time check)

Add a placeholder test in `ProductValidationIntegrationTests` (or a separate `LlmClientTest`):
- Create a `LlmClient` instance using a mock `RestClient` / `LlmProperties` / `ObjectMapper`.
- Verify it compiles and the `complete` method signature is `String complete(String systemPrompt, String userMessage)`.
- This test can be a simple compile-only verification (assertion deferred to Group 6 integration tests).

### 3.2 — Create `PromptBuilder.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/PromptBuilder.java`

```java
package pl.devstyle.aj.productvalidation;

import org.springframework.stereotype.Component;
import pl.devstyle.aj.product.Product;

@Component
public class PromptBuilder {

    public String systemPrompt() {
        return """
            You are a product data quality assistant. Given a product's details, evaluate three aspects and return ONLY a JSON object — no markdown, no explanation outside the JSON.

            The JSON must have exactly this structure:
            {
              "categoryValidation": {
                "valid": <boolean>,
                "suggestion": "<string>",
                "confidence": "<HIGH|MEDIUM|LOW>",
                "explanation": "<string>"
              },
              "descriptionValidation": {
                "valid": <boolean>,
                "suggestion": "<string>",
                "confidence": "<HIGH|MEDIUM|LOW>",
                "explanation": "<string>"
              },
              "priceValidation": {
                "valid": <boolean>,
                "suggestion": "<string>",
                "confidence": "<HIGH|MEDIUM|LOW>",
                "explanation": "<string>"
              }
            }

            Evaluation criteria:
            - categoryValidation.valid = true if the category name (and description if provided) is a good fit for the product name and description.
            - descriptionValidation.valid = true if the description is accurate, sufficiently detailed, and consistent with the product name and category.
            - priceValidation.valid = true if the price seems reasonable for this type of product compared to typical market prices.

            confidence must reflect how certain you are: HIGH (very confident), MEDIUM (somewhat confident), LOW (guessing or insufficient information).
            suggestion should be an actionable recommendation or a confirmation that things look correct.
            """;
    }

    public String userMessage(Product product) {
        var category = product.getCategory();
        var categoryDesc = (category.getDescription() != null && !category.getDescription().isBlank())
            ? category.getDescription()
            : "no description";
        var description = (product.getDescription() != null && !product.getDescription().isBlank())
            ? product.getDescription()
            : "not provided";

        return """
            Product details:
            - Name: %s
            - Category: %s — %s
            - Description: %s
            - Price: %s USD
            - SKU: %s

            Please validate the three aspects as instructed.
            """.formatted(
                product.getName(),
                category.getName(),
                categoryDesc,
                description,
                product.getPrice(),
                product.getSku()
            );
    }
}
```

### 3.3 — Create `LlmClient.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/LlmClient.java`

```java
package pl.devstyle.aj.productvalidation;

import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class LlmClient {

    private final RestClient    restClient;
    private final LlmProperties props;
    private final ObjectMapper  objectMapper;

    public LlmClient(RestClient restClient, LlmProperties props, ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.props        = props;
        this.objectMapper = objectMapper;
    }

    public String complete(String systemPrompt, String userMessage) {
        var messages = List.of(
            new LlmRequest.Message("system", systemPrompt),
            new LlmRequest.Message("user",   userMessage)
        );
        var request = new LlmRequest(props.model(), messages);

        var spec = restClient.post()
            .uri("/v1/chat/completions")
            .body(request);

        if (props.apiKey() != null) {
            spec = spec.header("Authorization", "Bearer " + props.apiKey());
        }

        var response = spec
            .retrieve()
            .body(LlmResponse.class);

        return response.choices().get(0).message().content();
    }
}
```

> Note: `RestClient.RequestBodySpec` and `RequestHeadersSpec` share the `header()` method — assign the result of each chained call to `var spec` to avoid type-variable complexity. Adjust the chain to be fully fluent if the compiler is happy with it; see note below.

**Implementation note on the header chain**: Because `body()` returns `RequestBodySpec` and `header()` is on `RequestHeadersSpec`, the cleanest approach is to build the request fully fluent:

```java
var responseBody = restClient.post()
    .uri("/v1/chat/completions")
    .headers(h -> {
        if (props.apiKey() != null) {
            h.setBearerAuth(props.apiKey());
        }
    })
    .body(request)
    .retrieve()
    .body(LlmResponse.class);
```

Use this fluent form in the actual implementation.

### 3.4 — Verify Group 3 compilation

Run `mvnw.cmd package -DskipTests -Dskip.jooq.generation=true` and confirm `PromptBuilder` and `LlmClient` compile cleanly.

---

## Group 4: Service Layer

### 4.1 — Write service-layer test stubs

In `ProductValidationIntegrationTests`, add stub bodies (no assertions yet) for:
- `validate_existingProduct_returns200WithValidationResult`
- `validate_existingProduct_savesPluginData`
- `validate_nonExistentProduct_returns404`
- `validate_llmUnavailable_returns200WithDegradedResult`

These will remain `@Disabled` or empty until Group 5 adds the controller. Their purpose here is to confirm the test file compiles.

### 4.2 — Register the plugin descriptor in the database

`PluginDataService.setData` calls `pluginDescriptorService.findEnabledOrThrow(pluginId)`, which requires a row in the `plugins` table. The plugin must be registered before any `validate` call can persist data.

Create a Liquibase migration `src/main/resources/db/changelog/2026/009-insert-product-validator-plugin.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 009-insert-product-validator-plugin
      author: aj
      context: dev
      changes:
        - insert:
            tableName: plugins
            columns:
              - column:
                  name: id
                  value: product-validator
              - column:
                  name: name
                  value: Product Validator
              - column:
                  name: version
                  value: "1.0.0"
              - column:
                  name: description
                  value: "Validates product category, description, and price using an LLM."
              - column:
                  name: enabled
                  valueBoolean: true
              - column:
                  name: manifest
                  value: '{"name":"Product Validator","version":"1.0.0"}'
                  type: jsonb
      rollback:
        - delete:
            tableName: plugins
            where: id='product-validator'
```

Register this file in `src/main/resources/db/changelog/db.changelog-master.yaml` by adding an `include` entry for `2026/009-insert-product-validator-plugin.yaml`.

> **Context note**: The migration uses `context: dev` so it runs in both local dev and the Testcontainers-backed tests (which default to the `dev` context per `application.properties`).

### 4.3 — Create `ProductValidationService.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/ProductValidationService.java`

```java
package pl.devstyle.aj.productvalidation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import pl.devstyle.aj.core.error.EntityNotFoundException;
import pl.devstyle.aj.core.plugin.PluginDataService;
import pl.devstyle.aj.product.ProductRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@Transactional
public class ProductValidationService {

    static final String PLUGIN_ID = "product-validator";

    private final ProductRepository productRepository;
    private final LlmClient         llmClient;
    private final PromptBuilder     promptBuilder;
    private final PluginDataService pluginDataService;
    private final ObjectMapper      objectMapper;

    public ProductValidationService(
            ProductRepository productRepository,
            LlmClient llmClient,
            PromptBuilder promptBuilder,
            PluginDataService pluginDataService,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.llmClient         = llmClient;
        this.promptBuilder     = promptBuilder;
        this.pluginDataService = pluginDataService;
        this.objectMapper      = objectMapper;
    }

    public ValidationResult validate(Long productId) {
        var product = productRepository.findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        var systemPrompt = promptBuilder.systemPrompt();
        var userMessage  = promptBuilder.userMessage(product);

        ValidationResult result;
        try {
            var json   = llmClient.complete(systemPrompt, userMessage);
            var output = objectMapper.readValue(json, LlmValidationOutput.class);
            result = new ValidationResult(
                productId,
                toCheckResult(output.categoryValidation()),
                toCheckResult(output.descriptionValidation()),
                toCheckResult(output.priceValidation())
            );
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            result = new ValidationResult(
                productId,
                CheckResult.unavailable(),
                CheckResult.unavailable(),
                CheckResult.unavailable()
            );
        }

        var resultMap = objectMapper.convertValue(result, new TypeReference<Map<String, Object>>() {});
        pluginDataService.setData(PLUGIN_ID, productId, resultMap);

        return result;
    }

    private CheckResult toCheckResult(LlmCheckOutput output) {
        Confidence confidence;
        try {
            confidence = Confidence.valueOf(output.confidence().toUpperCase());
        } catch (IllegalArgumentException e) {
            confidence = Confidence.LOW;
        }
        return new CheckResult(output.valid(), output.suggestion(), confidence, output.explanation());
    }
}
```

### 4.4 — Verify Group 4 compilation

Run `mvnw.cmd package -DskipTests -Dskip.jooq.generation=true` and confirm the service compiles cleanly.

---

## Group 5: Controller

### 5.1 — Write controller test stubs (compile verification)

Confirm the integration test file still compiles after adding the controller import (`ProductValidationController` referenced indirectly by the Spring context). No assertions yet — full test wiring comes in Group 6.

### 5.2 — Create `ProductValidationController.java`

File: `src/main/java/pl/devstyle/aj/productvalidation/ProductValidationController.java`

```java
package pl.devstyle.aj.productvalidation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductValidationController {

    private final ProductValidationService service;

    public ProductValidationController(ProductValidationService service) {
        this.service = service;
    }

    @PostMapping("/{productId}/validate")
    public ResponseEntity<ValidationResult> validate(@PathVariable Long productId) {
        return ResponseEntity.ok(service.validate(productId));
    }
}
```

### 5.3 — Verify full application compilation and existing tests pass

Run `mvnw.cmd test -Dtest="CategoryIntegrationTests,ProductIntegrationTests,PluginDataAndObjectsIntegrationTests"` to confirm no regressions were introduced by the new package.

---

## Group 6: Integration Tests

### 6.1 — Write all four integration tests (TDD Red → Green)

Replace the skeleton in `src/test/java/pl/devstyle/aj/productvalidation/ProductValidationIntegrationTests.java` with the full implementation below.

**Full file content**:

```java
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
import org.springframework.web.client.RestClientException;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
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
                  "categoryValidation":    {"valid":true,"suggestion":"ok","confidence":"HIGH","explanation":"fine"},
                  "descriptionValidation": {"valid":true,"suggestion":"ok","confidence":"HIGH","explanation":"fine"},
                  "priceValidation":       {"valid":true,"suggestion":"ok","confidence":"HIGH","explanation":"fine"}
                }
                """;
        }
    }

    @Test
    void validate_existingProduct_returns200WithValidationResult() throws Exception {
        var product = createAndSaveProduct("Laptop", "Description of laptop", new BigDecimal("999.99"));

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
        var product = createAndSaveProduct("Phone", "A smartphone", new BigDecimal("599.00"));

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

    @Test
    void validate_llmUnavailable_returns200WithDegradedResult() throws Exception {
        var product = createAndSaveProduct("Tablet", "A tablet", new BigDecimal("299.00"));

        // Override LlmClient to throw for this specific test using a local @TestConfiguration
        // is not easily done per-test; instead, test the degraded path by noting the
        // MockLlmConfig above covers the happy path.
        // For the degraded path, re-run with a throwing bean in a separate nested test class:
        mockMvc.perform(post("/api/products/{id}/validate", product.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryValidation.valid").value(true));
        // Full degraded-path assertions are covered by ProductValidationLlmUnavailableTests below.
    }

    // -------------------------------------------------------------------------
    private Product createAndSaveProduct(String name, String description, BigDecimal price) {
        var category = new Category();
        category.setName("Electronics");
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
```

> **Note on test 4 (LLM unavailable)**: Since `@TestConfiguration` applies per-class in Spring, the degraded-path test needs its own Spring context with the throwing `LlmClient`. Create a second test class `ProductValidationLlmUnavailableTests` in step 6.2.

### 6.2 — Create `ProductValidationLlmUnavailableTests.java`

File: `src/test/java/pl/devstyle/aj/productvalidation/ProductValidationLlmUnavailableTests.java`

```java
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
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
                throw new RestClientException("Connection refused");
            };
        }
    }

    @Test
    void validate_llmUnavailable_returns200WithDegradedResult() throws Exception {
        var product = createAndSaveProduct("Tablet", "A tablet", new BigDecimal("299.00"));

        mockMvc.perform(post("/api/products/{id}/validate", product.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryValidation.valid").value(false))
                .andExpect(jsonPath("$.categoryValidation.suggestion").value("LLM unavailable"))
                .andExpect(jsonPath("$.categoryValidation.confidence").value("LOW"))
                .andExpect(jsonPath("$.descriptionValidation.valid").value(false))
                .andExpect(jsonPath("$.priceValidation.valid").value(false));
    }

    private Product createAndSaveProduct(String name, String description, BigDecimal price) {
        var category = new Category();
        category.setName("Electronics");
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
```

### 6.3 — Make `LlmClient` a functional interface

To support the lambda mock in tests, add `@FunctionalInterface` to `LlmClient` — but since `LlmClient` is a concrete class, convert it to an interface with one abstract method, and rename the concrete implementation class.

**Option A (preferred — minimal disruption)**: Keep `LlmClient` as a class but extract an interface `LlmClient` and rename the class to `DefaultLlmClient`.

- Create interface `src/main/java/pl/devstyle/aj/productvalidation/LlmClient.java`:
  ```java
  package pl.devstyle.aj.productvalidation;

  @FunctionalInterface
  public interface LlmClient {
      String complete(String systemPrompt, String userMessage);
  }
  ```

- Rename the class created in step 3.3 to `DefaultLlmClient.java` — it `implements LlmClient`.
- Update `ProductValidationConfig` to return `LlmClient` from `@Bean` (it already constructs `new DefaultLlmClient(…)`).
- Update `ProductValidationService` field type to `LlmClient`.

### 6.4 — Run all integration tests and confirm green

Run `mvnw.cmd test -Dtest="ProductValidationIntegrationTests,ProductValidationLlmUnavailableTests"`.

All four test cases must pass:
- `validate_existingProduct_returns200WithValidationResult` ✓
- `validate_existingProduct_savesPluginData` ✓
- `validate_nonExistentProduct_returns404` ✓
- `validate_llmUnavailable_returns200WithDegradedResult` ✓

### 6.5 — Run full test suite to check for regressions

Run `mvnw.cmd test` and confirm zero failures across the entire project.

---

## Summary

| Group | Files Created | Key Outcome |
|-------|--------------|-------------|
| 1 | `Confidence`, `CheckResult`, `ValidationResult`, `LlmRequest`, `LlmResponse`, `LlmValidationOutput`, `LlmCheckOutput` | All DTOs and value objects compile |
| 2 | `LlmProperties`, `ProductValidationConfig`, `application.properties` update | Configuration loads with defaults |
| 3 | `PromptBuilder`, `LlmClient` (interface), `DefaultLlmClient` | LLM call chain complete |
| 4 | Liquibase migration 009, `ProductValidationService` | Orchestration and DB write complete |
| 5 | `ProductValidationController` | `POST /api/products/{id}/validate` live |
| 6 | `ProductValidationIntegrationTests`, `ProductValidationLlmUnavailableTests` | All 4 test cases green, full suite passes |
