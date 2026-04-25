package com.insuscan.pipeline.stage;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineWarning;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 10 — Medical-grade sanity checks.
 *
 * <p>Examines the final accumulated data to ensure it is physically possible
 * before presenting it to the user for dosing.</p>
 */
@Service
public class SanityCheckService {

    private static final Logger log = LoggerFactory.getLogger(SanityCheckService.class);
    private final PipelineWarningCollector warningCollector;

    public SanityCheckService(PipelineWarningCollector warningCollector) {
        this.warningCollector = warningCollector;
    }

    public void runSanityChecks(PipelineContext ctx) {
        PipelineContext.MealTotals totals = ctx.getMealTotals();
        if (totals == null) {
            log.warn("[SanityCheck] No meal totals found to check");
            return;
        }

        float totalWeight = totals.getTotalWeightG();

        // 1. Minimum reasonable weight
        if (totalWeight > 0 && totalWeight < 20f) {
            warningCollector.add(ctx, PipelineWarning.medium(
                PipelineWarningCollector.STAGE_SANITY_CHECK,
                "MEAL_TOO_LIGHT",
                String.format("Total meal weight is very light (%.1fg). Ensure reference object is placed correctly.", totalWeight)
            ));
        }

        // 2. Maximum reasonable weight (e.g., a standard meal rarely exceeds 1.5kg)
        if (totalWeight > 1500f) {
            warningCollector.add(ctx, PipelineWarning.high(
                PipelineWarningCollector.STAGE_SANITY_CHECK,
                "MEAL_TOO_HEAVY",
                String.format("Total meal weight is very heavy (%.1fg). Calibration or depth may be skewed.", totalWeight)
            ));
        }

        // 3. Physical impossibility check (Calories vs Weight)
        // Pure fat is 9 kcal/g. A meal cannot exceed 9 kcal/g.
        // Let's calculate total calories
        float totalCalories = (totals.getTotalCarbsG() * 4) + 
                              (totals.getTotalProteinG() * 4) + 
                              (totals.getTotalFatG() * 9);
        
        if (totalWeight > 0) {
            float energyDensity = totalCalories / totalWeight;
            if (energyDensity > 9.0f) {
                // This indicates a mathematical impossibility (e.g. USDA macros added up to more than 100g per 100g)
                warningCollector.add(ctx, PipelineWarning.critical(
                    PipelineWarningCollector.STAGE_SANITY_CHECK,
                    "IMPOSSIBLE_ENERGY_DENSITY",
                    String.format("Calculated energy density (%.1f kcal/g) exceeds physical limit of pure fat (9 kcal/g).", energyDensity)
                ));
            }
        }
        
        log.info("[SanityCheck] Completed. Total warnings: {}", ctx.getWarnings().size());
    }
}
