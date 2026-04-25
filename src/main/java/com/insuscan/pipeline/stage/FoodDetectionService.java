package com.insuscan.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.util.GeminiApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 3 — Detects food items in the top-down image.
 *
 * <p>Focuses strictly on identification, visual state, and bounding box.
 * Also retrieves the model's estimate of 'effectiveDensityFactor' (0.3 - 1.0)
 * which describes how much of the visual volume is actual food vs air.</p>
 */
@Service
public class FoodDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FoodDetectionService.class);

    private final GeminiApiClient geminiApiClient;
    private final PipelineWarningCollector warningCollector;
    private final ObjectMapper objectMapper;

    public FoodDetectionService(GeminiApiClient geminiApiClient,
                                PipelineWarningCollector warningCollector,
                                ObjectMapper objectMapper) {
        this.geminiApiClient = geminiApiClient;
        this.warningCollector = warningCollector;
        this.objectMapper = objectMapper;
    }

    public void detectFoods(PipelineContext ctx) {
        String prompt = buildPrompt(ctx);
        float confidence = 0f;

        try {
            String response = geminiApiClient.callVisionModel(prompt, ctx.getImageTopBase64());

            if (response != null && !response.isBlank()) {
                List<PipelineFoodItem> detectedItems = parseResponse(response);
                if (detectedItems.isEmpty()) {
                    log.warn("[FoodDetection] No foods detected in response.");
                    // Not necessarily an error (could be an empty plate)
                } else {
                    ctx.setFoodItems(detectedItems);
                    // Compute average confidence of detected items
                    double avgConf = detectedItems.stream()
                        .mapToDouble(PipelineFoodItem::getDetectionConfidence)
                        .average()
                        .orElse(0.0);
                    confidence = (float) avgConf;
                }
            } else {
                log.warn("[FoodDetection] Empty response from vision model");
            }
        } catch (Exception e) {
            log.error("[FoodDetection] Vision model call failed: {}", e.getMessage());
            warningCollector.add(ctx, com.insuscan.pipeline.model.PipelineWarning.critical(
                PipelineWarningCollector.STAGE_FOOD_DETECTION,
                "DETECTION_FAILED",
                "Failed to detect food items: " + e.getMessage()
            ));
        }

        ctx.recordConfidence("FOOD_DETECTION", confidence);
        log.info("[FoodDetection] Detected {} items, avg confidence={}",
            ctx.getFoodItems().size(), String.format("%.2f", confidence));
    }

    private String buildPrompt(PipelineContext ctx) {
        return "You are an expert food nutritionist AI.\n" +
            "Analyze the food items on the plate in the image.\n\n" +
            "For each distinct food item, provide:\n" +
            "1. name: Common recognizable name.\n" +
            "2. base_ingredient: The core ingredient (e.g. 'chicken', 'rice', 'beef') for USDA lookup.\n" +
            "3. visual_state: One of RAW, COOKED, FRIED, GRILLED, BAKED, BOILED.\n" +
            "4. search_terms: List of 2-3 specific terms for searching a nutrition database.\n" +
            "5. requires_user_validation: true if you are unsure what this food is.\n" +
            "6. confidence: float between 0.0 and 1.0.\n" +
            "7. bbox_pct: Bounding box covering the food as percentages of the image (0-100) -> {x, y, width, height}.\n" +
            "8. effective_density_factor: A float between 0.3 and 1.0 representing how much of the food's visual volume is actual solid food vs air. " +
            "For example: solid chicken=1.0, dense rice=0.9, bread/cake=0.65, airy leafy salad=0.35, popcorn=0.3.\n\n" +
            "Return ONLY a JSON array of food objects with NO markdown formatting:\n" +
            "[\n" +
            "  {\n" +
            "    \"name\": \"Grilled Chicken Breast\",\n" +
            "    \"base_ingredient\": \"chicken\",\n" +
            "    \"visual_state\": \"GRILLED\",\n" +
            "    \"search_terms\": [\"grilled chicken breast\", \"chicken breast\"],\n" +
            "    \"requires_user_validation\": false,\n" +
            "    \"confidence\": 0.95,\n" +
            "    \"effective_density_factor\": 1.0,\n" +
            "    \"bbox_pct\": { \"x\": 10, \"y\": 20, \"width\": 30, \"height\": 40 }\n" +
            "  }\n" +
            "]";
    }

    private List<PipelineFoodItem> parseResponse(String response) {
        List<PipelineFoodItem> items = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                log.warn("[FoodDetection] Expected JSON array, got something else");
                return items;
            }

            for (JsonNode node : root) {
                PipelineFoodItem item = new PipelineFoodItem();
                item.setName(node.path("name").asText("Unknown Food"));
                item.setBaseIngredient(node.path("base_ingredient").asText(item.getName()));
                item.setVisualState(node.path("visual_state").asText("COOKED"));
                item.setRequiresUserValidation(node.path("requires_user_validation").asBoolean(true));
                item.setDetectionConfidence((float) node.path("confidence").asDouble(0.5));
                item.setEffectiveDensityFactor((float) node.path("effective_density_factor").asDouble(0.8));

                List<String> terms = new ArrayList<>();
                JsonNode termsNode = node.path("search_terms");
                if (termsNode.isArray()) {
                    for (JsonNode term : termsNode) {
                        terms.add(term.asText());
                    }
                }
                item.setSearchTerms(terms);

                JsonNode bbox = node.path("bbox_pct");
                if (!bbox.isMissingNode()) {
                    item.setBoundingBoxPct(new float[]{
                        (float) bbox.path("x").asDouble(0),
                        (float) bbox.path("y").asDouble(0),
                        (float) bbox.path("width").asDouble(0),
                        (float) bbox.path("height").asDouble(0)
                    });
                }

                items.add(item);
            }
        } catch (Exception e) {
            log.error("[FoodDetection] Failed to parse response: {}", e.getMessage());
        }
        return items;
    }
}
