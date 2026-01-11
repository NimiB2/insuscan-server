package com.insuscan.data;

import com.insuscan.enums.MealStatus;

import java.util.Date;
import java.util.List;

// Firestore collection: meals
public class MealEntity {
    
    private String id;              // Format: systemId_mealUuid
    
    private String userId;          // Reference to user who scanned
    private String imageUrl;        // URL to stored meal image
    
    // Detected food items and their nutritional values
    private List<FoodItem> foodItems;
    
    // Total nutritional summary
    private Float totalCarbs;
    
    // Portion analysis data
    private Float estimatedWeight;      // Total weight in grams
    private Float plateVolumeCm3;
    private Float plateDiameterCm;
    private Float plateDepthCm;
    private Float analysisConfidence;   // 0.0 to 1.0
    private Boolean referenceDetected;  // Was syringe found in image
    
    // Insulin calculation results
    private Float recommendedDose;
    private Float actualDose;           // User can override
    
    // Status tracking
    private MealStatus status;
    private Date scannedAt;
    private Date confirmedAt;
    private Date completedAt;

    public MealEntity() {
        this.scannedAt = new Date();
        this.status = MealStatus.PENDING;
    }

    // Nested class for individual food items
    public static class FoodItem {
        private String name;
        private String nameHebrew;      // Hebrew translation if available
        private Float quantity;         // Amount in grams
        private Float carbs;            // Carbs in grams
        private Float confidence;       // Detection confidence 0.0 to 1.0
        private String usdaFdcId;       // USDA FoodData Central ID

        public FoodItem() {}

        // Getters and Setters
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
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

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
