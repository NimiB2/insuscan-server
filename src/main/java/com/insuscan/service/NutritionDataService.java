package com.insuscan.service;

import com.insuscan.boundary.NutritionInfo;
import java.util.List;

// Service for fetching nutrition data from USDA FoodData Central
public interface NutritionDataService {

    // Get nutrition info for a single food item
    NutritionInfo getNutritionInfo(String foodName);

    // Get nutrition info for multiple food items
    List<NutritionInfo> getNutritionInfoBatch(List<String> foodNames);

    // Search for foods matching a query
    List<NutritionInfo> searchFoods(String query, int maxResults);

    // Check if service is available
    boolean isServiceAvailable();
}
