package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.FoodRecognitionResult;
import com.insuscan.boundary.NutritionInfo;
import com.insuscan.util.ApiLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SemanticMatchingServiceImpl implements SemanticMatchingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticMatchingServiceImpl.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ApiLogger apiLogger;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    public SemanticMatchingServiceImpl(WebClient.Builder webClientBuilder,
                                       ObjectMapper objectMapper,
                                       ApiLogger apiLogger) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .build();
        this.objectMapper = objectMapper;
        this.apiLogger = apiLogger;
    }

    @Override
    public String findBestMatch(FoodRecognitionResult.RecognizedFoodItem visualTarget, List<NutritionInfo> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // Optimization: If only 1 candidate exists, verify it loosely or just return it
        if (candidates.size() == 1) {
            return candidates.get(0).getFdcId();
        }

        apiLogger.openaiStart("SEMANTIC_JUDGE", candidates.size());
        long startTime = System.currentTimeMillis();

        try {
            // 1. Build the "Trial" (The Prompt)
            String prompt = buildJudgePrompt(visualTarget, candidates);
            Map<String, Object> requestBody = buildRequestBody(prompt);

            // 2. Call the Judge (OpenAI)
            String response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 3. Parse the Verdict
            String bestFdcId = parseJudgeVerdict(response);
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[JUDGE] Verdict: Selected ID {} for target '{}' (in {}ms)", bestFdcId, visualTarget.getName(), elapsed);

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

        return String.format("""
                You are a Clinical Nutrition Data Matcher.
                
                TARGET (Visual Analysis):
                - Item: %s
                - State: %s (Critical for Glycemic Index)
                - Risk Flags: [%s]
                - Base Ingredient: %s
                
                CANDIDATE LIST (From USDA Database):
                %s
                
                TASK:
                Select the SINGLE fdcId from the list that represents the most scientifically accurate nutritional match for the TARGET.
                
                LOGIC RULES:
                1. FORM CHECK: If target is a whole vegetable/fruit, DISCARD 'Flour', 'Powder', 'Baby Food', 'Bread'.
                2. STATE MATCH: If target state is '%s', prioritize items with descriptions like 'Roasted', 'Baked', 'Cooked'. Avoid 'Raw' unless no cooked option exists.
                3. FAT CHECK: If Risk Flags contain 'HIGH_FAT' or 'OIL', prefer items mentioning 'oil', 'fat added', or preparation methods involving fat.
                4. SAFETY FALLBACK: If uncertain, choose the 'Plain/Raw' version rather than a processed product (like chips/bread).
                
                OUTPUT:
                Return STRICT JSON ONLY:
                { "best_match_id": "12345", "reason": "Matches roasted state and whole form" }
                """,
                target.getName(),
                state,
                risks,
                target.getBaseIngredient(),
                candidatesList,
                state
        );
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "model", openAiModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a JSON-only data matching engine."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.0 // Zero temperature for maximum logic/determinism
        );
    }

    private String parseJudgeVerdict(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode choices = root.get("choices");
            if (choices != null && !choices.isEmpty()) {
                String content = choices.get(0).get("message").get("content").asText();
                
                // Extract JSON from content (handle potential markdown)
                int start = content.indexOf('{');
                int end = content.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    content = content.substring(start, end + 1);
                    JsonNode result = objectMapper.readTree(content);
                    if (result.has("best_match_id")) {
                        return result.get("best_match_id").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse judge response", e);
        }
        return null;
    }
}