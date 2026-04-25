package com.insuscan.pipeline.stage;

import com.insuscan.boundary.NutritionInfo;
import com.insuscan.pipeline.calculation.DensityCalculator;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.service.NutritionDataService;
import com.insuscan.service.SemanticMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stage 7 — Resolves USDA nutrition data and calculates physical density.
 *
 * <p>
 * Replaces the old heuristic matching with strict USDA searches and
 * physics-based density calculation.
 * </p>
 */
@Service
public class NutritionAndDensityService {

    private static final Logger log = LoggerFactory.getLogger(NutritionAndDensityService.class);

    private final NutritionDataService nutritionDataService;
    private final DensityCalculator densityCalculator;
    private final PipelineWarningCollector warningCollector;
    private final SemanticMatchingService semanticMatchingService;

    public NutritionAndDensityService(NutritionDataService nutritionDataService,
            DensityCalculator densityCalculator,
            PipelineWarningCollector warningCollector,
            SemanticMatchingService semanticMatchingService) {
        this.nutritionDataService = nutritionDataService;
        this.densityCalculator = densityCalculator;
        this.warningCollector = warningCollector;
        this.semanticMatchingService = semanticMatchingService;
    }

    public void resolveNutritionAndDensity(PipelineContext ctx) {
        float totalConf = 0f;
        int count = 0;

        for (PipelineFoodItem item : ctx.getFoodItems()) {
            NutritionInfo bestMatch = findNutritionForFood(item, ctx);

            if (bestMatch != null && bestMatch.isFound()) {
                item.setFoundInUsda(true);
                item.setCarbsPer100g(bestMatch.getCarbsPer100g());
                item.setProteinPer100g(bestMatch.getProteinPer100g());
                item.setFatPer100g(bestMatch.getFatPer100g());
                item.setFiberPer100g(bestMatch.getFiberPer100g());
                item.setWaterPer100g(bestMatch.getWaterPer100g());

                densityCalculator.calculateAndSetDensity(item, bestMatch);

                totalConf += 1.0f; // High confidence for USDA match
                log.info("[NutritionDensity] {} -> {} (carbs: {}g/100g)",
                        item.getName(), bestMatch.getFoodName(), bestMatch.getCarbsPer100g());
            } else {
                item.setFoundInUsda(false);
                item.setDensitySource(PipelineFoodItem.DensitySource.UNKNOWN);
                warningCollector.foodNotFoundInUsda(ctx, item.getName());

                totalConf += 0.2f; // Low confidence, user input required
                log.warn("[NutritionDensity] {} -> Not found in USDA", item.getName());
            }
            count++;
        }

        float confidence = count > 0 ? (totalConf / count) : 1.0f;
        ctx.recordConfidence("NUTRITION", confidence);
    }

    private NutritionInfo findNutritionForFood(PipelineFoodItem item, PipelineContext ctx) {
        List<NutritionInfo> candidates = new java.util.ArrayList<>();

        // Try base ingredient first (most reliable for USDA Foundation foods)
        if (item.getBaseIngredient() != null && !item.getBaseIngredient().isBlank()) {
            candidates = nutritionDataService.searchCandidates(item.getBaseIngredient());
        }

        // Fallback to exact name
        if (candidates.isEmpty()) {
            candidates = nutritionDataService.searchCandidates(item.getName());
        }

        // Try specific search terms
        if (candidates.isEmpty() && item.getSearchTerms() != null) {
            for (String term : item.getSearchTerms()) {
                candidates = nutritionDataService.searchCandidates(term);
                if (!candidates.isEmpty())
                    break;
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Use AI Judge to find the best match semantically and visually
        String bestFdcId = semanticMatchingService.findBestMatch(item, candidates, ctx.getImageTopBase64());

        if (bestFdcId != null) {
            return nutritionDataService.getNutritionInfoById(bestFdcId);
        }

        // Fallback: choose the simplest (shortest) name instead of random ID sorting
        return candidates.stream()
                .min(java.util.Comparator.comparingInt(c -> c.getFoodName() != null ? c.getFoodName().length() : 999))
                .orElse(candidates.get(0));
    }
}
