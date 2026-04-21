package pl.devstyle.aj.productvalidation;

@FunctionalInterface
public interface LlmClient {
    /**
     * Sends a chat-completion request to LiteLLM (or any OpenAI-compatible endpoint)
     * and returns the raw content string from the first choice.
     *
     * @throws org.springframework.web.client.RestClientException on HTTP/network errors
     */
    String complete(String systemPrompt, String userMessage);
}
