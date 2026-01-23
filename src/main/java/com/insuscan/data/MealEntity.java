package com.insuscan.data;

import com.insuscan.enums.MealStatus;
import java.util.Date;
import java.util.List;

// Firestore collection: meals
public class MealEntity {

    // --- Identity ---
    private String id;              // Format: systemId_mealUuid
    private String userId;          // Reference to user who scanned
    private String imageUrl;        // URL to stored meal image
    private String note;            // General note for the entire meal

    // --- Core Data ---
    private List<FoodItem> foodItems; // Detected food items
    private Float totalCarbs;         // Total carbs in grams

    // --- Portion Analysis & Metadata ---
    private Float estimatedWeight;      // Total weight in grams
    private Float plateVolumeCm3;
    private Float plateDiameterCm;
    private Float plateDepthCm;
    private Float analysisConfidence;   // 0.0 to 1.0
    private Boolean referenceDetected;  // Was reference object found

    // --- User Context (State at meal time) ---
    private Integer currentGlucose;     // Blood sugar
    private String activityLevel;       // "normal", "light", "intense"

    // --- Insulin Calculation Breakdown ---
    private Float carbDose;             // Dose from carbs alone
    private Float correctionDose;       // Dose from glucose correction
    private Float sickAdjustment;       // Adjustment amount
    private Float stressAdjustment;     // Adjustment amount
    private Float exerciseAdjustment;   // Adjustment amount (negative)

    // --- Final Results ---
    private Float recommendedDose;
    private Float actualDose;           // User override

    // --- Status & Timestamps ---
    private MealStatus status;
    private Date scannedAt;
    private Date confirmedAt;
    private Date completedAt;
    
    // --- Profile Status (for client feedback) ---
    private boolean profileComplete;
    private List<String> missingProfileFields;
    private String insulinMessage;
    
    private Boolean wasSickMode;
    private Boolean wasStressMode;

    public MealEntity() {
        this.scannedAt = new Date();
        this.status = MealStatus.PENDING;
    }

    // ==========================================
    // Nested Class: Food Item
    // ==========================================
    public static class FoodItem {
        private String name;
        private String nameHebrew;      // Hebrew translation if available
        private Float quantity;         // Amount in grams
        private Float carbs;            // Carbs in grams
        private Float confidence;       // Detection confidence 0.0 to 1.0
        private String usdaFdcId;       // USDA FoodData Central ID
        
        // This is the field that was missing causing the error
        private String note;            // Specific note/risk for this item (e.g., "High Fat")

        public FoodItem() {}

        // --- FoodItem Getters & Setters ---
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getNameHebrew() { return nameHebrew; }
        public void setNameHebrew(String nameHebrew) { this.nameHebrew = nameHebrew; }

        public Float getQuantity() { return quantity; }
        public void setQuantity(Float quantity) { this.quantity = quantity; }

        public Float getCarbs() { return carbs; }
        public void setCarbs(Float carbs) { this.carbs = carbs; }

        public Float getConfidence() { return confidence; }
        public void setConfidence(Float confidence) { this.confidence = confidence; }

        public String getUsdaFdcId() { return usdaFdcId; }
        public void setUsdaFdcId(String usdaFdcId) { this.usdaFdcId = usdaFdcId; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    // ==========================================
    // Outer Class Getters & Setters
    // ==========================================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public List<FoodItem> getFoodItems() { return foodItems; }
    public void setFoodItems(List<FoodItem> foodItems) { this.foodItems = foodItems; }

    public Float getTotalCarbs() { return totalCarbs; }
    public void setTotalCarbs(Float totalCarbs) { this.totalCarbs = totalCarbs; }

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

    public Integer getCurrentGlucose() { return currentGlucose; }
    public void setCurrentGlucose(Integer currentGlucose) { this.currentGlucose = currentGlucose; }

    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }

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

    public Float getRecommendedDose() { return recommendedDose; }
    public void setRecommendedDose(Float recommendedDose) { this.recommendedDose = recommendedDose; }

    public Float getActualDose() { return actualDose; }
    public void setActualDose(Float actualDose) { this.actualDose = actualDose; }

    public MealStatus getStatus() { return status; }
    public void setStatus(MealStatus status) { this.status = status; }

    public Date getScannedAt() { return scannedAt; }
    public void setScannedAt(Date scannedAt) { this.scannedAt = scannedAt; }

    public Date getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Date confirmedAt) { this.confirmedAt = confirmedAt; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }

    public boolean isProfileComplete() { return profileComplete; }
    public void setProfileComplete(boolean profileComplete) { this.profileComplete = profileComplete; }

    public List<String> getMissingProfileFields() { return missingProfileFields; }
    public void setMissingProfileFields(List<String> missingProfileFields) { this.missingProfileFields = missingProfileFields; }

    public String getInsulinMessage() { return insulinMessage; }
    public void setInsulinMessage(String insulinMessage) { this.insulinMessage = insulinMessage; }
    
    public Boolean getWasSickMode() { return wasSickMode; }
    public void setWasSickMode(Boolean wasSickMode) { this.wasSickMode = wasSickMode; }

    public Boolean getWasStressMode() { return wasStressMode; }
    public void setWasStressMode(Boolean wasStressMode) { this.wasStressMode = wasStressMode; }
    
    @Override
    public String toString() {
        return "MealEntity{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", totalCarbs=" + totalCarbs +
                ", recommendedDose=" + recommendedDose +
                ", status=" + status +
                '}';
    }
}