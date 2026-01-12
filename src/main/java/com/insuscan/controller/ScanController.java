package com.insuscan.controller;

import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.ScanRequestBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.service.ScanService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(path = "/scan")
@CrossOrigin(origins = "*")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    // POST /scan - Full scan workflow
    // Takes image and returns meal with detected foods and carbs
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary scanMeal(@RequestBody ScanRequestBoundary request) {
        return scanService.scanMeal(request);
    }

    // POST /scan/with-portion - Scan with manual portion data
    @PostMapping(
        path = "/with-portion",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary scanMealWithPortion(
            @RequestBody ScanRequestBoundary request,
            @RequestParam(required = false) Float estimatedWeightGrams,
            @RequestParam(required = false) Float portionConfidence) {
        return scanService.scanMealWithPortion(request, estimatedWeightGrams, portionConfidence);
    }

    // POST /scan/upload - Scan by uploading an image file (multipart/form-data)
    // This is a convenience endpoint that wraps the existing JSON-based scan flow.
    @PostMapping(
        path = "/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public MealBoundary scanMealFromUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("systemId") String systemId,
            @RequestParam("email") String email,
            @RequestParam(value = "estimatedWeightGrams", required = false) Float estimatedWeightGrams,
            @RequestParam(value = "portionConfidence", required = false) Float portionConfidence
    ) throws IOException {

        byte[] bytes = file.getBytes();
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);

        ScanRequestBoundary request = new ScanRequestBoundary();
        request.setImageBase64(base64);

        UserIdBoundary userId = new UserIdBoundary();
        userId.setSystemId(systemId);
        userId.setEmail(email);
        request.setUserId(userId);

        return scanService.scanMealWithPortion(request, estimatedWeightGrams, portionConfidence);
    }
}