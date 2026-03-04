package com.insuscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.insuscan.util.OpenAiJsonParser;
import com.insuscan.util.GeminiApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class ScanServiceImpl implements ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanServiceImpl.class);

    private final ImageAnalysisService imageAnalysisService;
    private final NutritionDataService nutritionDataService;
    private final SemanticMatchingService semanticMatchingService; // <-- NEW JUDGE
    private final UserRepository userRepository;
    private final MealConverter mealConverter;
    private final MealIdGenerator mealIdGenerator;
    private final PortionEstimator portionEstimator;
    private final ApiLogger apiLogger;
//    private final OpenAiJsonParser jsonParser;

//    private final WebClient openAiWebClient;
//
//    @Value("${openai.api.key:}")
//    private String openAiApiKey;
//
   @Value("${spring.application.name:insuscan}")
   private String systemId;
    
    private final OpenAiJsonParser jsonParser;
    private final GeminiApiClient geminiClient;

    private static final float DEFAULT_PORTION_WEIGHT = 150f;

//    public ScanServiceImpl(
//            ImageAnalysisService imageAnalysisService,
//            NutritionDataService nutritionDataService,
//            SemanticMatchingService semanticMatchingService,
//            MealRepository mealRepository,
//            UserRepository userRepository,
//            MealConverter mealConverter,
//            MealIdGenerator mealIdGenerator,
//            PortionEstimator portionEstimator,
//            ApiLogger apiLogger,
//            WebClient.Builder webClientBuilder,
//            OpenAiJsonParser jsonParser) {
//
//        this.imageAnalysisService = imageAnalysisService;
//        this.nutritionDataService = nutritionDataService;
//        this.semanticMatchingService = semanticMatchingService;
//        this.userRepository = userRepository;
//        this.mealConverter = mealConverter;
//        this.mealIdGenerator = mealIdGenerator;
//        this.portionEstimator = portionEstimator;
//        this.apiLogger = apiLogger;
//        this.openAiWebClient = webClientBuilder
//                .baseUrl("https://api.openai.com/v1")
//                .build();
//        this.jsonParser = jsonParser;
//    }
    public ScanServiceImpl(
            ImageAnalysisService imageAnalysisService,
            NutritionDataService nutritionDataService,
            SemanticMatchingService semanticMatchingService,
            MealRepository mealRepository,
            UserRepository userRepository,
            MealConverter mealConverter,
            MealIdGenerator mealIdGenerator,
            PortionEstimator portionEstimator,
            ApiLogger apiLogger,
            GeminiApiClient geminiClient,
            OpenAiJsonParser jsonParser) {

        this.imageAnalysisService = imageAnalysisService;
        this.nutritionDataService = nutritionDataService;
        this.semanticMatchingService = semanticMatchingService;
        this.userRepository = userRepository;
        this.mealConverter = mealConverter;
        this.mealIdGenerator = mealIdGenerator;
        this.portionEstimator = portionEstimator;
        this.apiLogger = apiLogger;
        this.geminiClient = geminiClient;
        this.jsonParser = jsonParser;
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
        FoodRecognitionResult visionResult = imageAnalysisService.analyzeImage(request.getImageBase64(),
                request.getReferenceObjectType());

        if (!visionResult.isSuccess() || visionResult.getDetectedFoods().isEmpty()) {
            String msg = visionResult.getErrorMessage() != null ? visionResult.getErrorMessage()
                    : "No food items detected";
            apiLogger.scanStep(1, "VISION WARNING: " + msg);
            log.error("[VISION] Proceeding without food items to resolve plate geometry: {}", msg);
            // We continue instead of throwing to allow plate geometry tests to work
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

        // Total weight is distributed among detected food items
        // (Removed unused totalWeightToDistribute variable)

        // Check if we have plate geometry for per-food physics calculation
        boolean hasPlateGeometry = request.getPlateDiameterCm() != null
                && request.getPlateDiameterCm() > 0;

        // Check if GPT provided coverage data for at least one food
        boolean hasCoverageData = visionResult.getDetectedFoods().stream()
                .anyMatch(f -> f.getCoveragePercent() != null && f.getCoveragePercent() > 0);

        // Pre-compute plate area if available (used per-food in the loop below)
        float plateAreaCm2 = 0f;
        float plateDepthCm = 0f;
        Float resolvedPlateDiameterCm = null;
        Float resolvedPlateDepthCm = null;
        String resolvedContainerType = null;
        boolean isBowl = false;

        if (hasPlateGeometry) {
            resolvedPlateDiameterCm = request.getPlateDiameterCm();
            float radiusCm = resolvedPlateDiameterCm / 2.0f;
            plateAreaCm2 = (float) (Math.PI * radiusCm * radiusCm);

            if (request.getPlateDepthCm() != null && request.getPlateDepthCm() > 0) {
                // We have BOTH diameter and depth (ARCore full measurement)
                plateDepthCm = request.getPlateDepthCm();
                resolvedPlateDepthCm = plateDepthCm;

                // Thresholds: Flat <= 2.0cm, Regular <= 4.5cm, Deep > 4.5cm
                resolvedContainerType = plateDepthCm > 4.5f ? "DEEP_BOWL"
                        : plateDepthCm > 2.0f ? "REGULAR_BOWL" : "FLAT_PLATE";

                isBowl = resolvedContainerType.toUpperCase().contains("BOWL");

                log.info("[v3] Plate geometry (ARCore): d={}cm, depth={}cm, area={}cm², type={}",
                        resolvedPlateDiameterCm, resolvedPlateDepthCm,
                        String.format("%.1f", plateAreaCm2), resolvedContainerType);
            } else {
                // Diameter provided but depth missing — trigger visual estimation
                log.info("[v3] Diameter provided ({}cm) but depth missing — triggering visual estimation",
                        resolvedPlateDiameterCm);

                float[] gptEstimate = estimateContainerDimensionsFromGpt(request.getImageBase64(),
                        request.getReferenceObjectType(),
                        request.getPlateDiameterCm());

                if (gptEstimate != null) {
                    resolvedPlateDepthCm = gptEstimate[1];
                    resolvedContainerType = resolvedPlateDepthCm > 4.5f ? "DEEP_BOWL"
                            : resolvedPlateDepthCm > 2.0f ? "REGULAR_BOWL" : "FLAT_PLATE";
                    log.info("[v3-GPT] Estimated depth: {}cm (type={})", resolvedPlateDepthCm, resolvedContainerType);
                } else {
                    // Absolute fallback: no history, no GPT, just defaults
                    resolvedPlateDepthCm = 1.5f;
                    resolvedContainerType = "FLAT_PLATE";
                    log.warn("[v3-DEFAULT] GPT estimation failed, using default depth (1.5cm)");
                }
                plateDepthCm = resolvedPlateDepthCm;
                isBowl = resolvedContainerType.toUpperCase().contains("BOWL");
            }
        } else {
            // No direct measurements — fallback to 100% Visual Estimation
            resolvedContainerType = (visionResult.getDetectedContainerType() != null)
                    ? visionResult.getDetectedContainerType()
                    : (request.getContainerType() != null) ? request.getContainerType() : "FLAT_PLATE";

            log.info("[v3] No geometry provided for container type '{}' — asking GPT to estimate",
                    resolvedContainerType);

            float[] gptEstimate = estimateContainerDimensionsFromGpt(request.getImageBase64(),
                    request.getReferenceObjectType(), null);

            if (gptEstimate != null) {
                resolvedPlateDiameterCm = gptEstimate[0];
                resolvedPlateDepthCm = gptEstimate[1];
                resolvedContainerType = resolvedPlateDepthCm > 4.5f ? "DEEP_BOWL"
                        : resolvedPlateDepthCm > 2.0f ? "REGULAR_BOWL" : "FLAT_PLATE";

                float radiusCm = resolvedPlateDiameterCm / 2.0f;
                plateAreaCm2 = (float) (Math.PI * radiusCm * radiusCm);
                plateDepthCm = resolvedPlateDepthCm;
                hasPlateGeometry = true;
                isBowl = resolvedContainerType.toUpperCase().contains("BOWL");

                log.info("[v3-GPT] Full visual estimation: d={}cm, depth={}cm, type={}",
                        String.format("%.1f", resolvedPlateDiameterCm),
                        String.format("%.1f", resolvedPlateDepthCm), resolvedContainerType);
            } else {
                // Absolute last resort
                log.warn("[v3-FALLBACK] GPT estimation failed, using safe defaults for {}", resolvedContainerType);
                resolvedPlateDiameterCm = 25.0f;
                resolvedPlateDepthCm = resolvedContainerType.toUpperCase().contains("BOWL") ? 4.5f : 1.5f;
                float radiusCm = resolvedPlateDiameterCm / 2.0f;
                plateAreaCm2 = (float) (Math.PI * radiusCm * radiusCm);
                plateDepthCm = resolvedPlateDepthCm;
                hasPlateGeometry = true;
                isBowl = resolvedContainerType.toUpperCase().contains("BOWL");
            }
        }

        // Fallback: distribute total weight when per-food physics not possible
        Float totalWeightFallback = null;
        Map<String, Float> distributedPortions = null;

        if (!hasPlateGeometry || !hasCoverageData) {
            totalWeightFallback = estimatedWeightGrams;
            if (totalWeightFallback == null) {
                totalWeightFallback = (totalVisionPortions > 0)
                        ? totalVisionPortions
                        : estimateTotalWeightFromFoodTypes(portionItems);
            }
            distributedPortions = portionEstimator.distributePortions(portionItems, totalWeightFallback);
            log.info("[v3-FALLBACK] Distributed total: {}g", totalWeightFallback);
        }

        // Step 3: Get nutrition data
        apiLogger.scanStep(3, "SEMANTIC NUTRITION MATCHING + DENSITY CALCULATION");
        List<MealEntity.FoodItem> foodItems = new ArrayList<>();
        float totalCarbs = 0f;
        int regionIndex = 0;

        for (FoodRecognitionResult.RecognizedFoodItem detected : visionResult.getDetectedFoods()) {
            NutritionInfo finalNutrition = null;
            List<NutritionInfo> candidates = new ArrayList<>();
            List<String> gptTerms = detected.getUsdaSearchTerms();

            if (gptTerms != null && !gptTerms.isEmpty()) {
                for (String term : gptTerms) {
                    candidates = nutritionDataService.searchCandidates(term);
                    if (!candidates.isEmpty())
                        break;
                }
            }

            if (candidates.isEmpty()) {
                candidates = nutritionDataService.searchCandidates(detected.getBaseIngredient());
            }

            if (!candidates.isEmpty()) {
                String bestFdcId = semanticMatchingService.findBestMatch(detected, candidates,
                        request.getImageBase64());
                finalNutrition = candidates.stream()
                        .filter(c -> bestFdcId != null && bestFdcId.equals(c.getFdcId()))
                        .findFirst()
                        .orElse(candidates.get(0));
            } else {
                finalNutrition = nutritionDataService.getNutritionInfo(detected.getName());
            }

            MealEntity.FoodItem item = new MealEntity.FoodItem();
            item.setName(detected.getName());
            item.setConfidence(NumberUtils.roundTo2Decimals(detected.getConfidence()));
            item.setBboxXPct(detected.getBboxXPct());
            item.setBboxYPct(detected.getBboxYPct());
            item.setBboxWPct(detected.getBboxWPct());
            item.setBboxHPct(detected.getBboxHPct());

            if (detected.getRiskFlags() != null && !detected.getRiskFlags().isEmpty()) {
                item.setNote("Risks: " + String.join(", ", detected.getRiskFlags()));
            }

            // Weight calculation
            Float itemWeight;
            String weightSource;

            if (hasPhysicsData && regionIndex < foodRegions.size()) {
                Map<String, Object> region = foodRegions.get(regionIndex);
                float areaCm2 = ((Number) region.getOrDefault("areaCm2", 0.0)).floatValue();
                float heightCm = ((Number) region.getOrDefault("heightCm", 1.0)).floatValue();
                float shapeFactor = getShapeFactor(heightCm);
                float bowlFactor = getBowlGeometryFactor(resolvedContainerType);
                float volumeCm3 = areaCm2 * heightCm * shapeFactor * bowlFactor;
                float density = resolveDensity(detected.getName(), finalNutrition);
                itemWeight = volumeCm3 * density;
                weightSource = "CLIENT_PHYSICS";
                regionIndex++;
            } else if (hasPlateGeometry && detected.getCoveragePercent() != null && detected.getCoveragePercent() > 0) {
                float foodAreaCm2 = plateAreaCm2 * (detected.getCoveragePercent() / 100.0f);
                float heightCm = heightCategoryToCm(detected.getHeightCategory());
                if (isBowl && heightCm > plateDepthCm)
                    heightCm = plateDepthCm * 0.8f;
                float shapeFactor = getShapeFactor(heightCm);
                float bowlFactor = getBowlGeometryFactor(resolvedContainerType);
                float volumeCm3 = foodAreaCm2 * heightCm * shapeFactor * bowlFactor;
                float density = resolveDensity(detected.getName(), finalNutrition);
                itemWeight = volumeCm3 * density;
                weightSource = "GPT_PHYSICS";
            } else {
                itemWeight = (distributedPortions != null) ? distributedPortions.get(detected.getName()) : null;
                if (itemWeight == null) {
                    itemWeight = portionEstimator.estimatePortion(detected.getName(), detected.getConfidence(),
                            detected.getEstimatedPortionGrams());
                }
                weightSource = "DISTRIBUTED";
            }

            item.setQuantity(NumberUtils.roundTo2Decimals(itemWeight));
            String existingNote = item.getNote();
            item.setNote(existingNote != null ? existingNote + " | Calc: " + weightSource : "Calc: " + weightSource);

            if (finalNutrition != null && finalNutrition.isFound()) {
                float itemCarbs = finalNutrition.calculateCarbs(itemWeight);
                item.setCarbs(NumberUtils.roundTo2Decimals(itemCarbs));
                item.setUsdaFdcId(finalNutrition.getFdcId());
                totalCarbs += itemCarbs;
                apiLogger.carbCalculation(detected.getName(), finalNutrition.getCarbsPer100g(), itemWeight, itemCarbs,
                        weightSource);
            } else {
                item.setCarbs(0f);
            }
            foodItems.add(item);
        }

        log.info("Total carbs: {}g", totalCarbs);

        // Final sanity review
        List<String> reviewWarnings = runFinalReview(request.getImageBase64(), foodItems, totalCarbs);

        // Prepare result
        MealEntity meal = new MealEntity();
        meal.setId(mealIdGenerator.generateMealId(systemId));
        meal.setUserId(userDocId);
        meal.setImageUrl(request.getImageUrl());
        meal.setFoodItems(foodItems);
        meal.setTotalCarbs(NumberUtils.roundTo2Decimals(totalCarbs));
        meal.setStatus(MealStatus.PENDING);
        if (estimatedWeightGrams != null)
            meal.setEstimatedWeight(estimatedWeightGrams);
        if (portionConfidence != null)
            meal.setAnalysisConfidence(portionConfidence);
        meal.setReferenceObjectType(request.getReferenceObjectType());
        if (resolvedPlateDiameterCm != null)
            meal.setPlateDiameterCm(resolvedPlateDiameterCm);
        if (resolvedPlateDepthCm != null)
            meal.setPlateDepthCm(resolvedPlateDepthCm);
        if (resolvedContainerType != null)
            meal.setContainerType(resolvedContainerType);

        meal.setProfileComplete(isProfileComplete(user));
        meal.setMissingProfileFields(getMissingProfileFields(user));
        if (foodItems.isEmpty())
            meal.setInsulinMessage("Plate is empty — no food detected");

        if (!reviewWarnings.isEmpty())
            meal.setReviewWarnings(reviewWarnings);

        long totalTime = System.currentTimeMillis() - scanStartTime;
        apiLogger.scanComplete(foodItems.size(), totalCarbs, null, totalTime);

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

    // just checks if the user has the minimum fields for insulin calc
    private boolean isProfileComplete(UserEntity user) {
        if (user == null)
            return false;
        return user.getInsulinCarbRatio() != null
                && user.getCorrectionFactor() != null
                && user.getTargetGlucose() != null;
    }

    // returns which fields are still missing
    private List<String> getMissingProfileFields(UserEntity user) {
        List<String> missing = new ArrayList<>();
        if (user == null) {
            missing.add("Full profile");
            return missing;
        }
        if (user.getInsulinCarbRatio() == null)
            missing.add("Insulin-to-Carb Ratio");
        if (user.getCorrectionFactor() == null)
            missing.add("Correction Factor (ISF)");
        if (user.getTargetGlucose() == null)
            missing.add("Target Glucose");
        return missing;
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

    // ── v2 Pipeline Helper Methods ──

    /**
     * Estimate what fraction of the container is filled with food.
     * Deep bowls are rarely filled to the top; flat plates have thin food layers.
     */
    // private float getContainerFillFactor(String containerType, float depthCm) {
    // if (containerType != null) {
    // switch (containerType.toUpperCase()) {
    // case "FLAT_PLATE":
    // return 0.70f; // thin spread on plate
    // case "REGULAR_BOWL":
    // return 0.55f; // typical bowl fill
    // case "DEEP_BOWL":
    // return 0.50f; // deep bowls aren't filled to brim
    // }
    // }
    // // Infer from depth if no container type specified
    // if (depthCm <= 2.0f)
    // return 0.70f; // flat plate
    // if (depthCm <= 4.0f)
    // return 0.55f; // regular bowl
    // return 0.50f; // deep bowl
    // }

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
     * Geometric correction for bowl curvature.
     * A bowl's curved floor means less volume than a cylinder of the same
     * diameter and height. This factor accounts for that.
     *
     * A paraboloid bowl holds ~50% of the equivalent cylinder,
     * a hemisphere ~67%. We use intermediate values.
     *
     * Only affects volume — NOT fill level (GPT handles that separately
     * via coverage_percent and container_fill_percent).
     */
    private float getBowlGeometryFactor(String containerType) {
        if (containerType == null)
            return 1.0f;
        switch (containerType.toUpperCase()) {
            case "FLAT_PLATE":
                return 1.0f; // flat surface, no correction
            case "REGULAR_BOWL":
                return 0.65f; // mid-depth curve
            case "DEEP_BOWL":
                return 0.55f; // steep paraboloid
            default:
                return 1.0f;
        }
    }

    /**
     * Maps GPT height category to estimated height in cm.
     * Used when ARCore depth is not available.
     */
    private float heightCategoryToCm(String category) {
        if (category == null)
            return 1.5f; // safe default
        switch (category.toUpperCase()) {
            case "FLAT":
                return 0.4f;
            case "LOW_PILE":
                return 1.0f;
            case "MEDIUM_PILE":
                return 2.0f;
            case "HIGH_PILE":
                return 4.0f;
            default:
                return 1.5f;
        }
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

    /**
     * Final sanity check — asks GPT to review the calculated results against the
     * image.
     * Returns a list of warnings if something looks off. Does NOT change the
     * results,
     * just flags issues for the client to show the user.
     */
//    private List<String> runFinalReview(String base64Image,
//            List<MealEntity.FoodItem> foodItems,
//            float totalCarbs) {
//        if (base64Image == null || base64Image.isBlank() || foodItems.isEmpty()) {
//            return List.of();
//        }
//
//        try {
//            // Build a summary of what we calculated
//            StringBuilder summary = new StringBuilder();
//            for (MealEntity.FoodItem item : foodItems) {
//                summary.append(String.format("- %s: %.0fg, %.1fg carbs\n",
//                        item.getName(), item.getQuantity(), item.getCarbs()));
//            }
//
//            String prompt = String.format("""
//                    You are a clinical nutrition reviewer for a diabetes app.
//                    Look at this meal photo and compare it to the calculated results below.
//
//                    CALCULATED RESULTS:
//                    %s
//                    Total carbs: %.1fg
//
//                    REVIEW CHECKLIST:
//                    1. Does the total weight per item seem reasonable for the visible portion size?
//                    2. Are there any visible foods MISSING from the list?
//                    3. Does total carbs seem realistic for this meal?
//
//                    If everything looks reasonable, return: { "ok": true, "warnings": [] }
//                    If something is off, return: { "ok": false, "warnings": ["specific issue"] }
//
//                    Return STRICT JSON ONLY.
//                    """, summary.toString(), totalCarbs);
//
//            Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
//            Map<String, Object> imageUrlObj = Map.of("url", "data:image/jpeg;base64," + base64Image);
//            Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlObj);
//
//            Map<String, Object> requestBody = Map.of(
//                    "model", "gpt-4o-mini",
//                    "messages", List.of(
//                            Map.of("role", "user", "content", List.of(textContent, imageContent))),
//                    "temperature", 0.0,
//                    "max_tokens", 300,
//                    "response_format", Map.of("type", "json_object"));
//
//            // Reuse the same OpenAI WebClient (needs injection — see 8B)
//            String response = openAiWebClient.post()
//                    .uri("/chat/completions")
//                    .header("Authorization", "Bearer " + openAiApiKey)
//                    .header("Content-Type", "application/json")
//                    .bodyValue(requestBody)
//                    .retrieve()
//                    .bodyToMono(String.class)
//                    .block();
//
//            if (response != null) {
//                JsonNode result = jsonParser.parseContent(response);
//                if (result.has("warnings") && result.get("warnings").isArray()) {
//                    List<String> warnings = new ArrayList<>();
//                    for (JsonNode w : result.get("warnings")) {
//                        warnings.add(w.asText());
//                    }
//                    if (!warnings.isEmpty()) {
//                        log.warn("[FINAL_REVIEW] Issues found: {}", warnings);
//                    }
//                    return warnings;
//                }
//            }
//        } catch (Exception e) {
//            // Review is best-effort — never block the scan
//            log.warn("[FINAL_REVIEW] Failed (non-blocking): {}", e.getMessage());
//        }
//        return List.of();
//    }
    
    private List<String> runFinalReview(String base64Image,
            List<MealEntity.FoodItem> foodItems,
            float totalCarbs) {
        if (base64Image == null || base64Image.isBlank() || foodItems.isEmpty()) {
            return List.of();
        }

        try {
            StringBuilder summary = new StringBuilder();
            for (MealEntity.FoodItem item : foodItems) {
                summary.append(String.format("- %s: %.0fg, %.1fg carbs\n",
                        item.getName(), item.getQuantity(), item.getCarbs()));
            }

            String prompt = String.format("""
                    You are a clinical nutrition reviewer for a diabetes app.
                    Look at this meal photo and compare it to the calculated results below.

                    CALCULATED RESULTS:
                    %s
                    Total carbs: %.1fg

                    REVIEW CHECKLIST:
                    1. Does the total weight per item seem reasonable for the visible portion size?
                    2. Are there any visible foods MISSING from the list?
                    3. Does total carbs seem realistic for this meal?

                    If everything looks reasonable, return: { "ok": true, "warnings": [] }
                    If something is off, return: { "ok": false, "warnings": ["specific issue"] }

                    Return STRICT JSON ONLY.
                    """, summary.toString(), totalCarbs);

            String content = geminiClient.callFlashModelWithImage(prompt, base64Image);

            if (content != null && !content.isBlank()) {
                String json = jsonParser.extractJsonObject(content);
                JsonNode result = new ObjectMapper().readTree(json);
                if (result.has("warnings") && result.get("warnings").isArray()) {
                    List<String> warnings = new ArrayList<>();
                    for (JsonNode w : result.get("warnings")) {
                        warnings.add(w.asText());
                    }
                    if (!warnings.isEmpty()) {
                        log.warn("[FINAL_REVIEW] Issues found: {}", warnings);
                    }
                    return warnings;
                }
            }
        } catch (Exception e) {
            log.warn("[FINAL_REVIEW] Failed (non-blocking): {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * GPT-based container dimension estimation.
     * When ARCore is unavailable and there's no user history, ask OpenAI to
     * visually estimate the plate/bowl dimensions from the image.
     *
     * Returns float[2] = { diameterCm, depthCm } or null if estimation fails.
     */
//    private float[] estimateContainerDimensionsFromGpt(String imageBase64, String referenceObjectType,
//            Float knownDiameterCm) {
//        try {
//            if (imageBase64 == null || imageBase64.isBlank()) {
//                log.warn("[GPT-DIM] No image available for dimension estimation");
//                return null;
//            }
//
//            String scaleContext = "";
//            if (knownDiameterCm != null && knownDiameterCm > 0) {
//                scaleContext += String.format(
//                        "\nSCALE ANCHOR: We already measured the container's OUTER DIAMETER as %.1f cm. Use this as your primary scale reference to calculate the depth.",
//                        knownDiameterCm);
//            }
//
//            if (referenceObjectType != null && !referenceObjectType.equalsIgnoreCase("NONE")) {
//                scaleContext += "\nSYSTEM ALERT: Our local computer vision HAS ALREADY DETECTED a '"
//                        + referenceObjectType
//                        + "' in this specific image. It is definitely there. Search for it diligently and use it as your ground truth for scale.";
//            }
//
//            String prompt = "You are an expert in estimating real-world physical dimensions from photographs. "
//                    + "Your task: estimate the INNER diameter and depth of the plate, bowl, or container in this image.\n\n"
//                    + "CONTEXT: " + scaleContext + "\n\n"
//                    + "STEP 1 — Identify the container (Plate/Bowl) and its type. You MUST classify it as FLAT_PLATE, REGULAR_BOWL, or DEEP_BOWL.\n\n"
//                    + "STEP 2 — Use the scale reference. "
//                    + "If a SYSTEM ALERT is mentioned above, the object IS in the image. Use its known length (Card=8.5cm, Syringe/Pen=16cm) to measure the container.\n\n"
//                    + "STEP 3 — Cross-validate with container type norms:\n"
//                    + "- Flat dinner plate: ~24-26cm diameter, 1-2cm depth\n"
//                    + "- Small/Salad plate: ~18-20cm diameter, 1-2cm depth\n"
//                    + "- Regular bowl: ~14-16cm diameter, 4-5cm depth\n"
//                    + "- Deep bowl/Pot: ~14-16cm diameter, 6-10cm depth\n\n"
//                    + "IMPORTANT: Even if the image is slightly blurry or dark, do NOT return 0 and do not complain. Always provide your best professional estimate. Trust the SYSTEM ALERT for the presence of the scale object.\n\n"
//                    + "Reply ONLY with this JSON object:\n"
//                    + "{\n"
//                    + "  \"referenceObjectFound\": \"name of object used for scale\",\n"
//                    + "  \"diameterCm\": <number>,\n"
//                    + "  \"depthCm\": <number>,\n"
//                    + "  \"containerType\": \"FLAT_PLATE\" | \"REGULAR_BOWL\" | \"DEEP_BOWL\",\n"
//                    + "  \"confidence\": \"low\" | \"medium\" | \"high\",\n"
//                    + "  \"reasoning\": \"Explain how you found the reference object and used it to measure the container.\"\n"
//                    + "}";
//
//            Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
//            Map<String, Object> imageUrlObj = Map.of("url", "data:image/jpeg;base64," + imageBase64);
//            Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlObj);
//
//            Map<String, Object> userMessage = Map.of(
//                    "role", "user",
//                    "content", List.of(textContent, imageContent));
//
//            Map<String, Object> requestBody = Map.of(
//                    "model", "gpt-4o-mini",
//                    "messages", List.of(userMessage),
//                    "temperature", 0.0,
//                    "max_tokens", 500,
//                    "response_format", Map.of("type", "json_object"));
//
//            long startTime = System.currentTimeMillis();
//
//            String response = openAiWebClient.post()
//                    .uri("/chat/completions")
//                    .header("Authorization", "Bearer " + openAiApiKey)
//                    .header("Content-Type", "application/json")
//                    .bodyValue(requestBody)
//                    .retrieve()
//                    .bodyToMono(String.class)
//                    .block();
//
//            long elapsed = System.currentTimeMillis() - startTime;
//
//            if (response == null || response.isBlank()) {
//                log.warn("[GPT-DIM] Empty response from OpenAI");
//                return null;
//            }
//
//            // Extract content from the response
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode root = mapper.readTree(response);
//            String content = root.path("choices").path(0).path("message").path("content").asText();
//
//            if (content == null || content.isBlank()) {
//                log.warn("[GPT-DIM] No content in OpenAI response");
//                return null;
//            }
//
//            JsonNode parsed = mapper.readTree(content);
//            float diameter = (float) parsed.path("diameterCm").asDouble(0);
//            float depth = (float) parsed.path("depthCm").asDouble(0);
//            String confidence = parsed.path("confidence").asText("low");
//            String reasoning = parsed.path("reasoning").asText("");
//
//            if (diameter <= 0 || depth <= 0) {
//                log.warn("[GPT-DIM] Invalid dimensions: d={}, depth={}. Reasoning: {}", diameter, depth, reasoning);
//                return null;
//            }
//
//            // Sanity bounds
//            diameter = Math.max(10f, Math.min(35f, diameter));
//            depth = Math.max(0.5f, Math.min(15f, depth));
//
//            log.info("[GPT-DIM] Estimated in {}ms: d={}, depth={}, conf={}, reasoning='{}'",
//                    elapsed, diameter, depth, confidence, reasoning);
//
//            return new float[] { diameter, depth };
//
//        } catch (Exception e) {
//            log.error("[GPT-DIM] Critical error during dimension estimation: {}", e.getMessage());
//            return null;
//        }
//    }
    
    private float[] estimateContainerDimensionsFromGpt(String imageBase64, String referenceObjectType,
            Float knownDiameterCm) {
        try {
            if (imageBase64 == null || imageBase64.isBlank()) {
                log.warn("[GPT-DIM] No image available for dimension estimation");
                return null;
            }

            String scaleContext = "";
            if (knownDiameterCm != null && knownDiameterCm > 0) {
                scaleContext += String.format(
                        "\nSCALE ANCHOR: We already measured the container's OUTER DIAMETER as %.1f cm. Use this as your primary scale reference to calculate the depth.",
                        knownDiameterCm);
            }

            if (referenceObjectType != null && !referenceObjectType.equalsIgnoreCase("NONE")) {
                scaleContext += "\nSYSTEM ALERT: Our local computer vision HAS ALREADY DETECTED a '"
                        + referenceObjectType
                        + "' in this specific image. It is definitely there. Search for it diligently and use it as your ground truth for scale.";
            }

            String prompt = "You are an expert in estimating real-world physical dimensions from photographs. "
                    + "Your task: estimate the INNER diameter and depth of the plate, bowl, or container in this image.\n\n"
                    + "CONTEXT: " + scaleContext + "\n\n"
                    + "STEP 1 — Identify the container (Plate/Bowl) and its type. You MUST classify it as FLAT_PLATE, REGULAR_BOWL, or DEEP_BOWL.\n\n"
                    + "STEP 2 — Use the scale reference. "
                    + "If a SYSTEM ALERT is mentioned above, the object IS in the image. Use its known length (Card=8.5cm, Syringe/Pen=16cm) to measure the container.\n\n"
                    + "STEP 3 — Cross-validate with container type norms:\n"
                    + "- Flat dinner plate: ~24-26cm diameter, 1-2cm depth\n"
                    + "- Small/Salad plate: ~18-20cm diameter, 1-2cm depth\n"
                    + "- Regular bowl: ~14-16cm diameter, 4-5cm depth\n"
                    + "- Deep bowl/Pot: ~14-16cm diameter, 6-10cm depth\n\n"
                    + "IMPORTANT: Even if the image is slightly blurry or dark, do NOT return 0 and do not complain. Always provide your best professional estimate. Trust the SYSTEM ALERT for the presence of the scale object.\n\n"
                    + "Reply ONLY with this JSON object:\n"
                    + "{\n"
                    + "  \"referenceObjectFound\": \"name of object used for scale\",\n"
                    + "  \"diameterCm\": <number>,\n"
                    + "  \"depthCm\": <number>,\n"
                    + "  \"containerType\": \"FLAT_PLATE\" | \"REGULAR_BOWL\" | \"DEEP_BOWL\",\n"
                    + "  \"confidence\": \"low\" | \"medium\" | \"high\",\n"
                    + "  \"reasoning\": \"Explain how you found the reference object and used it to measure the container.\"\n"
                    + "}";

            long startTime = System.currentTimeMillis();

            String content = geminiClient.callVisionModel(prompt, imageBase64);

            long elapsed = System.currentTimeMillis() - startTime;

            if (content == null || content.isBlank()) {
                log.warn("[GPT-DIM] Empty response from Gemini");
                return null;
            }

            String json = jsonParser.extractJsonObject(content);
            JsonNode parsed = new ObjectMapper().readTree(json);
            float diameter = (float) parsed.path("diameterCm").asDouble(0);
            float depth = (float) parsed.path("depthCm").asDouble(0);
            String confidence = parsed.path("confidence").asText("low");
            String reasoning = parsed.path("reasoning").asText("");

            if (diameter <= 0 || depth <= 0) {
                log.warn("[GPT-DIM] Invalid dimensions: d={}, depth={}. Reasoning: {}", diameter, depth, reasoning);
                return null;
            }

            diameter = Math.max(10f, Math.min(35f, diameter));
            depth = Math.max(0.5f, Math.min(15f, depth));

            log.info("[GPT-DIM] Estimated in {}ms: d={}, depth={}, conf={}, reasoning='{}'",
                    elapsed, diameter, depth, confidence, reasoning);

            return new float[] { diameter, depth };

        } catch (Exception e) {
            log.error("[GPT-DIM] Critical error during dimension estimation: {}", e.getMessage());
            return null;
        }
    }

}