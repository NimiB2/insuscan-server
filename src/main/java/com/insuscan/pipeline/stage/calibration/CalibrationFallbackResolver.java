
package com.insuscan.pipeline.stage.calibration;

import com.insuscan.enums.ReferenceObjectDimensions;
import com.insuscan.pipeline.stage.CalibrationService.DetectionResult;
import com.insuscan.pipeline.support.ReferenceObjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CalibrationFallbackResolver {

    private static final Logger log = LoggerFactory.getLogger(CalibrationFallbackResolver.class);

    private static final float STANDARD_PLATE_DIAMETER_CM     = 22.0f;
    private static final float STANDARD_PLATE_PIXELS_ESTIMATE = 700f;
    private static final float PLATE_FALLBACK_CONFIDENCE      = 0.3f;

    private final ReferenceObjectRegistry referenceObjectRegistry;

    public CalibrationFallbackResolver(ReferenceObjectRegistry referenceObjectRegistry) {
        this.referenceObjectRegistry = referenceObjectRegistry;
    }

    public enum FallbackSource {
        OBJECT_FOUND,
        ALTERNATIVE_OBJECT_DETECTED,
        PLATE_SIZE_ESTIMATE
    }

    public record FallbackResolution(
        float pixelToCmRatio,
        FallbackSource source,
        float confidence,
        String detectedObjectType,
        String description
    ) {}

    public FallbackResolution resolve(DetectionResult result, String imageView) {
        if (result.found()) {
            return new FallbackResolution(
                result.pixelToCmRatio(),
                FallbackSource.OBJECT_FOUND,
                result.confidence(),
                result.detectedObjectType(),
                "Selected object detected in " + imageView + " image"
            );
        }

        if (result.alternativeObjectType() != null) {
            ReferenceObjectRegistry.Dimensions dims =
                referenceObjectRegistry.getDimensions(result.alternativeObjectType());
            if (dims != null) {
                float ratio = computeRatioFromBbox(result.bboxPx(), dims);
                if (ratio > 0f) {
                    log.info("[CalibrationFallback][{}] Alternative '{}' detected, ratio={}",
                        imageView, result.alternativeObjectType(), ratio);
                    return new FallbackResolution(
                        ratio,
                        FallbackSource.ALTERNATIVE_OBJECT_DETECTED,
                        result.confidence(),
                        result.alternativeObjectType(),
                        "Alternative object '" + result.alternativeObjectType() +
                            "' used in " + imageView + " image"
                    );
                }
            }
        }

        float estimatedRatio = STANDARD_PLATE_PIXELS_ESTIMATE / STANDARD_PLATE_DIAMETER_CM;
        log.warn("[CalibrationFallback][{}] No object found, using plate-size estimate ratio={}",
            imageView, estimatedRatio);
        return new FallbackResolution(
            estimatedRatio,
            FallbackSource.PLATE_SIZE_ESTIMATE,
            PLATE_FALLBACK_CONFIDENCE,
            null,
            "No reference object found in " + imageView +
                " — using standard " + STANDARD_PLATE_DIAMETER_CM + "cm plate estimate"
        );
    }

    private float computeRatioFromBbox(float[] bboxPx, ReferenceObjectRegistry.Dimensions dims) {
        if (bboxPx == null || bboxPx.length < 4) return 0f;
        float widthPx  = bboxPx[2];
        float heightPx = bboxPx[3];
        if (widthPx <= 0 || heightPx <= 0) return 0f;
        float ratioFromLength = widthPx  / dims.lengthCm();
        float ratioFromWidth  = heightPx / dims.widthCm();
        return (ratioFromLength + ratioFromWidth) / 2f;
    }
}