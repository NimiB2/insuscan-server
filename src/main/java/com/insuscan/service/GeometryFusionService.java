package com.insuscan.service;

import com.insuscan.boundary.FusedMeasurement;
import java.util.List;

/**
 * Fuses AR-based measurements with vision-model estimates to produce robust plate and food-height dimensions.
 */
public interface GeometryFusionService {

    FusedMeasurement fuseDiameter(Float arDiameterCm, String topImageBase64,
            String sideImageBase64, String referenceObjectType, Float pixelToCmRatio);

    FusedMeasurement fuseDepth(Float arDepthCm, String topImageBase64,
            String sideImageBase64, String referenceObjectType, Float pixelToCmRatio);

    List<FusedMeasurement> fuseFoodHeights(List<String> foodNames, List<String> heightCategories,
            String sideImageBase64, String referenceObjectType, Float pixelToCmRatio);
}