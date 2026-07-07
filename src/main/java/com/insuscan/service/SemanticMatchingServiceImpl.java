package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.boundary.NutritionInfo;
import com.insuscan.util.ApiLogger;
import com.insuscan.util.OpenAiJsonParser;
import com.insuscan.util.GeminiApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Uses a vision model as a judge to pick the best USDA candidate for a detected food item.
 */
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
    public String findBestMatch(PipelineFoodItem visualTarget,
            List<NutritionInfo> candidates,
            String base64Image) {

        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0).getFdcId();
        }

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
            return candidates.get(0).getFdcId();
        }
    }

    private String buildJudgePrompt(PipelineFoodItem target, List<NutritionInfo> candidates) {
        String candidatesList = candidates.stream()
                .map(c -> String.format("- ID: %s | Name: %s", c.getFdcId(), c.getFoodName()))
                .collect(Collectors.joining("\n"));

        String risks = "NONE";
        String state = target.getVisualState() != null ? target.getVisualState() : "UNKNOWN";

        return String.format(
                """
                                    You are an automated data mapping assistant.

                                    TARGET:
                                    - Item Name: %s
                                    - Preparation State: %s
                                    - Characteristics: [%s]
                                    - Base Ingredient: %s

                                    CANDIDATE LIST:
                                    %s

                        TASK:
                                    Select the SINGLE fdcId from the list that is the best logical match for the TARGET.

                                    LOGIC RULES:
                                    1. FORM CHECK: If target is a whole vegetable/fruit, DISCARD 'Flour', 'Powder', 'Baby Food', 'Bread'.
                                    2. INGREDIENT MATCH: Prioritize the 'Base Ingredient'. If the target is 'Rice', prefer matches that ARE rice (grains), NOT rice-based products like 'Rice Noodles', 'Rice Paper', or 'Rice Flour'.
                                    3. STATE MATCH: If target state is '%s', prioritize items with descriptions like 'Roasted', 'Baked', 'Cooked'. Avoid 'Raw' unless no cooked option exists.
                                    4. FAT CHECK: If Characteristics contain 'HIGH_FAT' or 'OIL', prefer items mentioning 'oil', 'fat added', or preparation methods involving fat.
                                    5. SAFETY FALLBACK: If uncertain, choose the 'Plain' version rather than a highly processed product.

                                    OUTPUT FORMAT:
                                    You must return ONLY a JSON object exactly like this:
                                    {"best_match_id": "12345", "reason": "your reasoning here"}
                                    """,
                target.getName(),
                state,
                risks,
                target.getBaseIngredient(),
                candidatesList,
                state);
    }

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
            log.error("[JUDGE] Failed to parse verdict: {}. Raw content: \n{}", e.getMessage(), content);
        }
        return null;
    }
}