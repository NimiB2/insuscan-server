package com.insuscan.service;

import com.insuscan.boundary.NutritionInfo;
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

    @Value("${insuscan.usda.api.key:}")
    private String apiKey;

    private static final String USDA_BASE_URL = "https://api.nal.usda.gov/fdc/v1";

    // Fallback data for common foods when API unavailable
    private static final Map<String, NutritionInfo> FALLBACK_DATA = initFallbackData();

    public NutritionDataServiceImpl(WebClient webClient, FoodNameNormalizer foodNameNormalizer) {
        this.webClient = webClient;
        this.foodNameNormalizer = foodNameNormalizer;
    }

    @Override
    public NutritionInfo getNutritionInfo(String foodName) {
        if (foodName == null || foodName.trim().isEmpty()) {
            log.warn("Empty food name provided");
            return NutritionInfo.notFound(foodName);
        }
        
        // Try fallback first with original name (faster, no API call)
        NutritionInfo fallback = getFallbackNutrition(foodName);
        if (fallback.isFound()) {
            log.info("Found in fallback data: '{}' -> {}g carbs/100g", foodName, fallback.getCarbsPer100g());
            return fallback;
        }
        
        // Normalize food name for USDA search
        String normalizedName = foodNameNormalizer.normalize(foodName);
        log.debug("Normalized food name: '{}' -> '{}'", foodName, normalizedName);
        
        // Try fallback again with normalized name
        if (!normalizedName.equalsIgnoreCase(foodName)) {
            fallback = getFallbackNutrition(normalizedName);
            if (fallback.isFound()) {
                log.info("Found in fallback data (normalized): '{}' -> {}g carbs/100g", normalizedName, fallback.getCarbsPer100g());
                return fallback;
            }
        }
        
        if (!isServiceAvailable()) {
            log.warn("USDA API key not configured, using fallback data");
            return fallback;
        }

        try {
            // Try multiple search terms for better matching
            List<String> searchTerms = foodNameNormalizer.getSearchTerms(normalizedName);
            List<NutritionInfo> results = new ArrayList<>();
            
            for (String term : searchTerms) {
                results = searchFoods(term, 3); // Get top 3 results
                if (!results.isEmpty()) {
                    // Find best match
                    NutritionInfo bestMatch = findBestMatch(normalizedName, results);
                    if (bestMatch != null) {
                        log.info("Found in USDA API: '{}' -> {}g carbs/100g (searched: {})", 
                                bestMatch.getFoodName(), bestMatch.getCarbsPer100g(), term);
                        return bestMatch;
                    }
                }
            }
            
            log.warn("No USDA results found for: '{}' (tried: {})", foodName, searchTerms);
            return fallback;

        } catch (Exception e) {
            log.error("USDA API call failed for '{}': {}", foodName, e.getMessage());
            return fallback;
        }
    }
    
    /**
     * Find the best matching nutrition info from search results
     */
    private NutritionInfo findBestMatch(String normalizedName, List<NutritionInfo> results) {
        if (results.isEmpty()) return null;
        
        String normalizedLower = normalizedName.toLowerCase();
        
        // Try exact match first
        for (NutritionInfo info : results) {
            if (info.getFoodName() != null && 
                info.getFoodName().toLowerCase().equals(normalizedLower)) {
                return info;
            }
        }
        
        // Try contains match
        for (NutritionInfo info : results) {
            String foodName = info.getFoodName() != null ? info.getFoodName().toLowerCase() : "";
            if (foodName.contains(normalizedLower) || normalizedLower.contains(foodName)) {
                return info;
            }
        }
        
        // Return first result if no better match
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
    @SuppressWarnings("unchecked")
    public List<NutritionInfo> searchFoods(String query, int maxResults) {
        if (!isServiceAvailable()) {
            NutritionInfo fallback = getFallbackNutrition(query);
            return fallback.isFound() ? List.of(fallback) : List.of();
        }

        try {
            // Call USDA search endpoint
            Map<String, Object> requestBody = Map.of(
                "query", query,
                "pageSize", maxResults,
                "dataType", List.of("Foundation", "SR Legacy")
            );

            Map response = webClient.post()
                .uri(USDA_BASE_URL + "/foods/search?api_key=" + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return parseSearchResponse(response);

        } catch (Exception e) {
            log.error("USDA search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isServiceAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    // Parse USDA search response
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

                // Extract nutrients
                List<Map> nutrients = (List<Map>) food.get("foodNutrients");
                if (nutrients != null) {
                    for (Map nutrient : nutrients) {
                        String name = (String) nutrient.get("nutrientName");
                        Number value = (Number) nutrient.get("value");
                        
                        if (name == null || value == null) continue;
                        
                        float val = value.floatValue();
                        String nameLower = name.toLowerCase();
                        
                        if (nameLower.contains("carbohydrate")) {
                            info.setCarbsPer100g(val);
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

    // Get fallback nutrition data for common foods
    private NutritionInfo getFallbackNutrition(String foodName) {
        if (foodName == null || foodName.trim().isEmpty()) {
            return NutritionInfo.notFound(foodName);
        }
        
        String key = foodName.toLowerCase().trim();
        
        // Try exact match first
        if (FALLBACK_DATA.containsKey(key)) {
            log.debug("Exact fallback match: '{}'", foodName);
            return FALLBACK_DATA.get(key);
        }
        
        // Try normalized match
        String normalized = foodNameNormalizer.normalize(foodName).toLowerCase();
        if (!normalized.equals(key) && FALLBACK_DATA.containsKey(normalized)) {
            log.debug("Normalized fallback match: '{}' -> '{}'", foodName, normalized);
            return FALLBACK_DATA.get(normalized);
        }
        
        // Try word-by-word matching (e.g., "parmesan cheese" matches "cheese")
        String[] words = key.split("\\s+");
        for (String word : words) {
            if (word.length() > 3 && FALLBACK_DATA.containsKey(word)) {
                log.debug("Word-based fallback match: '{}' -> '{}'", foodName, word);
                return FALLBACK_DATA.get(word);
            }
        }
        
        // Try partial match (check if key contains any fallback key or vice versa)
        for (Map.Entry<String, NutritionInfo> entry : FALLBACK_DATA.entrySet()) {
            String fallbackKey = entry.getKey();
            if (key.contains(fallbackKey) || fallbackKey.contains(key) ||
                normalized.contains(fallbackKey) || fallbackKey.contains(normalized)) {
                log.debug("Partial fallback match: '{}' -> '{}'", foodName, fallbackKey);
                return entry.getValue();
            }
        }
        
        log.debug("No fallback match found for: '{}' (tried: '{}', normalized: '{}')", foodName, key, normalized);
        return NutritionInfo.notFound(foodName);
    }

    // Initialize fallback nutrition data for common foods (expanded list)
    private static Map<String, NutritionInfo> initFallbackData() {
        Map<String, NutritionInfo> data = new HashMap<>();
        
        // Grains & Starches (carbs per 100g)
        data.put("rice", createFallback("rice", 28f));
        data.put("white rice", createFallback("white rice", 28f));
        data.put("brown rice", createFallback("brown rice", 23f));
        data.put("bread", createFallback("bread", 49f));
        data.put("white bread", createFallback("white bread", 49f));
        data.put("whole wheat bread", createFallback("whole wheat bread", 41f));
        data.put("pasta", createFallback("pasta", 25f));
        data.put("spaghetti", createFallback("spaghetti", 25f));
        data.put("penne pasta", createFallback("penne pasta", 25f));
        data.put("macaroni", createFallback("macaroni", 25f));
        data.put("noodles", createFallback("noodles", 25f));
        data.put("potato", createFallback("potato", 17f));
        data.put("mashed potato", createFallback("mashed potato", 17f));
        data.put("baked potato", createFallback("baked potato", 17f));
        data.put("french fries", createFallback("french fries", 41f));
        data.put("fries", createFallback("french fries", 41f));
        data.put("corn", createFallback("corn", 19f));
        data.put("quinoa", createFallback("quinoa", 22f));
        data.put("oats", createFallback("oats", 66f));
        
        // Proteins (mostly 0 carbs)
        data.put("chicken", createFallback("chicken", 0f));
        data.put("chicken breast", createFallback("chicken breast", 0f));
        data.put("chicken thigh", createFallback("chicken thigh", 0f));
        data.put("chicken wings", createFallback("chicken wings", 0f));
        data.put("fried chicken", createFallback("fried chicken", 3f));
        data.put("grilled chicken", createFallback("grilled chicken", 0f));
        data.put("beef", createFallback("beef", 0f));
        data.put("ground beef", createFallback("ground beef", 0f));
        data.put("beef steak", createFallback("beef steak", 0f));
        data.put("pork", createFallback("pork", 0f));
        data.put("fish", createFallback("fish", 0f));
        data.put("salmon", createFallback("salmon", 0f));
        data.put("tuna", createFallback("tuna", 0f));
        data.put("cod", createFallback("cod", 0f));
        data.put("tilapia", createFallback("tilapia", 0f));
        data.put("egg", createFallback("egg", 1.1f));
        data.put("eggs", createFallback("egg", 1.1f));
        data.put("tofu", createFallback("tofu", 1.9f));
        
        // Dairy
        data.put("cheese", createFallback("cheese", 1.3f));
        data.put("cheddar cheese", createFallback("cheddar cheese", 1.3f));
        data.put("mozzarella cheese", createFallback("mozzarella cheese", 2.2f));
        data.put("parmesan cheese", createFallback("parmesan cheese", 4.1f));
        data.put("swiss cheese", createFallback("swiss cheese", 1.4f));
        data.put("milk", createFallback("milk", 5f));
        data.put("yogurt", createFallback("yogurt", 10f));
        
        // Vegetables (low carbs)
        data.put("vegetables", createFallback("mixed vegetables", 7f));
        data.put("mixed vegetables", createFallback("mixed vegetables", 7f));
        data.put("salad", createFallback("salad", 3f));
        data.put("lettuce", createFallback("lettuce", 2.9f));
        data.put("tomato", createFallback("tomato", 3.9f));
        data.put("carrot", createFallback("carrot", 10f));
        data.put("broccoli", createFallback("broccoli", 7f));
        data.put("spinach", createFallback("spinach", 3.6f));
        data.put("onion", createFallback("onion", 9.3f));
        data.put("bell pepper", createFallback("bell pepper", 6f));
        data.put("cucumber", createFallback("cucumber", 4f));
        data.put("zucchini", createFallback("zucchini", 3.1f));
        
        // Fruits (moderate carbs)
        data.put("apple", createFallback("apple", 14f));
        data.put("banana", createFallback("banana", 23f));
        data.put("orange", createFallback("orange", 12f));
        data.put("orange juice", createFallback("orange juice", 10f));
        data.put("strawberry", createFallback("strawberry", 8f));
        data.put("grapes", createFallback("grapes", 16f));
        data.put("watermelon", createFallback("watermelon", 8f));
        data.put("pineapple", createFallback("pineapple", 13f));
        
        // Sauces & Condiments
        data.put("sauce", createFallback("sauce", 10f));
        data.put("marinara sauce", createFallback("marinara sauce", 7f));
        data.put("tomato sauce", createFallback("tomato sauce", 7f));
        data.put("alfredo sauce", createFallback("alfredo sauce", 3f));
        data.put("ketchup", createFallback("ketchup", 25f));
        data.put("mayonnaise", createFallback("mayonnaise", 0.6f));
        
        // Legumes
        data.put("beans", createFallback("beans", 23f));
        data.put("black beans", createFallback("black beans", 23f));
        data.put("chickpeas", createFallback("chickpeas", 27f));
        data.put("lentils", createFallback("lentils", 20f));
        
        return data;
    }

    // Helper to create fallback NutritionInfo
    private static NutritionInfo createFallback(String name, float carbs) {
        NutritionInfo info = new NutritionInfo();
        info.setFoodName(name);
        info.setFound(true);
        info.setCarbsPer100g(carbs);
        info.setFdcId("fallback-" + name);
        return info;
    }
}
