package com.insuscan.pipeline.stage.height;

import org.springframework.stereotype.Component;

@Component
public class HeightResponseSanitizer {

    public String sanitize(String raw) {
        if (raw == null) return null;
        String cleaned = stripMarkdownFences(raw.trim());
        cleaned = extractJsonArray(cleaned);
        cleaned = repairTruncatedArray(cleaned);
        return cleaned;
    }

    private String stripMarkdownFences(String s) {
        return s.replaceAll("(?s)```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();
    }

    private String extractJsonArray(String s) {
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        if (start >= 0) return s.substring(start);
        return s;
    }

    private String repairTruncatedArray(String s) {
        if (s.isEmpty() || !s.startsWith("[")) return "[]";
        if (s.endsWith("]")) return s;

        int lastCompleteEnd = findLastCompleteObjectEnd(s);
        if (lastCompleteEnd < 0) return "[]";
        return s.substring(0, lastCompleteEnd + 1) + "]";
    }

    private int findLastCompleteObjectEnd(String s) {
        int depth = 0;
        int lastEnd = -1;
        boolean inString = false;
        boolean escape = false;

        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) lastEnd = i;
            }
        }
        return lastEnd;
    }
}