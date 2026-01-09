package com.insuscan.service;

import com.insuscan.boundary.VisionAnalysisResult;

//Service for analyzing food images using Google Cloud Vision
public interface ImageAnalysisService {

 // Analyze image from base64 string
 VisionAnalysisResult analyzeImage(String imageBase64);

 // Analyze image from URL
 VisionAnalysisResult analyzeImageFromUrl(String imageUrl);

 // Check if service is available
 boolean isServiceAvailable();
}
