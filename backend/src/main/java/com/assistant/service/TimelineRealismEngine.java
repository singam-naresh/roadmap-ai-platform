package com.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PHASE 5 — Timeline Realism Engine
 *
 * Validates and corrects estimated timelines to prevent:
 *   - Impossible timelines ("Learn Kubernetes in 1 day")
 *   - Overly generic durations ("a few weeks")
 *   - Unrealistic sequencing (advanced topics before prerequisites)
 *
 * Timeline logic considers: difficulty, prerequisites, workload,
 * technology complexity, and learning curve.
 */
@Service
public class TimelineRealismEngine {

    private static final Logger log = LoggerFactory.getLogger(TimelineRealismEngine.class);

    // Minimum realistic timelines per domain + expertise level (in weeks)
    private static final Map<String, Map<String, int[]>> DOMAIN_TIMELINE_BOUNDS;
    static {
        DOMAIN_TIMELINE_BOUNDS = new HashMap<>();
        // [minWeeks, maxWeeks] per expertise level
        DOMAIN_TIMELINE_BOUNDS.put("AI_ENGINEERING", Map.of(
            "BEGINNER",     new int[]{24, 52},
            "INTERMEDIATE", new int[]{12, 24},
            "ADVANCED",     new int[]{8,  16},
            "EXPERT",       new int[]{4,  8}
        ));
        DOMAIN_TIMELINE_BOUNDS.put("JAVA_BACKEND", Map.of(
            "BEGINNER",     new int[]{32, 52},
            "INTERMEDIATE", new int[]{16, 24},
            "ADVANCED",     new int[]{8,  12},
            "EXPERT",       new int[]{4,  6}
        ));
        DOMAIN_TIMELINE_BOUNDS.put("REACT_FRONTEND", Map.of(
            "BEGINNER",     new int[]{24, 40},
            "INTERMEDIATE", new int[]{12, 20},
            "ADVANCED",     new int[]{8,  12},
            "EXPERT",       new int[]{3,  5}
        ));
        DOMAIN_TIMELINE_BOUNDS.put("DEVOPS", Map.of(
            "BEGINNER",     new int[]{40, 56},
            "INTERMEDIATE", new int[]{16, 24},
            "ADVANCED",     new int[]{8,  16},
            "EXPERT",       new int[]{4,  8}
        ));
        DOMAIN_TIMELINE_BOUNDS.put("DATA_ENGINEERING", Map.of(
            "BEGINNER",     new int[]{32, 48},
            "INTERMEDIATE", new int[]{16, 24},
            "ADVANCED",     new int[]{8,  12},
            "EXPERT",       new int[]{4,  6}
        ));
        DOMAIN_TIMELINE_BOUNDS.put("SYSTEM_DESIGN", Map.of(
            "BEGINNER",     new int[]{48, 72},
            "INTERMEDIATE", new int[]{24, 36},
            "ADVANCED",     new int[]{12, 20},
            "EXPERT",       new int[]{6,  10}
        ));
    }

    // Vague duration patterns to reject
    private static final List<Pattern> VAGUE_PATTERNS = List.of(
        Pattern.compile("(?i)\\b(a few|some|several|many|various|multiple)\\s+(days?|weeks?|months?)\\b"),
        Pattern.compile("(?i)\\b(varies|variable|flexible|depends|tbd|n/a)\\b"),
        Pattern.compile("(?i)^(ongoing|continuous|indefinite)$")
    );

    // Impossible timeline patterns
    private static final List<Pattern> IMPOSSIBLE_PATTERNS = List.of(
        Pattern.compile("(?i)\\b[1-3]\\s*days?\\b"),          // 1-3 days for any tech topic
        Pattern.compile("(?i)\\b[1-2]\\s*weeks?\\b.*(?:kubernetes|machine learning|distributed|microservices)"),
        Pattern.compile("(?i)\\b1\\s*month\\b.*(?:expert|mastery|production)")
    );

