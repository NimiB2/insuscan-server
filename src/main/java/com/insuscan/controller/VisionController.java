package com.insuscan.controller;

import com.insuscan.boundary.FoodRecognitionResult;
import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.ScanRequestBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.converter.MealConverter;
import com.insuscan.crud.MealRepository;
import com.insuscan.data.MealEntity;
import com.insuscan.enums.MealStatus;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.service.ImageAnalysisService;
import com.insuscan.service.MealService;
import com.insuscan.service.ScanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/vision")
@CrossOrigin(origins = "*")
public class VisionController {

    private final ScanService scanService;
    private final MealService mealService;
    private final ImageAnalysisService imageAnalysisService;
    private final MealRepository mealRepository;
    private final MealConverter mealConverter;

    @Value("${spring.application.name}")
    private String systemId;

    public VisionController(ScanService scanService, 
                           MealService mealService,
                           ImageAnalysisService imageAnalysisService,
                           MealRepository mealRepository,
                           MealConverter mealConverter) {
        this.scanService = scanService;
        this.mealService = mealService;
        this.imageAnalysisService = imageAnalysisService;
        this.mealRepository = mealRepository;
        this.mealConverter = mealConverter;
    }

    /**
     * Prototype endpoint:
     * - Input: image file (multipart/form-data)
     * - Output: Complete MealBoundary with vision analysis, nutrition, and saves to Firebase
     * - Tries full ScanService workflow first, falls back to simple vision analysis if user doesn't exist
     */
    @PostMapping(
        path = "/analyze",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> analyzeImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "userId", required = false, defaultValue = "prototype_user") String userId,
            @RequestParam(value = "email", required = false, defaultValue = "test@example.com") String email,
            @RequestParam(value = "estimatedWeightGrams", required = false) Float estimatedWeightGrams,
            @RequestParam(value = "portionConfidence", required = false) Float portionConfidence
    ) throws IOException {
        
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No image file provided");
        }

        byte[] bytes = file.getBytes();
        String base64 = Base64.getEncoder().encodeToString(bytes);

        // Build scan request
        ScanRequestBoundary request = new ScanRequestBoundary();
        request.setImageBase64(base64);
        
        UserIdBoundary userIdBoundary = new UserIdBoundary();
        userIdBoundary.setSystemId(systemId);
        userIdBoundary.setEmail(email);
        request.setUserId(userIdBoundary);

        // Try full ScanService workflow first (requires user to exist)
        try {
            MealBoundary meal = scanService.scanMealWithPortion(request, estimatedWeightGrams, portionConfidence);
            return ResponseEntity.ok(meal);
        } catch (InsuScanNotFoundException e) {
            // User doesn't exist - fall back to simple vision analysis and save basic meal
            FoodRecognitionResult visionResult = imageAnalysisService.analyzeImage(base64);
            
            if (!visionResult.isSuccess()) {
                return ResponseEntity.ok(visionResult);
            }
            
            // Save basic meal to database
            MealEntity meal = saveBasicMeal(visionResult, email, file.getOriginalFilename());
            
            // Convert to MealBoundary for response
            MealBoundary mealBoundary = mealConverter.toBoundary(meal);
            return ResponseEntity.ok(mealBoundary);
        } catch (Exception e) {
            // If anything else fails, just return vision result
            FoodRecognitionResult visionResult = imageAnalysisService.analyzeImage(base64);
            return ResponseEntity.ok(visionResult);
        }
    }

    /**
     * Get all saved vision analyses for a user
     */
    @GetMapping(path = "/saved", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MealBoundary> getSavedAnalyses(
            @RequestParam(value = "userId", required = false, defaultValue = "prototype_user") String userId,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit
    ) {
        return mealService.getRecentMeals(systemId, userId, limit);
    }

    /**
     * Get a specific saved analysis by meal ID
     * Accepts either full ID (systemId_mealUuid) or just UUID
     */
    @GetMapping(path = "/saved/{mealId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary getSavedAnalysis(@PathVariable String mealId) {
        // Extract UUID if full ID format (systemId_uuid)
        String uuid = mealId;
        if (mealId.contains("_")) {
            uuid = mealId.substring(mealId.lastIndexOf("_") + 1);
        }
        
        return mealService.getMealById(systemId, uuid)
                .orElseThrow(() -> new RuntimeException("Meal not found: " + mealId));
    }
    
    private MealEntity saveBasicMeal(FoodRecognitionResult result, String email, String imageName) {
        MealEntity meal = new MealEntity();
        String mealUuid = UUID.randomUUID().toString();
        meal.setId(systemId + "_" + mealUuid);
        meal.setUserId(systemId + "_" + email);
        meal.setImageUrl("uploaded://" + imageName);
        
        // Convert vision results to food items
        List<MealEntity.FoodItem> foodItems = new ArrayList<>();
        for (FoodRecognitionResult.RecognizedFoodItem detected : result.getDetectedFoods()) {
            MealEntity.FoodItem item = new MealEntity.FoodItem();
            item.setName(detected.getName());
            item.setConfidence(detected.getConfidence());
            item.setQuantity(100f); // Default quantity
            item.setCarbs(0f); // Will be calculated later
            foodItems.add(item);
        }
        
        meal.setFoodItems(foodItems);
        meal.setTotalCarbs(0f);
        meal.setStatus(MealStatus.PENDING);
        
        return mealRepository.save(meal);
    }
    
}

