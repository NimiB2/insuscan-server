package com.insuscan.converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.insuscan.boundary.NewUserBoundary;
import com.insuscan.boundary.UserBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.data.UserEntity;

/**
 * Converts between {@link UserEntity} (Firestore document) and
 * {@link UserBoundary} (API DTO), handling composite ID parsing/building
 * and ICR representation (Float internally, "1:X" string in the boundary).
 */
@Component
public class UserConverter {

    @Value("${spring.application.name}")
    private String systemId;

    public UserBoundary toBoundary(UserEntity entity) {
        UserBoundary boundary = new UserBoundary();

        UserIdBoundary userId = new UserIdBoundary();
        if (entity.getId() != null) {
            String[] parts = entity.getId().split("_", 2);
            if (parts.length == 2) {
                userId.setSystemId(parts[0]);
                userId.setEmail(parts[1]);
            }
        }

        boundary.setUserId(userId);
        boundary.setRole(entity.getRole());
        boundary.setUserName(entity.getUserName());
        boundary.setAvatar(entity.getAvatar());

        if (entity.getInsulinCarbRatio() != null) {
            boundary.setInsulinCarbRatio(floatToRatioString(entity.getInsulinCarbRatio()));
        }
        boundary.setCorrectionFactor(entity.getCorrectionFactor());
        boundary.setTargetGlucose(entity.getTargetGlucose());

        boundary.setAge(entity.getAge());
        boundary.setGender(entity.getGender());

        boundary.setDoseRounding(entity.getDoseRounding());

        boundary.setInsulinPlans(entity.getInsulinPlans());

        return boundary;
    }

    public UserEntity toEntity(UserBoundary boundary) {
        UserEntity entity = new UserEntity();

        if (boundary.getUserId() != null) {
            entity.setId(boundary.getUserId().getSystemId() + "_" + boundary.getUserId().getEmail());
        }

        entity.setRole(boundary.getRole());
        entity.setUserName(boundary.getUserName());
        entity.setAvatar(boundary.getAvatar());

        if (boundary.getInsulinCarbRatio() != null) {
            entity.setInsulinCarbRatio(ratioStringToFloat(boundary.getInsulinCarbRatio()));
        }
        entity.setCorrectionFactor(boundary.getCorrectionFactor());
        entity.setTargetGlucose(boundary.getTargetGlucose());

        entity.setAge(boundary.getAge());
        entity.setGender(boundary.getGender());

        entity.setDoseRounding(boundary.getDoseRounding());

        entity.setInsulinPlans(boundary.getInsulinPlans());

        return entity;
    }

    public UserBoundary newUserToBoundary(NewUserBoundary newUser) {
        UserBoundary boundary = new UserBoundary();

        UserIdBoundary userId = new UserIdBoundary(newUser.getEmail(), this.systemId);
        boundary.setUserId(userId);
        boundary.setUserName(newUser.getUserName());
        boundary.setAvatar(newUser.getAvatar());
        boundary.setRole(newUser.getRole());

        boundary.setInsulinCarbRatio(newUser.getInsulinCarbRatio());
        boundary.setCorrectionFactor(newUser.getCorrectionFactor());
        boundary.setTargetGlucose(newUser.getTargetGlucose());

        boundary.setAge(newUser.getAge());
        boundary.setGender(newUser.getGender());

        boundary.setDoseRounding(newUser.getDoseRounding());

        boundary.setInsulinPlans(newUser.getInsulinPlans());

        return boundary;
    }

    private Float ratioStringToFloat(String ratioStr) {
        if (ratioStr == null || ratioStr.isBlank()) {
            return null;
        }
        try {
            String[] parts = ratioStr.split(":");
            if (parts.length == 2) {
                float units = Float.parseFloat(parts[0].trim());
                float grams = Float.parseFloat(parts[1].trim());
                if (grams > 0) {
                    return units / grams;
                }
            }
        } catch (NumberFormatException e) {
        }
        return null;
    }

    private String floatToRatioString(Float ratio) {
        if (ratio == null || ratio <= 0) {
            return null;
        }
        float carbsPerUnit = Math.round((1f / ratio) * 10f) / 10f;
        if (carbsPerUnit == Math.floor(carbsPerUnit)) {
            return "1:" + (int) carbsPerUnit;
        }
        return "1:" + carbsPerUnit;
    }
}