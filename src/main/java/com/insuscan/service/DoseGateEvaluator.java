package com.insuscan.service;

import com.insuscan.pipeline.model.PipelineWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Clinical safety gate that decides whether a calculated dose can be recommended
 * to the user, based on meal validity and pipeline warnings.
 */

@Component
public class DoseGateEvaluator {

	private static final Logger log = LoggerFactory.getLogger(DoseGateEvaluator.class);

	@Value("${insulin.dose-gate.blocking-codes:WEIGHT_OUT_OF_RANGE,MEAL_TOO_HEAVY,AREA_EXCEEDS_PLATE,VOLUME_EXCEEDS_CONTAINER,PLATE_SIZE_OUT_OF_RANGE}")
	private String blockingCodesRaw;

	@Value("${insulin.dose-gate.block-on-critical:true}")
	private boolean blockOnCritical;

	public DoseGateDecision evaluate(List<PipelineWarning> warnings, Float totalWeightG, Float totalCarbsG) {
		List<String> reasons = new ArrayList<>();

		if (totalCarbsG == null || totalWeightG == null || totalWeightG <= 0f) {
			reasons.add("Meal result is invalid (weight=" + totalWeightG + ", carbs=" + totalCarbsG + ")");
		}

		if (warnings != null && !warnings.isEmpty()) {
			Set<String> blockingCodes = parseBlockingCodes();
			for (PipelineWarning w : warnings) {
				boolean criticalBlock = blockOnCritical && isCritical(w);
				boolean codeBlock = blockingCodes.contains(w.getCode());
				if (criticalBlock || codeBlock) {
					reasons.add("[" + w.getCode() + "] " + w.getMessage());
				}
			}
		}

		if (reasons.isEmpty()) {
			return DoseGateDecision.allowed();
		}

		log.warn("[DoseGate] Dose blocked — {} reason(s): {}", reasons.size(), String.join(" | ", reasons));
		return DoseGateDecision.blocked(reasons);
	}

	private Set<String> parseBlockingCodes() {
		return Arrays.stream(blockingCodesRaw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
	}

	private boolean isCritical(PipelineWarning warning) {
		return warning.getSeverity() == PipelineWarning.Severity.CRITICAL;
	}
}