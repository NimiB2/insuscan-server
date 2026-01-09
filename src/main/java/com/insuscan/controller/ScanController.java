package com.insuscan.controller;

import com.insuscan.boundary.MealBoundary;
import com.insuscan.boundary.ScanRequestBoundary;
import com.insuscan.service.ScanService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

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
}