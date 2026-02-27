package com.insuscan.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiJsonParser {

    private static final Logger log = LoggerFactory.getLogger(OpenAiJsonParser.class);

    private final ObjectMapper objectMapper;

    public OpenAiJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extractContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    return message.get("content").asText();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[OpenAiJsonParser] Failed to extract content: {}", e.getMessage());
            return null;
        }
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

    public JsonNode parseContent(String rawResponse) {
        String content = extractContent(rawResponse);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("No content in OpenAI response");
        }
        try {
            String json = extractJsonObject(content);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JSON from content: " + e.getMessage(), e);
        }
    }
}