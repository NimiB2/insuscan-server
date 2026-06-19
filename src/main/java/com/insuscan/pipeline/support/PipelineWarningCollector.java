package com.insuscan.pipeline.support;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.insuscan.pipeline.support.StandardPlateConfig;

/**
 * Collects and manages warnings throughout the pipeline. Provides convenience
 * methods for common warning patterns.
 */
@Component
public class PipelineWarningCollector {

	private static final Logger log = LoggerFactory.getLogger(PipelineWarningCollector.class);

	// Stage name constants
	public static final String STAGE_CALIBRATION = "CALIBRATION";
	public static final String STAGE_PLATE_GEOMETRY = "PLATE_GEOMETRY";
	public static final String STAGE_FOOD_DETECTION = "FOOD_DETECTION";
	public static final String STAGE_FOOD_AREA = "FOOD_AREA";
	public static final String STAGE_FOOD_HEIGHT = "FOOD_HEIGHT";
	public static final String STAGE_VOLUME = "VOLUME";
	public static final String STAGE_NUTRITION = "NUTRITION";
	public static final String STAGE_WEIGHT = "WEIGHT";
	public static final String STAGE_AGGREGATION = "AGGREGATION";
	public static final String STAGE_SANITY_CHECK = "SANITY_CHECK";
	public static final String STAGE_PERSPECTIVE_CORRECTION = "PERSPECTIVE_CORRECTION";

	public void add(PipelineContext ctx, PipelineWarning warning) {
		ctx.addWarning(warning);
		log.warn("[Pipeline][{}] {} — {}", warning.getSourceStage(), warning.getCode(), warning.getMessage());
	}

	// ── Calibration warnings ──────────────────────────────────────────────

	public void calibrationFailed(PipelineContext ctx, String reason) {
		add(ctx, PipelineWarning.critical(STAGE_CALIBRATION, "CALIBRATION_FAILED",
				"Reference object not detected in both images. Cannot calibrate scale. " + reason));
	}

	public void referenceObjectNotFound(PipelineContext ctx, String imageView) {
		add(ctx, PipelineWarning.high(STAGE_CALIBRATION, "REF_NOT_FOUND_IN_" + imageView.toUpperCase(),
				"Reference object not detected in " + imageView + " image. Using ratio from other image."));
	}

	public void calibrationRatioMismatch(PipelineContext ctx, float ratioTop, float ratioSide, float gapPct) {
		add(ctx, PipelineWarning.high(STAGE_CALIBRATION, "RATIO_MISMATCH",
				String.format(
						"Calibration ratio gap %.1f%% (top=%.4f, side=%.4f). "
								+ "Images may have been taken from different distances.",
						gapPct, ratioTop, ratioSide)));
	}

	public void alternativeObjectUsed(PipelineContext ctx, String imageView, String selectedType, String detectedType) {
		boolean userSelectedNone = isNone(selectedType);
		String message = userSelectedNone
				? "No reference object was selected, but '" + detectedType + "' was detected in the " + imageView
						+ " image and is being used for calibration."
				: "Selected reference '" + selectedType + "' was not found in the " + imageView + " image. '"
						+ detectedType + "' was detected instead and is being used for calibration.";
		add(ctx, PipelineWarning.medium(STAGE_CALIBRATION, "ALTERNATIVE_OBJECT_USED_IN_" + imageView.toUpperCase(),
				message));
	}

	public void usingPlateSizeEstimate(PipelineContext ctx, String imageView, String selectedType) {
        boolean userSelectedNone = isNone(selectedType);
        String prefix = userSelectedNone
            ? "No reference object was selected and none was detected in the " + imageView + " image. "
            : "Selected reference '" + selectedType + "' was not found, and no alternative reference object " +
                "was detected in the " + imageView + " image. ";
        String message = prefix + "Calibration is based on a standard " + StandardPlateConfig.DIAMETER_CM +
            "cm plate size. Confidence is low — please review the results carefully.";
        add(ctx, PipelineWarning.high(
            STAGE_CALIBRATION,
            "PLATE_SIZE_ESTIMATE_IN_" + imageView.toUpperCase(),
            message
        ));
    }
	
