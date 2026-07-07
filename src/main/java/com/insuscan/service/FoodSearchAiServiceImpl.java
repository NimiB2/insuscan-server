package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.NutritionInfo;
import com.insuscan.boundary.ScoredFoodResult;
import com.insuscan.boundary.SearchOptimization;
import com.insuscan.util.ApiLogger;
import com.insuscan.util.OpenAiJsonParser;
import com.insuscan.util.GeminiApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI-backed query optimizer and result ranker for USDA food searches, with time-limited in-memory caching of query optimizations.
 */
@Service
public class FoodSearchAiServiceImpl implements FoodSearchAiService {

	private static final Logger log = LoggerFactory.getLogger(FoodSearchAiServiceImpl.class);

	private final GeminiApiClient geminiClient;
	private final ObjectMapper objectMapper;
	private final ApiLogger apiLogger;
	private final OpenAiJsonParser jsonParser;

	private final Map<String, CachedOptimization> optimizationCache = new ConcurrentHashMap<>();
	private static final long CACHE_TTL_MS = 60 * 60 * 1000;

	public FoodSearchAiServiceImpl(GeminiApiClient geminiClient, ObjectMapper objectMapper, ApiLogger apiLogger,
			OpenAiJsonParser jsonParser) {
		this.geminiClient = geminiClient;
		this.objectMapper = objectMapper;
		this.apiLogger = apiLogger;
		this.jsonParser = jsonParser;
	}

	@Override
	public SearchOptimization optimizeQuery(String query, String language) {
		if (!isServiceAvailable()) {
			log.warn("[AI-SEARCH] OpenAI not available, returning basic optimization");
			return createBasicOptimization(query);
		}

		String cacheKey = getCacheKey(query, language);
		CachedOptimization cached = optimizationCache.get(cacheKey);
		if (cached != null && !cached.isExpired()) {
			log.debug("[AI-SEARCH] Cache hit for query: {}", query);
			return cached.optimization;
		}

		apiLogger.openaiStart("QUERY_OPTIMIZATION", query.length());
		long startTime = System.currentTimeMillis();

		try {
			String prompt = buildQueryOptimizationPrompt(query, language);

			String content = geminiClient.callFlashModel(prompt);

			long elapsed = System.currentTimeMillis() - startTime;
			apiLogger.openaiResponseReceived(elapsed, content != null ? content.length() : 0);

			SearchOptimization optimization = parseOptimizationDirect(content);

			optimizationCache.put(cacheKey, new CachedOptimization(optimization));

			log.info("[AI-SEARCH] Optimized '{}' -> '{}' ({}ms)", query, optimization.getTranslatedQuery(), elapsed);

			return optimization;

		} catch (Exception e) {
			apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
			log.error("[AI-SEARCH] Query optimization failed: {}", e.getMessage());
			return createBasicOptimization(query);
		}
	}

	private SearchOptimization parseOptimizationDirect(String content) {
		try {
			String jsonText = jsonParser.extractJsonObject(content);
			JsonNode root = objectMapper.readTree(jsonText);

			String translatedQuery = root.has("translatedQuery") ? root.get("translatedQuery").asText() : "";

			String[] variations = parseJsonArray(root.get("searchVariations"));
			String[] excludes = parseJsonArray(root.get("excludeTerms"));
			String intent = root.has("intent") ? root.get("intent").asText() : "unknown";
			String[] prepKeywords = parseJsonArray(root.get("preparationKeywords"));

			SearchOptimization opt = new SearchOptimization(translatedQuery, variations, excludes, intent);
			opt.setPreparationKeywords(prepKeywords);
			return opt;

		} catch (Exception e) {
			log.error("[AI-SEARCH] Failed to parse optimization response: {}", e.getMessage());
			throw new RuntimeException("Failed to parse AI response", e);
		}
	}

	@Override
	public List<ScoredFoodResult> rankAndScoreResults(String originalQuery, List<NutritionInfo> results) {
		if (!isServiceAvailable() || results == null || results.isEmpty()) {
			return results.stream().map(r -> new ScoredFoodResult(r, 50, "Standard search", r.getFoodName()))
					.collect(Collectors.toList());
		}

		apiLogger.openaiStart("RESULT_RANKING", results.size());
		long startTime = System.currentTimeMillis();

		try {
			String prompt = buildRankingPrompt(originalQuery, results);

			String content = geminiClient.callFlashModel(prompt);

			long elapsed = System.currentTimeMillis() - startTime;
			apiLogger.openaiResponseReceived(elapsed, content != null ? content.length() : 0);

			List<ScoredFoodResult> scoredResults = parseRankingDirect(content, results);

			scoredResults.sort((a, b) -> Integer.compare(b.getRelevanceScore() != null ? b.getRelevanceScore() : 0,
					a.getRelevanceScore() != null ? a.getRelevanceScore() : 0));

			log.info("[AI-SEARCH] Ranked {} results for query '{}' ({}ms)", scoredResults.size(), originalQuery,
					elapsed);

			return scoredResults;

		} catch (Exception e) {
			apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
			log.error("[AI-SEARCH] Result ranking failed: {}", e.getMessage());

			return results.stream().map(r -> new ScoredFoodResult(r, 50, "AI ranking unavailable", r.getFoodName()))
					.collect(Collectors.toList());
		}
	}

	@Override
	public boolean isServiceAvailable() {
		return geminiClient.isServiceAvailable();
	}

