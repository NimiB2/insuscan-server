package com.insuscan.boundary;

// Request DTO for scanning a meal image
public class ScanRequestBoundary {
    private String imageBase64;     // Base64 encoded image
    private String imageUrl;        // Or URL to image
    private UserIdBoundary userId;
    
    // Optional portion analysis hints
    private Float syringeLengthCm;  // Override user profile syringe
    private Float plateDepthHint;   // User-provided depth estimate

    public ScanRequestBoundary() {}

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

    @Override
    public String toString() {
        return "ScanRequestBoundary{" +
                "userId=" + userId +
                ", hasImage=" + (imageBase64 != null || imageUrl != null) +
                '}';
    }
}
