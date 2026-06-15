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
public class GeminiApiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiClient.class);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ApiLogger apiLogger;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model.vision:gemini-3.1-pro-preview}")
    private String visionModel;

    @Value("${gemini.model.flash:gemini-3-flash-preview}")
    private String flashModel;

    public GeminiApiClient(WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            ApiLogger apiLogger) {
        this.webClient = webClientBuilder
                .baseUrl(GEMINI_BASE_URL)
                .build();
        this.objectMapper = objectMapper;
        this.apiLogger = apiLogger;
    }

    public boolean isServiceAvailable() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public String getKeyPreview() {
        return (geminiApiKey != null && geminiApiKey.length() > 5)
                ? geminiApiKey.substring(0, 5)
                : "N/A";
    }

    public String getVisionModel() {
        return visionModel;
    }

    public String getFlashModel() {
        return flashModel;
    }

    public String callVisionModel(String prompt, String base64Image) {
        return callModel(visionModel, prompt, base64Image, null, 0.0);
    }

    public String callFlashModel(String prompt) {
        return callModel(flashModel, prompt, null, null, 0.0);
    }

    public String callFlashModelWithImage(String prompt, String base64Image) {
        return callModel(flashModel, prompt, base64Image, null, 0.0);
    }

    public String callFlashModelWithSystem(String systemPrompt, String userPrompt) {
        return callModel(flashModel, userPrompt, null, systemPrompt, 0.2);
    }

    /**
     * Call vision model with two images (top-down + side view).
     * Used for improved depth estimation when ARCore is unavailable.
     */
    public String callVisionModelWithMultipleImages(String prompt, String base64Image1, String base64Image2) {
        return callModelWithMultipleImages(visionModel, prompt, base64Image1, base64Image2);
    }

    public String callModelWithMultipleImages(String model, String prompt, String base64Image1, String base64Image2) {
        Map<String, Object> requestBody = buildMultiImageRequestBody(prompt, base64Image1, base64Image2);

        String uri = String.format("/models/%s:generateContent?key=%s", model, geminiApiKey);

        apiLogger.openaiStart(model, prompt.length());
        long startTime = System.currentTimeMillis();

        try {
            String response = webClient.post()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("Gemini API error: " + body)))
                    .bodyToMono(String.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;
            apiLogger.openaiResponseReceived(elapsed, response != null ? response.length() : 0);

            return extractTextFromResponse(response);

        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                throw new RuntimeException("Gemini rate limit exceeded. Please wait.");
            }
            throw e;
        }
    }

    public String callModel(String model, String prompt, String base64Image,
            String systemPrompt, double temperature) {
        Map<String, Object> requestBody = buildRequestBody(prompt, base64Image, systemPrompt, temperature);

        String uri = String.format("/models/%s:generateContent?key=%s", model, geminiApiKey);

        apiLogger.openaiStart(model, prompt.length());
        long startTime = System.currentTimeMillis();

        try {
            String response = webClient.post()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("Gemini API error: " + body)))
                    .bodyToMono(String.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;
            apiLogger.openaiResponseReceived(elapsed, response != null ? response.length() : 0);

            return extractTextFromResponse(response);

        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                throw new RuntimeException("Gemini rate limit exceeded. Please wait.");
            }
            throw e;
        }
    }

    private Map<String, Object> buildMultiImageRequestBody(String prompt, String base64Image1, String base64Image2) {
        List<Map<String, Object>> parts = new ArrayList<>();

        parts.add(Map.of("text", prompt));

        // First image (top-down view)
        if (base64Image1 != null && !base64Image1.isBlank()) {
            parts.add(Map.of(
                    "inlineData", Map.of(
                            "mimeType", "image/jpeg",
                            "data", base64Image1)));
        }

        // Second image (side view)
        if (base64Image2 != null && !base64Image2.isBlank()) {
            parts.add(Map.of(
                    "inlineData", Map.of(
                            "mimeType", "image/jpeg",
                            "data", base64Image2)));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of("parts", parts));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", contents);
        body.put("generationConfig", buildGenerationConfig(0.0));

        return body;
    }

    private Map<String, Object> buildRequestBody(String prompt, String base64Image,
            String systemPrompt, double temperature) {
        List<Map<String, Object>> parts = new ArrayList<>();

        parts.add(Map.of("text", prompt));

        if (base64Image != null && !base64Image.isBlank()) {
            parts.add(Map.of(
                    "inlineData", Map.of(
                            "mimeType", "image/jpeg",
                            "data", base64Image)));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of("role", "user", "parts", parts));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", contents);
        body.put("generationConfig", buildGenerationConfig(temperature));

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))));
        }

        return body;
    }

    private String extractTextFromResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("[GEMINI] Empty response received");
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            JsonNode error = root.get("error");
            if (error != null) {
                String errorMsg = error.has("message") ? error.get("message").asText() : "Unknown error";
                log.error("[GEMINI] API error: {}", errorMsg);
                throw new RuntimeException("Gemini API error: " + errorMsg);
            }

            JsonNode candidates = root.get("candidates");
            if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
                JsonNode finishReason = candidates.get(0).get("finishReason");
                JsonNode usage = root.get("usageMetadata");
                log.info("[GEMINI] finishReason={} usage={}",
                        finishReason != null ? finishReason.asText() : "none",
                        usage != null ? usage.toString() : "none");
                JsonNode content = candidates.get(0).get("content");
                if (content != null) {
                    JsonNode responseParts = content.get("parts");
                    if (responseParts != null && responseParts.isArray() && !responseParts.isEmpty()) {
                        StringBuilder fullText = new StringBuilder();
                        for (JsonNode part : responseParts) {
                            if (part.has("text")) {
                                fullText.append(part.get("text").asText());
                            }
                        }
                        String text = fullText.toString();
                        log.debug("[GEMINI] Extracted response length: {} chars", text.length());
                        return text;
                    }
                }
            }
            log.warn("[GEMINI] Could not extract text from response structure");
            return null;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GEMINI] Failed to parse response: {}", e.getMessage());
            return null;
        }
    }
    
    private Map<String, Object> buildGenerationConfig(double temperature) {
        Map<String, Object> config = new HashMap<>();
        config.put("temperature", temperature);
        config.put("maxOutputTokens", 8192);
        config.put("responseMimeType", "application/json");
        config.put("thinkingConfig", Map.of("thinkingBudget", 128));
        return config;
    }
}