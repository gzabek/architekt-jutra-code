package pl.devstyle.aj.productvalidation;

import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class DefaultLlmClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties props;
    private final ObjectMapper objectMapper;

    public DefaultLlmClient(RestClient restClient, LlmProperties props, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        var messages = List.of(
                new LlmRequest.Message("system", systemPrompt),
                new LlmRequest.Message("user", userMessage)
        );
        var request = new LlmRequest(props.model(), messages);

        var response = restClient.post()
                .uri("/v1/chat/completions")
                .headers(h -> {
                    if (props.apiKey() != null) {
                        h.setBearerAuth(props.apiKey());
                    }
                })
                .body(request)
                .retrieve()
                .body(LlmResponse.class);

        return response.choices().getFirst().message().content();
    }
}
