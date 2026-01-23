package com.insuscan.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.insuscan.boundary.FoodItemBoundary;
import com.insuscan.boundary.MealBoundary;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.service.MealService;

import java.time.LocalDate;
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
        path = "/{systemId}/{email:.+}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary createMeal(
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email,
            @RequestParam(value = "imageUrl", required = false) String imageUrl) {
        return mealService.createMeal(systemId, email, imageUrl);
    }

    // GET /meals/{systemId}/{mealId} - Get meal by ID
    @GetMapping(
        path = "/{systemId}/{mealId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary getMeal(
            @PathVariable("systemId") String systemId,
            @PathVariable("mealId") String mealId) {
        return mealService.getMealById(systemId, mealId)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "Meal not found: " + mealId));
    }

    // GET /meals/user/{systemId}/{email} - Get meals for user
    @GetMapping(
        path = "/user/{systemId}/{email:.+}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MealBoundary> getMealsByUser(
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return mealService.getMealsByUser(systemId, email, page, size);
    }

    // GET /meals/recent/{systemId}/{email} - Get recent meals for user
    @GetMapping(
        path = "/recent/{systemId}/{email:.+}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MealBoundary> getRecentMeals(
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email,
            @RequestParam(value = "count", defaultValue = "5") int count) {
        return mealService.getRecentMeals(systemId, email, count);
    }

    // PUT /meals/{systemId}/{mealId}/fooditems - Update food items
    @PutMapping(
        path = "/{systemId}/{mealId}/fooditems",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary updateFoodItems(
            @PathVariable("systemId") String systemId,
            @PathVariable("mealId") String mealId,
            @RequestBody List<FoodItemBoundary> foodItems) {
        return mealService.updateFoodItems(systemId, mealId, foodItems);
    }

    // PUT /meals/{systemId}/{mealId}/confirm - Confirm meal and calculate dose
    @PutMapping(
        path = "/{systemId}/{mealId}/confirm",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary confirmMeal(
            @PathVariable("systemId") String systemId,
            @PathVariable("mealId") String mealId,
            @RequestParam(value = "actualDose", required = false) Float actualDose,
            @RequestParam(value = "currentGlucose", required = false) Integer currentGlucose,
            @RequestParam(value = "activityLevel", required = false) String activityLevel,
            @RequestParam(value = "sickMode", required = false) Boolean sickMode,
            @RequestParam(value = "stressMode", required = false) Boolean stressMode) {
        return mealService.confirmMeal(systemId, mealId, actualDose, 
                                       currentGlucose, activityLevel, 
                                       sickMode, stressMode);
    }

    // PUT /meals/{systemId}/{mealId}/complete - Mark meal as completed
    @PutMapping(
        path = "/{systemId}/{mealId}/complete",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary completeMeal(
            @PathVariable("systemId") String systemId,
            @PathVariable("mealId") String mealId) {
        return mealService.completeMeal(systemId, mealId);
    }
       
    @GetMapping(path = "/user/{systemId}/{email:.+}/by-date")
    public List<MealBoundary> getMealsByDateRange(
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email,
            @RequestParam("from") @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return mealService.getMealsByDateRange(systemId, email, from, to, page, size);
    }

    // PUT /meals/{systemId}/{mealId}/portion - Update portion analysis
    @PutMapping(
        path = "/{systemId}/{mealId}/portion",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary updatePortionAnalysis(
            @PathVariable("systemId") String systemId,
            @PathVariable("mealId") String mealId,
            @RequestParam(value = "estimatedWeight", required = false) Float estimatedWeight,
            @RequestParam(value = "volumeCm3", required = false) Float volumeCm3,
            @RequestParam(value = "diameterCm", required = false) Float diameterCm,
            @RequestParam(value = "depthCm", required = false) Float depthCm,
            @RequestParam(value = "confidence", required = false) Float confidence,
            @RequestParam(value = "refDetected", required = false) Boolean refDetected) {
        return mealService.updatePortionAnalysis(
            systemId, mealId, estimatedWeight, volumeCm3, 
            diameterCm, depthCm, confidence, refDetected);
    }

    // DELETE /meals/{systemId}/{mealId} - Delete meal
    @DeleteMapping(path = "/{systemId}/{mealId}")
    public void deleteMeal(
            @PathVariable("systemId") String systemId,
            @PathVariable("mealId") String mealId) {
        mealService.deleteMeal(systemId, mealId);
    }

    // GET /meals/count/{systemId}/{email} - Get meal count for user
    @GetMapping(path = "/count/{systemId}/{email:.+}")
    public long getMealCount(
            @PathVariable("systemId") String systemId,
            @PathVariable("email") String email) {
        return mealService.getMealCountForUser(systemId, email);
    }
}
