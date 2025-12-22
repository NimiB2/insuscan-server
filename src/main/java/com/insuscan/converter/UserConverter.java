package com.insuscan.converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.insuscan.boundary.NewUserBoundary;
import com.insuscan.boundary.UserBoundary;
import com.insuscan.boundary.UserIdBoundary;
import com.insuscan.data.UserEntity;

@Component
public class UserConverter {

    @Value("${spring.application.name}")
    private String systemId;

    // Convert entity to boundary (for API responses)
    public UserBoundary toBoundary(UserEntity entity) {
        UserBoundary boundary = new UserBoundary();

        // Parse composite ID: systemId_email
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

        // Medical profile - convert Float ratio to String "1:X" format
        if (entity.getInsulinCarbRatio() != null) {
            boundary.setInsulinCarbRatio(floatToRatioString(entity.getInsulinCarbRatio()));
        }
        boundary.setCorrectionFactor(entity.getCorrectionFactor());
        boundary.setTargetGlucose(entity.getTargetGlucose());

        // Syringe settings
        boundary.setSyringeType(entity.getSyringeType());
        boundary.setCustomSyringeLength(entity.getCustomSyringeLength());

        return boundary;
    }

    // Convert boundary to entity (for saving to DB)
    public UserEntity toEntity(UserBoundary boundary) {
        UserEntity entity = new UserEntity();

        // Build composite ID
        if (boundary.getUserId() != null) {
            entity.setId(boundary.getUserId().getSystemId() + "_" + boundary.getUserId().getEmail());
        }

        entity.setRole(boundary.getRole());
        entity.setUserName(boundary.getUserName());
        entity.setAvatar(boundary.getAvatar());

        // Medical profile - convert String "1:X" to Float
        if (boundary.getInsulinCarbRatio() != null) {
            entity.setInsulinCarbRatio(ratioStringToFloat(boundary.getInsulinCarbRatio()));
        }
        entity.setCorrectionFactor(boundary.getCorrectionFactor());
        entity.setTargetGlucose(boundary.getTargetGlucose());

        // Syringe settings
        entity.setSyringeType(boundary.getSyringeType());
        entity.setCustomSyringeLength(boundary.getCustomSyringeLength());

        return entity;
    }

    // Convert new user registration to user boundary
    public UserBoundary newUserToBoundary(NewUserBoundary newUser) {
        UserBoundary boundary = new UserBoundary();

        UserIdBoundary userId = new UserIdBoundary(newUser.getEmail(), this.systemId);
        boundary.setUserId(userId);
        boundary.setUserName(newUser.getUserName());
        boundary.setAvatar(newUser.getAvatar());
        boundary.setRole(newUser.getRole());

        // Optional medical profile on registration
        boundary.setInsulinCarbRatio(newUser.getInsulinCarbRatio());
        boundary.setCorrectionFactor(newUser.getCorrectionFactor());
        boundary.setTargetGlucose(newUser.getTargetGlucose());
        boundary.setSyringeType(newUser.getSyringeType());

        return boundary;
    }

    // Helper: Convert "1:10" ratio string to float (0.1)
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
            // Invalid format
        }
        return null;
    }

    // Helper: Convert float (0.1) to ratio string "1:10"
    private String floatToRatioString(Float ratio) {
        if (ratio == null || ratio <= 0) {
            return null;
        }
        // Convert to "1:X" format
        int carbsPerUnit = Math.round(1 / ratio);
        return "1:" + carbsPerUnit;
    }
}
