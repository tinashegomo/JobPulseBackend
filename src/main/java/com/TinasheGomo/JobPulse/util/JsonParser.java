package com.TinasheGomo.JobPulse.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonParser {

    private JsonParser() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

    public static JsonNode parseAiJson(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("parseAiJson: empty or null input");
        }

        // Attempt 1: raw parse
        try {
            return MAPPER.readTree(text);
        } catch (Exception ignored) {}

        // Attempt 2: strip code fences
        String stripped = stripCodeFences(text);
        try {
            return MAPPER.readTree(stripped);
        } catch (Exception ignored) {}

        // Attempt 3: repair trailing commas and comments
        String repaired = attemptRepair(stripped);
        try {
            return MAPPER.readTree(repaired);
        } catch (Exception ignored) {}

        // Attempt 4: extract JSON object from surrounding text
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(stripped);
        if (matcher.find()) {
            try {
                return MAPPER.readTree(matcher.group());
            } catch (Exception ignored) {}
        }

        throw new IllegalArgumentException(
                "Failed to parse AI response as JSON. First 200 chars: " + text.substring(0, Math.min(200, text.length())));
    }

    private static String stripCodeFences(String text) {
        return text
                .replaceAll("(?i)^```(?:json)?\\s*\\n?", "")
                .replaceAll("\\n?\\s*```\\s*$", "")
                .trim();
    }

    private static String attemptRepair(String text) {
        String fixed = text;
        fixed = fixed.replaceAll(",\\s*([}\\]])", "$1");
        fixed = fixed.replaceAll("//.*$", "");
        return fixed;
    }
}
