package com.insuscan.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Centralized logging for the InsuScan server.
 * Covers meal insulin calculation, user profile updates, and external API calls
 * to Gemini, OpenAI, and USDA. Every message goes to both the SLF4J console
 * and the on-disk file logger so we have a persistent audit trail.
 */
@Component
public class ApiLogger {

    private static final Logger log = LoggerFactory.getLogger(ApiLogger.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ServerFileLogger fileLogger;

    public ApiLogger(ServerFileLogger fileLogger) {
        this.fileLogger = fileLogger;
    }

    // Writes an empty separator line to visually break log sections
    private void blank() {
        logBoth("");
    }

    // Overload for messages without arguments, avoids the ugly (Object[])null cast
    private void logBoth(String message) {
        logBoth(message, (Object[]) null);
    }

    // Writes to SLF4J console and file logger. File writes never break the caller
    private void logBoth(String format, Object... args) {
        log.info(format, args);
        try {
            String msg = format;
            if (args != null) {
                for (Object arg : args) {
                    msg = msg.replaceFirst("\\{\\}", String.valueOf(arg));
                }
            }
            fileLogger.append(msg);
        } catch (Exception ignored) {
        }
    }

    // ===================== INSULIN CALCULATION =====================

    public void insulinCalcStart(Float totalCarbs, Integer currentGlucose, String activityLevel,
                                 Boolean sickMode, Boolean stressMode, String userEmail) {
        blank();
        logBoth("---------------- INSULIN CALCULATION STARTED ----------------");
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
            logBoth("[INSULIN] Profile status: COMPLETE ✓");
        } else {
            logBoth("[INSULIN] Profile status: INCOMPLETE");
            logBoth("[INSULIN] Missing fields: {}", missingFields);
        }
    }

    public void insulinCalcSkipped(List<String> reasons) {
        logBoth("[INSULIN] Calculation SKIPPED - profile incomplete");
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
        logBoth("---------------- INSULIN CALCULATION COMPLETE ---------------");
        logBoth("[INSULIN] Raw Dose     : {} units", String.format("%.2f", finalDose));
        logBoth("[INSULIN] Rounded Dose : {} units", String.format("%.1f", roundedDose));
        logBoth("[INSULIN] Time         : {}ms", timeMs);
        blank();
    }

    public void insulinCalcError(String message) {
        logBoth("[INSULIN] ❌ ERROR: {}", message);
    }

    // ===================== USER PROFILE =====================

    public void logUserUpdateIncoming(String email, com.insuscan.boundary.UserBoundary update) {
        blank();
        logBoth("---------------- INCOMING PROFILE UPDATE 📥 ----------------");
        logBoth("[USER] Email      : {}", email);
        logBoth("[USER] Name       : {}", update.getUserName());
        logBoth("[USER] --- Personal ---");
        logBoth("[USER] Age        : {}", update.getAge());
        logBoth("[USER] Gender     : {}", update.getGender());
        logBoth("[USER] --- Medical ---");
        logBoth("[USER] ICR (Ratio): {}", update.getInsulinCarbRatio());
        logBoth("[USER] ISF        : {}", update.getCorrectionFactor());
        logBoth("[USER] Target     : {}", update.getTargetGlucose());
        logBoth("[USER] --- Settings ---");
        logBoth("[USER] Rounding   : {}", update.getDoseRounding());
    }

    public void logUserEntityBeforeSave(com.insuscan.data.UserEntity entity) {
        logBoth("[USER] 💾 SAVING TO FIRESTORE");
        logBoth("[USER] ID         : {}", entity.getId());
        logBoth("[USER] ICR (Float): {}", entity.getInsulinCarbRatio());
        logBoth("[USER] ISF        : {}", entity.getCorrectionFactor());
        logBoth("[USER] Age        : {}", entity.getAge());
        logBoth("[USER] UpdatedAt  : {}", entity.getUpdatedAt());
        blank();
    }

    // ===================== GEMINI / OPENAI =====================

    public void openaiStart(String model, int imageBase64Chars) {
        logBoth("-------------------- GEMINI REQUEST --------------------");
        logBoth("[GEMINI] Model       : {}", model);
        logBoth("[GEMINI] Image Size  : {} KB", (int) (imageBase64Chars * 0.75 / 1024));
        logBoth("[GEMINI] Status      : Sending to Gemini...");
    }

    public void openaiSuccess(int foodCount, long totalTimeMs) {
        logBoth("[GEMINI] SUCCESS - {} foods detected in {}ms", foodCount, totalTimeMs);
    }

    public void openaiError(String stage, String message) {
        logBoth("[GEMINI] ❌ ERROR during {}: {}", stage, message);
    }

    public void openaiResponseReceived(long durationMs, int tokenCount) {
        logBoth("[GEMINI] Response received in {}ms. Tokens: {}",
            durationMs, tokenCount > 0 ? tokenCount : "unknown");
    }

    public void openaiParsedFoods(List<?> foods) {
        logBoth("[GEMINI] Parsed {} items: {}", foods != null ? foods.size() : 0, foods);
    }
    
    public void openaiRawResponse(String partialResponse) {
        logBoth("[GEMINI] Raw (partial): {}", partialResponse);
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

    // ===================== USDA =====================

    public void usdaStart(String foodName, String normalizedName, List<String> searchTerms) {
        logBoth("-------------------- USDA NUTRITION LOOKUP --------------------");
        logBoth("[USDA] Original Food   : '{}'", foodName);
        logBoth("[USDA] Normalized      : '{}'", normalizedName);
        logBoth("[USDA] Search Terms    : {}", searchTerms);
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
}