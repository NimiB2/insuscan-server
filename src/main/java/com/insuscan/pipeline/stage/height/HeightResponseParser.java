package com.insuscan.pipeline.stage.height;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.pipeline.model.PipelineFoodItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HeightResponseParser {

    private static final Logger log = LoggerFactory.getLogger(HeightResponseParser.class);

    private final HeightResponseSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public HeightResponseParser(HeightResponseSanitizer sanitizer, ObjectMapper objectMapper) {
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    public Map<String, HeightResult> parse(String response, List<PipelineFoodItem> foods) {
        Map<String, HeightResult> results = new HashMap<>();
        if (response == null || response.isBlank()) return results;

        Map<String, PipelineFoodItem> foodsByName = foods.stream()
            .collect(Collectors.toMap(PipelineFoodItem::getName, f -> f, (a, b) -> a));

        String sanitized = sanitizer.sanitize(response);
        log.debug("[FoodHeight] Sanitized response length: {} chars", sanitized.length());

        try {
            JsonNode root = objectMapper.readTree(sanitized);
            if (!root.isArray()) {
                log.warn("[FoodHeight] Response is not a JSON array after sanitization");
                return results;
            }
            for (JsonNode node : root) {
                extractItem(node, results, foodsByName);
            }
        } catch (Exception e) {
            log.error("[FoodHeight] Failed to parse sanitized response: {}", e.getMessage());
            log.debug("[FoodHeight] Sanitized content: {}", sanitized);
        }
        return results;
    }

    private void extractItem(JsonNode node, Map<String, HeightResult> results,
                             Map<String, PipelineFoodItem> foodsByName) {
        String name = node.path("name").asText(null);
        if (name == null || name.isBlank()) return;

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
            log.info("[FoodHeight] {} side bbox: x={} y={} w={} h={}",
                name, String.format("%.1f", x), String.format("%.1f", y),
                String.format("%.1f", w), String.format("%.1f", h));
        }

        results.put(name, new HeightResult(isVisible, height, conf));
    }
}