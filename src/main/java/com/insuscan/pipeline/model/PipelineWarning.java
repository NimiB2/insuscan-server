package com.insuscan.pipeline.model;

/**
 * A structured warning emitted by any pipeline stage.
 * Warnings never block the pipeline — they are collected and returned to the client.
 */
public class PipelineWarning {

    public enum Severity {
        INFO,    // Informational — no action needed
        LOW,     // Minor issue — result still usable
        MEDIUM,  // Notable issue — confidence reduced
        HIGH,    // Serious issue — result may be inaccurate
        CRITICAL // Severe issue — pipeline marked as unreliable
    }

    private final String sourceStage;  // e.g. "CALIBRATION", "FOOD_DETECTION"
    private final String code;         // e.g. "RATIO_MISMATCH", "FOOD_NOT_IN_USDA"
    private final String message;      // Human-readable, shown to user if needed
    private final Severity severity;

    public PipelineWarning(String sourceStage, String code, String message, Severity severity) {
        this.sourceStage = sourceStage;
        this.code = code;
        this.message = message;
        this.severity = severity;
    }

    // --- Factories ---

    public static PipelineWarning info(String stage, String code, String message) {
        return new PipelineWarning(stage, code, message, Severity.INFO);
    }

    public static PipelineWarning medium(String stage, String code, String message) {
        return new PipelineWarning(stage, code, message, Severity.MEDIUM);
    }

    public static PipelineWarning high(String stage, String code, String message) {
        return new PipelineWarning(stage, code, message, Severity.HIGH);
    }

    public static PipelineWarning critical(String stage, String code, String message) {
        return new PipelineWarning(stage, code, message, Severity.CRITICAL);
    }

    // --- Getters ---

    public String getSourceStage() { return sourceStage; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Severity getSeverity() { return severity; }

    @Override
    public String toString() {
        return "[" + severity + "] " + sourceStage + "/" + code + ": " + message;
    }
}
