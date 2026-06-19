package com.insuscan.pipeline.model;

import com.insuscan.enums.ReferenceObjectDimensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.insuscan.boundary.ArcoreDepthData;

/**
 * Shared mutable context that flows through all pipeline stages.
 *
 * <p>Each stage reads what it needs and writes its own outputs.
 * No stage should read outputs of a later stage.</p>
 *
 * <p>Field groups (by stage that fills them):</p>
 * <ul>
 *   <li>Input (pre-pipeline): imageTop, imageSide, referenceObjectType</li>
 *   <li>Stage 1 — Calibration: pixelToCmRatioTop, pixelToCmRatioSide, referenceBoundsTop/Side</li>
 *   <li>Stage 2 — PlateGeometry: plateGeometry</li>
 *   <li>Stage 3 — FoodDetection: foodItems (partially filled)</li>
 *   <li>Stage 4 — FoodArea: foodItems[].areaCm2</li>
 *   <li>Stage 5 — FoodHeight: foodItems[].effectiveHeightCm</li>
 *   <li>Stage 6 — Volume: foodItems[].volumeCm3</li>
 *   <li>Stage 7 — NutritionAndDensity: foodItems[].nutritionPer100g, densityGPerCm3</li>
 *   <li>Stage 8 — Weight: foodItems[].weightG</li>
 *   <li>Stage 9 — Aggregation: mealTotals</li>
 *   <li>Stage 10 — SanityCheck: warnings (appended)</li>
 * </ul>
 */
public class PipelineContext {

    // ── Input ──────────────────────────────────────────────────────────────

    private String imageTopBase64;
    private String imageSideBase64;
    private String referenceObjectType;  // e.g. "INSULIN_PEN", "CARD"
    private String userId;
    private String systemId;
    private ArcoreDepthData arcoreDepthData;

    private Integer imageTopWidthPx;
    private Integer imageTopHeightPx;
    private Integer imageSideWidthPx;
    private Integer imageSideHeightPx;

    // ── Stage 1: Calibration ───────────────────────────────────────────────

    /** Pixels per centimeter, derived from reference object in top image. */
    private Float pixelToCmRatioTop;

    /** Pixels per centimeter, derived from reference object in side image. */
    private Float pixelToCmRatioSide;

    /** Bounding box of the reference object in the top image [x, y, w, h] as pixel coords. */
    private float[] referenceBoundsTopPx;

    /** Bounding box of the reference object in the side image [x, y, w, h] as pixel coords. */
    private float[] referenceBoundsSidePx;

    /** Bounding box of the plate in the top image [x, y, w, h] as pixel coords, if found. */
    private float[] plateBoundsTopPx;
    
    /** Plate circle in the top image as pixel coords [cx, cy, r], if measured. */
    private float[] plateCircleTopPx;
    
    /** True when calibration fell back to standard plate estimate in BOTH images.
     *  Signals later stages (notably PlateGeometryService) to override Gemini's
     *  plate dimensions with the standard plate config. */
    private boolean useStandardPlateFallback = false;

    // ── Stage 2: Plate Geometry ────────────────────────────────────────────

    private PlateGeometry plateGeometry;

 // ── Stage 2.5: Perspective Correction ─────────────────────────────────────

    private boolean perspectiveCorrectionApplied;
    private Float perspectiveTiltDegrees;
    
    // ── Stages 3–9: Food Items (accumulated) ──────────────────────────────

    private List<PipelineFoodItem> foodItems = new ArrayList<>();

    // ── Stage 9: Meal-level totals ─────────────────────────────────────────

    private MealTotals mealTotals;

    // ── Cross-cutting: Warnings & Metrics ─────────────────────────────────

    private final List<PipelineWarning> warnings = new ArrayList<>();
    private final Map<String, Long> stepTimingsMs = new HashMap<>();
    private final Map<String, Float> stepConfidences = new HashMap<>();

    // ── Nested: PlateGeometry ──────────────────────────────────────────────

    public static class PlateGeometry {
        private float innerDiameterCm;
        private float innerDepthCm;
        private String containerType;   // FLAT_PLATE | REGULAR_BOWL | DEEP_BOWL
        private float fillPercent;
        private float diameterConfidence;
        private float depthConfidence;
        private String reasoningNotes;  // From model — for logging only

        public PlateGeometry() {}

        public float getInnerDiameterCm() { return innerDiameterCm; }
        public void setInnerDiameterCm(float v) { this.innerDiameterCm = v; }
        public float getInnerDepthCm() { return innerDepthCm; }
        public void setInnerDepthCm(float v) { this.innerDepthCm = v; }
        public String getContainerType() { return containerType; }
        public void setContainerType(String v) { this.containerType = v; }
        public float getFillPercent() { return fillPercent; }
        public void setFillPercent(float v) { this.fillPercent = v; }
        public float getDiameterConfidence() { return diameterConfidence; }
        public void setDiameterConfidence(float v) { this.diameterConfidence = v; }
        public float getDepthConfidence() { return depthConfidence; }
        public void setDepthConfidence(float v) { this.depthConfidence = v; }
        public String getReasoningNotes() { return reasoningNotes; }
        public void setReasoningNotes(String v) { this.reasoningNotes = v; }
    }

    // ── Nested: MealTotals ─────────────────────────────────────────────────

    public static class MealTotals {
        private float totalWeightG;
        private float totalNetCarbsG;
        private float totalCarbsG;
        private float totalProteinG;
        private float totalFatG;
        private float totalFiberG;
        private int itemsFound;
        private int itemsNotInUsda;
        private int itemsRequiringValidation;

