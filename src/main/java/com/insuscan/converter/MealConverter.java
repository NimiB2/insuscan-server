package com.insuscan.converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.MealIdBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.data.MealEntity;
import com.insuscan.enums.MealStatus;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class MealConverter {

    @Value("${spring.application.name}")
    private String systemId;

    // Convert MealEntity to MealBoundary for API responses
    public MealBoundary toBoundary(MealEntity entity) {
        if (entity == null) {
            return null;
        }

        MealBoundary boundary = new MealBoundary();

        // Parse composite meal ID (systemId_mealUuid)
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

        // Parse composite user ID (systemId_email)
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

        // Convert food items
        if (entity.getFoodItems() != null) {
            boundary.setFoodItems(
                entity.getFoodItems().stream()
                    .map(this::foodItemToBoundary)
                    .collect(Collectors.toList())
            );
        }

        // Nutrition totals
        boundary.setTotalCarbs(entity.getTotalCarbs());
        boundary.setTotalCalories(entity.getTotalCalories());
        boundary.setTotalProtein(entity.getTotalProtein());
        boundary.setTotalFat(entity.getTotalFat());

        // Portion analysis
        boundary.setEstimatedWeight(entity.getEstimatedWeight());
        boundary.setPlateVolumeCm3(entity.getPlateVolumeCm3());
        boundary.setPlateDiameterCm(entity.getPlateDiameterCm());
        boundary.setPlateDepthCm(entity.getPlateDepthCm());
        boundary.setAnalysisConfidence(entity.getAnalysisConfidence());
        boundary.setReferenceDetected(entity.getReferenceDetected());

        // Insulin data
        boundary.setRecommendedDose(entity.getRecommendedDose());
        boundary.setActualDose(entity.getActualDose());

        // Status and timestamps
        boundary.setStatus(entity.getStatus());
        boundary.setScannedAt(entity.getScannedAt());
        boundary.setConfirmedAt(entity.getConfirmedAt());
        boundary.setCompletedAt(entity.getCompletedAt());

        return boundary;
    }

    // Convert MealBoundary to MealEntity for database storage
    public MealEntity toEntity(MealBoundary boundary) {
        if (boundary == null) {
            return null;
        }

        MealEntity entity = new MealEntity();

        // Build composite meal ID
        if (boundary.getMealId() != null) {
            String sys = boundary.getMealId().getSystemId() != null 
                ? boundary.getMealId().getSystemId() : systemId;
            String mealUuid = boundary.getMealId().getMealId();
            entity.setId(sys + "_" + mealUuid);
        }

        // Build composite user ID
        if (boundary.getUserId() != null) {
            String sys = boundary.getUserId().getSystemId() != null 
                ? boundary.getUserId().getSystemId() : systemId;
            String email = boundary.getUserId().getEmail();
            entity.setUserId(sys + "_" + email);
        }

        entity.setImageUrl(boundary.getImageUrl());

        // Convert food items
        if (boundary.getFoodItems() != null) {
            entity.setFoodItems(
                boundary.getFoodItems().stream()
                    .map(this::foodItemToEntity)
                    .collect(Collectors.toList())
            );
        }

        // Nutrition totals
        entity.setTotalCarbs(boundary.getTotalCarbs());
        entity.setTotalCalories(boundary.getTotalCalories());
        entity.setTotalProtein(boundary.getTotalProtein());
        entity.setTotalFat(boundary.getTotalFat());

        // Portion analysis
        entity.setEstimatedWeight(boundary.getEstimatedWeight());
        entity.setPlateVolumeCm3(boundary.getPlateVolumeCm3());
        entity.setPlateDiameterCm(boundary.getPlateDiameterCm());
        entity.setPlateDepthCm(boundary.getPlateDepthCm());
        entity.setAnalysisConfidence(boundary.getAnalysisConfidence());
        entity.setReferenceDetected(boundary.getReferenceDetected());

        // Insulin data
        entity.setRecommendedDose(boundary.getRecommendedDose());
        entity.setActualDose(boundary.getActualDose());

        // Status and timestamps
        entity.setStatus(boundary.getStatus());
        entity.setScannedAt(boundary.getScannedAt());
        entity.setConfirmedAt(boundary.getConfirmedAt());
        entity.setCompletedAt(boundary.getCompletedAt());

        return entity;
    }

    // Create a new meal entity with generated ID for initial scan
    public MealEntity createNewMealEntity(String userEmail, String imageUrl) {
        MealEntity entity = new MealEntity();
        
        // Generate new meal ID
        String mealUuid = UUID.randomUUID().toString();
        entity.setId(systemId + "_" + mealUuid);
        
        // Set user reference
        entity.setUserId(systemId + "_" + userEmail);
        
        entity.setImageUrl(imageUrl);
        entity.setStatus(MealStatus.PENDING);
        entity.setFoodItems(Collections.emptyList());
        
        return entity;
    }

    // Convert FoodItem entity to boundary
    private FoodItemBoundary foodItemToBoundary(MealEntity.FoodItem item) {
        if (item == null) {
            return null;
        }

        FoodItemBoundary boundary = new FoodItemBoundary();
        boundary.setName(item.getName());
        boundary.setNameHebrew(item.getNameHebrew());
        boundary.setQuantity(item.getQuantity());
        boundary.setCarbs(item.getCarbs());
        boundary.setCalories(item.getCalories());
        boundary.setProtein(item.getProtein());
        boundary.setFat(item.getFat());
        boundary.setConfidence(item.getConfidence());
        boundary.setUsdaFdcId(item.getUsdaFdcId());

        return boundary;
    }

    // Convert FoodItem boundary to entity
    private MealEntity.FoodItem foodItemToEntity(FoodItemBoundary boundary) {
        if (boundary == null) {
            return null;
        }

        MealEntity.FoodItem item = new MealEntity.FoodItem();
        item.setName(boundary.getName());
        item.setNameHebrew(boundary.getNameHebrew());
        item.setQuantity(boundary.getQuantity());
        item.setCarbs(boundary.getCarbs());
        item.setCalories(boundary.getCalories());
        item.setProtein(boundary.getProtein());
        item.setFat(boundary.getFat());
        item.setConfidence(boundary.getConfidence());
        item.setUsdaFdcId(boundary.getUsdaFdcId());

        return item;
    }

    // Calculate total nutrition from food items
    public void calculateTotals(MealEntity entity) {
        if (entity.getFoodItems() == null || entity.getFoodItems().isEmpty()) {
            entity.setTotalCarbs(0f);
            entity.setTotalCalories(0f);
            entity.setTotalProtein(0f);
            entity.setTotalFat(0f);
            return;
        }

        float totalCarbs = 0f;
        float totalCalories = 0f;
        float totalProtein = 0f;
        float totalFat = 0f;

        for (MealEntity.FoodItem item : entity.getFoodItems()) {
            if (item.getCarbs() != null) totalCarbs += item.getCarbs();
            if (item.getCalories() != null) totalCalories += item.getCalories();
            if (item.getProtein() != null) totalProtein += item.getProtein();
            if (item.getFat() != null) totalFat += item.getFat();
        }

        entity.setTotalCarbs(totalCarbs);
        entity.setTotalCalories(totalCalories);
        entity.setTotalProtein(totalProtein);
        entity.setTotalFat(totalFat);
    }
}
