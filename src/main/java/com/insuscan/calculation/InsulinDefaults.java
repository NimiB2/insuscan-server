package com.insuscan.calculation;

public final class InsulinDefaults {
    
    private InsulinDefaults() {} // prevent instantiation
    
    // === Insulin Calculation Defaults ===
    public static final float INSULIN_CARB_RATIO = 10f;       // 1 unit per 10g carbs
    public static final float CORRECTION_FACTOR = 50f;         // 1 unit drops 50 mg/dL
    public static final int TARGET_GLUCOSE = 100;              // mg/dL
    
    // === Adjustment Percentages ===
    public static final int SICK_DAY_PERCENT = 15;             // +15%
    public static final int STRESS_PERCENT = 10;               // +10%
    public static final int LIGHT_EXERCISE_PERCENT = 15;       // -15%
    public static final int INTENSE_EXERCISE_PERCENT = 30;     // -30%
    
    // === Safety Thresholds ===
    public static final int LOW_GLUCOSE_THRESHOLD = 70;        // mg/dL
    public static final int HIGH_GLUCOSE_THRESHOLD = 250;      // mg/dL
    public static final float HIGH_DOSE_THRESHOLD = 15f;       // units
    public static final float MINIMUM_DOSE = 0.5f;             // units
}
