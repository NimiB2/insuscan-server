package com.insuscan.controller;

import com.insuscan.boundary.FoodRecognitionResult;
import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.NutritionInfo;
import com.insuscan.boundary.ScanRequestBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.converter.MealConverter;
import com.insuscan.crud.MealRepository;
import com.insuscan.data.MealEntity;
import com.insuscan.enums.MealStatus;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.service.ImageAnalysisService;
import com.insuscan.service.MealService;
import com.insuscan.service.NutritionDataService;
import com.insuscan.service.ScanService;
import com.insuscan.util.MealIdGenerator;
import com.insuscan.util.NumberUtils;
import com.insuscan.util.PortionEstimator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/vision")
@CrossOrigin(origins = "*")
@Tag(name = "Vision Analysis", description = "Image analysis and meal scanning endpoints")
public class VisionController {

    private static final Logger log = LoggerFactory.getLogger(VisionController.class);
    
    private final ScanService scanService;
    private final MealService mealService;
    private final ImageAnalysisService imageAnalysisService;
    private final MealRepository mealRepository;
    private final MealConverter mealConverter;
    private final MealIdGenerator mealIdGenerator;
    private final PortionEstimator portionEstimator;
    private final NutritionDataService nutritionDataService;

    @Value("${spring.application.name}")
    private String systemId;

    public VisionController(ScanService scanService, 
                           MealService mealService,
                           ImageAnalysisService imageAnalysisService,
                           MealRepository mealRepository,
                           MealConverter mealConverter,
                           MealIdGenerator mealIdGenerator,
                           PortionEstimator portionEstimator,
                           NutritionDataService nutritionDataService) {
        this.scanService = scanService;
        this.mealService = mealService;
        this.imageAnalysisService = imageAnalysisService;
        this.mealRepository = mealRepository;
        this.mealConverter = mealConverter;
        this.mealIdGenerator = mealIdGenerator;
        this.portionEstimator = portionEstimator;
        this.nutritionDataService = nutritionDataService;
    }

