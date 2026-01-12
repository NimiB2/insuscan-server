package com.insuscan.util;

import com.insuscan.crud.MealRepository;
import com.insuscan.data.MealEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates readable meal IDs in format: systemId_YYYYMMDD_XXX
 * Example: insuscan_20260112_001
 */
@Component
public class MealIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(MealIdGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern MEAL_ID_PATTERN = Pattern.compile("^(.+)_(\\d{8})_(\\d+)$");
    
    private final MealRepository mealRepository;

    public MealIdGenerator(MealRepository mealRepository) {
        this.mealRepository = mealRepository;
    }

    /**
     * Generate a new meal ID with format: systemId_YYYYMMDD_XXX
     * 
     * @param systemId The system identifier (e.g., "insuscan")
     * @return A new meal ID like "insuscan_20260112_001"
     */
    public String generateMealId(String systemId) {
        String datePrefix = LocalDate.now().format(DATE_FORMATTER);
        String baseId = systemId + "_" + datePrefix;
        
        // Find the highest sequence number for today
        int nextSequence = getNextSequenceNumber(baseId);
        
        // Format with leading zeros (001, 002, etc.)
        String sequence = String.format("%03d", nextSequence);
        
        String mealId = baseId + "_" + sequence;
        log.debug("Generated meal ID: {}", mealId);
        
        return mealId;
    }

    /**
     * Get the next sequence number for a given date prefix
     * Queries existing meals to find the highest sequence and returns next number
     */
    private int getNextSequenceNumber(String baseId) {
        try {
            int maxSequence = 0;
            
            // Query recent meals (last 200 should cover most of today's meals)
            // We'll filter by ID prefix to find meals from today
            List<MealEntity> recentMeals = mealRepository.findAllRecent(200);
            
            // Extract sequence numbers from meal IDs matching our pattern
            for (MealEntity meal : recentMeals) {
                String mealId = meal.getId();
                if (mealId != null && mealId.startsWith(baseId + "_")) {
                    Matcher matcher = MEAL_ID_PATTERN.matcher(mealId);
                    if (matcher.matches()) {
                        try {
                            int sequence = Integer.parseInt(matcher.group(3));
                            if (sequence > maxSequence) {
                                maxSequence = sequence;
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid sequence numbers
                            log.debug("Invalid sequence in meal ID: {}", mealId);
                        }
                    }
                }
            }
            
            return maxSequence + 1;
            
        } catch (Exception e) {
            log.warn("Error finding next sequence number, starting from 1: {}", e.getMessage());
            return 1;
        }
    }
}
