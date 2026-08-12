package com.insuscan.controller;

import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.InsulinCalculationBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.converter.MealConverter;
import com.insuscan.crud.MealRepository;
import com.insuscan.crud.UserRepository;
import com.insuscan.data.MealEntity;
import com.insuscan.data.UserEntity;
import com.insuscan.enums.MealStatus;
import com.insuscan.pipeline.FoodEstimationPipeline;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.model.PipelineResult;
import com.insuscan.service.DoseGateDecision;
import com.insuscan.service.DoseGateEvaluator;
import com.insuscan.service.InsulinCalculationService;
import com.insuscan.service.MealService;
import com.insuscan.util.MealIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import com.insuscan.boundary.ArcoreDepthData;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * New scan endpoint for the dual-image pipeline (Milestone 7).
 * Path: POST /vision/v2/scan
 */
@RestController
@RequestMapping(path = "/vision/v2")
@CrossOrigin(origins = "*")
@Tag(name = "Vision v2 — Dual Image Pipeline", description = "Precision food estimation using top + side images with reference object")
public class ScanPipelineController {

    private static final Logger log = LoggerFactory.getLogger(ScanPipelineController.class);

    /** Number of times a fresh meal id is generated when the previous one is already taken. */
    private static final int MEAL_ID_MAX_ATTEMPTS = 5;
    
    private final FoodEstimationPipeline pipeline;
    private final UserRepository userRepository;
    private final MealRepository mealRepository;
    private final MealIdGenerator mealIdGenerator;
    private final InsulinCalculationService insulinCalculationService;
    private final MealConverter mealConverter;
    private final MealService mealService;
    private final ObjectMapper objectMapper;
    private final DoseGateEvaluator doseGateEvaluator;

    @Value("${spring.application.name}")
    private String systemId;

    public ScanPipelineController(
            FoodEstimationPipeline pipeline,
            UserRepository userRepository,
            MealRepository mealRepository,
            MealIdGenerator mealIdGenerator,
            InsulinCalculationService insulinCalculationService,
            MealConverter mealConverter,
            MealService mealService,
            ObjectMapper objectMapper,
            DoseGateEvaluator doseGateEvaluator) {

        this.pipeline = pipeline;
        this.userRepository = userRepository;
        this.mealRepository = mealRepository;
        this.mealIdGenerator = mealIdGenerator;
        this.insulinCalculationService = insulinCalculationService;
        this.mealConverter = mealConverter;
        this.mealService = mealService;
        this.objectMapper = objectMapper;
        this.doseGateEvaluator = doseGateEvaluator;
    }

