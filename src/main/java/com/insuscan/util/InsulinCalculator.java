package com.insuscan.util;

import com.insuscan.data.UserEntity;
import java.util.ArrayList;
import java.util.List;

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

 // Default adjustment percentages
    private static final int DEFAULT_SICK_PERCENT = 15;
    private static final int DEFAULT_STRESS_PERCENT = 10;
    private static final int DEFAULT_LIGHT_EXERCISE_PERCENT = 15;
    private static final int DEFAULT_INTENSE_EXERCISE_PERCENT = 30;
    
 // ==================== CALCULATION PARAMS ====================

    /**
     * Holds validated calculation parameters.
     * Use Builder to construct - handles null checks and tracks missing fields.
     */
    public static class CalculationParams {
        private final Float insulinCarbRatio;
        private final Float correctionFactor;
        private final Integer targetGlucose;
        private final int sickDayPercent;
        private final int stressPercent;
        private final int lightExercisePercent;
        private final int intenseExercisePercent;
        private final boolean profileComplete;
        private final List<String> missingFields;

        private CalculationParams(Builder builder) {
            this.insulinCarbRatio = builder.insulinCarbRatio;
            this.correctionFactor = builder.correctionFactor;
            this.targetGlucose = builder.targetGlucose;
            this.sickDayPercent = builder.sickDayPercent;
            this.stressPercent = builder.stressPercent;
            this.lightExercisePercent = builder.lightExercisePercent;
            this.intenseExercisePercent = builder.intenseExercisePercent;
            this.profileComplete = builder.missingFields.isEmpty();
            this.missingFields = new ArrayList<>(builder.missingFields);
        }

        // Getters
        public Float getInsulinCarbRatio() { return insulinCarbRatio; }
        public Float getCorrectionFactor() { return correctionFactor; }
        public Integer getTargetGlucose() { return targetGlucose; }
        public int getSickDayPercent() { return sickDayPercent; }
        public int getStressPercent() { return stressPercent; }
        public int getLightExercisePercent() { return lightExercisePercent; }
        public int getIntenseExercisePercent() { return intenseExercisePercent; }
        public boolean isProfileComplete() { return profileComplete; }
        public List<String> getMissingFields() { return missingFields; }

        /**
         * Builder for CalculationParams.
         * Loads from user profile and tracks missing required fields.
         */
        public static class Builder {
            private Float insulinCarbRatio = null;
            private Float correctionFactor = null;
            private Integer targetGlucose = null;
            private int sickDayPercent = DEFAULT_SICK_PERCENT;
            private int stressPercent = DEFAULT_STRESS_PERCENT;
            private int lightExercisePercent = DEFAULT_LIGHT_EXERCISE_PERCENT;
            private int intenseExercisePercent = DEFAULT_INTENSE_EXERCISE_PERCENT;
            private List<String> missingFields = new ArrayList<>();

            public Builder() {}

            /**
             * Load parameters from user entity.
             * Required fields (ICR, ISF, Target) are tracked if missing.
             * Optional fields (adjustments) use defaults if not set.
             */
            public Builder fromUser(UserEntity user) {
                if (user == null) {
                    missingFields.add("User profile not found");
                    return this;
                }

                // Required: Insulin to Carb Ratio
                if (user.getInsulinCarbRatio() != null && user.getInsulinCarbRatio() > 0) {
                    this.insulinCarbRatio = user.getInsulinCarbRatio();
                } else {
                    missingFields.add("Insulin to Carb Ratio (ICR)");
                }

                // Required: Correction Factor (ISF)
                if (user.getCorrectionFactor() != null && user.getCorrectionFactor() > 0) {
                    this.correctionFactor = user.getCorrectionFactor();
                } else {
                    missingFields.add("Correction Factor (ISF)");
                }

                // Required: Target Glucose
                if (user.getTargetGlucose() != null && user.getTargetGlucose() > 0) {
                    this.targetGlucose = user.getTargetGlucose();
                } else {
                    missingFields.add("Target Glucose");
                }

                // Optional: Adjustment percentages (use defaults if not set)
                if (user.getSickDayAdjustment() != null) {
                    this.sickDayPercent = user.getSickDayAdjustment();
                }
                if (user.getStressAdjustment() != null) {
                    this.stressPercent = user.getStressAdjustment();
                }
                if (user.getLightExerciseAdjustment() != null) {
                    this.lightExercisePercent = user.getLightExerciseAdjustment();
                }
                if (user.getIntenseExerciseAdjustment() != null) {
                    this.intenseExercisePercent = user.getIntenseExerciseAdjustment();
                }

                return this;
            }

            public CalculationParams build() {
                return new CalculationParams(this);
            }
        }
    }
    
    
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

    
 // ==================== FULL CALCULATION METHODS ====================

    /**
     * Full calculation with all adjustments.
     * Returns empty result with missing fields if profile incomplete.
     */
    public static InsulinCalculationResult calculateFull(
            float totalCarbs,
            Integer currentGlucose,
            String activityLevel,
            boolean sickModeEnabled,
            boolean stressModeEnabled,
            CalculationParams params) {

        // Profile incomplete - return result with missing fields, no calculation
        if (!params.isProfileComplete()) {
            return new InsulinCalculationResult(
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                null, false, params.getMissingFields()
            );
        }

        // Step 1: Carb dose
        float carbDose = totalCarbs * params.getInsulinCarbRatio();

        // Step 2: Correction dose
        float correctionDose = 0f;
        if (currentGlucose != null && params.getCorrectionFactor() > 0) {
            int target = params.getTargetGlucose();
            float isf = params.getCorrectionFactor();

            if (currentGlucose > target) {
                // Above target - positive correction
                correctionDose = (currentGlucose - target) / isf;
            } else if (currentGlucose < target) {
                // Below target - negative correction (limited to 50% of carb dose)
                correctionDose = (currentGlucose - target) / isf;
                float maxNegative = -(carbDose * 0.5f);
                correctionDose = Math.max(correctionDose, maxNegative);
            }
        }

        // Step 3: Base dose
        float baseDose = carbDose + correctionDose;

        // Step 4: Adjustments
        float sickAdj = 0f;
        float stressAdj = 0f;
        float exerciseAdj = 0f;

        if (sickModeEnabled && params.getSickDayPercent() > 0) {
            sickAdj = baseDose * (params.getSickDayPercent() / 100f);
        }

        if (stressModeEnabled && params.getStressPercent() > 0) {
            stressAdj = baseDose * (params.getStressPercent() / 100f);
        }

        if ("light".equalsIgnoreCase(activityLevel)) {
            exerciseAdj = -(baseDose * (params.getLightExercisePercent() / 100f));
        } else if ("intense".equalsIgnoreCase(activityLevel)) {
            exerciseAdj = -(baseDose * (params.getIntenseExercisePercent() / 100f));
        }

        // Step 5: Total dose
        float totalDose = baseDose + sickAdj + stressAdj + exerciseAdj;
        if (totalDose < 0) {
            totalDose = 0f;
        }

        float roundedDose = roundDose(totalDose);

        // Step 6: Warnings
        String warning = buildWarnings(currentGlucose, totalDose);

        return new InsulinCalculationResult(
            carbDose, correctionDose, sickAdj, stressAdj, exerciseAdj,
            baseDose, totalDose, roundedDose,
            warning, true, new ArrayList<>()
        );
    }

    /**
     * Simple calculation - carbs only, no glucose correction or adjustments.
     */
    public static InsulinCalculationResult calculateSimple(float totalCarbs, CalculationParams params) {
        return calculateFull(totalCarbs, null, "normal", false, false, params);
    }

    /**
     * Build warning messages based on glucose level and dose.
     */
    private static String buildWarnings(Integer currentGlucose, float dose) {
        StringBuilder warnings = new StringBuilder();

        // Critical: Low glucose
        if (currentGlucose != null && currentGlucose < 70) {
            warnings.append("⚠️ LOW GLUCOSE! Treat hypoglycemia before eating. ");
        }

        // Warning: High glucose
        if (currentGlucose != null && currentGlucose > 250) {
            warnings.append("⚠️ High glucose - consider checking ketones. ");
        }

        // Warning: High dose
        if (isHighDose(dose)) {
            warnings.append("⚠️ Dose exceeds ").append((int) MAX_SINGLE_DOSE)
                    .append(" units - verify before injection. ");
        }

        // Note: Low dose
        if (isBelowMinimum(dose)) {
            warnings.append("Note: Dose below ").append(MIN_DOSE).append(" units. ");
        }

        return warnings.length() > 0 ? warnings.toString().trim() : null;
    }
  
 // ==================== CALCULATION RESULT ====================

    /**
     * Complete calculation result with full breakdown.
     */
    public static class InsulinCalculationResult {
        private final float carbDose;
        private final float correctionDose;
        private final float sickAdjustment;
        private final float stressAdjustment;
        private final float exerciseAdjustment;
        private final float baseDose;
        private final float totalDose;
        private final float roundedDose;
        private final String warning;
        private final boolean profileComplete;
        private final List<String> missingFields;

        // Full constructor with all adjustments
        public InsulinCalculationResult(
                float carbDose, float correctionDose,
                float sickAdjustment, float stressAdjustment, float exerciseAdjustment,
                float baseDose, float totalDose, float roundedDose,
                String warning, boolean profileComplete, List<String> missingFields) {
            this.carbDose = carbDose;
            this.correctionDose = correctionDose;
            this.sickAdjustment = sickAdjustment;
            this.stressAdjustment = stressAdjustment;
            this.exerciseAdjustment = exerciseAdjustment;
            this.baseDose = baseDose;
            this.totalDose = totalDose;
            this.roundedDose = roundedDose;
            this.warning = warning;
            this.profileComplete = profileComplete;
            this.missingFields = missingFields != null ? missingFields : new ArrayList<>();
        }

        // Simple constructor (backwards compatible)
        public InsulinCalculationResult(
                float carbDose, float correctionDose,
                float totalDose, float roundedDose, String warning) {
            this(carbDose, correctionDose, 0f, 0f, 0f, 
                 carbDose + correctionDose, totalDose, roundedDose, 
                 warning, true, new ArrayList<>());
        }

        // Getters
        public float getCarbDose() { return carbDose; }
        public float getCorrectionDose() { return correctionDose; }
        public float getSickAdjustment() { return sickAdjustment; }
        public float getStressAdjustment() { return stressAdjustment; }
        public float getExerciseAdjustment() { return exerciseAdjustment; }
        public float getBaseDose() { return baseDose; }
        public float getTotalDose() { return totalDose; }
        public float getRoundedDose() { return roundedDose; }
        public String getWarning() { return warning; }
        public boolean hasWarning() { return warning != null && !warning.isEmpty(); }
        public boolean isProfileComplete() { return profileComplete; }
        public List<String> getMissingFields() { return missingFields; }

        @Override
        public String toString() {
            return "InsulinCalculationResult{" +
                    "carbDose=" + carbDose +
                    ", correctionDose=" + correctionDose +
                    ", roundedDose=" + roundedDose +
                    ", profileComplete=" + profileComplete +
                    ", warning='" + warning + '\'' +
                    '}';
        }
    }
}
