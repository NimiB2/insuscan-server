package com.insuscan.util;

import org.springframework.stereotype.Component;

@Component
public class FoodTextureClassifier {

    public enum TextureType {
        SOLID,
        GRANULAR,
        LEAFY,
        MIXED;

        public float getPackingFactor() {
            switch (this) {
                case SOLID:    return 1.00f;
                case GRANULAR: return 0.55f;
                case LEAFY:    return 0.20f;
                case MIXED:    return 0.70f;
                default:       return 0.75f;
            }
        }
    }

    public TextureType classify(String textureTypeFromGemini) {
        if (textureTypeFromGemini != null) {
            try {
                return TextureType.valueOf(textureTypeFromGemini.toUpperCase().trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return TextureType.SOLID;
    }
}