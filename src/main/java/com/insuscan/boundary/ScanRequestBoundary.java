package com.insuscan.boundary;

// Request DTO for scanning a meal image
public class ScanRequestBoundary {
    private String imageBase64; // Base64 encoded image
    private String imageUrl; // Or URL to image
    private UserIdBoundary userId;

    // Optional portion analysis hints
    private Float syringeLengthCm; // Override user profile syringe
    private Float plateDepthHint; // User-provided depth estimate
    private String referenceObjectType; // E.g., "Pen", "Card", "Unknown"

    // ── Advanced Pipeline fields (v2) ──
    private Float plateDiameterCm; // Measured plate diameter from OpenCV + ref object
    private Float plateDepthCm; // Physical depth of the container
    private String containerType; // FLAT_PLATE, REGULAR_BOWL, DEEP_BOWL
    private Float pixelToCmRatio; // Calibration ratio from reference object
    private String foodRegionsJson; // JSON-encoded list of per-food region measurements

    public ScanRequestBoundary() {
    }

    // Getters and Setters
    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public UserIdBoundary getUserId() {
        return userId;
    }

    public void setUserId(UserIdBoundary userId) {
        this.userId = userId;
    }

    public Float getSyringeLengthCm() {
        return syringeLengthCm;
    }

    public void setSyringeLengthCm(Float syringeLengthCm) {
        this.syringeLengthCm = syringeLengthCm;
    }

    public Float getPlateDepthHint() {
        return plateDepthHint;
    }

    public void setPlateDepthHint(Float plateDepthHint) {
        this.plateDepthHint = plateDepthHint;
    }

    public String getReferenceObjectType() {
        return referenceObjectType;
    }

    public void setReferenceObjectType(String referenceObjectType) {
        this.referenceObjectType = referenceObjectType;
    }

    // ── v2 Pipeline Getters/Setters ──

    public Float getPlateDiameterCm() {
        return plateDiameterCm;
    }

    public void setPlateDiameterCm(Float plateDiameterCm) {
        this.plateDiameterCm = plateDiameterCm;
    }

    public Float getPlateDepthCm() {
        return plateDepthCm;
    }

    public void setPlateDepthCm(Float plateDepthCm) {
        this.plateDepthCm = plateDepthCm;
    }

    public String getContainerType() {
        return containerType;
    }

    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    public Float getPixelToCmRatio() {
        return pixelToCmRatio;
    }

    public void setPixelToCmRatio(Float pixelToCmRatio) {
        this.pixelToCmRatio = pixelToCmRatio;
    }

    public String getFoodRegionsJson() {
        return foodRegionsJson;
    }

    public void setFoodRegionsJson(String foodRegionsJson) {
        this.foodRegionsJson = foodRegionsJson;
    }

    @Override
    public String toString() {
        return "ScanRequestBoundary{" +
                "userId=" + userId +
                ", hasImage=" + (imageBase64 != null || imageUrl != null) +
                ", refType=" + referenceObjectType +
                '}';
    }
}
