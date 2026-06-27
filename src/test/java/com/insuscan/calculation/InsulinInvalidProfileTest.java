package com.insuscan.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsulinInvalidProfileTest {

    private final InsulinCalculator calculator = new InsulinCalculator();

    private CalculationResult calc(CalculationParams.Builder builder) {
        return calculator.calculate(builder.build());
    }

    @Test
    @DisplayName("Zero ratio is invalid")
    void zeroRatioIsInvalid() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio(0f)
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Insulin:Carb Ratio"));
    }

    @Test
    @DisplayName("Negative ratio is invalid")
    void negativeRatioIsInvalid() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio(-5f)
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Insulin:Carb Ratio"));
    }

    @Test
    @DisplayName("Ratio 1:0 is invalid")
    void zeroStringRatioIsInvalid() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:0")
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Insulin:Carb Ratio"));
    }

    @Test
    @DisplayName("Zero correction factor is invalid")
    void zeroCorrectionFactorIsInvalid() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:10")
                .withCorrectionFactor(0f).withTargetGlucose(100));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Correction Factor"));
    }

    @Test
    @DisplayName("Negative correction factor is invalid")
    void negativeCorrectionFactorIsInvalid() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:10")
                .withCorrectionFactor(-10f).withTargetGlucose(100));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Correction Factor"));
    }

    @Test
    @DisplayName("Zero target glucose is invalid")
    void zeroTargetIsInvalid() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:10")
                .withCorrectionFactor(50f).withTargetGlucose(0));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Target Glucose"));
    }

    @Test
    @DisplayName("Negative target glucose is invalid")
    void negativeTargetIsInvalid() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:10")
                .withCorrectionFactor(50f).withTargetGlucose(-20));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Target Glucose"));
    }

    @Test
    @DisplayName("All valid values keep the profile complete")
    void allValidIsComplete() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:10")
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertTrue(r.isProfileComplete());
        assertTrue(r.getMissingFields().isEmpty());
    }
}