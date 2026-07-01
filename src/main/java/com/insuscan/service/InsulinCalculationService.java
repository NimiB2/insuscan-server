package com.insuscan.service;

import com.insuscan.boundary.InsulinCalculationBoundary;
import com.insuscan.boundary.UserIdBoundary;

/**
 * Service for calculating insulin doses based on carbs and glucose.
 * Implements standard bolus calculation formulas used in diabetes management.
 */
public interface InsulinCalculationService {

    InsulinCalculationBoundary calculateDose(
            Float totalCarbs,
            Integer currentGlucose,
            UserIdBoundary userId);

    InsulinCalculationBoundary calculateDose(
            Float totalCarbs,
            Integer currentGlucose,
            UserIdBoundary userId,
            Float planIcr,
            Float planIsf,
            Integer planTargetGlucose);
}