	public void noReferenceObjectEstimate(PipelineContext ctx) {
		add(ctx, PipelineWarning.medium(STAGE_CALIBRATION, "NO_REFERENCE_OBJECT",
				"No reference object was selected. Plate size was estimated visually — please review the result."));
	}

	public void referenceObjectNotMeasured(PipelineContext ctx) {
		add(ctx, PipelineWarning.high(STAGE_CALIBRATION, "REFERENCE_OBJECT_NOT_MEASURED",
				"The selected reference object was not reliably detected in the photo. Plate size was estimated visually — consider retaking the photo with the reference object clearly visible."));
	}

	// ── Plate geometry warnings ───────────────────────────────────────────

	public void plateTypeDepthMismatch(PipelineContext ctx, String containerType, float depthCm) {
		add(ctx, PipelineWarning.medium(STAGE_PLATE_GEOMETRY, "TYPE_DEPTH_MISMATCH",
				String.format("Container type '%s' inconsistent with depth %.1fcm.", containerType, depthCm)));
	}

	// ── Area warnings ─────────────────────────────────────────────────────

	public void foodAreaExceedsPlate(PipelineContext ctx, float totalAreaCm2, float plateAreaCm2) {
		add(ctx, PipelineWarning.high(STAGE_FOOD_AREA, "AREA_EXCEEDS_PLATE",
				String.format("Total food area %.1fcm² > plate area %.1fcm². Calibration or segmentation may be wrong.",
						totalAreaCm2, plateAreaCm2)));
	}
	
	public void coverageMismatch(PipelineContext ctx, String foodName, float samCoverage, float geminiCoverage) {
		add(ctx, PipelineWarning.medium(STAGE_FOOD_AREA, "COVERAGE_MISMATCH",
				String.format(
						"'%s' coverage disagreement: SAM mask %.0f%% vs visual estimate %.0f%%. "
								+ "Segmentation may be inaccurate — please review.",
						foodName, samCoverage, geminiCoverage)));
	}

	// ── Height warnings ───────────────────────────────────────────────────

	public void heightExceedsPlateDepth(PipelineContext ctx, String foodName, float heightCm, float depthCm) {
		add(ctx, PipelineWarning.medium(STAGE_FOOD_HEIGHT, "HEIGHT_EXCEEDS_DEPTH",
				String.format(
						"'%s' effective height %.1fcm > plate depth %.1fcm. "
								+ "Food may be overflowing plate — this is not necessarily an error.",
						foodName, heightCm, depthCm)));
	}

	public void heightNotVisible(PipelineContext ctx, String foodName) {
		add(ctx, PipelineWarning.medium(STAGE_FOOD_HEIGHT, "HEIGHT_NOT_VISIBLE", String.format(
				"'%s' is not clearly visible in the side image. Height estimate may be inaccurate.", foodName)));
	}

	// ── Volume warnings ───────────────────────────────────────────────────

	public void totalVolumeExceedsContainer(PipelineContext ctx, float totalVol, float containerVol) {
		add(ctx, PipelineWarning.high(STAGE_VOLUME, "VOLUME_EXCEEDS_CONTAINER",
				String.format("Total food volume %.1fcm³ > container volume %.1fcm³. Results may be overestimated.",
						totalVol, containerVol)));
	}

	// ── Nutrition warnings ────────────────────────────────────────────────

	public void foodNotFoundInUsda(PipelineContext ctx, String foodName) {
		add(ctx, PipelineWarning.high(STAGE_NUTRITION, "FOOD_NOT_IN_USDA",
				String.format("'%s' not found in USDA database. Manual weight input required.", foodName)));
	}

	// ── Weight warnings ───────────────────────────────────────────────────

	public void weightOutOfRange(PipelineContext ctx, String foodName, float weightG) {
		add(ctx, PipelineWarning.medium(STAGE_WEIGHT, "WEIGHT_OUT_OF_RANGE",
				String.format("'%s' calculated weight %.1fg is outside plausible range [3g–500g]. Please review.",
						foodName, weightG)));
	}
	
	private static boolean isNone(String selectedType) {
        return selectedType == null || selectedType.isBlank() || "NONE".equalsIgnoreCase(selectedType);
    }

}
