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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 7 — Resolves USDA nutrition data and calculates physical density.
 *
 * <p>
 * Flow: 1. optimizeQuery() → AI returns preparationKeywords (e.g. ["cooked"],
 * ["raw"], ["fried"]) and searchVariations where the FIRST variation already
 * includes the prep keyword (e.g. "rice white cooked" instead of just "white
 * rice") 2. buildTermList() uses the prep keyword to build a precise primary
 * USDA search term. Broader fallback terms are tried only if the precise term
 * returns no results. 3. All candidates from tried terms are pooled and
 * pickBestCandidate() selects the winner, preferring candidates whose USDA name
 * matches the expected preparation style.
 * </p>
 */
@Service
public class NutritionAndDensityService {

	private static final Logger log = LoggerFactory.getLogger(NutritionAndDensityService.class);

	/**
	 * USDA keywords that are opposite to each other. Used to penalize candidates
	 * that contradict the expected preparation style.
	 */
	private static final Map<String, List<String>> OPPOSITE_PREPS = Map.of("raw",
			List.of("cooked", "boiled", "roasted", "fried", "baked", "broiled"), "cooked", List.of("raw"), "fried",
			List.of("raw", "baked", "boiled"), "roasted", List.of("raw", "boiled"), "broiled", List.of("raw", "boiled"),
			"canned", List.of("raw", "fresh"), "dried", List.of("raw", "fresh", "cooked"), "baked",
			List.of("raw", "fried"));

	/**
	 * Maximum number of USDA search terms tried per food item, to bound API call
	 * volume.
	 */
	private static final int MAX_TERMS = 4;

	/** Confidence recorded when a food item is matched in USDA. */
	private static final float FOUND_CONFIDENCE = 1.0f;

	/** Confidence recorded when a food item could not be matched in USDA. */
	private static final float NOT_FOUND_CONFIDENCE = 0.2f;

	private final NutritionDataService nutritionDataService;
	private final DensityCalculator densityCalculator;
	private final PipelineWarningCollector warningCollector;
	private final FoodSearchAiService foodSearchAiService;

	public NutritionAndDensityService(NutritionDataService nutritionDataService, DensityCalculator densityCalculator,
			PipelineWarningCollector warningCollector, FoodSearchAiService foodSearchAiService) {
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

				totalConf += FOUND_CONFIDENCE;
				log.info("[NutritionDensity] {} -> {} (carbs: {}g/100g)", item.getName(), bestMatch.getFoodName(),
						bestMatch.getCarbsPer100g());
			} else {
				item.setFoundInUsda(false);
				item.setDensitySource(PipelineFoodItem.DensitySource.UNKNOWN);
				warningCollector.foodNotFoundInUsda(ctx, item.getName());

				totalConf += NOT_FOUND_CONFIDENCE;
				log.warn("[NutritionDensity] {} -> Not found in USDA", item.getName());
			}
			count++;
		}

		float confidence = count > 0 ? (totalConf / count) : 1.0f;
		ctx.recordConfidence("NUTRITION", confidence);
	}

	/**
	 * Main resolution flow: 1. AI gives us precise, prep-aware search terms +
	 * preparationKeywords 2. We try each term and collect all candidates into a
	 * pool 3. pickBestCandidate() selects the winner using prep-aware matching
	 */
	private NutritionInfo findNutritionForFood(PipelineFoodItem item) {
		SearchOptimization opt = fetchOptimization(item);
		List<String> termsToTry = buildTermList(item, opt);
		List<String> prepKeywords = opt != null && opt.getPreparationKeywords() != null
				? Arrays.asList(opt.getPreparationKeywords())
				: List.of();

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

		NutritionInfo best = pickBestCandidate(item.getName(), new ArrayList<>(candidateMap.values()), prepKeywords);
		log.info("[NutritionDensity] Matched '{}' for '{}' (prep: {})", best.getFoodName(), item.getName(),
				prepKeywords);
		return best;
	}

	private SearchOptimization fetchOptimization(PipelineFoodItem item) {
		try {
			return foodSearchAiService.optimizeQuery(item.getName(), "en");
		} catch (Exception e) {
			log.warn("[NutritionDensity] optimizeQuery failed for '{}': {}", item.getName(), e.getMessage());
			return null;
		}
	}

	/**
	 * Builds an ordered list of USDA search terms.
	 *
	 * Priority: 1. AI-generated searchVariations — the FIRST one already includes
	 * the prep keyword (e.g. "rice white cooked") thanks to the updated prompt. 2.
	 * Gemini Vision searchTerms from FoodDetectionService (item.getSearchTerms())
	 * 3. Base ingredient fallback (item.getBaseIngredient()) 4. Display name last
	 * resort (item.getName())
	 *
	 * Capped at MAX_TERMS to avoid excessive USDA API calls.
	 */

	private List<String> buildTermList(PipelineFoodItem item, SearchOptimization opt) {
		List<String> terms = new ArrayList<>();

		if (opt != null && opt.getSearchVariations() != null) {
			for (String v : opt.getSearchVariations()) {
				addIfNew(terms, v);
				if (terms.size() >= MAX_TERMS)
					break;
			}
		}

		if (terms.size() < MAX_TERMS && item.getSearchTerms() != null) {
			for (String t : item.getSearchTerms()) {
				addIfNew(terms, t);
				if (terms.size() >= MAX_TERMS)
					break;
			}
		}

		if (terms.size() < MAX_TERMS)
			addIfNew(terms, item.getBaseIngredient());
		if (terms.size() < MAX_TERMS)
			addIfNew(terms, item.getName());

		return terms;
	}

	private void addIfNew(List<String> list, String term) {
		if (term != null && !term.isBlank() && !list.contains(term)) {
			list.add(term);
		}
	}

	/**
	 * Picks the best USDA candidate using preparation-aware string matching:
	 *
	 * 1. Exact match (case-insensitive) 2. Prep-style match: candidate contains a
	 * prep keyword AND matches food name, AND does NOT contain an opposite prep
	 * keyword (e.g. for prep=["cooked"]: prefers "rice, white, cooked"; penalizes
	 * "rice, white, raw") 3. Contains match (non-conflicting prep first, then all)
	 * 4. Best word overlap (prep keyword gives +1 bonus point) 5. First result
	 * (USDA relevance order)
	 */
	private NutritionInfo pickBestCandidate(String targetName, List<NutritionInfo> candidates,
			List<String> prepKeywords) {
		String target = targetName.toLowerCase().trim();

		for (NutritionInfo c : candidates) {
			if (c.getFoodName() != null && c.getFoodName().toLowerCase().trim().equals(target)) {
				return c;
			}
		}

		for (String prep : prepKeywords) {
			List<String> opposites = OPPOSITE_PREPS.getOrDefault(prep, List.of());
			for (NutritionInfo c : candidates) {
				if (c.getFoodName() == null)
					continue;
				String name = c.getFoodName().toLowerCase();
				boolean hasPrep = name.contains(prep);
				boolean hasConflict = opposites.stream().anyMatch(name::contains);
				boolean nameMatches = name.contains(target) || target.contains(name) || hasWordOverlap(target, name);
				if (hasPrep && !hasConflict && nameMatches) {
					return c;
				}
			}
		}

// First pass excludes candidates with a conflicting prep style; second pass accepts any.
		for (NutritionInfo c : candidates) {
			if (c.getFoodName() == null)
				continue;
			String name = c.getFoodName().toLowerCase();
			boolean hasConflict = prepKeywords.stream().flatMap(p -> OPPOSITE_PREPS.getOrDefault(p, List.of()).stream())
					.anyMatch(name::contains);
			if (!hasConflict && (name.contains(target) || target.contains(name))) {
				return c;
			}
		}

		for (NutritionInfo c : candidates) {
			if (c.getFoodName() == null)
				continue;
			String name = c.getFoodName().toLowerCase();
			if (name.contains(target) || target.contains(name)) {
				return c;
			}
		}

		String[] targetWords = target.split("\\s+");
		NutritionInfo bestWordMatch = null;
		int bestWordCount = 0;
		for (NutritionInfo c : candidates) {
			if (c.getFoodName() == null)
				continue;
			String name = c.getFoodName().toLowerCase();
			int matchCount = 0;
			for (String word : targetWords) {
				if (word.length() > 2 && name.contains(word))
					matchCount++;
			}
			if (prepKeywords.stream().anyMatch(name::contains))
				matchCount++;
			if (matchCount > bestWordCount) {
				bestWordCount = matchCount;
				bestWordMatch = c;
			}
		}
		if (bestWordMatch != null && bestWordCount > 0) {
			return bestWordMatch;
		}

		return candidates.get(0);
	}

	private boolean hasWordOverlap(String a, String b) {
		for (String word : a.split("\\s+")) {
			if (word.length() > 2 && b.contains(word))
				return true;
		}
		return false;
	}
}
