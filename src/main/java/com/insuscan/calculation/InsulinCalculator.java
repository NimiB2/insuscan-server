package com.insuscan.calculation;

import com.insuscan.util.NumberUtils;

import java.util.List;

/**
 * Core logic for Insulin Dose Calculation.
 * <p>
 * <b>Formula:</b>
 * <pre>
 * 1. Carb Dose = Total Carbs (g) / Insulin-to-Carb Ratio (g/unit)
 * 2. Correction Dose = (Current Glucose - Target Glucose) / ISF
 *    (Only calculated if Current Glucose is provided. Can be negative to reduce meal dose)
 * 3. Total Dose = Carb Dose + Correction Dose (Floored at 0)
 * </pre>
 */
public class InsulinCalculator {

    private static final float DEFAULT_INSULIN_CARB_RATIO = InsulinDefaults.INSULIN_CARB_RATIO;
    private static final float DEFAULT_CORRECTION_FACTOR = InsulinDefaults.CORRECTION_FACTOR;
    private static final int DEFAULT_TARGET_GLUCOSE = InsulinDefaults.TARGET_GLUCOSE;

    public CalculationResult calculate(CalculationParams params) {

        // 1. Validate & Extract Profile Parameters (Grams per Unit standard)
        float icr = getValueOrDefault(params.getInsulinCarbRatio(), DEFAULT_INSULIN_CARB_RATIO);
        float isf = getValueOrDefault(params.getCorrectionFactor(), DEFAULT_CORRECTION_FACTOR);
        int targetGlucose = getValueOrDefault(params.getTargetGlucose(), DEFAULT_TARGET_GLUCOSE);
        float totalCarbs = params.getTotalCarbs() != null ? params.getTotalCarbs() : 0f;

        // 2. Carb Dose (Meal Bolus): Carbs / Ratio
        float carbDose = 0f;
        if (icr > 0) {
            carbDose = totalCarbs / icr;
        }

        // 3. Correction Dose: (Current - Target) / ISF. Allowed to be negative.
        float correctionDose = 0f;
        Integer currentGlucose = params.getCurrentGlucose();
        if (currentGlucose != null && isf > 0) {
            correctionDose = (currentGlucose - targetGlucose) / isf;
        }

        // 4. Total Dose = carb + correction (floored at 0)
        float totalDose = carbDose + correctionDose;
        if (totalDose < 0) {
            totalDose = 0f;
        }

        float roundedDose = NumberUtils.roundTo2Decimals(totalDose);

        // 5. Profile status
        List<String> missingFields = params.getMissingFields();
        boolean profileComplete = params.isProfileComplete();
        String warning = profileComplete ? "" : "Profile incomplete - using defaults";

        return new CalculationResult(
            carbDose, correctionDose,
            totalDose, roundedDose,
            warning, profileComplete, missingFields
        );
    }

    private float getValueOrDefault(Float value, float defaultValue) {
        return (value != null && value > 0) ? value : defaultValue;
    }

    private int getValueOrDefault(Integer value, int defaultValue) {
        return (value != null && value > 0) ? value : defaultValue;
    }
}