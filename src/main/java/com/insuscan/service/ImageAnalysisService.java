package com.insuscan.service;

import com.insuscan.boundary.FoodRecognitionResult;

/**
 * Analyzes food images through a provider-agnostic vision backend.
 */
public interface ImageAnalysisService {

    FoodRecognitionResult analyzeImage(String imageBase64, String referenceObjectType);

    FoodRecognitionResult analyzeImageFromUrl(String imageUrl, String referenceObjectType);

    boolean isServiceAvailable();
}