package com.insuscan.pipeline.stage;

import com.insuscan.boundary.NutritionInfo;
import com.insuscan.boundary.SearchOptimization;
import com.insuscan.pipeline.calculation.DensityCalculator;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.service.FoodSearchAiService;
import com.insuscan.service.NutritionDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 7 — Resolves USDA nutrition data and calculates physical density.
 *
 * <p>
 * Two-step approach (proven from old system):
 * 1. optimizeQuery() — AI generates USDA-formatted search terms like "rice, white, cooked"
 *    (this is fast, cheap, and cached; it also handles Hebrew→English translation)
 * 2. searchCandidates(term) → string matching — no AI ranking, just reliable string matching
 * </p>
 */
@Service
public class NutritionAndDensityService {

    private static final Logger log = LoggerFactory.getLogger(NutritionAndDensityService.class);

    private final NutritionDataService nutritionDataService;
    private final DensityCalculator densityCalculator;
    private final PipelineWarningCollector warningCollector;
    private final FoodSearchAiService foodSearchAiService;

    public NutritionAndDensityService(NutritionDataService nutritionDataService,
            DensityCalculator densityCalculator,
            PipelineWarningCollector warningCollector,
            FoodSearchAiService foodSearchAiService) {
        this.nutritionDataService = nutritionDataService;
        this.densityCalculator = densityCalculator;
        this.warningCollector = warningCollector;
        this.foodSearchAiService = foodSearchAiService;
    }

    public void resolveNutritionAndDensity(PipelineContext ctx) {
        float totalConf = 0f;
        int count = 0;

        for (PipelineFoodItem item : ctx.getFoodItems()) {
            NutritionInfo bestMatch = findNutritionForFood(item);

            if (bestMatch != null && bestMatch.isFound()) {
                item.setFoundInUsda(true);
                item.setCarbsPer100g(bestMatch.getCarbsPer100g());
                item.setProteinPer100g(bestMatch.getProteinPer100g());
                item.setFatPer100g(bestMatch.getFatPer100g());
                item.setFiberPer100g(bestMatch.getFiberPer100g());
                item.setWaterPer100g(bestMatch.getWaterPer100g());

                densityCalculator.calculateAndSetDensity(item, bestMatch);

                totalConf += 1.0f;
                log.info("[NutritionDensity] {} -> {} (carbs: {}g/100g)",
                        item.getName(), bestMatch.getFoodName(), bestMatch.getCarbsPer100g());
            } else {
                item.setFoundInUsda(false);
                item.setDensitySource(PipelineFoodItem.DensitySource.UNKNOWN);
                warningCollector.foodNotFoundInUsda(ctx, item.getName());

                totalConf += 0.2f;
                log.warn("[NutritionDensity] {} -> Not found in USDA", item.getName());
            }
            count++;
        }

        float confidence = count > 0 ? (totalConf / count) : 1.0f;
        ctx.recordConfidence("NUTRITION", confidence);
    }

    /**
     * Search strategy:
     * 1. Run ALL search terms and aggregate candidates (dedup by fdcId)
     *    — this ensures "rice, white, cooked" candidates enter the pool
     *      even if "white rice" already returned 20 results.
     * 2. Apply smart string matching with cooked-over-raw preference.
     */
    private NutritionInfo findNutritionForFood(PipelineFoodItem item) {
        List<String> termsToTry = buildTermList(item);

        // Collect candidates from ALL terms, dedup by fdcId
        Map<String, NutritionInfo> candidateMap = new LinkedHashMap<>();
        for (String term : termsToTry) {
            List<NutritionInfo> results = nutritionDataService.searchCandidates(term);
            for (NutritionInfo r : results) {
                candidateMap.putIfAbsent(r.getFdcId(), r);
            }
        }

        if (candidateMap.isEmpty()) {
            return null;
        }

        NutritionInfo best = pickBestCandidate(item.getName(), new ArrayList<>(candidateMap.values()));
        log.info("[NutritionDensity] Matched '{}' for '{}'", best.getFoodName(), item.getName());
        return best;
    }

