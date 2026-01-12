package com.insuscan.boundary;

import java.util.List;

// Result from a food recognition service 
public class FoodRecognitionResult {

    private List<RecognizedFoodItem> detectedFoods;
    private boolean success;
    private String errorMessage;

    public FoodRecognitionResult() {
        this.detectedFoods = List.of(); // avoid nulls
    }

    // Recognized food item from the recognition service
    public static class RecognizedFoodItem {
        private String name;      // food name (English)
        private float confidence; // 0.0 to 1.0

        public RecognizedFoodItem() {}

        public RecognizedFoodItem(String name, float confidence) {
            this.name = name;
            this.confidence = confidence;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public float getConfidence() { return confidence; }
        public void setConfidence(float confidence) { this.confidence = confidence; }
    }

    public List<RecognizedFoodItem> getDetectedFoods() { return detectedFoods; }
    public void setDetectedFoods(List<RecognizedFoodItem> detectedFoods) {
        this.detectedFoods = (detectedFoods != null) ? detectedFoods : List.of();
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
        result.setDetectedFoods(List.of()); // never null
        return result;
    }
}