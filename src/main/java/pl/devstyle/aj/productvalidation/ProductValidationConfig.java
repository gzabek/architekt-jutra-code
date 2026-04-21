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
        return new DefaultLlmClient(restClient, props, objectMapper);
    }
}
