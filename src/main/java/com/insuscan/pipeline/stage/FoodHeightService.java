package com.insuscan.pipeline.stage;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.model.PipelineWarning;
import com.insuscan.pipeline.stage.height.HeightResponseParser;
import com.insuscan.pipeline.stage.height.HeightResult;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.util.GeminiApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * Stage 5 — Estimates the effective height of each food item using the side
 * image.
 *
 * <p>
 * Effective height represents the height of a perfect cylinder that would
 * contain the same volume as the food item, given its 2D surface area.
 * </p>
 */
@Service
public class FoodHeightService {

	private static final Logger log = LoggerFactory.getLogger(FoodHeightService.class);

	/** Default effective height (cm) used when no height data is available for an item. */
	private static final float DEFAULT_HEIGHT_CM = 2.5f;

	/** Height above this multiple of the plate depth triggers a "height exceeds depth" warning. */
	private static final float HEIGHT_EXCEEDS_DEPTH_MULTIPLIER = 1.5f;

	/** Confidence assigned when the model omitted an item from its response. */
	private static final float MODEL_MISS_CONFIDENCE = 0.1f;

	/** Confidence assigned when falling back to default heights entirely (no side calibration or empty response). */
	private static final float DEFAULT_HEIGHT_CONFIDENCE = 0.2f;

	private final GeminiApiClient geminiApiClient;
	private final PipelineWarningCollector warningCollector;
	private final HeightResponseParser responseParser;

	public FoodHeightService(GeminiApiClient geminiApiClient, PipelineWarningCollector warningCollector,
	        HeightResponseParser responseParser) {
	    this.geminiApiClient = geminiApiClient;
	    this.warningCollector = warningCollector;
	    this.responseParser = responseParser;
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
				Map<String, HeightResult> parsedHeights = responseParser.parse(response, foods);

				float totalConf = 0f;
				int count = 0;

				for (PipelineFoodItem item : foods) {
					HeightResult hr = parsedHeights.get(item.getName());
					if (hr != null) {
						item.setEffectiveHeightCm(hr.heightCm());
						item.setHeightConfidence(hr.confidence());

						if (!hr.isVisible()) {
							warningCollector.heightNotVisible(ctx, item.getName());
							// Fallback to plate depth if not visible
							float defaultH = ctx.getPlateGeometry() != null ? ctx.getPlateGeometry().getInnerDepthCm()
									: DEFAULT_HEIGHT_CM;
							item.setEffectiveHeightCm(defaultH);
						} else if (ctx.getPlateGeometry() != null
						        && hr.heightCm() > ctx.getPlateGeometry().getInnerDepthCm() * HEIGHT_EXCEEDS_DEPTH_MULTIPLIER) {
						    warningCollector.heightExceedsPlateDepth(ctx, item.getName(), hr.heightCm(),
						            ctx.getPlateGeometry().getInnerDepthCm());
						}

						totalConf += hr.confidence();
						count++;
						log.info("[FoodHeight] {} -> {}cm (conf: {})", item.getName(),
						        String.format("%.1f", item.getEffectiveHeightCm()),
						        String.format("%.2f", hr.confidence()));
					} else {
						log.warn("[FoodHeight] Model missed item: {}", item.getName());
						item.setEffectiveHeightCm(DEFAULT_HEIGHT_CM);
						item.setHeightConfidence(MODEL_MISS_CONFIDENCE);
						totalConf += MODEL_MISS_CONFIDENCE;
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
			warningCollector.add(ctx,
					PipelineWarning.high(PipelineWarningCollector.STAGE_FOOD_HEIGHT,
							"HEIGHT_ESTIMATION_FAILED", "Failed to estimate food heights: " + e.getMessage()));
			applyDefaultHeights(ctx, foods);
		}

		ctx.recordConfidence("FOOD_HEIGHT", confidence);
	}

	private void applyDefaultHeights(PipelineContext ctx, List<PipelineFoodItem> foods) {
		float defaultH = ctx.getPlateGeometry() != null ? ctx.getPlateGeometry().getInnerDepthCm() : DEFAULT_HEIGHT_CM;
		for (PipelineFoodItem item : foods) {
			item.setEffectiveHeightCm(defaultH);
			item.setHeightConfidence(DEFAULT_HEIGHT_CONFIDENCE);
		}
		ctx.recordConfidence("FOOD_HEIGHT", DEFAULT_HEIGHT_CONFIDENCE);
	}

	private String buildPrompt(PipelineContext ctx, List<PipelineFoodItem> foods) {
		String foodNamesList = foods.stream().map(PipelineFoodItem::getName)
				.collect(Collectors.joining("', '", "'", "'"));

		String plateContext = "";
		if (ctx.getPlateGeometry() != null) {
			plateContext = String.format("The container is a %s with a depth of %.1fcm.\n",
					ctx.getPlateGeometry().getContainerType(), ctx.getPlateGeometry().getInnerDepthCm());
		}

		return String.format("You are estimating the vertical height of food items from a side-angle view.\n\n"
				+ "Scale calibration for this side image: %.4f cm per pixel.\n" + "%s\n"
				+ "The following food items were detected from the top-down view:\n[%s]\n\n"
				+ "For EACH food item in the list, estimate its 'effective height' in cm.\n"
				+ "Effective height is the height of a perfect cylinder that would contain the same volume as the food item, given its 2D surface area.\n"
				+ "For flat/spread foods (like sauce or a thin layer of rice), this will be small (e.g. 1.0cm).\n"
				+ "For domed foods (like a chicken breast), imagine flattening it out over its footprint.\n\n"
				+ "CRITICAL: Return ONLY a valid JSON array. NO markdown, NO code fences, NO explanations. "
				+ "Start with [ and end with ]. Include ALL food items from the list above.\n"
				+ "Format:\n" + "[\n" + "  {\n"
				+ "    \"name\": \"<exact name from list>\",\n" + "    \"is_visible_in_side_view\": true,\n"
				+ "    \"effective_height_cm\": <float>,\n" + "    \"confidence\": <float 0.0-1.0>,\n"
				+ "    \"bbox_side_pct\": { \"x\": <float 0-100>, \"y\": <float 0-100>, \"w\": <float 0-100>, \"h\": <float 0-100> }\n"
				+ "  }\n" + "]", ctx.getPixelToCmRatioSide(), plateContext, foodNamesList);
	}	
}