        public float getTotalWeightG() { return totalWeightG; }
        public void setTotalWeightG(float v) { this.totalWeightG = v; }
        public float getTotalNetCarbsG() { return totalNetCarbsG; }
        public void setTotalNetCarbsG(float v) { this.totalNetCarbsG = v; }
        public float getTotalCarbsG() { return totalCarbsG; }
        public void setTotalCarbsG(float v) { this.totalCarbsG = v; }
        public float getTotalProteinG() { return totalProteinG; }
        public void setTotalProteinG(float v) { this.totalProteinG = v; }
        public float getTotalFatG() { return totalFatG; }
        public void setTotalFatG(float v) { this.totalFatG = v; }
        public float getTotalFiberG() { return totalFiberG; }
        public void setTotalFiberG(float v) { this.totalFiberG = v; }
        public int getItemsFound() { return itemsFound; }
        public void setItemsFound(int v) { this.itemsFound = v; }
        public int getItemsNotInUsda() { return itemsNotInUsda; }
        public void setItemsNotInUsda(int v) { this.itemsNotInUsda = v; }
        public int getItemsRequiringValidation() { return itemsRequiringValidation; }
        public void setItemsRequiringValidation(int v) { this.itemsRequiringValidation = v; }
    }

    // ── Convenience helpers ────────────────────────────────────────────────

    public void addWarning(PipelineWarning warning) {
        warnings.add(warning);
    }

    public void recordTiming(String stage, long ms) {
        stepTimingsMs.put(stage, ms);
    }

    public void recordConfidence(String stage, float confidence) {
        stepConfidences.put(stage, confidence);
    }

    public ReferenceObjectDimensions getReferenceObjectDimensions() {
        return ReferenceObjectDimensions.fromServerValue(referenceObjectType);
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public String getImageTopBase64() { return imageTopBase64; }
    public void setImageTopBase64(String v) { this.imageTopBase64 = v; }
    public String getImageSideBase64() { return imageSideBase64; }
    public void setImageSideBase64(String v) { this.imageSideBase64 = v; }
    public String getReferenceObjectType() { return referenceObjectType; }
    public void setReferenceObjectType(String v) { this.referenceObjectType = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
    public String getSystemId() { return systemId; }
    public void setSystemId(String v) { this.systemId = v; }
    
    public ArcoreDepthData getArcoreDepthData() { return arcoreDepthData; }
    public void setArcoreDepthData(ArcoreDepthData v) { this.arcoreDepthData = v; }

    public Integer getImageTopWidthPx() { return imageTopWidthPx; }
    public void setImageTopWidthPx(Integer v) { this.imageTopWidthPx = v; }
    public Integer getImageTopHeightPx() { return imageTopHeightPx; }
    public void setImageTopHeightPx(Integer v) { this.imageTopHeightPx = v; }
    public Integer getImageSideWidthPx() { return imageSideWidthPx; }
    public void setImageSideWidthPx(Integer v) { this.imageSideWidthPx = v; }
    public Integer getImageSideHeightPx() { return imageSideHeightPx; }
    public void setImageSideHeightPx(Integer v) { this.imageSideHeightPx = v; }

    public Float getPixelToCmRatioTop() { return pixelToCmRatioTop; }
    public void setPixelToCmRatioTop(Float v) { this.pixelToCmRatioTop = v; }
    public Float getPixelToCmRatioSide() { return pixelToCmRatioSide; }
    public void setPixelToCmRatioSide(Float v) { this.pixelToCmRatioSide = v; }

    public boolean isUseStandardPlateFallback() { return useStandardPlateFallback; }
    public void setUseStandardPlateFallback(boolean v) { this.useStandardPlateFallback = v; }
    
    public float[] getReferenceBoundsTopPx() { return referenceBoundsTopPx; }
    public void setReferenceBoundsTopPx(float[] v) { this.referenceBoundsTopPx = v; }
    public float[] getReferenceBoundsSidePx() { return referenceBoundsSidePx; }
    public void setReferenceBoundsSidePx(float[] v) { this.referenceBoundsSidePx = v; }
    public float[] getPlateBoundsTopPx() { return plateBoundsTopPx; }
    public void setPlateBoundsTopPx(float[] v) { this.plateBoundsTopPx = v; }
    public float[] getPlateCircleTopPx() { return plateCircleTopPx; }
    public void setPlateCircleTopPx(float[] v) { this.plateCircleTopPx = v; }

    public PlateGeometry getPlateGeometry() { return plateGeometry; }
    public void setPlateGeometry(PlateGeometry v) { this.plateGeometry = v; }

    public List<PipelineFoodItem> getFoodItems() { return foodItems; }
    public void setFoodItems(List<PipelineFoodItem> v) { this.foodItems = v; }

    public MealTotals getMealTotals() { return mealTotals; }
    public void setMealTotals(MealTotals v) { this.mealTotals = v; }

    public List<PipelineWarning> getWarnings() { return warnings; }
    public Map<String, Long> getStepTimingsMs() { return stepTimingsMs; }
    public Map<String, Float> getStepConfidences() { return stepConfidences; }
    
    public boolean isPerspectiveCorrectionApplied() { return perspectiveCorrectionApplied; }
    public void setPerspectiveCorrectionApplied(boolean v) { this.perspectiveCorrectionApplied = v; }
    public Float getPerspectiveTiltDegrees() { return perspectiveTiltDegrees; }
    public void setPerspectiveTiltDegrees(Float v) { this.perspectiveTiltDegrees = v; }
}
