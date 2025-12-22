package com.insuscan.util;

import com.insuscan.data.UserEntity;

// Insulin dose calculation utilities
// Uses standard formulas for Type 1 diabetes insulin dosing
public class InsulinCalculator {

    // Default values if user profile is incomplete
    private static final float DEFAULT_INSULIN_CARB_RATIO = 0.1f;  // 1:10 ratio
    private static final float DEFAULT_CORRECTION_FACTOR = 50f;    // 50 mg/dL per unit
    private static final int DEFAULT_TARGET_GLUCOSE = 100;         // 100 mg/dL

    // Warning thresholds
    private static final float MAX_SINGLE_DOSE = 30f;   // Units - trigger warning above this
    private static final float MIN_DOSE = 0.5f;         // Units - minimum practical dose

    // Calculate insulin dose for carbs (bolus dose)
    // Formula: carbs * (insulin units / carb grams)
    public static float calculateCarbDose(float totalCarbs, Float insulinCarbRatio) {
        float ratio = insulinCarbRatio != null ? insulinCarbRatio : DEFAULT_INSULIN_CARB_RATIO;
        return totalCarbs * ratio;
    }

    // Calculate correction dose for high blood sugar
    // Formula: (currentGlucose - targetGlucose) / correctionFactor
    public static float calculateCorrectionDose(
            int currentGlucose, 
            Integer targetGlucose, 
            Float correctionFactor) {
        
        int target = targetGlucose != null ? targetGlucose : DEFAULT_TARGET_GLUCOSE;
        float factor = correctionFactor != null ? correctionFactor : DEFAULT_CORRECTION_FACTOR;

        float correction = (currentGlucose - target) / factor;
        
        // Don't return negative correction (would mean blood sugar is low)
        return Math.max(0, correction);
    }

    // Calculate total recommended dose (carb dose + correction)
    public static float calculateTotalDose(
            float totalCarbs,
            Float insulinCarbRatio,
            Integer currentGlucose,
            Integer targetGlucose,
            Float correctionFactor) {

        float carbDose = calculateCarbDose(totalCarbs, insulinCarbRatio);
        
        float correctionDose = 0;
        if (currentGlucose != null) {
            correctionDose = calculateCorrectionDose(
                currentGlucose, targetGlucose, correctionFactor);
        }

        return carbDose + correctionDose;
    }

    // Calculate dose using user's profile settings
    public static float calculateDoseForUser(float totalCarbs, UserEntity user) {
        return calculateCarbDose(totalCarbs, user.getInsulinCarbRatio());
    }

    // Calculate full dose with glucose correction using user profile
    public static float calculateFullDoseForUser(
            float totalCarbs, 
            int currentGlucose, 
            UserEntity user) {
        
        return calculateTotalDose(
            totalCarbs,
            user.getInsulinCarbRatio(),
            currentGlucose,
            user.getTargetGlucose(),
            user.getCorrectionFactor()
        );
    }

    // Round dose to practical value (nearest 0.5 units)
    public static float roundDose(float dose) {
        return Math.round(dose * 2) / 2.0f;
    }

    // Check if dose is unusually high (might need confirmation)
    public static boolean isHighDose(float dose) {
        return dose > MAX_SINGLE_DOSE;
    }

    // Check if dose is below minimum practical amount
    public static boolean isBelowMinimum(float dose) {
        return dose > 0 && dose < MIN_DOSE;
    }

    // Get warning message if dose is unusual
    public static String getDoseWarning(float dose) {
        if (isHighDose(dose)) {
            return "Warning: Calculated dose (" + roundDose(dose) + 
                   " units) is unusually high. Please verify before injection.";
        }
        if (isBelowMinimum(dose)) {
            return "Note: Calculated dose is below " + MIN_DOSE + 
                   " units. Consider rounding or skipping.";
        }
        return null;
    }

    // Create calculation result with all details
    public static InsulinCalculationResult calculate(
            float totalCarbs,
            Float insulinCarbRatio,
            Integer currentGlucose,
            Integer targetGlucose,
            Float correctionFactor) {

        float carbDose = calculateCarbDose(totalCarbs, insulinCarbRatio);
        
        float correctionDose = 0;
        if (currentGlucose != null) {
            correctionDose = calculateCorrectionDose(
                currentGlucose, targetGlucose, correctionFactor);
        }

        float totalDose = carbDose + correctionDose;
        float roundedDose = roundDose(totalDose);
        String warning = getDoseWarning(totalDose);

        return new InsulinCalculationResult(
            carbDose,
            correctionDose,
            totalDose,
            roundedDose,
            warning
        );
    }

    // Result class for detailed calculation info
    public static class InsulinCalculationResult {
        private final float carbDose;
        private final float correctionDose;
        private final float totalDose;
        private final float roundedDose;
        private final String warning;

        public InsulinCalculationResult(
                float carbDose, 
                float correctionDose, 
                float totalDose,
                float roundedDose,
                String warning) {
            this.carbDose = carbDose;
            this.correctionDose = correctionDose;
            this.totalDose = totalDose;
            this.roundedDose = roundedDose;
            this.warning = warning;
        }

        public float getCarbDose() { return carbDose; }
        public float getCorrectionDose() { return correctionDose; }
        public float getTotalDose() { return totalDose; }
        public float getRoundedDose() { return roundedDose; }
        public String getWarning() { return warning; }
        public boolean hasWarning() { return warning != null; }

        @Override
        public String toString() {
            return "InsulinCalculationResult{" +
                    "carbDose=" + carbDose +
                    ", correctionDose=" + correctionDose +
                    ", roundedDose=" + roundedDose +
                    ", warning='" + warning + '\'' +
                    '}';
        }
    }
}
