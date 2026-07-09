package com.insuscan.pipeline.stage;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineWarning;
import com.insuscan.pipeline.stage.calibration.CalibrationFallbackResolver;
import com.insuscan.pipeline.stage.calibration.CvDetectionResult;
import com.insuscan.pipeline.stage.calibration.GeminiCalibrationDetector;
import com.insuscan.pipeline.stage.calibration.GeminiCalibrationDetector.DetectionResult;
import com.insuscan.pipeline.stage.calibration.ReferenceObjectCvDetector;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.pipeline.support.ReferenceObjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stage 1 — Calibrates pixel-to-cm scale from the reference object in the top and side images.
 * Delegates CV detection to {@link ReferenceObjectCvDetector} and Gemini detection to {@link GeminiCalibrationDetector}.
 * When both detectors succeed, cross-checks their ratios and rejects the CV result if they disagree by more than the configured gap.
 */
@Service
public class CalibrationService {

	private static final Logger log = LoggerFactory.getLogger(CalibrationService.class);

	private final ReferenceObjectRegistry referenceObjectRegistry;
	private final PipelineWarningCollector warningCollector;
	private final CalibrationFallbackResolver fallbackResolver;
	private final ReferenceObjectCvDetector cvDetector;
	private final GeminiCalibrationDetector geminiDetector;

	@Value("${calibration.crosscheck.max-gap:0.25}")
	private float crosscheckMaxGap;

	@Value("${calibration.cv.min-confidence:0.5}")
	private float cvMinConfidence;

	public CalibrationService(ReferenceObjectRegistry referenceObjectRegistry,
			PipelineWarningCollector warningCollector,
			CalibrationFallbackResolver fallbackResolver,
			ReferenceObjectCvDetector cvDetector,
			GeminiCalibrationDetector geminiDetector) {
		this.referenceObjectRegistry = referenceObjectRegistry;
		this.warningCollector = warningCollector;
		this.fallbackResolver = fallbackResolver;
		this.cvDetector = cvDetector;
		this.geminiDetector = geminiDetector;
	}

	public void calibrate(PipelineContext ctx) {
	    String selectedType = ctx.getReferenceObjectType();
	    ReferenceObjectRegistry.Dimensions dims =
	        referenceObjectRegistry.getDimensions(selectedType);
	    boolean noneSelected = dims == null;

	    DetectionResult topResult  = detectWithCvFirst(ctx, ctx.getImageTopBase64(), dims, selectedType, true);
	    DetectionResult sideResult = detectWithCvFirst(ctx, ctx.getImageSideBase64(), dims, selectedType, false);

	    CalibrationFallbackResolver.FallbackResolution topResolution  = fallbackResolver.resolve(topResult, "top", selectedType);
	    CalibrationFallbackResolver.FallbackResolution sideResolution = fallbackResolver.resolve(sideResult, "side", selectedType);

	    applySideCalibration(ctx, sideResult, sideResolution);
	    applyTopCalibration(ctx, topResult, topResolution, noneSelected);

	    ctx.setUseStandardPlateFallback(false);

	    float confidence = (topResolution.confidence() + sideResolution.confidence()) / 2f;
	    ctx.recordConfidence("CALIBRATION", confidence);

	    String topRatioStr = ctx.getPixelToCmRatioTop() == null
	        ? "none" : String.format("%.4f", ctx.getPixelToCmRatioTop());
	    log.info("[Calibration] top={}({}) side={}({}) confidence={}",
	        topRatioStr, topResolution.source(),
	        String.format("%.4f", sideResolution.pixelToCmRatio()), sideResolution.source(),
	        String.format("%.2f", confidence));
	}

	private void applyTopCalibration(PipelineContext ctx, DetectionResult topResult,
			CalibrationFallbackResolver.FallbackResolution topResolution, boolean noneSelected) {

		switch (topResolution.source()) {
			case OBJECT_FOUND -> {
				ctx.setPixelToCmRatioTop(topResolution.pixelToCmRatio());
				if (topResult.found()) ctx.setReferenceBoundsTopPx(topResult.bboxPx());
			}
			case ALTERNATIVE_OBJECT_DETECTED -> {
				ctx.setPixelToCmRatioTop(topResolution.pixelToCmRatio());
				warningCollector.alternativeObjectUsed(ctx, "top",
						topResolution.selectedType(), topResolution.detectedObjectType());
			}
			case PLATE_SIZE_ESTIMATE -> {
				ctx.setPixelToCmRatioTop(null);
				if (noneSelected) warningCollector.noReferenceObjectEstimate(ctx);
				else warningCollector.referenceObjectNotMeasured(ctx);
			}
		}
	}

	private void applySideCalibration(PipelineContext ctx, DetectionResult sideResult,
			CalibrationFallbackResolver.FallbackResolution sideResolution) {

		ctx.setPixelToCmRatioSide(sideResolution.pixelToCmRatio());
		if (sideResult.found()) ctx.setReferenceBoundsSidePx(sideResult.bboxPx());

		if (sideResolution.source() == CalibrationFallbackResolver.FallbackSource.ALTERNATIVE_OBJECT_DETECTED) {
			warningCollector.alternativeObjectUsed(ctx, "side",
					sideResolution.selectedType(), sideResolution.detectedObjectType());
		} else if (sideResolution.source() == CalibrationFallbackResolver.FallbackSource.PLATE_SIZE_ESTIMATE) {
			warningCollector.usingPlateSizeEstimate(ctx, "side", sideResolution.selectedType());
		}
	}

