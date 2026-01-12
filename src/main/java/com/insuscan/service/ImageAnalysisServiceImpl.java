package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.FoodRecognitionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ImageAnalysisServiceImpl implements ImageAnalysisService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    public ImageAnalysisServiceImpl(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public FoodRecognitionResult analyzeImage(String base64Image) {
        if (!isServiceAvailable()) {
            return FoodRecognitionResult.failure("AI provider is not configured");
        }

        try {
            Map<String, Object> requestBody = buildOpenAiRequestWithBase64(base64Image);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                if (clientResponse.statusCode().value() == 429) {
                                    return clientResponse.bodyToMono(String.class)
                                            .map(body -> new RuntimeException("Rate limit exceeded. Please wait a moment and try again. " + 
                                                    (body != null ? body : "")));
                                }
                                return clientResponse.bodyToMono(String.class)
                                        .map(body -> new RuntimeException("OpenAI API error (" + 
                                                clientResponse.statusCode() + "): " + 
                                                (body != null ? body : "")));
                            })
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                return FoodRecognitionResult.failure("Provider returned empty response");
            }

            // Extract the content from OpenAI's chat/completions response
            String content = extractContentFromResponse(response);
            if (content == null || content.isBlank()) {
                return FoodRecognitionResult.failure("Could not extract content from OpenAI response");
            }

            List<FoodRecognitionResult.RecognizedFoodItem> foods = parseFoodsFromOpenAi(content);
            return FoodRecognitionResult.success(foods);

        } catch (RuntimeException e) {
            // Handle rate limiting and API errors
            if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                return FoodRecognitionResult.failure("OpenAI rate limit exceeded. Please wait a few minutes and try again.");
            }
            return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
        } catch (Exception e) {
            return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
        }
    }

    @Override
    public FoodRecognitionResult analyzeImageFromUrl(String imageUrl) {
        if (!isServiceAvailable()) {
            return FoodRecognitionResult.failure("AI provider is not configured");
        }

        try {
            Map<String, Object> requestBody = buildOpenAiRequestWithUrl(imageUrl);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                if (clientResponse.statusCode().value() == 429) {
                                    return clientResponse.bodyToMono(String.class)
                                            .map(body -> new RuntimeException("Rate limit exceeded. Please wait a moment and try again. " + 
                                                    (body != null ? body : "")));
                                }
                                return clientResponse.bodyToMono(String.class)
                                        .map(body -> new RuntimeException("OpenAI API error (" + 
                                                clientResponse.statusCode() + "): " + 
                                                (body != null ? body : "")));
                            })
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                return FoodRecognitionResult.failure("Provider returned empty response");
            }

            // Extract the content from OpenAI's chat/completions response
            String content = extractContentFromResponse(response);
            if (content == null || content.isBlank()) {
                return FoodRecognitionResult.failure("Could not extract content from OpenAI response");
            }

            List<FoodRecognitionResult.RecognizedFoodItem> foods = parseFoodsFromOpenAi(content);
            return FoodRecognitionResult.success(foods);

        } catch (RuntimeException e) {
            // Handle rate limiting and API errors
            if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                return FoodRecognitionResult.failure("OpenAI rate limit exceeded. Please wait a few minutes and try again.");
            }
            return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
        } catch (Exception e) {
            return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isServiceAvailable() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    private Map<String, Object> buildOpenAiRequestWithBase64(String base64Image) {
        String prompt = buildPrompt();

        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageUrlObj = Map.of("url", "data:image/jpeg;base64," + base64Image);
        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", imageUrlObj
        );

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );

        return Map.of(
                "model", openAiModel,
                "messages", List.of(userMessage)
        );
    }

    private Map<String, Object> buildOpenAiRequestWithUrl(String imageUrl) {
        String prompt = buildPrompt();

        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageUrlObj = Map.of("url", imageUrl);
        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", imageUrlObj
        );

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );

        return Map.of(
                "model", openAiModel,
                "messages", List.of(userMessage)
        );
    }

    private String buildPrompt() {
        return """
                You are a food recognition assistant for a medical-grade diabetes management system.

                TASK:
                Analyze the provided image of a food plate and identify visible food items.

                OUTPUT:
                Return STRICT JSON only, with the following exact structure:
                {
                  "items": [
                    {
                      "name": "string",
                      "confidence": 0.0
                    }
                  ],
                  "warnings": [
                    "string"
                  ]
                }

                STRICT RULES:
                - Include ONLY food items with confidence >= 0.6 in the "items" list
                - Do NOT include items with confidence below 0.6 in the "items" list
                - If a food component is uncertain or ambiguous, do NOT add it to "items"
                - Uncertain or ambiguous detections must be mentioned ONLY in the "warnings" list
                - "confidence" must be a number between 0.0 and 1.0
                - Do NOT guess or hallucinate food items
                - If no food items meet the confidence threshold, return an empty "items" list
                - Do NOT add any keys outside the defined JSON schema
                - Do NOT include explanations, comments, or additional text outside the JSON
                """;
    }

    private List<FoodRecognitionResult.RecognizedFoodItem> parseFoodsFromOpenAi(String rawResponse) throws Exception {
        String jsonText = extractFirstJsonObject(rawResponse);

        JsonNode root = objectMapper.readTree(jsonText);
        JsonNode items = root.get("items");

        List<FoodRecognitionResult.RecognizedFoodItem> results = new ArrayList<>();
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                String name = item.hasNonNull("name") ? item.get("name").asText() : "unknown";
                float conf = item.hasNonNull("confidence") ? (float) item.get("confidence").asDouble() : 0.0f;
                results.add(new FoodRecognitionResult.RecognizedFoodItem(name, conf));
            }
        }
        return results;
    }

    private String extractContentFromResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            // Fallback: try to extract JSON from raw response
            return extractFirstJsonObject(rawResponse);
        }
    }

    private String extractFirstJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalStateException("Could not locate JSON object in provider response");
        }
        return raw.substring(start, end + 1);
    }
}