package com.insuscan.calculation;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Input parameters for insulin dose calculation.
 * Uses Builder pattern for clean construction.
 */
public class CalculationParams {

    private static final Logger log = LoggerFactory.getLogger(CalculationParams.class);

    /**
     * Insulin-to-Carb Ratio in Grams per Unit (e.g. 10 means 1 unit covers 10g carbs).
     * DO NOT pass the inverted ratio (0.1).
     */
    private final Float insulinCarbRatio;
    private final Float correctionFactor;
    private final Integer targetGlucose;

    private final Float totalCarbs;
    private final Integer currentGlucose;

    private CalculationParams(Builder builder) {
        this.insulinCarbRatio = parseRatio(builder.insulinCarbRatio);
        this.correctionFactor = builder.correctionFactor;
        this.targetGlucose = builder.targetGlucose;
        this.totalCarbs = builder.totalCarbs;
        this.currentGlucose = builder.currentGlucose;
    }

    // Handles both "1:10" string format and direct float value
    // Also normalizes "Units per Gram" (e.g. 0.1) to "Grams per Unit" (e.g. 10)
    private Float parseRatio(String ratioStr) {
        if (ratioStr == null) return null;
        try {
            float val = ratioStr.contains(":")
                ? Float.parseFloat(ratioStr.split(":")[1])
                : Float.parseFloat(ratioStr);
            return normalizeRatio(val);
        } catch (Exception e) {
            return null;
        }
    }

    private Float normalizeRatio(float val) {
        if (val <= 0) return val;
        if (val < 1.0f) {
            float corrected = 1.0f / val;
            log.warn("[ICR] Inverted ratio detected ({} units/g). Auto-correcting to {} g/unit.", val, corrected);
            return corrected;
        }
        return val;
    }

    public Float getInsulinCarbRatio() { return insulinCarbRatio; }
    public Float getCorrectionFactor() { return correctionFactor; }
    public Integer getTargetGlucose() { return targetGlucose; }
    public Float getTotalCarbs() { return totalCarbs; }
    public Integer getCurrentGlucose() { return currentGlucose; }

    public boolean isProfileComplete() {
        return isValid(insulinCarbRatio)
            && isValid(correctionFactor)
            && isValid(targetGlucose);
    }

    private boolean isValid(Float value) {
        return value != null && value > 0;
    }

    private boolean isValid(Integer value) {
        return value != null && value > 0;
    }

    public List<String> getMissingFields() {
        List<String> missing = new ArrayList<>();
        if (!isValid(insulinCarbRatio)) missing.add("Insulin:Carb Ratio");
        if (!isValid(correctionFactor)) missing.add("Correction Factor");
        if (!isValid(targetGlucose)) missing.add("Target Glucose");
        return missing;
    }

    public static class Builder {
        private String insulinCarbRatio;
        private Float correctionFactor;
        private Integer targetGlucose;
        private Float totalCarbs;
        private Integer currentGlucose;

        public Builder fromUser(com.insuscan.data.UserEntity user) {
            if (user == null) return this;
            this.insulinCarbRatio = user.getInsulinCarbRatio() != null
                ? user.getInsulinCarbRatio().toString() : null;
            this.correctionFactor = user.getCorrectionFactor();
            this.targetGlucose = user.getTargetGlucose();
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

        public CalculationParams build() {
            return new CalculationParams(this);
        }
    }
}