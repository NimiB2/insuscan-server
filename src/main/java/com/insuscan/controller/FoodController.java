package com.insuscan.controller;

import com.insuscan.boundary.NutritionInfo;
import com.insuscan.service.NutritionDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/food")
@CrossOrigin(origins = "*")
@Tag(name = "Food Database", description = "Search USDA food database")
public class FoodController {

    private static final Logger log = LoggerFactory.getLogger(FoodController.class);
    private final NutritionDataService nutritionDataService;

    public FoodController(NutritionDataService nutritionDataService) {
        this.nutritionDataService = nutritionDataService;
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

    // Get single food by name - reuses existing getNutritionInfo()
    @Operation(summary = "Get nutrition info for a food")
    @GetMapping(path = "/nutrition", produces = MediaType.APPLICATION_JSON_VALUE)
    public NutritionInfo getNutrition(
            @Parameter(description = "Food name", required = true)
            @RequestParam("name") String foodName) {
        
        return nutritionDataService.getNutritionInfo(foodName);
    }
}
