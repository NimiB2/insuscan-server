package com.insuscan.service;

import com.insuscan.boundary.FoodRecognitionResult;

// Service for analyzing food images (provider-agnostic)
public interface ImageAnalysisService {

    // Analyze image from base64 string
    FoodRecognitionResult analyzeImage(String imageBase64);

    // Analyze image from URL
    FoodRecognitionResult analyzeImageFromUrl(String imageUrl);

    // Check if the underlying provider is configured and usable
    boolean isServiceAvailable();
}