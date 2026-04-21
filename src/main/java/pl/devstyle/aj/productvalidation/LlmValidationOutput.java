package pl.devstyle.aj.productvalidation;

record LlmValidationOutput(
        LlmCheckOutput categoryValidation,
        LlmCheckOutput descriptionValidation,
        LlmCheckOutput priceValidation
) {}
