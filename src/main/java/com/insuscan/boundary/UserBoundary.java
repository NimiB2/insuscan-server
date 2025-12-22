package com.insuscan.boundary;

import com.insuscan.enums.UserRole;
import com.insuscan.enums.SyringeType;

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
    
    // Reference object settings
    private SyringeType syringeType;
    private Float customSyringeLength;   // if using custom syringe

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
