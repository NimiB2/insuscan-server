package com.insuscan.data;

import com.insuscan.enums.UserRole;

import java.util.Date;
import java.util.List;

/**
 * Firestore document representing a user account. Contains identity,
 * personal info, medical profile (ICR/ISF/target), dose rounding preference,
 * and the user's insulin plans.
 */
public class UserEntity {

    private String id;

    private UserRole role;
    private String userName;
    private String avatar;

    private Integer age;
    private String gender;

    private String doseRounding;

    private List<InsulinPlan> insulinPlans;

    private Float insulinCarbRatio;
    private Float correctionFactor;
    private Integer targetGlucose;

    private Date createdAt;
    private Date updatedAt;

    public UserEntity() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

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
        return "UserEntity{" +
                "id='" + id + '\'' +
                ", role=" + role +
                ", userName='" + userName + '\'' +
                ", insulinCarbRatio=" + insulinCarbRatio +
                '}';
    }
}