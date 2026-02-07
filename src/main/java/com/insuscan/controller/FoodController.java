package com.insuscan.controller;

import com.insuscan.boundary.AiSearchRequest;
import com.insuscan.boundary.AiSearchResponse;
import com.insuscan.boundary.NutritionInfo;
import com.insuscan.boundary.ScoredFoodResult;
import com.insuscan.boundary.SearchOptimization;
import com.insuscan.service.FoodSearchAiService;
import com.insuscan.service.NutritionDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/food")
@CrossOrigin(origins = "*")
@Tag(name = "Food Database", description = "Search USDA food database")
public class FoodController {

    private static final Logger log = LoggerFactory.getLogger(FoodController.class);
    private final NutritionDataService nutritionDataService;
    private final FoodSearchAiService foodSearchAiService;

    public FoodController(NutritionDataService nutritionDataService, 
                         FoodSearchAiService foodSearchAiService) {
        this.nutritionDataService = nutritionDataService;
        this.foodSearchAiService = foodSearchAiService;
    }

    // Search for food items - reuses existing searchFoods()
    @Operation(summary = "Search food database")
    @GetMapping(path = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<NutritionInfo> searchFood(
            @Parameter(description = "Search query", required = true)
            @RequestParam("query") String query,
            
            @Parameter(description = "Max results (default 10, max 25)")
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        
        log.info("Food search: query='{}', limit={}", query, limit);
        return nutritionDataService.searchFoods(query, Math.min(limit, 25));
    }

    // Get single food by name - reus existing getNutritionInfo()
    @Operation(summary = "Get nutrition info for a food")
    @GetMapping(path = "/nutrition", produces = MediaType.APPLICATION_JSON_VALUE)
    public NutritionInfo getNutrition(
            @Parameter(description = "Food name", required = true)
            @RequestParam("name") String foodName) {
        
        return nutritionDataService.getNutritionInfo(foodName);
    }
    
    // AI-enhanced search with intelligent ranking
    @Operation(summary = "AI-enhanced food search with intelligent ranking", 
               description = "Uses AI to understand user intent, translate queries, and rank results by relevance")
    @PostMapping(path = "/ai-search", produces = MediaType.APPLICATION_JSON_VALUE)
    public AiSearchResponse aiSearchFood(@RequestBody AiSearchRequest request) {
        
        log.info("[AI-SEARCH] Query: '{}', Language: {}, Limit: {}", 
                request.getQuery(), request.getUserLanguage(), request.getLimit());
        
        // Validate request
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            log.warn("[AI-SEARCH] Empty query provided");
            return new AiSearchResponse(List.of(), null, false);
        }
        
        // Check if AI is available
        if (!foodSearchAiService.isServiceAvailable()) {
            log.info("[AI-SEARCH] AI not available, falling back to standard search");
            List<NutritionInfo> standardResults = nutritionDataService.searchFoods(
                    request.getQuery(), request.getLimit());
            
            // Convert to ScoredFoodResult with default scores
            List<ScoredFoodResult> results = standardResults.stream()
                    .map(r -> new ScoredFoodResult(r, 50, "Standard search (AI unavailable)", null))
                    .collect(Collectors.toList());
            
            return new AiSearchResponse(results, null, false);
        }
        
        try {
            // Step 1: Optimize query using AI
            SearchOptimization optimization = foodSearchAiService.optimizeQuery(
                    request.getQuery(), 
                    request.getUserLanguage());
            
            log.info("[AI-SEARCH] Optimized to: {} with {} variations", 
                    optimization.getTranslatedQuery(), 
                    optimization.getSearchVariations().length);
            
            // Step 2: Search USDA with multiple variations
            List<NutritionInfo> rawResults = searchWithVariations(
                    optimization.getSearchVariations(), 
                    request.getLimit() * 2); // Fetch more for better filtering
            
            log.info("[AI-SEARCH] Found {} raw USDA results", rawResults.size());
            
            // Step 3: Rank and score results using AI
            List<ScoredFoodResult> scoredResults = foodSearchAiService.rankAndScoreResults(
                    request.getQuery(), 
                    rawResults);
            
            // Step 4: Limit to requested number
            List<ScoredFoodResult> finalResults = scoredResults.stream()
                    .limit(request.getLimit())
                    .collect(Collectors.toList());
            
            log.info("[AI-SEARCH] Returning {} scored results", finalResults.size());
            
            return new AiSearchResponse(finalResults, optimization, true);
            
        } catch (Exception e) {
            log.error("[AI-SEARCH] Failed: {}", e.getMessage(), e);
            
            // Fallback to standard search
            List<NutritionInfo> fallbackResults = nutritionDataService.searchFoods(
                    request.getQuery(), request.getLimit());
            
            List<ScoredFoodResult> results = fallbackResults.stream()
                    .map(r -> new ScoredFoodResult(r, 50, "Fallback search (AI error)", null))
                    .collect(Collectors.toList());
            
            return new AiSearchResponse(results, null, false);
        }
    }
    
    /**
     * Search USDA with multiple query variations and combine unique results.
     */
    private List<NutritionInfo> searchWithVariations(String[] variations, int totalLimit) {
        List<NutritionInfo> allResults = new java.util.ArrayList<>();
        java.util.Set<String> seenFdcIds = new java.util.HashSet<>();
        
        int limitPerVariation = Math.max(10, totalLimit / variations.length);
        
        for (String variation : variations) {
            List<NutritionInfo> results = nutritionDataService.searchFoods(
                    variation, limitPerVariation);
            
            // Add unique results only
            for (NutritionInfo result : results) {
                if (result.getFdcId() != null && seenFdcIds.add(result.getFdcId())) {
                    allResults.add(result);
                }
            }
            
            // Stop if we have enough results
            if (allResults.size() >= totalLimit) {
                break;
            }
        }
        
        return allResults;
    }
}
