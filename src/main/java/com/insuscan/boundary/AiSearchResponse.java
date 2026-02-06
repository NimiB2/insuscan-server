package com.insuscan.boundary;

import java.util.List;

public class AiSearchResponse {
    
    private List<ScoredFoodResult> results;
    private SearchOptimization processingInfo;
    private boolean aiEnabled;
    
    // Constructors
    public AiSearchResponse() {
    }
    
    public AiSearchResponse(List<ScoredFoodResult> results, SearchOptimization processingInfo, boolean aiEnabled) {
        this.results = results;
        this.processingInfo = processingInfo;
        this.aiEnabled = aiEnabled;
    }
    
    // Getters and Setters
    public List<ScoredFoodResult> getResults() {
        return results;
    }
    
    public void setResults(List<ScoredFoodResult> results) {
        this.results = results;
    }
    
    public SearchOptimization getProcessingInfo() {
        return processingInfo;
    }
    
    public void setProcessingInfo(SearchOptimization processingInfo) {
        this.processingInfo = processingInfo;
    }
    
    public boolean isAiEnabled() {
        return aiEnabled;
    }
    
    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }
}
