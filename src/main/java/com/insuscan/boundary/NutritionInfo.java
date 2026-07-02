package com.insuscan.boundary;

/**
 * Nutrition data from USDA FoodData Central, including per-100g macros,
 * density (for volume-to-weight conversion), and serving size info.
 */

public class NutritionInfo {
    private String fdcId; // USDA food ID
    private String foodName; // official name
    private float carbsPer100g; // carbs per 100 grams
    private String servingSize; // e.g. "1 cup", "100g"
    private float servingSizeGrams;
    private boolean found; // did we find this food?

    // ── v2 Density fields ──
    private Float densityGPerCm3; // Calculated or looked-up density (g/cm³)
    private String densitySource; // USDA, LOOKUP, GEMINI, UNKNOWN
    private Float servingVolumeCm3; // Volume of a single serving (from USDA)

    private float fiberPer100g; 
    private float waterPer100g;
    private float proteinPer100g;
    private float fatPer100g;
    private float ashPer100g;

    public NutritionInfo() {
    }

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

    public float getFiberPer100g() {
        return fiberPer100g;
    }

    public void setFiberPer100g(float fiberPer100g) {
        this.fiberPer100g = fiberPer100g;
    }
    
    public float getWaterPer100g() {
        return waterPer100g;
    }

    public void setWaterPer100g(float waterPer100g) {
        this.waterPer100g = waterPer100g;
    }

    public float getProteinPer100g() {
        return proteinPer100g;
    }

    public void setProteinPer100g(float proteinPer100g) {
        this.proteinPer100g = proteinPer100g;
    }

    public float getFatPer100g() {
        return fatPer100g;
    }

    public void setFatPer100g(float fatPer100g) {
        this.fatPer100g = fatPer100g;
    }

    public float getAshPer100g() {
        return ashPer100g;
    }

    public void setAshPer100g(float ashPer100g) {
        this.ashPer100g = ashPer100g;
    }
    

    public float calculateCarbs(float weightGrams) {
        return (carbsPer100g * weightGrams) / 100f;
    }
    
    public Float calculateDensityFromProximates() {
        if (waterPer100g <= 0 && proteinPer100g <= 0 && fatPer100g <= 0) {
            return null;
        }

        float water   = waterPer100g   / 100f;
        float protein = proteinPer100g / 100f;
        float fat     = fatPer100g     / 100f;
        float carb    = carbsPer100g   / 100f;
        float ash     = ashPer100g > 0 ? ashPer100g / 100f : Math.max(0f, 1f - water - protein - fat - carb);

        float specificVolume = (water   / 1.000f)
                             + (protein / 1.330f)
                             + (fat     / 0.925f)
                             + (carb    / 1.600f)
                             + (ash     / 2.400f);

        if (specificVolume <= 0) {
            return null;
        }

        float density = 1f / specificVolume;

        if (density < 0.1f || density > 1.4f) {
            return null;
        }

        return density;
    }
    

    public static NutritionInfo notFound(String foodName) {
        NutritionInfo info = new NutritionInfo();
        info.setFoodName(foodName);
        info.setFound(false);
        return info;
    }
}