package com.insuscan.enums;

public enum MealStatus {
    PENDING,     // just created, waiting for confirmation
    CONFIRMED,   // user confirmed food items and dose
    COMPLETED,   // insulin injected
    CANCELLED,   // user cancelled
    FAILED       // analysis failed
}
