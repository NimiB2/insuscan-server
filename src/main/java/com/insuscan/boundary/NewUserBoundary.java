package com.insuscan.boundary;

import com.insuscan.data.InsulinPlan;
import com.insuscan.enums.UserRole;

import java.util.List;

/**
 * DTO for creating new users on registration, including optional medical
 * profile, personal info, dose settings, and initial insulin plans.
 */
public class NewUserBoundary {

    private String email;
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

    private String glucoseUnits;

    public NewUserBoundary() {}

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

    public String getGlucoseUnits() {
        return glucoseUnits;
    }

    public void setGlucoseUnits(String glucoseUnits) {
        this.glucoseUnits = glucoseUnits;
    }

    public List<InsulinPlan> getInsulinPlans() {
        return insulinPlans;
    }

    public void setInsulinPlans(List<InsulinPlan> insulinPlans) {
        this.insulinPlans = insulinPlans;
    }

    @Override
    public String toString() {
        return "NewUserBoundary{" +
                "email='" + email + '\'' +
                ", role=" + role +
                ", userName='" + userName + '\'' +
                '}';
    }
}