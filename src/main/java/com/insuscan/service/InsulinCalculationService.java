package com.insuscan.service;

import com.insuscan.boundary.InsulinCalculationBoundary;
import com.insuscan.boundary.UserIdBoundary;

/**
 * Service for calculating insulin doses based on carbs, glucose, and adjustments.
 * Implements standard bolus calculation formulas used in diabetes management.
 */

public interface InsulinCalculationService {
	 /**
     * Calculate insulin dose - simple version (backwards compatible)
     */
    InsulinCalculationBoundary calculateDose(
            Float totalCarbs,
            Integer currentGlucose,
            UserIdBoundary userId);
    
    /**
     * Calculate insulin dose - full version with all adjustments
     * 
     * @param totalCarbs Total carbohydrates in grams
     * @param currentGlucose Current blood glucose in mg/dL (optional)
     * @param activityLevel Activity level: "normal", "light", or "intense"
     * @param sickModeEnabled True if user has sick day mode on
     * @param stressModeEnabled True if user has stress mode on
     * @param userId User ID for personalized settings
     * @return Detailed calculation with full breakdown
     */
    InsulinCalculationBoundary calculateDoseWithAdjustments(
            Float totalCarbs,
            Integer currentGlucose,
            String activityLevel,
            Boolean sickModeEnabled,
            Boolean stressModeEnabled,
            UserIdBoundary userId);
}
