package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.FusedMeasurement;
import com.insuscan.enums.ReferenceObjectDimensions;
import com.insuscan.util.GeminiApiClient;
import com.insuscan.util.OpenAiApiClient;
import com.insuscan.util.OpenAiJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines AR-provided dimensions with vision-model estimates, applying confidence weighting and conflict detection.
 */
@Service
public class GeometryFusionServiceImpl implements GeometryFusionService {

    private static final Logger log = LoggerFactory.getLogger(GeometryFusionServiceImpl.class);

    private static final float AR_CONFIDENCE_BASE        = 0.75f;
    private static final float SIDE_IMAGE_CONFIDENCE_BASE = 0.70f;
    private static final float REFERENCE_BOOST           = 0.15f;
    private static final float CONFLICT_PENALTY          = 0.20f;
    private static final float CONFLICT_THRESHOLD_PCT    = 0.20f;

    private final OpenAiApiClient openAiClient;
    private final GeminiApiClient geminiClient;
    private final OpenAiJsonParser jsonParser;
    private final ObjectMapper objectMapper;

    public GeometryFusionServiceImpl(OpenAiApiClient openAiClient,
            GeminiApiClient geminiClient,
            OpenAiJsonParser jsonParser) {
        this.openAiClient = openAiClient;
        this.geminiClient = geminiClient;
        this.jsonParser = jsonParser;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public FusedMeasurement fuseDiameter(Float arDiameterCm, String topImageBase64,
            String sideImageBase64, String referenceObjectType, Float pixelToCmRatio) {

        Float visualDiameter = estimateVisualDiameter(
                topImageBase64, sideImageBase64, referenceObjectType, pixelToCmRatio);

        return fuseValues(
                arDiameterCm,    AR_CONFIDENCE_BASE,
                visualDiameter,  SIDE_IMAGE_CONFIDENCE_BASE,
                referenceObjectType, pixelToCmRatio,
                "diameter");
    }

    @Override
    public FusedMeasurement fuseDepth(Float arDepthCm, String topImageBase64,
            String sideImageBase64, String referenceObjectType, Float pixelToCmRatio) {

        Float visualDepth = estimateVisualDepth(
                topImageBase64, sideImageBase64, referenceObjectType, pixelToCmRatio);

        return fuseValues(
                arDepthCm,    AR_CONFIDENCE_BASE,
                visualDepth,  SIDE_IMAGE_CONFIDENCE_BASE,
                referenceObjectType, pixelToCmRatio,
                "depth");
    }

    @Override
    public List<FusedMeasurement> fuseFoodHeights(List<String> foodNames,
            List<String> heightCategories, String sideImageBase64,
            String referenceObjectType, Float pixelToCmRatio) {

        List<Float> visualHeights = estimateVisualFoodHeights(
                foodNames, sideImageBase64, referenceObjectType, pixelToCmRatio);

        List<FusedMeasurement> results = new ArrayList<>();

        for (int i = 0; i < foodNames.size(); i++) {
            Float visualHeight = (i < visualHeights.size()) ? visualHeights.get(i) : null;
            String category = (i < heightCategories.size()) ? heightCategories.get(i) : null;
            Float geminiHeight = heightCategoryToCm(category);

            FusedMeasurement fused = fuseValues(
                    geminiHeight, 0.50f,
                    visualHeight, SIDE_IMAGE_CONFIDENCE_BASE,
                    referenceObjectType, pixelToCmRatio,
                    "height[" + foodNames.get(i) + "]");

            results.add(fused);
        }

        return results;
    }

    private FusedMeasurement fuseValues(Float valueA, float confidenceA,
            Float valueB, float confidenceB,
            String referenceObjectType, Float pixelToCmRatio,
            String label) {

        boolean hasA = valueA != null && valueA > 0;
        boolean hasB = valueB != null && valueB > 0;
        boolean hasReference = referenceObjectType != null
                && !referenceObjectType.equalsIgnoreCase("NONE")
                && pixelToCmRatio != null && pixelToCmRatio > 0;

        if (hasReference) {
            confidenceA += REFERENCE_BOOST;
            confidenceB += REFERENCE_BOOST;
        }

        if (!hasA && !hasB) {
            log.warn("[fusion] {} — no measurements available, using fallback", label);
            return new FusedMeasurement(0f, 0f, FusedMeasurement.Source.FALLBACK);
        }

        if (hasA && !hasB) {
            log.debug("[fusion] {} → AR_ONLY: {}cm (conf={})", label, valueA, confidenceA);
            return new FusedMeasurement(valueA, Math.min(confidenceA, 1.0f),
                    hasReference ? FusedMeasurement.Source.AR_AND_REFERENCE
                                 : FusedMeasurement.Source.AR_ONLY);
        }

        if (!hasA && hasB) {
            log.debug("[fusion] {} → SIDE_ONLY: {}cm (conf={})", label, valueB, confidenceB);
            return new FusedMeasurement(valueB, Math.min(confidenceB, 1.0f),
                    hasReference ? FusedMeasurement.Source.SIDE_AND_REFERENCE
                                 : FusedMeasurement.Source.SIDE_IMAGE_ONLY);
        }

        float diff = Math.abs(valueA - valueB) / Math.max(valueA, valueB);
        if (diff > CONFLICT_THRESHOLD_PCT) {
            log.warn("[fusion] {} — conflict detected: AR={}cm vs SIDE={}cm ({}% diff), applying penalty",
                    label, valueA, valueB, String.format("%.0f", diff * 100));
            confidenceA -= CONFLICT_PENALTY;
            confidenceB -= CONFLICT_PENALTY;
        }

        confidenceA = Math.max(0f, Math.min(confidenceA, 1.0f));
        confidenceB = Math.max(0f, Math.min(confidenceB, 1.0f));

        float totalConfidence = confidenceA + confidenceB;
        float fusedValue = (valueA * confidenceA + valueB * confidenceB) / totalConfidence;
        float fusedConfidence = Math.min((confidenceA + confidenceB) / 2f + 0.10f, 1.0f);

        FusedMeasurement.Source source = hasReference
                ? FusedMeasurement.Source.ALL_THREE
                : FusedMeasurement.Source.AR_AND_SIDE;

        log.debug("[fusion] {} → {}: AR={}cm(w={}) + SIDE={}cm(w={}) = {}cm (conf={})",
                label, source, valueA, String.format("%.2f", confidenceA),
                valueB, String.format("%.2f", confidenceB),
                String.format("%.2f", fusedValue), String.format("%.2f", fusedConfidence));

        return new FusedMeasurement(fusedValue, fusedConfidence, source);
    }

    private Float estimateVisualDiameter(String topImageBase64, String sideImageBase64,
            String referenceObjectType, Float pixelToCmRatio) {
        try {
            String prompt = buildDimensionPrompt(referenceObjectType, pixelToCmRatio, true, false);
            String content = callVisionModel(prompt, topImageBase64, sideImageBase64);
            if (content == null) return null;

            JsonNode parsed = objectMapper.readTree(jsonParser.extractJsonObject(content));
            float diameter = (float) parsed.path("diameterCm").asDouble(0);
            return diameter > 0 ? Math.max(10f, Math.min(35f, diameter)) : null;

        } catch (Exception e) {
            log.warn("[fusion] estimateVisualDiameter failed: {}", e.getMessage());
            return null;
        }
    }

    private Float estimateVisualDepth(String topImageBase64, String sideImageBase64,
            String referenceObjectType, Float pixelToCmRatio) {
        try {
            String prompt = buildDimensionPrompt(referenceObjectType, pixelToCmRatio, false, true);
            String content = callVisionModel(prompt, topImageBase64, sideImageBase64);
            if (content == null) return null;

            JsonNode parsed = objectMapper.readTree(jsonParser.extractJsonObject(content));
            float depth = (float) parsed.path("depthCm").asDouble(0);
            return depth > 0 ? Math.max(0.5f, Math.min(15f, depth)) : null;

        } catch (Exception e) {
            log.warn("[fusion] estimateVisualDepth failed: {}", e.getMessage());
            return null;
        }
    }

    private List<Float> estimateVisualFoodHeights(List<String> foodNames,
            String sideImageBase64, String referenceObjectType, Float pixelToCmRatio) {

        List<Float> results = new ArrayList<>();
        if (sideImageBase64 == null || sideImageBase64.isBlank()) {
            return results;
        }

        try {
            String foodList = String.join(", ", foodNames);
            String scaleHint = buildScaleHint(referenceObjectType, pixelToCmRatio);

            String prompt = "You are a food volume measurement AI.\n"
                    + "Look at this SIDE VIEW image of a meal.\n\n"
                    + scaleHint + "\n\n"
                    + "Measure the HEIGHT of each food item as it sits in the container.\n"
                    + "Height = from the bottom of the food to the top of the pile.\n\n"
                    + "Foods to measure: " + foodList + "\n\n"
                    + "RULES:\n"
                    + "- Measure in centimeters.\n"
                    + "- A whole tomato or pepper lying flat = its diameter as height (~6-8cm).\n"
                    + "- A thin sauce layer = 0.3-0.5cm.\n"
                    + "- Rice pile = 2-4cm.\n"
                    + "- If a food is not visible from the side, return your best estimate.\n\n"
                    + "Return ONLY this JSON:\n"
                    + "{\n"
                    + "  \"heights\": [\n"
                    + "    { \"food\": \"food name\", \"heightCm\": <number>, \"confidence\": \"low|medium|high\" }\n"
                    + "  ]\n"
                    + "}";

            String content = geminiClient.callFlashModelWithImage(prompt, sideImageBase64);
            if (content == null || content.isBlank()) return results;

            JsonNode parsed = objectMapper.readTree(jsonParser.extractJsonObject(content));
            JsonNode heights = parsed.path("heights");

            if (heights.isArray()) {
                for (JsonNode h : heights) {
                    float heightCm = (float) h.path("heightCm").asDouble(0);
                    results.add(heightCm > 0 ? Math.max(0.3f, Math.min(15f, heightCm)) : null);
                }
            }

        } catch (Exception e) {
            log.warn("[fusion] estimateVisualFoodHeights failed: {}", e.getMessage());
        }

        return results;
    }

    private String buildDimensionPrompt(String referenceObjectType, Float pixelToCmRatio,
            boolean measureDiameter, boolean measureDepth) {

        String scaleHint = buildScaleHint(referenceObjectType, pixelToCmRatio);
        String task = measureDiameter && measureDepth ? "diameter AND depth"
                : measureDiameter ? "INNER DIAMETER only"
                : "INNER DEPTH only";

        return "You are a food container measurement AI.\n"
                + "Task: Measure the " + task + " of the container.\n\n"
                + scaleHint + "\n\n"
                + "Return ONLY this JSON:\n"
                + "{\n"
                + "  \"diameterCm\": <number or 0 if not measuring>,\n"
                + "  \"depthCm\": <number or 0 if not measuring>,\n"
                + "  \"confidence\": \"low|medium|high\"\n"
                + "}";
    }

    private String buildScaleHint(String referenceObjectType, Float pixelToCmRatio) {
        if (pixelToCmRatio != null && pixelToCmRatio > 0) {
            return String.format(
                    "CALIBRATED SCALE: 1 pixel = %.6f cm. Use this to convert pixel measurements to cm.",
                    pixelToCmRatio);
        }
        if (referenceObjectType != null && !referenceObjectType.equalsIgnoreCase("NONE")) {
            String dims = ReferenceObjectDimensions.getScaleDescription(referenceObjectType);
            return "REFERENCE OBJECT: A '" + referenceObjectType + "' is in the image. Known dimensions: "
                    + dims + ". Use it as your scale anchor.";
        }
        return "No scale reference available. Use standard container sizes as guidance.";
    }

    private String callVisionModel(String prompt, String topImageBase64, String sideImageBase64) {
        try {
            boolean hasSide = sideImageBase64 != null && !sideImageBase64.isBlank();
            boolean hasTop = topImageBase64 != null && !topImageBase64.isBlank();

            if (hasSide && hasTop) {
                return openAiClient.callModelWithMultipleImages(
                        openAiClient.getModel(), prompt, topImageBase64, sideImageBase64);
            } else if (hasTop) {
                return geminiClient.callFlashModelWithImage(prompt, topImageBase64);
            } else if (hasSide) {
                return geminiClient.callFlashModelWithImage(prompt, sideImageBase64);
            }
            return null;
        } catch (Exception e) {
            log.warn("[fusion] callVisionModel failed: {}", e.getMessage());
            return null;
        }
    }

    private float heightCategoryToCm(String category) {
        if (category == null) return 1.5f;
        switch (category.toUpperCase()) {
            case "FLAT":        return 0.4f;
            case "LOW_PILE":    return 1.0f;
            case "MEDIUM_PILE": return 2.0f;
            case "HIGH_PILE":   return 3.0f;
            default:            return 1.5f;
        }
    }
}