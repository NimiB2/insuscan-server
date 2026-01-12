package com.insuscan.util;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Estimates portion sizes based on food type, confidence scores, and typical serving sizes.
 */
@Component
public class PortionEstimator {

    // Typical portion sizes in grams for different food categories
    private static final Map<String, PortionRange> FOOD_PORTION_RANGES = initPortionRanges();
    
    // Food category mapping
    private static final Map<String, String> FOOD_CATEGORIES = initFoodCategories();

    /**
     * Estimate portion size for a food item based on type and confidence
     * 
     * @param foodName Normalized food name
     * @param confidence Detection confidence (0.0 to 1.0)
     * @param visionEstimate Optional vision-provided estimate
     * @return Estimated portion in grams
     */
    public float estimatePortion(String foodName, float confidence, Float visionEstimate) {
        // If vision provided estimate, use it (with confidence adjustment)
        if (visionEstimate != null && visionEstimate > 0) {
            // Adjust vision estimate based on confidence
            // Higher confidence = trust vision more, lower confidence = adjust toward typical
            float adjustedEstimate = adjustVisionEstimate(visionEstimate, confidence, foodName);
            return adjustedEstimate;
        }
        
        // Otherwise, estimate based on food type and confidence
        return estimateFromFoodType(foodName, confidence);
    }

    /**
     * Adjust vision estimate based on confidence and food type
     */
    private float adjustVisionEstimate(float visionEstimate, float confidence, String foodName) {
        String category = getFoodCategory(foodName);
        PortionRange typicalRange = FOOD_PORTION_RANGES.getOrDefault(category, 
                FOOD_PORTION_RANGES.get("other"));
        
        // If confidence is high (>= 0.8), trust vision estimate more
        if (confidence >= 0.8) {
            // Still validate it's within reasonable range
            if (visionEstimate >= typicalRange.min * 0.5 && visionEstimate <= typicalRange.max * 2.0) {
                return visionEstimate;
            }
            // If outside reasonable range, adjust toward typical
            return Math.max(typicalRange.min, Math.min(typicalRange.max, visionEstimate));
        }
        
        // Medium confidence (0.6-0.8): blend vision with typical
        if (confidence >= 0.6) {
            float typicalPortion = (typicalRange.min + typicalRange.max) / 2f;
            float blendFactor = (confidence - 0.6f) / 0.2f; // 0.0 to 1.0
            return visionEstimate * blendFactor + typicalPortion * (1 - blendFactor);
        }
        
        // Low confidence (< 0.6): use typical portion
        return (typicalRange.min + typicalRange.max) / 2f;
    }

    /**
     * Estimate portion from food type when no vision estimate available
     */
    private float estimateFromFoodType(String foodName, float confidence) {
        String category = getFoodCategory(foodName);
        PortionRange range = FOOD_PORTION_RANGES.getOrDefault(category, 
                FOOD_PORTION_RANGES.get("other"));
        
        // Use confidence to determine where in the range
        // Higher confidence = use upper range (more food visible)
        // Lower confidence = use lower range (less certain)
        float rangeSize = range.max - range.min;
        float offset = rangeSize * confidence;
        
        return range.min + offset;
    }

    /**
     * Get food category for portion estimation
     */
    private String getFoodCategory(String foodName) {
        String nameLower = foodName.toLowerCase();
        
        // Check direct mapping
        if (FOOD_CATEGORIES.containsKey(nameLower)) {
            return FOOD_CATEGORIES.get(nameLower);
        }
        
        // Check partial matches
        for (Map.Entry<String, String> entry : FOOD_CATEGORIES.entrySet()) {
            if (nameLower.contains(entry.getKey()) || entry.getKey().contains(nameLower)) {
                return entry.getValue();
            }
        }
        
        return "other";
    }

    /**
     * Distribute total weight among food items based on confidence and type
     * Uses deterministic approach: prioritizes food-type typical portions over vision estimates
     * to ensure consistency across multiple scans of the same image.
     * 
     * @param foods List of food items with names and confidences
     * @param totalWeight Total weight to distribute
     * @return Map of food name to portion weight
     */
    public Map<String, Float> distributePortions(List<FoodItem> foods, float totalWeight) {
        if (foods.isEmpty()) {
            return new HashMap<>();
        }
        
        // Calculate weights for each food based on confidence and typical portion
        // Use typical portions as primary factor for consistency
        List<PortionWeight> weights = new ArrayList<>();
        float totalWeightScore = 0f;
        
        for (FoodItem food : foods) {
            String category = getFoodCategory(food.name);
            PortionRange range = FOOD_PORTION_RANGES.getOrDefault(category, 
                    FOOD_PORTION_RANGES.get("other"));
            
            // Use typical portion as base (more deterministic than vision estimates)
            // Confidence only slightly adjusts the portion
            float typicalPortion = (range.min + range.max) / 2f;
            
            // Weight = typical portion * (0.8 + 0.2 * confidence)
            // This ensures consistency: typical portion is 80% of weight, confidence adds up to 20%
            float confidenceFactor = 0.8f + (0.2f * food.confidence);
            float weightScore = typicalPortion * confidenceFactor;
            
            weights.add(new PortionWeight(food.name, weightScore, food.visionEstimate));
            totalWeightScore += weightScore;
        }
        
        // Distribute total weight proportionally
        Map<String, Float> portions = new HashMap<>();
        if (totalWeightScore > 0) {
            for (PortionWeight pw : weights) {
                float portion = (pw.weightScore / totalWeightScore) * totalWeight;
                
                // If vision provided estimate, blend it (but give more weight to typical portion)
                // This reduces variance from non-deterministic vision API
                if (pw.visionEstimate != null && pw.visionEstimate > 0) {
                    // 70% typical-based portion, 30% vision estimate
                    portion = (portion * 0.7f) + (pw.visionEstimate * 0.3f);
                }
                
                portions.put(pw.foodName, portion);
            }
        } else {
            // Fallback: equal distribution
            float equalPortion = totalWeight / foods.size();
            for (FoodItem food : foods) {
                portions.put(food.name, equalPortion);
            }
        }
        
        return portions;
    }

