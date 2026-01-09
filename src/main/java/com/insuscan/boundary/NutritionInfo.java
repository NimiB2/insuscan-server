package com.insuscan.boundary;

//Nutrition data from USDA FoodData Central
public class NutritionInfo {
 private String fdcId;              // USDA food ID
 private String foodName;           // official name
 private float carbsPer100g;        // carbs per 100 grams
 private float caloriesPer100g;
 private float proteinPer100g;
 private float fatPer100g;
 private String servingSize;        // e.g. "1 cup", "100g"
 private float servingSizeGrams;
 private boolean found;             // did we find this food?

 public NutritionInfo() {}

 // Getters and Setters
 public String getFdcId() { return fdcId; }
 public void setFdcId(String fdcId) { this.fdcId = fdcId; }
 
 public String getFoodName() { return foodName; }
 public void setFoodName(String foodName) { this.foodName = foodName; }
 
 public float getCarbsPer100g() { return carbsPer100g; }
 public void setCarbsPer100g(float carbsPer100g) { this.carbsPer100g = carbsPer100g; }
 
 public float getCaloriesPer100g() { return caloriesPer100g; }
 public void setCaloriesPer100g(float caloriesPer100g) { this.caloriesPer100g = caloriesPer100g; }
 
 public float getProteinPer100g() { return proteinPer100g; }
 public void setProteinPer100g(float proteinPer100g) { this.proteinPer100g = proteinPer100g; }
 
 public float getFatPer100g() { return fatPer100g; }
 public void setFatPer100g(float fatPer100g) { this.fatPer100g = fatPer100g; }
 
 public String getServingSize() { return servingSize; }
 public void setServingSize(String servingSize) { this.servingSize = servingSize; }
 
 public float getServingSizeGrams() { return servingSizeGrams; }
 public void setServingSizeGrams(float servingSizeGrams) { this.servingSizeGrams = servingSizeGrams; }
 
 public boolean isFound() { return found; }
 public void setFound(boolean found) { this.found = found; }

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