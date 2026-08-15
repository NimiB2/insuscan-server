package com.insuscan.pipeline.model;

import com.insuscan.enums.ReferenceObjectDimensions;
import com.insuscan.boundary.ArcoreDepthData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private String sessionId;
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

    // ── Stage 2.5: Perspective Correction ──────────────────────────────────

    private boolean perspectiveCorrectionApplied;
    private Float perspectiveTiltDegrees;

    // ── Stages 3–9: Food Items (accumulated) ───────────────────────────────

    private List<PipelineFoodItem> foodItems = new ArrayList<>();

    // ── Stage 9: Meal-level totals ─────────────────────────────────────────

    private MealTotals mealTotals;

    // ── Cross-cutting: Warnings & Metrics ──────────────────────────────────

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
        private String reasoningNotes;  // From model - for logging only

        public PlateGeometry() {}

        public float getInnerDiameterCm() { return innerDiameterCm; }
        public void setInnerDiameterCm(float innerDiameterCm) { this.innerDiameterCm = innerDiameterCm; }
        public float getInnerDepthCm() { return innerDepthCm; }
        public void setInnerDepthCm(float innerDepthCm) { this.innerDepthCm = innerDepthCm; }
        public String getContainerType() { return containerType; }
        public void setContainerType(String containerType) { this.containerType = containerType; }
        public float getFillPercent() { return fillPercent; }
        public void setFillPercent(float fillPercent) { this.fillPercent = fillPercent; }
        public float getDiameterConfidence() { return diameterConfidence; }
        public void setDiameterConfidence(float diameterConfidence) { this.diameterConfidence = diameterConfidence; }
        public float getDepthConfidence() { return depthConfidence; }
        public void setDepthConfidence(float depthConfidence) { this.depthConfidence = depthConfidence; }
        public String getReasoningNotes() { return reasoningNotes; }
        public void setReasoningNotes(String reasoningNotes) { this.reasoningNotes = reasoningNotes; }
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
        public void setTotalWeightG(float totalWeightG) { this.totalWeightG = totalWeightG; }
        public float getTotalNetCarbsG() { return totalNetCarbsG; }
        public void setTotalNetCarbsG(float totalNetCarbsG) { this.totalNetCarbsG = totalNetCarbsG; }
        public float getTotalCarbsG() { return totalCarbsG; }
        public void setTotalCarbsG(float totalCarbsG) { this.totalCarbsG = totalCarbsG; }
        public float getTotalProteinG() { return totalProteinG; }
        public void setTotalProteinG(float totalProteinG) { this.totalProteinG = totalProteinG; }
        public float getTotalFatG() { return totalFatG; }
        public void setTotalFatG(float totalFatG) { this.totalFatG = totalFatG; }
        public float getTotalFiberG() { return totalFiberG; }
        public void setTotalFiberG(float totalFiberG) { this.totalFiberG = totalFiberG; }
        public int getItemsFound() { return itemsFound; }
        public void setItemsFound(int itemsFound) { this.itemsFound = itemsFound; }
        public int getItemsNotInUsda() { return itemsNotInUsda; }
        public void setItemsNotInUsda(int itemsNotInUsda) { this.itemsNotInUsda = itemsNotInUsda; }
        public int getItemsRequiringValidation() { return itemsRequiringValidation; }
        public void setItemsRequiringValidation(int itemsRequiringValidation) { this.itemsRequiringValidation = itemsRequiringValidation; }
    }

    // ── Convenience helpers ────────────────────────────────────────────────

    public void addWarning(PipelineWarning warning) { warnings.add(warning); }
    public void recordTiming(String stage, long ms) { stepTimingsMs.put(stage, ms); }
    public void recordConfidence(String stage, float confidence) { stepConfidences.put(stage, confidence); }
    public ReferenceObjectDimensions getReferenceObjectDimensions() { return ReferenceObjectDimensions.fromServerValue(referenceObjectType); }

    // ── Input — Getters & Setters ──────────────────────────────────────────

    public String getImageTopBase64() { return imageTopBase64; }
    public void setImageTopBase64(String imageTopBase64) { this.imageTopBase64 = imageTopBase64; }
    public String getImageSideBase64() { return imageSideBase64; }
    public void setImageSideBase64(String imageSideBase64) { this.imageSideBase64 = imageSideBase64; }
    public String getReferenceObjectType() { return referenceObjectType; }
    public void setReferenceObjectType(String referenceObjectType) { this.referenceObjectType = referenceObjectType; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSystemId() { return systemId; }
    public void setSystemId(String systemId) { this.systemId = systemId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public ArcoreDepthData getArcoreDepthData() { return arcoreDepthData; }
    public void setArcoreDepthData(ArcoreDepthData arcoreDepthData) { this.arcoreDepthData = arcoreDepthData; }
    public Integer getImageTopWidthPx() { return imageTopWidthPx; }
    public void setImageTopWidthPx(Integer imageTopWidthPx) { this.imageTopWidthPx = imageTopWidthPx; }
    public Integer getImageTopHeightPx() { return imageTopHeightPx; }
    public void setImageTopHeightPx(Integer imageTopHeightPx) { this.imageTopHeightPx = imageTopHeightPx; }
    public Integer getImageSideWidthPx() { return imageSideWidthPx; }
    public void setImageSideWidthPx(Integer imageSideWidthPx) { this.imageSideWidthPx = imageSideWidthPx; }
    public Integer getImageSideHeightPx() { return imageSideHeightPx; }
    public void setImageSideHeightPx(Integer imageSideHeightPx) { this.imageSideHeightPx = imageSideHeightPx; }

    // ── Stage 1: Calibration — Getters & Setters ───────────────────────────

    public Float getPixelToCmRatioTop() { return pixelToCmRatioTop; }
    public void setPixelToCmRatioTop(Float pixelToCmRatioTop) { this.pixelToCmRatioTop = pixelToCmRatioTop; }
    public Float getPixelToCmRatioSide() { return pixelToCmRatioSide; }
    public void setPixelToCmRatioSide(Float pixelToCmRatioSide) { this.pixelToCmRatioSide = pixelToCmRatioSide; }
    public float[] getReferenceBoundsTopPx() { return referenceBoundsTopPx; }
    public void setReferenceBoundsTopPx(float[] referenceBoundsTopPx) { this.referenceBoundsTopPx = referenceBoundsTopPx; }
    public float[] getReferenceBoundsSidePx() { return referenceBoundsSidePx; }
    public void setReferenceBoundsSidePx(float[] referenceBoundsSidePx) { this.referenceBoundsSidePx = referenceBoundsSidePx; }
    public float[] getPlateBoundsTopPx() { return plateBoundsTopPx; }
    public void setPlateBoundsTopPx(float[] plateBoundsTopPx) { this.plateBoundsTopPx = plateBoundsTopPx; }
    public float[] getPlateCircleTopPx() { return plateCircleTopPx; }
    public void setPlateCircleTopPx(float[] plateCircleTopPx) { this.plateCircleTopPx = plateCircleTopPx; }
    public boolean isUseStandardPlateFallback() { return useStandardPlateFallback; }
    public void setUseStandardPlateFallback(boolean useStandardPlateFallback) { this.useStandardPlateFallback = useStandardPlateFallback; }

    // ── Stage 2: Plate Geometry — Getters & Setters ────────────────────────

    public PlateGeometry getPlateGeometry() { return plateGeometry; }
    public void setPlateGeometry(PlateGeometry plateGeometry) { this.plateGeometry = plateGeometry; }

    // ── Stage 2.5: Perspective Correction — Getters & Setters ──────────────

    public boolean isPerspectiveCorrectionApplied() { return perspectiveCorrectionApplied; }
    public void setPerspectiveCorrectionApplied(boolean perspectiveCorrectionApplied) { this.perspectiveCorrectionApplied = perspectiveCorrectionApplied; }
    public Float getPerspectiveTiltDegrees() { return perspectiveTiltDegrees; }
    public void setPerspectiveTiltDegrees(Float perspectiveTiltDegrees) { this.perspectiveTiltDegrees = perspectiveTiltDegrees; }

    // ── Stages 3–9: Food Items — Getters & Setters ─────────────────────────

    public List<PipelineFoodItem> getFoodItems() { return foodItems; }
    public void setFoodItems(List<PipelineFoodItem> foodItems) { this.foodItems = foodItems; }

    // ── Stage 9: Meal-level totals — Getters & Setters ─────────────────────

    public MealTotals getMealTotals() { return mealTotals; }
    public void setMealTotals(MealTotals mealTotals) { this.mealTotals = mealTotals; }

    // ── Cross-cutting: Warnings & Metrics — Getters ────────────────────────

    public List<PipelineWarning> getWarnings() { return warnings; }
    public Map<String, Long> getStepTimingsMs() { return stepTimingsMs; }
    public Map<String, Float> getStepConfidences() { return stepConfidences; }
}