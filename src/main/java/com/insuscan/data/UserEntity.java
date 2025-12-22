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
