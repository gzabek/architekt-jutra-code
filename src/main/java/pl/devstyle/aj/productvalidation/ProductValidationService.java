package pl.devstyle.aj.productvalidation;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import pl.devstyle.aj.core.error.EntityNotFoundException;
import pl.devstyle.aj.core.plugin.PluginDataService;
import pl.devstyle.aj.product.ProductRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@Transactional
@Slf4j
public class ProductValidationService {

    static final String PLUGIN_ID = "product-validator";

    static final Logger LOGGER = LoggerFactory.getLogger(ProductValidationService.class);

    private final ProductRepository productRepository;
    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final PluginDataService pluginDataService;
    private final ObjectMapper objectMapper;

    public ProductValidationService(
            ProductRepository productRepository,
            LlmClient llmClient,
            PromptBuilder promptBuilder,
            PluginDataService pluginDataService,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.pluginDataService = pluginDataService;
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(Long productId) {
        var product = productRepository.findByIdWithCategory(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        var systemPrompt = promptBuilder.systemPrompt();
        var userMessage = promptBuilder.userMessage(product);

        ValidationResult result;
        try {
            var json = stripMarkdownFences(llmClient.complete(systemPrompt, userMessage));
            var output = objectMapper.readValue(json, LlmValidationOutput.class);
            result = new ValidationResult(
                    productId,
                    toCheckResult(output.categoryValidation()),
                    toCheckResult(output.descriptionValidation()),
                    toCheckResult(output.priceValidation())
            );
        } catch (RestClientException | JacksonException e) {
            LOGGER.error(e.getMessage(), e);
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

    private static String stripMarkdownFences(String text) {
        var stripped = text.strip();
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            int lastFence = stripped.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                return stripped.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return stripped;
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
