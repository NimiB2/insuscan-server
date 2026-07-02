package com.insuscan.boundary;

import java.util.List;

/**
 * Request/Response DTO for insulin dose calculation. Carries inputs (carbs,
 * glucose, optional plan overrides), computed outputs (dose breakdown),
 * profile status, and warnings.
 */
public class InsulinCalculationBoundary {

    private Float totalCarbs;
    private Float currentGlucose;
    private UserIdBoundary userId;

    private Float planIcr;
    private Float planIsf;
    private Integer planTargetGlucose;

    private Float carbDose;
    private Float correctionDose;
    private Float totalRecommendedDose;

    private String insulinCarbRatioUsed;
    private Float correctionFactorUsed;
    private Integer targetGlucoseUsed;

    private boolean profileComplete;
    private List<String> missingFields;

    private String message;

    private String warning;

    public InsulinCalculationBoundary() {}

    public Float getTotalCarbs() {
        return totalCarbs;
    }

    public void setTotalCarbs(Float totalCarbs) {
        this.totalCarbs = totalCarbs;
    }

    public Float getCurrentGlucose() {
        return currentGlucose;
    }

    public void setCurrentGlucose(Float currentGlucose) {
        this.currentGlucose = currentGlucose;
    }

    public UserIdBoundary getUserId() {
        return userId;
    }

    public void setUserId(UserIdBoundary userId) {
        this.userId = userId;
    }

    public Float getPlanIcr() {
        return planIcr;
    }

    public void setPlanIcr(Float planIcr) {
        this.planIcr = planIcr;
    }

    public Float getPlanIsf() {
        return planIsf;
    }

    public void setPlanIsf(Float planIsf) {
        this.planIsf = planIsf;
    }

    public Integer getPlanTargetGlucose() {
        return planTargetGlucose;
    }

    public void setPlanTargetGlucose(Integer planTargetGlucose) {
        this.planTargetGlucose = planTargetGlucose;
    }

    public Float getCarbDose() {
        return carbDose;
    }

    public void setCarbDose(Float carbDose) {
        this.carbDose = carbDose;
    }

    public Float getCorrectionDose() {
        return correctionDose;
    }

    public void setCorrectionDose(Float correctionDose) {
        this.correctionDose = correctionDose;
    }

    public Float getTotalRecommendedDose() {
        return totalRecommendedDose;
    }

    public void setTotalRecommendedDose(Float totalRecommendedDose) {
        this.totalRecommendedDose = totalRecommendedDose;
    }

    public String getInsulinCarbRatioUsed() {
        return insulinCarbRatioUsed;
    }

    public void setInsulinCarbRatioUsed(String insulinCarbRatioUsed) {
        this.insulinCarbRatioUsed = insulinCarbRatioUsed;
    }

    public Float getCorrectionFactorUsed() {
        return correctionFactorUsed;
    }

    public void setCorrectionFactorUsed(Float correctionFactorUsed) {
        this.correctionFactorUsed = correctionFactorUsed;
    }

    public Integer getTargetGlucoseUsed() {
        return targetGlucoseUsed;
    }

    public void setTargetGlucoseUsed(Integer targetGlucoseUsed) {
        this.targetGlucoseUsed = targetGlucoseUsed;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public boolean isProfileComplete() {
        return profileComplete;
    }

    public void setProfileComplete(boolean profileComplete) {
        this.profileComplete = profileComplete;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "InsulinCalculationBoundary{" +
                "totalCarbs=" + totalCarbs +
                ", totalRecommendedDose=" + totalRecommendedDose +
                ", warning='" + warning + '\'' +
                '}';
    }
}