    @Operation(
        summary = "Precision meal scan (v2)",
        description = "Analyzes a meal using two images, calculates physics-based macros, saves to DB, and computes insulin."
    )
    @PostMapping(
        path = "/scan",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> scan(
        @RequestPart("topFile")  MultipartFile topFile,
        @RequestPart("sideFile") MultipartFile sideFile,
        @RequestParam("referenceObjectType") String referenceObjectType,
        @RequestParam(value = "email", required = false, defaultValue = "anonymous") String email,
        @RequestParam(value = "arcoreData", required = false) String arcoreDataJson,
        @RequestParam(value = "topImageWidth",   required = false) Integer topImageWidth,
        @RequestParam(value = "topImageHeight",  required = false) Integer topImageHeight,
        @RequestParam(value = "sideImageWidth",  required = false) Integer sideImageWidth,
        @RequestParam(value = "sideImageHeight", required = false) Integer sideImageHeight
        ) throws IOException {

        if (topFile == null || topFile.isEmpty()) {
            return ResponseEntity.badRequest().body("topFile is required");
        }
        if (sideFile == null || sideFile.isEmpty()) {
            return ResponseEntity.badRequest().body("sideFile is required");
        }
        if (referenceObjectType == null || referenceObjectType.isBlank()) {
            return ResponseEntity.badRequest().body("referenceObjectType is required");
        }

        log.info("[ScanV2] Request from user={}, refType={}", email, referenceObjectType);

        PipelineContext ctx = new PipelineContext();
        ctx.setImageTopBase64(Base64.getEncoder().encodeToString(topFile.getBytes()));
        ctx.setImageSideBase64(Base64.getEncoder().encodeToString(sideFile.getBytes()));
        ctx.setReferenceObjectType(referenceObjectType.toUpperCase());
        ctx.setUserId(email);
        ctx.setSystemId(systemId);
        ctx.setImageTopWidthPx(topImageWidth);
        ctx.setImageTopHeightPx(topImageHeight);
        ctx.setImageSideWidthPx(sideImageWidth);
        ctx.setImageSideHeightPx(sideImageHeight);

        if (arcoreDataJson != null && !arcoreDataJson.isBlank()) {
            try {
                ArcoreDepthData arcoreDepthData = objectMapper.readValue(arcoreDataJson, ArcoreDepthData.class);
                ctx.setArcoreDepthData(arcoreDepthData);
                log.info("[ScanV2] ARCore data received: plateDepth={}m, confidence={}, items={}",
                        arcoreDepthData.getPlateDepthM(),
                        arcoreDepthData.getArConfidence(),
                        arcoreDepthData.getItemDepthsM() != null ? arcoreDepthData.getItemDepthsM().size() : 0);
            } catch (Exception e) {
                log.warn("[ScanV2] Failed to parse arcoreData JSON, continuing without it: {}", e.getMessage());
            }
        }

        PipelineResult result = pipeline.run(ctx);

        if (!result.isSuccess()) {
            return ResponseEntity.unprocessableEntity().body(result);
        }

        String userDocId = systemId + "_" + email;
        String newId = mealIdGenerator.generateMealId();
        
        MealEntity meal = new MealEntity();
        meal.setId(systemId + "_" + newId);
        meal.setUserId(userDocId);
        meal.setImageUrl("uploaded://" + newId + ".jpg");
        meal.setScannedAt(new Date());
        meal.setStatus(MealStatus.PENDING);
        meal.setReferenceObjectType(referenceObjectType);

        if (ctx.getMealTotals() != null) {
            meal.setTotalCarbs(ctx.getMealTotals().getTotalNetCarbsG());
            meal.setEstimatedWeight(ctx.getMealTotals().getTotalWeightG());
        }

        if (ctx.getPlateGeometry() != null) {
            meal.setPlateDiameterCm(ctx.getPlateGeometry().getInnerDiameterCm());
            meal.setPlateDepthCm(ctx.getPlateGeometry().getInnerDepthCm());
            meal.setContainerType(ctx.getPlateGeometry().getContainerType());
            meal.setReferenceDetected(true);

            float radius = meal.getPlateDiameterCm() / 2f;
            meal.setPlateVolumeCm3((float) (Math.PI * radius * radius * meal.getPlateDepthCm()));
        }

        meal.setAnalysisConfidence(result.getOverallConfidence());

        List<MealEntity.FoodItem> foodItems = new ArrayList<>();
        if (ctx.getFoodItems() != null) {
            for (PipelineFoodItem pi : ctx.getFoodItems()) {
                MealEntity.FoodItem item = new MealEntity.FoodItem();
                item.setName(pi.getName());
                item.setQuantity(pi.getWeightG());
                item.setCarbs(pi.getNetCarbsG());
                item.setConfidence(pi.getDetectionConfidence());

                if (pi.getBoundingBoxPct() != null && pi.getBoundingBoxPct().length == 4) {
                    item.setBboxXPct(pi.getBoundingBoxPct()[0]);
                    item.setBboxYPct(pi.getBoundingBoxPct()[1]);
                    item.setBboxWPct(pi.getBoundingBoxPct()[2]);
                    item.setBboxHPct(pi.getBoundingBoxPct()[3]);
                }
                foodItems.add(item);
            }
        }
        meal.setFoodItems(foodItems);

        if (result.getWarnings() != null && !result.getWarnings().isEmpty()) {
            List<String> textWarnings = result.getWarnings().stream()
                .map(w -> "[" + w.getCode() + "] " + w.getMessage())
                .collect(Collectors.toList());
            meal.setReviewWarnings(textWarnings);
        }

        Float gateWeight = ctx.getMealTotals() != null ? ctx.getMealTotals().getTotalWeightG() : null;
        Float gateCarbs  = ctx.getMealTotals() != null ? ctx.getMealTotals().getTotalNetCarbsG() : null;
        DoseGateDecision gateDecision = doseGateEvaluator.evaluate(result.getWarnings(), gateWeight, gateCarbs);

        UserEntity user = userRepository.findById(userDocId).orElse(null);
        if (gateDecision.blocked()) {
            meal.setRecommendedDose(null);
            meal.setCarbDose(null);
            meal.setCorrectionDose(null);
            meal.setRequiresManualReview(true);
            meal.setInsulinMessage("Dose recommendation withheld — please review manually. Reason: "
                    + gateDecision.summary());
            log.warn("[ScanV2] Dose recommendation blocked for meal {}: {}", meal.getId(), gateDecision.summary());
        } else if (user != null) {
            try {
                UserIdBoundary userIdBoundary = new UserIdBoundary();
                userIdBoundary.setSystemId(systemId);
                userIdBoundary.setEmail(email);

                InsulinCalculationBoundary calc = insulinCalculationService.calculateDose(
                    meal.getTotalCarbs() != null ? meal.getTotalCarbs() : 0f,
                    null,
                    userIdBoundary
                );

                meal.setRecommendedDose(calc.getTotalRecommendedDose());
                meal.setCarbDose(calc.getCarbDose());
                meal.setCorrectionDose(calc.getCorrectionDose());
                meal.setInsulinMessage(calc.getMessage());
                meal.setProfileComplete(calc.isProfileComplete());
                meal.setMissingProfileFields(calc.getMissingFields());
            } catch (Exception e) {
                log.error("[ScanV2] Failed to calculate insulin: {}", e.getMessage());
                meal.setInsulinMessage("Error calculating insulin: " + e.getMessage());
            }
        } else {
            log.warn("[ScanV2] User not found ({}), saving meal without profile/insulin", userDocId);
            meal.setProfileComplete(false);
            meal.setInsulinMessage("User profile not found. Please log in.");
        }

        if (!persistNewMeal(meal)) {
            log.error("[ScanV2] Could not allocate a free meal id after {} attempts", MEAL_ID_MAX_ATTEMPTS);
            return ResponseEntity.internalServerError().body("Could not save the scanned meal. Please try again.");
        }
        log.info("[ScanV2] Saved MealEntity (id={}) with totalCarbs={}, estimatedWeight={}",
            meal.getId(), meal.getTotalCarbs(), meal.getEstimatedWeight());

        MealBoundary boundary = mealConverter.toBoundary(meal);
        return ResponseEntity.ok(boundary);
    }

    @GetMapping(path = "/saved", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MealBoundary> getSavedAnalyses(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {

        String userEmail = email;
        if (userEmail == null || userEmail.isEmpty()) {
            if (userId != null && userId.contains("@")) {
                userEmail = userId;
            } else if (userId != null && !userId.isEmpty()) {
                userEmail = userId + "@example.com";
            } else {
                userEmail = "test@example.com";
            }
        }
        return mealService.getRecentMeals(systemId, userEmail, limit);
    }
    
    private boolean persistNewMeal(MealEntity meal) {
        for (int attempt = 1; attempt <= MEAL_ID_MAX_ATTEMPTS; attempt++) {
            if (mealRepository.createIfAbsent(meal)) {
                return true;
            }
            String retryId = systemId + "_" + mealIdGenerator.generateMealId();
            log.warn("[ScanV2] Meal id {} was taken, retrying with {} (attempt {}/{})",
                    meal.getId(), retryId, attempt, MEAL_ID_MAX_ATTEMPTS);
            meal.setId(retryId);
        }
        return false;
    }
    

    @GetMapping(path = "/saved/{mealId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary getSavedAnalysis(@PathVariable("mealId") String mealId) {
        String uuid = mealId;
        if (mealId.contains("_")) {
            uuid = mealId.substring(mealId.lastIndexOf("_") + 1);
        }
        return mealService.getMealById(systemId, uuid)
                .orElseThrow(() -> new RuntimeException("Meal not found: " + mealId));
    }

    @GetMapping(path = "/test", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> getTestPage() {
        Resource resource = new ClassPathResource("static/test-vision.html");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(resource);
    }
}