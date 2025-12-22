package com.insuscan.boundary;

// Meal identifier containing meal UUID and system ID
public class MealIdBoundary {
    private String mealId;
    private String systemId;
    
    public MealIdBoundary() {}
    
    public MealIdBoundary(String mealId, String systemId) {
        this.mealId = mealId;
        this.systemId = systemId;
    }

    public String getMealId() {
        return mealId;
    }

    public void setMealId(String mealId) {
        this.mealId = mealId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    @Override
    public String toString() {
        return "MealIdBoundary{mealId='" + mealId + "', systemId='" + systemId + "'}";
    }
}