    /**
     * Builds the ordered list of unique search terms to try.
     * AI-optimized terms come first because they are formatted for USDA
     * (e.g. "rice, white, cooked" instead of "White Rice").
     */
    private List<String> buildTermList(PipelineFoodItem item) {
        List<String> terms = new ArrayList<>();

        // 1. AI-optimized terms (cached after first call — fast on repeat)
        try {
            SearchOptimization opt = foodSearchAiService.optimizeQuery(item.getName(), "en");
            if (opt.getSearchVariations() != null) {
                for (String v : opt.getSearchVariations()) {
                    addIfNew(terms, v);
                }
            }
            // Also add the translated/normalized query itself
            addIfNew(terms, opt.getTranslatedQuery());
        } catch (Exception e) {
            log.warn("[NutritionDensity] optimizeQuery failed for '{}', falling back: {}",
                    item.getName(), e.getMessage());
        }

        // 2. Gemini Vision search terms (from FoodDetectionService)
        if (item.getSearchTerms() != null) {
            for (String t : item.getSearchTerms()) {
                addIfNew(terms, t);
            }
        }

        // 3. Base ingredient
        addIfNew(terms, item.getBaseIngredient());

        // 4. Display name (last resort)
        addIfNew(terms, item.getName());

        return terms;
    }

    private void addIfNew(List<String> list, String term) {
        if (term != null && !term.isBlank() && !list.contains(term)) {
            list.add(term);
        }
    }

    /**
     * Picks the best candidate using smart string matching:
     *   1. Exact match (case-insensitive)
     *   2. Cooked-over-raw: if target doesn't imply raw, prefer "cooked" candidates
     *   3. Contains match (either direction) — non-raw first
     *   4. Best word overlap
     *   5. First result (USDA relevance order)
     */
    private NutritionInfo pickBestCandidate(String targetName, List<NutritionInfo> candidates) {
        String target = targetName.toLowerCase().trim();
        boolean targetImpliesRaw = target.contains("raw") || target.contains("fresh")
                || target.contains("uncooked");

        // 1. Exact match
        for (NutritionInfo c : candidates) {
            if (c.getFoodName() != null && c.getFoodName().toLowerCase().trim().equals(target)) {
                return c;
            }
        }

        // 2. Cooked-over-raw preference (skip raw candidates when target is a cooked dish)
        if (!targetImpliesRaw) {
            for (NutritionInfo c : candidates) {
                if (c.getFoodName() == null) continue;
                String name = c.getFoodName().toLowerCase();
                if (name.contains("cooked") && (name.contains(target) || target.contains(name))) {
                    return c;
                }
            }
        }

        // 3. Contains match (non-raw first, then all)
        for (NutritionInfo c : candidates) {
            if (c.getFoodName() == null) continue;
            String name = c.getFoodName().toLowerCase();
            boolean isRaw = name.contains(", raw") || name.endsWith(" raw");
            if (!isRaw && (name.contains(target) || target.contains(name))) {
                return c;
            }
        }
        for (NutritionInfo c : candidates) {
            if (c.getFoodName() == null) continue;
            String name = c.getFoodName().toLowerCase();
            if (name.contains(target) || target.contains(name)) {
                return c;
            }
        }

        // 4. Best word overlap (cooked preferred, ignores short stop-words)
        String[] targetWords = target.split("\\s+");
        NutritionInfo bestWordMatch = null;
        int bestWordCount = 0;
        for (NutritionInfo c : candidates) {
            if (c.getFoodName() == null) continue;
            String name = c.getFoodName().toLowerCase();
            int matchCount = 0;
            for (String word : targetWords) {
                if (word.length() > 2 && name.contains(word)) matchCount++;
            }
            // Give cooked a bonus point
            if (!targetImpliesRaw && name.contains("cooked")) matchCount++;
            if (matchCount > bestWordCount) {
                bestWordCount = matchCount;
                bestWordMatch = c;
            }
        }
        if (bestWordMatch != null && bestWordCount > 0) {
            return bestWordMatch;
        }

        // 5. USDA relevance order — first result
        return candidates.get(0);
    }
}
