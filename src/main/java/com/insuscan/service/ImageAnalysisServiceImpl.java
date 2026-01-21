package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.FoodRecognitionResult;
import com.insuscan.util.ApiLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ImageAnalysisServiceImpl implements ImageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ImageAnalysisServiceImpl.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final VisionCacheService visionCache;
    private final ApiLogger apiLogger;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    public ImageAnalysisServiceImpl(WebClient.Builder webClientBuilder,
                                   ObjectMapper objectMapper,
                                   VisionCacheService visionCache,
                                   ApiLogger apiLogger) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .build();
        this.objectMapper = objectMapper;
        this.visionCache = visionCache;
        this.apiLogger = apiLogger;
    }

    @Override
    public FoodRecognitionResult analyzeImage(String base64Image) {
        // Log API key status
        String keyPreview = (openAiApiKey != null && openAiApiKey.length() > 5) 
            ? openAiApiKey.substring(0, 5) : "N/A";
        apiLogger.apiKeyStatus("OPENAI", isServiceAvailable(), keyPreview);

        if (!isServiceAvailable()) {
            apiLogger.openaiError("API key not configured", "ConfigurationException");
            return FoodRecognitionResult.failure("AI provider is not configured");
        }

        // Check cache first
        String imageHash = visionCache.hashImage(base64Image);
        FoodRecognitionResult cached = visionCache.getCached(imageHash);
        if (cached != null) {
            apiLogger.openaiCacheHit(imageHash);
            return cached;
        }

        // Start request
        apiLogger.openaiStart(openAiModel, base64Image.length());
        long totalStartTime = System.currentTimeMillis();

        try {
            // First pass: strict prompt
            List<FoodRecognitionResult.RecognizedFoodItem> foods = analyzeWithPrompt(base64Image, true);

            // Retry with relaxed prompt if needed
            if (foods.isEmpty()) {
                apiLogger.openaiRetry("Strict prompt returned 0 foods");
                foods = analyzeWithPrompt(base64Image, false);
            }

            long totalTime = System.currentTimeMillis() - totalStartTime;

            if (foods.isEmpty()) {
                apiLogger.openaiError("No foods detected even with relaxed prompt", "EmptyResult");
                return FoodRecognitionResult.failure("No foods detected in image. Try a clearer photo.");
            }

            // Log parsed foods
            apiLogger.openaiParsedFoods(foods);
            apiLogger.openaiSuccess(foods.size(), totalTime);

            FoodRecognitionResult result = FoodRecognitionResult.success(foods);
            visionCache.putCache(imageHash, result);

            return result;

        } catch (RuntimeException e) {
            apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
            if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                return FoodRecognitionResult.failure("OpenAI rate limit exceeded. Please wait.");
            }
            return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
        } catch (Exception e) {
            apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
            return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
        }
    }

    @Override
    public FoodRecognitionResult analyzeImageFromUrl(String imageUrl) {
        apiLogger.apiKeyStatus("OPENAI", isServiceAvailable(), 
            openAiApiKey != null && openAiApiKey.length() > 5 ? openAiApiKey.substring(0, 5) : "N/A");

        if (!isServiceAvailable()) {
            return FoodRecognitionResult.failure("AI provider is not configured");
        }

        apiLogger.openaiStart(openAiModel, 0);
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = buildOpenAiRequestWithUrl(imageUrl);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("OpenAI error: " + body)))
                    .bodyToMono(String.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;
            apiLogger.openaiResponseReceived(elapsed, response != null ? response.length() : 0);

            if (response == null || response.isBlank()) {
                return FoodRecognitionResult.failure("Provider returned empty response");
            }

            String content = extractContentFromResponse(response);
            apiLogger.openaiRawResponse(content);

            if (content == null || content.isBlank()) {
                return FoodRecognitionResult.failure("Could not extract content from response");
            }

            List<FoodRecognitionResult.RecognizedFoodItem> foods = parseFoodsFromOpenAi(content);
            apiLogger.openaiParsedFoods(foods);

            return FoodRecognitionResult.success(foods);

        } catch (Exception e) {
            apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
            return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isServiceAvailable() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    private List<FoodRecognitionResult.RecognizedFoodItem> analyzeWithPrompt(String base64Image, boolean strict) throws Exception {
        Map<String, Object> requestBody = buildOpenAiRequestWithBase64(base64Image, strict);

        log.debug("[OPENAI] Sending {} prompt request...", strict ? "STRICT" : "RELAXED");
        long startTime = System.currentTimeMillis();

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
                                        .map(body -> new RuntimeException("Rate limit exceeded: " + body));
                            }
                            return clientResponse.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("OpenAI API error: " + body));
                        })
                .bodyToMono(String.class)
                .block();

        long elapsed = System.currentTimeMillis() - startTime;
        apiLogger.openaiResponseReceived(elapsed, response != null ? response.length() : 0);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Provider returned empty response");
        }

        String content = extractContentFromResponse(response);
        apiLogger.openaiRawResponse(content);

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Could not extract content from OpenAI response");
        }

        return parseFoodsFromOpenAi(content);
    }

    private Map<String, Object> buildOpenAiRequestWithBase64(String base64Image, boolean strict) {
        String prompt = buildPrompt(strict);

        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageUrlObj = Map.of("url", "data:image/jpeg;base64," + base64Image);
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlObj);

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );

        return Map.of(
                "model", openAiModel,
                "messages", List.of(userMessage),
                "temperature", 0.2,
                "max_tokens", 600
        );
    }

    private Map<String, Object> buildOpenAiRequestWithUrl(String imageUrl) {
        String prompt = buildPrompt(true);

        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageUrlObj = Map.of("url", imageUrl);
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlObj);

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );

        return Map.of(
                "model", openAiModel,
                "messages", List.of(userMessage),
                "temperature", 0.2,
                "max_tokens", 600
        );
    }

    private String buildPrompt(boolean strict) {
        // If strict is false (fallback mode), use a simpler prompt but still safe
        if (!strict) {
            return """
                    You are a defensive food analysis AI.
                    Identify food items strictly based on visual evidence.
                    Return JSON: { "items": [{ "name": "string", "confidence": 0.5, "estimatedPortionGrams": 100 }] }
                    """;
        }

        // --- MEDICAL GRADE PROMPT ---
        return """
                You are a Clinical Food Safety AI for a diabetes management system.
                Your goal is to analyze food not just for identity, but for metabolic impact.

                PROTOCOL:
                1. EVIDENCE ONLY: Do not guess ingredients you cannot see. If a sauce is visible but unknown, flag it.
                2. STATE ANALYSIS: Distinguish between RAW, BOILED, ROASTED, and FRIED. This critically affects Glycemic Index.
                3. BASE INGREDIENT: Separate the full description (e.g. 'Mashed Potatoes with Gravy') from the search term (e.g. 'Potato').

                OUTPUT FORMAT (Strict JSON):
                {
                  "items": [
                    {
                      "visual_name": "Roasted Potato Wedges",   // Full descriptive name
                      "base_ingredient": "Potato",              // The core keyword for DB search (Singular)
                      "confidence": 0.95,
                      "estimated_grams": 150,
                      "visual_state": "ROASTED",                // Enum: RAW, BOILED, FRIED, ROASTED, PROCESSED, UNKNOWN
                      "risk_flags": ["HIGH_FAT", "POSSIBLE_SUGAR_GLAZE", "SAUCE_DETECTED"], // List potential risks
                      "requires_user_validation": false         // Set TRUE if the item is ambiguous or high-risk
                    }
                  ],
                  "warnings": []
                }

                RULES:
                - If packaging text is visible, prioritize it over visual appearance.
                - If the food is blurry or unidentifiable, return an empty list. Do not hallucinate.
                """;
    }

    private List<FoodRecognitionResult.RecognizedFoodItem> parseFoodsFromOpenAi(String rawResponse) throws Exception {
        String jsonText = extractFirstJsonObject(rawResponse);

        JsonNode root = objectMapper.readTree(jsonText);
        JsonNode items = root.get("items");

        List<FoodRecognitionResult.RecognizedFoodItem> results = new ArrayList<>();
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                // 1. Basic Fields extraction
                String visualName = item.has("visual_name") ? item.get("visual_name").asText() : 
                                   (item.has("name") ? item.get("name").asText() : "unknown");

                if (visualName.isEmpty() || "unknown".equalsIgnoreCase(visualName)) continue;

                float conf = item.has("confidence") ? (float) item.get("confidence").asDouble() : 0.0f;
                
                Float weight = null;
                if (item.has("estimated_grams")) weight = (float) item.get("estimated_grams").asDouble();
                else if (item.has("estimatedPortionGrams")) weight = (float) item.get("estimatedPortionGrams").asDouble();

                // 2. New Medical Fields extraction
                String baseIngredient = item.has("base_ingredient") ? item.get("base_ingredient").asText() : visualName;
                String state = item.has("visual_state") ? item.get("visual_state").asText() : "UNKNOWN";
                boolean needsValidation = item.has("requires_user_validation") && item.get("requires_user_validation").asBoolean();

                List<String> risks = new ArrayList<>();
                if (item.has("risk_flags") && item.get("risk_flags").isArray()) {
                    for (JsonNode risk : item.get("risk_flags")) {
                        risks.add(risk.asText());
                    }
                }

                // 3. Construct the Object
                FoodRecognitionResult.RecognizedFoodItem recognizedItem = 
                    new FoodRecognitionResult.RecognizedFoodItem(visualName, conf, weight);
                
                // Set the new fields
                recognizedItem.setBaseIngredient(baseIngredient);
                recognizedItem.setVisualState(state);
                recognizedItem.setRequiresValidation(needsValidation);
                recognizedItem.setRiskFlags(risks);

                results.add(recognizedItem);
            }
        }
        return results;
    }

    private String extractContentFromResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    return message.get("content").asText();
                }
            }
            return null;
        } catch (Exception e) {
            return extractFirstJsonObject(rawResponse);
        }
    }

    private String extractFirstJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalStateException("Could not locate JSON object in response");
        }
        return raw.substring(start, end + 1);
    }
}