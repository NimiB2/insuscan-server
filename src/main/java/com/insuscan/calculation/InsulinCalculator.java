package com.insuscan.calculation;

import java.util.ArrayList;
import java.util.List;

/**
 * Core logic for Insulin Dose Calculation.
 * <p>
 * This class implements the "Golden Logic" for medical insulin calculation using the standard
 * formulas agreed upon for the InsuScan platform.
 * <p>
 * <b>Formula:</b>
 * <pre>
 * 1. Carb Dose = Total Carbs (g) / Insulin-to-Carb Ratio (g/unit)
 * 2. Correction Dose = (Current Glucose - Target Glucose) / ISF
 *    (Only calculated if Current Glucose is provided. Can be negative to reduce meal dose)
 * 3. Base Dose = Carb Dose + Correction Dose
 * 4. Adjustments = Base Dose * (Sick% + Stress% - Exercise%)
 * 5. Total Dose = Base Dose + Adjustments (Floored at 0)
 * </pre>
 */
public class InsulinCalculator {

    // Default fallback values (used only if profile is incomplete/missing)
    private static final float DEFAULT_INSULIN_CARB_RATIO = InsulinDefaults.INSULIN_CARB_RATIO;
    private static final float DEFAULT_CORRECTION_FACTOR = InsulinDefaults.CORRECTION_FACTOR;
    private static final int DEFAULT_TARGET_GLUCOSE = InsulinDefaults.TARGET_GLUCOSE;

    /**
     * Calculates the recommended insulin dose based on the provided parameters.
     *
     * @param params The calculation parameters (user profile, meal carbs, context).
     * @return A detailed result object containing the dose breakdown.
     */
    public CalculationResult calculate(CalculationParams params) {
        
        // 1. Validate & Extract Profile Parameters
        //    We use "Grams per Unit" standard (e.g., 10g/unit).
        float icr = getValueOrDefault(params.getInsulinCarbRatio(), DEFAULT_INSULIN_CARB_RATIO);
        float isf = getValueOrDefault(params.getCorrectionFactor(), DEFAULT_CORRECTION_FACTOR);
        int targetGlucose = getValueOrDefault(params.getTargetGlucose(), DEFAULT_TARGET_GLUCOSE);
        
        float totalCarbs = params.getTotalCarbs() != null ? params.getTotalCarbs() : 0f;

        // 2. Calculate Carb Dose (Meal Bolus)
        //    Formula: Carbs / Ratio
        float carbDose = 0f;
        if (icr > 0) {
            carbDose = totalCarbs / icr;
        }

        // 3. Calculate Correction Dose
        //    Formula: (Current - Target) / ISF
        //    Note: Allowed to be negative (to handle below-target glucose).
        float correctionDose = 0f;
        Integer currentGlucose = params.getCurrentGlucose();
        
        if (currentGlucose != null && isf > 0) {
            correctionDose = (currentGlucose - targetGlucose) / isf;
        }

        // 4. Calculate Base Dose
        //    This is the sum of the meal requirement and the correction.
        float baseDose = carbDose + correctionDose;

        // 5. Apply Temporary Adjustments (Sick / Stress / Exercise)
        //    Adjustments are applied to the ENTIRE base dose (meal + correction).
        float sickAdj = calculateAdjustment(baseDose, params.getSickDayPercent());
        float stressAdj = calculateAdjustment(baseDose, params.getStressPercent());
        
        // Exercise Handling:
        // - Prioritize specific Radio Button selection (Light/Intense)
        // - Fallback to global "Exercise Mode" if defined in params (handled by Builder/Caller)
        float exerciseAdj = calculateExerciseAdjustment(baseDose, params);

        // 6. Calculate Final Total
        //    Sick/Stress ADD to dose. Exercise SUBTRACTS from dose.
        float finalTotal = baseDose + sickAdj + stressAdj - exerciseAdj;

        // Safety Floor: Dose cannot be negative.
        if (finalTotal < 0) {
            finalTotal = 0f;
        }

        // 7. No Step Rounding (User requested precise result)
        // We round to 2 decimal places for display/storage sanity, but do not force 0.5 increments.
        float roundedDose = (float) (Math.round(finalTotal * 100.0) / 100.0);

        // 8. Generate Status/Warning
        List<String> missingFields = params.getMissingFields();
        boolean profileComplete = params.isProfileComplete();
        String warning = profileComplete ? "" : "Profile incomplete - using defaults";

        // Return detailed result
        return new CalculationResult(
            carbDose, 
            correctionDose,
            sickAdj, 
            stressAdj, 
            exerciseAdj,
            finalTotal, 
            roundedDose, // Now represents the precise dose (max 2 decimals)
            warning, 
            profileComplete, 
            missingFields
        );
    }

    /**
     * Helper to calculate a percentage adjustment.
     * @param base The base amount to adjust.
     * @param percent The percentage (0-100).
     * @return The absolute amount to add/subtract.
     */
    private float calculateAdjustment(float base, int percent) {
        if (percent <= 0) return 0f;
        return base * (percent / 100f);
    }

    /**
     * Determines the exercise reduction amount.
     * Assumes params.getActivityLevel() is normalized ("light", "intense", "normal").
     */
    private float calculateExerciseAdjustment(float base, CalculationParams params) {
        if (params.getActivityLevel() == null) return 0f;
        
        String level = params.getActivityLevel().toLowerCase();
        float reductionPercent = 0f;

        if (level.equals("light")) {
            reductionPercent = params.getLightExercisePercent();
        } else if (level.equals("intense")) {
            reductionPercent = params.getIntenseExercisePercent();
        }
        
        // Make sure we have a valid positive percentage
        if (reductionPercent > 0) {
            return base * (reductionPercent / 100f);
        }
        
        return 0f;
    }

    /**
     * Rounds a float value to 2 decimal places.
     */
    private float roundToTwoDecimals(float value) {
        return (float) (Math.round(value * 100.0) / 100.0);
    }

    private float getValueOrDefault(Float value, float defaultValue) {
        return (value != null && value > 0) ? value : defaultValue;
    }

    private int getValueOrDefault(Integer value, int defaultValue) {
        return (value != null && value > 0) ? value : defaultValue;
    }
}
