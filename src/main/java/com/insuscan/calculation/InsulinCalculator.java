package com.insuscan.calculation;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates insulin doses based on carbs, glucose, and user profile.
 * Supports temporary adjustments for sick days, stress, and exercise.
 */
public class InsulinCalculator {

    // Defaults when user profile is incomplete
    private static final float DEFAULT_INSULIN_CARB_RATIO = 10f;
    private static final float DEFAULT_CORRECTION_FACTOR = 50f;
    private static final int DEFAULT_TARGET_GLUCOSE = 100;

    /**
     * Main calculation - returns full breakdown for display
     */
    public CalculationResult calculate(CalculationParams params) {
        
        // 1. Get profile values or use defaults
        float ratio = getValueOrDefault(params.getInsulinCarbRatio(), DEFAULT_INSULIN_CARB_RATIO);
        float isf = getValueOrDefault(params.getCorrectionFactor(), DEFAULT_CORRECTION_FACTOR);
        int target = getValueOrDefault(params.getTargetGlucose(), DEFAULT_TARGET_GLUCOSE);
        float totalCarbs = params.getTotalCarbs() != null ? params.getTotalCarbs() : 0f;

        // 2. Base carb dose: carbs / ratio
        float carbDose = totalCarbs / ratio;

        // 3. Correction dose: (current - target) / ISF
        float correctionDose = 0f;
        if (params.getCurrentGlucose() != null && params.getCurrentGlucose() > target) {
            correctionDose = (params.getCurrentGlucose() - target) / isf;
        }

        // 4. Calculate adjustments on raw total
        float rawTotal = carbDose + correctionDose;
        
        float sickAdj = calculatePercentAdjustment(rawTotal, params.getSickDayPercent());
        float stressAdj = calculatePercentAdjustment(rawTotal, params.getStressPercent());
        float exerciseAdj = calculateExerciseAdjustment(rawTotal, params);

        // 5. Final sum (exercise reduces dose)
        float finalTotal = rawTotal + sickAdj + stressAdj - exerciseAdj;
        if (finalTotal < 0) finalTotal = 0;

        // 6. Round to nearest 0.5
        float roundedDose = roundDose(finalTotal);

        // 7. Check profile completeness
        List<String> missing = new ArrayList<>();
        if (params.getInsulinCarbRatio() == null) missing.add("Insulin:Carb Ratio");
        if (params.getCorrectionFactor() == null) missing.add("Correction Factor");
        if (params.getTargetGlucose() == null) missing.add("Target Glucose");
        
        boolean profileComplete = missing.isEmpty();
        String warning = profileComplete ? "" : "Profile incomplete - using defaults";

        return new CalculationResult(
            carbDose, correctionDose,
            sickAdj, stressAdj, exerciseAdj,
            finalTotal, roundedDose,
            warning, profileComplete, missing
        );
    }

    /**
     * Rounds dose to nearest 0.5 unit
     */
    public static float roundDose(float dose) {
        return Math.round(dose * 2) / 2.0f;
    }

    // Helper: get value or default if null/invalid
    private float getValueOrDefault(Float value, float defaultVal) {
        return (value != null && value > 0) ? value : defaultVal;
    }

    private int getValueOrDefault(Integer value, int defaultVal) {
        return (value != null && value > 0) ? value : defaultVal;
    }

    // Helper: calculate percentage adjustment
    private float calculatePercentAdjustment(float base, int percent) {
        if (percent <= 0) return 0f;
        return base * (percent / 100f);
    }

    // Helper: calculate exercise adjustment based on activity level
    private float calculateExerciseAdjustment(float base, CalculationParams params) {
        if (params.getActivityLevel() == null) return 0f;
        
        String level = params.getActivityLevel().toLowerCase();
        if (level.equals("light") && params.getLightExercisePercent() > 0) {
            return base * (params.getLightExercisePercent() / 100f);
        }
        if (level.equals("intense") && params.getIntenseExercisePercent() > 0) {
            return base * (params.getIntenseExercisePercent() / 100f);
        }
        return 0f;
    }
    
    public static float calculateCarbDose(float totalCarbs, float insulinCarbRatio) {
        if (insulinCarbRatio <= 0) return 0f;
        return totalCarbs * insulinCarbRatio;
    }

    public static float calculateCorrectionDose(int currentGlucose, int targetGlucose, float correctionFactor) {
        if (currentGlucose <= targetGlucose || correctionFactor <= 0) return 0f;
        return (currentGlucose - targetGlucose) / correctionFactor;
    }

    public static boolean isHighDose(float dose) {
        return dose > 15f;  // threshold for warning
    }

    public static boolean isBelowMinimum(float dose) {
        return dose > 0 && dose < 0.5f;
    }
    
    public static class InsulinCalculationResult extends CalculationResult {
        public InsulinCalculationResult(float carbDose, float correctionDose,
                                        float sickAdjustment, float stressAdjustment, 
                                        float exerciseAdjustment, float totalDose, 
                                        float roundedDose, String warning, 
                                        boolean profileComplete, List<String> missingFields) {
            super(carbDose, correctionDose, sickAdjustment, stressAdjustment,
                  exerciseAdjustment, totalDose, roundedDose, warning, 
                  profileComplete, missingFields);
        }
        
        public float getBaseDose() { return getCarbDose() + getCorrectionDose(); }
        public boolean hasWarning() { return getWarning() != null && !getWarning().isEmpty(); }
    }

    // Simple calculation for scan service
    public static InsulinCalculationResult calculateSimple(float totalCarbs, CalculationParams params) {
        InsulinCalculator calculator = new InsulinCalculator();
        CalculationResult result = calculator.calculate(
            new CalculationParams.Builder()
                .withTotalCarbs(totalCarbs)
                .withInsulinCarbRatio(params.getInsulinCarbRatio())
                .withCorrectionFactor(params.getCorrectionFactor())
                .withTargetGlucose(params.getTargetGlucose())
                .withCurrentGlucose(params.getCurrentGlucose())
                .withActivityLevel(params.getActivityLevel())
                .withSickDayPercent(params.getSickDayPercent())
                .withStressPercent(params.getStressPercent())
                .withLightExercisePercent(params.getLightExercisePercent())
                .withIntenseExercisePercent(params.getIntenseExercisePercent())
                .build()
        );
        
        return new InsulinCalculationResult(
            result.getCarbDose(), result.getCorrectionDose(),
            result.getSickAdjustment(), result.getStressAdjustment(),
            result.getExerciseAdjustment(), result.getTotalDose(),
            result.getRoundedDose(), result.getWarning(),
            result.isProfileComplete(), result.getMissingFields()
        );
    }
}