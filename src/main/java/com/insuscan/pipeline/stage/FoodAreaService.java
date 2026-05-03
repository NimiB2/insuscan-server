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

			if (item.hasBoundingBox()) {
				float[] bbox = item.getBoundingBoxPct();
				
				log.info("[FoodArea] {} bbox raw: w={}% h={}% -> widthCm={} heightCm={}",
					    item.getName(),
					    String.format("%.1f", bbox[2]),
					    String.format("%.1f", bbox[3]),
					    String.format("%.2f", (bbox[2] / 100f) * imageWidthPx * ratio),
					    String.format("%.2f", (bbox[3] / 100f) * imageHeightPx * ratio));
				
				float widthCm = (bbox[2] / 100f) * imageWidthPx * ratio;
				float heightCm = (bbox[3] / 100f) * imageHeightPx * ratio;
				estimatedArea = widthCm * heightCm * 0.75f;
			} else {
				estimatedArea = (plateAreaCm2 * 0.70f) / Math.max(1, ctx.getFoodItems().size());
				log.warn("[FoodArea] {} has no bbox, using plate fallback", item.getName());
			}

			estimatedArea = Math.min(estimatedArea, plateAreaCm2);

			item.setAreaCm2(estimatedArea);
			item.setCoveragePercent((estimatedArea / plateAreaCm2) * 100f);
			item.setAreaConfidence(0.7f);

			totalAreaCm2 += estimatedArea;
			log.info("[FoodArea] {} -> area={} cm², coverage={}%", item.getName(), String.format("%.1f", estimatedArea),
					String.format("%.1f", item.getCoveragePercent()));
		}

		if (totalAreaCm2 > plateAreaCm2) {
			warningCollector.foodAreaExceedsPlate(ctx, totalAreaCm2, plateAreaCm2);
		}

		ctx.recordConfidence("FOOD_AREA", 0.7f);
	}

}
