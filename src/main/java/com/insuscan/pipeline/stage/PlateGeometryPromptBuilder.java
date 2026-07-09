package com.insuscan.pipeline.stage;

import com.insuscan.pipeline.support.ReferenceObjectRegistry;
import com.insuscan.pipeline.support.StandardPlateConfig;
import org.springframework.stereotype.Component;

/**
 * Builds the Gemini prompts used by {@link PlateGeometryService} to measure plate diameter (top view) and depth (side view).
 */
@Component
public class PlateGeometryPromptBuilder {

    private final ReferenceObjectRegistry referenceObjectRegistry;
    private final StandardPlateConfig standardPlateConfig;

    public PlateGeometryPromptBuilder(ReferenceObjectRegistry referenceObjectRegistry,
            StandardPlateConfig standardPlateConfig) {
        this.referenceObjectRegistry = referenceObjectRegistry;
        this.standardPlateConfig = standardPlateConfig;
    }

    public String diameterPromptWithReference(String refType) {
        ReferenceObjectRegistry.Dimensions dims = referenceObjectRegistry.getDimensions(refType);
        String description = referenceObjectRegistry.getDescription(refType);

        return String.format(
                "You are measuring a round plate from a TOP-DOWN view.\n\n" +
                "A reference object is present in the image: %s (%.2fcm long x %.2fcm wide).\n" +
                "The reference object is placed NEXT TO the plate (to its left or right), NOT above or below it.\n\n" +
                "TASK: Identify the vertical extent of the plate in the image.\n" +
                "- top_y_px: pixel Y coordinate of the TOP edge of the plate (highest point of the plate rim).\n" +
                "- bottom_y_px: pixel Y coordinate of the BOTTOM edge of the plate (lowest point of the plate rim).\n" +
                "- plate_center_x_px: pixel X coordinate of the horizontal center of the plate.\n" +
                "Use the VERTICAL axis (Y) because the reference object lies horizontally next to the plate " +
                "and would interfere with horizontal (X) measurements.\n\n" +
                "Also identify the container type and fill percentage.\n\n" +
                "Return ONLY valid JSON with no markdown or extra text:\n" +
                "{\n" +
                "  \"container_type\": \"FLAT_PLATE\" or \"REGULAR_BOWL\" or \"DEEP_BOWL\",\n" +
                "  \"top_y_px\": <integer>,\n" +
                "  \"bottom_y_px\": <integer>,\n" +
                "  \"plate_center_x_px\": <integer>,\n" +
                "  \"fill_percent\": <integer 0-100>,\n" +
                "  \"diameter_confidence\": <float 0.0-1.0>,\n" +
                "  \"reasoning_notes\": \"<max 20 words>\"\n" +
                "}",
                description, dims.lengthCm(), dims.widthCm());
    }

    public String diameterPromptNoReference() {
        return String.format(
                "You are measuring a round plate from a TOP-DOWN view.\n\n" +
                "NO reference object is available for scale calibration.\n" +
                "Estimate the plate's inner diameter based on visual cues alone.\n\n" +
                "Typical plate sizes in the world are:\n" +
                "- Small (%.0fcm): side plate / dessert plate\n" +
                "- Standard (%.0fcm): regular dinner plate\n" +
                "- Large (%.0fcm): large dinner plate / serving plate\n\n" +
                "Estimate the diameter as accurately as possible, and rate your confidence honestly.\n" +
                "Also identify the plate's pixel geometry so the system can derive scale:\n" +
                "- top_y_px: pixel Y coordinate of the TOP edge of the plate rim.\n" +
                "- bottom_y_px: pixel Y coordinate of the BOTTOM edge of the plate rim.\n" +
                "- plate_center_x_px: pixel X coordinate of the horizontal center of the plate.\n\n" +
                "Return ONLY valid JSON with no markdown or extra text:\n" +
                "{\n" +
                "  \"container_type\": \"FLAT_PLATE\" or \"REGULAR_BOWL\" or \"DEEP_BOWL\",\n" +
                "  \"inner_diameter_cm\": <float>,\n" +
                "  \"top_y_px\": <integer>,\n" +
                "  \"bottom_y_px\": <integer>,\n" +
                "  \"plate_center_x_px\": <integer>,\n" +
                "  \"fill_percent\": <integer 0-100>,\n" +
                "  \"diameter_confidence\": <float 0.0-1.0>,\n" +
                "  \"reasoning_notes\": \"<max 20 words>\"\n" +
                "}",
                standardPlateConfig.getSmallDiameterCm(),
                standardPlateConfig.getStandardDiameterCm(),
                standardPlateConfig.getLargeDiameterCm());
    }

    public String depthPrompt(String refType, Float pixelToCmRatioSide) {
        ReferenceObjectRegistry.Dimensions dims = referenceObjectRegistry.getDimensions(refType);
        String description = referenceObjectRegistry.getDescription(refType);

        String sideRatio = pixelToCmRatioSide != null
                ? String.format("%.2f", pixelToCmRatioSide)
                : "not available";

        return String.format(
                "You are measuring a food container from a SIDE view.\n\n" +
                "Scale calibration: %s pixels per cm\n" +
                "Reference object in image: %s (%.2fcm long x %.2fcm wide)\n\n" +
                "Estimate ONLY the inner depth of the plate or bowl (from rim to bottom, in cm).\n" +
                "Do NOT estimate diameter or width — that is measured from a top-down image.\n\n" +
                "Return ONLY valid JSON with no markdown or extra text:\n" +
                "{\n" +
                "  \"inner_depth_cm\": <float>,\n" +
                "  \"depth_confidence\": <float 0.0-1.0>,\n" +
                "  \"reasoning_notes\": \"<max 20 words>\"\n" +
                "}",
                sideRatio, description, dims.lengthCm(), dims.widthCm());
    }
}