package com.insuscan.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiApiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiApiClient.class);
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ApiLogger apiLogger;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    public OpenAiApiClient(WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            ApiLogger apiLogger) {
        this.webClient = webClientBuilder
                .baseUrl(OPENAI_BASE_URL)
                .build();
        this.objectMapper = objectMapper;
        this.apiLogger = apiLogger;
    }

    public boolean isServiceAvailable() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    public String getKeyPreview() {
        return (openAiApiKey != null && openAiApiKey.length() > 5)
                ? openAiApiKey.substring(0, 5)
                : "N/A";
    }

    public String getModel() {
        return openAiModel;
    }

    public String callChatModel(String prompt, String base64Image) {
        return callChatModel(openAiModel, prompt, base64Image, null, 0.0);
    }

    public String callChatModel(String model, String prompt, String base64Image, String systemPrompt,
            double temperature) {
        Map<String, Object> requestBody = buildRequestBody(model, prompt, base64Image, systemPrompt, temperature);

        apiLogger.openaiStart(model, prompt.length());
        long startTime = System.currentTimeMillis();

        try {
            String response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("OpenAI API error: " + body)))
                    .bodyToMono(String.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;
            apiLogger.openaiResponseReceived(elapsed, response != null ? response.length() : 0);

            return extractContentFromResponse(response);

        } catch (RuntimeException e) {
            apiLogger.openaiError("OpenAI call", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                throw new RuntimeException("OpenAI rate limit exceeded. Please wait.");
            }
            throw e;
        }
    }

    public String callModelWithMultipleImages(String model, String prompt, String base64Image1, String base64Image2) {
        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", prompt));

        if (base64Image1 != null && !base64Image1.isBlank()) {
            contentParts.add(
                    Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image1)));
        }
        if (base64Image2 != null && !base64Image2.isBlank()) {
            contentParts.add(
                    Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image2)));
        }

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", contentParts);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(message));
        requestBody.put("temperature", 0.0);
        requestBody.put("response_format", Map.of("type", "json_object"));

        apiLogger.openaiStart(model, prompt.length());
        long startTime = System.currentTimeMillis();

        try {
            String response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("OpenAI API error: " + body)))
                    .bodyToMono(String.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;
            apiLogger.openaiResponseReceived(elapsed, response != null ? response.length() : 0);

            return extractContentFromResponse(response);

        } catch (RuntimeException e) {
            apiLogger.openaiError("OpenAI multi-image call", e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> buildRequestBody(String model, String prompt, String base64Image, String systemPrompt,
            double temperature) {
        List<Map<String, Object>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", prompt));

        if (base64Image != null && !base64Image.isBlank()) {
            contentParts.add(
                    Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image)));
        }

        messages.add(Map.of("role", "user", "content", contentParts));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("response_format", Map.of("type", "json_object"));

        return body;
    }

    private String extractContentFromResponse(String response) {
        if (response == null || response.isBlank())
            return null;
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("[OPENAI] Failed to parse response: {}", e.getMessage());
            return null;
        }
    }
}
