package com.insuscan.boundary;

import java.util.List;

/**
 * Response from chat parsing: identifies the user's intended action and
 * carries the parsed payload (food items, glucose, activity, or profile updates).
 */
public class ChatParseResponse {

    private String action; // "add_food", "set_glucose", "set_activity", "unknown"
    private List<FoodEntry> items; // For "add_food" action
    private Integer glucose; // For "set_glucose" action
    private String activity; // For "set_activity" action

    private Double icr;
    private Double isf;
    private Integer targetGlucose;

    private String message;

    public ChatParseResponse() {
    }

    public ChatParseResponse(String action, String message) {
        this.action = action;
        this.message = message;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<FoodEntry> getItems() {
        return items;
    }

    public void setItems(List<FoodEntry> items) {
        this.items = items;
    }

    public Integer getGlucose() {
        return glucose;
    }

    public void setGlucose(Integer glucose) {
        this.glucose = glucose;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public Double getIcr() {
        return icr;
    }

    public void setIcr(Double icr) {
        this.icr = icr;
    }

    public Double getIsf() {
        return isf;
    }

    public void setIsf(Double isf) {
        this.isf = isf;
    }

    public Integer getTargetGlucose() {
        return targetGlucose;
    }

    public void setTargetGlucose(Integer targetGlucose) {
        this.targetGlucose = targetGlucose;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * A single parsed food item from an "add_food" action.
     */
    public static class FoodEntry {
        private String name;
        private Integer quantity;
        private Float estimatedCarbsGrams;

        public FoodEntry() {
        }

        public FoodEntry(String name, Integer quantity, Float estimatedCarbsGrams) {
            this.name = name;
            this.quantity = quantity;
            this.estimatedCarbsGrams = estimatedCarbsGrams;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public Float getEstimatedCarbsGrams() {
            return estimatedCarbsGrams;
        }

        public void setEstimatedCarbsGrams(Float estimatedCarbsGrams) {
            this.estimatedCarbsGrams = estimatedCarbsGrams;
        }
    }
}
