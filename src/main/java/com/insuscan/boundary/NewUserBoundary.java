package com.insuscan.boundary;

import com.insuscan.enums.UserRole;

// DTO for creating new users (registration)
public class NewUserBoundary {
    private String email;
    private UserRole role;
    private String userName;
    private String avatar;
    
    // Medical profile (optional on registration)
    private String insulinCarbRatio;
    private Float correctionFactor;
    private Integer targetGlucose;
    
    // Personal info
    private Integer age;
    private String gender;
    
    // Dose settings
    private String doseRounding;
 
    // Insslin Plans
    private java.util.List<com.insuscan.data.InsulinPlan> insulinPlans;

	// Preferences
    private String glucoseUnits;

    public NewUserBoundary() {}

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getInsulinCarbRatio() {
        return insulinCarbRatio;
    }

    public void setInsulinCarbRatio(String insulinCarbRatio) {
        this.insulinCarbRatio = insulinCarbRatio;
    }

    public Float getCorrectionFactor() {
        return correctionFactor;
    }

    public void setCorrectionFactor(Float correctionFactor) {
        this.correctionFactor = correctionFactor;
    }

    public Integer getTargetGlucose() {
        return targetGlucose;
    }

    public void setTargetGlucose(Integer targetGlucose) {
        this.targetGlucose = targetGlucose;
    }

 // === Personal Info ===
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    // === Dose Settings ===
    public String getDoseRounding() { return doseRounding; }
    public void setDoseRounding(String doseRounding) { this.doseRounding = doseRounding; }

    // === Preferences ===
    public String getGlucoseUnits() { return glucoseUnits; }
    public void setGlucoseUnits(String glucoseUnits) { this.glucoseUnits = glucoseUnits; }
    
    // === Plans ===
    public java.util.List<com.insuscan.data.InsulinPlan> getInsulinPlans() { return insulinPlans; }
    public void setInsulinPlans(java.util.List<com.insuscan.data.InsulinPlan> insulinPlans) { this.insulinPlans = insulinPlans; }
    
    @Override
    public String toString() {
        return "NewUserBoundary{" +
                "email='" + email + '\'' +
                ", role=" + role +
                ", userName='" + userName + '\'' +
                '}';
    }
}
