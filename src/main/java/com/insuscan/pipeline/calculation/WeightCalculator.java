package com.insuscan.pipeline.calculation;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 8 — Pure mathematics layer to calculate physical weight.
 *
 * <p>Formula: Weight = Volume × Density.</p>
 */
@Service
public class WeightCalculator {

    private static final Logger log = LoggerFactory.getLogger(WeightCalculator.class);

    private final PipelineWarningCollector warningCollector;

    public WeightCalculator(PipelineWarningCollector warningCollector) {
        this.warningCollector = warningCollector;
    }

    public void calculateWeights(PipelineContext ctx) {
        for (PipelineFoodItem item : ctx.getFoodItems()) {
            if (item.canCalculateWeight()) {
                float weight = item.getVolumeCm3() * item.getDensityGPerCm3();
                item.setWeightG(weight);
                
                log.info("[Weight] {} -> {:.1f}g", item.getName(), weight);

                if (weight < 3f || weight > 500f) {
                    warningCollector.weightOutOfRange(ctx, item.getName(), weight);
                }
            } else {
                log.warn("[Weight] Cannot calculate weight for '{}'. Missing volume or density (densitySource={})", 
                    item.getName(), item.getDensitySource());
            }
        }
    }
}
