package com.insuscan.service;

import com.insuscan.boundary.NutritionInfo;
import com.insuscan.boundary.ScoredFoodResult;
import com.insuscan.boundary.SearchOptimization;

import java.util.List;

/**
 * AI-powered food search service using OpenAI.
 * Provides intelligent query optimization and result ranking for USDA food search.
 */
public interface FoodSearchAiService {
    
    /**
     * Optimizes a user's search query using AI.
     * Translates from Hebrew to English if needed, generates search variations,
     * and identifies terms to exclude.
     * 
     * @param query The original search query (Hebrew or English)
     * @param language The language code ("he" or "en")
     * @return SearchOptimization containing translated query, variations, and filters
     */
    SearchOptimization optimizeQuery(String query, String language);
    
    /**
     * Ranks and scores USDA search results based on relevance to the original query.
     * Uses AI to assign relevance scores (0-100) and match reasons.
     * 
     * @param originalQuery The user's original search query
     * @param results The raw USDA search results to rank
     * @return List of ScoredFoodResult objects sorted by relevance (highest first)
     */
    List<ScoredFoodResult> rankAndScoreResults(String originalQuery, List<NutritionInfo> results);
    
    /**
     * Checks if the AI service is available (OpenAI API key configured).
     * 
     * @return true if OpenAI is available, false otherwise
     */
    boolean isServiceAvailable();
}
