package pl.devstyle.aj.productvalidation;

import java.util.List;

public record LlmResponse(
        List<Choice> choices
) {
    public record Choice(Message message) {}
    public record Message(String content) {}
}
