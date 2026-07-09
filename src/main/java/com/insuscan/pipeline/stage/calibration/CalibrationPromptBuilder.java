package com.insuscan.pipeline.stage.calibration;

import com.insuscan.enums.ReferenceObjectDimensions;
import com.insuscan.pipeline.support.ReferenceObjectRegistry;
import org.springframework.stereotype.Component;

/**
 * Builds the Gemini calibration prompts for both top and side views, and for the case where no primary reference object was selected by the user.
 */
@Component
public class CalibrationPromptBuilder {

    private final ReferenceObjectRegistry referenceObjectRegistry;

    public CalibrationPromptBuilder(ReferenceObjectRegistry referenceObjectRegistry) {
        this.referenceObjectRegistry = referenceObjectRegistry;
    }

    public String topPrompt(String refType, ReferenceObjectRegistry.Dimensions dims) {
        return String.format(
            "Find the following reference object in this TOP-DOWN image for scale calibration.\n\n" +
            "PRIMARY object to find: %s\n" +
            "Known physical dimensions: %.2fcm long × %.2fcm wide.\n\n" +
            "The object is placed OUTSIDE the food container, next to the plate.\n\n" +
            "STEP 1: Look for the PRIMARY object. If found, set 'found' to true.\n" +
            "STEP 2: If the PRIMARY object is NOT found, scan for any of these known objects:\n%s\n" +
            "If you find one, set 'found' to false and 'alternative_object_type' to its exact name.\n\n" +
            "Return ONLY valid JSON with no markdown:\n" +
            "{\n" +
            "  \"found\": <true or false>,\n" +
            "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
            "  \"confidence\": <float 0.0-1.0>,\n" +
            "  \"detected_object_type\": \"<PRIMARY type if found, else null>\",\n" +
            "  \"alternative_object_type\": \"<alternative type if found, else null>\",\n" +
            "  \"reason_if_not_found\": \"<string>\"\n" +
            "}",
            referenceObjectRegistry.getDescription(refType), dims.lengthCm(), dims.widthCm(), buildKnownObjectsList());
    }

    public String sidePrompt(String refType, ReferenceObjectRegistry.Dimensions dims) {
        return String.format(
            "Find the following reference object in this SIDE-VIEW image for scale calibration.\n\n" +
            "PRIMARY object to find: %s\n" +
            "Known physical dimension visible from the side: %.2fcm.\n\n" +
            "The object is placed OUTSIDE the food container, next to the plate.\n\n" +
            "STEP 1: Look for the PRIMARY object. If found, set 'found' to true.\n" +
            "STEP 2: If the PRIMARY object is NOT found, scan for any of these known objects:\n%s\n" +
            "If you find one, set 'found' to false and 'alternative_object_type' to its exact name.\n\n" +
            "Return ONLY valid JSON with no markdown:\n" +
            "{\n" +
            "  \"found\": <true or false>,\n" +
            "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
            "  \"confidence\": <float 0.0-1.0>,\n" +
            "  \"detected_object_type\": \"<PRIMARY type if found, else null>\",\n" +
            "  \"alternative_object_type\": \"<alternative type if found, else null>\",\n" +
            "  \"reason_if_not_found\": \"<string>\"\n" +
            "}",
            referenceObjectRegistry.getDescription(refType), dims.sideViewDimensionCm(), buildKnownObjectsList());
    }

    public String topPromptNone() {
        return String.format(
            "Find any reference object in this TOP-DOWN image for scale calibration.\n\n" +
            "The user did NOT select a primary reference object. " +
            "Scan the entire image for any of these known objects:\n%s\n" +
            "The object is typically placed OUTSIDE the food container, next to the plate.\n\n" +
            "If you find one, set 'found' to false, set 'alternative_object_type' to its exact name, " +
            "and fill 'bbox_px' with its bounding box.\n" +
            "If you find none, set 'found' to false and 'alternative_object_type' to null.\n\n" +
            "Return ONLY valid JSON with no markdown:\n" +
            "{\n" +
            "  \"found\": false,\n" +
            "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
            "  \"confidence\": <float 0.0-1.0>,\n" +
            "  \"detected_object_type\": null,\n" +
            "  \"alternative_object_type\": \"<type if found, else null>\",\n" +
            "  \"reason_if_not_found\": \"<string>\"\n" +
            "}",
            buildKnownObjectsList());
    }

    public String sidePromptNone() {
        return String.format(
            "Find any reference object in this SIDE-VIEW image for scale calibration.\n\n" +
            "The user did NOT select a primary reference object. " +
            "Scan the entire image for any of these known objects:\n%s\n" +
            "The object is typically placed OUTSIDE the food container, next to the plate.\n\n" +
            "If you find one, set 'found' to false, set 'alternative_object_type' to its exact name, " +
            "and fill 'bbox_px' with its bounding box.\n" +
            "If you find none, set 'found' to false and 'alternative_object_type' to null.\n\n" +
            "Return ONLY valid JSON with no markdown:\n" +
            "{\n" +
            "  \"found\": false,\n" +
            "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
            "  \"confidence\": <float 0.0-1.0>,\n" +
            "  \"detected_object_type\": null,\n" +
            "  \"alternative_object_type\": \"<type if found, else null>\",\n" +
            "  \"reason_if_not_found\": \"<string>\"\n" +
            "}",
            buildKnownObjectsList());
    }

    private String buildKnownObjectsList() {
        StringBuilder sb = new StringBuilder();
        for (ReferenceObjectDimensions ref : ReferenceObjectDimensions.values()) {
            if (ref == ReferenceObjectDimensions.NONE) continue;
            sb.append("- ").append(ref.name())
              .append(": ").append(ref.getScaleDescription()).append("\n");
        }
        return sb.toString();
    }
}