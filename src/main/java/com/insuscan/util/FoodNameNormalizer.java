package com.insuscan.util;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Normalizes food names for better matching with nutrition databases.
 * Handles synonyms, common variations, and fuzzy matching.
 */
@Component
public class FoodNameNormalizer {

    private static final Map<String, String> FOOD_SYNONYMS = initFoodSynonyms();

    private static final Map<String, List<String>> SEARCH_TERMS = initSearchTerms();

    // Cleans up a raw food name and maps it to a canonical synonym when possible
    public String normalize(String foodName) {
        if (foodName == null || foodName.trim().isEmpty()) {
            return foodName;
        }

        String normalized = foodName.toLowerCase().trim();

        normalized = normalized.replaceAll("^(a |an |the |some |piece of |pieces of |cup of |cups of |bowl of |bowls of )", "");
        normalized = normalized.replaceAll("(, cooked|, raw|, fried|, grilled|, baked|, roasted|, steamed)$", "");
        normalized = normalized.replaceAll("\\s+", " ");

        if (FOOD_SYNONYMS.containsKey(normalized)) {
            return FOOD_SYNONYMS.get(normalized);
        }

        for (Map.Entry<String, String> entry : FOOD_SYNONYMS.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                return entry.getValue();
            }
        }

        return capitalizeWords(normalized);
    }

    // Returns the USDA search terms to try for a given normalized food name
    public List<String> getSearchTerms(String normalizedName) {
        String normalized = normalize(normalizedName);

        if (SEARCH_TERMS.containsKey(normalized.toLowerCase())) {
            return new ArrayList<>(SEARCH_TERMS.get(normalized.toLowerCase()));
        }

        List<String> terms = new ArrayList<>();
        terms.add(normalized);
        if (!normalizedName.equalsIgnoreCase(normalized)) {
            terms.add(normalizedName);
        }
        return terms;
    }

    // Checks if two food names likely refer to the same food (via synonyms or substring match)
    public boolean isLikelySameFood(String name1, String name2) {
        String norm1 = normalize(name1).toLowerCase();
        String norm2 = normalize(name2).toLowerCase();

        if (norm1.equals(norm2)) return true;
        if (norm1.contains(norm2) || norm2.contains(norm1)) return true;

        String syn1 = FOOD_SYNONYMS.getOrDefault(norm1, norm1);
        String syn2 = FOOD_SYNONYMS.getOrDefault(norm2, norm2);
        return syn1.equals(syn2);
    }

    private static String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return str;

        String[] words = str.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }

    private static Map<String, String> initFoodSynonyms() {
        Map<String, String> synonyms = new HashMap<>();

        // Pasta variations
        synonyms.put("pasta", "pasta");
        synonyms.put("spaghetti", "spaghetti");
        synonyms.put("spagetti", "spaghetti");
        synonyms.put("penne", "penne pasta");
        synonyms.put("macaroni", "macaroni");
        synonyms.put("fettuccine", "fettuccine");
        synonyms.put("linguine", "linguine");
        synonyms.put("lasagna", "lasagna");
        synonyms.put("ravioli", "ravioli");
        synonyms.put("noodles", "noodles");

        // Rice variations
        synonyms.put("rice", "rice");
        synonyms.put("white rice", "white rice");
        synonyms.put("brown rice", "brown rice");
        synonyms.put("jasmine rice", "jasmine rice");
        synonyms.put("basmati rice", "basmati rice");

        // Bread variations
        synonyms.put("bread", "bread");
        synonyms.put("white bread", "white bread");
        synonyms.put("whole wheat bread", "whole wheat bread");
        synonyms.put("toast", "bread");
        synonyms.put("sandwich", "bread");

        // Potato variations
        synonyms.put("potato", "potato");
        synonyms.put("potatoes", "potato");
        synonyms.put("mashed potatoes", "mashed potato");
        synonyms.put("french fries", "french fries");
        synonyms.put("fries", "french fries");
        synonyms.put("baked potato", "baked potato");

        // Chicken variations
        synonyms.put("chicken", "chicken");
        synonyms.put("chicken breast", "chicken breast");
        synonyms.put("chicken thigh", "chicken thigh");
        synonyms.put("chicken wing", "chicken wings");
        synonyms.put("chicken wings", "chicken wings");
        synonyms.put("fried chicken", "fried chicken");
        synonyms.put("grilled chicken", "grilled chicken");

        // Beef variations
        synonyms.put("beef", "beef");
        synonyms.put("ground beef", "ground beef");
        synonyms.put("steak", "beef steak");
        synonyms.put("hamburger", "ground beef");
        synonyms.put("burger", "ground beef");

        // Fish variations
        synonyms.put("fish", "fish");
        synonyms.put("salmon", "salmon");
        synonyms.put("tuna", "tuna");
        synonyms.put("cod", "cod");
        synonyms.put("tilapia", "tilapia");

        // Cheese variations
        synonyms.put("cheese", "cheese");
        synonyms.put("cheddar cheese", "cheddar cheese");
        synonyms.put("mozzarella", "mozzarella cheese");
        synonyms.put("parmesan", "parmesan cheese");
        synonyms.put("parmesan cheese", "parmesan cheese");
        synonyms.put("swiss cheese", "swiss cheese");

        // Vegetables
        synonyms.put("vegetables", "mixed vegetables");
        synonyms.put("veggies", "mixed vegetables");
        synonyms.put("salad", "salad");
        synonyms.put("lettuce", "lettuce");
        synonyms.put("tomato", "tomato");
        synonyms.put("tomatoes", "tomato");
        synonyms.put("carrot", "carrot");
        synonyms.put("carrots", "carrot");
        synonyms.put("broccoli", "broccoli");
        synonyms.put("spinach", "spinach");
        synonyms.put("onion", "onion");
        synonyms.put("onions", "onion");

        // Fruits
        synonyms.put("apple", "apple");
        synonyms.put("banana", "banana");
        synonyms.put("orange", "orange");
        synonyms.put("orange juice", "orange juice");
        synonyms.put("strawberry", "strawberry");
        synonyms.put("strawberries", "strawberry");
        synonyms.put("grapes", "grapes");

        // Sauces
        synonyms.put("sauce", "sauce");
        synonyms.put("marinara sauce", "marinara sauce");
        synonyms.put("tomato sauce", "tomato sauce");
        synonyms.put("pasta sauce", "marinara sauce");
        synonyms.put("alfredo sauce", "alfredo sauce");

        return synonyms;
    }

    private static Map<String, List<String>> initSearchTerms() {
        Map<String, List<String>> terms = new HashMap<>();

        terms.put("spaghetti", Arrays.asList("spaghetti", "pasta", "spaghetti cooked"));
        terms.put("pasta", Arrays.asList("pasta", "spaghetti", "macaroni"));
        terms.put("rice", Arrays.asList("rice", "white rice", "cooked rice"));
        terms.put("bread", Arrays.asList("bread", "white bread", "wheat bread"));
        terms.put("chicken", Arrays.asList("chicken", "chicken breast", "chicken meat"));
        terms.put("beef", Arrays.asList("beef", "ground beef", "beef meat"));
        terms.put("salmon", Arrays.asList("salmon", "salmon fillet", "salmon fish"));
        terms.put("cheese", Arrays.asList("cheese", "cheddar cheese", "dairy"));

        return terms;
    }
}