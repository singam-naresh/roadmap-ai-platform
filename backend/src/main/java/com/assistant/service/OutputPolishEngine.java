package com.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * PHASE 5 — Output Polish Engine
 *
 * Post-processing pass that fixes:
 *   - Grammar issues (double spaces, capitalization)
 *   - Formatting inconsistencies
 *   - Markdown consistency
 *   - Duplicated phrases within a step
 *   - Broken sections
 *   - Readability improvements
 *
 * Operates on the parsed JSON — no AI call needed.
 */
@Service
public class OutputPolishEngine {

    private static final Logger log = LoggerFactory.getLogger(OutputPolishEngine.class);

    private final ObjectMapper objectMapper;

    public OutputPolishEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode polish(JsonNode roadmapJson) {
        try {
            ObjectNode polished = roadmapJson.deepCopy();

            // Polish summary
            if (polished.has("summary")) {
                polished.put("summary", polishText(polished.get("summary").asText()));
            }

            // Polish steps
            if (polished.has("steps") && polished.get("steps").isArray()) {
                ArrayNode polishedSteps = objectMapper.createArrayNode();
                for (JsonNode step : polished.get("steps")) {
                    polishedSteps.add(polishStep(step.asText()));
                }
                polished.set("steps", polishedSteps);
            }

            // Polish tips
            if (polished.has("tips") && polished.get("tips").isArray()) {
                ArrayNode polishedTips = objectMapper.createArrayNode();
                for (JsonNode tip : polished.get("tips")) {
                    polishedTips.add(polishText(tip.asText()));
                }
                polished.set("tips", polishedTips);
            }

            // Polish mistakesToAvoid
            if (polished.has("mistakesToAvoid") && polished.get("mistakesToAvoid").isArray()) {
                ArrayNode polishedMistakes = objectMapper.createArrayNode();
                for (JsonNode mistake : polished.get("mistakesToAvoid")) {
                    polishedMistakes.add(polishText(mistake.asText()));
                }
                polished.set("mistakesToAvoid", polishedMistakes);
            }

            // Normalize difficulty
            if (polished.has("difficulty")) {
                polished.put("difficulty", normalizeDifficulty(polished.get("difficulty").asText()));
            }

            // Normalize estimatedTime
            if (polished.has("estimatedTime")) {
                polished.put("estimatedTime", normalizeTimeline(polished.get("estimatedTime").asText()));
            }

            log.debug("[polish] Polished roadmap JSON");
            return polished;

        } catch (Exception e) {
            log.warn("[polish] Failed to polish JSON: {}", e.getMessage());
            return roadmapJson; // Return original on failure
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Polish methods
    // ─────────────────────────────────────────────────────────────────────────

    String polishStep(String step) {
        if (step == null || step.isBlank()) return step;

        String polished = step.trim();

        // Fix double spaces
        polished = polished.replaceAll("\\s{2,}", " ");

        // Ensure first letter is capitalized
        if (!polished.isEmpty() && Character.isLowerCase(polished.charAt(0))) {
            polished = Character.toUpperCase(polished.charAt(0)) + polished.substring(1);
        }

        // Remove trailing punctuation inconsistencies (keep periods, remove trailing commas)
        polished = polished.replaceAll(",\\s*$", "");

        // Fix common markdown issues
        polished = polished.replaceAll("\\*{3,}", "**"); // *** → **
        polished = polished.replaceAll("`{2,}", "`");    // `` → `

        // Remove duplicate adjacent phrases
        polished = removeDuplicatePhrases(polished);

        // Fix "and and", "the the", etc.
        polished = polished.replaceAll("(?i)\\b(\\w+)\\s+\\1\\b", "$1");

        return polished;
    }

    String polishText(String text) {
        if (text == null || text.isBlank()) return text;

        String polished = text.trim();
        polished = polished.replaceAll("\\s{2,}", " ");

        // Capitalize first letter
        if (!polished.isEmpty() && Character.isLowerCase(polished.charAt(0))) {
            polished = Character.toUpperCase(polished.charAt(0)) + polished.substring(1);
        }

        // Fix repeated words
        polished = polished.replaceAll("(?i)\\b(\\w+)\\s+\\1\\b", "$1");

        return polished;
    }

    private String removeDuplicatePhrases(String text) {
        // Split on common delimiters and remove exact duplicates
        String[] parts = text.split(",\\s*");
        if (parts.length <= 1) return text;

        List<String> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            String normalized = part.trim().toLowerCase();
            if (seen.add(normalized)) {
                unique.add(part.trim());
            }
        }
        return String.join(", ", unique);
    }

    private String normalizeDifficulty(String difficulty) {
        if (difficulty == null) return "Intermediate";
        return switch (difficulty.toLowerCase().trim()) {
            case "beginner", "easy", "basic", "novice"         -> "Beginner";
            case "intermediate", "medium", "moderate"          -> "Intermediate";
            case "advanced", "hard", "difficult", "expert"     -> "Advanced";
            default -> difficulty.length() > 0
                    ? Character.toUpperCase(difficulty.charAt(0)) + difficulty.substring(1).toLowerCase()
                    : "Intermediate";
        };
    }

    private String normalizeTimeline(String timeline) {
        if (timeline == null || timeline.isBlank()) return "3–6 months";

        String t = timeline.trim();

        // Normalize "3-6 months" → "3–6 months" (en-dash)
        t = t.replaceAll("(\\d+)\\s*-\\s*(\\d+)", "$1–$2");

        // Normalize "3 to 6 months" → "3–6 months"
        t = t.replaceAll("(\\d+)\\s+to\\s+(\\d+)", "$1–$2");

        return t;
    }
}
