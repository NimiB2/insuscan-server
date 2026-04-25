package com.insuscan.pipeline.calculation;

import com.insuscan.boundary.NutritionInfo;
import com.insuscan.pipeline.model.PipelineFoodItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Calculates physical density (g/cm³) using a 3-tier strategy system.
 * 
 * <p>Strategies:</p>
 * <ol>
 *   <li><b>USDA_SERVING:</b> Uses precise volume-to-weight ratios from USDA foodPortions.</li>
 *   <li><b>PROXIMATE_AIRY:</b> Uses Choi & Okos (1986) proximate composition formula, 
 *       multiplied by the vision model's effectiveDensityFactor (for foods with air gaps).</li>
 *   <li><b>PROXIMATE_CALC:</b> Pure Choi & Okos formula (for solid, homogeneous foods).</li>
 * </ol>
 */
@Service
public class DensityCalculator {

    private static final Logger log = LoggerFactory.getLogger(DensityCalculator.class);

    public void calculateAndSetDensity(PipelineFoodItem item, NutritionInfo usdaInfo) {
        // Strategy 1: USDA Serving Portions (Highest precision, lab measured)
        if (usdaInfo.getDensitySource() != null && "USDA".equals(usdaInfo.getDensitySource())
            && usdaInfo.getDensityGPerCm3() != null) {
            
            float usdaDensity = usdaInfo.getDensityGPerCm3();
            // If the food is airy (factor < 0.95) and we have USDA density, 
            // USDA density usually ALREADY accounts for air (e.g. 1 cup of chopped lettuce).
            // However, our volume is the "envelope" volume. If USDA volume is also envelope, they match.
            item.setDensityGPerCm3(usdaDensity);
            item.setDensitySource(PipelineFoodItem.DensitySource.USDA_SERVING);
            log.info("[Density] {} -> {:.3f} g/cm³ (USDA_SERVING)", item.getName(), usdaDensity);
            return;
        }

        // Strategy 2/3: Choi & Okos Proximate Formula
        Float proximateDensity = usdaInfo.calculateDensityFromProximates();
        if (proximateDensity != null) {
            Float factor = item.getEffectiveDensityFactor();
            if (factor == null || factor <= 0f) {
                factor = 1.0f;
            }

            if (factor < 0.95f) {
                // Strategy 2: Proximate with AI air-gap factor
                float adjustedDensity = proximateDensity * factor;
                item.setDensityGPerCm3(adjustedDensity);
                item.setDensitySource(PipelineFoodItem.DensitySource.PROXIMATE_WITH_TEXTURE);
                log.info("[Density] {} -> {:.3f} g/cm³ (PROXIMATE_WITH_TEXTURE: base {:.3f} * factor {:.2f})", 
                    item.getName(), adjustedDensity, proximateDensity, factor);
            } else {
                // Strategy 3: Pure Proximate (Solid food)
                item.setDensityGPerCm3(proximateDensity);
                item.setDensitySource(PipelineFoodItem.DensitySource.PROXIMATE_CALC);
                log.info("[Density] {} -> {:.3f} g/cm³ (PROXIMATE_CALC)", item.getName(), proximateDensity);
            }
            return;
        }

        // Fallback: Unknown
        item.setDensityGPerCm3(null);
        item.setDensitySource(PipelineFoodItem.DensitySource.UNKNOWN);
        log.warn("[Density] {} -> UNKNOWN (Missing USDA portions and proximates)", item.getName());
    }
}
