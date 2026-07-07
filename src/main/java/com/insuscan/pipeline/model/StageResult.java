package com.insuscan.pipeline.model;

/**
 * Generic wrapper for the result of a single pipeline stage.
 *
 * <p>Every service in the pipeline returns {@code StageResult<T>} instead of
 * throwing exceptions or returning null. This makes failure handling explicit
 * and uniform across all stages.</p>
 *
 * <p>Failure severity:</p>
 * <ul>
 *   <li>{@link FailureSeverity#FATAL} — pipeline must stop immediately.</li>
 *   <li>{@link FailureSeverity#DEGRADED} — pipeline continues with lowered confidence.</li>
 * </ul>
 */
public class StageResult<T> {

    public enum FailureSeverity {
        FATAL,    // Stop the pipeline, return error to client
        DEGRADED  // Continue, but mark overall confidence as lower
    }


    private final T value;
    private final float confidence;    // 0.0–1.0, meaningful only on success
    private final boolean success;
    private final String failureReason;
    private final FailureSeverity failureSeverity;


    private StageResult(T value, float confidence, boolean success,
                        String failureReason, FailureSeverity failureSeverity) {
        this.value = value;
        this.confidence = confidence;
        this.success = success;
        this.failureReason = failureReason;
        this.failureSeverity = failureSeverity;
    }

    // ── Factories ─────────────────────────────────────────────────────────

    public static <T> StageResult<T> success(T value, float confidence) {
        return new StageResult<>(value, confidence, true, null, null);
    }

    /** Convenience for stages where confidence is not relevant (pure math). */
    public static <T> StageResult<T> success(T value) {
        return success(value, 1.0f);
    }

    public static <T> StageResult<T> fatal(String reason) {
        return new StageResult<>(null, 0f, false, reason, FailureSeverity.FATAL);
    }

    public static <T> StageResult<T> degraded(T partialValue, float confidence, String reason) {
        return new StageResult<>(partialValue, confidence, false, reason, FailureSeverity.DEGRADED);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public boolean isSuccess() { return success; }
    public boolean isFatal() { return !success && failureSeverity == FailureSeverity.FATAL; }
    public boolean isDegraded() { return !success && failureSeverity == FailureSeverity.DEGRADED; }
    public T getValue() { return value; }
    public float getConfidence() { return confidence; }
    public String getFailureReason() { return failureReason; }
    public FailureSeverity getFailureSeverity() { return failureSeverity; }

    @Override
    public String toString() {
        if (success) return "StageResult{SUCCESS, confidence=" + confidence + "}";
        return "StageResult{" + failureSeverity + ", reason='" + failureReason + "'}";
    }
}