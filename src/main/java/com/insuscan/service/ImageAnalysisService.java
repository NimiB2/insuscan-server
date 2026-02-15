package com.insuscan.service;

import com.insuscan.boundary.FoodRecognitionResult;

// Service for analyzing food images (provider-agnostic)
public interface ImageAnalysisService {

    // Analyze image from base64 string
    // @param referenceObjectType - The expected reference object (e.g. "Pen",
    // "Card", "Other")
    FoodRecognitionResult analyzeImage(String imageBase64, String referenceObjectType);

    // Analyze image from URL
    FoodRecognitionResult analyzeImageFromUrl(String imageUrl, String referenceObjectType);

    // Check if the underlying provider is configured and usable
    boolean isServiceAvailable();
}