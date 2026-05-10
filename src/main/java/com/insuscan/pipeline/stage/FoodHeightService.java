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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stage 5 — Estimates the effective height of each food item using the side image.
 *
 * <p>Effective height represents the height of a perfect cylinder that would contain
 * the same volume as the food item, given its 2D surface area.</p>
 */
@Service
public class FoodHeightService {

    private static final Logger log = LoggerFactory.getLogger(FoodHeightService.class);

    private final GeminiApiClient geminiApiClient;
    private final PipelineWarningCollector warningCollector;
    private final ObjectMapper objectMapper;

    public FoodHeightService(GeminiApiClient geminiApiClient,
                             PipelineWarningCollector warningCollector,
                             ObjectMapper objectMapper) {
        this.geminiApiClient = geminiApiClient;
        this.warningCollector = warningCollector;
        this.objectMapper = objectMapper;
    }

    public void estimateHeights(PipelineContext ctx) {
        List<PipelineFoodItem> foods = ctx.getFoodItems();
        if (foods.isEmpty()) {
            ctx.recordConfidence("FOOD_HEIGHT", 1.0f);
            return;
        }

        if (ctx.getPixelToCmRatioSide() == null || ctx.getPixelToCmRatioSide() <= 0) {
            log.warn("[FoodHeight] Missing side calibration ratio. Using default heights.");
            applyDefaultHeights(ctx, foods);
            return;
        }

        String prompt = buildPrompt(ctx, foods);
        float confidence = 0f;

        try {
        	String response = geminiApiClient.callFlashModelWithImage(prompt, ctx.getImageSideBase64());
            if (response != null && !response.isBlank()) {
            	Map<String, HeightResult> parsedHeights = parseResponse(response, foods);
                
                float totalConf = 0f;
                int count = 0;

                for (PipelineFoodItem item : foods) {
                    HeightResult hr = parsedHeights.get(item.getName());
                    if (hr != null) {
                        item.setEffectiveHeightCm(hr.heightCm);
                        item.setHeightConfidence(hr.confidence);
                        
                        if (!hr.isVisible) {
                            warningCollector.heightNotVisible(ctx, item.getName());
                            // Fallback to plate depth if not visible
                            float defaultH = ctx.getPlateGeometry() != null ? ctx.getPlateGeometry().getInnerDepthCm() : 2.5f;
                            item.setEffectiveHeightCm(defaultH);
                        } else if (ctx.getPlateGeometry() != null && hr.heightCm > ctx.getPlateGeometry().getInnerDepthCm() * 1.5f) {
                            warningCollector.heightExceedsPlateDepth(ctx, item.getName(), hr.heightCm, ctx.getPlateGeometry().getInnerDepthCm());
                        }

                        totalConf += hr.confidence;
                        count++;
                        log.info("[FoodHeight] {} -> {}cm (conf: {})", item.getName(), String.format("%.1f", item.getEffectiveHeightCm()), String.format("%.2f", hr.confidence));
                        } else {
                        log.warn("[FoodHeight] Model missed item: {}", item.getName());
                        item.setEffectiveHeightCm(2.5f);
                        item.setHeightConfidence(0.1f);
                        totalConf += 0.1f;
                        count++;
                    }
                }
                confidence = count > 0 ? (totalConf / count) : 0f;

            } else {
                log.warn("[FoodHeight] Empty response from vision model");
                applyDefaultHeights(ctx, foods);
            }
        } catch (Exception e) {
            log.error("[FoodHeight] Vision model call failed: {}", e.getMessage());
            warningCollector.add(ctx, com.insuscan.pipeline.model.PipelineWarning.high(
                PipelineWarningCollector.STAGE_FOOD_HEIGHT,
                "HEIGHT_ESTIMATION_FAILED",
                "Failed to estimate food heights: " + e.getMessage()
            ));
            applyDefaultHeights(ctx, foods);
        }

        ctx.recordConfidence("FOOD_HEIGHT", confidence);
    }