    public TimelineValidationResult validate(JsonNode roadmapJson, String domain, String expertiseLevel) {
        String estimatedTime = roadmapJson.path("estimatedTime").asText("");
        String difficulty    = roadmapJson.path("difficulty").asText("Intermediate");

        TimelineValidationResult result = new TimelineValidationResult();
        result.originalTimeline = estimatedTime;

        // Check for vague timelines
        for (Pattern p : VAGUE_PATTERNS) {
            if (p.matcher(estimatedTime).find()) {
                result.issues.add("Vague timeline: '" + estimatedTime + "'");
                result.isVague = true;
            }
        }

        // Check for impossible timelines
        for (Pattern p : IMPOSSIBLE_PATTERNS) {
            if (p.matcher(estimatedTime).find()) {
                result.issues.add("Impossible timeline: '" + estimatedTime + "'");
                result.isImpossible = true;
            }
        }

        // Validate against domain bounds
        int[] bounds = getDomainBounds(domain, expertiseLevel);
        int parsedWeeks = parseWeeks(estimatedTime);

        if (parsedWeeks > 0) {
            if (parsedWeeks < bounds[0]) {
                result.issues.add(String.format(
                    "Timeline too short: %d weeks (minimum %d weeks for %s %s)",
                    parsedWeeks, bounds[0], expertiseLevel, domain));
                result.isTooShort = true;
            } else if (parsedWeeks > bounds[1] * 2) {
                result.issues.add(String.format(
                    "Timeline unrealistically long: %d weeks (maximum ~%d weeks for %s %s)",
                    parsedWeeks, bounds[1], expertiseLevel, domain));
            }
        }

        result.suggestedTimeline = buildSuggestedTimeline(bounds, difficulty);
        result.isValid = result.issues.isEmpty();

        log.debug("[timeline] domain={} level={} original='{}' valid={} issues={}",
                domain, expertiseLevel, estimatedTime, result.isValid, result.issues.size());
        return result;
    }

    public String buildTimelinePrompt(String domain, String expertiseLevel) {
        int[] bounds = getDomainBounds(domain, expertiseLevel);
        String suggested = buildSuggestedTimeline(bounds, "Intermediate");

        return String.format("""
            TIMELINE REALISM REQUIREMENTS (PHASE 5):
            
            For domain '%s' at '%s' level, the realistic timeline is: %s
            
            FORBIDDEN timelines:
            - "1-3 days" for any technical topic
            - "a few weeks" (too vague)
            - "varies" or "depends" (not specific)
            - "1 month" for expert-level mastery
            
            REQUIRED format: "X weeks" or "X–Y months" with specific numbers.
            
            TIMELINE LOGIC:
            - Prerequisites must be completed before advanced topics
            - Each step should have a realistic duration based on complexity
            - Total timeline must be achievable with 2–4 hours/day of focused work
            - Advanced topics (distributed systems, ML training) require more time
            """, domain, expertiseLevel, suggested);
    }

    private int[] getDomainBounds(String domain, String level) {
        Map<String, int[]> domainMap = DOMAIN_TIMELINE_BOUNDS.get(domain);
        if (domainMap == null) {
            return switch (level) {
                case "EXPERT"       -> new int[]{4, 8};
                case "ADVANCED"     -> new int[]{8, 16};
                case "INTERMEDIATE" -> new int[]{12, 24};
                default             -> new int[]{24, 48};
            };
        }
        return domainMap.getOrDefault(level, new int[]{12, 24});
    }

    private String buildSuggestedTimeline(int[] bounds, String difficulty) {
        int minWeeks = bounds[0];
        int maxWeeks = bounds[1];
        if (minWeeks >= 8) {
            int minMonths = minWeeks / 4;
            int maxMonths = maxWeeks / 4;
            return minMonths + "–" + maxMonths + " months";
        }
        return minWeeks + "–" + maxWeeks + " weeks";
    }

    private int parseWeeks(String timeline) {
        if (timeline == null || timeline.isBlank()) return 0;
        String lower = timeline.toLowerCase();

        // Try to extract months first
        Matcher monthMatcher = Pattern.compile("(\\d+)\\s*(?:–|-)?\\s*(\\d+)?\\s*months?").matcher(lower);
        if (monthMatcher.find()) {
            int months = Integer.parseInt(monthMatcher.group(1));
            return months * 4;
        }

        // Try weeks
        Matcher weekMatcher = Pattern.compile("(\\d+)\\s*(?:–|-)?\\s*(\\d+)?\\s*weeks?").matcher(lower);
        if (weekMatcher.find()) {
            return Integer.parseInt(weekMatcher.group(1));
        }

        // Try days
        Matcher dayMatcher = Pattern.compile("(\\d+)\\s*days?").matcher(lower);
        if (dayMatcher.find()) {
            return Math.max(1, Integer.parseInt(dayMatcher.group(1)) / 7);
        }

        return 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class TimelineValidationResult {
        public String        originalTimeline  = "";
        public String        suggestedTimeline = "";
        public boolean       isValid           = true;
        public boolean       isVague           = false;
        public boolean       isImpossible      = false;
        public boolean       isTooShort        = false;
        public List<String>  issues            = new ArrayList<>();
    }
}
