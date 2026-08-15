package com.insuscan.converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.MealIdBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.data.MealEntity;
import com.insuscan.enums.MealStatus;
import com.insuscan.util.MealIdGenerator;
import com.insuscan.util.NumberUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts between {@link MealEntity} (Firestore document) and
 * {@link MealBoundary} (API DTO), handling composite ID parsing/building
 * and food item sub-conversion.
 */
@Component
public class MealConverter {

    @Value("${spring.application.name}")
    private String systemId;

    private final MealIdGenerator mealIdGenerator;

    public MealConverter(MealIdGenerator mealIdGenerator) {
        this.mealIdGenerator = mealIdGenerator;
    }

    public MealBoundary toBoundary(MealEntity entity) {
        if (entity == null) {
            return null;
        }

        MealBoundary boundary = new MealBoundary();

        if (entity.getId() != null) {
            MealIdBoundary mealId = new MealIdBoundary();
            String[] parts = entity.getId().split("_", 2);
            if (parts.length == 2) {
                mealId.setSystemId(parts[0]);
                mealId.setMealId(parts[1]);
            } else {
                mealId.setSystemId(systemId);
                mealId.setMealId(entity.getId());
            }
            boundary.setMealId(mealId);
        }

        if (entity.getUserId() != null) {
            UserIdBoundary userId = new UserIdBoundary();
            String[] parts = entity.getUserId().split("_", 2);
            if (parts.length == 2) {
                userId.setSystemId(parts[0]);
                userId.setEmail(parts[1]);
            } else {
                userId.setSystemId(systemId);
                userId.setEmail(entity.getUserId());
            }
            boundary.setUserId(userId);
        }

        boundary.setImageUrl(entity.getImageUrl());

        if (entity.getFoodItems() != null) {
            boundary.setFoodItems(
                    entity.getFoodItems().stream()
                            .map(this::foodItemToBoundary)
                            .collect(Collectors.toList()));
        }

        boundary.setTotalCarbs(entity.getTotalCarbs());

        boundary.setEstimatedWeight(entity.getEstimatedWeight());
        boundary.setPlateVolumeCm3(entity.getPlateVolumeCm3());
        boundary.setPlateDiameterCm(entity.getPlateDiameterCm());
        boundary.setPlateDepthCm(entity.getPlateDepthCm());
        boundary.setAnalysisConfidence(entity.getAnalysisConfidence());
        boundary.setReferenceDetected(entity.getReferenceDetected());
        boundary.setReferenceObjectType(entity.getReferenceObjectType());
        boundary.setContainerType(entity.getContainerType());

        boundary.setCurrentGlucose(entity.getCurrentGlucose());

        boundary.setCarbDose(entity.getCarbDose());
        boundary.setCorrectionDose(entity.getCorrectionDose());
        boundary.setActiveInsulin(entity.getActiveInsulin());

        boundary.setRecommendedDose(entity.getRecommendedDose());
        boundary.setActualDose(entity.getActualDose());

        boundary.setStatus(entity.getStatus());
        boundary.setScannedAt(entity.getScannedAt());
        boundary.setConfirmedAt(entity.getConfirmedAt());
        boundary.setCompletedAt(entity.getCompletedAt());
        boundary.setInsulinMessage(entity.getInsulinMessage());
        boundary.setMissingProfileFields(entity.getMissingProfileFields());
        boundary.setProfileComplete(entity.isProfileComplete());

        boundary.setSavedPlanName(entity.getSavedPlanName());

        boundary.setReviewWarnings(entity.getReviewWarnings());
        boundary.setRequiresManualReview(entity.isRequiresManualReview());

        return boundary;
    }

