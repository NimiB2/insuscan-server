package com.insuscan.pipeline;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineResult;
import com.insuscan.pipeline.stage.ArcoreDepthFusionService;
import com.insuscan.pipeline.stage.CalibrationService;
import com.insuscan.pipeline.stage.PlateGeometryService;
import com.insuscan.pipeline.support.ConfidenceAggregator;
import com.insuscan.pipeline.support.ReferenceObjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates all 11 stages of the food estimation pipeline.
 *
 * <p>This class owns the pipeline flow only — no business logic.
 * Each step is delegated to a dedicated service.</p>
 *
 * <p>Failure policy (per work_plan.md decision #6):</p>
 * <ul>
 *   <li>FATAL failure at any stage → stop immediately, return error to client</li>
 *   <li>DEGRADED failure → continue with lower confidence, add warning</li>
 * </ul>
 *
 * <p>Current state: SKELETON — all stage methods are stubs returning empty results.
 * Each milestone fills in the real implementations.</p>
 */
@Service
public class FoodEstimationPipeline {

    private static final Logger log = LoggerFactory.getLogger(FoodEstimationPipeline.class);

    private final ConfidenceAggregator confidenceAggregator;
    private final ReferenceObjectRegistry referenceObjectRegistry;
    private final CalibrationService calibrationService;
    private final PlateGeometryService plateGeometryService;
    private final com.insuscan.pipeline.stage.FoodDetectionService foodDetectionService;
    private final com.insuscan.pipeline.stage.FoodAreaService foodAreaService;
    private final com.insuscan.pipeline.stage.FoodHeightService foodHeightService;
    private final com.insuscan.pipeline.calculation.VolumeCalculator volumeCalculator;
    private final com.insuscan.pipeline.stage.NutritionAndDensityService nutritionAndDensityService;
    private final com.insuscan.pipeline.calculation.WeightCalculator weightCalculator;
    private final com.insuscan.pipeline.calculation.NutritionAggregator nutritionAggregator;
    private final com.insuscan.pipeline.stage.SanityCheckService sanityCheckService;
    private final ArcoreDepthFusionService arcoreDepthFusionService;
    
    public FoodEstimationPipeline(ConfidenceAggregator confidenceAggregator,
                                   ReferenceObjectRegistry referenceObjectRegistry,
                                   CalibrationService calibrationService,
                                   PlateGeometryService plateGeometryService,
                                   com.insuscan.pipeline.stage.FoodDetectionService foodDetectionService,
                                   com.insuscan.pipeline.stage.FoodAreaService foodAreaService,
                                   com.insuscan.pipeline.stage.FoodHeightService foodHeightService,
                                   com.insuscan.pipeline.calculation.VolumeCalculator volumeCalculator,
                                   com.insuscan.pipeline.stage.NutritionAndDensityService nutritionAndDensityService,
                                   com.insuscan.pipeline.calculation.WeightCalculator weightCalculator,
                                   com.insuscan.pipeline.calculation.NutritionAggregator nutritionAggregator,
                                   com.insuscan.pipeline.stage.SanityCheckService sanityCheckService,                                
                                   ArcoreDepthFusionService arcoreDepthFusionService) {
        this.confidenceAggregator = confidenceAggregator;
        this.referenceObjectRegistry = referenceObjectRegistry;
        this.calibrationService = calibrationService;
        this.plateGeometryService = plateGeometryService;
        this.foodDetectionService = foodDetectionService;
        this.foodAreaService = foodAreaService;
        this.foodHeightService = foodHeightService;
        this.volumeCalculator = volumeCalculator;
        this.nutritionAndDensityService = nutritionAndDensityService;
        this.weightCalculator = weightCalculator;
        this.nutritionAggregator = nutritionAggregator;
        this.sanityCheckService = sanityCheckService;
        this.arcoreDepthFusionService = arcoreDepthFusionService;
    }

    /**
     * Main entry point. Runs all stages sequentially.
     *
     * @param ctx Pre-initialized context with input images and reference type.
     * @return PipelineResult — always non-null. Use {@link PipelineResult#isSuccess()} to check outcome.
     */
    public PipelineResult run(PipelineContext ctx) {
        log.info("[Pipeline] Starting for user={}, refType={}", ctx.getUserId(), ctx.getReferenceObjectType());

        // ── Pre-flight validation ──────────────────────────────────────────

        String validationError = validateInput(ctx);
        if (validationError != null) {
            log.warn("[Pipeline] Input validation failed: {}", validationError);
            return PipelineResult.failure(validationError);
        }

        // ── Validate reference object is supported ─────────────────────────

        if (!referenceObjectRegistry.isSupported(ctx.getReferenceObjectType())) {
            return PipelineResult.failure(
                "Unsupported reference object type: " + ctx.getReferenceObjectType() +
                ". Supported types: INSULIN_SYRINGE, CARD, SYRINGE_KNIFE"
            );
        }

        try {
            runStage1_Calibration(ctx);
            if (hasFatalWarning(ctx)) return buildResult(ctx, false, "Calibration failed");

            runStage2_PlateGeometry(ctx);
            if (hasFatalWarning(ctx)) return buildResult(ctx, false, "Plate geometry measurement failed");

            runStage3_FoodDetection(ctx);
            if (hasFatalWarning(ctx)) return buildResult(ctx, false, "Food detection failed");

            runStage4_FoodArea(ctx);
            runStage5_FoodHeight(ctx);
            runStageArcore_DepthFusion(ctx);
            runStage6_Volume(ctx);
            runStage7_NutritionAndDensity(ctx);
            runStage8_Weight(ctx);
            runStage9_Aggregation(ctx);
            runStage10_SanityCheck(ctx);

        } catch (Exception e) {
            log.error("[Pipeline] Unexpected error", e);
            return PipelineResult.failure("Internal pipeline error: " + e.getMessage());
        }

        log.info("[Pipeline] Completed successfully. Items={}, totalNetCarbs={}g",
            ctx.getFoodItems().size(),
            ctx.getMealTotals() != null ? ctx.getMealTotals().getTotalNetCarbsG() : "N/A");

        return buildResult(ctx, true, null);
    }

    // ── Stage stubs (replaced milestone by milestone) ──────────────────────

    /** Stage 1 — Milestone 2 */
    private void runStage1_Calibration(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        calibrationService.calibrate(ctx);
        ctx.recordTiming("CALIBRATION", System.currentTimeMillis() - start);
    }

    /** Stage 2 — Milestone 2 */
    private void runStage2_PlateGeometry(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        plateGeometryService.measurePlate(ctx);
        ctx.recordTiming("PLATE_GEOMETRY", System.currentTimeMillis() - start);
    }

    /** Stage 3 — Milestone 3 */
    private void runStage3_FoodDetection(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        foodDetectionService.detectFoods(ctx);
        ctx.recordTiming("FOOD_DETECTION", System.currentTimeMillis() - start);
    }

    /** Stage 4 — Milestone 3 */
    private void runStage4_FoodArea(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        foodAreaService.calculateAreas(ctx);
        ctx.recordTiming("FOOD_AREA", System.currentTimeMillis() - start);
    }

    /** Stage 5 — Milestone 3 */
    private void runStage5_FoodHeight(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        foodHeightService.estimateHeights(ctx);
        ctx.recordTiming("FOOD_HEIGHT", System.currentTimeMillis() - start);
    }
    
    private void runStageArcore_DepthFusion(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        arcoreDepthFusionService.fuseDepths(ctx);
        ctx.recordTiming("ARCORE_FUSION", System.currentTimeMillis() - start);
    }

    /** Stage 6 — Milestone 4 */
    private void runStage6_Volume(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        volumeCalculator.calculateVolumes(ctx);
        ctx.recordTiming("VOLUME", System.currentTimeMillis() - start);
    }

    /** Stage 7 — Milestone 5 */
    private void runStage7_NutritionAndDensity(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        nutritionAndDensityService.resolveNutritionAndDensity(ctx);
        ctx.recordTiming("NUTRITION", System.currentTimeMillis() - start);
    }

    /** Stage 8 — Milestone 4 */
    private void runStage8_Weight(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        weightCalculator.calculateWeights(ctx);
        ctx.recordTiming("WEIGHT", System.currentTimeMillis() - start);
    }

    /** Stage 9 — Milestone 6 */
    private void runStage9_Aggregation(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        nutritionAggregator.aggregate(ctx);
        ctx.recordTiming("AGGREGATION", System.currentTimeMillis() - start);
    }

    /** Stage 10 — Milestone 6 */
    private void runStage10_SanityCheck(PipelineContext ctx) {
        long start = System.currentTimeMillis();
        sanityCheckService.runSanityChecks(ctx);
        ctx.recordTiming("SANITY_CHECK", System.currentTimeMillis() - start);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String validateInput(PipelineContext ctx) {
        if (ctx.getImageTopBase64() == null || ctx.getImageTopBase64().isBlank()) {
            return "Top image is required";
        }
        if (ctx.getImageSideBase64() == null || ctx.getImageSideBase64().isBlank()) {
            return "Side image is required";
        }
        if (ctx.getReferenceObjectType() == null || ctx.getReferenceObjectType().isBlank()) {
            return "Reference object type is required";
        }
        return null;
    }

    private boolean hasFatalWarning(PipelineContext ctx) {
        // A FATAL StageResult surfaces as a CRITICAL warning added by the stage service
        return ctx.getWarnings().stream()
            .anyMatch(w -> w.getSeverity() == com.insuscan.pipeline.model.PipelineWarning.Severity.CRITICAL);
    }

    private PipelineResult buildResult(PipelineContext ctx, boolean success, String failureReason) {
        if (!success) {
            PipelineResult r = PipelineResult.failure(failureReason);
            r.setWarnings(ctx.getWarnings());
            r.setStepTimingsMs(ctx.getStepTimingsMs());
            r.setStepConfidences(ctx.getStepConfidences());
            return r;
        }

        float baseConfidence = confidenceAggregator.computeOverallConfidence(ctx);
        float finalConfidence = confidenceAggregator.applyWarningPenalties(baseConfidence, ctx);
        boolean safeForDosing = confidenceAggregator.isSafeForDosing(finalConfidence, ctx);

        PipelineResult result = new PipelineResult();
        result.setSuccess(true);
        result.setOverallConfidence(finalConfidence);
        result.setSafeForDosing(safeForDosing);
        result.setFoodItems(ctx.getFoodItems());
        result.setWarnings(ctx.getWarnings());
        result.setStepTimingsMs(ctx.getStepTimingsMs());
        result.setStepConfidences(ctx.getStepConfidences());
        result.setPixelToCmRatioTop(ctx.getPixelToCmRatioTop());
        result.setPixelToCmRatioSide(ctx.getPixelToCmRatioSide());
        result.setRefObjectDetectedTop(ctx.getPixelToCmRatioTop() != null);
        result.setRefObjectDetectedSide(ctx.getPixelToCmRatioSide() != null);

        if (ctx.getPlateGeometry() != null) {
            result.setContainerType(ctx.getPlateGeometry().getContainerType());
            result.setPlateDiameterCm(ctx.getPlateGeometry().getInnerDiameterCm());
            result.setPlateDepthCm(ctx.getPlateGeometry().getInnerDepthCm());
            result.setPlateFillPercent(ctx.getPlateGeometry().getFillPercent());
        }

        if (ctx.getMealTotals() != null) {
            result.setTotalWeightG(ctx.getMealTotals().getTotalWeightG());
            result.setTotalCarbsG(ctx.getMealTotals().getTotalCarbsG());
            result.setTotalNetCarbsG(ctx.getMealTotals().getTotalNetCarbsG());
            result.setTotalProteinG(ctx.getMealTotals().getTotalProteinG());
            result.setTotalFatG(ctx.getMealTotals().getTotalFatG());
        }

        // Items requiring validation
        ctx.getFoodItems().stream()
            .filter(item -> item.isRequiresUserValidation() || !item.isFoundInUsda())
            .map(item -> item.getName())
            .forEach(name -> result.getItemsRequiringValidation().add(name));

        return result;
    }
}
