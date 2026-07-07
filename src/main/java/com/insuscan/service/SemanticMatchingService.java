package com.insuscan.service;

import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.boundary.NutritionInfo;
import java.util.List;

/**
 * Ranks USDA nutrition candidates against a visual food detection and returns the best-match fdcId.
 */

public interface SemanticMatchingService {
    /**
     * Receives the visual analysis (Target) and a list of USDA candidates.
     * Returns the fdcId of the best scientific match.
     * Returns null if no good match is found.
     */
	String findBestMatch(PipelineFoodItem visualTarget,
            List<NutritionInfo> candidates,
            String base64Image);
	}