	private String buildQueryOptimizationPrompt(String query, String language) {
		return String.format(
				"""
						You are a Food Search Query Optimizer for a diabetes management system.

						USER QUERY: "%s"
						USER LANGUAGE: %s

						TASK:
						1. If the query is in Hebrew, translate it to English.
						2. Identify how the food is typically prepared and return USDA-style preparation keywords.
						3. Generate 2-3 search variations for the USDA FoodData Central database.
						4. Identify terms to EXCLUDE from results.

						PREPARATION KEYWORDS — choose one or more from this list:
						  "cooked"   — for grains, pasta, legumes, potatoes, eggs (boiled/scrambled)
						  "raw"      — for fresh vegetables, fruits, salads
						  "fried"    — for fried foods (fries, schnitzel, fried chicken)
						  "roasted"  — for roasted/baked meats or vegetables
						  "broiled"  — for grilled meats or fish
						  "canned"   — for canned/preserved foods
						  "dried"    — for dried fruits, nuts, seeds
						  "baked"    — for bread, cakes, pastries

						RULES:
						- The first searchVariation MUST include the preparationKeyword
						  (e.g. if query is "white rice" and prep is "cooked" → first variation: "rice white cooked")
						- Exclude processed/branded variations unless specifically requested.

						OUTPUT (Strict JSON, no markdown):
						{
						  "translatedQuery": "white rice",
						  "searchVariations": ["rice white cooked", "rice, white, cooked", "white rice"],
						  "excludeTerms": ["pudding", "fried", "cake"],
						  "intent": "cooked grain",
						  "preparationKeywords": ["cooked"]
						}
						""",
				query, language);
	}

	private String buildRankingPrompt(String originalQuery, List<NutritionInfo> results) {
		StringBuilder resultsText = new StringBuilder();
		for (int i = 0; i < results.size(); i++) {
			NutritionInfo info = results.get(i);
			resultsText.append(String.format("%d. ID:%s | %s | %.1fg carbs\n", i, info.getFdcId(), info.getFoodName(),
					info.getCarbsPer100g()));
		}

		return String.format("""
				You are a Food Search Result Ranker for a diabetes management app.

				USER QUERY: "%s"

				RESULTS TO RANK:
				%s

				TASK:
				For each result:
				1. Assign a relevance score (0-100)
				2. Provide a brief match reason
				3. Create a clean DISPLAY NAME for the UI (see rules below)

				DISPLAY NAME RULES:
				- Remove descriptors like "cooked", "raw", "commercially prepared", "Chinese restaurant"
				- Use Title Case (e.g., "White Rice" not "white rice, cooked")
				- Keep it short and readable (2-4 words max)
				- Examples:
				  "Rice, white, cooked, Chinese restaurant" -> "White Rice"
				  "Bread, whole wheat, commercially prepared" -> "Whole Wheat Bread"
				  "Chicken breast, boneless, skinless, grilled" -> "Grilled Chicken Breast"

				SCORING RULES:
				- Exact match for basic home-cooked food: 95-100
				- Generic/plain version: 90-100
				- Contains all keywords, basic form: 80-94
				- Contains some keywords: 60-79

				PENALTIES:
				- Restaurant-specific ("Chinese restaurant", "fast food"): -30 to -40 points
				- Brand-specific products: -20 to -30 points
				- Heavily processed ("fried", "sweetened"): -15 to -25 points
				- Baby food: -30 points

				FILTERING: Items below 50 points should be excluded.

				OUTPUT (Strict JSON):
				{
				  "rankings": [
				    {"fdcId": "169756", "score": 95, "displayName": "White Rice", "reason": "Exact match"},
				    {"fdcId": "168878", "score": 85, "displayName": "Long Grain Rice", "reason": "Similar variant"}
				  ]
				}
				""", originalQuery, resultsText.toString());
	}

	private List<ScoredFoodResult> parseRankingDirect(String content, List<NutritionInfo> originalResults) {
		try {
			String jsonText = jsonParser.extractJsonObject(content);
			JsonNode root = objectMapper.readTree(jsonText);

			JsonNode rankings = root.get("rankings");
			if (rankings == null || !rankings.isArray()) {
				throw new IllegalStateException("No rankings array in response");
			}

			Map<String, NutritionInfo> resultsMap = originalResults.stream()
					.collect(Collectors.toMap(NutritionInfo::getFdcId, r -> r, (a, b) -> a));

			List<ScoredFoodResult> scoredResults = new ArrayList<>();

			for (JsonNode ranking : rankings) {
				String fdcId = ranking.get("fdcId").asText();
				int score = ranking.get("score").asInt();
				String reason = ranking.has("reason") ? ranking.get("reason").asText() : "Relevant match";
				String displayName = ranking.has("displayName") ? ranking.get("displayName").asText() : null;

				NutritionInfo original = resultsMap.get(fdcId);
				if (original != null && score >= 50) {
					scoredResults.add(new ScoredFoodResult(original, score, reason, displayName));
				}
			}

			return scoredResults;

		} catch (Exception e) {
			log.error("[AI-SEARCH] Failed to parse ranking response: {}", e.getMessage());
			throw new RuntimeException("Failed to parse AI response", e);
		}
	}

	private String[] parseJsonArray(JsonNode arrayNode) {
		if (arrayNode == null || !arrayNode.isArray()) {
			return new String[0];
		}

		List<String> items = new ArrayList<>();
		for (JsonNode item : arrayNode) {
			items.add(item.asText());
		}
		return items.toArray(new String[0]);
	}

	private SearchOptimization createBasicOptimization(String query) {
		return new SearchOptimization(query, new String[] { query }, new String[0], "basic");
	}

	private String getCacheKey(String query, String language) {
		return query.toLowerCase().trim() + "|" + language;
	}

	private static class CachedOptimization {
		final SearchOptimization optimization;
		final long timestamp;

		CachedOptimization(SearchOptimization optimization) {
			this.optimization = optimization;
			this.timestamp = System.currentTimeMillis();
		}

		boolean isExpired() {
			return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
		}
	}
}