package com.insuscan.service;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Meal management service: creation from scans, food item updates, confirmation
 * with insulin calculation, portion analysis updates, and admin-level deletes.
 */
public interface MealService {

    MealBoundary createMeal(String userSystemId, String userEmail, String imageUrl);

    MealBoundary saveScannedMeal(String systemId, String email, MealBoundary mealBoundary);

    Optional<MealBoundary> getMealById(String systemId, String mealId);

    List<MealBoundary> getMealsByUser(String systemId, String email, int page, int size);

    MealBoundary updateFoodItems(String systemId, String mealId, List<FoodItemBoundary> foodItems);

    MealBoundary confirmMeal(String systemId, String mealId, Float actualDose, Integer currentGlucose);

    MealBoundary completeMeal(String systemId, String mealId);

    MealBoundary updatePortionAnalysis(String systemId, String mealId,
            Float estimatedWeight, Float volumeCm3,
            Float diameterCm, Float depthCm,
            Float confidence, Boolean refDetected);

    void deleteMeal(String systemId, String mealId);

    void deleteAllMealsForUser(String adminSystemId, String adminEmail,
            String targetSystemId, String targetEmail);

    void deleteAllMeals(String adminSystemId, String adminEmail);

    List<MealBoundary> getRecentMeals(String systemId, String email, int count);

    long getMealCountForUser(String systemId, String email);

    List<MealBoundary> getMealsByDateRange(String systemId, String email,
            LocalDate from, LocalDate to,
            int page, int size);
}