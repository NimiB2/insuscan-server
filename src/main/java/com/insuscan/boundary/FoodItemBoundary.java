package com.insuscan.boundary;

/**
 * Individual food item detected in a meal, including its name, quantity,
 * carb content, and bounding box for client-side segmentation (GrabCut).
 */

public class FoodItemBoundary {
    private String name;
    private String nameHebrew;
    private Float quantity; // grams
    private Float carbs; // grams
    private Float confidence; // 0.0 to 1.0
    private String usdaFdcId; // USDA FoodData Central ID
    private String note;

    // bbox from Gemini vision (% of image), client uses for GrabCut
    private Float bboxXPct;
    private Float bboxYPct;
    private Float bboxWPct;
    private Float bboxHPct;

    public FoodItemBoundary() {
    }

    public Float getBboxXPct() {
        return bboxXPct;
    }

    public void setBboxXPct(Float bboxXPct) {
        this.bboxXPct = bboxXPct;
    }

    public Float getBboxYPct() {
        return bboxYPct;
    }

    public void setBboxYPct(Float bboxYPct) {
        this.bboxYPct = bboxYPct;
    }

    public Float getBboxWPct() {
        return bboxWPct;
    }

    public void setBboxWPct(Float bboxWPct) {
        this.bboxWPct = bboxWPct;
    }

    public Float getBboxHPct() {
        return bboxHPct;
    }

    public void setBboxHPct(Float bboxHPct) {
        this.bboxHPct = bboxHPct;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameHebrew() {
        return nameHebrew;
    }

    public void setNameHebrew(String nameHebrew) {
        this.nameHebrew = nameHebrew;
    }

    public Float getQuantity() {
        return quantity;
    }

    public void setQuantity(Float quantity) {
        this.quantity = quantity;
    }

    public Float getCarbs() {
        return carbs;
    }

    public void setCarbs(Float carbs) {
        this.carbs = carbs;
    }

    public Float getConfidence() {
        return confidence;
    }

    public void setConfidence(Float confidence) {
        this.confidence = confidence;
    }

    public String getUsdaFdcId() {
        return usdaFdcId;
    }

    public void setUsdaFdcId(String usdaFdcId) {
        this.usdaFdcId = usdaFdcId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "FoodItemBoundary{" +
                "name='" + name + '\'' +
                ", quantity=" + quantity +
                ", carbs=" + carbs +
                '}';
    }
}
