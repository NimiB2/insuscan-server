package com.insuscan.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.service.MealService;

import java.util.List;

@RestController
@RequestMapping(path = "/meals")
@CrossOrigin(origins = "*")
public class MealController {

    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    // POST /meals/{systemId}/{email} - Create new meal from scan
    @PostMapping(
        path = "/{systemId}/{email}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary createMeal(
            @PathVariable String systemId,
            @PathVariable String email,
            @RequestParam(required = false) String imageUrl) {
        return mealService.createMeal(systemId, email, imageUrl);
    }

    // GET /meals/{systemId}/{mealId} - Get meal by ID
    @GetMapping(
        path = "/{systemId}/{mealId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary getMeal(
            @PathVariable String systemId,
            @PathVariable String mealId) {
        return mealService.getMealById(systemId, mealId)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "Meal not found: " + mealId));
    }

    // GET /meals/user/{systemId}/{email} - Get meals for user
    @GetMapping(
        path = "/user/{systemId}/{email}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MealBoundary> getMealsByUser(
            @PathVariable String systemId,
            @PathVariable String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return mealService.getMealsByUser(systemId, email, page, size);
    }

    // GET /meals/recent/{systemId}/{email} - Get recent meals for user
    @GetMapping(
        path = "/recent/{systemId}/{email}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MealBoundary> getRecentMeals(
            @PathVariable String systemId,
            @PathVariable String email,
            @RequestParam(defaultValue = "5") int count) {
        return mealService.getRecentMeals(systemId, email, count);
    }

    // PUT /meals/{systemId}/{mealId}/fooditems - Update food items
    @PutMapping(
        path = "/{systemId}/{mealId}/fooditems",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary updateFoodItems(
            @PathVariable String systemId,
            @PathVariable String mealId,
            @RequestBody List<FoodItemBoundary> foodItems) {
        return mealService.updateFoodItems(systemId, mealId, foodItems);
    }

    // PUT /meals/{systemId}/{mealId}/confirm - Confirm meal and calculate dose
    @PutMapping(
        path = "/{systemId}/{mealId}/confirm",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary confirmMeal(
            @PathVariable String systemId,
            @PathVariable String mealId,
            @RequestParam(required = false) Float actualDose) {
        return mealService.confirmMeal(systemId, mealId, actualDose);
    }

    // PUT /meals/{systemId}/{mealId}/complete - Mark meal as completed
    @PutMapping(
        path = "/{systemId}/{mealId}/complete",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary completeMeal(
            @PathVariable String systemId,
            @PathVariable String mealId) {
        return mealService.completeMeal(systemId, mealId);
    }

    // PUT /meals/{systemId}/{mealId}/portion - Update portion analysis
    @PutMapping(
        path = "/{systemId}/{mealId}/portion",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary updatePortionAnalysis(
            @PathVariable String systemId,
            @PathVariable String mealId,
            @RequestParam(required = false) Float estimatedWeight,
            @RequestParam(required = false) Float volumeCm3,
            @RequestParam(required = false) Float diameterCm,
            @RequestParam(required = false) Float depthCm,
            @RequestParam(required = false) Float confidence,
            @RequestParam(required = false) Boolean refDetected) {
        return mealService.updatePortionAnalysis(
            systemId, mealId, estimatedWeight, volumeCm3, 
            diameterCm, depthCm, confidence, refDetected);
    }

    // DELETE /meals/{systemId}/{mealId} - Delete meal
    @DeleteMapping(path = "/{systemId}/{mealId}")
    public void deleteMeal(
            @PathVariable String systemId,
            @PathVariable String mealId) {
        mealService.deleteMeal(systemId, mealId);
    }

    // GET /meals/count/{systemId}/{email} - Get meal count for user
    @GetMapping(path = "/count/{systemId}/{email}")
    public long getMealCount(
            @PathVariable String systemId,
            @PathVariable String email) {
        return mealService.getMealCountForUser(systemId, email);
    }
}
