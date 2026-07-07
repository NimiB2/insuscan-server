package com.insuscan.pipeline.calculation;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 9 — Aggregates per-item nutrition into meal totals.
 *
 * <p>Calculates absolute macronutrient values based on the physical weight,
 * then sums everything into PipelineContext.MealTotals.</p>
 */
@Service
public class NutritionAggregator {

    private static final Logger log = LoggerFactory.getLogger(NutritionAggregator.class);

    public void aggregate(PipelineContext ctx) {
        PipelineContext.MealTotals totals = new PipelineContext.MealTotals();
        
        float totalWeight = 0f;
        float totalCarbs = 0f;
        float totalNetCarbs = 0f;
        float totalProtein = 0f;
        float totalFat = 0f;
        float totalFiber = 0f;
        
        int itemsFound = 0;
        int itemsNotInUsda = 0;
        int itemsRequiringValidation = 0;

        for (PipelineFoodItem item : ctx.getFoodItems()) {
            itemsFound++;
            if (!item.isFoundInUsda()) itemsNotInUsda++;
            if (item.isRequiresUserValidation()) itemsRequiringValidation++;

            Float weight = item.getWeightG();
            if (weight != null && weight > 0) {
                totalWeight += weight;

                if (item.getCarbsPer100g() != null) {
                    float carbs = (item.getCarbsPer100g() * weight) / 100f;
                    item.setCarbsG(carbs);
                    totalCarbs += carbs;

                    float fiber = 0f;
                    if (item.getFiberPer100g() != null) {
                        fiber = (item.getFiberPer100g() * weight) / 100f;
                        item.setFiberG(fiber);
                        totalFiber += fiber;
                    }
                    
                    float netCarbs = Math.max(0, carbs - fiber);
                    item.setNetCarbsG(netCarbs);
                    totalNetCarbs += netCarbs;
                }

                if (item.getProteinPer100g() != null) {
                    float protein = (item.getProteinPer100g() * weight) / 100f;
                    item.setProteinG(protein);
                    totalProtein += protein;
                }

                if (item.getFatPer100g() != null) {
                    float fat = (item.getFatPer100g() * weight) / 100f;
                    item.setFatG(fat);
                    totalFat += fat;
                }
                
                log.info("[Aggregator] {} -> {}g net carbs (weight: {}g)",
                        item.getName(), String.format("%.1f", item.getNetCarbsG()), String.format("%.1f", weight));
            }
        }

        totals.setTotalWeightG(totalWeight);
        totals.setTotalCarbsG(totalCarbs);
        totals.setTotalNetCarbsG(totalNetCarbs);
        totals.setTotalProteinG(totalProtein);
        totals.setTotalFatG(totalFat);
        totals.setTotalFiberG(totalFiber);
        totals.setItemsFound(itemsFound);
        totals.setItemsNotInUsda(itemsNotInUsda);
        totals.setItemsRequiringValidation(itemsRequiringValidation);

        ctx.setMealTotals(totals);
        log.info("[Aggregator] Meal Totals: {}g Weight, {}g Net Carbs", String.format("%.1f", totalWeight), String.format("%.1f", totalNetCarbs));
    }
}
