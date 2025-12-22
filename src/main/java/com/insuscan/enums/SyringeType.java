package com.insuscan.enums;

// Different insulin syringe sizes used as reference objects
public enum SyringeType {
    SYRINGE_30_UNIT(7.0f),     // 30 unit syringe, ~7cm length
    SYRINGE_50_UNIT(9.0f),     // 50 unit syringe, ~9cm length  
    SYRINGE_100_UNIT(12.0f),   // 100 unit syringe, ~12cm length
    INSULIN_PEN(16.5f);        // Standard insulin pen, ~16.5cm

    private final float lengthCm;

    SyringeType(float lengthCm) {
        this.lengthCm = lengthCm;
    }

    public float getLengthCm() {
        return lengthCm;
    }
}
