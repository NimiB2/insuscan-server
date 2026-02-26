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
import com.insuscan.calculation.CalculationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.insuscan.exception.NoFoodDetectedException;

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
                estimatedWeightGrams);

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
            throw new NoFoodDetectedException(visionResult.getErrorMessage());
        }

        if (visionResult.getDetectedFoods().isEmpty()) {
            apiLogger.scanFailed("VISION", "No food items detected");
            throw new NoFoodDetectedException("No food detected in image. Try a clearer photo.");
        }

        // Step 2: Calculate portion sizes
        apiLogger.scanStep(2, "CALCULATING PORTION SIZES (v2 PIPELINE)");

        // ── Parse food regions from client (if available) ──
        List<Map<String, Object>> foodRegions = parseFoodRegions(request.getFoodRegionsJson());
        boolean hasPhysicsData = !foodRegions.isEmpty();

        List<PortionEstimator.FoodItem> portionItems = new ArrayList<>();
        float totalVisionPortions = 0f;

        for (FoodRecognitionResult.RecognizedFoodItem detected : visionResult.getDetectedFoods()) {
            portionItems.add(new PortionEstimator.FoodItem(
                    detected.getName(),
                    detected.getConfidence(),
                    detected.getEstimatedPortionGrams()));

            if (detected.getEstimatedPortionGrams() != null && detected.getEstimatedPortionGrams() > 0) {
                totalVisionPortions += detected.getEstimatedPortionGrams();
            }
        }

        Float totalWeightToDistribute = estimatedWeightGrams;

        // PRIORITY 1: Physics from client per-food regions (most accurate)
        // → Handled below in per-item loop

        // PRIORITY 2: Plate/Bowl geometry from client measurements + GPT fill
        // estimation
        if (totalWeightToDistribute == null
                && request.getPlateDiameterCm() != null && request.getPlateDiameterCm() > 0) {

            float radiusCm = request.getPlateDiameterCm() / 2.0f;
            float depthCm = (request.getPlateDepthCm() != null && request.getPlateDepthCm() > 0)
                    ? request.getPlateDepthCm()
                    : 1.5f; // default shallow plate

            // Determine container type (GPT > client > infer from depth)
            String containerType = (visionResult.getDetectedContainerType() != null)
                    ? visionResult.getDetectedContainerType()
                    : (request.getContainerType() != null)
                            ? request.getContainerType()
                            : (depthCm > 3.0f ? "DEEP_BOWL" : depthCm > 1.5f ? "REGULAR_BOWL" : "FLAT_PLATE");

            boolean isBowl = containerType.toUpperCase().contains("BOWL");

            // Calculate container volume:
            // Bowls → paraboloid (½ × π × r² × h) — bowls are curved, not flat-sided
            // Plates → cylinder (π × r² × h)
            float containerVolumeCm3 = isBowl
                    ? (float) (0.5 * Math.PI * radiusCm * radiusCm * depthCm) // paraboloid
                    : (float) (Math.PI * radiusCm * radiusCm * depthCm); // cylinder

            // Fill level: GPT visual estimation (best) > hardcoded fallback
            float fillFraction;
            if (visionResult.getContainerFillPercent() != null && visionResult.getContainerFillPercent() > 0) {
                fillFraction = visionResult.getContainerFillPercent() / 100.0f;
                log.info("[v2-PLATE] GPT estimated fill: {}%", visionResult.getContainerFillPercent());
            } else {
                fillFraction = getContainerFillFactor(containerType, depthCm);
                log.info("[v2-PLATE] Using default fill factor: {}", fillFraction);
            }

            float foodVolumeCm3 = containerVolumeCm3 * fillFraction;

            // Resolve density from the primary food detected
            String primaryFood = !portionItems.isEmpty() ? portionItems.get(0).name : "other";
            float density = portionEstimator.calculateWeight(primaryFood, 1.0f); // density = weight/1cm³

            totalWeightToDistribute = foodVolumeCm3 * density;

            log.info("[v2-PLATE] {} {}cm×{}cm → {}Vol={}cm³ × fill={} → food={}cm³ × d={} → {}g",
                    containerType,
                    request.getPlateDiameterCm(), depthCm,
                    isBowl ? "paraboloid" : "cylinder",
                    String.format("%.1f", containerVolumeCm3),
                    String.format("%.0f%%", fillFraction * 100),
                    String.format("%.1f", foodVolumeCm3),
                    density,
                    String.format("%.1f", totalWeightToDistribute));
        }

        // PRIORITY 3: GPT vision estimate (least accurate fallback)
        if (totalWeightToDistribute == null) {
            totalWeightToDistribute = (totalVisionPortions > 0)
                    ? totalVisionPortions
                    : estimateTotalWeightFromFoodTypes(portionItems);
            log.info("[v2-FALLBACK] Using vision estimate: {}g", totalWeightToDistribute);
        }

        Map<String, Float> distributedPortions = portionEstimator.distributePortions(
                portionItems, totalWeightToDistribute);

        // Step 3: Get nutrition data (INTEGRATING THE JUDGE + DENSITY)
        apiLogger.scanStep(3, "SEMANTIC NUTRITION MATCHING + DENSITY CALCULATION");
        List<MealEntity.FoodItem> foodItems = new ArrayList<>();
        float totalCarbs = 0f;
        int regionIndex = 0;

        for (FoodRecognitionResult.RecognizedFoodItem detected : visionResult.getDetectedFoods()) {

            // --- USDA Matching (existing Judge logic) ---
            NutritionInfo finalNutrition = null;

            // A. Fetch Candidates (Step 2 - Retrieval)
            List<NutritionInfo> candidates = nutritionDataService.searchCandidates(detected.getBaseIngredient());

            if (!candidates.isEmpty()) {
                // B. The Judge Decides (Step 3 - Semantic Matching)
                String bestFdcId = semanticMatchingService.findBestMatch(detected, candidates);

                // C. Get Full Data for Winner
                if (bestFdcId != null) {
                    finalNutrition = nutritionDataService.getNutritionInfo(bestFdcId);
                } else {
                    finalNutrition = candidates.get(0);
                }
            } else {
                finalNutrition = nutritionDataService.getNutritionInfo(detected.getName());
            }

            MealEntity.FoodItem item = new MealEntity.FoodItem();
            item.setName(detected.getName());
            item.setConfidence(NumberUtils.roundTo2Decimals(detected.getConfidence()));

            // Add safety flags to the DB entity for future reference
            if (detected.getRiskFlags() != null && !detected.getRiskFlags().isEmpty()) {
                item.setNote("Risks: " + String.join(", ", detected.getRiskFlags()));
            }

            // ── v2: Per-Food Weight Calculation ──
            Float itemWeight;
            String weightSource;

            if (hasPhysicsData && regionIndex < foodRegions.size()) {
                // PHYSICS PATH: Use area × height × density
                Map<String, Object> region = foodRegions.get(regionIndex);
                float areaCm2 = ((Number) region.getOrDefault("areaCm2", 0.0)).floatValue();
                float heightCm = ((Number) region.getOrDefault("heightCm", 1.0)).floatValue();
                float shapeFactor = getShapeFactor(heightCm);

                float volumeCm3 = areaCm2 * heightCm * shapeFactor;

                // Resolve density: USDA → Lookup → Default
                float density = resolveDensity(detected.getName(), finalNutrition);

                itemWeight = volumeCm3 * density;
                weightSource = String.format("PHYSICS(V=%.1fcm³, d=%.2fg/cm³)", volumeCm3, density);

                log.info("[v2] {} → area={}cm² × h={}cm × sf={} = {}cm³ × d={} = {}g",
                        detected.getName(), areaCm2, heightCm, shapeFactor, volumeCm3, density, itemWeight);

                regionIndex++;
            } else {
                // FALLBACK PATH: Use existing distribution logic
                itemWeight = distributedPortions.get(detected.getName());
                if (itemWeight == null) {
                    itemWeight = portionEstimator.estimatePortion(
                            detected.getName(),
                            detected.getConfidence(),
                            detected.getEstimatedPortionGrams());
                }
                weightSource = "DISTRIBUTED";
            }

            item.setQuantity(NumberUtils.roundTo2Decimals(itemWeight));

            // Append weight source to note (for debugging/transparency)
            String existingNote = item.getNote();
            String sourceNote = "Calc: " + weightSource;
            item.setNote(existingNote != null ? existingNote + " | " + sourceNote : sourceNote);

            if (finalNutrition != null && finalNutrition.isFound()) {
                float itemCarbs = finalNutrition.calculateCarbs(itemWeight);
                item.setCarbs(NumberUtils.roundTo2Decimals(itemCarbs));
                item.setUsdaFdcId(finalNutrition.getFdcId());

                String source = hasPhysicsData ? "PHYSICS+JUDGE" : "SEMANTIC_JUDGE";
                if (finalNutrition.getFdcId().startsWith("fallback-"))
                    source = "FALLBACK";

                apiLogger.carbCalculation(
                        detected.getName(),
                        finalNutrition.getCarbsPer100g(),
                        itemWeight,
                        itemCarbs,
                        source);

                totalCarbs += itemCarbs;
            } else {
                item.setCarbs(0f);
                apiLogger.carbCalculationFailed(detected.getName(), "No nutrition data found");
            }

            foodItems.add(item);
        }

        log.info("Total carbs (Calculated): {}g [method={}]", totalCarbs, hasPhysicsData ? "PHYSICS" : "DISTRIBUTED");

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
                    .withTotalCarbs(totalCarbs)
                    .build();

            // Log profile status
            apiLogger.insulinCalcProfileStatus(params.isProfileComplete(), params.getMissingFields());

            if (params.isProfileComplete()) {
                // Profile complete - do full calculation
                apiLogger.insulinCalcParams(
                        params.getInsulinCarbRatio(),
                        params.getCorrectionFactor(),
                        params.getTargetGlucose(),
                        true);

                // Calculate using the new instance-based method
                // Note: InsulinCalculator is effectively stateless, we can instantiate it here.
                InsulinCalculator calculator = new InsulinCalculator();
                CalculationResult result = calculator.calculate(params);

                // Log breakdown
                apiLogger.insulinCalcBreakdown(
                        result.getCarbDose(),
                        result.getCorrectionDose(),
                        // Base dose is derived sum
                        (result.getCarbDose() + result.getCorrectionDose()),
                        result.getSickAdjustment(),
                        result.getStressAdjustment(),
                        result.getExerciseAdjustment(),
                        result.getTotalDose());

                // Log warning if any
                if (result.getWarning() != null && !result.getWarning().isEmpty()) {
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

        // Step 5: Return result WITHOUT saving (Stateless Analysis)
        apiLogger.scanStep(5, "PREPARING RESULT (NO DB SAVE)");
        MealEntity meal = new MealEntity();
        meal.setId(mealIdGenerator.generateMealId(systemId));
        meal.setUserId(userDocId);
        meal.setImageUrl(request.getImageUrl());
        meal.setFoodItems(foodItems);
        meal.setTotalCarbs(NumberUtils.roundTo2Decimals(totalCarbs));
        meal.setRecommendedDose(recommendedDose);
        meal.setStatus(MealStatus.PENDING); // Status is Pending until client saves

        if (estimatedWeightGrams != null)
            meal.setEstimatedWeight(estimatedWeightGrams);
        if (portionConfidence != null)
            meal.setAnalysisConfidence(portionConfidence);

        // Persist the user's selected reference object type
        meal.setReferenceObjectType(request.getReferenceObjectType());

        // Set profile status for client
        meal.setProfileComplete(profileComplete);
        meal.setMissingProfileFields(missingFields);
        meal.setInsulinMessage(insulinMessage);

        // Per user request, we do NOT save to DB here. The client must call
        // /save-scanned later.
        // MealEntity saved = mealRepository.save(meal);
        // log.info("Meal saved: {}", meal.getId());

        long totalTime = System.currentTimeMillis() - scanStartTime;
        apiLogger.scanComplete(foodItems.size(), totalCarbs, recommendedDose, totalTime);

        // Convert the transient entity to boundary
        return mealConverter.toBoundary(meal);
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
        if (request == null)
            throw new InsuScanInvalidInputException("Scan request cannot be null");
        if (request.getUserId() == null)
            throw new InsuScanInvalidInputException("User ID is required");
        InputValidators.validateSystemId(request.getUserId().getSystemId());
        InputValidators.validateEmail(request.getUserId().getEmail());

        if (request.getImageBase64() == null && request.getImageUrl() == null) {
            throw new InsuScanInvalidInputException("Image data required");
        }
    }

    private FoodRecognitionResult analyzeImage(ScanRequestBoundary request) {
        if (request.getImageBase64() != null) {
            return imageAnalysisService.analyzeImage(request.getImageBase64(), request.getReferenceObjectType());
        } else {
            return imageAnalysisService.analyzeImageFromUrl(request.getImageUrl(), request.getReferenceObjectType());
        }
    }

    // ── v2 Pipeline Helper Methods ──

    /**
     * Estimate what fraction of the container is filled with food.
     * Deep bowls are rarely filled to the top; flat plates have thin food layers.
     */
    private float getContainerFillFactor(String containerType, float depthCm) {
        if (containerType != null) {
            switch (containerType.toUpperCase()) {
                case "FLAT_PLATE":
                    return 0.70f; // thin spread on plate
                case "REGULAR_BOWL":
                    return 0.55f; // typical bowl fill
                case "DEEP_BOWL":
                    return 0.50f; // deep bowls aren't filled to brim
            }
        }
        // Infer from depth if no container type specified
        if (depthCm <= 2.0f)
            return 0.70f; // flat plate
        if (depthCm <= 4.0f)
            return 0.55f; // regular bowl
        return 0.50f; // deep bowl
    }

    /**
     * Parse the JSON-encoded food region measurements from the client.
     * Returns empty list if null/invalid (graceful fallback to distributed mode).
     */
    private List<Map<String, Object>> parseFoodRegions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            log.warn("[v2] Failed to parse foodRegionsJson: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Shape factor reduces the cylindrical volume estimate based on food pile
     * height.
     * Flat foods (like a tortilla) are closer to cylindrical; tall piles (like
     * rice) are more conical.
     */
    private float getShapeFactor(float heightCm) {
        if (heightCm <= 0.5f)
            return 0.85f; // flat (e.g., steak, tortilla)
        if (heightCm <= 1.5f)
            return 0.75f; // low pile (e.g., pasta)
        if (heightCm <= 3.0f)
            return 0.65f; // medium pile (e.g., rice)
        return 0.55f; // high pile (e.g., salad, fries)
    }

    /**
     * Resolve density for a food item using a 3-tier fallback:
     * 1. USDA data (serving weight / serving volume)
     * 2. Server lookup table (PortionEstimator.initFoodDensities)
     * 3. Default: 0.75 g/cm³
     */
    private float resolveDensity(String foodName, NutritionInfo nutrition) {
        // Tier 1: USDA-derived density
        if (nutrition != null && nutrition.getDensityGPerCm3() != null && nutrition.getDensityGPerCm3() > 0) {
            log.debug("[density] {} → USDA: {}g/cm³", foodName, nutrition.getDensityGPerCm3());
            return nutrition.getDensityGPerCm3();
        }

        // Tier 2: Lookup table (uses PortionEstimator's density map)
        float lookupDensity = portionEstimator.calculateWeight(foodName, 1.0f); // density = weight/1cm³
        if (lookupDensity != 0.75f) { // 0.75 is the default "other" — means we found a specific match
            log.debug("[density] {} → LOOKUP: {}g/cm³", foodName, lookupDensity);
            return lookupDensity;
        }

        // Tier 3: Default
        log.warn("[density] {} → DEFAULT: 0.75g/cm³ (no specific data)", foodName);
        return 0.75f;
    }
}