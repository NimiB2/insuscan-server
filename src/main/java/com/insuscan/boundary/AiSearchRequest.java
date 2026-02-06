package com.insuscan.boundary;

public class AiSearchRequest {
    
    private String query;
    private String userLanguage;
    private Integer limit;
    
    // Constructors
    public AiSearchRequest() {
        this.userLanguage = "en";
        this.limit = 10;
    }
    
    public AiSearchRequest(String query, String userLanguage, Integer limit) {
        this.query = query;
        this.userLanguage = userLanguage != null ? userLanguage : "en";
        this.limit = limit != null ? limit : 10;
    }
    
    // Getters and Setters
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public String getUserLanguage() {
        return userLanguage;
    }
    
    public void setUserLanguage(String userLanguage) {
        this.userLanguage = userLanguage;
    }
    
    public Integer getLimit() {
        return limit;
    }
    
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
    
    @Override
    public String toString() {
        return "AiSearchRequest{" +
                "query='" + query + '\'' +
                ", userLanguage='" + userLanguage + '\'' +
                ", limit=" + limit +
                '}';
    }
}
