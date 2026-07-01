package com.insuscan.util;

import com.insuscan.exception.InsuScanInvalidInputException;

import java.util.regex.Pattern;

// Validation utilities for user input
public class InputValidators {

    private static final String EMAIL_PATTERN =
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

    private static final Pattern emailPattern = Pattern.compile(EMAIL_PATTERN);

    // Insulin ratio format: "1:X" where X is a number (e.g. "1:10", "1:7.5")
    private static final String RATIO_PATTERN = "^\\d+:\\d+(\\.\\d+)?$";
    private static final Pattern ratioPattern = Pattern.compile(RATIO_PATTERN);

    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return emailPattern.matcher(email.trim()).matches();
    }

    public static void validateEmail(String email) {
        if (!isValidEmail(email)) {
            throw new InsuScanInvalidInputException("Invalid email format: " + email);
        }
    }

    public static boolean isValidSystemId(String systemId) {
        return systemId != null && !systemId.trim().isEmpty();
    }

    public static void validateSystemId(String systemId) {
        if (!isValidSystemId(systemId)) {
            throw new InsuScanInvalidInputException("SystemId cannot be null or empty");
        }
    }

    public static boolean isValidInsulinRatio(String ratio) {
        if (ratio == null || ratio.trim().isEmpty()) {
            return false;
        }
        return ratioPattern.matcher(ratio.trim()).matches();
    }

    // Parses "1:10" -> 0.1 (units per gram)
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

    public static boolean isPositive(Float value) {
        return value != null && value > 0;
    }

    public static boolean isNonNegative(Float value) {
        return value != null && value >= 0;
    }

    // Valid correction factor range: 1-200 mg/dL per unit (typical clinical range 10-100)
    public static boolean isValidCorrectionFactor(Float factor) {
        return factor != null && factor > 0 && factor <= 200;
    }

    // Valid target glucose range: 60-200 mg/dL (typical clinical range 80-150)
    public static boolean isValidTargetGlucose(Integer target) {
        return target != null && target >= 60 && target <= 200;
    }

    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static void validateNotEmpty(String value, String fieldName) {
        if (!isNotEmpty(value)) {
            throw new InsuScanInvalidInputException(fieldName + " cannot be null or empty");
        }
    }

    // Allows letters (including Hebrew), spaces, hyphens, apostrophes
    public static boolean isValidUserName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return name.matches("^[\\p{L}\\s\\-']+$");
    }

    // Valid carbs range: 0-500g per meal
    public static boolean isValidCarbsAmount(Float carbs) {
        return carbs != null && carbs >= 0 && carbs <= 500;
    }

    // Valid insulin dose range: 0-100 units
    public static boolean isValidInsulinDose(Float dose) {
        return dose != null && dose >= 0 && dose <= 100;
    }

    // Valid confidence score range: 0.0-1.0
    public static boolean isValidConfidence(Float confidence) {
        return confidence != null && confidence >= 0 && confidence <= 1;
    }
}