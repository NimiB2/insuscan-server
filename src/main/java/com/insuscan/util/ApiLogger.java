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

    private static final Logger log = LoggerFactory.getLogger(ApiLogger.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ServerFileLogger fileLogger;

    public ApiLogger(ServerFileLogger fileLogger) {
        this.fileLogger = fileLogger;
    }

    private void logBoth(String format, Object... args) {
        // format for SLF4J
        log.info(format, args);
        // format for File
        try {
            // Very basic formatter for our file logger which expects similar to SLF4J
            String msg = format;
            for (Object arg : args) {
                msg = msg.replaceFirst("\\{\\}", String.valueOf(arg));
            }
            fileLogger.append(msg);
        } catch (Exception e) {
            // ignore logging errors
        }
    }

    // ===================== SCAN WORKFLOW =====================

    public void scanStart(String userEmail, boolean hasImage, Float estimatedWeight) {
        logBoth("", (Object[])null);
        logBoth("================================================================================", (Object[])null);
        logBoth("                         MEAL SCAN WORKFLOW STARTED                            ", (Object[])null);
        logBoth("================================================================================", (Object[])null);
        logBoth("Timestamp    : {}", LocalDateTime.now().format(TIME_FMT));
        logBoth("User         : {}", userEmail != null ? userEmail : "unknown");
        logBoth("Image        : {}", hasImage ? "PROVIDED" : "MISSING");
        logBoth("Est. Weight  : {}", estimatedWeight != null ? estimatedWeight + "g" : "not provided");
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
    }

    public void scanStep(int step, String description) {
        logBoth("", (Object[])null);
        logBoth(">>> STEP {} : {}", step, description);
        logBoth("", (Object[])null);
    }

    public void scanComplete(int foodCount, float totalCarbs, Float recommendedDose, long totalTimeMs) {
        logBoth("", (Object[])null);
        logBoth("================================================================================", (Object[])null);
        logBoth("                         MEAL SCAN COMPLETED                                   ", (Object[])null);
        logBoth("================================================================================", (Object[])null);
        logBoth("Total Foods     : {}", foodCount);
        logBoth("Total Carbs     : {}g", String.format("%.1f", totalCarbs));
        logBoth("Recommended Dose: {}", recommendedDose != null ? recommendedDose + " units" : "N/A");
        logBoth("Total Time      : {}ms", totalTimeMs);
        logBoth("================================================================================", (Object[])null);
        logBoth("", (Object[])null);
    }

    public void scanFailed(String stage, String reason) {
        logBoth("", (Object[])null);
        logBoth("================================================================================", (Object[])null);
        logBoth("                         MEAL SCAN FAILED                                      ", (Object[])null);
        logBoth("================================================================================", (Object[])null);
        logBoth("Failed At : {}", stage);
        logBoth("Reason    : {}", reason);
        logBoth("================================================================================", (Object[])null);
        logBoth("", (Object[])null);
    }

    // ===================== INSULIN CALCULATION =====================

    public void insulinCalcStart(Float totalCarbs, Integer currentGlucose, String activityLevel,
                                  Boolean sickMode, Boolean stressMode, String userEmail) {
        logBoth("", (Object[])null);
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[INSULIN] CALCULATION STARTED", (Object[])null);
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[INSULIN] User           : {}", userEmail != null ? userEmail : "anonymous");
        logBoth("[INSULIN] Total Carbs    : {}g", totalCarbs);
        logBoth("[INSULIN] Current Glucose: {}", currentGlucose != null ? currentGlucose + " mg/dL" : "not provided");
        logBoth("[INSULIN] Activity Level : {}", activityLevel != null ? activityLevel : "normal");
        logBoth("[INSULIN] Sick Mode      : {}", Boolean.TRUE.equals(sickMode) ? "ON" : "OFF");
        logBoth("[INSULIN] Stress Mode    : {}", Boolean.TRUE.equals(stressMode) ? "ON" : "OFF");
    }
    
    public void insulinCalcStart(float totalCarbs, String userEmail) {
        logBoth("[INSULIN] Starting calculation for {}g carbs (user: {})", 
            String.format("%.1f", totalCarbs), 
            userEmail != null ? userEmail : "anonymous");
    }

    public void insulinCalcBreakdown(float carbDose, Float correctionDose, float baseDose, 
                                      float finalDose, float roundedDose) {
        logBoth("[INSULIN] Breakdown: CarbDose={} + Correction={} = Base:{} -> Final:{} -> Rounded:{}",
            String.format("%.2f", carbDose),
            correctionDose != null ? String.format("%.2f", correctionDose) : "0.00",
            String.format("%.2f", baseDose),
            String.format("%.2f", finalDose),
            String.format("%.1f", roundedDose));
    }

    public void insulinCalcParams(Float icr, Float isf, Integer targetGlucose, boolean fromProfile) {
        String source = fromProfile ? "USER PROFILE" : "DEFAULTS";
        logBoth("[INSULIN] Parameters from: {}", source);
        logBoth("[INSULIN]   ICR (units/g) : {} (1:{})", 
            String.format("%.3f", icr), 
            String.format("%.0f", 1.0f / icr));
        logBoth("[INSULIN]   ISF (mg/dL)   : {}", String.format("%.0f", isf));
        logBoth("[INSULIN]   Target Glucose: {} mg/dL", targetGlucose);
    }
    
    public void insulinCalcProfileStatus(boolean complete, List<String> missingFields) {
        if (complete) {
            logBoth("[INSULIN] Profile status: COMPLETE ✓", (Object[])null);
        } else {
            logBoth("[INSULIN] Profile status: INCOMPLETE", (Object[])null);
            logBoth("[INSULIN] Missing fields: {}", missingFields);
        }
    }

    public void insulinCalcSkipped(List<String> reasons) {
        logBoth("[INSULIN] Calculation SKIPPED - profile incomplete", (Object[])null);
        logBoth("[INSULIN] Missing: {}", reasons);
    }

    public void insulinCalcResult(Float dose, boolean profileComplete, String message) {
        if (profileComplete && dose != null) {
            logBoth("[INSULIN] RESULT: {} units ✓", String.format("%.1f", dose));
        } else {
            logBoth("[INSULIN] RESULT: No dose calculated ({})", 
                message != null ? message : "profile incomplete");
        }
    }

    public void insulinCalcWarning(String warning) {
        logBoth("[INSULIN] ⚠️ WARNING: {}", warning);
    }

    public void insulinCalcComplete(float finalDose, float roundedDose, long timeMs) {
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[INSULIN] CALCULATION COMPLETE", (Object[])null);
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[INSULIN] Raw Dose     : {} units", String.format("%.2f", finalDose));
        logBoth("[INSULIN] Rounded Dose : {} units", String.format("%.1f", roundedDose));
        logBoth("[INSULIN] Time         : {}ms", timeMs);
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("", (Object[])null);
    }

    public void logUserUpdateIncoming(String email, com.insuscan.boundary.UserBoundary update) {
        logBoth("", (Object[])null);
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[USER] 📥 INCOMING PROFILE UPDATE", (Object[])null);
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[USER] Email      : {}", email);
        logBoth("[USER] Name       : {}", update.getUserName());
        logBoth("[USER] --- Personal ---", (Object[])null);
        logBoth("[USER] Age        : {}", update.getAge());
        logBoth("[USER] Gender     : {}", update.getGender());
        logBoth("[USER] --- Medical ---", (Object[])null);
        logBoth("[USER] ICR (Ratio): {}", update.getInsulinCarbRatio());
        logBoth("[USER] ISF        : {}", update.getCorrectionFactor());
        logBoth("[USER] Target     : {}", update.getTargetGlucose());
        logBoth("[USER] --- Settings ---", (Object[])null);
        logBoth("[USER] Rounding   : {}", update.getDoseRounding());
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
    }

    public void logUserEntityBeforeSave(com.insuscan.data.UserEntity entity) {
        logBoth("[USER] 💾 SAVING TO FIRESTORE (ENTITY STATE)", (Object[])null);
        logBoth("[USER] ID         : {}", entity.getId());
        logBoth("[USER] ICR (Float): {}", entity.getInsulinCarbRatio());
        logBoth("[USER] ISF        : {}", entity.getCorrectionFactor());
        logBoth("[USER] Age        : {}", entity.getAge());
        logBoth("[USER] UpdatedAt  : {}", entity.getUpdatedAt());
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("", (Object[])null);
    }

    // Keep original methods if they weren't covered, or map them to logBoth if easy.
    // For now I covered the main ones.

    // ... (rest of the file as needed, but minimal changes to avoid breaking anything)
    
     public void openaiStart(String model, int imageSizeBytes) {
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[GEMINI] REQUEST", (Object[])null);
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[GEMINI] Model       : {}", model);
        logBoth("[GEMINI] Image Size  : {} KB", imageSizeBytes / 1024);
        logBoth("[GEMINI] Status      : Sending to Gemini...", (Object[])null);
    }

    public void openaiCacheHit(String imageHash) {
        logBoth("[GEMINI] CACHE HIT - returning cached result (hash: {}...)", 
            imageHash.substring(0, Math.min(8, imageHash.length())));
    }
    
    public void openaiSuccess(int foodCount, long totalTimeMs) {
        logBoth("[GEMINI] SUCCESS - {} foods detected in {}ms", foodCount, totalTimeMs);
    }
    
    public void usdaStart(String foodName, String normalizedName, List<String> searchTerms) {
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[USDA] NUTRITION LOOKUP", (Object[])null);
        logBoth("--------------------------------------------------------------------------------", (Object[])null);
        logBoth("[USDA] Original Food   : '{}'", foodName);
        logBoth("[USDA] Normalized      : '{}'", normalizedName);
        logBoth("[USDA] Search Terms    : {}", searchTerms);
    }

    public void carbCalculation(String foodName, float carbsPer100g, float portionGrams, float calculatedCarbs, String source) {
        logBoth("[CALC] {} : {}g/100g x {}g portion = {}g carbs [{}]",
            foodName,
            String.format("%.1f", carbsPer100g),
            String.format("%.0f", portionGrams),
            String.format("%.1f", calculatedCarbs),
            source);
    }
    
    public void openaiError(String stage, String message) {
        logBoth("[GEMINI] ❌ ERROR during {}: {}", stage, message);
    }

    public void openaiResponseReceived(long durationMs, int tokenCount) {
        logBoth("[GEMINI] Response received in {}ms. Tokens: {}", durationMs, tokenCount > 0 ? tokenCount : "unknown");
    }

    public void openaiRawResponse(String partialResponse) {
        logBoth("[OPENAI] Raw (partial): {}", partialResponse);
    }
    
    // We use a generic list or Object to avoid circular dependency on recognized food item if possible, 
    // but the error message explicitly mentions RecognizedFoodItem. 
    // Ideally we should import it, but to keep it simple and avoid import errors if that class moved, 
    // we can use a wildcard or just Object and toString() it, but the error said "List<RecognizedFoodItem>".
    // Let's assume the caller passes something that can be stringified.
    public void openaiParsedFoods(List<?> foods) {
        logBoth("[GEMINI] Parsed {} items: {}", foods != null ? foods.size() : 0, foods);
    }

    public void openaiRetry(String reason) {
        logBoth("[GEMINI] ⚠️ Retrying request. Reason: {}", reason);
    }

    public void apiKeyStatus(String service, boolean present, String preview) {
        logBoth("[CONFIG] {} API Key: {} (Preview: {})", 
            service, 
            present ? "PRESENT" : "MISSING", 
            preview);
    }

    public void usdaApiCall(String query) {
        logBoth("[USDA] 🔍 Calling API for: '{}'", query);
    }

    public void usdaApiResponse(long durationMs, int resultsCount, String firstMatch) {
        logBoth("[USDA] Response in {}ms. Found {} results. First: '{}'", 
            durationMs, resultsCount, firstMatch != null ? firstMatch : "none");
    }

    public void usdaMatchFound(String original, String match, float score) {
        logBoth("[USDA] ✅ MATCH: '{}' -> '{}' (Score: {})", 
            original, match, String.format("%.2f", score));
    }
    
    public void usdaFallbackHit(String term, float score) {
        logBoth("[USDA] ⚠️ FALLBACK MATCH for '{}' (Score: {})", 
             term, String.format("%.2f", score));
    }

    public void usdaNoMatch(String term, boolean isSpecific) {
        logBoth("[USDA] ❌ NO MATCH FOUND for '{}' (Specific search: {})", term, isSpecific);
    }

    public void usdaError(String message) {
        logBoth("[USDA] ❌ ERROR: {}", message);
    }
    
    public void insulinCalcError(String message) {
        logBoth("[INSULIN] ❌ ERROR: {}", message);
    }



    public void carbCalculationFailed(String foodName, String reason) {
        logBoth("[CALC] {} : FAILED - {}", foodName, reason);
    }
}