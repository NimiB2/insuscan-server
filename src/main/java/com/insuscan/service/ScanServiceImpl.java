package com.insuscan.service;

import com.insuscan.boundary.*;
import com.insuscan.converter.MealConverter;
import com.insuscan.crud.MealRepository;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.MealEntity;
import com.insuscan.data.UserEntity;
import com.insuscan.enums.MealStatus;
import com.insuscan.exception.InsuScanInvalidInputException;
import com.insuscan.exception.InsuScanNotFoundException;
import com.insuscan.util.InputValidators;
import com.insuscan.util.MealIdGenerator;
import com.insuscan.util.PortionEstimator;
import com.insuscan.util.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ScanServiceImpl implements ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanServiceImpl.class);

    private final ImageAnalysisService imageAnalysisService;
    private final NutritionDataService nutritionDataService;
    private final MealRepository mealRepository;
    private final UserRepository userRepository;
    private final MealConverter mealConverter;
    private final MealIdGenerator mealIdGenerator;
    private final PortionEstimator portionEstimator;

    @Value("${spring.application.name}")
    private String systemId;

    private static final float DEFAULT_PORTION_WEIGHT = 150f;

    public ScanServiceImpl(
            ImageAnalysisService imageAnalysisService,
            NutritionDataService nutritionDataService,
            MealRepository mealRepository,
            UserRepository userRepository,
            MealConverter mealConverter,
            MealIdGenerator mealIdGenerator,
            PortionEstimator portionEstimator) {
        this.imageAnalysisService = imageAnalysisService;
        this.nutritionDataService = nutritionDataService;
        this.mealRepository = mealRepository;
        this.userRepository = userRepository;
        this.mealConverter = mealConverter;
        this.mealIdGenerator = mealIdGenerator;
        this.portionEstimator = portionEstimator;
    }

    @Override
    public MealBoundary scanMeal(ScanRequestBoundary request) {
        return scanMealWithPortion(request, DEFAULT_PORTION_WEIGHT, null);
    }

    @Override
    public MealBoundary scanMealWithPortion(ScanRequestBoundary request,
                                             Float estimatedWeightGrams,
                                             Float portionConfidence) {
        validateScanRequest(request);

        UserIdBoundary userId = request.getUserId();
        String userDocId = userId.getSystemId() + "_" + userId.getEmail();

        // Verify user exists
        UserEntity user = userRepository.findById(userDocId)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "User not found: " + userId.getEmail()));

        log.info("Starting scan for user: {}", userId.getEmail());

        // Step 1: Analyze image
        FoodRecognitionResult visionResult = analyzeImage(request);
        if (!visionResult.isSuccess()) {
            log.warn("Vision analysis failed: {}", visionResult.getErrorMessage());
            return createFailedMeal(userDocId, request.getImageUrl());
        }

        log.info("Vision detected {} food items", visionResult.getDetectedFoods().size());

        // Step 2: Calculate portion sizes using smart distribution
        List<PortionEstimator.FoodItem> portionItems = new ArrayList<>();
        float totalVisionPortions = 0f;
        
        for (FoodRecognitionResult.RecognizedFoodItem detected : visionResult.getDetectedFoods()) {
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
        Float totalWeightToDistribute = estimatedWeightGrams;
        if (totalWeightToDistribute == null) {
            // If vision provided total, use it; otherwise estimate from food types
            if (totalVisionPortions > 0) {
                totalWeightToDistribute = totalVisionPortions;
                log.info("Using vision-estimated total weight: {}g", totalWeightToDistribute);
            } else {
                // Estimate total from food types
                totalWeightToDistribute = estimateTotalWeightFromFoodTypes(portionItems);
                log.info("Estimated total weight from food types: {}g", totalWeightToDistribute);
            }
        }

        // Use smart portion distribution
        Map<String, Float> distributedPortions = portionEstimator.distributePortions(
                portionItems, totalWeightToDistribute);

        // Step 3: Get nutrition data and calculate carbs
        List<MealEntity.FoodItem> foodItems = new ArrayList<>();
        float totalCarbs = 0f;

        for (FoodRecognitionResult.RecognizedFoodItem detected : visionResult.getDetectedFoods()) {
            NutritionInfo nutrition = nutritionDataService.getNutritionInfo(detected.getName());
            
            MealEntity.FoodItem item = new MealEntity.FoodItem();
            item.setName(detected.getName());
            item.setConfidence(NumberUtils.roundTo2Decimals(detected.getConfidence()));
            
            // Get portion from smart distribution, or estimate individually
            Float itemWeight = distributedPortions.get(detected.getName());
            if (itemWeight == null) {
                // Fallback: estimate individually
                itemWeight = portionEstimator.estimatePortion(
                        detected.getName(), 
                        detected.getConfidence(), 
                        detected.getEstimatedPortionGrams());
            }
            
            item.setQuantity(NumberUtils.roundTo2Decimals(itemWeight));
            log.debug("Food: {} - Portion: {}g (confidence: {:.2f}, vision: {})", 
                    detected.getName(), itemWeight, detected.getConfidence(),
                    detected.getEstimatedPortionGrams() != null ? detected.getEstimatedPortionGrams() + "g" : "none");

            if (nutrition.isFound()) {
                float itemCarbs = nutrition.calculateCarbs(itemWeight);
                item.setCarbs(NumberUtils.roundTo2Decimals(itemCarbs));
                item.setUsdaFdcId(nutrition.getFdcId());
                
                totalCarbs += itemCarbs;
                log.debug("  -> Carbs: {}g ({}g per 100g)", itemCarbs, nutrition.getCarbsPer100g());
            } else {
                item.setCarbs(0f);
                log.warn("No nutrition data found for: {}", detected.getName());
            }

            foodItems.add(item);
        }

        log.info("Total carbs: {}g", totalCarbs);

        // Step 3: Create meal using existing MealEntity structure
        MealEntity meal = new MealEntity();
        meal.setId(mealIdGenerator.generateMealId(systemId));
        meal.setUserId(userDocId);
        meal.setImageUrl(request.getImageUrl());
        meal.setFoodItems(foodItems);
        meal.setTotalCarbs(NumberUtils.roundTo2Decimals(totalCarbs));
        meal.setStatus(MealStatus.PENDING);
        
        // Set portion analysis if provided
        if (estimatedWeightGrams != null) {
            meal.setEstimatedWeight(estimatedWeightGrams);
        }
        if (portionConfidence != null) {
            meal.setAnalysisConfidence(portionConfidence);
        }

        // scannedAt is set in MealEntity constructor

        MealEntity saved = mealRepository.save(meal);
        log.info("Meal saved: {}", meal.getId());

        return mealConverter.toBoundary(saved);
    }

    /**
     * Estimate total weight from food types when no user/vision estimate available
     */
    private float estimateTotalWeightFromFoodTypes(List<PortionEstimator.FoodItem> items) {
        float total = 0f;
        for (PortionEstimator.FoodItem item : items) {
            float portion = portionEstimator.estimatePortion(item.name, item.confidence, item.visionEstimate);
            total += portion;
        }
        return total;
    }

    private void validateScanRequest(ScanRequestBoundary request) {
        if (request == null) {
            throw new InsuScanInvalidInputException("Scan request cannot be null");
        }
        if (request.getUserId() == null) {
            throw new InsuScanInvalidInputException("User ID is required");
        }
        InputValidators.validateSystemId(request.getUserId().getSystemId());
        InputValidators.validateEmail(request.getUserId().getEmail());

        if (request.getImageBase64() == null && request.getImageUrl() == null) {
            throw new InsuScanInvalidInputException(
                "Either imageBase64 or imageUrl must be provided");
        }
    }

    private FoodRecognitionResult analyzeImage(ScanRequestBoundary request) {
        if (request.getImageBase64() != null) {
            return imageAnalysisService.analyzeImage(request.getImageBase64());
        } else {
            return imageAnalysisService.analyzeImageFromUrl(request.getImageUrl());
        }
    }

    // Create meal for failed analysis - still saves the attempt
    private MealBoundary createFailedMeal(String userDocId, String imageUrl) {
        MealEntity meal = new MealEntity();
        String mealUuid = UUID.randomUUID().toString();
        meal.setId(systemId + "_" + mealUuid);
        meal.setUserId(userDocId);
        meal.setImageUrl(imageUrl);
        meal.setFoodItems(new ArrayList<>());
        meal.setTotalCarbs(0f);
        meal.setStatus(MealStatus.PENDING);
        // User can manually add food items later

        MealEntity saved = mealRepository.save(meal);
        return mealConverter.toBoundary(saved);
    }
}