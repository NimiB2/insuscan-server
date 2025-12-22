package com.insuscan.util;

import com.insuscan.enums.UserRole;
import com.insuscan.exception.InsuScanInvalidInputException;

import java.util.regex.Pattern;

// Validation utilities for user input
public class InputValidators {

    // Email regex pattern
    private static final String EMAIL_PATTERN = 
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    
    private static final Pattern emailPattern = Pattern.compile(EMAIL_PATTERN);

    // Insulin ratio pattern: "1:X" where X is a number
    private static final String RATIO_PATTERN = "^\\d+:\\d+$";
    private static final Pattern ratioPattern = Pattern.compile(RATIO_PATTERN);

    // Validate email format
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return emailPattern.matcher(email.trim()).matches();
    }

    // Validate email and throw if invalid
    public static void validateEmail(String email) {
        if (!isValidEmail(email)) {
            throw new InsuScanInvalidInputException("Invalid email format: " + email);
        }
    }

    // Validate user role
    public static boolean isValidRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return false;
        }
        try {
            UserRole.valueOf(role.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Validate role and throw if invalid
    public static void validateRole(String role) {
        if (!isValidRole(role)) {
            throw new InsuScanInvalidInputException(
                "Invalid role: " + role + ". Must be one of: PATIENT, CAREGIVER, ADMIN");
        }
    }

    // Validate systemId is not empty
    public static boolean isValidSystemId(String systemId) {
        return systemId != null && !systemId.trim().isEmpty();
    }

    // Validate systemId and throw if invalid
    public static void validateSystemId(String systemId) {
        if (!isValidSystemId(systemId)) {
            throw new InsuScanInvalidInputException("SystemId cannot be null or empty");
        }
    }

    // Validate insulin:carb ratio format (e.g., "1:10")
    public static boolean isValidInsulinRatio(String ratio) {
        if (ratio == null || ratio.trim().isEmpty()) {
            return false;
        }
        return ratioPattern.matcher(ratio.trim()).matches();
    }

    // Parse insulin ratio string to float (e.g., "1:10" -> 0.1)
    public static Float parseInsulinRatio(String ratio) {
        if (!isValidInsulinRatio(ratio)) {
            return null;
        }
        String[] parts = ratio.split(":");
        float units = Float.parseFloat(parts[0]);
        float carbs = Float.parseFloat(parts[1]);
        if (carbs == 0) {
            return null;
        }
        return units / carbs;
    }

    // Validate positive number
    public static boolean isPositive(Float value) {
        return value != null && value > 0;
    }

    // Validate non-negative number
    public static boolean isNonNegative(Float value) {
        return value != null && value >= 0;
    }

    // Validate correction factor (typically 10-100 mg/dL per unit)
    public static boolean isValidCorrectionFactor(Float factor) {
        return factor != null && factor > 0 && factor <= 200;
    }

    // Validate target glucose (typically 80-150 mg/dL)
    public static boolean isValidTargetGlucose(Integer target) {
        return target != null && target >= 60 && target <= 200;
    }

    // Validate string is not null or empty
    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // Validate string and throw if empty
    public static void validateNotEmpty(String value, String fieldName) {
        if (!isNotEmpty(value)) {
            throw new InsuScanInvalidInputException(fieldName + " cannot be null or empty");
        }
    }

    // Validate user name (letters, spaces, hyphens only)
    public static boolean isValidUserName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        // Allow letters (including Hebrew), spaces, hyphens, apostrophes
        return name.matches("^[\\p{L}\\s\\-']+$");
    }

    // Validate carbs value is reasonable (0-500g per meal)
    public static boolean isValidCarbsAmount(Float carbs) {
        return carbs != null && carbs >= 0 && carbs <= 500;
    }

    // Validate insulin dose is reasonable (0-100 units)
    public static boolean isValidInsulinDose(Float dose) {
        return dose != null && dose >= 0 && dose <= 100;
    }

    // Validate confidence score (0.0 to 1.0)
    public static boolean isValidConfidence(Float confidence) {
        return confidence != null && confidence >= 0 && confidence <= 1;
    }
}
