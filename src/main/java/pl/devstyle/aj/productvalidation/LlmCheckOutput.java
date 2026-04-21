package pl.devstyle.aj.productvalidation;

record LlmCheckOutput(
        boolean valid,
        String suggestion,
        String confidence,
        String explanation
) {}
