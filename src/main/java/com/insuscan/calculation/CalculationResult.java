package com.insuscan.calculation;

import java.util.List;

/**
 * Result of insulin dose calculation.
 * Contains full breakdown for history display.
 */
public class CalculationResult {
    
    // Dose components
    private final float carbDose;
    private final float correctionDose;
    
    // Adjustments applied
    private final float sickAdjustment;
    private final float stressAdjustment;
    private final float exerciseAdjustment;
    
    // Final values
    private final float totalDose;
    private final float roundedDose;
    
    // Profile status
    private final boolean profileComplete;
    private final List<String> missingFields;
    private final String warning;

    public CalculationResult(float carbDose, float correctionDose,
                             float sickAdjustment, float stressAdjustment, float exerciseAdjustment,
                             float totalDose, float roundedDose,
                             String warning, boolean profileComplete, List<String> missingFields) {
        this.carbDose = carbDose;
        this.correctionDose = correctionDose;
        this.sickAdjustment = sickAdjustment;
        this.stressAdjustment = stressAdjustment;
        this.exerciseAdjustment = exerciseAdjustment;
        this.totalDose = totalDose;
        this.roundedDose = roundedDose;
        this.warning = warning;
        this.profileComplete = profileComplete;
        this.missingFields = missingFields;
    }

    // Getters
    public float getCarbDose() { return carbDose; }
    public float getCorrectionDose() { return correctionDose; }
    public float getSickAdjustment() { return sickAdjustment; }
    public float getStressAdjustment() { return stressAdjustment; }
    public float getExerciseAdjustment() { return exerciseAdjustment; }
    public float getTotalDose() { return totalDose; }
    public float getRoundedDose() { return roundedDose; }
    public String getWarning() { return warning; }
    public boolean isProfileComplete() { return profileComplete; }
    public List<String> getMissingFields() { return missingFields; }

    @Override
    public String toString() {
        return "CalculationResult{" +
                "carbDose=" + carbDose +
                ", correctionDose=" + correctionDose +
                ", roundedDose=" + roundedDose +
                ", profileComplete=" + profileComplete +
                '}';
    }
}