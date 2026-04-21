package pl.devstyle.aj.productvalidation;

import org.springframework.stereotype.Component;
import pl.devstyle.aj.product.Product;

@Component
public class PromptBuilder {

    public String systemPrompt() {
        return """
                You are a product data quality assistant. Given a product's details, evaluate three aspects and return ONLY a JSON object — no markdown, no explanation outside the JSON.
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
                Given a product's details, evaluate three aspects and return ONLY a JSON object — no markdown, no explanation outside the JSON.

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
