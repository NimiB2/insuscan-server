package com.insuscan.service;

import com.insuscan.boundary.NutritionInfo;
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

    @Value("${insuscan.usda.api.key:}")
    private String apiKey;

    private static final String USDA_BASE_URL = "https://api.nal.usda.gov/fdc/v1";

    // Fallback data for common foods when API unavailable
    private static final Map<String, NutritionInfo> FALLBACK_DATA = initFallbackData();

    public NutritionDataServiceImpl(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public NutritionInfo getNutritionInfo(String foodName) {
        if (!isServiceAvailable()) {
            log.warn("USDA API key not configured, using fallback data");
            return getFallbackNutrition(foodName);
        }

        try {
            // Search USDA for this food
            List<NutritionInfo> results = searchFoods(foodName, 1);
            if (!results.isEmpty()) {
                return results.get(0);
            }
            return NutritionInfo.notFound(foodName);

        } catch (Exception e) {
            log.error("USDA API call failed for {}: {}", foodName, e.getMessage());
            return getFallbackNutrition(foodName);
        }
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
                        } else if (nameLower.contains("energy") && nameLower.contains("kcal")) {
                            info.setCaloriesPer100g(val);
                        } else if (nameLower.contains("protein")) {
                            info.setProteinPer100g(val);
                        } else if (nameLower.contains("fat") && nameLower.contains("total")) {
                            info.setFatPer100g(val);
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
        String key = foodName.toLowerCase().trim();
        
        // Try exact match
        if (FALLBACK_DATA.containsKey(key)) {
            return FALLBACK_DATA.get(key);
        }
        
        // Try partial match
        for (Map.Entry<String, NutritionInfo> entry : FALLBACK_DATA.entrySet()) {
            if (key.contains(entry.getKey()) || entry.getKey().contains(key)) {
                return entry.getValue();
            }
        }
        
        return NutritionInfo.notFound(foodName);
    }

    // Initialize fallback nutrition data for common foods
    private static Map<String, NutritionInfo> initFallbackData() {
        Map<String, NutritionInfo> data = new HashMap<>();
        
        // Common foods with approximate values per 100g
        data.put("rice", createFallback("rice", 28f, 130f, 2.7f, 0.3f));
        data.put("white rice", createFallback("white rice", 28f, 130f, 2.7f, 0.3f));
        data.put("bread", createFallback("bread", 49f, 265f, 9f, 3.2f));
        data.put("pasta", createFallback("pasta", 25f, 131f, 5f, 1.1f));
        data.put("potato", createFallback("potato", 17f, 77f, 2f, 0.1f));
        data.put("chicken", createFallback("chicken", 0f, 165f, 31f, 3.6f));
        data.put("beef", createFallback("beef", 0f, 250f, 26f, 15f));
        data.put("fish", createFallback("fish", 0f, 206f, 22f, 12f));
        data.put("egg", createFallback("egg", 1.1f, 155f, 13f, 11f));
        data.put("apple", createFallback("apple", 14f, 52f, 0.3f, 0.2f));
        data.put("banana", createFallback("banana", 23f, 89f, 1.1f, 0.3f));
        data.put("orange", createFallback("orange", 12f, 47f, 0.9f, 0.1f));
        data.put("vegetables", createFallback("vegetables", 7f, 35f, 2f, 0.3f));
        data.put("salad", createFallback("salad", 3f, 20f, 1.5f, 0.2f));
        data.put("cheese", createFallback("cheese", 1.3f, 402f, 25f, 33f));
        data.put("milk", createFallback("milk", 5f, 61f, 3.2f, 3.3f));
        
        return data;
    }

    // Helper to create fallback NutritionInfo
    private static NutritionInfo createFallback(String name, float carbs, 
            float calories, float protein, float fat) {
        NutritionInfo info = new NutritionInfo();
        info.setFoodName(name);
        info.setFound(true);
        info.setCarbsPer100g(carbs);
        info.setCaloriesPer100g(calories);
        info.setProteinPer100g(protein);
        info.setFatPer100g(fat);
        info.setFdcId("fallback-" + name);
        return info;
    }
}
