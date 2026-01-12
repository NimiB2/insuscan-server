package com.insuscan.util;

/**
 * Utility class for number formatting and rounding
 */
public class NumberUtils {
    
    /**
     * Round a float to 2 decimal places
     * @param value The float value to round
     * @return Rounded value with 2 decimal places
     */
    public static float roundTo2Decimals(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }
    
    /**
     * Round a Float to 2 decimal places (handles null)
     * @param value The Float value to round (can be null)
     * @return Rounded value with 2 decimal places, or null if input is null
     */
    public static Float roundTo2Decimals(Float value) {
        if (value == null) {
            return null;
        }
        return roundTo2Decimals(value.floatValue());
    }
}
