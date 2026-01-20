package com.insuscan.data;

import com.insuscan.enums.SyringeType;
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
    private Boolean pregnant;
    private String dueDate;             // format: "dd/MM/yyyy"
    
    // Medical info
    private String diabetesType;        // "Type 1", "Type 2", "Gestational", "Other"
    private String insulinType;         // "Rapid", "Short", "Other"
    private Integer activeInsulinTime;  // DIA in hours (3, 4, 5, 6)
    
    // Dose rounding preference
    private String doseRounding;        // "0.5" or "1"
    
    // Adjustment factors (percentages)
    private Integer sickDayAdjustment;       // e.g., 15 means +15%
    private Integer stressAdjustment;        // e.g., 10 means +10%
    private Integer lightExerciseAdjustment; // e.g., -15 means -15%
    private Integer intenseExerciseAdjustment; // e.g., -30 means -30%
    
    // Preferences
    private String glucoseUnits;        // "mg/dL" or "mmol/L"
    
    // Medical profile for insulin calculation
    private Float insulinCarbRatio;     // Units of insulin per gram of carbs (e.g., 0.1 = 1:10)
    private Float correctionFactor;     // mg/dL drop per unit of insulin (e.g., 50)
    private Integer targetGlucose;      // Target blood glucose in mg/dL (e.g., 100)
    
    // Reference object settings
    private SyringeType syringeType;
    private Float customSyringeLength;  // If user has non-standard syringe
    
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

    public SyringeType getSyringeType() {
        return syringeType;
    }

    public void setSyringeType(SyringeType syringeType) {
        this.syringeType = syringeType;
    }

    public Float getCustomSyringeLength() {
        return customSyringeLength;
    }

    public void setCustomSyringeLength(Float customSyringeLength) {
        this.customSyringeLength = customSyringeLength;
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
        return "UserEntity{" +
                "id='" + id + '\'' +
                ", role=" + role +
                ", userName='" + userName + '\'' +
                ", insulinCarbRatio=" + insulinCarbRatio +
                '}';
    }
}
