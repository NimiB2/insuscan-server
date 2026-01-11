package com.insuscan.boundary;

// Individual food item detected in a meal
public class FoodItemBoundary {
    private String name;
    private String nameHebrew;
    private Float quantity;         // grams
    private Float carbs;            // grams
    private Float confidence;       // 0.0 to 1.0
    private String usdaFdcId;       // USDA FoodData Central ID
    
    public FoodItemBoundary() {}

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameHebrew() {
        return nameHebrew;
    }

    public void setNameHebrew(String nameHebrew) {
        this.nameHebrew = nameHebrew;
    }

    public Float getQuantity() {
        return quantity;
    }

    public void setQuantity(Float quantity) {
        this.quantity = quantity;
    }

    public Float getCarbs() {
        return carbs;
    }

    public void setCarbs(Float carbs) {
        this.carbs = carbs;
    }

    public Float getConfidence() {
        return confidence;
    }

    public void setConfidence(Float confidence) {
        this.confidence = confidence;
    }

    public String getUsdaFdcId() {
        return usdaFdcId;
    }

    public void setUsdaFdcId(String usdaFdcId) {
        this.usdaFdcId = usdaFdcId;
    }

    @Override
    public String toString() {
        return "FoodItemBoundary{" +
                "name='" + name + '\'' +
                ", quantity=" + quantity +
                ", carbs=" + carbs +
                '}';
    }
}
