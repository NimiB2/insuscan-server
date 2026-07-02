package com.insuscan.boundary;

/**
 * Nutrition info enriched with a relevance score and a match reason,
 * used to rank food candidates returned from search.
 */
public class ScoredFoodResult extends NutritionInfo {

    private Integer relevanceScore;
    private String matchReason;
    private String displayName;

    public ScoredFoodResult(NutritionInfo nutritionInfo, Integer relevanceScore, String matchReason, String displayName) {
        this.setFound(nutritionInfo.isFound());
        this.setFdcId(nutritionInfo.getFdcId());
        this.setFoodName(nutritionInfo.getFoodName());
        this.setCarbsPer100g(nutritionInfo.getCarbsPer100g());
        this.setServingSize(nutritionInfo.getServingSize());
        this.setServingSizeGrams(nutritionInfo.getServingSizeGrams());

        this.relevanceScore = relevanceScore;
        this.matchReason = matchReason;
        this.displayName = displayName != null ? displayName : nutritionInfo.getFoodName();
    }

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

    public String getDisplayName() {
        return displayName != null ? displayName : getFoodName();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}