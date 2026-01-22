package com.insuscan.service;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Meal management service interface
public interface MealService {

    // Create a new meal entry (from scan)
    MealBoundary createMeal(String userSystemId, String userEmail, String imageUrl);

    // Get meal by ID
    Optional<MealBoundary> getMealById(String systemId, String mealId);

    // Get all meals for a user (with pagination)
    List<MealBoundary> getMealsByUser(String systemId, String email, int page, int size);

    // Update meal with detected food items
    MealBoundary updateFoodItems(String systemId, String mealId, List<FoodItemBoundary> foodItems);

    // Confirm meal and calculate insulin dose
    MealBoundary confirmMeal(String systemId, String mealId, Float actualDose);

    // Mark meal as completed (after injection)
    MealBoundary completeMeal(String systemId, String mealId);

    // Update portion analysis data
    MealBoundary updatePortionAnalysis(String systemId, String mealId, 
                                       Float estimatedWeight, Float volumeCm3,
                                       Float diameterCm, Float depthCm,
                                       Float confidence, Boolean refDetected);

    // Delete meal
    void deleteMeal(String systemId, String mealId);

    // Delete all meals for a user (admin)
    void deleteAllMealsForUser(String adminSystemId, String adminEmail, 
                               String targetSystemId, String targetEmail);

    // Delete all meals (admin only)
    void deleteAllMeals(String adminSystemId, String adminEmail);

    // Get recent meals for user (last N)
    List<MealBoundary> getRecentMeals(String systemId, String email, int count);

    // Get meal count for user
    long getMealCountForUser(String systemId, String email);
    
    List<MealBoundary> getMealsByDateRange(String systemId, String email, 
            LocalDate from, LocalDate to, 
            int page, int size);
}
