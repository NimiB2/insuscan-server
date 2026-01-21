package com.insuscan.service;

import com.insuscan.boundary.InsulinCalculationBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.UserEntity;
import com.insuscan.util.ApiLogger;
import com.insuscan.util.InsulinCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Insulin calculation service implementation.
 * Supports carb dosing, glucose correction, and lifestyle adjustments.
 */
@Service
public class InsulinCalculationServiceImpl implements InsulinCalculationService {

    private static final Logger log = LoggerFactory.getLogger(InsulinCalculationServiceImpl.class);
    
    private final UserRepository userRepository;
    private final ApiLogger apiLogger;

    
    @Value("${spring.application.name}")
    private String systemId;

    // Default values when user profile is missing
    private static final float DEFAULT_INSULIN_CARB_RATIO = 0.1f;  // 1:10
    private static final float DEFAULT_CORRECTION_FACTOR = 50f;     // 50 mg/dL per unit
    private static final int DEFAULT_TARGET_GLUCOSE = 100;          // 100 mg/dL
    
    // Default adjustment percentages
    private static final int DEFAULT_SICK_ADJUSTMENT = 15;          // +15%
    private static final int DEFAULT_STRESS_ADJUSTMENT = 10;        // +10%
    private static final int DEFAULT_LIGHT_EXERCISE_ADJ = 15;       // -15%
    private static final int DEFAULT_INTENSE_EXERCISE_ADJ = 30;     // -30%

    // Safety thresholds
    private static final int LOW_GLUCOSE_THRESHOLD = 70;            // mg/dL
    private static final int HIGH_GLUCOSE_THRESHOLD = 250;          // mg/dL

    public InsulinCalculationServiceImpl(UserRepository userRepository, ApiLogger apiLogger) {
        this.userRepository = userRepository;
        this.apiLogger = apiLogger;
    }

    @Override
    public InsulinCalculationBoundary calculateDose(
            Float totalCarbs,
            Integer currentGlucose,
            UserIdBoundary userId) {
        // Call full method with no adjustments
        return calculateDoseWithAdjustments(
            totalCarbs, currentGlucose, "normal", false, false, userId);
    }

    @Override
    public InsulinCalculationBoundary calculateDoseWithAdjustments(
            Float totalCarbs,
            Integer currentGlucose,
            String activityLevel,
            Boolean sickModeEnabled,
            Boolean stressModeEnabled,
            UserIdBoundary userId) {
        
        long startTime = System.currentTimeMillis();
        
        // Validate inputs
        if (totalCarbs == null || totalCarbs < 0) {
            apiLogger.insulinCalcError("Total carbs must be provided and >= 0");
            throw new IllegalArgumentException("Total carbs must be provided and >= 0");
        }

        // Log start
        String userEmail = userId != null ? userId.getEmail() : null;
        apiLogger.insulinCalcStart(totalCarbs, currentGlucose, activityLevel, 
                                   sickModeEnabled, stressModeEnabled, userEmail);

        // Load user profile
        UserEntity user = loadUserProfile(userId);
        boolean hasProfile = user != null;
        
        // Get calculation parameters
        Float insulinCarbRatio = getInsulinCarbRatio(user);
        Float correctionFactor = getCorrectionFactor(user);
        Integer targetGlucose = getTargetGlucose(user);
        
        // Log parameters
        apiLogger.insulinCalcParams(insulinCarbRatio, correctionFactor, targetGlucose, hasProfile);
        
        // Log adjustment factors if any mode is enabled
        if (Boolean.TRUE.equals(sickModeEnabled) || Boolean.TRUE.equals(stressModeEnabled) 
            || (activityLevel != null && !"normal".equals(activityLevel))) {
            
            Integer sickPct = Boolean.TRUE.equals(sickModeEnabled) ? 
                (user != null && user.getSickDayAdjustment() != null ? user.getSickDayAdjustment() : DEFAULT_SICK_ADJUSTMENT) : null;
            Integer stressPct = Boolean.TRUE.equals(stressModeEnabled) ? 
                (user != null && user.getStressAdjustment() != null ? user.getStressAdjustment() : DEFAULT_STRESS_ADJUSTMENT) : null;
            Integer exercisePct = getExercisePercent(activityLevel, user);
            
            apiLogger.insulinCalcAdjustments(sickPct, stressPct, exercisePct, hasProfile);
        }

        // Build ratio display string
        String ratioDisplay = buildRatioDisplay(insulinCarbRatio, hasProfile);

        // Step 1: Calculate carb dose
        float carbDose = InsulinCalculator.calculateCarbDose(totalCarbs, insulinCarbRatio);
        
        // Step 2: Calculate correction dose
        float correctionDose = 0f;
        if (currentGlucose != null && currentGlucose > 0) {
            correctionDose = InsulinCalculator.calculateCorrectionDose(
                currentGlucose, targetGlucose, correctionFactor);
        }
        
        // Step 3: Calculate base dose
        float baseDose = carbDose + correctionDose;
        
        // Step 4: Apply adjustments
        float sickAdj = calculateSickAdjustment(baseDose, sickModeEnabled, user);
        float stressAdj = calculateStressAdjustment(baseDose, stressModeEnabled, user);
        float exerciseAdj = calculateExerciseAdjustment(baseDose, activityLevel, user);
        
        // Step 5: Calculate final dose
        float finalDose = baseDose + sickAdj + stressAdj + exerciseAdj;
        if (finalDose < 0) {
            finalDose = 0f;
        }
        
        // Log the breakdown
        apiLogger.insulinCalcBreakdown(carbDose, correctionDose, baseDose, 
                                        sickAdj, stressAdj, exerciseAdj, finalDose);
        
        // Round to practical value
        float roundedDose = InsulinCalculator.roundDose(finalDose);
        
        // Generate warnings
        String warning = buildWarnings(currentGlucose, finalDose);
        if (warning != null) {
            apiLogger.insulinCalcWarning(warning);
        }

        // Log completion
        long totalTime = System.currentTimeMillis() - startTime;
        apiLogger.insulinCalcComplete(finalDose, roundedDose, totalTime);

        // Build response (same as before)
        InsulinCalculationBoundary response = new InsulinCalculationBoundary();
        // ... rest of the response building code ...
        
        return response;
    }

