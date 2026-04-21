package pl.devstyle.aj.productvalidation;

public record ValidationResult(
        Long productId,
        CheckResult categoryValidation,
        CheckResult descriptionValidation,
        CheckResult priceValidation
) {}
