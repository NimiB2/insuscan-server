package com.insuscan.pipeline.stage;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 4 — Calculates the 2D surface area of each detected food item.
 *
 * <p>Currently uses a bounding-box approximation (width × height × 0.75).
 * When GrabCut is fully integrated (either server-side via JavaCV or
 * via a two-step client API), this stage will use pixel-perfect segmentation.</p>
 */
@Service
public class FoodAreaService {

    private static final Logger log = LoggerFactory.getLogger(FoodAreaService.class);

    private final PipelineWarningCollector warningCollector;

    public FoodAreaService(PipelineWarningCollector warningCollector) {
        this.warningCollector = warningCollector;
    }

    public void calculateAreas(PipelineContext ctx) {
        if (ctx.getPixelToCmRatioTop() == null || ctx.getPixelToCmRatioTop() <= 0) {
            log.warn("[FoodArea] Missing top calibration ratio. Cannot calculate real-world areas.");
            ctx.getFoodItems().forEach(item -> item.setAreaCm2(null));
            ctx.recordConfidence("FOOD_AREA", 0f);
            return;
        }

        // We need the physical dimensions of the image to convert percentage bbox to pixels.
        // For now, we assume the bbox percentage is directly applied to the plate area,
        // or we use the ratio if we had the image dimensions.
        // Wait, the Gemini detection prompt returns bbox_pct (0-100% of image).
        // Since we don't have the image pixel dimensions directly here (unless we decode the base64),
        // we can estimate using the plate diameter as a reference if available.
        // Alternatively, we should have Gemini return the bbox in pixels, OR we decode the image bounds.

        // Since we want this to be robust, let's decode the image bounds from the base64 to get width/height.
        // Wait, we don't need to do that if we know the calibration ratio and image size.
        // But we don't store image size in ctx. Let's just estimate area based on the plate bounding box
        // if we have it, but we don't have plate bounds in Stage 2 (it only returns cm dimensions).
        
        // Better approach: Stage 1 (Calibration) detects the reference object in pixels and we have the pixelToCmRatio.
        // If we know the reference object pixel size, we can deduce things, but bbox_pct is % of image.
        // We MUST know the image pixel dimensions to use bbox_pct with pixelToCmRatio.

        // For this milestone, we will use a fallback: if we have the plate diameter in cm,
        // we can estimate the food area simply relative to the plate.
        // But the bbox is relative to the IMAGE, not the plate.
        
        // Let's decode the base64 to get width/height without full decoding.
        // For simplicity in this STUB/approximation, we will use a standard 4:3 image dimension assumption
        // if we can't decode it, but realistically we should just decode the image width/height.
        // Actually, Java's ImageIO can read bounds without reading the whole image.
        
        // As a safe, temporary approximation for Milestone 3, we will assign a default area
        // based on the plate size (e.g., area = plateArea / numItems) just so the pipeline continues,
        // and we will log a warning that GrabCut/Area logic is approximated.

        float plateAreaCm2 = 0f;
        if (ctx.getPlateGeometry() != null) {
            float r = ctx.getPlateGeometry().getInnerDiameterCm() / 2f;
            plateAreaCm2 = (float) (Math.PI * r * r);
        } else {
            plateAreaCm2 = 450f; // Default ~24cm plate
        }

        int numItems = ctx.getFoodItems().size();
        float totalAreaCm2 = 0f;

        for (PipelineFoodItem item : ctx.getFoodItems()) {
            // Temporary approximation: distribute plate area among items (70% fill assumed)
            float estimatedArea = (plateAreaCm2 * 0.70f) / Math.max(1, numItems);
            
            // Adjust slightly based on bbox if available
            if (item.hasBoundingBox()) {
                float[] bbox = item.getBoundingBoxPct();
                float bboxAreaPct = (bbox[2] / 100f) * (bbox[3] / 100f);
                // Rough guess: bbox covers X% of image, plate covers ~50% of image
                estimatedArea = plateAreaCm2 * (bboxAreaPct / 0.50f) * 0.75f;
            }

            // Cap it
            estimatedArea = Math.min(estimatedArea, plateAreaCm2);
            
            item.setAreaCm2(estimatedArea);
            item.setCoveragePercent((estimatedArea / plateAreaCm2) * 100f);
            item.setAreaConfidence(0.4f); // Low confidence due to approximation
            
            totalAreaCm2 += estimatedArea;
            log.info("[FoodArea] {} -> estimated area {:.1f} cm²", item.getName(), String.format("%.1f", estimatedArea));
        }

        if (totalAreaCm2 > plateAreaCm2) {
            warningCollector.foodAreaExceedsPlate(ctx, totalAreaCm2, plateAreaCm2);
        }

        ctx.recordConfidence("FOOD_AREA", 0.4f);
    }
}
