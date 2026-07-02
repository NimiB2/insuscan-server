package com.insuscan.boundary;

import com.insuscan.data.InsulinPlan;
import com.insuscan.enums.UserRole;

import java.util.List;

/**
 * User data transfer object for API responses, containing identity,
 * medical profile (ICR/ISF/target), personal info, and insulin plans.
 */
public class UserBoundary {

    private UserIdBoundary userId;
    private UserRole role;
    private String userName;
    private String avatar;

    private String insulinCarbRatio;
    private Float correctionFactor;
    private Integer targetGlucose;

    private Integer age;
    private String gender;

    private String doseRounding;

    private List<InsulinPlan> insulinPlans;

    public UserBoundary() {}

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

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDoseRounding() {
        return doseRounding;
    }

    public void setDoseRounding(String doseRounding) {
        this.doseRounding = doseRounding;
    }

    public List<InsulinPlan> getInsulinPlans() {
        return insulinPlans;
    }

    public void setInsulinPlans(List<InsulinPlan> insulinPlans) {
        this.insulinPlans = insulinPlans;
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