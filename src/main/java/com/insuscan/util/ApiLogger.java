package com.insuscan.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Centralized API logging for debugging external service calls.
 * Provides structured, readable logs for OpenAI and USDA interactions.
 */
@Component
public class ApiLogger {

    // Dedicated logger - will output to separate category
    private static final Logger log = LoggerFactory.getLogger("API_DEBUG");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // ===================== SCAN WORKFLOW =====================

    public void scanStart(String userEmail, boolean hasImage, Float estimatedWeight) {
        log.info("");
        log.info("================================================================================");
        log.info("                         MEAL SCAN WORKFLOW STARTED                            ");
        log.info("================================================================================");
        log.info("Timestamp    : {}", LocalDateTime.now().format(TIME_FMT));
        log.info("User         : {}", userEmail != null ? userEmail : "unknown");
        log.info("Image        : {}", hasImage ? "PROVIDED" : "MISSING");
        log.info("Est. Weight  : {}", estimatedWeight != null ? estimatedWeight + "g" : "not provided");
        log.info("--------------------------------------------------------------------------------");
    }

    public void scanStep(int step, String description) {
        log.info("");
        log.info(">>> STEP {} : {}", step, description);
        log.info("");
    }

    public void scanComplete(int foodCount, float totalCarbs, Float recommendedDose, long totalTimeMs) {
        log.info("");
        log.info("================================================================================");
        log.info("                         MEAL SCAN COMPLETED                                   ");
        log.info("================================================================================");
        log.info("Total Foods     : {}", foodCount);
        log.info("Total Carbs     : {}g", String.format("%.1f", totalCarbs));
        log.info("Recommended Dose: {}", recommendedDose != null ? recommendedDose + " units" : "N/A");
        log.info("Total Time      : {}ms", totalTimeMs);
        log.info("================================================================================");
        log.info("");
    }

    public void scanFailed(String stage, String reason) {
        log.error("");
        log.error("================================================================================");
        log.error("                         MEAL SCAN FAILED                                      ");
        log.error("================================================================================");
        log.error("Failed At : {}", stage);
        log.error("Reason    : {}", reason);
        log.error("================================================================================");
        log.error("");
    }

    // ===================== OPENAI VISION =====================

    public void openaiStart(String model, int imageSizeBytes) {
        log.info("--------------------------------------------------------------------------------");
        log.info("[OPENAI] REQUEST");
        log.info("--------------------------------------------------------------------------------");
        log.info("[OPENAI] Model       : {}", model);
        log.info("[OPENAI] Image Size  : {} KB", imageSizeBytes / 1024);
        log.info("[OPENAI] Status      : Sending to OpenAI...");
    }

    public void openaiCacheHit(String imageHash) {
        log.info("[OPENAI] CACHE HIT - returning cached result (hash: {}...)", 
            imageHash.substring(0, Math.min(8, imageHash.length())));
    }

    public void openaiResponseReceived(long timeMs, int responseLength) {
        log.info("[OPENAI] Response received in {}ms ({} chars)", timeMs, responseLength);
    }

    public void openaiRawResponse(String content) {
        log.info("--------------------------------------------------------------------------------");
        log.info("[OPENAI] RAW RESPONSE CONTENT");
        log.info("--------------------------------------------------------------------------------");
        if (content == null) {
            log.warn("[OPENAI] Content is NULL!");
            return;
        }
        // Log full content (truncate if very long)
        if (content.length() > 2000) {
            log.info("{}", content.substring(0, 2000));
            log.info("... [truncated, total {} chars]", content.length());
        } else {
            log.info("{}", content);
        }
    }

    public void openaiParsedFoods(List<?> foods) {
        log.info("--------------------------------------------------------------------------------");
        log.info("[OPENAI] PARSED FOODS: {} items", foods.size());
        log.info("--------------------------------------------------------------------------------");
        if (foods.isEmpty()) {
            log.warn("[OPENAI] No foods parsed from response!");
        } else {
            for (Object food : foods) {
                log.info("[OPENAI]   -> {}", food);
            }
        }
    }

    public void openaiRetry(String reason) {
        log.warn("[OPENAI] Retrying with RELAXED prompt. Reason: {}", reason);
    }

    public void openaiSuccess(int foodCount, long totalTimeMs) {
        log.info("[OPENAI] SUCCESS - {} foods detected in {}ms", foodCount, totalTimeMs);
    }

    public void openaiError(String error, String exceptionType) {
        log.error("--------------------------------------------------------------------------------");
        log.error("[OPENAI] ERROR");
        log.error("--------------------------------------------------------------------------------");
        log.error("[OPENAI] Type    : {}", exceptionType);
        log.error("[OPENAI] Message : {}", error);
    }

    // ===================== USDA NUTRITION =====================

    public void usdaStart(String foodName, String normalizedName, List<String> searchTerms) {
        log.info("--------------------------------------------------------------------------------");
        log.info("[USDA] NUTRITION LOOKUP");
        log.info("--------------------------------------------------------------------------------");
        log.info("[USDA] Original Food   : '{}'", foodName);
        log.info("[USDA] Normalized      : '{}'", normalizedName);
        log.info("[USDA] Search Terms    : {}", searchTerms);
    }

    public void usdaFallbackHit(String foodName, float carbsPer100g) {
        log.warn("[USDA] *** FALLBACK HIT ***");
        log.warn("[USDA] Food '{}' found in HARDCODED data (not real API!)", foodName);
        log.warn("[USDA] Returning {}g carbs/100g from FALLBACK", carbsPer100g);
    }

    public void usdaApiCall(String searchTerm) {
        log.info("[USDA] Calling API with term: '{}'", searchTerm);
    }

    public void usdaApiResponse(long timeMs, int resultCount, Object totalHits) {
        log.info("[USDA] Response in {}ms - {} results returned (total in DB: {})", 
            timeMs, resultCount, totalHits);
    }

    public void usdaSearchResults(List<?> results) {
        if (results.isEmpty()) {
            log.info("[USDA] No results from API");
        } else {
            for (Object r : results) {
                log.info("[USDA]   -> {}", r);
            }
        }
    }

    public void usdaMatchFound(String foodName, String fdcId, float carbsPer100g) {
        log.info("[USDA] MATCH: '{}' (fdcId: {}) -> {}g carbs/100g", foodName, fdcId, carbsPer100g);
    }

    public void usdaNoMatch(String foodName, boolean fallbackAvailable) {
        log.warn("[USDA] NO MATCH for '{}' - fallback available: {}", foodName, fallbackAvailable);
    }

    public void usdaError(String error) {
        log.error("[USDA] API ERROR: {}", error);
    }

    // ===================== NUTRITION CALCULATION =====================

    public void carbCalculation(String foodName, float carbsPer100g, float portionGrams, float calculatedCarbs, String source) {
        log.info("[CALC] {} : {}g/100g x {}g portion = {}g carbs [{}]",
            foodName,
            String.format("%.1f", carbsPer100g),
            String.format("%.0f", portionGrams),
            String.format("%.1f", calculatedCarbs),
            source);
    }

    public void carbCalculationFailed(String foodName, String reason) {
        log.warn("[CALC] {} : FAILED - {}", foodName, reason);
    }

    // ===================== GENERAL =====================

    public void apiKeyStatus(String service, boolean configured, String keyPreview) {
        if (configured) {
            log.info("[{}] API Key: CONFIGURED ({}...)", service, keyPreview);
        } else {
            log.error("[{}] API Key: NOT CONFIGURED!", service);
        }
    }
}