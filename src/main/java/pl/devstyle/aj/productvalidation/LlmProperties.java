package pl.devstyle.aj.productvalidation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "aj.llm")
public record LlmProperties(
        @DefaultValue("http://localhost:4000") String baseUrl,
        @DefaultValue("claude-haiku-4.5") String model,
        String apiKey
) {}
