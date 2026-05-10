package com.insuscan.pipeline.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single food item that accumulates data as it passes through pipeline stages
 * 3–9.
 *
 * <p>
 * Each stage is responsible for filling only its own fields. No stage should
 * read fields written by a later stage.
 * </p>
 *
 * <p>
 * Stage responsibilities:
 * </p>
 * <ul>
 * <li>Stage 3 (FoodDetection): name, baseIngredient, visualState,
 * boundingBoxPct, searchTerms, detectionConfidence, requiresUserValidation,
 * effectiveDensityFactor</li>
 * <li>Stage 4 (FoodArea): areaCm2, coveragePercent, areaConfidence</li>
 * <li>Stage 5 (FoodHeight): effectiveHeightCm, heightConfidence,
 * heightWarnings</li>
 * <li>Stage 6 (VolumeCalculator): volumeCm3</li>
 * <li>Stage 7 (NutritionAndDensity): nutritionPer100g, densityGPerCm3,
 * densitySource, foundInUsda, usdaItemId</li>
 * <li>Stage 8 (WeightCalculator): weightG, weightWarnings</li>
 * <li>Stage 9 (NutritionAggregator): carbsG, netCarbsG, proteinG, fatG,
 * fiberG</li>
 * </ul>
 */
public class PipelineFoodItem {

	/** Density source tells the client how reliable the density calculation is. */
	public enum DensitySource {
		USDA_SERVING, // Computed from USDA portion with known volume (most accurate)
		PROXIMATE_CALC, // Computed via Choi-Okos formula from macros
		PROXIMATE_WITH_TEXTURE, // Choi-Okos × effectiveDensityFactor from model
		UNKNOWN // No density available — item requires manual weight input
	}

	// ── Stage 3: Detection ────────────────────────────────────────────────

	private String name; // e.g. "Grilled Chicken Breast"
	private String baseIngredient; // e.g. "chicken" — for USDA search
	private String visualState; // RAW | COOKED | FRIED | GRILLED | BAKED | BOILED
	private float[] boundingBoxPct; // [xPct, yPct, wPct, hPct] — 0–100
	private List<String> searchTerms = new ArrayList<>(); // ordered specific→generic
	private float detectionConfidence;
	private boolean requiresUserValidation;

	/**
	 * Effective density factor (0.3–1.0) estimated by the vision model. Represents
	 * the fraction of the visual volume that is actual food (vs. air). Used only in
	 * DensitySource.PROXIMATE_WITH_TEXTURE. Example: bread ≈ 0.65, leafy salad ≈
	 * 0.35, solid chicken ≈ 1.0
	 */
	private Float effectiveDensityFactor;

	// ── Stage 3.5: SAM Segmentation ───────────────────────────────────────────

	private Integer maskPixelCount;
	private Integer imagePixelCount;
	
	private float[] boundingBoxSidePct;
	private Integer maskSidePixelCount;
	private Integer imageSidePixelCount;

	// ── Stage 4: Area ─────────────────────────────────────────────────────

	private Float areaCm2;
	private Float coveragePercent; // areaCm2 / plateAreaCm2 × 100
	private Float areaConfidence;

	// ── Stage 5: Height ───────────────────────────────────────────────────

	private Float effectiveHeightCm;
	private Float heightConfidence;
	private List<String> heightWarnings = new ArrayList<>();

	// ── Stage 6: Volume ───────────────────────────────────────────────────

	private Float volumeCm3; // = areaCm2 × effectiveHeightCm

	// ── Stage 7: Nutrition & Density ──────────────────────────────────────

	private Float carbsPer100g;
	private Float proteinPer100g;
	private Float fatPer100g;
	private Float fiberPer100g;
	private Float waterPer100g;
	private Float densityGPerCm3;
	private DensitySource densitySource;
	private boolean foundInUsda;
	private String usdaItemId;

	// ── Stage 8: Weight ───────────────────────────────────────────────────

	private Float weightG; // = volumeCm3 × densityGPerCm3
	private List<String> weightWarnings = new ArrayList<>();

	// ── Stage 9: Nutrition totals for this item ───────────────────────────

	private Float carbsG;
	private Float netCarbsG; // = carbsG - fiberG (what impacts blood sugar)
	private Float proteinG;
	private Float fatG;
	private Float fiberG;

	// ── Helpers ───────────────────────────────────────────────────────────

	public boolean hasDensity() {
		return densityGPerCm3 != null && densityGPerCm3 > 0 && densitySource != DensitySource.UNKNOWN;
	}

	public boolean canCalculateWeight() {
		return volumeCm3 != null && hasDensity();
	}

	public boolean hasBoundingBox() {
		return boundingBoxPct != null && boundingBoxPct.length == 4;
	}

	// ── Getters & Setters ─────────────────────────────────────────────────

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBaseIngredient() {
		return baseIngredient;
	}

	public void setBaseIngredient(String baseIngredient) {
		this.baseIngredient = baseIngredient;
	}

	public String getVisualState() {
		return visualState;
	}

	public void setVisualState(String visualState) {
		this.visualState = visualState;
	}

	public float[] getBoundingBoxPct() {
		return boundingBoxPct;
	}

	public void setBoundingBoxPct(float[] boundingBoxPct) {
		this.boundingBoxPct = boundingBoxPct;
	}

	public List<String> getSearchTerms() {
		return searchTerms;
	}

	public void setSearchTerms(List<String> searchTerms) {
		this.searchTerms = searchTerms;
	}

	public float getDetectionConfidence() {
		return detectionConfidence;
	}

	public void setDetectionConfidence(float detectionConfidence) {
		this.detectionConfidence = detectionConfidence;
	}

