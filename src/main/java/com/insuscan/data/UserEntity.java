package com.insuscan.data;

import com.insuscan.enums.UserRole;

import java.util.Date;

// Firestore collection: users
public class UserEntity {
    
    private String id;              // Format: systemId_email
    
    private UserRole role;
    private String userName;
    private String avatar;
    
 // Personal info
    private Integer age;
    private String gender;              // "Male", "Female", "Other", "Prefer not to say"
 
    // Dose rounding preference
    private String doseRounding;        // "0.5" or "1"
       
    // Insulin Plans
    private java.util.List<InsulinPlan> insulinPlans;
       
    // Medical profile for insulin calculation
    private Float insulinCarbRatio;     // Units of insulin per gram of carbs (e.g., 0.1 = 1:10)
    private Float correctionFactor;     // mg/dL drop per unit of insulin (e.g., 50)
    private Integer targetGlucose;      // Target blood glucose in mg/dL (e.g., 100)
    
    private Date createdAt;
    private Date updatedAt;

    public UserEntity() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Float getInsulinCarbRatio() {
        return insulinCarbRatio;
    }

    public void setInsulinCarbRatio(Float insulinCarbRatio) {
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

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
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
    public java.util.List<InsulinPlan> getInsulinPlans() { return insulinPlans; }
    public void setInsulinPlans(java.util.List<InsulinPlan> insulinPlans) { this.insulinPlans = insulinPlans; }
    
    @Override
    public String toString() {
        return "UserEntity{" +
                "id='" + id + '\'' +
                ", role=" + role +
                ", userName='" + userName + '\'' +
                ", insulinCarbRatio=" + insulinCarbRatio +
                '}';
    }
}
