package com.insuscan.boundary;

import com.insuscan.enums.MealStatus;
import java.util.Date;
import java.util.List;

/**
 * MealBoundary - Data Transfer Object (DTO)
 * Represents a complete meal record including vision analysis, user context, and insulin calculations.
 */
public class MealBoundary {

    // ============================================================================================
    // 1. Identity & Media 
    // ============================================================================================
    private MealIdBoundary mealId;
    private UserIdBoundary userId;
    private String imageUrl;

    // ============================================================================================
    // 2. Food Content & Nutrition 
    // ============================================================================================
    private List<FoodItemBoundary> foodItems;
    private Float totalCarbs;                 // Total calculated carbs in grams

    // ============================================================================================
    // 3. Vision & Portion Analysis 
    // ============================================================================================
    private Float estimatedWeight;            // Total weight in grams
    private Float plateVolumeCm3;             // Volume from AR/Depth
    private Float plateDiameterCm;            // Detected or referenced diameter
    private Float plateDepthCm;               // Detected depth
    private Float analysisConfidence;         // Overall algorithm confidence (0.0 - 1.0)
    private Boolean referenceDetected;        // Was a reference object (e.g. syringe) found?

    // ============================================================================================
    // 4. User Context & State
    // ============================================================================================
    private Integer currentGlucose;           // Glucose level in mg/dL
    private String activityLevel;             // "normal", "light", "intense"

    // Flags: Did the user explicitly select these modes? (For UI display)
    private Boolean wasSickMode;
    private Boolean wasStressMode;

    // ============================================================================================
    // 5. Calculation Breakdown
    // ============================================================================================
    private Float carbDose;                   // Base dose for carbohydrates
    private Float correctionDose;             // Dose for high glucose correction

    // Adjustment Values (The actual calculated amount added/subtracted)
    private Float sickAdjustment;             // e.g. +1.5 units
    private Float stressAdjustment;           // e.g. +0.5 units
    private Float exerciseAdjustment;         // e.g. -1.0 units

    // ============================================================================================
    // 6. Final Dosage 
    // ============================================================================================
    private Float recommendedDose;            // Calculated recommendation
    private Float actualDose;                 // What the user actually injected (confirmed)

    // ============================================================================================
    // 7. Status, Metadata & Feedback 
    // ============================================================================================
    private MealStatus status;                // SCANNED, CONFIRMED, DELETED
    private Date scannedAt;
    private Date confirmedAt;
    private Date completedAt;

    private String note;                      // User notes
    private boolean profileComplete;          // Flag: Was profile complete during calc?
    private List<String> missingProfileFields;// List of missing fields if incomplete
    private String insulinMessage;            // Warnings or info messages

    public MealBoundary() {
    }

    // --- 1. Identity ---
    public MealIdBoundary getMealId() { return mealId; }
    public void setMealId(MealIdBoundary mealId) { this.mealId = mealId; }

    public UserIdBoundary getUserId() { return userId; }
    public void setUserId(UserIdBoundary userId) { this.userId = userId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // --- 2. Content ---
    public List<FoodItemBoundary> getFoodItems() { return foodItems; }
    public void setFoodItems(List<FoodItemBoundary> foodItems) { this.foodItems = foodItems; }

    public Float getTotalCarbs() { return totalCarbs; }
    public void setTotalCarbs(Float totalCarbs) { this.totalCarbs = totalCarbs; }

    // --- 3. Vision Analysis ---
    public Float getEstimatedWeight() { return estimatedWeight; }
    public void setEstimatedWeight(Float estimatedWeight) { this.estimatedWeight = estimatedWeight; }

    public Float getPlateVolumeCm3() { return plateVolumeCm3; }
    public void setPlateVolumeCm3(Float plateVolumeCm3) { this.plateVolumeCm3 = plateVolumeCm3; }

    public Float getPlateDiameterCm() { return plateDiameterCm; }
    public void setPlateDiameterCm(Float plateDiameterCm) { this.plateDiameterCm = plateDiameterCm; }

    public Float getPlateDepthCm() { return plateDepthCm; }
    public void setPlateDepthCm(Float plateDepthCm) { this.plateDepthCm = plateDepthCm; }

    public Float getAnalysisConfidence() { return analysisConfidence; }
    public void setAnalysisConfidence(Float analysisConfidence) { this.analysisConfidence = analysisConfidence; }

    public Boolean getReferenceDetected() { return referenceDetected; }
    public void setReferenceDetected(Boolean referenceDetected) { this.referenceDetected = referenceDetected; }

    // --- 4. User Context ---
    public Integer getCurrentGlucose() { return currentGlucose; }
    public void setCurrentGlucose(Integer currentGlucose) { this.currentGlucose = currentGlucose; }

    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }

    public Boolean getWasSickMode() { return wasSickMode; }
    public void setWasSickMode(Boolean wasSickMode) { this.wasSickMode = wasSickMode; }

    public Boolean getWasStressMode() { return wasStressMode; }
    public void setWasStressMode(Boolean wasStressMode) { this.wasStressMode = wasStressMode; }

    // --- 5. Calculation Breakdown ---
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

    // --- 6. Final Dosage ---
    public Float getRecommendedDose() { return recommendedDose; }
    public void setRecommendedDose(Float recommendedDose) { this.recommendedDose = recommendedDose; }

    public Float getActualDose() { return actualDose; }
    public void setActualDose(Float actualDose) { this.actualDose = actualDose; }

    // --- 7. Status & Metadata ---
    public MealStatus getStatus() { return status; }
    public void setStatus(MealStatus status) { this.status = status; }

    public Date getScannedAt() { return scannedAt; }
    public void setScannedAt(Date scannedAt) { this.scannedAt = scannedAt; }

    public Date getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Date confirmedAt) { this.confirmedAt = confirmedAt; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isProfileComplete() { return profileComplete; }
    public void setProfileComplete(boolean profileComplete) { this.profileComplete = profileComplete; }

    public List<String> getMissingProfileFields() { return missingProfileFields; }
    public void setMissingProfileFields(List<String> missingProfileFields) { this.missingProfileFields = missingProfileFields; }

    public String getInsulinMessage() { return insulinMessage; }
    public void setInsulinMessage(String insulinMessage) { this.insulinMessage = insulinMessage; }

    
    @Override
    public String toString() {
        return "MealBoundary{" +
                "id=" + mealId +
                ", user=" + userId +
                ", carbs=" + totalCarbs +
                ", glucose=" + currentGlucose +
                ", recDose=" + recommendedDose +
                ", actDose=" + actualDose +
                ", status=" + status +
                ", sickMode=" + wasSickMode +
                ", stressMode=" + wasStressMode +
                '}';
    }
}