	private DetectionResult detectWithCvFirst(PipelineContext ctx, String base64Image,
			ReferenceObjectRegistry.Dimensions dims, String refType, boolean isTop) {

		String viewTag = isTop ? "TOP" : "SIDE";

		if (base64Image == null || base64Image.isBlank()) {
			return DetectionResult.notFound("No image provided");
		}

		if (dims == null) {
			return isTop ? geminiDetector.detectTop(base64Image, null, refType)
					: geminiDetector.detectSide(base64Image, null, refType);
		}

		ReferenceObjectCvDetector.DetectionMode mode =
				ReferenceObjectCvDetector.DetectionMode.fromReferenceType(refType);

		CvDetectionResult cvResult =
				cvDetector.detect(base64Image, mode, dims.lengthCm(), dims.widthCm());

		if (cvResult.isFound() && cvResult.getConfidence() >= cvMinConfidence) {
			log.info("[Calibration][{}] CV detection succeeded: ratio={} conf={}",
					viewTag, String.format("%.5f", cvResult.getPixelToCmRatio()),
					String.format("%.2f", cvResult.getConfidence()));
			return toDetectionResult(cvResult, refType);
		}

		String cvReason = cvResult.isFound()
				? String.format("candidate below confidence gate (conf=%.2f < min=%.2f)",
						cvResult.getConfidence(), cvMinConfidence)
				: "detection failed (" + cvResult.getFailureReason() + ")";
		log.info("[Calibration][{}] CV {}, falling back to Gemini", viewTag, cvReason);

		DetectionResult geminiResult = isTop
				? geminiDetector.detectTop(base64Image, dims, refType)
				: geminiDetector.detectSide(base64Image, dims, refType);

		if (!geminiResult.found() || geminiResult.bboxPx() == null) {
			return geminiResult;
		}

		CvDetectionResult regionResult =
				cvDetector.detectInRegion(base64Image, mode, dims.lengthCm(), dims.widthCm(),
						geminiResult.bboxPx());

		if (regionResult.isFound() && regionResult.getConfidence() >= cvMinConfidence) {
			DetectionResult crossChecked = crossCheck(ctx, regionResult, geminiResult, refType, viewTag);
			if (crossChecked != null) {
				return crossChecked;
			}
		} else {
			log.info("[Calibration][{}] Region CV failed", viewTag);
		}

		return handleUnmeasuredReference(isTop, geminiResult, viewTag);
	}

	private DetectionResult handleUnmeasuredReference(boolean isTop, DetectionResult geminiResult, String viewTag) {
		if (isTop) {
			log.info("[Calibration][TOP] No trustworthy reference measurement — discarding Gemini bbox ratio, switching to no-reference estimate");
			return DetectionResult.notFound("Reference object present but not reliably measured");
		}
		log.info("[Calibration][{}] Keeping Gemini result", viewTag);
		return geminiResult;
	}

	private DetectionResult crossCheck(PipelineContext ctx,
			CvDetectionResult cvResult,
			DetectionResult geminiResult, String refType, String viewTag) {

		float cvRatio = cvResult.getPixelToCmRatio();
		float geminiRatio = geminiResult.pixelToCmRatio();
		float gap = Math.abs(cvRatio - geminiRatio) / Math.max(cvRatio, geminiRatio);

		if (gap > crosscheckMaxGap) {
			log.warn("[Calibration][{}] Cross-check disagreement (gap={}%), cv={} gemini={} — rejecting CV",
					viewTag, String.format("%.0f", gap * 100),
					String.format("%.5f", cvRatio), String.format("%.5f", geminiRatio));
			warningCollector.add(ctx, PipelineWarning.medium(
					"CALIBRATION", "CALIBRATION_CROSSCHECK_GAP",
					String.format("Calibration sources disagree by %.0f%% in %s image",
							gap * 100, viewTag.toLowerCase())));
			return null;
		}

		float confidence = Math.max(cvResult.getConfidence(), geminiResult.confidence());
		log.info("[Calibration][{}] Cross-check agreed (gap={}%), cv={} gemini={}",
				viewTag, String.format("%.0f", gap * 100),
				String.format("%.5f", cvRatio), String.format("%.5f", geminiRatio));

		return new DetectionResult(true, toBboxArray(cvResult), cvRatio, confidence,
				null, refType, null);
	}

	private DetectionResult toDetectionResult(CvDetectionResult cvResult, String refType) {
		return new DetectionResult(true, toBboxArray(cvResult),
				cvResult.getPixelToCmRatio(), cvResult.getConfidence(), null, refType, null);
	}

	private float[] toBboxArray(CvDetectionResult cvResult) {
		return new float[] {
				cvResult.getBoundingBox().x,
				cvResult.getBoundingBox().y,
				cvResult.getBoundingBox().width,
				cvResult.getBoundingBox().height };
	}
}