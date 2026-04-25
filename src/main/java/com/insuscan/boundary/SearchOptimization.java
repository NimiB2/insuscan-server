package com.insuscan.boundary;

public class SearchOptimization {

    private String translatedQuery;
    private String[] searchVariations;
    private String[] excludeTerms;
    private String intent;

    /**
     * USDA-style preparation keywords describing how the food is prepared.
     * Examples: ["cooked"], ["raw"], ["fried"], ["roasted"], ["canned"], ["dried"]
     * Used to:
     *  1. Build precise primary USDA search terms (e.g. "white rice" + "cooked" → "white rice cooked")
     *  2. Prefer/penalize candidates in pickBestCandidate() based on preparation style
     */
    private String[] preparationKeywords;

    public SearchOptimization() {
    }

    public SearchOptimization(String translatedQuery, String[] searchVariations,
                              String[] excludeTerms, String intent) {
        this.translatedQuery = translatedQuery;
        this.searchVariations = searchVariations;
        this.excludeTerms = excludeTerms;
        this.intent = intent;
    }

    public String getTranslatedQuery() { return translatedQuery; }
    public void setTranslatedQuery(String translatedQuery) { this.translatedQuery = translatedQuery; }

    public String[] getSearchVariations() { return searchVariations; }
    public void setSearchVariations(String[] searchVariations) { this.searchVariations = searchVariations; }

    public String[] getExcludeTerms() { return excludeTerms; }
    public void setExcludeTerms(String[] excludeTerms) { this.excludeTerms = excludeTerms; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String[] getPreparationKeywords() { return preparationKeywords; }
    public void setPreparationKeywords(String[] preparationKeywords) { this.preparationKeywords = preparationKeywords; }
}
