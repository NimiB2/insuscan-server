package com.insuscan.boundary;

import com.insuscan.enums.MealStatus;

import java.util.Date;
import java.util.List;

// Complete meal data transfer object
public class MealBoundary {
    private MealIdBoundary mealId;
    private UserIdBoundary userId;
    private String imageUrl;
    
    // Detected food items
    private List<FoodItemBoundary> foodItems;
    
    // Nutrition totals
    private Float totalCarbs;
    
    // Portion analysis results
    private Float estimatedWeight;
    private Float plateVolumeCm3;
    private Float plateDiameterCm;
    private Float plateDepthCm;
    private Float analysisConfidence;
    private Boolean referenceDetected;
    
 // User state when meal was logged
    private Integer currentGlucose;
    private String activityLevel;
    
    // Calculation breakdown
    private Float carbDose;
    private Float correctionDose;
    private Float sickAdjustment;
    private Float stressAdjustment;
    private Float exerciseAdjustment;
    
    // Insulin data
    private Float recommendedDose;
    private Float actualDose;
    
    // Status and timestamps
    private MealStatus status;
    private Date scannedAt;
    private Date confirmedAt;
    private Date completedAt;

    public MealBoundary() {}

    // Getters and Setters
    public MealIdBoundary getMealId() {
        return mealId;
    }

    public void setMealId(MealIdBoundary mealId) {
        this.mealId = mealId;
    }

    public UserIdBoundary getUserId() {
        return userId;
    }

    public void setUserId(UserIdBoundary userId) {
        this.userId = userId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<FoodItemBoundary> getFoodItems() {
        return foodItems;
    }

    public void setFoodItems(List<FoodItemBoundary> foodItems) {
        this.foodItems = foodItems;
    }

    public Float getTotalCarbs() {
        return totalCarbs;
    }

    public void setTotalCarbs(Float totalCarbs) {
        this.totalCarbs = totalCarbs;
    }

    public Float getEstimatedWeight() {
        return estimatedWeight;
    }

    public void setEstimatedWeight(Float estimatedWeight) {
        this.estimatedWeight = estimatedWeight;
    }

    public Float getPlateVolumeCm3() {
        return plateVolumeCm3;
    }

    public void setPlateVolumeCm3(Float plateVolumeCm3) {
        this.plateVolumeCm3 = plateVolumeCm3;
    }

    public Float getPlateDiameterCm() {
        return plateDiameterCm;
    }

    public void setPlateDiameterCm(Float plateDiameterCm) {
        this.plateDiameterCm = plateDiameterCm;
    }

    public Float getPlateDepthCm() {
        return plateDepthCm;
    }

    public void setPlateDepthCm(Float plateDepthCm) {
        this.plateDepthCm = plateDepthCm;
    }

    public Float getAnalysisConfidence() {
        return analysisConfidence;
    }

    public void setAnalysisConfidence(Float analysisConfidence) {
        this.analysisConfidence = analysisConfidence;
    }

    public Boolean getReferenceDetected() {
        return referenceDetected;
    }

    public void setReferenceDetected(Boolean referenceDetected) {
        this.referenceDetected = referenceDetected;
    }

    public Float getRecommendedDose() {
        return recommendedDose;
    }

    public void setRecommendedDose(Float recommendedDose) {
        this.recommendedDose = recommendedDose;
    }

    public Float getActualDose() {
        return actualDose;
    }

    public void setActualDose(Float actualDose) {
        this.actualDose = actualDose;
    }

    public MealStatus getStatus() {
        return status;
    }

    public void setStatus(MealStatus status) {
        this.status = status;
    }

    public Date getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(Date scannedAt) {
        this.scannedAt = scannedAt;
    }

    public Date getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Date confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Date getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Date completedAt) {
        this.completedAt = completedAt;
    }

 // === User State ===
    public Integer getCurrentGlucose() { return currentGlucose; }
    public void setCurrentGlucose(Integer currentGlucose) { this.currentGlucose = currentGlucose; }

    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }

    // === Calculation Breakdown ===
    public Float getCarbDose() { return carbDose; }
    public void setCarbDose(Float carbDose) { this.carbDose = carbDose; }

    public Float getCorrectionDose() { return correctionDose; }
    public void setCorrectionDose(Float correctionDose) { this.correctionDose = correctionDose; }

    public Float getSickAdjustment() { return sickAdjustment; }
    public void setSickAdjustment(Float sickAdjustment) { this.sickAdjustment = sickAdjustment; }

    public Float getStressAdjustment() { return stressAdjustment; }
    public void setStressAdjustment(Float stressAdjustment) { this.stressAdjustment = stressAdjustment; }

    public Float getExerciseAdjustment() { return exerciseAdjustment; }
    public void setExerciseAdjustment(Float exerciseAdjustment) { this.exerciseAdjustment = exerciseAdjustment; }
    
    @Override
    public String toString() {
        return "MealBoundary{" +
                "mealId=" + mealId +
                ", totalCarbs=" + totalCarbs +
                ", recommendedDose=" + recommendedDose +
                ", status=" + status +
                '}';
    }
}
