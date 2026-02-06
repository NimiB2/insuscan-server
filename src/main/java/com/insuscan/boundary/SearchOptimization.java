package com.insuscan.boundary;

public class SearchOptimization {
    
    private String translatedQuery;
    private String[] searchVariations;
    private String[] excludeTerms;
    private String intent;
    
    // Constructors
    public SearchOptimization() {
    }
    
    public SearchOptimization(String translatedQuery, String[] searchVariations, 
                            String[] excludeTerms, String intent) {
        this.translatedQuery = translatedQuery;
        this.searchVariations = searchVariations;
        this.excludeTerms = excludeTerms;
        this.intent = intent;
    }
    
    // Getters and Setters
    public String getTranslatedQuery() {
        return translatedQuery;
    }
    
    public void setTranslatedQuery(String translatedQuery) {
        this.translatedQuery = translatedQuery;
    }
    
    public String[] getSearchVariations() {
        return searchVariations;
    }
    
    public void setSearchVariations(String[] searchVariations) {
        this.searchVariations = searchVariations;
    }
    
    public String[] getExcludeTerms() {
        return excludeTerms;
    }
    
    public void setExcludeTerms(String[] excludeTerms) {
        this.excludeTerms = excludeTerms;
    }
    
    public String getIntent() {
        return intent;
    }
    
    public void setIntent(String intent) {
        this.intent = intent;
    }
}