    // Helper class for food item info
    public static class FoodItem {
        public final String name;
        public final float confidence;
        public final Float visionEstimate;
        
        public FoodItem(String name, float confidence, Float visionEstimate) {
            this.name = name;
            this.confidence = confidence;
            this.visionEstimate = visionEstimate;
        }
    }
    
    private static class PortionWeight {
        final String foodName;
        final float weightScore;
        final Float visionEstimate;
        
        PortionWeight(String foodName, float weightScore, Float visionEstimate) {
            this.foodName = foodName;
            this.weightScore = weightScore;
            this.visionEstimate = visionEstimate;
        }
    }
    
    private static class PortionRange {
        final float min;
        final float max;
        
        PortionRange(float min, float max) {
            this.min = min;
            this.max = max;
        }
    }

    private static Map<String, PortionRange> initPortionRanges() {
        Map<String, PortionRange> ranges = new HashMap<>();
        
        // Grains & Starches (larger portions)
        ranges.put("grain", new PortionRange(100f, 200f));
        ranges.put("pasta", new PortionRange(120f, 250f));
        ranges.put("rice", new PortionRange(100f, 200f));
        ranges.put("bread", new PortionRange(30f, 100f));
        ranges.put("potato", new PortionRange(150f, 300f));
        
        // Proteins (medium portions)
        ranges.put("protein", new PortionRange(100f, 200f));
        ranges.put("chicken", new PortionRange(100f, 200f));
        ranges.put("beef", new PortionRange(100f, 200f));
        ranges.put("fish", new PortionRange(100f, 200f));
        ranges.put("pork", new PortionRange(100f, 200f));
        
        // Vegetables (smaller portions)
        ranges.put("vegetable", new PortionRange(50f, 150f));
        ranges.put("salad", new PortionRange(50f, 150f));
        
        // Fruits (small to medium)
        ranges.put("fruit", new PortionRange(80f, 200f));
        
        // Dairy & Cheese (small portions)
        ranges.put("cheese", new PortionRange(20f, 80f));
        ranges.put("dairy", new PortionRange(50f, 150f));
        
        // Sauces (small portions)
        ranges.put("sauce", new PortionRange(30f, 120f));
        
        // Default/Other
        ranges.put("other", new PortionRange(50f, 150f));
        
        return ranges;
    }

    private static Map<String, String> initFoodCategories() {
        Map<String, String> categories = new HashMap<>();
        
        // Grains
        categories.put("pasta", "pasta");
        categories.put("spaghetti", "pasta");
        categories.put("penne", "pasta");
        categories.put("macaroni", "pasta");
        categories.put("noodles", "pasta");
        categories.put("rice", "rice");
        categories.put("white rice", "rice");
        categories.put("brown rice", "rice");
        categories.put("bread", "bread");
        categories.put("toast", "bread");
        categories.put("potato", "potato");
        categories.put("potatoes", "potato");
        categories.put("fries", "potato");
        categories.put("french fries", "potato");
        
        // Proteins
        categories.put("chicken", "chicken");
        categories.put("chicken breast", "chicken");
        categories.put("chicken wing", "chicken");
        categories.put("chicken wings", "chicken");
        categories.put("beef", "beef");
        categories.put("steak", "beef");
        categories.put("ground beef", "beef");
        categories.put("fish", "fish");
        categories.put("salmon", "fish");
        categories.put("tuna", "fish");
        categories.put("cod", "fish");
        categories.put("pork", "pork");
        categories.put("egg", "protein");
        categories.put("eggs", "protein");
        categories.put("tofu", "protein");
        
        // Vegetables
        categories.put("vegetables", "vegetable");
        categories.put("salad", "salad");
        categories.put("lettuce", "vegetable");
        categories.put("tomato", "vegetable");
        categories.put("carrot", "vegetable");
        categories.put("broccoli", "vegetable");
        categories.put("spinach", "vegetable");
        categories.put("onion", "vegetable");
        
        // Fruits
        categories.put("apple", "fruit");
        categories.put("banana", "fruit");
        categories.put("orange", "fruit");
        categories.put("strawberry", "fruit");
        categories.put("grapes", "fruit");
        
        // Dairy
        categories.put("cheese", "cheese");
        categories.put("cheddar", "cheese");
        categories.put("mozzarella", "cheese");
        categories.put("parmesan", "cheese");
        categories.put("milk", "dairy");
        categories.put("yogurt", "dairy");
        
        // Sauces
        categories.put("sauce", "sauce");
        categories.put("marinara", "sauce");
        categories.put("tomato sauce", "sauce");
        categories.put("alfredo", "sauce");
        
        return categories;
    }
}
