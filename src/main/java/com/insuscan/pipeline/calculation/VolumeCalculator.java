package com.insuscan.pipeline.calculation;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 6 — Pure mathematics layer to calculate volume.
 *
 * <p>Formula: Volume = Area × EffectiveHeight.
 * No AI or heuristics are used here.</p>
 */
@Service
public class VolumeCalculator {

    private static final Logger log = LoggerFactory.getLogger(VolumeCalculator.class);

    private final PipelineWarningCollector warningCollector;

    public VolumeCalculator(PipelineWarningCollector warningCollector) {
        this.warningCollector = warningCollector;
    }

    public void calculateVolumes(PipelineContext ctx) {
        float totalVolume = 0f;

        for (PipelineFoodItem item : ctx.getFoodItems()) {
        	if (item.getAreaCm2() != null && item.getEffectiveHeightCm() != null) {
                float volume = item.getAreaCm2() * item.getEffectiveHeightCm();
                item.setVolumeCm3(volume);
                totalVolume += volume;
                log.info("[Volume] {} -> {} cm³", item.getName(), String.format("%.1f", volume));
            } else {
                log.warn("[Volume] Missing area or height for {}", item.getName());
            }
        }

        if (ctx.getPlateGeometry() != null && totalVolume > 0) {
            PipelineContext.PlateGeometry geom = ctx.getPlateGeometry();
            
            // Treat flat plates as shallow cylinders for capacity estimation
            float diameter = geom.getInnerDiameterCm();
            float depth = geom.getInnerDepthCm();
            
            // Check against absolute physical capacity, not the model's fill estimate.
            float maxVolume = (float) (Math.PI * Math.pow(diameter / 2, 2) * depth);
            
            if (totalVolume > maxVolume) {
                warningCollector.totalVolumeExceedsContainer(ctx, totalVolume, maxVolume);
            }
        }
    }
}
