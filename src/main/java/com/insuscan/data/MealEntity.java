package com.insuscan.data;

import com.insuscan.enums.MealStatus;
import java.util.Date;
import java.util.List;

/**
 * Firestore document representing a scanned meal. Contains vision analysis,
 * user context at meal time, insulin calculation breakdown, and metadata.
 */
public class MealEntity {

    private String id;
    private String userId;
    private String imageUrl;
    private String note;

    private List<FoodItem> foodItems;
    private Float totalCarbs;

    private Float estimatedWeight;
    private Float plateVolumeCm3;
    private Float plateDiameterCm;
    private Float plateDepthCm;
    private Float analysisConfidence;
    private Boolean referenceDetected;
    private String referenceObjectType;
    private String containerType;

    private Integer currentGlucose;

    private Float carbDose;
    private Float correctionDose;

    private Float recommendedDose;
    private Float actualDose;
    private boolean requiresManualReview;

    private MealStatus status;
    private Date scannedAt;
    private Date confirmedAt;
    private Date completedAt;

    private boolean profileComplete;
    private List<String> missingProfileFields;
    private String insulinMessage;
    private String savedPlanName;

    private Float activeInsulin;
    private List<String> reviewWarnings;

    public MealEntity() {
        this.scannedAt = new Date();
        this.status = MealStatus.PENDING;
    }

    /**
     * A single food item within a meal, with location bbox for GrabCut.
     */
    public static class FoodItem {
        private String name;
        private String nameHebrew;
        private Float quantity;
        private Float carbs;
        private Float confidence;
        private String usdaFdcId;

        private String note;

        private Float bboxXPct;
        private Float bboxYPct;
        private Float bboxWPct;
        private Float bboxHPct;

        public FoodItem() {
        }

        public Float getBboxXPct() {
            return bboxXPct;
        }

        public void setBboxXPct(Float bboxXPct) {
            this.bboxXPct = bboxXPct;
        }

        public Float getBboxYPct() {
            return bboxYPct;
        }

        public void setBboxYPct(Float bboxYPct) {
            this.bboxYPct = bboxYPct;
        }

        public Float getBboxWPct() {
            return bboxWPct;
        }

        public void setBboxWPct(Float bboxWPct) {
            this.bboxWPct = bboxWPct;
        }

        public Float getBboxHPct() {
            return bboxHPct;
        }

        public void setBboxHPct(Float bboxHPct) {
            this.bboxHPct = bboxHPct;
        }

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

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<FoodItem> getFoodItems() {
        return foodItems;
    }

    public void setFoodItems(List<FoodItem> foodItems) {
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

    public String getReferenceObjectType() {
        return referenceObjectType;
    }

    public void setReferenceObjectType(String referenceObjectType) {
        this.referenceObjectType = referenceObjectType;
    }

    public String getContainerType() {
        return containerType;
    }

    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    public Integer getCurrentGlucose() {
        return currentGlucose;
    }

    public void setCurrentGlucose(Integer currentGlucose) {
        this.currentGlucose = currentGlucose;
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

    public boolean isProfileComplete() {
        return profileComplete;
    }

    public void setProfileComplete(boolean profileComplete) {
        this.profileComplete = profileComplete;
    }

    public List<String> getMissingProfileFields() {
        return missingProfileFields;
    }

    public void setMissingProfileFields(List<String> missingProfileFields) {
        this.missingProfileFields = missingProfileFields;
    }

    public String getInsulinMessage() {
        return insulinMessage;
    }

    public void setInsulinMessage(String insulinMessage) {
        this.insulinMessage = insulinMessage;
    }

    public Float getActiveInsulin() {
        return activeInsulin;
    }

    public void setActiveInsulin(Float activeInsulin) {
        this.activeInsulin = activeInsulin;
    }

    public List<String> getReviewWarnings() {
        return reviewWarnings;
    }

    public void setReviewWarnings(List<String> reviewWarnings) {
        this.reviewWarnings = reviewWarnings;
    }

    public String getSavedPlanName() {
        return savedPlanName;
    }

    public void setSavedPlanName(String savedPlanName) {
        this.savedPlanName = savedPlanName;
    }

    public boolean isRequiresManualReview() {
        return requiresManualReview;
    }

    public void setRequiresManualReview(boolean requiresManualReview) {
        this.requiresManualReview = requiresManualReview;
    }

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