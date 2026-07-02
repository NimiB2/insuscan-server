package com.insuscan.boundary;

import java.util.List;
import java.util.ArrayList;

/**
 * Result from a food recognition service: detected foods, success flag,
 * and container-level analysis (fill percent, container type).
 */
public class FoodRecognitionResult {

    private List<RecognizedFoodItem> detectedFoods;
    private boolean success;
    private String errorMessage;

    private Float containerFillPercent;
    private String detectedContainerType;

    public FoodRecognitionResult() {
        this.detectedFoods = new ArrayList<>();
    }

    /**
     * A single food item recognized from an image, including its identity,
     * portion estimate, cooking state, safety flags, and location bbox.
     */
    public static class RecognizedFoodItem {
        private String name;
        private float confidence;
        private Float estimatedPortionGrams;

        private String baseIngredient;
        private String visualState;
        private List<String> riskFlags;
        private boolean requiresValidation;

        private Float coveragePercent;

        private String heightCategory;

        private String textureType;

        private List<String> usdaSearchTerms;

        private Float bboxXPct;
        private Float bboxYPct;
        private Float bboxWPct;
        private Float bboxHPct;

        public RecognizedFoodItem() {
            this.riskFlags = new ArrayList<>();
            this.usdaSearchTerms = new ArrayList<>();
        }

        public RecognizedFoodItem(String name, float confidence, Float estimatedPortionGrams) {
            this.name = name;
            this.confidence = confidence;
            this.estimatedPortionGrams = estimatedPortionGrams;
            this.riskFlags = new ArrayList<>();
            this.usdaSearchTerms = new ArrayList<>();
            this.visualState = "UNKNOWN";
            this.baseIngredient = name;
            this.requiresValidation = false;
        }

        public void setBoundingBox(Float xPct, Float yPct, Float wPct, Float hPct) {
            this.bboxXPct = xPct;
            this.bboxYPct = yPct;
            this.bboxWPct = wPct;
            this.bboxHPct = hPct;
        }

        public Float getBboxXPct() {
            return bboxXPct;
        }

        public Float getBboxYPct() {
            return bboxYPct;
        }

        public Float getBboxWPct() {
            return bboxWPct;
        }

        public Float getBboxHPct() {
            return bboxHPct;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public float getConfidence() {
            return confidence;
        }

        public void setConfidence(float confidence) {
            this.confidence = confidence;
        }

        public Float getEstimatedPortionGrams() {
            return estimatedPortionGrams;
        }

        public void setEstimatedPortionGrams(Float estimatedPortionGrams) {
            this.estimatedPortionGrams = estimatedPortionGrams;
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

        public List<String> getRiskFlags() {
            return riskFlags;
        }

        public void setRiskFlags(List<String> riskFlags) {
            this.riskFlags = (riskFlags != null) ? riskFlags : new ArrayList<>();
        }

        public boolean isRequiresValidation() {
            return requiresValidation;
        }

        public void setRequiresValidation(boolean requiresValidation) {
            this.requiresValidation = requiresValidation;
        }

        public Float getCoveragePercent() {
            return coveragePercent;
        }

        public void setCoveragePercent(Float coveragePercent) {
            this.coveragePercent = coveragePercent;
        }

        public String getHeightCategory() {
            return heightCategory;
        }

        public void setHeightCategory(String heightCategory) {
            this.heightCategory = heightCategory;
        }

        public String getTextureType() {
            return textureType;
        }

        public void setTextureType(String textureType) {
            this.textureType = textureType;
        }

        public List<String> getUsdaSearchTerms() {
            return usdaSearchTerms;
        }

        public void setUsdaSearchTerms(List<String> usdaSearchTerms) {
            this.usdaSearchTerms = (usdaSearchTerms != null) ? usdaSearchTerms : new ArrayList<>();
        }
    }

    public List<RecognizedFoodItem> getDetectedFoods() {
        return detectedFoods;
    }

    public void setDetectedFoods(List<RecognizedFoodItem> detectedFoods) {
        this.detectedFoods = (detectedFoods != null) ? detectedFoods : new ArrayList<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Float getContainerFillPercent() {
        return containerFillPercent;
    }

    public void setContainerFillPercent(Float containerFillPercent) {
        this.containerFillPercent = containerFillPercent;
    }

    public String getDetectedContainerType() {
        return detectedContainerType;
    }

    public void setDetectedContainerType(String detectedContainerType) {
        this.detectedContainerType = detectedContainerType;
    }

    public static FoodRecognitionResult success(List<RecognizedFoodItem> foods) {
        FoodRecognitionResult result = new FoodRecognitionResult();
        result.setSuccess(true);
        result.setDetectedFoods(foods);
        return result;
    }

    public static FoodRecognitionResult failure(String error) {
        FoodRecognitionResult result = new FoodRecognitionResult();
        result.setSuccess(false);
        result.setErrorMessage(error);
        result.setDetectedFoods(new ArrayList<>());
        return result;
    }
}