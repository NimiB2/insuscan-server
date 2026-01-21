package com.insuscan.controller;

import com.insuscan.boundary.InsulinCalculationBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.service.InsulinCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for insulin dose calculation endpoints
 */
@RestController
@RequestMapping(path = "/insulin")
@CrossOrigin(origins = "*")
@Tag(name = "Insulin Calculation", description = "Calculate insulin doses based on carbs and glucose levels")
public class InsulinController {

    private final InsulinCalculationService insulinCalculationService;

    @Value("${spring.application.name}")
    private String systemId;

    public InsulinController(InsulinCalculationService insulinCalculationService) {
        this.insulinCalculationService = insulinCalculationService;
    }

    /**
     * Calculate insulin dose from carbs
     * POST /insulin/calculate?totalCarbs=50&currentGlucose=150&email=user@example.com
     * 
     * @param totalCarbs Total carbohydrates in grams (required)
     * @param currentGlucose Current blood glucose in mg/dL (optional, for correction dose)
     * @param email User email (optional, for personalized settings)
     * @return Detailed insulin calculation result
     */
    @Operation(summary = "Calculate insulin dose", description = "Calculates insulin dose based on carbs and optional glucose correction. Works with or without user profile.")
    @PostMapping(
        path = "/calculate",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InsulinCalculationBoundary> calculateDose(
            @Parameter(description = "Total carbohydrates in grams (required)", required = true, example = "50")
            @RequestParam(value = "totalCarbs") Float totalCarbs,
            
            @Parameter(description = "Current blood glucose in mg/dL (optional, for correction dose)", example = "150")
            @RequestParam(value = "currentGlucose", required = false) Integer currentGlucose,
            
            @Parameter(description = "User email for personalized insulin settings (optional, uses defaults if not provided)", example = "test@example.com")
            @RequestParam(value = "email", required = false) String email) {
        
        UserIdBoundary userId = null;
        if (email != null && !email.isEmpty()) {
            userId = new UserIdBoundary();
            userId.setSystemId(systemId);
            userId.setEmail(email);
        }

        InsulinCalculationBoundary result = insulinCalculationService.calculateDose(
                totalCarbs,
                currentGlucose,
                userId);

        return ResponseEntity.ok(result);
    }

    /**
     * Calculate insulin dose from request body (JSON)
     * POST /insulin/calculate
     * Body: { "totalCarbs": 50, "currentGlucose": 150, "userId": { "email": "user@example.com" } }
     */
    @PostMapping(
        path = "/calculate",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InsulinCalculationBoundary> calculateDoseFromBody(
            @RequestBody InsulinCalculationBoundary request) {
        
        Integer currentGlucose = request.getCurrentGlucose() != null 
                ? request.getCurrentGlucose().intValue() 
                : null;
        
        InsulinCalculationBoundary result = insulinCalculationService.calculateDose(
                request.getTotalCarbs(),
                currentGlucose,
                request.getUserId());

        return ResponseEntity.ok(result);
    }
    
    /**
     * Calculate insulin dose with all adjustments
     * POST /insulin/calculate/full
     */
    @PostMapping(
        path = "/calculate/full",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Calculate insulin dose with adjustments",
        description = "Full calculation including sick day, stress, and exercise adjustments")
    public ResponseEntity<InsulinCalculationBoundary> calculateWithAdjustments(
            @RequestBody InsulinCalculationBoundary request) {
        
        // Extract userId if provided
        UserIdBoundary userId = request.getUserId();
        if (userId != null && userId.getSystemId() == null) {
            userId.setSystemId(systemId);
        }
        
        InsulinCalculationBoundary result = insulinCalculationService.calculateDoseWithAdjustments(
                request.getTotalCarbs(),
                request.getCurrentGlucose() != null ? request.getCurrentGlucose().intValue() : null,
                request.getActivityLevel(),
                request.getSickModeEnabled(),
                request.getStressModeEnabled(),
                userId
        );
        
        return ResponseEntity.ok(result);
    }
}
