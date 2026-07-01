package com.insuscan.calculation;

public final class InsulinDefaults {
    
    private InsulinDefaults() {}
    
    // === Insulin Calculation Defaults ===
    public static final float INSULIN_CARB_RATIO = 10f;
    public static final float CORRECTION_FACTOR = 50f;
    public static final int TARGET_GLUCOSE = 100;
    
    // Threshold above which a calculated dose is considered high (used by tests and future warning logic)
    public static final float HIGH_DOSE_THRESHOLD = 15f;
}