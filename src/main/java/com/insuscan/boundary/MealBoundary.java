package com.insuscan.boundary;

import com.insuscan.enums.MealStatus;
import java.util.Date;
import java.util.List;

/**
 * MealBoundary: Data Transfer Object (DTO) Represents a complete meal record
 * including vision analysis, user context, and insulin calculations.
 */
public class MealBoundary {

	private MealIdBoundary mealId;
	private UserIdBoundary userId;
	private String imageUrl;

	private List<FoodItemBoundary> foodItems;
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
	private String glucoseUnits;

	private Float activeInsulin;

	private Float recommendedDose;
	private Float actualDose;

	private MealStatus status;
	@com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
	private Date scannedAt;
	@com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
	private Date confirmedAt;
	@com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
	private Date completedAt;

	private String note;
	private boolean profileComplete;
	private List<String> missingProfileFields;
	private String insulinMessage;
	private String savedPlanName;
	private List<String> reviewWarnings;
	private boolean requiresManualReview;

	public MealBoundary() {
	}

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

	public Float getActiveInsulin() {
		return activeInsulin;
	}

	public void setActiveInsulin(Float activeInsulin) {
		this.activeInsulin = activeInsulin;
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

	public String getGlucoseUnits() {
		return glucoseUnits;
	}

	public void setGlucoseUnits(String glucoseUnits) {
		this.glucoseUnits = glucoseUnits;
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

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
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

	public String getSavedPlanName() {
		return savedPlanName;
	}

	public void setSavedPlanName(String savedPlanName) {
		this.savedPlanName = savedPlanName;
	}

	public List<String> getReviewWarnings() {
		return reviewWarnings;
	}

	public void setReviewWarnings(List<String> reviewWarnings) {
		this.reviewWarnings = reviewWarnings;
	}

	public boolean isRequiresManualReview() {
		return requiresManualReview;
	}

	public void setRequiresManualReview(boolean requiresManualReview) {
		this.requiresManualReview = requiresManualReview;
	}

	@Override
	public String toString() {
		return "MealBoundary{" + "id=" + mealId + ", user=" + userId + ", carbs=" + totalCarbs + ", glucose="
				+ currentGlucose + ", recDose=" + recommendedDose + ", actDose=" + actualDose + ", status=" + status
				+ ", savedPlanName=" + savedPlanName + '}';
	}
}