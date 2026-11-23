package com.superpersonopc.springaimodule.chat;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/** Adapts the application's synchronous text chat to the OpenAI Responses API. */
@Component
public class OpenAiResponsesChatModel implements ChatModel {

    private final RestClient restClient;
    private final String responsesPath;
    private final ChatOptions defaultOptions;

    public OpenAiResponsesChatModel(RestClient.Builder builder,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.responses-path}") String responsesPath,
            @Value("${spring.ai.openai.chat.options.model}") String model,
            @Value("${spring.ai.openai.chat.options.temperature}") Double temperature) {
        Assert.hasText(apiKey, "OPENAI_API_KEY must not be blank");
        Assert.hasText(baseUrl, "OPENAI_BASE_URL must not be blank");
        Assert.isTrue(responsesPath.startsWith("/") && !responsesPath.startsWith("//"),
                "OPENAI_RESPONSES_PATH must be an absolute path, such as /v1/responses");
        this.restClient = builder.clone()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeaders(headers -> headers.setBearerAuth(apiKey))
                .build();
        this.responsesPath = responsesPath;
        this.defaultOptions = ChatOptions.builder().model(model).temperature(temperature).build();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return this.defaultOptions.copy();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<InputMessage> input = prompt.getInstructions().stream().map(message -> {
            Assert.isTrue(message.getMessageType() != MessageType.TOOL,
                    "The Responses adapter supports text messages only, not tool results");
            return new InputMessage(message.getMessageType().getValue(), message.getText());
        }).toList();
        ChatOptions options = prompt.getOptions();
        String model = options != null && options.getModel() != null
                ? options.getModel() : this.defaultOptions.getModel();
        Double temperature = options != null && options.getTemperature() != null
                ? options.getTemperature() : this.defaultOptions.getTemperature();

        ResponseBody response = this.restClient.post()
                .uri(this.responsesPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RequestBody(model, input, temperature))
                .retrieve()
                .body(ResponseBody.class);

        if (response == null || !"completed".equals(response.status())) {
            throw new IllegalStateException("OpenAI Responses API did not complete the response (status: "
                    + (response == null ? "empty body" : response.status()) + ")");
        }

        List<String> text = new ArrayList<>();
        if (response.output() != null) {
            for (OutputItem item : response.output()) {
                if (!"message".equals(item.type()) || !"assistant".equals(item.role())
                        || item.content() == null) {
                    continue;
                }
                for (OutputContent content : item.content()) {
                    if ("output_text".equals(content.type()) && content.text() != null) {
                        text.add(content.text());
                    }
                    else if ("refusal".equals(content.type()) && content.refusal() != null) {
                        text.add(content.refusal());
                    }
                }
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("OpenAI Responses API returned no assistant text");
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(String.join("", text)))));
    }

    private record RequestBody(String model, List<InputMessage> input, Double temperature) {
    }

    private record InputMessage(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseBody(String status, List<OutputItem> output) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OutputItem(String type, String role, List<OutputContent> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OutputContent(String type, String text, String refusal) {
    }
}
