package com.insuscan.util;

/**
 * Utility class for number formatting and rounding.
 */
public class NumberUtils {

    public static float roundTo2Decimals(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }

    // Null-safe overload
    public static Float roundTo2Decimals(Float value) {
        if (value == null) {
            return null;
        }
        return roundTo2Decimals(value.floatValue());
    }
}