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

     // Personal info
        boundary.setAge(entity.getAge());
        boundary.setGender(entity.getGender());
        boundary.setPregnant(entity.getPregnant());
        boundary.setDueDate(entity.getDueDate());

        // Medical info
        boundary.setDiabetesType(entity.getDiabetesType());
        boundary.setInsulinType(entity.getInsulinType());
        boundary.setActiveInsulinTime(entity.getActiveInsulinTime());

        // Dose settings
        boundary.setDoseRounding(entity.getDoseRounding());

        // Adjustment factors
        boundary.setSickDayAdjustment(entity.getSickDayAdjustment());
        boundary.setStressAdjustment(entity.getStressAdjustment());
        boundary.setLightExerciseAdjustment(entity.getLightExerciseAdjustment());
        boundary.setIntenseExerciseAdjustment(entity.getIntenseExerciseAdjustment());

        // Preferences
        boundary.setGlucoseUnits(entity.getGlucoseUnits());
        
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

     // Personal info
        entity.setAge(boundary.getAge());
        entity.setGender(boundary.getGender());
        entity.setPregnant(boundary.getPregnant());
        entity.setDueDate(boundary.getDueDate());

        // Medical info
        entity.setDiabetesType(boundary.getDiabetesType());
        entity.setInsulinType(boundary.getInsulinType());
        entity.setActiveInsulinTime(boundary.getActiveInsulinTime());

        // Dose settings
        entity.setDoseRounding(boundary.getDoseRounding());

        // Adjustment factors
        entity.setSickDayAdjustment(boundary.getSickDayAdjustment());
        entity.setStressAdjustment(boundary.getStressAdjustment());
        entity.setLightExerciseAdjustment(boundary.getLightExerciseAdjustment());
        entity.setIntenseExerciseAdjustment(boundary.getIntenseExerciseAdjustment());

        // Preferences
        entity.setGlucoseUnits(boundary.getGlucoseUnits());
        
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
        
        // Personal info
        boundary.setAge(newUser.getAge());
        boundary.setGender(newUser.getGender());
        boundary.setPregnant(newUser.getPregnant());
        boundary.setDueDate(newUser.getDueDate());

        // Medical info
        boundary.setDiabetesType(newUser.getDiabetesType());
        boundary.setInsulinType(newUser.getInsulinType());
        boundary.setActiveInsulinTime(newUser.getActiveInsulinTime());

        // Dose settings
        boundary.setDoseRounding(newUser.getDoseRounding());

        // Adjustment factors
        boundary.setSickDayAdjustment(newUser.getSickDayAdjustment());
        boundary.setStressAdjustment(newUser.getStressAdjustment());
        boundary.setLightExerciseAdjustment(newUser.getLightExerciseAdjustment());
        boundary.setIntenseExerciseAdjustment(newUser.getIntenseExerciseAdjustment());

        // Preferences
        boundary.setGlucoseUnits(newUser.getGlucoseUnits());

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