    // Helper method for getting exercise percent
    private Integer getExercisePercent(String activityLevel, UserEntity user) {
        if (activityLevel == null || "normal".equalsIgnoreCase(activityLevel)) {
            return null;
        }
        
        if ("light".equalsIgnoreCase(activityLevel)) {
            return user != null && user.getLightExerciseAdjustment() != null ? 
                user.getLightExerciseAdjustment() : DEFAULT_LIGHT_EXERCISE_ADJ;
        } else if ("intense".equalsIgnoreCase(activityLevel)) {
            return user != null && user.getIntenseExerciseAdjustment() != null ? 
                user.getIntenseExerciseAdjustment() : DEFAULT_INTENSE_EXERCISE_ADJ;
        }
        return null;
    }

    // ===== Helper Methods =====

    private UserEntity loadUserProfile(UserIdBoundary userId) {
        if (userId == null || userId.getEmail() == null) {
            return null;
        }
        
        try {
            String userDocId = (userId.getSystemId() != null ? userId.getSystemId() : systemId) 
                    + "_" + userId.getEmail();
            return userRepository.findById(userDocId).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load user profile: {}", e.getMessage());
            return null;
        }
    }

    private Float getInsulinCarbRatio(UserEntity user) {
        if (user != null && user.getInsulinCarbRatio() != null) {
            return user.getInsulinCarbRatio();
        }
        return DEFAULT_INSULIN_CARB_RATIO;
    }

    private Float getCorrectionFactor(UserEntity user) {
        if (user != null && user.getCorrectionFactor() != null) {
            return user.getCorrectionFactor();
        }
        return DEFAULT_CORRECTION_FACTOR;
    }

    private Integer getTargetGlucose(UserEntity user) {
        if (user != null && user.getTargetGlucose() != null) {
            return user.getTargetGlucose();
        }
        return DEFAULT_TARGET_GLUCOSE;
    }

    private String buildRatioDisplay(Float ratio, boolean fromProfile) {
        float icr = 1.0f / ratio;  // Convert back to readable format
        String source = fromProfile ? "user profile" : "default";
        return String.format("1:%.0f (%s)", icr, source);
    }

    private float calculateSickAdjustment(float baseDose, Boolean enabled, UserEntity user) {
        if (!Boolean.TRUE.equals(enabled)) {
            return 0f;
        }
        
        int percent = DEFAULT_SICK_ADJUSTMENT;
        if (user != null && user.getSickDayAdjustment() != null) {
            percent = user.getSickDayAdjustment();
        }
        
        return baseDose * (percent / 100f);
    }

    private float calculateStressAdjustment(float baseDose, Boolean enabled, UserEntity user) {
        if (!Boolean.TRUE.equals(enabled)) {
            return 0f;
        }
        
        int percent = DEFAULT_STRESS_ADJUSTMENT;
        if (user != null && user.getStressAdjustment() != null) {
            percent = user.getStressAdjustment();
        }
        
        return baseDose * (percent / 100f);
    }

    private float calculateExerciseAdjustment(float baseDose, String activityLevel, UserEntity user) {
        if (activityLevel == null || "normal".equalsIgnoreCase(activityLevel)) {
            return 0f;
        }
        
        int percent = 0;
        
        if ("light".equalsIgnoreCase(activityLevel)) {
            percent = DEFAULT_LIGHT_EXERCISE_ADJ;
            if (user != null && user.getLightExerciseAdjustment() != null) {
                percent = user.getLightExerciseAdjustment();
            }
        } else if ("intense".equalsIgnoreCase(activityLevel)) {
            percent = DEFAULT_INTENSE_EXERCISE_ADJ;
            if (user != null && user.getIntenseExerciseAdjustment() != null) {
                percent = user.getIntenseExerciseAdjustment();
            }
        }
        
        // Exercise reduces dose, so return negative
        return -(baseDose * (percent / 100f));
    }

    private String buildWarnings(Integer currentGlucose, float finalDose) {
        StringBuilder warnings = new StringBuilder();
        
        // Low glucose warning - critical!
        if (currentGlucose != null && currentGlucose < LOW_GLUCOSE_THRESHOLD) {
            warnings.append("⚠️ LOW GLUCOSE! Treat hypoglycemia before eating. ");
        }
        
        // High glucose warning
        if (currentGlucose != null && currentGlucose > HIGH_GLUCOSE_THRESHOLD) {
            warnings.append("⚠️ High glucose detected. Consider checking ketones. ");
        }
        
        // High dose warning
        if (InsulinCalculator.isHighDose(finalDose)) {
            warnings.append("⚠️ Dose is unusually high (")
                    .append(InsulinCalculator.roundDose(finalDose))
                    .append(" units). Please verify before injection. ");
        }
        
        // Low dose warning
        if (InsulinCalculator.isBelowMinimum(finalDose)) {
            warnings.append("Note: Dose is below 0.5 units. Consider skipping or rounding. ");
        }
        
        return warnings.length() > 0 ? warnings.toString().trim() : null;
    }
}