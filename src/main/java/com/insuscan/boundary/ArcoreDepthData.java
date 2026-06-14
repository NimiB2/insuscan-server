package com.insuscan.boundary;

import java.util.Map;

public class ArcoreDepthData {

    private Float plateDepthM;
    private Map<String, Float> itemDepthsM;
    private Float arConfidence;
    private Float plateDiameterCm;

    public ArcoreDepthData() {}

    public Float getPlateDepthM() { return plateDepthM; }
    public void setPlateDepthM(Float v) { this.plateDepthM = v; }

    public Map<String, Float> getItemDepthsM() { return itemDepthsM; }
    public void setItemDepthsM(Map<String, Float> v) { this.itemDepthsM = v; }

    public Float getArConfidence() { return arConfidence; }
    public void setArConfidence(Float v) { this.arConfidence = v; }

    public Float getPlateDiameterCm() { return plateDiameterCm; }
    public void setPlateDiameterCm(Float v) { this.plateDiameterCm = v; }

    public boolean isValid() {
        return arConfidence != null && arConfidence > 0f && plateDepthM != null && plateDepthM > 0f;
    }
}