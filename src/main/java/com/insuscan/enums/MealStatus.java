package com.insuscan.enums;

/**
 * Lifecycle status of a meal record.
 */
public enum MealStatus {
    PENDING,     // just created, waiting for confirmation
    CONFIRMED,   // user confirmed food items and dose
    COMPLETED,   // insulin injected
    CANCELLED,   // user cancelled
    FAILED       // analysis failed
}