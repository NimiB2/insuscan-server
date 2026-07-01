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

/**
 * HTTP client for OpenAI's chat completions endpoint.
 * Currently only exposes the multi-image call used by GeometryFusionServiceImpl
 * for top-and-side view depth estimation; other OpenAI usages have migrated to Gemini.
 */
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

    public String getModel() {
        return openAiModel;
    }

    public String callModelWithMultipleImages(String model, String prompt, String base64Image1, String base64Image2) {
        Map<String, Object> requestBody = buildMultiImageRequestBody(model, prompt, base64Image1, base64Image2);

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

    private Map<String, Object> buildMultiImageRequestBody(String model, String prompt,
            String base64Image1, String base64Image2) {
        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", prompt));

        if (base64Image1 != null && !base64Image1.isBlank()) {
            contentParts.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/jpeg;base64," + base64Image1)));
        }
        if (base64Image2 != null && !base64Image2.isBlank()) {
            contentParts.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/jpeg;base64," + base64Image2)));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", contentParts)));
        body.put("temperature", 0.0);
        body.put("response_format", Map.of("type", "json_object"));

        return body;
    }

    private String extractContentFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("[OPENAI] Failed to parse response: {}", e.getMessage());
            return null;
        }
    }
}