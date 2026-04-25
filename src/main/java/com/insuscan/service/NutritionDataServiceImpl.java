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
                        apiLogger.usdaMatchFound(bestMatch.getFoodName(), bestMatch.getFdcId(),
                                bestMatch.getCarbsPer100g());
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

    @Override
    public NutritionInfo getNutritionInfoById(String fdcId) {
        if (fdcId == null || fdcId.isBlank()) {
            return NutritionInfo.notFound("unknown");
        }

        // Fallback entries don't have real USDA IDs
        if (fdcId.startsWith("fallback-")) {
            String foodName = fdcId.replace("fallback-", "");
            return getFallbackNutrition(foodName);
        }

        if (!isServiceAvailable()) {
            log.warn("[USDA] API key missing, can't fetch by ID: {}", fdcId);
            return NutritionInfo.notFound(fdcId);
        }

        try {
            apiLogger.usdaApiCall("FDC ID lookup: " + fdcId);
            long startTime = System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri(USDA_BASE_URL + "/food/" + fdcId + "?api_key=" + apiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;

            if (response == null) {
                apiLogger.usdaError("Empty response for fdcId: " + fdcId);
                return NutritionInfo.notFound(fdcId);
            }

            NutritionInfo info = new NutritionInfo();
            info.setFound(true);
            info.setFdcId(fdcId);
            info.setFoodName((String) response.get("description"));

            // Parse nutrients
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nutrients = (List<Map<String, Object>>) response.get("foodNutrients");
            float fiber = 0f;

            if (nutrients != null) {
                for (Map<String, Object> nutrient : nutrients) {
                    // USDA detail endpoint nests nutrient name inside a "nutrient" object
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nutrientInfo = (Map<String, Object>) nutrient.get("nutrient");
                    String name = (nutrientInfo != null)
                            ? (String) nutrientInfo.get("name")
                            : (String) nutrient.get("nutrientName");
                    Number value = (Number) nutrient.get("amount");
                    if (value == null)
                        value = (Number) nutrient.get("value");

                    if (name == null || value == null)
                        continue;

                    String lower = name.toLowerCase();
                    if (lower.contains("carbohydrate")) {
                        info.setCarbsPer100g(value.floatValue());
                    } else if (lower.contains("fiber")) {
                        fiber = value.floatValue();
                    } else if (lower.contains("protein")) {
                        info.setProteinPer100g(value.floatValue());
                    } else if (lower.contains("fat") && !lower.contains("fatty")) {
                        info.setFatPer100g(value.floatValue());
                    } else if (lower.contains("water") || lower.contains("moisture")) {
                        info.setWaterPer100g(value.floatValue());
                    } else if (lower.equals("ash")) {
                        info.setAshPer100g(value.floatValue());
                    }
                }
            }
            info.setFiberPer100g(fiber);

            // Parse serving/portion data for density
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> portions = (List<Map<String, Object>>) response.get("foodPortions");
            if (portions != null && !portions.isEmpty()) {
                for (Map<String, Object> portion : portions) {
                    Number gramWeight = (Number) portion.get("gramWeight");
                    String desc = (String) portion.get("portionDescription");
                    if (desc == null)
                        desc = (String) portion.get("modifier");

                    if (gramWeight != null && desc != null && gramWeight.floatValue() > 0) {
                        float volumeCm3 = estimateVolumeCm3FromDescription(desc);
                        if (volumeCm3 > 0) {
                            float density = gramWeight.floatValue() / volumeCm3;
                            if (density >= 0.1f && density <= 1.4f) {
                                info.setDensityGPerCm3(density);
                                info.setDensitySource("USDA");
                                info.setServingVolumeCm3(volumeCm3);
                                break;
                            }
                        }
                    }
                }
            }

            apiLogger.usdaApiResponse(elapsed, 1, fdcId);
            log.info("[USDA] Fetched by ID {}: '{}', carbs={}g/100g, density={}",
                    fdcId, info.getFoodName(), info.getCarbsPer100g(), info.getDensityGPerCm3());

            return info;

        } catch (Exception e) {
            apiLogger.usdaError("Failed to fetch fdcId " + fdcId + ": " + e.getMessage());
            return NutritionInfo.notFound(fdcId);
        }
    }

    private NutritionInfo findBestMatch(String normalizedName, List<NutritionInfo> results) {
        if (results.isEmpty())
            return null;

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
                    "requireAllWords", true);

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

        String cleanQuery = baseIngredient.replaceAll("[^a-zA-Z0-9 ]", "").trim();
        apiLogger.usdaApiCall("Candidate Search: " + cleanQuery);

        List<NutritionInfo> candidates = searchFoodsInternal(cleanQuery, 20).stream()
                .sorted(Comparator.comparing(NutritionInfo::getFdcId))
                .toList();

        return candidates.stream()
                .map(candidate -> {
                    if (candidate.getWaterPer100g() <= 0 && !candidate.getFdcId().startsWith("fallback-")) {
                        NutritionInfo enriched = getNutritionInfoById(candidate.getFdcId());
                        return enriched.isFound() ? enriched : candidate;
                    }
                    return candidate;
                })
                .toList();
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
            if (foods == null)
                return results;

            for (Map food : foods) {
                NutritionInfo info = new NutritionInfo();
                info.setFound(true);
                info.setFdcId(String.valueOf(food.get("fdcId")));
                info.setFoodName((String) food.get("description"));

                List<Map> nutrients = (List<Map>) food.get("foodNutrients");
                if (nutrients != null) {
                    float fiber = 0f;
                    float protein = 0f;
                    float fat = 0f;

                    for (Map nutrient : nutrients) {
                        String name = (String) nutrient.get("nutrientName");
                        Number value = (Number) nutrient.get("value");

                        if (name == null || value == null)
                            continue;

                        String lower = name.toLowerCase();
                        if (lower.contains("carbohydrate")) {
                            info.setCarbsPer100g(value.floatValue());
                        } else if (lower.contains("fiber")) {
                            fiber = value.floatValue();
                        } else if (lower.contains("protein")) {
                            protein = value.floatValue();
                            info.setProteinPer100g(value.floatValue());
                        } else if (lower.contains("fat") && !lower.contains("fatty")) {
                            fat = value.floatValue();
                            info.setFatPer100g(value.floatValue());
                        } else if (lower.contains("water") || lower.contains("moisture")) {
                            info.setWaterPer100g(value.floatValue());
                        } else if (lower.equals("ash")) {
                            info.setAshPer100g(value.floatValue());
                        }
                    }

                    info.setFiberPer100g(fiber);
                }

                // Try to extract density from USDA portion data
                // USDA returns "foodMeasures" with gramWeight + volume descriptions
                List<Map> measures = (List<Map>) food.get("foodMeasures");
                if (measures == null) {
                    // Some responses use "foodPortions" instead
                    measures = (List<Map>) food.get("foodPortions");
                }

                if (measures != null && !measures.isEmpty()) {
                    for (Map measure : measures) {
                        Number gramWeight = (Number) measure.get("gramWeight");
                        String desc = (String) measure.get("disseminationText");
                        if (desc == null)
                            desc = (String) measure.get("measureUnitName");

                        if (gramWeight != null && desc != null && gramWeight.floatValue() > 0) {
                            float volumeCm3 = estimateVolumeCm3FromDescription(desc);
                            if (volumeCm3 > 0) {
                                float density = gramWeight.floatValue() / volumeCm3;
                                // Sanity check: food density is typically 0.1-1.4 g/cm³
                                if (density >= 0.1f && density <= 1.4f) {
                                    info.setDensityGPerCm3(density);
                                    info.setDensitySource("USDA");
                                    info.setServingVolumeCm3(volumeCm3);
                                    break; // use the first valid one
                                }
                            }
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

    /**
     * Converts common USDA serving descriptions to volume in cm³.
     * "1 cup" = 236.6 cm³, "1 tbsp" = 14.8 cm³, etc.
     */
    private float estimateVolumeCm3FromDescription(String desc) {
        if (desc == null)
            return 0f;
        String lower = desc.toLowerCase().trim();

        // Standard volume conversions to cm³
        if (lower.contains("cup"))
            return 236.6f;
        if (lower.contains("tablespoon") || lower.contains("tbsp"))
            return 14.8f;
        if (lower.contains("teaspoon") || lower.contains("tsp"))
            return 4.9f;
        if (lower.contains("fluid oz") || lower.contains("fl oz"))
            return 29.6f;
        if (lower.contains("liter") || lower.contains("litre"))
            return 1000f;
        if (lower.contains("ml")) {
            // Try to extract the number: "250 ml" → 250
            try {
                String num = lower.replaceAll("[^0-9.]", "");
                if (!num.isEmpty())
                    return Float.parseFloat(num);
            } catch (NumberFormatException e) {
                /* ignore */ }
            return 0f;
        }

        return 0f;
    }
}