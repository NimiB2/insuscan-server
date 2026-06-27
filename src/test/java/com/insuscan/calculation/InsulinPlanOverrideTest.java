package com.insuscan.calculation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InsulinPlanOverrideTest {

    private final InsulinCalculator calculator = new InsulinCalculator();

    @Test
    public void planRatioOverridesBase() {
        CalculationParams params = new CalculationParams.Builder()
                .withTotalCarbs(50f)
                .withInsulinCarbRatio(5f)
                .withCorrectionFactor(50f)
                .withTargetGlucose(100)
                .build();

        CalculationResult result = calculator.calculate(params);

        assertEquals(10.0f, result.getCarbDose(), 0.01f);
    }
}