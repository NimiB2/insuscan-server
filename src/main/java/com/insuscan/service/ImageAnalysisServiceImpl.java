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
    public FoodRecognitionResult analyzeImage(String base64Image, String referenceObjectType) {
        // Log API key status
        String keyPreview = (openAiApiKey != null && openAiApiKey.length() > 5)
                ? openAiApiKey.substring(0, 5)
                : "N/A";
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
            // First pass: strict prompt with reference type
            FoodRecognitionResult parsedResult = analyzeWithPrompt(base64Image, true,
                    referenceObjectType);

            // Retry with relaxed prompt if needed
            if (parsedResult.getDetectedFoods().isEmpty()) {
                apiLogger.openaiRetry("Strict prompt returned 0 foods");
                parsedResult = analyzeWithPrompt(base64Image, false, referenceObjectType);
            }

            long totalTime = System.currentTimeMillis() - totalStartTime;

            if (parsedResult.getDetectedFoods().isEmpty()) {
                apiLogger.openaiError("No foods detected even with relaxed prompt", "EmptyResult");
                return FoodRecognitionResult.failure("No foods detected in image. Try a clearer photo.");
            }

            // Log parsed foods
            apiLogger.openaiParsedFoods(parsedResult.getDetectedFoods());
            apiLogger.openaiSuccess(parsedResult.getDetectedFoods().size(), totalTime);

            parsedResult.setSuccess(true);
            visionCache.putCache(imageHash, parsedResult);

            log.info("[v2] Container: type={}, fill={}%",
                    parsedResult.getDetectedContainerType(),
                    parsedResult.getContainerFillPercent());

            return parsedResult;

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
    public FoodRecognitionResult analyzeImageFromUrl(String imageUrl, String referenceObjectType) {
        apiLogger.apiKeyStatus("OPENAI", isServiceAvailable(),
                openAiApiKey != null && openAiApiKey.length() > 5 ? openAiApiKey.substring(0, 5) : "N/A");

        if (!isServiceAvailable()) {
            return FoodRecognitionResult.failure("AI provider is not configured");
        }

        apiLogger.openaiStart(openAiModel, 0);
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = buildOpenAiRequestWithUrl(imageUrl, referenceObjectType);

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

            FoodRecognitionResult parsedResult = parseFoodsFromOpenAi(content);
            apiLogger.openaiParsedFoods(parsedResult.getDetectedFoods());
            parsedResult.setSuccess(true);

            return parsedResult;

        } catch (Exception e) {
            apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
            return FoodRecognitionResult.failure("Image analysis failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isServiceAvailable() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    private FoodRecognitionResult analyzeWithPrompt(String base64Image, boolean strict,
            String referenceObjectType)
            throws Exception {
        Map<String, Object> requestBody = buildOpenAiRequestWithBase64(base64Image, strict, referenceObjectType);

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

    private Map<String, Object> buildOpenAiRequestWithBase64(String base64Image, boolean strict,
            String referenceObjectType) {
        String prompt = buildPrompt(strict, referenceObjectType);

        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageUrlObj = Map.of("url", "data:image/jpeg;base64," + base64Image);
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlObj);

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent));

        return Map.of(
                "model", openAiModel,
                "messages", List.of(userMessage),
                "temperature", 0.0,
                "max_tokens", 1200);
    }

    private Map<String, Object> buildOpenAiRequestWithUrl(String imageUrl, String referenceObjectType) {
        String prompt = buildPrompt(true, referenceObjectType);

        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageUrlObj = Map.of("url", imageUrl);
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlObj);

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent));

        return Map.of(
                "model", openAiModel,
                "messages", List.of(userMessage),
                "temperature", 0.0,
                "max_tokens", 1200);
    }

    private String buildPrompt(boolean strict, String referenceObjectType) {
        // Determine instructions based on selected reference type
        boolean isCard = referenceObjectType != null && (referenceObjectType.toLowerCase().contains("card")
                || referenceObjectType.toLowerCase().contains("id"));

        // If strict is false (fallback mode), use a simpler prompt but still safe
        if (!strict) {
            boolean isSyringe = referenceObjectType != null && referenceObjectType.toLowerCase().contains("syringe");
            String fallbackRef = isCard
                    ? "Look for a CREDIT CARD / ID CARD dimensions 8.5cm x 5.5cm."
                    : (isSyringe ? "Look for an INSULIN SYRINGE dimensions 16cm x 1.25cm."
                            : "Look for an Insulin Pen or standard cutlery.");

            return """
                    You are a defensive food analysis AI.
                    Identify food items strictly based on visual evidence.
                    CRITICAL: If the image does not contain any food, return { "items": [] }.
                    IMPORTANT: List each food SEPARATELY. A plate with rice and chicken = 2 items, not 1. Do not include a combined "Main Dish" item.
                    Reference Object Strategy: %s
                    Return JSON: { "items": [{ "name": "string", "confidence": 0.5, "estimatedPortionGrams": 100 }] }
                    """
                    .formatted(fallbackRef);
        }

        // --- MEDICAL GRADE PROMPT ---
        String referenceInstruction;
        if (isCard) {
            referenceInstruction = "- REFERENCE OBJECT: Look for a CREDIT CARD / ID CARD near the food. Known dimensions: 8.5cm × 5.5cm. Use this known size to accurately estimate food volume.";
        } else if (referenceObjectType != null && referenceObjectType.toLowerCase().contains("syringe")) {
            referenceInstruction = "- REFERENCE OBJECT: Look for an INSULIN SYRINGE near the food. Known dimensions: 16cm length × 1.25cm width. Use this known size to accurately estimate food volume.";
        } else {
            referenceInstruction = "- REFERENCE OBJECT: Look for any known-size object near the food (pen, card, cutlery). IF FOUND, use it as a scale reference.";
        }

        return """
                You are a Clinical Food Safety AI for a diabetes management system.
                Your goal is to analyze food NOT just for identity, but for metabolic impact.

                MEAL ANALYSIS PROTOCOL:
                - Analyze the image for any food items, containers, or meals.
                - NOTE: It is expected to see medical tools (syringes) or cards near the food for scale. Identify the FOOD, and ignore these reference objects in your final list.
                - IF NO FOOD IS PRESENT (e.g. empty desk, car, wall): Return { "items": [] }.

                PROTOCOL:
                1. EVIDENCE ONLY: Do not guess ingredients you cannot see. If a sauce is visible but unknown, flag it.
                2. STATE ANALYSIS: Distinguish between RAW, BOILED, ROASTED, and FRIED. This critically affects Glycemic Index.
                3. BASE INGREDIENT: Separate the full description (e.g. 'Mashed Potatoes with Gravy') from the search term (e.g. 'Potato').
                4. ITEM SEPARATION: Break the plate into INDIVIDUAL food items. "Rice with chicken and salad" must become THREE items: "Rice", "Chicken", "Salad".

                CONTAINER & FILL ANALYSIS (CRITICAL FOR ACCURATE PORTIONS):
                - Identify the container type: FLAT_PLATE, REGULAR_BOWL, or DEEP_BOWL
                - Estimate what percentage (0-100) of the container's USABLE VOLUME is filled.
                - BOWL MATH RULE: In curved bowls, VOLUME decreases faster than height as you go down.
                  * If food is halfway up the wall (50%% height), volume is only ~30%%.
                  * If food is 75%% up the wall, volume is ~55%%.
                - CALIBRATION GUIDE:
                  * 15 = thin layer at the bottom
                  * 30 = halfway up the height of a curved bowl
                  * 50 = significantly filled, but clearly 2-3cm below the rim
                  * 70 = near the rim (approx 1cm gap)
                  * 85 = level with the rim
                  * 95+ = heaping / overflowing
                - IMPORTANT: Be conservative. If unsure, 40-50%% is a safe default for a normal serving.
                - Imagine the container empty, then imagine how much space the food occupies.

                PORTION ESTIMATION (CRITICAL):
                %s
                - FALLBACK (NO REFERENCE): If no reference object is visible, perform a "best-effort" estimation assuming a standard dinner plate size (approx 26cm diameter).
                - DO NOT FAIL simply because a reference object is missing. Always provide an estimated weight (grams) based on visual volume.

                CRITICAL NEGATIVE CONSTRAINTS:
                - Do NOT create a "Main Dish" or "Summary" item that combines others.
                - Do NOT list "Chicken and Rice" if you have already listed "Chicken" and "Rice" separately.
                - Each food pixel in the image should belong to EXACTLY ONE item in your list. Do not double count.

                OUTPUT FORMAT (Strict JSON):
                {
                  "container_type": "REGULAR_BOWL",
                  "container_fill_percent": 50,
                  "items": [
                    {
                      "visual_name": "Roasted Potato Wedges",
                      "base_ingredient": "Potato",
                      "confidence": 0.95,
                      "estimated_grams": 150,
                      "visual_state": "ROASTED",
                      "risk_flags": ["HIGH_FAT", "POSSIBLE_SUGAR_GLAZE", "SAUCE_DETECTED"],
                      "requires_user_validation": false
                    }
                  ],
                  "warnings": []
                }

                RULES:
                - If packaging text is visible, prioritize it over visual appearance.
                - If the food is blurry or unidentifiable, make a best-effort estimate of the major food group (e.g., 'Grains', 'Vegetables') based on context. Do not hallucinate brand names, but identify the base ingredient if possible.
                """
                .formatted(referenceInstruction);
    }

    private FoodRecognitionResult parseFoodsFromOpenAi(String rawResponse) throws Exception {
        String jsonText = extractFirstJsonObject(rawResponse);

        JsonNode root = objectMapper.readTree(jsonText);
        JsonNode items = root.get("items");

        // v2: Extract container-level fields
        FoodRecognitionResult result = new FoodRecognitionResult();
        if (root.has("container_fill_percent")) {
            result.setContainerFillPercent((float) root.get("container_fill_percent").asDouble());
        }
        if (root.has("container_type")) {
            result.setDetectedContainerType(root.get("container_type").asText());
        }

        List<FoodRecognitionResult.RecognizedFoodItem> results = new ArrayList<>();
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                // 1. Basic Fields extraction
                String visualName = item.has("visual_name") ? item.get("visual_name").asText()
                        : (item.has("name") ? item.get("name").asText() : "unknown");

                if (visualName.isEmpty() || "unknown".equalsIgnoreCase(visualName))
                    continue;

                float conf = item.has("confidence") ? (float) item.get("confidence").asDouble() : 0.0f;

                Float weight = null;
                if (item.has("estimated_grams"))
                    weight = (float) item.get("estimated_grams").asDouble();
                else if (item.has("estimatedPortionGrams"))
                    weight = (float) item.get("estimatedPortionGrams").asDouble();

                // 2. New Medical Fields extraction
                String baseIngredient = item.has("base_ingredient") ? item.get("base_ingredient").asText() : visualName;
                String state = item.has("visual_state") ? item.get("visual_state").asText() : "UNKNOWN";
                boolean needsValidation = item.has("requires_user_validation")
                        && item.get("requires_user_validation").asBoolean();

                List<String> risks = new ArrayList<>();
                if (item.has("risk_flags") && item.get("risk_flags").isArray()) {
                    for (JsonNode risk : item.get("risk_flags")) {
                        risks.add(risk.asText());
                    }
                }

                // 3. Construct the Object
                FoodRecognitionResult.RecognizedFoodItem recognizedItem = new FoodRecognitionResult.RecognizedFoodItem(
                        visualName, conf, weight);

                // Set the new fields
                recognizedItem.setBaseIngredient(baseIngredient);
                recognizedItem.setVisualState(state);
                recognizedItem.setRequiresValidation(needsValidation);
                recognizedItem.setRiskFlags(risks);

                results.add(recognizedItem);
            }
        }
        result.setDetectedFoods(results);
        return result;
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