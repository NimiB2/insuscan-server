package com.insuscan.calculation;

import java.util.List;

/**
 * Input parameters for insulin dose calculation.
 * Uses Builder pattern for clean construction.
 */
public class CalculationParams {
    
    // User profile settings
    private final Float insulinCarbRatio;
    private final Float correctionFactor;
    private final Integer targetGlucose;
    
    // Meal-time context
    private final Float totalCarbs;
    private final Integer currentGlucose;
    private final String activityLevel;
    
    // Temporary adjustment percentages
    private final int sickDayPercent;
    private final int stressPercent;
    private final int lightExercisePercent;
    private final int intenseExercisePercent;

    private CalculationParams(Builder builder) {
        this.insulinCarbRatio = parseRatio(builder.insulinCarbRatio);
        this.correctionFactor = builder.correctionFactor;
        this.targetGlucose = builder.targetGlucose;
        this.totalCarbs = builder.totalCarbs;
        this.currentGlucose = builder.currentGlucose;
        this.activityLevel = builder.activityLevel;
        this.sickDayPercent = builder.sickDayPercent != null ? builder.sickDayPercent : 0;
        this.stressPercent = builder.stressPercent != null ? builder.stressPercent : 0;
        this.lightExercisePercent = builder.lightExercisePercent != null ? builder.lightExercisePercent : 0;
        this.intenseExercisePercent = builder.intenseExercisePercent != null ? builder.intenseExercisePercent : 0;
    }

    // Handles both "1:10" string format and direct float value
    private Float parseRatio(String ratioStr) {
        if (ratioStr == null) return null;
        try {
            if (ratioStr.contains(":")) {
                return Float.parseFloat(ratioStr.split(":")[1]);
            }
            return Float.parseFloat(ratioStr);
        } catch (Exception e) {
            return null;
        }
    }

    // Getters
    public Float getInsulinCarbRatio() { return insulinCarbRatio; }
    public Float getCorrectionFactor() { return correctionFactor; }
    public Integer getTargetGlucose() { return targetGlucose; }
    public Float getTotalCarbs() { return totalCarbs; }
    public Integer getCurrentGlucose() { return currentGlucose; }
    public String getActivityLevel() { return activityLevel; }
    public int getSickDayPercent() { return sickDayPercent; }
    public int getStressPercent() { return stressPercent; }
    public int getLightExercisePercent() { return lightExercisePercent; }
    public int getIntenseExercisePercent() { return intenseExercisePercent; }
    
 // Profile completeness check
    public boolean isProfileComplete() {
        return insulinCarbRatio != null 
            && correctionFactor != null 
            && targetGlucose != null;
    }

    public List<String> getMissingFields() {
        List<String> missing = new java.util.ArrayList<>();
        if (insulinCarbRatio == null) missing.add("Insulin:Carb Ratio");
        if (correctionFactor == null) missing.add("Correction Factor");
        if (targetGlucose == null) missing.add("Target Glucose");
        return missing;
    }

    /**
     * Builder for constructing CalculationParams
     */
    public static class Builder {
        private String insulinCarbRatio;
        private Float correctionFactor;
        private Integer targetGlucose;
        private Float totalCarbs;
        private Integer currentGlucose;
        private String activityLevel;
        private Integer sickDayPercent;
        private Integer stressPercent;
        private Integer lightExercisePercent;
        private Integer intenseExercisePercent;

        public Builder fromUser(com.insuscan.data.UserEntity user) {
            if (user == null) return this;
            this.insulinCarbRatio = user.getInsulinCarbRatio() != null 
                ? user.getInsulinCarbRatio().toString() : null;
            this.correctionFactor = user.getCorrectionFactor();
            this.targetGlucose = user.getTargetGlucose();
            this.sickDayPercent = user.getSickDayAdjustment();
            this.stressPercent = user.getStressAdjustment();
            this.lightExercisePercent = user.getLightExerciseAdjustment();
            this.intenseExercisePercent = user.getIntenseExerciseAdjustment();
            return this;
        }
        
        // String format like "1:10"
        public Builder withInsulinCarbRatio(String val) { 
            this.insulinCarbRatio = val; 
            return this; 
        }
       

        // Float format from UserEntity
        public Builder withInsulinCarbRatio(Float val) { 
            this.insulinCarbRatio = val != null ? val.toString() : null; 
            return this; 
        }

        public Builder withCorrectionFactor(Float val) { 
            this.correctionFactor = val; 
            return this; 
        }

        public Builder withTargetGlucose(Integer val) { 
            this.targetGlucose = val; 
            return this; 
        }

        public Builder withTotalCarbs(Float val) { 
            this.totalCarbs = val; 
            return this; 
        }

        public Builder withCurrentGlucose(Integer val) { 
            this.currentGlucose = val; 
            return this; 
        }

        public Builder withActivityLevel(String val) { 
            this.activityLevel = val; 
            return this; 
        }

        public Builder withSickDayPercent(Integer val) { 
            this.sickDayPercent = val; 
            return this; 
        }

        public Builder withStressPercent(Integer val) { 
            this.stressPercent = val; 
            return this; 
        }

        public Builder withLightExercisePercent(Integer val) { 
            this.lightExercisePercent = val; 
            return this; 
        }

        public Builder withIntenseExercisePercent(Integer val) { 
            this.intenseExercisePercent = val; 
            return this; 
        }

        public CalculationParams build() {
            return new CalculationParams(this);
        }
    }
}
