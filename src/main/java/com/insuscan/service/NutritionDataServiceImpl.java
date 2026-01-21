package com.insuscan.service;

import com.insuscan.boundary.NutritionInfo;
import com.insuscan.util.ApiLogger;
import com.insuscan.util.FoodNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class NutritionDataServiceImpl implements NutritionDataService {

    private static final Logger log = LoggerFactory.getLogger(NutritionDataServiceImpl.class);

    private final WebClient webClient;
    private final FoodNameNormalizer foodNameNormalizer;
    private final ApiLogger apiLogger;

    @Value("${insuscan.usda.api.key:}")
    private String apiKey;

    private static final String USDA_BASE_URL = "https://api.nal.usda.gov/fdc/v1";

    // Fallback data for common foods when API unavailable
    private static final Map<String, NutritionInfo> FALLBACK_DATA = initFallbackData();

    public NutritionDataServiceImpl(WebClient.Builder webClientBuilder, 
                                   FoodNameNormalizer foodNameNormalizer,
                                   ApiLogger apiLogger) {
    	int bufferSize = 16 * 1024 * 1024; 

        this.webClient = webClientBuilder
                .baseUrl(USDA_BASE_URL)
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(bufferSize)) 
                .build();
        this.foodNameNormalizer = foodNameNormalizer;
        this.apiLogger = apiLogger;
    }

    @Override
    public NutritionInfo getNutritionInfo(String foodName) {
        if (foodName == null || foodName.trim().isEmpty()) {
            log.warn("Empty food name provided");
            return NutritionInfo.notFound(foodName);
        }

        // Normalize and prepare search terms
        String normalizedName = foodNameNormalizer.normalize(foodName);
        List<String> searchTerms = foodNameNormalizer.getSearchTerms(normalizedName);
        
        // Log the lookup start
        apiLogger.usdaStart(foodName, normalizedName, searchTerms);

        // Check if API is available
        if (!isServiceAvailable()) {
            String keyPreview = apiKey != null && apiKey.length() > 5 ? apiKey.substring(0, 5) : "N/A";
            apiLogger.apiKeyStatus("USDA", false, keyPreview);
            
            // No API key - use fallback
            NutritionInfo fallback = getFallbackNutrition(foodName);
            if (fallback.isFound()) {
                apiLogger.usdaFallbackHit(foodName, fallback.getCarbsPer100g());
            } else {
                apiLogger.usdaNoMatch(foodName, false);
            }
            return fallback;
        }

        // API is available - try USDA first!
        try {
            for (String term : searchTerms) {
                apiLogger.usdaApiCall(term);
                long startTime = System.currentTimeMillis();

                List<NutritionInfo> results = searchFoodsInternal(term, 3);

                long elapsed = System.currentTimeMillis() - startTime;
                apiLogger.usdaApiResponse(elapsed, results.size(), "N/A");

                if (!results.isEmpty()) {
                    // Log all results
                    for (NutritionInfo r : results) {
                        log.debug("[USDA]   -> {} (fdcId: {}, carbs: {}g)", 
                            r.getFoodName(), r.getFdcId(), r.getCarbsPer100g());
                    }

                    NutritionInfo bestMatch = findBestMatch(normalizedName, results);
                    if (bestMatch != null) {
                        apiLogger.usdaMatchFound(bestMatch.getFoodName(), bestMatch.getFdcId(), bestMatch.getCarbsPer100g());
                        return bestMatch;
                    }
                }
            }

            // API returned no results - now use fallback
            log.info("[USDA] API returned no results, checking fallback...");
            NutritionInfo fallback = getFallbackNutrition(foodName);
            if (fallback.isFound()) {
                apiLogger.usdaFallbackHit(foodName + " (after API miss)", fallback.getCarbsPer100g());
                return fallback;
            }
            
            apiLogger.usdaNoMatch(foodName, false);
            return NutritionInfo.notFound(foodName);

        } catch (Exception e) {
            apiLogger.usdaError(e.getMessage());
            
            // API failed - use fallback
            NutritionInfo fallback = getFallbackNutrition(foodName);
            if (fallback.isFound()) {
                apiLogger.usdaFallbackHit(foodName + " (after API error)", fallback.getCarbsPer100g());
            }
            return fallback;
        }
    }

    private NutritionInfo findBestMatch(String normalizedName, List<NutritionInfo> results) {
        if (results.isEmpty()) return null;

        String normalizedLower = normalizedName.toLowerCase();

        // Exact match
        for (NutritionInfo info : results) {
            if (info.getFoodName() != null &&
                info.getFoodName().toLowerCase().equals(normalizedLower)) {
                return info;
            }
        }

        // Contains match
        for (NutritionInfo info : results) {
            String foodName = info.getFoodName() != null ? info.getFoodName().toLowerCase() : "";
            if (foodName.contains(normalizedLower) || normalizedLower.contains(foodName)) {
                return info;
            }
        }

        // Return first result
        return results.get(0);
    }

    @Override
    public List<NutritionInfo> getNutritionInfoBatch(List<String> foodNames) {
        List<NutritionInfo> results = new ArrayList<>();
        for (String name : foodNames) {
            results.add(getNutritionInfo(name));
        }
        return results;
    }

    @Override
    public List<NutritionInfo> searchFoods(String query, int maxResults) {
        apiLogger.usdaApiCall(query);
        
        if (!isServiceAvailable()) {
            NutritionInfo fallback = getFallbackNutrition(query);
            return fallback.isFound() ? List.of(fallback) : List.of();
        }

        return searchFoodsInternal(query, maxResults);
    }

    @SuppressWarnings("unchecked")
    private List<NutritionInfo> searchFoodsInternal(String query, int maxResults) {
        try {
            // --- MEDICAL GRADE CONFIGURATION ---
            Map<String, Object> requestBody = Map.of(
                "query", query,
                // Fetch enough results to ensure the correct item is present
                "pageSize", Math.max(maxResults, 20), 
                // ONLY use lab-verified data (No "Branded" or user-submitted data)
                "dataType", List.of("Foundation", "SR Legacy"), 
                // STRICT MODE: "Apple" should not return "Apple Pie"
                "requireAllWords", true 
            );

            Map response = webClient.post()
                .uri(USDA_BASE_URL + "/foods/search?api_key=" + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return parseSearchResponse(response);

        } catch (Exception e) {
            apiLogger.usdaError("USDA Search Failed: " + e.getMessage());
            return List.of();
        }
    }
    
    /**
     * MEDICAL GRADE RETRIEVAL:
     * Fetches a pool of high-quality candidates for the Semantic Matcher (Step 3).
     * Uses strictly "Foundation" and "SR Legacy" data types.
     */
    public List<NutritionInfo> searchCandidates(String baseIngredient) {
        if (baseIngredient == null || baseIngredient.trim().isEmpty()) {
            return List.of();
        }
        
        // 1. Normalize: Remove special chars to ensure clean search
        String cleanQuery = baseIngredient.replaceAll("[^a-zA-Z0-9 ]", "").trim();
        
        // 2. Fetch 25 candidates (The "Wide Net" strategy)
        apiLogger.usdaApiCall("Candidate Search: " + cleanQuery);
        return searchFoodsInternal(cleanQuery, 20);
    }

    @Override
    public boolean isServiceAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<NutritionInfo> parseSearchResponse(Map response) {
        List<NutritionInfo> results = new ArrayList<>();

        try {
            List<Map> foods = (List<Map>) response.get("foods");
            if (foods == null) return results;

            for (Map food : foods) {
                NutritionInfo info = new NutritionInfo();
                info.setFound(true);
                info.setFdcId(String.valueOf(food.get("fdcId")));
                info.setFoodName((String) food.get("description"));

                List<Map> nutrients = (List<Map>) food.get("foodNutrients");
                if (nutrients != null) {
                    for (Map nutrient : nutrients) {
                        String name = (String) nutrient.get("nutrientName");
                        Number value = (Number) nutrient.get("value");

                        if (name == null || value == null) continue;

                        if (name.toLowerCase().contains("carbohydrate")) {
                            info.setCarbsPer100g(value.floatValue());
                        }
                    }
                }

                results.add(info);
            }

        } catch (Exception e) {
            log.error("Failed to parse USDA response: {}", e.getMessage());
        }

        return results;
    }

    private NutritionInfo getFallbackNutrition(String foodName) {
        if (foodName == null || foodName.trim().isEmpty()) {
            return NutritionInfo.notFound(foodName);
        }

        String key = foodName.toLowerCase().trim();

        if (FALLBACK_DATA.containsKey(key)) {
            return FALLBACK_DATA.get(key);
        }

        String normalized = foodNameNormalizer.normalize(foodName).toLowerCase();
        if (!normalized.equals(key) && FALLBACK_DATA.containsKey(normalized)) {
            return FALLBACK_DATA.get(normalized);
        }

        String[] words = key.split("\\s+");
        for (String word : words) {
            if (word.length() > 3 && FALLBACK_DATA.containsKey(word)) {
                return FALLBACK_DATA.get(word);
            }
        }

        for (Map.Entry<String, NutritionInfo> entry : FALLBACK_DATA.entrySet()) {
            String fallbackKey = entry.getKey();
            if (key.contains(fallbackKey) || fallbackKey.contains(key) ||
                normalized.contains(fallbackKey) || fallbackKey.contains(normalized)) {
                return entry.getValue();
            }
        }

        return NutritionInfo.notFound(foodName);
    }

    // Fallback data (same as before)
    private static Map<String, NutritionInfo> initFallbackData() {
        Map<String, NutritionInfo> data = new HashMap<>();

        // Grains
        data.put("rice", createFallback("rice", 28f));
        data.put("white rice", createFallback("white rice", 28f));
        data.put("bread", createFallback("bread", 49f));
        data.put("pasta", createFallback("pasta", 25f));
        data.put("spaghetti", createFallback("spaghetti", 25f));
        data.put("potato", createFallback("potato", 17f));

        // Proteins
        data.put("chicken", createFallback("chicken", 0f));
        data.put("chicken breast", createFallback("chicken breast", 0f));
        data.put("beef", createFallback("beef", 0f));
        data.put("fish", createFallback("fish", 0f));
        data.put("salmon", createFallback("salmon", 0f));
        data.put("egg", createFallback("egg", 1.1f));

        // Dairy
        data.put("cheese", createFallback("cheese", 1.3f));
        data.put("milk", createFallback("milk", 5f));

        // Vegetables
        data.put("salad", createFallback("salad", 3f));
        data.put("tomato", createFallback("tomato", 3.9f));
        data.put("broccoli", createFallback("broccoli", 7f));

        // Fruits
        data.put("apple", createFallback("apple", 14f));
        data.put("banana", createFallback("banana", 23f));

        return data;
    }

    private static NutritionInfo createFallback(String name, float carbs) {
        NutritionInfo info = new NutritionInfo();
        info.setFoodName(name);
        info.setFound(true);
        info.setCarbsPer100g(carbs);
        info.setFdcId("fallback-" + name);
        return info;
    }
}