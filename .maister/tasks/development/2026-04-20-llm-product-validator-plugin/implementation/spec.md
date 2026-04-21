# Specification: LLM Product Validator Plugin

## Overview

**Plugin ID**: `product-validator`  
**Domain package**: `pl.devstyle.aj.productvalidation`  
**Purpose**: A backend plugin that integrates with an LLM via LiteLLM to validate products against three criteria: category correctness, description accuracy, and market price alignment. Results are persisted to plugin data on the product record.

---

## REST API Contract

### Endpoint

```
POST /api/products/{productId}/validate
```

**Path parameter**: `productId` — Long, the product's database ID.  
**Request body**: none.  
**Produces**: `application/json`

### Success Response — `200 OK`

```json
{
  "productId": 42,
  "categoryValidation": {
    "valid": true,
    "suggestion": "Category 'Electronics' is appropriate for this product.",
    "confidence": "HIGH",
    "explanation": "The product name and description strongly match the assigned category."
  },
  "descriptionValidation": {
    "valid": false,
    "suggestion": "Description is vague; consider adding technical specifications.",
    "confidence": "MEDIUM",
    "explanation": "The description lacks detail about key product features."
  },
  "priceValidation": {
    "valid": true,
    "suggestion": "Price of $29.99 is within the expected range for this category.",
    "confidence": "HIGH",
    "explanation": "Market prices for similar items range from $20 to $40."
  }
}
```

### LLM Unavailable Response — `200 OK`

When the LLM call fails, the endpoint still returns `200 OK` with degraded results (all three sub-results contain the error sentinel):

```json
{
  "productId": 42,
  "categoryValidation": {
    "valid": false,
    "suggestion": "LLM unavailable",
    "confidence": "LOW",
    "explanation": "Could not reach the LLM service."
  },
  "descriptionValidation": {
    "valid": false,
    "suggestion": "LLM unavailable",
    "confidence": "LOW",
    "explanation": "Could not reach the LLM service."
  },
  "priceValidation": {
    "valid": false,
    "suggestion": "LLM unavailable",
    "confidence": "LOW",
    "explanation": "Could not reach the LLM service."
  }
}
```

### Error Responses

| Status | Condition |
|--------|-----------|
| `404 Not Found` | `productId` does not exist — thrown by `EntityNotFoundException`, handled by `GlobalExceptionHandler` |

---

## Configuration

### `LlmProperties.java`

```java
@ConfigurationProperties(prefix = "aj.llm")
public record LlmProperties(
    @DefaultValue("http://localhost:4000") String baseUrl,
    @DefaultValue("gpt-4o-mini")          String model,
    String                                 apiKey      // optional, may be null
) {}
```

Enable via `@EnableConfigurationProperties(LlmProperties.class)` in the plugin configuration class.

### `application.yml` additions

```yaml
aj:
  llm:
    base-url: http://localhost:4000
    model: gpt-4o-mini
    # api-key: sk-...   # optional; omit if LiteLLM needs no key
```

---

## Files to Create

```
src/main/java/pl/devstyle/aj/productvalidation/
├── ProductValidationPlugin.java          # Plugin descriptor registration
├── ProductValidationConfig.java          # @Configuration: RestClient bean, @EnableConfigurationProperties
├── LlmProperties.java                    # @ConfigurationProperties record
├── ProductValidationController.java      # POST /api/products/{productId}/validate
├── ProductValidationService.java         # Orchestration: load product, call LLM, store result
├── LlmClient.java                        # RestClient wrapper for OpenAI-compatible /v1/chat/completions
├── ValidationResult.java                 # Response record (productId + 3 sub-results)
├── CheckResult.java                      # Sub-result record (valid, suggestion, confidence, explanation)
├── Confidence.java                       # Enum: HIGH, MEDIUM, LOW
├── LlmRequest.java                       # Internal record for HTTP request to LiteLLM
├── LlmResponse.java                      # Internal record for HTTP response from LiteLLM
└── PromptBuilder.java                    # Builds system + user messages for the single combined prompt

src/test/java/pl/devstyle/aj/productvalidation/
└── ProductValidationIntegrationTests.java
```

---

## Detailed Component Specifications

