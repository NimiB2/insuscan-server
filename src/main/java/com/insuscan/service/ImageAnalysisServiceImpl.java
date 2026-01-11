package com.insuscan.service;


import com.insuscan.boundary.VisionAnalysisResult;
import com.insuscan.boundary.VisionAnalysisResult.DetectedFood;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class ImageAnalysisServiceImpl implements ImageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ImageAnalysisServiceImpl.class);

    private final WebClient webClient;

    @Value("${insuscan.google.vision.api.key:}")
    private String apiKey;

    private static final String VISION_API_URL = 
        "https://vision.googleapis.com/v1/images:annotate";

    // Food-related labels to filter from Vision API
    private static final Set<String> FOOD_CATEGORIES = Set.of(
        "food", "dish", "meal", "cuisine", "ingredient", "produce",
        "fruit", "vegetable", "meat", "bread", "rice", "pasta",
        "salad", "soup", "dessert", "snack", "beverage", "drink"
    );

    public ImageAnalysisServiceImpl(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public VisionAnalysisResult analyzeImage(String imageBase64) {
        if (!isServiceAvailable()) {
            log.warn("Vision API key not configured, using mock data");
            return getMockResult();
        }

        try {
            // Build request body for Vision API
            Map<String, Object> request = buildVisionRequest(imageBase64, "BASE64");
            
            // Call Vision API
            Map response = webClient.post()
                .uri(VISION_API_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return parseVisionResponse(response);

        } catch (Exception e) {
            log.error("Vision API call failed: {}", e.getMessage());
            return VisionAnalysisResult.failure("Vision API error: " + e.getMessage());
        }
    }

    @Override
    public VisionAnalysisResult analyzeImageFromUrl(String imageUrl) {
        if (!isServiceAvailable()) {
            log.warn("Vision API key not configured, using mock data");
            return getMockResult();
        }

        try {
            Map<String, Object> request = buildVisionRequest(imageUrl, "URL");
            
            Map response = webClient.post()
                .uri(VISION_API_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return parseVisionResponse(response);

        } catch (Exception e) {
            log.error("Vision API call failed: {}", e.getMessage());
            return VisionAnalysisResult.failure("Vision API error: " + e.getMessage());
        }
    }

    @Override
    public boolean isServiceAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    // Build the JSON request for Vision API
    private Map<String, Object> buildVisionRequest(String imageData, String sourceType) {
        Map<String, Object> image = new HashMap<>();
        if ("BASE64".equals(sourceType)) {
            image.put("content", imageData);
        } else {
            Map<String, String> source = new HashMap<>();
            source.put("imageUri", imageData);
            image.put("source", source);
        }

        // Request label detection and object localization
        List<Map<String, Object>> features = List.of(
            Map.of("type", "LABEL_DETECTION", "maxResults", 20),
            Map.of("type", "OBJECT_LOCALIZATION", "maxResults", 10)
        );

        Map<String, Object> requestItem = new HashMap<>();
        requestItem.put("image", image);
        requestItem.put("features", features);

        return Map.of("requests", List.of(requestItem));
    }

    // Parse Vision API response and extract food items
    @SuppressWarnings("unchecked")
    private VisionAnalysisResult parseVisionResponse(Map response) {
        List<DetectedFood> foods = new ArrayList<>();

        try {
            List<Map> responses = (List<Map>) response.get("responses");
            if (responses == null || responses.isEmpty()) {
                return VisionAnalysisResult.failure("No response from Vision API");
            }

            Map firstResponse = responses.get(0);

            // Check for errors
            if (firstResponse.containsKey("error")) {
                Map error = (Map) firstResponse.get("error");
                return VisionAnalysisResult.failure((String) error.get("message"));
            }

            // Extract from label annotations
            List<Map> labels = (List<Map>) firstResponse.get("labelAnnotations");
            if (labels != null) {
                for (Map label : labels) {
                    String description = ((String) label.get("description")).toLowerCase();
                    float score = ((Number) label.get("score")).floatValue();
                    
                    // Filter for food-related labels
                    if (isFoodRelated(description) && score > 0.5f) {
                        foods.add(new DetectedFood(description, score));
                    }
                }
            }

            // Extract from object localization
            List<Map> objects = (List<Map>) firstResponse.get("localizedObjectAnnotations");
            if (objects != null) {
                for (Map obj : objects) {
                    String name = ((String) obj.get("name")).toLowerCase();
                    float score = ((Number) obj.get("score")).floatValue();
                    
                    if (isFoodRelated(name) && score > 0.5f) {
                        // Avoid duplicates
                        boolean exists = foods.stream()
                            .anyMatch(f -> f.getName().equalsIgnoreCase(name));
                        if (!exists) {
                            foods.add(new DetectedFood(name, score));
                        }
                    }
                }
            }

            if (foods.isEmpty()) {
                return VisionAnalysisResult.failure("No food items detected in image");
            }

            return VisionAnalysisResult.success(foods);

        } catch (Exception e) {
            log.error("Failed to parse Vision response: {}", e.getMessage());
            return VisionAnalysisResult.failure("Failed to parse Vision response");
        }
    }

    // Check if a label is food-related
    private boolean isFoodRelated(String label) {
        String lower = label.toLowerCase();
        
        // Direct match with food categories
        if (FOOD_CATEGORIES.contains(lower)) {
            return true;
        }
        
        // Check if label contains food category
        for (String category : FOOD_CATEGORIES) {
            if (lower.contains(category)) {
                return true;
            }
        }
        
        // Common specific foods (extend this list as needed)
        Set<String> specificFoods = Set.of(
            "apple", "banana", "orange", "chicken", "beef", "fish",
            "egg", "cheese", "milk", "butter", "pizza", "burger",
            "sandwich", "sushi", "noodle", "potato", "tomato", "carrot"
        );
        
        return specificFoods.contains(lower);
    }

    // Mock result for testing without API key
    private VisionAnalysisResult getMockResult() {
        List<DetectedFood> mockFoods = List.of(
            new DetectedFood("rice", 0.92f),
            new DetectedFood("chicken", 0.88f),
            new DetectedFood("vegetables", 0.75f)
        );
        return VisionAnalysisResult.success(mockFoods);
    }
}