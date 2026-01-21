package com.insuscan.boundary;

import java.util.List;
import java.util.ArrayList;

// Result from a food recognition service 
public class FoodRecognitionResult {

    private List<RecognizedFoodItem> detectedFoods;
    private boolean success;
    private String errorMessage;

    public FoodRecognitionResult() {
        this.detectedFoods = new ArrayList<>(); // avoid nulls
    }

    // --- Inner Class: The Medical-Grade Food Item ---
    public static class RecognizedFoodItem {
        // --- Existing Fields ---
        private String name;              // Full visual description (e.g., "Roasted Potato Wedges")
        private float confidence;         // 0.0 to 1.0
        private Float estimatedPortionGrams; 

        // --- New Medical/Safety Fields ---
        private String baseIngredient;    // Clean name for DB search (e.g., "Potato")
        private String visualState;       // Cooking state: RAW, BOILED, ROASTED, FRIED
        private List<String> riskFlags;   // Warnings: "HIDDEN_SUGAR", "HIGH_FAT", "SAUCE_DETECTED"
        private boolean requiresValidation; // True if AI is unsure or detects high risk

        public RecognizedFoodItem() {
            this.riskFlags = new ArrayList<>();
        }

        public RecognizedFoodItem(String name, float confidence, Float estimatedPortionGrams) {
            this.name = name;
            this.confidence = confidence;
            this.estimatedPortionGrams = estimatedPortionGrams;
            this.riskFlags = new ArrayList<>();
            this.visualState = "UNKNOWN";
            this.baseIngredient = name; // Default fallback
            this.requiresValidation = false;
        }

        // --- Getters & Setters ---

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public float getConfidence() { return confidence; }
        public void setConfidence(float confidence) { this.confidence = confidence; }

        public Float getEstimatedPortionGrams() { return estimatedPortionGrams; }
        public void setEstimatedPortionGrams(Float estimatedPortionGrams) { 
            this.estimatedPortionGrams = estimatedPortionGrams; 
        }

        public String getBaseIngredient() { return baseIngredient; }
        public void setBaseIngredient(String baseIngredient) { this.baseIngredient = baseIngredient; }

        public String getVisualState() { return visualState; }
        public void setVisualState(String visualState) { this.visualState = visualState; }

        public List<String> getRiskFlags() { return riskFlags; }
        public void setRiskFlags(List<String> riskFlags) { 
            this.riskFlags = (riskFlags != null) ? riskFlags : new ArrayList<>(); 
        }

        public boolean isRequiresValidation() { return requiresValidation; }
        public void setRequiresValidation(boolean requiresValidation) { this.requiresValidation = requiresValidation; }
    }
    // --- End of Inner Class ---

    // --- Outer Class Methods (Factory & Accessors) ---

    public List<RecognizedFoodItem> getDetectedFoods() { return detectedFoods; }
    public void setDetectedFoods(List<RecognizedFoodItem> detectedFoods) {
        this.detectedFoods = (detectedFoods != null) ? detectedFoods : new ArrayList<>();
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    // Factory methods
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
        result.setDetectedFoods(new ArrayList<>()); // never null
        return result;
    }
}