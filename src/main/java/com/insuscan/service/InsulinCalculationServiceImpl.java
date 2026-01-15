package com.insuscan.service;

import com.insuscan.boundary.InsulinCalculationBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.UserEntity;
import com.insuscan.util.InsulinCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of insulin calculation service
 * Uses InsulinCalculationBoundary for structured response
 */
@Service
public class InsulinCalculationServiceImpl implements InsulinCalculationService {

    private static final Logger log = LoggerFactory.getLogger(InsulinCalculationServiceImpl.class);
    
    private final UserRepository userRepository;
    
    @Value("${spring.application.name}")
    private String systemId;

    // Default values (from InsulinCalculator)
    private static final float DEFAULT_INSULIN_CARB_RATIO = 0.1f;  // 1:10 ratio
    private static final float DEFAULT_CORRECTION_FACTOR = 50f;    // 50 mg/dL per unit
    private static final int DEFAULT_TARGET_GLUCOSE = 100;         // 100 mg/dL

    public InsulinCalculationServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public InsulinCalculationBoundary calculateDose(
            Float totalCarbs,
            Integer currentGlucose,
            UserIdBoundary userId) {
        
        // Validate inputs
        if (totalCarbs == null || totalCarbs <= 0) {
            throw new IllegalArgumentException("Total carbs must be provided and greater than 0");
        }

        // Try to get user profile for personalized settings
        UserEntity user = null;
        Float insulinCarbRatio = DEFAULT_INSULIN_CARB_RATIO;
        Float correctionFactor = DEFAULT_CORRECTION_FACTOR;
        Integer targetGlucose = DEFAULT_TARGET_GLUCOSE;
        String insulinCarbRatioUsed = String.format("1:%.0f (default)", 1.0f / DEFAULT_INSULIN_CARB_RATIO);

        if (userId != null) {
            try {
                String userDocId = (userId.getSystemId() != null ? userId.getSystemId() : systemId) 
                        + "_" + userId.getEmail();
                user = userRepository.findById(userDocId)
                        .orElse(null);
                
                if (user != null) {
                    // Use user's personalized settings
                    insulinCarbRatio = user.getInsulinCarbRatio();
                    correctionFactor = user.getCorrectionFactor();
                    targetGlucose = user.getTargetGlucose();
                    
                    // Build display string for what was used
                    if (insulinCarbRatio != null) {
                        insulinCarbRatioUsed = String.format("1:%.0f (user profile)", 1.0f / insulinCarbRatio);
                    }
                    
                    log.info("Using personalized settings for user: {}", userId.getEmail());
                } else {
                    log.warn("User not found: {}, using default values", userId.getEmail());
                }
            } catch (Exception e) {
                log.warn("Error fetching user profile, using defaults: {}", e.getMessage());
            }
        }

        // Calculate doses using InsulinCalculator
        InsulinCalculator.InsulinCalculationResult result;
        
        if (currentGlucose != null && currentGlucose > 0) {
            // Full calculation with glucose correction
            result = InsulinCalculator.calculate(
                    totalCarbs,
                    insulinCarbRatio,
                    currentGlucose,
                    targetGlucose,
                    correctionFactor
            );
        } else {
            // Carb dose only (no correction)
            float carbDose = InsulinCalculator.calculateCarbDose(totalCarbs, insulinCarbRatio);
            result = new InsulinCalculator.InsulinCalculationResult(
                    carbDose,
                    0f,  // No correction
                    carbDose,
                    InsulinCalculator.roundDose(carbDose),
                    InsulinCalculator.getDoseWarning(carbDose)
            );
        }

        // Build response using InsulinCalculationBoundary
        InsulinCalculationBoundary response = new InsulinCalculationBoundary();
        response.setTotalCarbs(totalCarbs);
        response.setCurrentGlucose(currentGlucose != null ? currentGlucose.floatValue() : null);
        response.setUserId(userId);
        
        response.setCarbDose(InsulinCalculator.roundDose(result.getCarbDose()));
        response.setCorrectionDose(InsulinCalculator.roundDose(result.getCorrectionDose()));
        response.setTotalRecommendedDose(result.getRoundedDose());
        
        response.setInsulinCarbRatioUsed(insulinCarbRatioUsed);
        response.setCorrectionFactorUsed(correctionFactor);
        response.setTargetGlucoseUsed(targetGlucose);
        
        if (result.hasWarning()) {
            response.setWarning(result.getWarning());
        }

        log.info("Calculated insulin dose: {} units (carbs: {}g, correction: {} units)", 
                result.getRoundedDose(), 
                totalCarbs,
                result.getCorrectionDose());

        return response;
    }
}
