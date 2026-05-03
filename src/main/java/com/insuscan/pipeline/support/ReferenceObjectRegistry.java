package com.insuscan.pipeline.support;

import com.insuscan.enums.ReferenceObjectDimensions;
import org.springframework.stereotype.Component;

/**
 * Registry of physical dimensions for all supported reference objects.
 *
 * <p>This class wraps the existing {@link ReferenceObjectDimensions} enum and
 * provides a clean interface for the pipeline. Adding a new reference object type
 * only requires adding an entry to {@link ReferenceObjectDimensions} — no logic changes.</p>
 */
@Component
public class ReferenceObjectRegistry {

    /**
     * Physical dimensions of a reference object.
     */
    public record Dimensions(float lengthCm, float widthCm, float sideViewDimensionCm, String description) {}

    /**
     * Returns the physical dimensions for the given reference object type string.
     * Returns null if the type is unknown or NONE.
     *
     * @param serverValue e.g. "INSULIN_SYRINGE", "CARD", "SYRINGE_KNIFE"
     * @return dimensions, or null if type not supported
     */
    public Dimensions getDimensions(String serverValue) {
        ReferenceObjectDimensions ref = ReferenceObjectDimensions.fromServerValue(serverValue);
        if (ref == ReferenceObjectDimensions.NONE) return null;
        return new Dimensions(ref.getLengthCm(), ref.getWidthCm(), ref.getSideViewDimensionCm(), ref.getScaleDescription());
    }

    /**
     * Returns true if the type is a recognized, non-NONE reference object.
     */
    public boolean isSupported(String serverValue) {
        return getDimensions(serverValue) != null;
    }

    /**
     * Returns a human-readable description for use in prompts and logs.
     * Example: "CREDIT CARD / ID CARD — width=8.56cm, height=5.398cm"
     */
    public String getDescription(String serverValue) {
        ReferenceObjectDimensions ref = ReferenceObjectDimensions.fromServerValue(serverValue);
        return ref.getScaleDescription();
    }
}
