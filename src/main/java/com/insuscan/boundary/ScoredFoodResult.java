package com.insuscan.boundary;

public class ScoredFoodResult extends NutritionInfo {
    
    private Integer relevanceScore;
    private String matchReason;
    
    // Constructors
    public ScoredFoodResult() {
        super();
    }
    
    public ScoredFoodResult(NutritionInfo nutritionInfo, Integer relevanceScore, String matchReason) {
        // Copy fields from NutritionInfo
        this.setFound(nutritionInfo.isFound());
        this.setFdcId(nutritionInfo.getFdcId());
        this.setFoodName(nutritionInfo.getFoodName());
        this.setCarbsPer100g(nutritionInfo.getCarbsPer100g());
        this.setServingSize(nutritionInfo.getServingSize());
        this.setServingSizeGrams(nutritionInfo.getServingSizeGrams());
        
        // Set scoring fields
        this.relevanceScore = relevanceScore;
        this.matchReason = matchReason;
    }
    
    // Getters and Setters
    public Integer getRelevanceScore() {
        return relevanceScore;
    }
    
    public void setRelevanceScore(Integer relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
    
    public String getMatchReason() {
        return matchReason;
    }
    
    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }
}
