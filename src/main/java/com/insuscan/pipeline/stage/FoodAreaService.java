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
 * <p>
 * Currently uses a bounding-box approximation (width × height × 0.75). When
 * GrabCut is fully integrated (either server-side via JavaCV or via a two-step
 * client API), this stage will use pixel-perfect segmentation.
 * </p>
 */
@Service
public class FoodAreaService {

	private static final Logger log = LoggerFactory.getLogger(FoodAreaService.class);
	private static final float SAM_AREA_CONFIDENCE = 0.7f;
	private static final float BBOX_AREA_CONFIDENCE = 0.5f;
	private static final float PLATE_FALLBACK_AREA_CONFIDENCE = 0.3f;

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

		if (ctx.getImageTopWidthPx() == null || ctx.getImageTopHeightPx() == null) {
			log.warn("[FoodArea] Missing image dimensions. Cannot calculate real-world areas.");
			ctx.getFoodItems().forEach(item -> item.setAreaCm2(null));
			ctx.recordConfidence("FOOD_AREA", 0f);
			return;
		}

		float plateAreaCm2 = 0f;
		if (ctx.getPlateGeometry() != null) {
			float r = ctx.getPlateGeometry().getInnerDiameterCm() / 2f;
			plateAreaCm2 = (float) (Math.PI * r * r);
		} else {
			plateAreaCm2 = 450f;
		}

		float ratio = ctx.getPixelToCmRatioTop();
		float imageWidthPx = ctx.getImageTopWidthPx();
		float imageHeightPx = ctx.getImageTopHeightPx();

		float totalAreaCm2 = 0f;

		for (PipelineFoodItem item : ctx.getFoodItems()) {
			float estimatedArea;
			
			log.info("[FoodArea] {} gemini_coverage={}%", 
				    item.getName(), 
				    item.getCoveragePercent() != null ? String.format("%.0f", item.getCoveragePercent()) : "N/A");

			if (item.getMaskPixelCount() != null && item.getImagePixelCount() != null && item.getImagePixelCount() > 0) {
			    float imageAreaCm2 = (imageWidthPx * ratio) * (imageHeightPx * ratio);
			    float maskRatio = (float) item.getMaskPixelCount() / item.getImagePixelCount();
			    estimatedArea = maskRatio * imageAreaCm2;
			    log.info("[FoodArea] {} using SAM mask: maskPixels={} / totalPixels={} -> area={}cm²",
			        item.getName(), item.getMaskPixelCount(), item.getImagePixelCount(),
			        String.format("%.1f", estimatedArea));
			} else if (item.hasBoundingBox()) {
			    float[] bbox = item.getBoundingBoxPct();
			    float widthCm = (bbox[2] / 100f) * imageWidthPx * ratio;
			    float heightCm = (bbox[3] / 100f) * imageHeightPx * ratio;
			    estimatedArea = widthCm * heightCm * 0.75f;
			    log.info("[FoodArea] {} using bbox fallback: area={}cm²",
			        item.getName(), String.format("%.1f", estimatedArea));
			} else {
			    estimatedArea = (plateAreaCm2 * 0.70f) / Math.max(1, ctx.getFoodItems().size());
			    log.warn("[FoodArea] {} has no SAM mask or bbox, using plate fallback", item.getName());
			}

			estimatedArea = Math.min(estimatedArea, plateAreaCm2);

			item.setAreaCm2(estimatedArea);
			item.setCoveragePercent((estimatedArea / plateAreaCm2) * 100f);
			item.setAreaConfidence(resolveAreaConfidence(item));

			totalAreaCm2 += estimatedArea;
			log.info("[FoodArea] {} -> area={} cm², coverage={}%", item.getName(), String.format("%.1f", estimatedArea),
					String.format("%.1f", item.getCoveragePercent()));
		}

		if (totalAreaCm2 > plateAreaCm2) {
			warningCollector.foodAreaExceedsPlate(ctx, totalAreaCm2, plateAreaCm2);
		}

		ctx.recordConfidence("FOOD_AREA", 0.7f);
	}
	
	private float resolveAreaConfidence(PipelineFoodItem item) {
		boolean usedSamMask = item.getMaskPixelCount() != null
				&& item.getImagePixelCount() != null
				&& item.getImagePixelCount() > 0;

		if (usedSamMask && item.getSamMaskScore() != null) {
			return item.getSamMaskScore();
		}
		if (usedSamMask) {
			return SAM_AREA_CONFIDENCE;
		}
		if (item.hasBoundingBox()) {
			return BBOX_AREA_CONFIDENCE;
		}
		return PLATE_FALLBACK_AREA_CONFIDENCE;
	}

}