    /**
     * Prototype endpoint:
     * - Input: image file (multipart/form-data)
     * - Output: Complete MealBoundary with vision analysis, nutrition, and saves to Firebase
     * - Tries full ScanService workflow first, falls back to simple vision analysis if user doesn't exist
     */
    @Operation(summary = "Analyze meal image", description = "Uploads an image, analyzes food items, calculates nutrition and insulin dose, then saves to Firebase")
    @PostMapping(
        path = "/analyze",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> analyzeImage(
            @Parameter(description = "Image file to analyze (JPEG, PNG, etc.)", required = true)
            @RequestPart("file") MultipartFile file,
            
            @Parameter(description = "User ID (legacy parameter, use email instead)", example = "prototype_user")
            @RequestParam(value = "userId", required = false, defaultValue = "prototype_user") String userId,
            
            @Parameter(description = "User email for personalized calculations (optional, defaults to test@example.com)", example = "test@example.com")
            @RequestParam(value = "email", required = false, defaultValue = "test@example.com") String email,
            
            @Parameter(description = "User's estimated total meal weight in grams (optional, helps with portion estimation)", example = "300")
            @RequestParam(value = "estimatedWeightGrams", required = false) Float estimatedWeightGrams,
            
            @Parameter(description = "User's confidence in weight estimate (0.0 to 1.0, optional)", example = "0.8")
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
            log.info("Meal scanned successfully for user: {}", email);
            return ResponseEntity.ok(meal);
        } catch (InsuScanNotFoundException e) {
            // User doesn't exist - fall back to simple vision analysis and save basic meal
            log.warn("User not found: {}, saving meal without user profile", email);
            FoodRecognitionResult visionResult = imageAnalysisService.analyzeImage(base64);
            
            if (!visionResult.isSuccess()) {
                return ResponseEntity.ok(visionResult);
            }
            
            // Save basic meal to database (linked to email, even if user doesn't exist yet)
            MealEntity meal = saveBasicMeal(visionResult, email, file.getOriginalFilename());
            log.info("Saved basic meal for email: {} (mealId: {})", email, meal.getId());
            
            // Convert to MealBoundary for response
            MealBoundary mealBoundary = mealConverter.toBoundary(meal);
            return ResponseEntity.ok(mealBoundary);
        } catch (Exception e) {
            log.error("Error during meal scan: ", e);
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
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit
    ) {
        // Use email if provided, otherwise fall back to userId (for backward compatibility)
        String userEmail = email;
        
        if (userEmail == null || userEmail.isEmpty()) {
            // Legacy support: if userId is provided and looks like an email, use it
            if (userId != null && userId.contains("@")) {
                userEmail = userId;
            } else if (userId != null && !userId.isEmpty()) {
                // Legacy: userId without @ - construct email for backward compatibility
                userEmail = userId + "@example.com";
            } else {
                // Default fallback
                userEmail = "test@example.com";
            }
        }
        
        return mealService.getRecentMeals(systemId, userEmail, limit);
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
    
    /**
     * Serve the test vision HTML page
     */
    @GetMapping(path = "/test", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> getTestPage() {
        Resource resource = new ClassPathResource("static/test-vision.html");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(resource);
    }
    
    private MealEntity saveBasicMeal(FoodRecognitionResult result, String email, String imageName) {
        MealEntity meal = new MealEntity();
        meal.setId(mealIdGenerator.generateMealId(systemId));
        meal.setUserId(systemId + "_" + email);
        meal.setImageUrl("uploaded://" + imageName);
        
        // Use smart portion estimation and nutrition lookup (same as ScanService)
        List<PortionEstimator.FoodItem> portionItems = new ArrayList<>();
        float totalVisionPortions = 0f;
        
        for (FoodRecognitionResult.RecognizedFoodItem detected : result.getDetectedFoods()) {
            portionItems.add(new PortionEstimator.FoodItem(
                    detected.getName(),
                    detected.getConfidence(),
                    detected.getEstimatedPortionGrams()
            ));
            
            if (detected.getEstimatedPortionGrams() != null && detected.getEstimatedPortionGrams() > 0) {
                totalVisionPortions += detected.getEstimatedPortionGrams();
            }
        }
        
        // Determine total weight to distribute
        Float totalWeightToDistribute = null;
        if (totalVisionPortions > 0) {
            totalWeightToDistribute = totalVisionPortions;
        } else {
            // Estimate from food types
            for (PortionEstimator.FoodItem item : portionItems) {
                float portion = portionEstimator.estimatePortion(item.name, item.confidence, item.visionEstimate);
                if (totalWeightToDistribute == null) totalWeightToDistribute = 0f;
                totalWeightToDistribute += portion;
            }
        }
        
        // Use smart portion distribution
        Map<String, Float> distributedPortions = portionEstimator.distributePortions(
                portionItems, totalWeightToDistribute != null ? totalWeightToDistribute : 200f);
        
        // Convert vision results to food items with nutrition data
        List<MealEntity.FoodItem> foodItems = new ArrayList<>();
        float totalCarbs = 0f;
        
        for (FoodRecognitionResult.RecognizedFoodItem detected : result.getDetectedFoods()) {
            // Get nutrition data
            NutritionInfo nutrition = nutritionDataService.getNutritionInfo(detected.getName());
            
            MealEntity.FoodItem item = new MealEntity.FoodItem();
            item.setName(detected.getName());
            item.setConfidence(NumberUtils.roundTo2Decimals(detected.getConfidence()));
            
            // Get portion from smart distribution
            Float itemWeight = distributedPortions.get(detected.getName());
            if (itemWeight == null) {
                // Fallback: estimate individually
                itemWeight = portionEstimator.estimatePortion(
                        detected.getName(), 
                        detected.getConfidence(), 
                        detected.getEstimatedPortionGrams());
            }
            
            item.setQuantity(NumberUtils.roundTo2Decimals(itemWeight));
            
            // Calculate carbs if nutrition data found
            if (nutrition.isFound()) {
                float itemCarbs = nutrition.calculateCarbs(itemWeight);
                item.setCarbs(NumberUtils.roundTo2Decimals(itemCarbs));
                item.setUsdaFdcId(nutrition.getFdcId());
                totalCarbs += itemCarbs;
            } else {
                item.setCarbs(0f);
            }
            
            foodItems.add(item);
        }
        
        meal.setFoodItems(foodItems);
        meal.setTotalCarbs(NumberUtils.roundTo2Decimals(totalCarbs));
        meal.setStatus(MealStatus.PENDING);
        
        return mealRepository.save(meal);
    }
    
}

