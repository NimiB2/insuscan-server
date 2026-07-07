package com.insuscan.pipeline.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The final result returned to the client after all pipeline stages complete.
 *
 * <p>Structure mirrors the three levels defined in food_estimation_pipeline.md §Stage 11:</p>
 * <ul>
 *   <li>Per-food item: all measurements and nutrition values</li>
 *   <li>Meal-level: aggregated totals and container info</li>
 *   <li>Meta: warnings, timings, calibration data</li>
 * </ul>
 */
public class PipelineResult {

    // ── Overall status ─────────────────────────────────────────────────────

    /** Whether the pipeline completed enough to produce actionable results. */
    private boolean success;

    /** If not success, the reason why the pipeline stopped. */
    private String failureReason;

    /** Overall confidence 0.0–1.0 aggregated from all stages. */
    private float overallConfidence;

    /**
     * Whether results are safe for automated insulin dose calculation.
     * False if any critical cross-validation failed or confidence < threshold.
     */
    private boolean safeForDosing;

    // ── Per-food items ─────────────────────────────────────────────────────

    private List<PipelineFoodItem> foodItems = new ArrayList<>();

    // ── Meal-level totals ──────────────────────────────────────────────────

    private Float totalWeightG;
    private Float totalNetCarbsG;
    private Float totalCarbsG;
    private Float totalProteinG;
    private Float totalFatG;

    // ── Container info ─────────────────────────────────────────────────────

    private String containerType;
    private Float plateDiameterCm;
    private Float plateDepthCm;
    private Float plateFillPercent;

    // ── Calibration info ───────────────────────────────────────────────────

    private Float pixelToCmRatioTop;
    private Float pixelToCmRatioSide;
    private boolean refObjectDetectedTop;
    private boolean refObjectDetectedSide;

    // ── Meta ───────────────────────────────────────────────────────────────

    private List<PipelineWarning> warnings = new ArrayList<>();
    private List<String> itemsRequiringValidation = new ArrayList<>();
    private Map<String, Long> stepTimingsMs;
    private Map<String, Float> stepConfidences;

    // ── Factories ──────────────────────────────────────────────────────────

    public static PipelineResult failure(String reason) {
        PipelineResult r = new PipelineResult();
        r.success = false;
        r.failureReason = reason;
        r.overallConfidence = 0f;
        r.safeForDosing = false;
        return r;
    }

    public static PipelineResult empty() {
        PipelineResult r = new PipelineResult();
        r.success = true;
        r.overallConfidence = 0f;
        r.safeForDosing = false;
        return r;
    }

    // ── Overall status — Getters & Setters ─────────────────────────────────

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public float getOverallConfidence() { return overallConfidence; }
    public void setOverallConfidence(float overallConfidence) { this.overallConfidence = overallConfidence; }
    public boolean isSafeForDosing() { return safeForDosing; }
    public void setSafeForDosing(boolean safeForDosing) { this.safeForDosing = safeForDosing; }

    // ── Per-food items — Getters & Setters ─────────────────────────────────

    public List<PipelineFoodItem> getFoodItems() { return foodItems; }
    public void setFoodItems(List<PipelineFoodItem> foodItems) { this.foodItems = foodItems; }

    // ── Meal-level totals — Getters & Setters ──────────────────────────────

    public Float getTotalWeightG() { return totalWeightG; }
    public void setTotalWeightG(Float totalWeightG) { this.totalWeightG = totalWeightG; }
    public Float getTotalNetCarbsG() { return totalNetCarbsG; }
    public void setTotalNetCarbsG(Float totalNetCarbsG) { this.totalNetCarbsG = totalNetCarbsG; }
    public Float getTotalCarbsG() { return totalCarbsG; }
    public void setTotalCarbsG(Float totalCarbsG) { this.totalCarbsG = totalCarbsG; }
    public Float getTotalProteinG() { return totalProteinG; }
    public void setTotalProteinG(Float totalProteinG) { this.totalProteinG = totalProteinG; }
    public Float getTotalFatG() { return totalFatG; }
    public void setTotalFatG(Float totalFatG) { this.totalFatG = totalFatG; }

    // ── Container info — Getters & Setters ─────────────────────────────────

    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }
    public Float getPlateDiameterCm() { return plateDiameterCm; }
    public void setPlateDiameterCm(Float plateDiameterCm) { this.plateDiameterCm = plateDiameterCm; }
    public Float getPlateDepthCm() { return plateDepthCm; }
    public void setPlateDepthCm(Float plateDepthCm) { this.plateDepthCm = plateDepthCm; }
    public Float getPlateFillPercent() { return plateFillPercent; }
    public void setPlateFillPercent(Float plateFillPercent) { this.plateFillPercent = plateFillPercent; }

    // ── Calibration info — Getters & Setters ───────────────────────────────

    public Float getPixelToCmRatioTop() { return pixelToCmRatioTop; }
    public void setPixelToCmRatioTop(Float pixelToCmRatioTop) { this.pixelToCmRatioTop = pixelToCmRatioTop; }
    public Float getPixelToCmRatioSide() { return pixelToCmRatioSide; }
    public void setPixelToCmRatioSide(Float pixelToCmRatioSide) { this.pixelToCmRatioSide = pixelToCmRatioSide; }
    public boolean isRefObjectDetectedTop() { return refObjectDetectedTop; }
    public void setRefObjectDetectedTop(boolean refObjectDetectedTop) { this.refObjectDetectedTop = refObjectDetectedTop; }
    public boolean isRefObjectDetectedSide() { return refObjectDetectedSide; }
    public void setRefObjectDetectedSide(boolean refObjectDetectedSide) { this.refObjectDetectedSide = refObjectDetectedSide; }

    // ── Meta — Getters & Setters ───────────────────────────────────────────

    public List<PipelineWarning> getWarnings() { return warnings; }
    public void setWarnings(List<PipelineWarning> warnings) { this.warnings = warnings; }
    public List<String> getItemsRequiringValidation() { return itemsRequiringValidation; }
    public void setItemsRequiringValidation(List<String> itemsRequiringValidation) { this.itemsRequiringValidation = itemsRequiringValidation; }
    public Map<String, Long> getStepTimingsMs() { return stepTimingsMs; }
    public void setStepTimingsMs(Map<String, Long> stepTimingsMs) { this.stepTimingsMs = stepTimingsMs; }
    public Map<String, Float> getStepConfidences() { return stepConfidences; }
    public void setStepConfidences(Map<String, Float> stepConfidences) { this.stepConfidences = stepConfidences; }
}