package com.insuscan.boundary;

import com.insuscan.enums.UserRole;
import com.insuscan.enums.SyringeType;

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
    private SyringeType syringeType;
    
    // Personal info
    private Integer age;
    private String gender;
    private Boolean pregnant;
    private String dueDate;
    
    // Medical info
    private String diabetesType;
    private String insulinType;
    private Integer activeInsulinTime;
    
    // Dose settings
    private String doseRounding;
    
    // Adjustment factors
    private Integer sickDayAdjustment;
    private Integer stressAdjustment;
    private Integer lightExerciseAdjustment;
    private Integer intenseExerciseAdjustment;
    
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

    public SyringeType getSyringeType() {
        return syringeType;
    }

    public void setSyringeType(SyringeType syringeType) {
        this.syringeType = syringeType;
    }

 // === Personal Info ===
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Boolean getPregnant() { return pregnant; }
    public void setPregnant(Boolean pregnant) { this.pregnant = pregnant; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    // === Medical Info ===
    public String getDiabetesType() { return diabetesType; }
    public void setDiabetesType(String diabetesType) { this.diabetesType = diabetesType; }

    public String getInsulinType() { return insulinType; }
    public void setInsulinType(String insulinType) { this.insulinType = insulinType; }

    public Integer getActiveInsulinTime() { return activeInsulinTime; }
    public void setActiveInsulinTime(Integer activeInsulinTime) { this.activeInsulinTime = activeInsulinTime; }

    // === Dose Settings ===
    public String getDoseRounding() { return doseRounding; }
    public void setDoseRounding(String doseRounding) { this.doseRounding = doseRounding; }

    // === Adjustment Factors ===
    public Integer getSickDayAdjustment() { return sickDayAdjustment; }
    public void setSickDayAdjustment(Integer sickDayAdjustment) { this.sickDayAdjustment = sickDayAdjustment; }

    public Integer getStressAdjustment() { return stressAdjustment; }
    public void setStressAdjustment(Integer stressAdjustment) { this.stressAdjustment = stressAdjustment; }

    public Integer getLightExerciseAdjustment() { return lightExerciseAdjustment; }
    public void setLightExerciseAdjustment(Integer lightExerciseAdjustment) { this.lightExerciseAdjustment = lightExerciseAdjustment; }

    public Integer getIntenseExerciseAdjustment() { return intenseExerciseAdjustment; }
    public void setIntenseExerciseAdjustment(Integer intenseExerciseAdjustment) { this.intenseExerciseAdjustment = intenseExerciseAdjustment; }

    // === Preferences ===
    public String getGlucoseUnits() { return glucoseUnits; }
    public void setGlucoseUnits(String glucoseUnits) { this.glucoseUnits = glucoseUnits; }
    
    @Override
    public String toString() {
        return "NewUserBoundary{" +
                "email='" + email + '\'' +
                ", role=" + role +
                ", userName='" + userName + '\'' +
                '}';
    }
}
