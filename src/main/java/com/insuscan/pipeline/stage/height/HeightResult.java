package com.insuscan.pipeline.stage.height;

/**
 * A single food item's height estimate parsed from Gemini's side-view response.
 *
 * @param isVisible  whether the item was visible in the side image (false triggers a plate-depth fallback)
 * @param heightCm   the effective height in centimeters
 * @param confidence the model's confidence in this estimate (0.0–1.0)
 */
public record HeightResult(boolean isVisible, float heightCm, float confidence) {
}