### `Confidence.java`

```java
package pl.devstyle.aj.productvalidation;

public enum Confidence {
    HIGH, MEDIUM, LOW
}
```

### `CheckResult.java`

```java
package pl.devstyle.aj.productvalidation;

public record CheckResult(
    boolean    valid,
    String     suggestion,
    Confidence confidence,
    String     explanation
) {
    /** Sentinel used when the LLM is unreachable. */
    static CheckResult unavailable() {
        return new CheckResult(false, "LLM unavailable", Confidence.LOW,
                               "Could not reach the LLM service.");
    }
}
```

### `ValidationResult.java`

```java
package pl.devstyle.aj.productvalidation;

public record ValidationResult(
    Long        productId,
    CheckResult categoryValidation,
    CheckResult descriptionValidation,
    CheckResult priceValidation
) {}
```

### `LlmRequest.java`

Internal record representing an OpenAI-compatible chat-completions request body.

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

### `LlmResponse.java`

Internal record representing the relevant portion of the OpenAI chat-completions response.

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

### `LlmClient.java`

Calls LiteLLM at `POST {baseUrl}/v1/chat/completions`.

**Constructor parameters**:
- `RestClient restClient` (pre-configured with base URL)
- `LlmProperties props`
- `ObjectMapper objectMapper`

**Method**:

```java
/** Returns the raw JSON string from the LLM, or throws on HTTP/network error. */
String complete(String systemPrompt, String userMessage)
```

**Implementation notes**:
- Build `LlmRequest` with `model` from `LlmProperties`.
- `messages`: `[{role:"system", content:systemPrompt}, {role:"user", content:userMessage}]`
- If `apiKey` is non-null, set `Authorization: Bearer {apiKey}` header.
- Use `restClient.post().uri("/v1/chat/completions")`.
- Deserialize `LlmResponse` from response body; return `choices.get(0).message().content()`.
- Let any `RestClientException` propagate — the service layer catches it.

### `PromptBuilder.java`

Stateless helper (can be a `@Component`) that returns the system prompt and the user message for a combined single-request validation.

**System prompt** (exact text):

```
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
```

**User message** built from product fields:

```
Product details:
- Name: {product.name()}
- Category: {category.name()} — {category.description() or "no description"}
- Description: {product.description() or "not provided"}
- Price: {product.price()} {currency}
- SKU: {product.sku()}

Please validate the three aspects as instructed.
```

Currency is hard-coded as `USD` (acceptable for this iteration; can be made configurable later).

### `ProductValidationService.java`

```java
@Service
@Transactional
public class ProductValidationService {

    static final String PLUGIN_ID = "product-validator";

    // constructor-injected
    private final ProductRepository     productRepository;
    private final LlmClient             llmClient;
    private final PromptBuilder         promptBuilder;
    private final PluginDataService     pluginDataService;
    private final ObjectMapper          objectMapper;

    public ValidationResult validate(Long productId) { ... }
}
```

**`validate` algorithm**:

1. Load product: `productRepository.findById(productId).orElseThrow(() -> new EntityNotFoundException("Product", productId))`.
2. Build prompts via `promptBuilder.systemPrompt()` and `promptBuilder.userMessage(product)`.
3. Call `llmClient.complete(systemPrompt, userMessage)`.
   - On success: parse the returned JSON string with `objectMapper` into an intermediate record `LlmValidationOutput` (see below), then map to `ValidationResult`.
   - On any exception (`RestClientException`, `JsonProcessingException`, etc.): build a `ValidationResult` where all three sub-results are `CheckResult.unavailable()`.
4. Convert `ValidationResult` to `Map<String, Object>` using `objectMapper.convertValue(result, new TypeReference<>(){})` and call `pluginDataService.setData(PLUGIN_ID, productId, resultMap)`.
5. Return `ValidationResult`.

**`LlmValidationOutput`** — internal deserialization target (package-private record):

```java
record LlmValidationOutput(
    LlmCheckOutput categoryValidation,
    LlmCheckOutput descriptionValidation,
    LlmCheckOutput priceValidation
) {}

record LlmCheckOutput(
    boolean valid,
    String  suggestion,
    String  confidence,   // deserialized as String, converted to Confidence enum
    String  explanation
) {}
```

