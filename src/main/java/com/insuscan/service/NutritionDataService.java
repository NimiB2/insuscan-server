package com.insuscan.service;

import com.insuscan.boundary.NutritionInfo;
import java.util.List;

public interface NutritionDataService {
    
    NutritionInfo getNutritionInfo(String foodName);
    
    List<NutritionInfo> getNutritionInfoBatch(List<String> foodNames);
    
    List<NutritionInfo> searchFoods(String query, int maxResults);

    List<NutritionInfo> searchCandidates(String baseIngredient);

    boolean isServiceAvailable();
}
