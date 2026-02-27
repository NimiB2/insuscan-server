package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.boundary.NutritionInfo;
import com.insuscan.boundary.ScoredFoodResult;
import com.insuscan.boundary.SearchOptimization;
import com.insuscan.util.ApiLogger;
import com.insuscan.util.OpenAiJsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class FoodSearchAiServiceImpl implements FoodSearchAiService {

	private static final Logger log = LoggerFactory.getLogger(FoodSearchAiServiceImpl.class);

	private final WebClient webClient;
	private final ObjectMapper objectMapper;
	private final ApiLogger apiLogger;
	private final OpenAiJsonParser jsonParser;

	@Value("${openai.api.key:}")
	private String openAiApiKey;

	@Value("${openai.model:gpt-4o-mini}")
	private String openAiModel;

	// Simple in-memory cache for query optimizations (1 hour TTL)
	private final Map<String, CachedOptimization> optimizationCache = new ConcurrentHashMap<>();
	private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

	public FoodSearchAiServiceImpl(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, ApiLogger apiLogger,
			OpenAiJsonParser jsonParser) {
		this.webClient = webClientBuilder.baseUrl("https://api.openai.com/v1").build();
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

		// Check cache
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
			Map<String, Object> requestBody = buildRequestBody(prompt);

			String response = webClient.post().uri("/chat/completions")
					.header("Authorization", "Bearer " + openAiApiKey).header("Content-Type", "application/json")
					.bodyValue(requestBody).retrieve().bodyToMono(String.class).block();

			long elapsed = System.currentTimeMillis() - startTime;
			apiLogger.openaiResponseReceived(elapsed, response != null ? response.length() : 0);

			SearchOptimization optimization = parseOptimizationResponse(response);

			// Cache the result
			optimizationCache.put(cacheKey, new CachedOptimization(optimization));

			log.info("[AI-SEARCH] Optimized '{}' -> '{}' ({}ms)", query, optimization.getTranslatedQuery(), elapsed);

			return optimization;

		} catch (Exception e) {
			apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
			log.error("[AI-SEARCH] Query optimization failed: {}", e.getMessage());
			return createBasicOptimization(query);
		}
	}

	@Override
	public List<ScoredFoodResult> rankAndScoreResults(String originalQuery, List<NutritionInfo> results) {
		if (!isServiceAvailable() || results == null || results.isEmpty()) {
			// Fallback: return results with default scores and original name as displayName
			return results.stream().map(r -> new ScoredFoodResult(r, 50, "Standard search", r.getFoodName()))
					.collect(Collectors.toList());
		}

		apiLogger.openaiStart("RESULT_RANKING", results.size());
		long startTime = System.currentTimeMillis();

		try {
			String prompt = buildRankingPrompt(originalQuery, results);
			Map<String, Object> requestBody = buildRequestBody(prompt);

			String response = webClient.post().uri("/chat/completions")
					.header("Authorization", "Bearer " + openAiApiKey).header("Content-Type", "application/json")
					.bodyValue(requestBody).retrieve().bodyToMono(String.class).block();

			long elapsed = System.currentTimeMillis() - startTime;
			apiLogger.openaiResponseReceived(elapsed, response != null ? response.length() : 0);

			List<ScoredFoodResult> scoredResults = parseRankingResponse(response, results);

			// Sort by relevance score (highest first)
			scoredResults.sort((a, b) -> Integer.compare(b.getRelevanceScore() != null ? b.getRelevanceScore() : 0,
					a.getRelevanceScore() != null ? a.getRelevanceScore() : 0));

			log.info("[AI-SEARCH] Ranked {} results for query '{}' ({}ms)", scoredResults.size(), originalQuery,
					elapsed);

			return scoredResults;

		} catch (Exception e) {
			apiLogger.openaiError(e.getMessage(), e.getClass().getSimpleName());
			log.error("[AI-SEARCH] Result ranking failed: {}", e.getMessage());

			// Fallback: return results with default scores and original name
			return results.stream().map(r -> new ScoredFoodResult(r, 50, "AI ranking unavailable", r.getFoodName()))
					.collect(Collectors.toList());
		}
	}

	@Override
	public boolean isServiceAvailable() {
		return openAiApiKey != null && !openAiApiKey.isBlank();
	}

	// ===== PRIVATE HELPER METHODS =====

	private String buildQueryOptimizationPrompt(String query, String language) {
		return String.format(
				"""
						You are a Food Search Query Optimizer for a diabetes management system.

						USER QUERY: "%s"
						USER LANGUAGE: %s

						TASK:
						1. If the query is in Hebrew, translate it to English
						2. Identify the user's search intent (basic ingredient, prepared dish, etc.)
						3. Generate 2-3 search variations that would find the item in USDA database
						4. Identify terms that should EXCLUDE results (e.g., "pudding", "fried", "cake" if user wants plain rice)

						RULES:
						- For basic ingredients, prioritize plain/cooked versions
						- Exclude processed/prepared variations unless specifically requested
						- Generate both specific and general search terms

						OUTPUT (Strict JSON):
						{
						  "translatedQuery": "white rice",
						  "searchVariations": ["white rice", "rice, white, cooked", "rice white long-grain"],
						  "excludeTerms": ["pudding", "fried", "cake", "mix"],
						  "intent": "basic ingredient"
						}
						""",
				query, language);
	}

	private String buildRankingPrompt(String originalQuery, List<NutritionInfo> results) {
		// Build compact results list for the prompt
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

	private Map<String, Object> buildRequestBody(String prompt) {
		return Map.of("model", openAiModel, "messages",
				List.of(Map.of("role", "system", "content",
						"You are a JSON-only response engine. Always return valid JSON."),
						Map.of("role", "user", "content", prompt)),
				"temperature", 0.3, "response_format", Map.of("type", "json_object"));
	}

	private SearchOptimization parseOptimizationResponse(String jsonResponse) {
		try {
			String content = extractContentFromResponse(jsonResponse);
			String jsonText = extractFirstJsonObject(content);
			JsonNode root = objectMapper.readTree(jsonText);

			String translatedQuery = root.has("translatedQuery") ? root.get("translatedQuery").asText() : "";

			String[] variations = parseJsonArray(root.get("searchVariations"));
			String[] excludes = parseJsonArray(root.get("excludeTerms"));
			String intent = root.has("intent") ? root.get("intent").asText() : "unknown";

			return new SearchOptimization(translatedQuery, variations, excludes, intent);

		} catch (Exception e) {
			log.error("[AI-SEARCH] Failed to parse optimization response: {}", e.getMessage());
			throw new RuntimeException("Failed to parse AI response", e);
		}
	}

	private List<ScoredFoodResult> parseRankingResponse(String jsonResponse, List<NutritionInfo> originalResults) {
		try {
			String content = extractContentFromResponse(jsonResponse);
			String jsonText = extractFirstJsonObject(content);
			JsonNode root = objectMapper.readTree(jsonText);

			JsonNode rankings = root.get("rankings");
			if (rankings == null || !rankings.isArray()) {
				throw new IllegalStateException("No rankings array in response");
			}

			// Create a map of fdcId -> original result for quick lookup
			Map<String, NutritionInfo> resultsMap = originalResults.stream()
					.collect(Collectors.toMap(NutritionInfo::getFdcId, r -> r, (a, b) -> a));

			List<ScoredFoodResult> scoredResults = new ArrayList<>();

			for (JsonNode ranking : rankings) {
				String fdcId = ranking.get("fdcId").asText();
				int score = ranking.get("score").asInt();
				String reason = ranking.has("reason") ? ranking.get("reason").asText() : "Relevant match";
				String displayName = ranking.has("displayName") ? ranking.get("displayName").asText() : null;

				NutritionInfo original = resultsMap.get(fdcId);
				if (original != null && score >= 50) { // Filter out low-scoring results
					scoredResults.add(new ScoredFoodResult(original, score, reason, displayName));
				}
			}

			return scoredResults;

		} catch (Exception e) {
			log.error("[AI-SEARCH] Failed to parse ranking response: {}", e.getMessage());
			throw new RuntimeException("Failed to parse AI response", e);
		}
	}

	private String extractContentFromResponse(String rawResponse) {
		return jsonParser.extractContent(rawResponse);
	}

	private String extractFirstJsonObject(String raw) {
		return jsonParser.extractJsonObject(raw);
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
		// Fallback: create basic optimization without AI
		return new SearchOptimization(query, new String[] { query }, new String[0], "basic");
	}

	private String getCacheKey(String query, String language) {
		return query.toLowerCase().trim() + "|" + language;
	}

	// ===== CACHE HELPER CLASS =====

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
