package pl.devstyle.aj.productvalidation;

public record CheckResult(
        boolean valid,
        String suggestion,
        Confidence confidence,
        String explanation
) {
    static CheckResult unavailable() {
        return new CheckResult(false, "LLM unavailable", Confidence.LOW,
                "Could not reach the LLM service.");
    }
}
