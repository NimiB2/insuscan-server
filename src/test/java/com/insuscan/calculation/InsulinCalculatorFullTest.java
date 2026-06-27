package com.insuscan.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsulinCalculatorFullTest {

    private static final float DELTA = 0.001f;
    private final InsulinCalculator calculator = new InsulinCalculator();

    private CalculationResult calc(CalculationParams.Builder builder) {
        return calculator.calculate(builder.build());
    }

    private CalculationParams.Builder base() {
        return new CalculationParams.Builder()
                .withInsulinCarbRatio("1:10")
                .withCorrectionFactor(50f)
                .withTargetGlucose(100);
    }

    @Test
    @DisplayName("Standard meal: carb dose only")
    void standardMeal() {
        CalculationResult r = calc(base().withTotalCarbs(60f).withCurrentGlucose(100));
        assertEquals(6.0f, r.getCarbDose(), DELTA);
        assertEquals(0.0f, r.getCorrectionDose(), DELTA);
        assertEquals(6.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("High glucose adds positive correction")
    void highGlucoseCorrection() {
        CalculationResult r = calc(base().withTotalCarbs(0f).withCurrentGlucose(200));
        assertEquals(2.0f, r.getCorrectionDose(), DELTA);
        assertEquals(2.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Low glucose reduces dose via negative correction")
    void lowGlucoseReducesDose() {
        CalculationResult r = calc(base().withTotalCarbs(60f).withCurrentGlucose(70));
        assertEquals(-0.6f, r.getCorrectionDose(), DELTA);
        assertEquals(5.4f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Very low glucose floors the dose at zero")
    void doseFloorsAtZero() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(10f).withInsulinCarbRatio("1:10")
                .withCorrectionFactor(10f).withTargetGlucose(150).withCurrentGlucose(50));
        assertEquals(0.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Glucose at target gives zero correction")
    void glucoseAtTarget() {
        CalculationResult r = calc(base().withTotalCarbs(50f).withCurrentGlucose(100));
        assertEquals(0.0f, r.getCorrectionDose(), DELTA);
        assertEquals(5.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Very high glucose gives large correction")
    void veryHighGlucose() {
        CalculationResult r = calc(base().withTotalCarbs(0f).withCurrentGlucose(400));
        assertEquals(6.0f, r.getCorrectionDose(), DELTA);
    }

    @Test
    @DisplayName("Borderline strong ratio 1:5")
    void ratioOneToFive() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(30f).withInsulinCarbRatio("1:5")
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertEquals(6.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Borderline weak ratio 1:30")
    void ratioOneToThirty() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(60f).withInsulinCarbRatio("1:30")
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertEquals(2.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Very tight ratio 1:3")
    void ratioOneToThree() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(30f).withInsulinCarbRatio("1:3")
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertEquals(10.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Very loose ratio 1:50")
    void ratioOneToFifty() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:50")
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertEquals(1.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Decimal ratio 1:7.5 parses correctly")
    void decimalRatio() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(45f).withInsulinCarbRatio("1:7.5")
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertEquals(6.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Inverted ratio value is auto-normalized")
    void invertedRatio() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio(0.1f)
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertEquals(5.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Plan ratio overrides base")
    void planOverride() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio(5f)
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertEquals(10.0f, r.getCarbDose(), DELTA);
    }

    @Test
    @DisplayName("Fractional carbs produce fractional dose")
    void fractionalCarbs() {
        CalculationResult r = calc(base().withTotalCarbs(37.5f));
        assertEquals(3.75f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Result is rounded to two decimals")
    void roundingToTwoDecimals() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(10f).withInsulinCarbRatio("1:3")
                .withCorrectionFactor(50f).withTargetGlucose(100));
        assertEquals(3.33f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Zero carbs and no glucose gives zero dose")
    void zeroCarbs() {
        CalculationResult r = calc(base().withTotalCarbs(0f));
        assertEquals(0.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("High carbs exceed warning threshold")
    void highCarbs() {
        CalculationResult r = calc(base().withTotalCarbs(200f));
        assertEquals(20.0f, r.getRoundedDose(), DELTA);
        assertTrue(r.getRoundedDose() > InsulinDefaults.HIGH_DOSE_THRESHOLD);
    }

    @Test
    @DisplayName("Empty profile falls back to defaults and flags incomplete")
    void emptyProfile() {
        CalculationResult r = calc(new CalculationParams.Builder().withTotalCarbs(50f));
        assertFalse(r.isProfileComplete());
        assertEquals(3, r.getMissingFields().size());
        assertEquals(5.0f, r.getRoundedDose(), DELTA);
    }

    @Test
    @DisplayName("Missing correction factor is reported")
    void missingCorrectionFactor() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:10").withTargetGlucose(100));
        assertFalse(r.isProfileComplete());
        assertEquals(1, r.getMissingFields().size());
        assertTrue(r.getMissingFields().contains("Correction Factor"));
    }

    @Test
    @DisplayName("Missing ratio is reported")
    void missingRatio() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withCorrectionFactor(50f).withTargetGlucose(100));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Insulin:Carb Ratio"));
    }

    @Test
    @DisplayName("Missing target glucose is reported")
    void missingTarget() {
        CalculationResult r = calc(new CalculationParams.Builder()
                .withTotalCarbs(50f).withInsulinCarbRatio("1:10").withCorrectionFactor(50f));
        assertFalse(r.isProfileComplete());
        assertTrue(r.getMissingFields().contains("Target Glucose"));
    }

    @Test
    @DisplayName("Complete profile reports no missing fields")
    void completeProfile() {
        CalculationResult r = calc(base().withTotalCarbs(50f));
        assertTrue(r.isProfileComplete());
        assertTrue(r.getMissingFields().isEmpty());
    }
}