    public MealEntity toEntity(MealBoundary boundary) {
        if (boundary == null) {
            return null;
        }

        MealEntity entity = new MealEntity();

        if (boundary.getMealId() != null) {
            String sys = boundary.getMealId().getSystemId();
            if (sys == null || sys.trim().isEmpty()) {
                sys = systemId;
            }
            String mealUuid = boundary.getMealId().getMealId();
            entity.setId(sys + "_" + mealUuid);
        }

        if (boundary.getUserId() != null) {
            String sys = boundary.getUserId().getSystemId() != null
                    ? boundary.getUserId().getSystemId()
                    : systemId;
            String email = boundary.getUserId().getEmail();
            entity.setUserId(sys + "_" + email);
        }

        entity.setImageUrl(boundary.getImageUrl());

        if (boundary.getFoodItems() != null) {
            entity.setFoodItems(
                    boundary.getFoodItems().stream()
                            .map(this::foodItemToEntity)
                            .collect(Collectors.toList()));
        }

        entity.setTotalCarbs(boundary.getTotalCarbs());

        entity.setEstimatedWeight(boundary.getEstimatedWeight());
        entity.setPlateVolumeCm3(boundary.getPlateVolumeCm3());
        entity.setPlateDiameterCm(boundary.getPlateDiameterCm());
        entity.setPlateDepthCm(boundary.getPlateDepthCm());
        entity.setAnalysisConfidence(boundary.getAnalysisConfidence());
        entity.setReferenceDetected(boundary.getReferenceDetected());
        entity.setReferenceObjectType(boundary.getReferenceObjectType());
        entity.setContainerType(boundary.getContainerType());

        entity.setCurrentGlucose(boundary.getCurrentGlucose());

        entity.setCarbDose(boundary.getCarbDose());
        entity.setCorrectionDose(boundary.getCorrectionDose());
        entity.setActiveInsulin(boundary.getActiveInsulin());

        entity.setRecommendedDose(boundary.getRecommendedDose());
        entity.setActualDose(boundary.getActualDose());

        entity.setStatus(boundary.getStatus());
        entity.setScannedAt(boundary.getScannedAt());
        entity.setConfirmedAt(boundary.getConfirmedAt());
        entity.setCompletedAt(boundary.getCompletedAt());

        entity.setInsulinMessage(boundary.getInsulinMessage());
        entity.setMissingProfileFields(boundary.getMissingProfileFields());
        entity.setProfileComplete(boundary.isProfileComplete());

        entity.setSavedPlanName(boundary.getSavedPlanName());

        entity.setReviewWarnings(boundary.getReviewWarnings());
        entity.setRequiresManualReview(boundary.isRequiresManualReview());

        return entity;
    }

    public MealEntity createNewMealEntity(String userEmail, String imageUrl) {
        MealEntity entity = new MealEntity();

        entity.setId(systemId + "_" + mealIdGenerator.generateMealId());

        entity.setUserId(systemId + "_" + userEmail);

        entity.setImageUrl(imageUrl);
        entity.setStatus(MealStatus.PENDING);
        entity.setFoodItems(Collections.emptyList());

        return entity;
    }

    private FoodItemBoundary foodItemToBoundary(MealEntity.FoodItem item) {
        if (item == null) {
            return null;
        }

        FoodItemBoundary boundary = new FoodItemBoundary();
        boundary.setName(item.getName());
        boundary.setNameHebrew(item.getNameHebrew());
        boundary.setQuantity(item.getQuantity());
        boundary.setCarbs(item.getCarbs());
        boundary.setConfidence(item.getConfidence());
        boundary.setUsdaFdcId(item.getUsdaFdcId());

        boundary.setBboxXPct(item.getBboxXPct());
        boundary.setBboxYPct(item.getBboxYPct());
        boundary.setBboxWPct(item.getBboxWPct());
        boundary.setBboxHPct(item.getBboxHPct());

        return boundary;
    }

    public MealEntity.FoodItem foodItemToEntity(FoodItemBoundary boundary) {
        if (boundary == null) {
            return null;
        }

        MealEntity.FoodItem item = new MealEntity.FoodItem();
        item.setName(boundary.getName());
        item.setNameHebrew(boundary.getNameHebrew());
        item.setQuantity(boundary.getQuantity());
        item.setCarbs(boundary.getCarbs());
        item.setConfidence(boundary.getConfidence());
        item.setUsdaFdcId(boundary.getUsdaFdcId());
        item.setNote(boundary.getNote());

        item.setBboxXPct(boundary.getBboxXPct());
        item.setBboxYPct(boundary.getBboxYPct());
        item.setBboxWPct(boundary.getBboxWPct());
        item.setBboxHPct(boundary.getBboxHPct());

        return item;
    }

    public void calculateTotals(MealEntity entity) {
        if (entity.getFoodItems() == null || entity.getFoodItems().isEmpty()) {
            entity.setTotalCarbs(0f);
            return;
        }

        float totalCarbs = 0f;

        for (MealEntity.FoodItem item : entity.getFoodItems()) {
            if (item.getCarbs() != null)
                totalCarbs += item.getCarbs();
        }

        entity.setTotalCarbs(NumberUtils.roundTo2Decimals(totalCarbs));
    }
}