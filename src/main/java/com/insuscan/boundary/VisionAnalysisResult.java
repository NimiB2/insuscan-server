package com.insuscan.boundary;

import java.util.List;

//Result from Google Cloud Vision food detection
public class VisionAnalysisResult {
 private List<DetectedFood> detectedFoods;
 private boolean success;
 private String errorMessage;

 public VisionAnalysisResult() {}

 // Detected food item from Vision API
 public static class DetectedFood {
     private String name;           // english name from Vision
     private float confidence;      // 0.0 to 1.0
     private String boundingBox;    // optional: location in image

     public DetectedFood() {}

     public DetectedFood(String name, float confidence) {
         this.name = name;
         this.confidence = confidence;
     }

     // Getters and Setters
     public String getName() { return name; }
     public void setName(String name) { this.name = name; }
     
     public float getConfidence() { return confidence; }
     public void setConfidence(float confidence) { this.confidence = confidence; }
     
     public String getBoundingBox() { return boundingBox; }
     public void setBoundingBox(String boundingBox) { this.boundingBox = boundingBox; }
 }

 // Getters and Setters
 public List<DetectedFood> getDetectedFoods() { return detectedFoods; }
 public void setDetectedFoods(List<DetectedFood> detectedFoods) { this.detectedFoods = detectedFoods; }
 
 public boolean isSuccess() { return success; }
 public void setSuccess(boolean success) { this.success = success; }
 
 public String getErrorMessage() { return errorMessage; }
 public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

 // Factory methods for quick creation
 public static VisionAnalysisResult success(List<DetectedFood> foods) {
     VisionAnalysisResult result = new VisionAnalysisResult();
     result.setSuccess(true);
     result.setDetectedFoods(foods);
     return result;
 }

 public static VisionAnalysisResult failure(String error) {
     VisionAnalysisResult result = new VisionAnalysisResult();
     result.setSuccess(false);
     result.setErrorMessage(error);
     return result;
 }
}
