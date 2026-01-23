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
import com.insuscan.util.ApiLogger;
import com.insuscan.util.InputValidators;
import com.insuscan.util.MealIdGenerator;
import com.insuscan.util.PortionEstimator;
import com.insuscan.util.NumberUtils;
import com.insuscan.calculation.InsulinCalculator;
import com.insuscan.calculation.CalculationParams;
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
    private final SemanticMatchingService semanticMatchingService; // <-- NEW JUDGE
    private final MealRepository mealRepository;
    private final UserRepository userRepository;
    private final MealConverter mealConverter;
    private final MealIdGenerator mealIdGenerator;
    private final PortionEstimator portionEstimator;
    private final ApiLogger apiLogger;

    @Value("${spring.application.name}")
    private String systemId;

    private static final float DEFAULT_PORTION_WEIGHT = 150f;

    public ScanServiceImpl(
            ImageAnalysisService imageAnalysisService,
            NutritionDataService nutritionDataService,
            SemanticMatchingService semanticMatchingService, // <-- Inject Judge
            MealRepository mealRepository,
            UserRepository userRepository,
            MealConverter mealConverter,
            MealIdGenerator mealIdGenerator,
            PortionEstimator portionEstimator,
            ApiLogger apiLogger) {
        this.imageAnalysisService = imageAnalysisService;
        this.nutritionDataService = nutritionDataService;
        this.semanticMatchingService = semanticMatchingService;
        this.mealRepository = mealRepository;
        this.userRepository = userRepository;
        this.mealConverter = mealConverter;
        this.mealIdGenerator = mealIdGenerator;
        this.portionEstimator = portionEstimator;
        this.apiLogger = apiLogger;
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

        // Start timing and logging
        long scanStartTime = System.currentTimeMillis();
        apiLogger.scanStart(
            userId.getEmail(),
            request.getImageBase64() != null || request.getImageUrl() != null,
            estimatedWeightGrams
        );

        // Verify user exists
        UserEntity user = userRepository.findById(userDocId)
            .orElseThrow(() -> new InsuScanNotFoundException(
                "User not found: " + userId.getEmail()));

        log.info("Starting MEDICAL-GRADE scan for user: {}", userId.getEmail());

        // Step 1: Analyze image (Medical Vision)
        apiLogger.scanStep(1, "ANALYZING IMAGE (MEDICAL VISION)");
        FoodRecognitionResult visionResult = analyzeImage(request);
        
        if (!visionResult.isSuccess()) {
            apiLogger.scanFailed("VISION", visionResult.getErrorMessage());
            return createFailedMeal(userDocId, request.getImageUrl());
        }

        if (visionResult.getDetectedFoods().isEmpty()) {
            apiLogger.scanFailed("VISION", "0 foods detected");
            return createFailedMeal(userDocId, request.getImageUrl());
        }

        // Step 2: Calculate portion sizes (Logic remains same)
        apiLogger.scanStep(2, "CALCULATING PORTION SIZES");
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

        Float totalWeightToDistribute = estimatedWeightGrams;
        if (totalWeightToDistribute == null) {
            totalWeightToDistribute = (totalVisionPortions > 0) 
                ? totalVisionPortions 
                : estimateTotalWeightFromFoodTypes(portionItems);
        }

        Map<String, Float> distributedPortions = portionEstimator.distributePortions(
                portionItems, totalWeightToDistribute);

        // Step 3: Get nutrition data (INTEGRATING THE JUDGE)
        apiLogger.scanStep(3, "SEMANTIC NUTRITION MATCHING");
        List<MealEntity.FoodItem> foodItems = new ArrayList<>();
        float totalCarbs = 0f;

        for (FoodRecognitionResult.RecognizedFoodItem detected : visionResult.getDetectedFoods()) {
            
            // --- NEW LOGIC START ---
            NutritionInfo finalNutrition = null;
            
            // A. Fetch Candidates (Step 2 - Retrieval)
            List<NutritionInfo> candidates = nutritionDataService.searchCandidates(detected.getBaseIngredient());
            
            if (!candidates.isEmpty()) {
                // B. The Judge Decides (Step 3 - Semantic Matching)
                String bestFdcId = semanticMatchingService.findBestMatch(detected, candidates);
                
                // C. Get Full Data for Winner
                if (bestFdcId != null) {
                    finalNutrition = nutritionDataService.getNutritionInfo(bestFdcId); // Fetch by ID specifically
                } else {
                    finalNutrition = candidates.get(0); // Fallback to first candidate
                }
            } else {
                // Fallback: If search fails, try old direct lookup or fallback map
                finalNutrition = nutritionDataService.getNutritionInfo(detected.getName());
            }
            // --- NEW LOGIC END ---

            MealEntity.FoodItem item = new MealEntity.FoodItem();
            item.setName(detected.getName());
            item.setConfidence(NumberUtils.roundTo2Decimals(detected.getConfidence()));
            
            // Add safety flags to the DB entity for future reference
            if (detected.getRiskFlags() != null && !detected.getRiskFlags().isEmpty()) {
                item.setNote("Risks: " + String.join(", ", detected.getRiskFlags())); 
            }
            
            Float itemWeight = distributedPortions.get(detected.getName());
            if (itemWeight == null) {
                itemWeight = portionEstimator.estimatePortion(
                        detected.getName(), 
                        detected.getConfidence(), 
                        detected.getEstimatedPortionGrams());
            }
            item.setQuantity(NumberUtils.roundTo2Decimals(itemWeight));

            if (finalNutrition != null && finalNutrition.isFound()) {
                float itemCarbs = finalNutrition.calculateCarbs(itemWeight);
                item.setCarbs(NumberUtils.roundTo2Decimals(itemCarbs));
                item.setUsdaFdcId(finalNutrition.getFdcId());
                
                String source = "SEMANTIC_JUDGE";
                if (finalNutrition.getFdcId().startsWith("fallback-")) source = "FALLBACK";
                
                apiLogger.carbCalculation(
                    detected.getName(),
                    finalNutrition.getCarbsPer100g(),
                    itemWeight,
                    itemCarbs,
                    source
                );
                
                totalCarbs += itemCarbs;
            } else {
                item.setCarbs(0f);
                apiLogger.carbCalculationFailed(detected.getName(), "No nutrition data found");
            }

            foodItems.add(item);
        }

        log.info("Total carbs (Calculated): {}g", totalCarbs);

        // Step 4: Calculate insulin dose
        apiLogger.scanStep(4, "CALCULATING INSULIN DOSE");
        Float recommendedDose = null;
        boolean profileComplete = false;
        List<String> missingFields = new ArrayList<>();
        String insulinMessage = null;

        if (totalCarbs > 0) {
            String userEmail = userId != null ? userId.getEmail() : null;
            apiLogger.insulinCalcStart(totalCarbs, userEmail);

            // Build params from user profile - handles null checks
            CalculationParams params = new CalculationParams.Builder()
            	    .fromUser(user)
            	    .build();

            // Log profile status
            apiLogger.insulinCalcProfileStatus(params.isProfileComplete(), params.getMissingFields());

            if (params.isProfileComplete()) {
                // Profile complete - do full calculation
                apiLogger.insulinCalcParams(
                    params.getInsulinCarbRatio(),
                    params.getCorrectionFactor(),
                    params.getTargetGlucose(),
                    true
                );

                // Calculate using the new method
                InsulinCalculator.InsulinCalculationResult result =
                    InsulinCalculator.calculateSimple(totalCarbs, params);

                // Log breakdown
                apiLogger.insulinCalcBreakdown(
                    result.getCarbDose(),
                    result.getCorrectionDose(),
                    result.getBaseDose(),
                    result.getSickAdjustment(),
                    result.getStressAdjustment(),
                    result.getExerciseAdjustment(),
                    result.getTotalDose()
                );

                // Log warning if any
                if (result.hasWarning()) {
                    apiLogger.insulinCalcWarning(result.getWarning());
                }

                recommendedDose = result.getRoundedDose();
                profileComplete = true;

                apiLogger.insulinCalcResult(recommendedDose, true, null);
                log.info("Calculated recommended insulin dose: {} units", recommendedDose);

            } else {
                // Profile incomplete - skip calculation
                missingFields = params.getMissingFields();
                insulinMessage = "Complete your medical profile to get insulin recommendations";

                apiLogger.insulinCalcSkipped(missingFields);
                apiLogger.insulinCalcResult(null, false, insulinMessage);
            }
        }

        // Step 5: Save meal
        apiLogger.scanStep(5, "SAVING MEAL TO DATABASE");
        MealEntity meal = new MealEntity();
        meal.setId(mealIdGenerator.generateMealId(systemId));
        meal.setUserId(userDocId);
        meal.setImageUrl(request.getImageUrl());
        meal.setFoodItems(foodItems);
        meal.setTotalCarbs(NumberUtils.roundTo2Decimals(totalCarbs));
        meal.setRecommendedDose(recommendedDose);
        meal.setStatus(MealStatus.PENDING);
        
        if (estimatedWeightGrams != null) meal.setEstimatedWeight(estimatedWeightGrams);
        if (portionConfidence != null) meal.setAnalysisConfidence(portionConfidence);

        // Set profile status for client
        meal.setProfileComplete(profileComplete);
        meal.setMissingProfileFields(missingFields);
        meal.setInsulinMessage(insulinMessage);
        
        MealEntity saved = mealRepository.save(meal);
        log.info("Meal saved: {}", meal.getId());

        long totalTime = System.currentTimeMillis() - scanStartTime;
        apiLogger.scanComplete(foodItems.size(), totalCarbs, recommendedDose, totalTime);

        return mealConverter.toBoundary(saved);
    }

    private float estimateTotalWeightFromFoodTypes(List<PortionEstimator.FoodItem> items) {
        float total = 0f;
        for (PortionEstimator.FoodItem item : items) {
            float portion = portionEstimator.estimatePortion(item.name, item.confidence, item.visionEstimate);
            total += portion;
        }
        return total;
    }

    private void validateScanRequest(ScanRequestBoundary request) {
        if (request == null) throw new InsuScanInvalidInputException("Scan request cannot be null");
        if (request.getUserId() == null) throw new InsuScanInvalidInputException("User ID is required");
        InputValidators.validateSystemId(request.getUserId().getSystemId());
        InputValidators.validateEmail(request.getUserId().getEmail());

        if (request.getImageBase64() == null && request.getImageUrl() == null) {
            throw new InsuScanInvalidInputException("Image data required");
        }
    }

    private FoodRecognitionResult analyzeImage(ScanRequestBoundary request) {
        if (request.getImageBase64() != null) {
            return imageAnalysisService.analyzeImage(request.getImageBase64());
        } else {
            return imageAnalysisService.analyzeImageFromUrl(request.getImageUrl());
        }
    }

    private MealBoundary createFailedMeal(String userDocId, String imageUrl) {
        MealEntity meal = new MealEntity();
        String mealUuid = UUID.randomUUID().toString();
        meal.setId(systemId + "_" + mealUuid);
        meal.setUserId(userDocId);
        meal.setImageUrl(imageUrl);
        meal.setFoodItems(new ArrayList<>());
        meal.setTotalCarbs(0f);
        meal.setStatus(MealStatus.FAILED);
        MealEntity saved = mealRepository.save(meal);
        return mealConverter.toBoundary(saved);
    }
}