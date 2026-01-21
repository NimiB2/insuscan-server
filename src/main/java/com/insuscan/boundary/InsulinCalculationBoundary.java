package com.insuscan.boundary;

import java.util.List;


// Request/Response DTO for insulin calculation
public class InsulinCalculationBoundary {
    // Input
    private Float totalCarbs;
    private Float currentGlucose;       // Optional: for correction dose
    private UserIdBoundary userId;      // To fetch user's medical profile
    
    // Output
    private Float carbDose;             // Dose for carbs coverage
    private Float correctionDose;       // Dose for glucose correction
    private Float totalRecommendedDose;
    
    // Calculation breakdown (for transparency)
    private String insulinCarbRatioUsed;
    private Float correctionFactorUsed;
    private Integer targetGlucoseUsed;
    
    private Float sickAdjustment;
    private Float stressAdjustment;  
    private Float exerciseAdjustment;
    private String activityLevel;
    
    private Boolean sickModeEnabled;
    private Boolean stressModeEnabled;
    
 // Profile status
    private boolean profileComplete;
    private List<String> missingFields;

    // User-friendly message
    private String message;
    
    // Warnings
    private String warning;             // e.g. "Dose unusually high - please verify"

    public InsulinCalculationBoundary() {}

    // Getters and Setters
    public Float getTotalCarbs() {
        return totalCarbs;
    }

    public void setTotalCarbs(Float totalCarbs) {
        this.totalCarbs = totalCarbs;
    }

    public Float getCurrentGlucose() {
        return currentGlucose;
    }

    public void setCurrentGlucose(Float currentGlucose) {
        this.currentGlucose = currentGlucose;
    }

    public UserIdBoundary getUserId() {
        return userId;
    }

    public void setUserId(UserIdBoundary userId) {
        this.userId = userId;
    }

    public Float getCarbDose() {
        return carbDose;
    }

    public void setCarbDose(Float carbDose) {
        this.carbDose = carbDose;
    }

    public Float getCorrectionDose() {
        return correctionDose;
    }

    public void setCorrectionDose(Float correctionDose) {
        this.correctionDose = correctionDose;
    }

    public Float getTotalRecommendedDose() {
        return totalRecommendedDose;
    }

    public void setTotalRecommendedDose(Float totalRecommendedDose) {
        this.totalRecommendedDose = totalRecommendedDose;
    }

    public String getInsulinCarbRatioUsed() {
        return insulinCarbRatioUsed;
    }

    public void setInsulinCarbRatioUsed(String insulinCarbRatioUsed) {
        this.insulinCarbRatioUsed = insulinCarbRatioUsed;
    }

    public Float getCorrectionFactorUsed() {
        return correctionFactorUsed;
    }

    public void setCorrectionFactorUsed(Float correctionFactorUsed) {
        this.correctionFactorUsed = correctionFactorUsed;
    }

    public Integer getTargetGlucoseUsed() {
        return targetGlucoseUsed;
    }

    public void setTargetGlucoseUsed(Integer targetGlucoseUsed) {
        this.targetGlucoseUsed = targetGlucoseUsed;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }
    
    public Float getSickAdjustment() { return sickAdjustment; }
    public void setSickAdjustment(Float sickAdjustment) { this.sickAdjustment = sickAdjustment; }

    public Float getStressAdjustment() { return stressAdjustment; }
    public void setStressAdjustment(Float stressAdjustment) { this.stressAdjustment = stressAdjustment; }

    public Float getExerciseAdjustment() { return exerciseAdjustment; }
    public void setExerciseAdjustment(Float exerciseAdjustment) { this.exerciseAdjustment = exerciseAdjustment; }

    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }

    public Boolean getSickModeEnabled() { return sickModeEnabled; }
    public void setSickModeEnabled(Boolean sickModeEnabled) { this.sickModeEnabled = sickModeEnabled; }

    public Boolean getStressModeEnabled() { return stressModeEnabled; }
    public void setStressModeEnabled(Boolean stressModeEnabled) { this.stressModeEnabled = stressModeEnabled; }

    public boolean isProfileComplete() { return profileComplete; }
    public void setProfileComplete(boolean profileComplete) { this.profileComplete = profileComplete; }

    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    @Override
    public String toString() {
        return "InsulinCalculationBoundary{" +
                "totalCarbs=" + totalCarbs +
                ", totalRecommendedDose=" + totalRecommendedDose +
                ", warning='" + warning + '\'' +
                '}';
    }
}
