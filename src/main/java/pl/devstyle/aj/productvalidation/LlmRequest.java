package pl.devstyle.aj.productvalidation;

import java.util.List;

public record LlmRequest(
        String model,
        List<Message> messages
) {
    public record Message(String role, String content) {}
}