    private void applyDefaultHeights(PipelineContext ctx, List<PipelineFoodItem> foods) {
        float defaultH = ctx.getPlateGeometry() != null ? ctx.getPlateGeometry().getInnerDepthCm() : 2.5f;
        for (PipelineFoodItem item : foods) {
            item.setEffectiveHeightCm(defaultH);
            item.setHeightConfidence(0.2f);
        }
        ctx.recordConfidence("FOOD_HEIGHT", 0.2f);
    }

    private String buildPrompt(PipelineContext ctx, List<PipelineFoodItem> foods) {
        String foodNamesList = foods.stream()
            .map(PipelineFoodItem::getName)
            .collect(Collectors.joining("', '", "'", "'"));

        String plateContext = "";
        if (ctx.getPlateGeometry() != null) {
            plateContext = String.format("The container is a %s with a depth of %.1fcm.\n",
                ctx.getPlateGeometry().getContainerType(),
                ctx.getPlateGeometry().getInnerDepthCm());
        }

        return String.format(
            "You are estimating the vertical height of food items from a side-angle view.\n\n" +
            "Scale calibration for this side image: %.4f cm per pixel.\n" +
            "%s\n" +
            "The following food items were detected from the top-down view:\n[%s]\n\n" +
            "For EACH food item in the list, estimate its 'effective height' in cm.\n" +
            "Effective height is the height of a perfect cylinder that would contain the same volume as the food item, given its 2D surface area.\n" +
            "For flat/spread foods (like sauce or a thin layer of rice), this will be small (e.g. 1.0cm).\n" +
            "For domed foods (like a chicken breast), imagine flattening it out over its footprint.\n\n" +
            "Return ONLY a JSON array of objects with NO markdown formatting:\n" +
            "[\n" +
            "  {\n" +
            "    \"name\": \"<exact name from list>\",\n" +
            "    \"is_visible_in_side_view\": true,\n" +
            "    \"effective_height_cm\": <float>,\n" +
            "    \"confidence\": <float 0.0-1.0>,\n" +
            "    \"bbox_side_pct\": { \"x\": <float 0-100>, \"y\": <float 0-100>, \"w\": <float 0-100>, \"h\": <float 0-100> }\n" +
            "  }\n" +
            "]",
            ctx.getPixelToCmRatioSide(), plateContext, foodNamesList);
    }

    private Map<String, HeightResult> parseResponse(String response, List<PipelineFoodItem> foods) {
        Map<String, HeightResult> results = new HashMap<>();
        Map<String, PipelineFoodItem> foodsByName = foods.stream()
            .collect(Collectors.toMap(PipelineFoodItem::getName, f -> f, (a, b) -> a));

        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) return results;

            for (JsonNode node : root) {
                String name = node.path("name").asText();
                boolean isVisible = node.path("is_visible_in_side_view").asBoolean(true);
                float height = (float) node.path("effective_height_cm").asDouble(2.5);
                float conf = (float) node.path("confidence").asDouble(0.5);

                JsonNode bbox = node.path("bbox_side_pct");
                if (!bbox.isMissingNode() && foodsByName.containsKey(name)) {
                    float x = (float) bbox.path("x").asDouble(0);
                    float y = (float) bbox.path("y").asDouble(0);
                    float w = (float) bbox.path("w").asDouble(80);
                    float h = (float) bbox.path("h").asDouble(80);
                    foodsByName.get(name).setBoundingBoxSidePct(new float[]{x, y, w, h});
                    log.info("[FoodHeight] {} side bbox: x={} y={} w={} h={}", name,
                        String.format("%.1f", x), String.format("%.1f", y),
                        String.format("%.1f", w), String.format("%.1f", h));
                }

                results.put(name, new HeightResult(isVisible, height, conf));
            }
        } catch (Exception e) {
            log.error("[FoodHeight] Failed to parse response: {}", e.getMessage());
        }
        return results;
    }

    private record HeightResult(boolean isVisible, float heightCm, float confidence) {}
}
