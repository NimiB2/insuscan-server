package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.FoodRecognitionResult;
import com.insuscan.boundary.NutritionInfo;
import com.insuscan.util.ApiLogger;
import com.insuscan.util.OpenAiJsonParser;
import com.insuscan.util.GeminiApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SemanticMatchingServiceImpl implements SemanticMatchingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticMatchingServiceImpl.class);

    private final GeminiApiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final ApiLogger apiLogger;
    private final OpenAiJsonParser jsonParser;

    public SemanticMatchingServiceImpl(GeminiApiClient geminiClient,
            ObjectMapper objectMapper,
            ApiLogger apiLogger,
            OpenAiJsonParser jsonParser) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.apiLogger = apiLogger;
        this.jsonParser = jsonParser;
    }

    @Override
    public String findBestMatch(FoodRecognitionResult.RecognizedFoodItem visualTarget,
                                List<NutritionInfo> candidates,
                                String base64Image) {
    	
    	if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // Optimization: If only 1 candidate exists, verify it loosely or just return it
        if (candidates.size() == 1) {
            return candidates.get(0).getFdcId();
        }

//        apiLogger.openaiStart("SEMANTIC_JUDGE", candidates.size());
//        long startTime = System.currentTimeMillis();
//
//        try {
//        	// 1. Build the "Trial" (The Prompt)
//            String prompt = buildJudgePrompt(visualTarget, candidates);
//            Map<String, Object> requestBody = buildRequestBody(prompt, base64Image);
//            
//            // 2. Call the Judge (OpenAI)
//            String response = webClient.post()
//                    .uri("/chat/completions")
//                    .header("Authorization", "Bearer " + openAiApiKey)
//                    .header("Content-Type", "application/json")
//                    .bodyValue(requestBody)
//                    .retrieve()
//                    .bodyToMono(String.class)
//                    .block();
//
//            // 3. Parse the Verdict
//            String bestFdcId = parseJudgeVerdict(response);
//
//            long elapsed = System.currentTimeMillis() - startTime;
        
        apiLogger.openaiStart("SEMANTIC_JUDGE", candidates.size());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildJudgePrompt(visualTarget, candidates);

            String content;
            if (base64Image != null && !base64Image.isBlank()) {
                content = geminiClient.callFlashModelWithImage(prompt, base64Image);
            } else {
                content = geminiClient.callFlashModel(prompt);
            }

            String bestFdcId = parseJudgeVerdictDirect(content);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[JUDGE] Verdict: Selected ID {} for target '{}' (in {}ms)", bestFdcId, visualTarget.getName(),
                    elapsed);

            return bestFdcId;

        } catch (Exception e) {
            log.error("[JUDGE] Failed to rank candidates: {}", e.getMessage());
            // Fallback: If judge fails, return the first item (safest fallback)
            return candidates.get(0).getFdcId();
        }
    }

    private String buildJudgePrompt(FoodRecognitionResult.RecognizedFoodItem target, List<NutritionInfo> candidates) {
        // Prepare simplified candidate list string to save tokens
        String candidatesList = candidates.stream()
                .map(c -> String.format("- ID: %s | Name: %s", c.getFdcId(), c.getFoodName()))
                .collect(Collectors.joining("\n"));

        String risks = target.getRiskFlags() != null ? String.join(", ", target.getRiskFlags()) : "NONE";
        String state = target.getVisualState() != null ? target.getVisualState() : "UNKNOWN";

        return String.format(
                """
                        You are a Clinical Nutrition Data Matcher.

                        TARGET (Visual Analysis):
                        - Item: %s
                        - State: %s (Critical for Glycemic Index)
                        - Risk Flags: [%s]
                        - Base Ingredient: %s

                        CANDIDATE LIST (From USDA Database):
                        %s

        				TASK:
                        Look at the image and the TARGET description. Select the SINGLE fdcId from the list that represents the most scientifically accurate nutritional match.
                        If the image clearly contradicts the TARGET name (e.g. TARGET says "fried rice" but image shows plain white rice), note this in your reason.
                        
                        LOGIC RULES:
                        1. FORM CHECK: If target is a whole vegetable/fruit, DISCARD 'Flour', 'Powder', 'Baby Food', 'Bread'.
                        2. INGREDIENT MATCH: Prioritize the 'Base Ingredient'. If the target is 'Rice', prefer matches that ARE rice (grains), NOT rice-based products like 'Rice Noodles', 'Rice Paper', or 'Rice Flour'.
                        3. STATE MATCH: If target state is '%s', prioritize items with descriptions like 'Roasted', 'Baked', 'Cooked'. Avoid 'Raw' unless no cooked option exists.
                        4. FAT CHECK: If Risk Flags contain 'HIGH_FAT' or 'OIL', prefer items mentioning 'oil', 'fat added', or preparation methods involving fat.
                        5. SAFETY FALLBACK: If uncertain, choose the 'Plain/Raw' version rather than a processed product (like chips/bread).

                        OUTPUT:
                        Return STRICT JSON ONLY:
                        { "best_match_id": "12345", "reason": "Matches roasted state and whole form" }
                        """,
                target.getName(),
                state,
                risks,
                target.getBaseIngredient(),
                candidatesList,
                state);
    }

//    private Map<String, Object> buildRequestBody(String prompt, String base64Image) {
//        // Build user message content — text only, or text + image
//        Object userContent;
//        if (base64Image != null && !base64Image.isBlank()) {
//            // Send image so the Judge can visually verify the match
//            Map<String, Object> textPart = Map.of("type", "text", "text", prompt);
//            Map<String, Object> imageUrl = Map.of("url", "data:image/jpeg;base64," + base64Image);
//            Map<String, Object> imagePart = Map.of("type", "image_url", "image_url", imageUrl);
//            userContent = List.of(textPart, imagePart);
//        } else {
//            // Fallback: text-only (still works, just less accurate)
//            userContent = prompt;
//        }
//
//        return Map.of(
//                "model", openAiModel,
//                "messages", List.of(
//                        Map.of("role", "system", "content", "You are a JSON-only data matching engine."),
//                        Map.of("role", "user", "content", userContent)),
//                "temperature", 0.0,
//                "response_format", Map.of("type", "json_object")
//        );
//    }

//    private String parseJudgeVerdict(String jsonResponse) {
//        try {
//            JsonNode result = jsonParser.parseContent(jsonResponse);
//            if (result.has("best_match_id")) {
//                return result.get("best_match_id").asText();
//            }
//        } catch (Exception e) {
//            log.error("Failed to parse judge response", e);
//        }
//        return null;
//    }
    
    private String parseJudgeVerdictDirect(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            String json = jsonParser.extractJsonObject(content);
            JsonNode result = objectMapper.readTree(json);
            if (result.has("best_match_id")) {
                return result.get("best_match_id").asText();
            }
        } catch (Exception e) {
            log.error("[JUDGE] Failed to parse verdict: {}", e.getMessage());
        }
        return null;
    }
}