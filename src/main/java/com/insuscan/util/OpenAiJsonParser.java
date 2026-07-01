package com.insuscan.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extracts a JSON object substring from raw LLM text responses.
 * Handles the common case where models wrap JSON in prose or markdown code fences.
 */
public class OpenAiJsonParser {

    private final ObjectMapper objectMapper;

    public OpenAiJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Empty response — cannot extract JSON");
        }
        String trimmed = raw.strip();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalStateException("Could not locate JSON object in response");
        }
        return raw.substring(start, end + 1);
    }
}