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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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

    private final WebClient openAiWebClient;
    
    @Value("${openai.api.key:}")
    private String openAiApiKey;
    
    @Value("${spring.application.name}")
    private String systemId;

    private static final float DEFAULT_PORTION_WEIGHT = 150f;

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
            WebClient.Builder webClientBuilder) {
    	
        this.imageAnalysisService = imageAnalysisService;
        this.nutritionDataService = nutritionDataService;
        this.semanticMatchingService = semanticMatchingService;
        this.mealRepository = mealRepository;
        this.userRepository = userRepository;
        this.mealConverter = mealConverter;
        this.mealIdGenerator = mealIdGenerator;
        this.portionEstimator = portionEstimator;
        this.apiLogger = apiLogger;
        this.openAiWebClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .build();
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

        // Check if we have plate geometry for per-food physics calculation
        boolean hasPlateGeometry = request.getPlateDiameterCm() != null
                && request.getPlateDiameterCm() > 0;

        // Check if GPT provided coverage data for at least one food
        boolean hasCoverageData = visionResult.getDetectedFoods().stream()
                .anyMatch(f -> f.getCoveragePercent() != null && f.getCoveragePercent() > 0);

        // Pre-compute plate area if available (used per-food in the loop below)
        float plateAreaCm2 = 0f;
        float plateDepthCm = 0f;
        String resolvedContainerType = null;
        boolean isBowl = false;

        if (hasPlateGeometry) {
            float radiusCm = request.getPlateDiameterCm() / 2.0f;
            plateAreaCm2 = (float) (Math.PI * radiusCm * radiusCm);
            plateDepthCm = (request.getPlateDepthCm() != null && request.getPlateDepthCm() > 0)
                    ? request.getPlateDepthCm()
                    : 1.5f;

            resolvedContainerType = (visionResult.getDetectedContainerType() != null)
                    ? visionResult.getDetectedContainerType()
                    : (request.getContainerType() != null)
                            ? request.getContainerType()
                            : (plateDepthCm > 3.0f ? "DEEP_BOWL"
                                    : plateDepthCm > 1.5f ? "REGULAR_BOWL" : "FLAT_PLATE");

            isBowl = resolvedContainerType.toUpperCase().contains("BOWL");

            log.info("[v3] Plate geometry: d={}cm, depth={}cm, area={}cm², type={}, hasCoverage={}",
                    request.getPlateDiameterCm(), plateDepthCm,
                    String.format("%.1f", plateAreaCm2), resolvedContainerType, hasCoverageData);
        }

        // Fallback: distribute total weight when per-food physics not possible
        // Only computed if we actually need it (no plate geometry or no coverage data)
        Float totalWeightFallback = null;
        Map<String, Float> distributedPortions = null;

        if (!hasPlateGeometry || !hasCoverageData) {
            totalWeightFallback = estimatedWeightGrams;
            if (totalWeightFallback == null) {
                totalWeightFallback = (totalVisionPortions > 0)
                        ? totalVisionPortions
                        : estimateTotalWeightFromFoodTypes(portionItems);
            }
            distributedPortions = portionEstimator.distributePortions(
                    portionItems, totalWeightFallback);
            log.info("[v3-FALLBACK] Distributed total: {}g", totalWeightFallback);
        }

        // Step 3: Get nutrition data (INTEGRATING THE JUDGE + DENSITY)
        apiLogger.scanStep(3, "SEMANTIC NUTRITION MATCHING + DENSITY CALCULATION");
        List<MealEntity.FoodItem> foodItems = new ArrayList<>();
        float totalCarbs = 0f;
        int regionIndex = 0;

        for (FoodRecognitionResult.RecognizedFoodItem detected : visionResult.getDetectedFoods()) {

        	// --- USDA Matching (v3: GPT search terms → Judge) ---
            NutritionInfo finalNutrition = null;

            // A. Fetch Candidates — prefer GPT-provided search terms over hardcoded normalizer
            List<NutritionInfo> candidates;
            List<String> gptTerms = detected.getUsdaSearchTerms();

            if (gptTerms != null && !gptTerms.isEmpty()) {
                // Try each GPT term until we get results
                candidates = new ArrayList<>();
                for (String term : gptTerms) {
                    candidates = nutritionDataService.searchCandidates(term);
                    if (!candidates.isEmpty()) {
                        log.info("[v3] USDA hit on GPT term '{}' → {} candidates", term, candidates.size());
                        break;
                    }
                }
                // Last resort: fall back to baseIngredient if all GPT terms missed
                if (candidates.isEmpty()) {
                    log.warn("[v3] All GPT terms missed, falling back to baseIngredient: {}",
                            detected.getBaseIngredient());
                    candidates = nutritionDataService.searchCandidates(detected.getBaseIngredient());
                }
            } else {
                // No GPT terms available — use existing baseIngredient path
                candidates = nutritionDataService.searchCandidates(detected.getBaseIngredient());
            }

            if (!candidates.isEmpty()) {
                // B. The Judge Decides (Step 3 - Semantic Matching with image)
                String bestFdcId = semanticMatchingService.findBestMatch(
                        detected, candidates, request.getImageBase64());

             // C. Get Full Data for Winner
                if (bestFdcId != null) {
                    // First try to find it in the candidates we already have (saves an API call)
                    finalNutrition = candidates.stream()
                            .filter(c -> bestFdcId.equals(c.getFdcId()))
                            .findFirst()
                            .orElse(null);

                    // If not in candidates (shouldn't happen, but just in case), fetch by ID
                    if (finalNutrition == null) {
                        finalNutrition = nutritionDataService.getNutritionInfoById(bestFdcId);
                    }
                } else {
                    finalNutrition = candidates.get(0);
                }
            } else {
                finalNutrition = nutritionDataService.getNutritionInfo(detected.getName());
            }

            MealEntity.FoodItem item = new MealEntity.FoodItem();
            item.setName(detected.getName());
            item.setConfidence(NumberUtils.roundTo2Decimals(detected.getConfidence()));
            
            // pass GPT bounding box through to client
            item.setBboxXPct(detected.getBboxXPct());
            item.setBboxYPct(detected.getBboxYPct());
            item.setBboxWPct(detected.getBboxWPct());
            item.setBboxHPct(detected.getBboxHPct());
            
            // Add safety flags to the DB entity for future reference
            if (detected.getRiskFlags() != null && !detected.getRiskFlags().isEmpty()) {
                item.setNote("Risks: " + String.join(", ", detected.getRiskFlags()));
            }

            // ── v3: Per-Food Weight Calculation ──
            Float itemWeight;
            String weightSource;

            // PRIORITY 1: Client sent per-food region measurements (OpenCV + ARCore)
            if (hasPhysicsData && regionIndex < foodRegions.size()) {
                Map<String, Object> region = foodRegions.get(regionIndex);
                float areaCm2 = ((Number) region.getOrDefault("areaCm2", 0.0)).floatValue();
                float heightCm = ((Number) region.getOrDefault("heightCm", 1.0)).floatValue();
                float shapeFactor = getShapeFactor(heightCm);
                float bowlFactor = getBowlGeometryFactor(resolvedContainerType);
                float volumeCm3 = areaCm2 * heightCm * shapeFactor * bowlFactor;
                
                float density = resolveDensity(detected.getName(), finalNutrition);
                itemWeight = volumeCm3 * density;
                weightSource = String.format("CLIENT_PHYSICS(a=%.1f h=%.1f sf=%.2f bf=%.2f V=%.1f d=%.2f)",
                        areaCm2, heightCm, shapeFactor, bowlFactor, volumeCm3, density);

                log.info("[v3] {} → CLIENT region: {}cm² × {}cm × sf={} × bf={} = {}cm³ × d={} = {}g",
                        detected.getName(), areaCm2, heightCm, shapeFactor, bowlFactor,
                        String.format("%.1f", volumeCm3), density, String.format("%.1f", itemWeight));

            // PRIORITY 2: Plate geometry + GPT coverage (server-side physics)
            } else if (hasPlateGeometry && detected.getCoveragePercent() != null
                    && detected.getCoveragePercent() > 0) {

                // Area this food covers on the plate
                float foodAreaCm2 = plateAreaCm2 * (detected.getCoveragePercent() / 100.0f);

                // Height from GPT category, or ARCore depth as cap
                float heightCm = heightCategoryToCm(detected.getHeightCategory());

                // For bowls: cap height at measured bowl depth
                if (isBowl && heightCm > plateDepthCm) {
                    heightCm = plateDepthCm * 0.8f; // food rarely fills bowl completely
                }

                float shapeFactor = getShapeFactor(heightCm);
                float bowlFactor = getBowlGeometryFactor(resolvedContainerType);
                float volumeCm3 = foodAreaCm2 * heightCm * shapeFactor * bowlFactor;
                
                float density = resolveDensity(detected.getName(), finalNutrition);
                itemWeight = volumeCm3 * density;
                weightSource = String.format("GPT_PHYSICS(cov=%s%% a=%.1f h=%.1f[%s] sf=%.2f bf=%.2f V=%.1f d=%.2f)",
                        detected.getCoveragePercent(), foodAreaCm2, heightCm,
                        detected.getHeightCategory(), shapeFactor, bowlFactor, volumeCm3, density);

                log.info("[v3] {} → cov={}% area={}cm² × h={}cm({}) × sf={} × bf={} = {}cm³ × d={} = {}g",
                        detected.getName(), detected.getCoveragePercent(),
                        String.format("%.1f", foodAreaCm2), heightCm, detected.getHeightCategory(),
                        shapeFactor, bowlFactor, String.format("%.1f", volumeCm3), density,
                        String.format("%.1f", itemWeight));
                
            // PRIORITY 3: Distributed fallback (no geometry or no coverage)
            } else {
                itemWeight = (distributedPortions != null) ? distributedPortions.get(detected.getName()) : null;
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

        String method = hasPhysicsData ? "CLIENT_PHYSICS"
                : (hasPlateGeometry && hasCoverageData) ? "GPT_PHYSICS"
                : "DISTRIBUTED";
        log.info("Total carbs: {}g [method={}]", totalCarbs, method);
        
        // Step 3.5: Final sanity review (non-blocking, best-effort)
        List<String> reviewWarnings = runFinalReview(request.getImageBase64(), foodItems, totalCarbs);
        if (!reviewWarnings.isEmpty()) {
            apiLogger.scanStep(3, "FINAL REVIEW: " + reviewWarnings.size() + " warnings");
        }

     // Step 4: Skip insulin calc here — client handles it
        // (client has glucose, IOB, activity level, and unit preferences
        //  that aren't available at scan time)
        Float recommendedDose = null;
        boolean profileComplete = isProfileComplete(user);
        List<String> missingFields = new ArrayList<>();
        String insulinMessage = null;

        if (!profileComplete) {
            missingFields = getMissingProfileFields(user);
            insulinMessage = "Complete your medical profile to get insulin recommendations";
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
        
        // Attach review warnings if any
        if (!reviewWarnings.isEmpty()) {
            meal.setReviewWarnings(reviewWarnings);
        }
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
    
 // just checks if the user has the minimum fields for insulin calc
    private boolean isProfileComplete(UserEntity user) {
        if (user == null) return false;
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
        if (user.getInsulinCarbRatio() == null) missing.add("Insulin-to-Carb Ratio");
        if (user.getCorrectionFactor() == null) missing.add("Correction Factor (ISF)");
        if (user.getTargetGlucose() == null) missing.add("Target Glucose");
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
//    private float getContainerFillFactor(String containerType, float depthCm) {
//        if (containerType != null) {
//            switch (containerType.toUpperCase()) {
//                case "FLAT_PLATE":
//                    return 0.70f; // thin spread on plate
//                case "REGULAR_BOWL":
//                    return 0.55f; // typical bowl fill
//                case "DEEP_BOWL":
//                    return 0.50f; // deep bowls aren't filled to brim
//            }
//        }
//        // Infer from depth if no container type specified
//        if (depthCm <= 2.0f)
//            return 0.70f; // flat plate
//        if (depthCm <= 4.0f)
//            return 0.55f; // regular bowl
//        return 0.50f; // deep bowl
//    }

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
        if (containerType == null) return 1.0f;
        switch (containerType.toUpperCase()) {
            case "FLAT_PLATE":   return 1.0f;  // flat surface, no correction
            case "REGULAR_BOWL": return 0.65f;  // mid-depth curve
            case "DEEP_BOWL":    return 0.55f;  // steep paraboloid
            default:             return 1.0f;
        }
    }
    
    /**
     * Maps GPT height category to estimated height in cm.
     * Used when ARCore depth is not available.
     */
    private float heightCategoryToCm(String category) {
        if (category == null) return 1.5f; // safe default
        switch (category.toUpperCase()) {
            case "FLAT":        return 0.4f;
            case "LOW_PILE":    return 1.0f;
            case "MEDIUM_PILE": return 2.0f;
            case "HIGH_PILE":   return 4.0f;
            default:            return 1.5f;
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
     * Final sanity check — asks GPT to review the calculated results against the image.
     * Returns a list of warnings if something looks off. Does NOT change the results,
     * just flags issues for the client to show the user.
     */
    private List<String> runFinalReview(String base64Image,
                                        List<MealEntity.FoodItem> foodItems,
                                        float totalCarbs) {
        if (base64Image == null || base64Image.isBlank() || foodItems.isEmpty()) {
            return List.of();
        }

        try {
            // Build a summary of what we calculated
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

            Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
            Map<String, Object> imageUrlObj = Map.of("url", "data:image/jpeg;base64," + base64Image);
            Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlObj);

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(
                            Map.of("role", "user", "content", List.of(textContent, imageContent))),
                    "temperature", 0.0,
                    "max_tokens", 300);

            // Reuse the same OpenAI WebClient (needs injection — see 8B)
            String response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);
                com.fasterxml.jackson.databind.JsonNode choices = root.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    String content = choices.get(0).get("message").get("content").asText();
                    // Extract JSON
                    int start = content.indexOf('{');
                    int end = content.lastIndexOf('}');
                    if (start >= 0 && end > start) {
                        com.fasterxml.jackson.databind.JsonNode result = mapper.readTree(
                                content.substring(start, end + 1));
                        if (result.has("warnings") && result.get("warnings").isArray()) {
                            List<String> warnings = new ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode w : result.get("warnings")) {
                                warnings.add(w.asText());
                            }
                            if (!warnings.isEmpty()) {
                                log.warn("[FINAL_REVIEW] Issues found: {}", warnings);
                            }
                            return warnings;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Review is best-effort — never block the scan
            log.warn("[FINAL_REVIEW] Failed (non-blocking): {}", e.getMessage());
        }
        return List.of();
    }
}