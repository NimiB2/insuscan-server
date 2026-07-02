package com.insuscan.service;

import java.util.List;

/**
 * Outcome of a dose gate evaluation: either allowed, or blocked with reasons.
 */

public record DoseGateDecision(boolean blocked, List<String> reasons) {

	public static DoseGateDecision allowed() {
		return new DoseGateDecision(false, List.of());
	}

	public static DoseGateDecision blocked(List<String> reasons) {
		return new DoseGateDecision(true, reasons);
	}

	public String summary() {
		return String.join("; ", reasons);
	}
}