Mapping to `CheckResult`: `Confidence.valueOf(output.confidence().toUpperCase())` — wrap in try/catch and default to `Confidence.LOW` on `IllegalArgumentException`.

### `ProductValidationController.java`

```java
@RestController
@RequestMapping("/api/products")
public class ProductValidationController {

    private final ProductValidationService service;

    @PostMapping("/{productId}/validate")
    public ResponseEntity<ValidationResult> validate(@PathVariable Long productId) {
        return ResponseEntity.ok(service.validate(productId));
    }
}
```

### `ProductValidationConfig.java`

```java
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class ProductValidationConfig {

    @Bean
    public RestClient llmRestClient(LlmProperties props) {
        return RestClient.builder()
            .baseUrl(props.baseUrl())
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
```

The `LlmClient` bean is a `@Component`; it receives `llmRestClient` by name via `@Qualifier("llmRestClient")` or, preferably, is constructed directly inside a `@Bean` method in `ProductValidationConfig` to avoid ambiguity with any other `RestClient` beans in the context.

Preferred approach — explicit bean in config:

```java
@Bean
public LlmClient llmClient(LlmProperties props, ObjectMapper objectMapper) {
    var restClient = RestClient.builder()
        .baseUrl(props.baseUrl())
        .defaultHeader("Content-Type", "application/json")
        .build();
    return new LlmClient(restClient, props, objectMapper);
}
```

### `ProductValidationPlugin.java`

Manually registers the plugin descriptor so the plugin framework is aware of this plugin.

```java
@Component
public class ProductValidationPlugin implements ApplicationRunner {

    private final PluginRegistry registry; // or however plugins are registered — adapt to existing PluginObject/PluginDescriptor API

    @Override
    public void run(ApplicationArguments args) {
        registry.register(new PluginDescriptor(
            ProductValidationService.PLUGIN_ID,
            "Product Validator",
            "Validates product category, description, and price using an LLM."
        ));
    }
}
```

> **Note**: Adapt `PluginRegistry` / `PluginDescriptor` / `PluginObject` to whatever the actual plugin framework API looks like in `core/plugin/`. If registration is done differently (e.g., simply by declaring a bean of a specific type), adjust accordingly. No Liquibase seed is required.

---

## PluginData Storage Format

`PluginDataService.setData("product-validator", productId, data)` is called with the `ValidationResult` serialised to a `Map<String, Object>`. The resulting JSONB stored on the product looks like:

```json
{
  "productId": 42,
  "categoryValidation": {
    "valid": true,
    "suggestion": "...",
    "confidence": "HIGH",
    "explanation": "..."
  },
  "descriptionValidation": {
    "valid": false,
    "suggestion": "...",
    "confidence": "MEDIUM",
    "explanation": "..."
  },
  "priceValidation": {
    "valid": true,
    "suggestion": "...",
    "confidence": "HIGH",
    "explanation": "..."
  }
}
```

The key used by `PluginDataService` is `"product-validator"`.

---

## Error Handling

| Scenario | Behaviour |
|----------|-----------|
| `productId` not found in DB | `EntityNotFoundException("Product", productId)` → 404 via `GlobalExceptionHandler` |
| LiteLLM HTTP error / timeout | Caught in `ProductValidationService.validate`; returns all-unavailable `ValidationResult`; plugin data is still written with the degraded result |
| LLM returns malformed JSON | `JsonProcessingException` caught same as above |
| `confidence` value not in enum | `IllegalArgumentException` caught; field defaults to `Confidence.LOW` |
| Other unexpected runtime exception | Propagates to `GlobalExceptionHandler` → 500 |

---

## Integration Test Strategy

