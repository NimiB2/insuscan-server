package com.insuscan.service;

import com.insuscan.boundary.InsulinCalculationBoundary;

/**
 * Service for calculating insulin doses based on carbs and glucose levels
 */
public interface InsulinCalculationService {
    
    /**
     * Calculate insulin dose from carbs and optional glucose correction
     * Works with or without user profile (uses defaults if user not found)
     * 
     * @param totalCarbs Total carbohydrates in grams
     * @param currentGlucose Current blood glucose in mg/dL (optional, for correction)
     * @param userId User ID for personalized settings (optional)
     * @return Detailed calculation result with breakdown
     */
    InsulinCalculationBoundary calculateDose(
            Float totalCarbs,
            Integer currentGlucose,
            com.insuscan.boundary.UserIdBoundary userId);
}
