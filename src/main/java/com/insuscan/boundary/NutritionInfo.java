package com.insuscan.boundary;

//Nutrition data from USDA FoodData Central
public class NutritionInfo {
    private String fdcId; // USDA food ID
    private String foodName; // official name
    private float carbsPer100g; // carbs per 100 grams
    private String servingSize; // e.g. "1 cup", "100g"
    private float servingSizeGrams;
    private boolean found; // did we find this food?

    // ── v2 Density fields ──
    private Float densityGPerCm3; // Calculated or looked-up density (g/cm³)
    private String densitySource; // USDA, LOOKUP, GPT, UNKNOWN
    private Float servingVolumeCm3; // Volume of a single serving (from USDA)

    public NutritionInfo() {
    }

    // Getters and Setters
    public String getFdcId() {
        return fdcId;
    }

    public void setFdcId(String fdcId) {
        this.fdcId = fdcId;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public float getCarbsPer100g() {
        return carbsPer100g;
    }

    public void setCarbsPer100g(float carbsPer100g) {
        this.carbsPer100g = carbsPer100g;
    }

    public String getServingSize() {
        return servingSize;
    }

    public void setServingSize(String servingSize) {
        this.servingSize = servingSize;
    }

    public float getServingSizeGrams() {
        return servingSizeGrams;
    }

    public void setServingSizeGrams(float servingSizeGrams) {
        this.servingSizeGrams = servingSizeGrams;
    }

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    // ── v2 Density Getters/Setters ──
    public Float getDensityGPerCm3() {
        return densityGPerCm3;
    }

    public void setDensityGPerCm3(Float densityGPerCm3) {
        this.densityGPerCm3 = densityGPerCm3;
    }

    public String getDensitySource() {
        return densitySource;
    }

    public void setDensitySource(String densitySource) {
        this.densitySource = densitySource;
    }

    public Float getServingVolumeCm3() {
        return servingVolumeCm3;
    }

    public void setServingVolumeCm3(Float servingVolumeCm3) {
        this.servingVolumeCm3 = servingVolumeCm3;
    }

    // Calculate carbs for a specific weight
    public float calculateCarbs(float weightGrams) {
        return (carbsPer100g * weightGrams) / 100f;
    }

    // Factory for "not found" result
    public static NutritionInfo notFound(String foodName) {
        NutritionInfo info = new NutritionInfo();
        info.setFoodName(foodName);
        info.setFound(false);
        return info;
    }
}