### `ProductValidationIntegrationTests.java`

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ProductValidationIntegrationTests {
    ...
}
```

**Test infrastructure**:
- Real PostgreSQL via Testcontainers (existing `TestcontainersConfiguration`).
- `MockMvc` for HTTP layer.
- The `RestClient` used by `LlmClient` must be intercepted. Use `MockRestServiceServer` (from `spring-test`) which integrates with `RestClient` through a `MockClientHttpRequestFactory`. Register the mock server against the `RestClient` builder in a `@TestConfiguration` that replaces the `llmRestClient` bean.

**Preferred mocking approach** — `@TestConfiguration` overriding the `LlmClient` bean:

```java
@TestConfiguration
static class MockLlmConfig {
    @Bean
    @Primary
    LlmClient llmClient() {
        return (systemPrompt, userMessage) -> """
            {
              "categoryValidation":   {"valid":true,"suggestion":"ok","confidence":"HIGH","explanation":"fine"},
              "descriptionValidation":{"valid":true,"suggestion":"ok","confidence":"HIGH","explanation":"fine"},
              "priceValidation":      {"valid":true,"suggestion":"ok","confidence":"HIGH","explanation":"fine"}
            }
            """;
    }
}
```

This avoids network calls entirely and keeps tests fast.

**Test cases to implement**:

1. **`validate_existingProduct_returns200WithValidationResult`**
   - Create and persist a `Category` and a `Product` with all fields.
   - POST to `/api/products/{id}/validate`.
   - Assert `status().isOk()`.
   - Assert `jsonPath("$.categoryValidation.valid").value(true)`.
   - Assert `jsonPath("$.categoryValidation.confidence").value("HIGH")`.
   - Assert all three sub-results are present.

2. **`validate_existingProduct_savesPluginData`**
   - After the POST, call `pluginDataService.getData("product-validator", productId)`.
   - Assert the returned map contains `categoryValidation`.

3. **`validate_nonExistentProduct_returns404`**
   - POST to `/api/products/999999/validate`.
   - Assert `status().isNotFound()`.

4. **`validate_llmUnavailable_returns200WithDegradedResult`**
   - Override `LlmClient` bean to throw `RestClientException`.
   - POST to `/api/products/{id}/validate`.
   - Assert `status().isOk()`.
   - Assert `jsonPath("$.categoryValidation.valid").value(false)`.
   - Assert `jsonPath("$.categoryValidation.suggestion").value("LLM unavailable")`.
   - Assert `jsonPath("$.categoryValidation.confidence").value("LOW")`.

**Helper method** (same pattern as codebase convention):

```java
private Product createAndSaveProduct(String name, String description, BigDecimal price) {
    var category = new Category();
    category.setName("Electronics");
    category.setDescription("Electronic devices");
    categoryRepository.saveAndFlush(category);

    var product = new Product();
    product.setName(name);
    product.setDescription(description);
    product.setPrice(price);
    product.setSku("SKU-001");
    product.setCategory(category);
    return productRepository.saveAndFlush(product);
}
```

---

## Implementation Order

Suggested order to minimise compilation errors:

1. `Confidence.java` (enum, no deps)
2. `CheckResult.java`
3. `ValidationResult.java`
4. `LlmRequest.java`, `LlmResponse.java` (HTTP transport records)
5. `LlmProperties.java` (config record)
6. `PromptBuilder.java` (@Component)
7. `LlmClient.java` (no Spring annotations — plain class constructed by config)
8. `ProductValidationConfig.java` (@Configuration — wires LlmClient)
9. `ProductValidationService.java` (@Service)
10. `ProductValidationController.java` (@RestController)
11. `ProductValidationPlugin.java` (@Component — adapt to plugin API)
12. `ProductValidationIntegrationTests.java`

---

## Constraints and Assumptions

- No new Maven dependencies. `spring-boot-starter-web` already provides `RestClient`; `spring-boot-starter-test` provides Testcontainers and MockMvc; Jackson is available as `tools.jackson.databind.ObjectMapper`.
- The LLM is called with a **single combined request** for all three checks. This avoids three round-trips and reduces latency.
- Currency is hard-coded as `USD` in this iteration.
- The plugin does not expose a `GET` endpoint to read stored validation data; callers can retrieve it via `PluginDataService` programmatically or through any generic plugin-data API if one exists.
- Thread safety: `ProductValidationService` is stateless; `LlmClient` is stateless. Both are safe as singletons.
- The `@Transactional` on `ProductValidationService.validate` ensures that both the product read and the plugin-data write happen in the same transaction; if `PluginDataService.setData` fails, the transaction rolls back and no partial state is left.