	public boolean isRequiresUserValidation() {
		return requiresUserValidation;
	}

	public void setRequiresUserValidation(boolean requiresUserValidation) {
		this.requiresUserValidation = requiresUserValidation;
	}

	public Float getEffectiveDensityFactor() {
		return effectiveDensityFactor;
	}

	public void setEffectiveDensityFactor(Float effectiveDensityFactor) {
		this.effectiveDensityFactor = effectiveDensityFactor;
	}

	public Float getAreaCm2() {
		return areaCm2;
	}

	public void setAreaCm2(Float areaCm2) {
		this.areaCm2 = areaCm2;
	}

	public Float getCoveragePercent() {
		return coveragePercent;
	}

	public void setCoveragePercent(Float coveragePercent) {
		this.coveragePercent = coveragePercent;
	}

	public Float getAreaConfidence() {
		return areaConfidence;
	}

	public void setAreaConfidence(Float areaConfidence) {
		this.areaConfidence = areaConfidence;
	}

	public Float getEffectiveHeightCm() {
		return effectiveHeightCm;
	}

	public void setEffectiveHeightCm(Float effectiveHeightCm) {
		this.effectiveHeightCm = effectiveHeightCm;
	}

	public Float getHeightConfidence() {
		return heightConfidence;
	}

	public void setHeightConfidence(Float heightConfidence) {
		this.heightConfidence = heightConfidence;
	}

	public List<String> getHeightWarnings() {
		return heightWarnings;
	}

	public void setHeightWarnings(List<String> heightWarnings) {
		this.heightWarnings = heightWarnings;
	}

	public Float getVolumeCm3() {
		return volumeCm3;
	}

	public void setVolumeCm3(Float volumeCm3) {
		this.volumeCm3 = volumeCm3;
	}

	public Float getCarbsPer100g() {
		return carbsPer100g;
	}

	public void setCarbsPer100g(Float carbsPer100g) {
		this.carbsPer100g = carbsPer100g;
	}

	public Float getProteinPer100g() {
		return proteinPer100g;
	}

	public void setProteinPer100g(Float proteinPer100g) {
		this.proteinPer100g = proteinPer100g;
	}

	public Float getFatPer100g() {
		return fatPer100g;
	}

	public void setFatPer100g(Float fatPer100g) {
		this.fatPer100g = fatPer100g;
	}

	public Float getFiberPer100g() {
		return fiberPer100g;
	}

	public void setFiberPer100g(Float fiberPer100g) {
		this.fiberPer100g = fiberPer100g;
	}

	public Float getWaterPer100g() {
		return waterPer100g;
	}

	public void setWaterPer100g(Float waterPer100g) {
		this.waterPer100g = waterPer100g;
	}

	public Float getDensityGPerCm3() {
		return densityGPerCm3;
	}

	public void setDensityGPerCm3(Float densityGPerCm3) {
		this.densityGPerCm3 = densityGPerCm3;
	}

	public DensitySource getDensitySource() {
		return densitySource;
	}

	public void setDensitySource(DensitySource densitySource) {
		this.densitySource = densitySource;
	}

	public boolean isFoundInUsda() {
		return foundInUsda;
	}

	public void setFoundInUsda(boolean foundInUsda) {
		this.foundInUsda = foundInUsda;
	}

	public String getUsdaItemId() {
		return usdaItemId;
	}

	public void setUsdaItemId(String usdaItemId) {
		this.usdaItemId = usdaItemId;
	}

	public Float getWeightG() {
		return weightG;
	}

	public void setWeightG(Float weightG) {
		this.weightG = weightG;
	}

	public List<String> getWeightWarnings() {
		return weightWarnings;
	}

	public void setWeightWarnings(List<String> weightWarnings) {
		this.weightWarnings = weightWarnings;
	}

	public Float getCarbsG() {
		return carbsG;
	}

	public void setCarbsG(Float carbsG) {
		this.carbsG = carbsG;
	}

	public Float getNetCarbsG() {
		return netCarbsG;
	}

	public void setNetCarbsG(Float netCarbsG) {
		this.netCarbsG = netCarbsG;
	}

	public Float getProteinG() {
		return proteinG;
	}

	public void setProteinG(Float proteinG) {
		this.proteinG = proteinG;
	}

	public Float getFatG() {
		return fatG;
	}

	public void setFatG(Float fatG) {
		this.fatG = fatG;
	}

	public Float getFiberG() {
		return fiberG;
	}

	public void setFiberG(Float fiberG) {
		this.fiberG = fiberG;
	}

	@Override
	public String toString() {
		return "PipelineFoodItem{name='" + name + "', weight=" + weightG + "g, netCarbs=" + netCarbsG + "g}";
	}

	public Integer getMaskPixelCount() {
		return maskPixelCount;
	}

	public void setMaskPixelCount(Integer v) {
		this.maskPixelCount = v;
	}

	public Integer getImagePixelCount() {
		return imagePixelCount;
	}

	public void setImagePixelCount(Integer v) {
		this.imagePixelCount = v;
	}
	
	public float[] getBoundingBoxSidePct() { return boundingBoxSidePct; }
	public void setBoundingBoxSidePct(float[] v) { this.boundingBoxSidePct = v; }
	public boolean hasSideBoundingBox() { return boundingBoxSidePct != null && boundingBoxSidePct.length == 4; }
	public Integer getMaskSidePixelCount() { return maskSidePixelCount; }
	public void setMaskSidePixelCount(Integer v) { this.maskSidePixelCount = v; }
	public Integer getImageSidePixelCount() { return imageSidePixelCount; }
	public void setImageSidePixelCount(Integer v) { this.imageSidePixelCount = v; }
}
