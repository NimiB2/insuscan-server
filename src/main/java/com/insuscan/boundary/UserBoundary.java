package com.insuscan.boundary;

import com.insuscan.enums.UserRole;

// User data transfer object for API responses
public class UserBoundary {
    private UserIdBoundary userId;
    private UserRole role;
    private String userName;
    private String avatar;
    
    // Medical profile
    private String insulinCarbRatio;    // e.g. "1:10"
    private Float correctionFactor;
    private Integer targetGlucose;

 // Personal info
    private Integer age;
    private String gender;
   
    // Dose settings
    private String doseRounding;
     
    // Insulin Plans
    private java.util.List<com.insuscan.data.InsulinPlan> insulinPlans;
    

    public UserBoundary() {}

    // Getters and Setters
    public UserIdBoundary getUserId() {
        return userId;
    }

    public void setUserId(UserIdBoundary userId) {
        this.userId = userId;
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

    // === Plans ===
    public java.util.List<com.insuscan.data.InsulinPlan> getInsulinPlans() { return insulinPlans; }
    public void setInsulinPlans(java.util.List<com.insuscan.data.InsulinPlan> insulinPlans) { this.insulinPlans = insulinPlans; }
    
    @Override
    public String toString() {
        return "UserBoundary{" +
                "userId=" + userId +
                ", role=" + role +
                ", userName='" + userName + '\'' +
                ", insulinCarbRatio='" + insulinCarbRatio + '\'' +
                '}';
    }
}
