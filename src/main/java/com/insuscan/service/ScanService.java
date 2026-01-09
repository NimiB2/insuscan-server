package com.insuscan.service;

import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.ScanRequestBoundary;

// Main scanning service that orchestrates the full workflow
public interface ScanService {

    // Full scan workflow: image -> food detection -> nutrition -> meal
    MealBoundary scanMeal(ScanRequestBoundary request);

    // Scan with portion analysis data
    MealBoundary scanMealWithPortion(ScanRequestBoundary request, 
                                      Float estimatedWeightGrams,
                                      Float portionConfidence);
}