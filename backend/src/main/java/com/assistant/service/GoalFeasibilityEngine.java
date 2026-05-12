package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PHASE 6 — Goal Feasibility Engine
 *
 * Detects unrealistic requests BEFORE generation and returns:
 *   - Why the goal is unrealistic
 *   - The minimum realistic estimate
 *   - An accelerated alternative roadmap offer
 *
 * Examples caught:
 *   "Become expert AI engineer in 2 weeks"
 *   "Master distributed systems in 5 days"
 *   "Learn Kubernetes in 1 day"
 */
@Service
public class GoalFeasibilityEngine {

    private static final Logger log = LoggerFactory.getLogger(GoalFeasibilityEngine.class);

    // Minimum realistic weeks per domain + level
    private static final Map<String, Map<String, Integer>> MIN_WEEKS;
    static {
        MIN_WEEKS = new HashMap<>();
        MIN_WEEKS.put("ai", Map.of("beginner", 24, "intermediate", 12, "advanced", 8, "expert", 4));
        MIN_WEEKS.put("machine learning", Map.of("beginner", 24, "intermediate", 12, "advanced", 8, "expert", 4));
        MIN_WEEKS.put("java", Map.of("beginner", 20, "intermediate", 10, "advanced", 6, "expert", 3));
        MIN_WEEKS.put("spring boot", Map.of("beginner", 16, "intermediate", 8, "advanced", 4, "expert", 2));
        MIN_WEEKS.put("kubernetes", Map.of("beginner", 20, "intermediate", 10, "advanced", 6, "expert", 3));
        MIN_WEEKS.put("devops", Map.of("beginner", 24, "intermediate", 12, "advanced", 8, "expert", 4));
        MIN_WEEKS.put("distributed systems", Map.of("beginner", 36, "intermediate", 20, "advanced", 12, "expert", 6));
        MIN_WEEKS.put("react", Map.of("beginner", 16, "intermediate", 8, "advanced", 4, "expert", 2));
        MIN_WEEKS.put("frontend", Map.of("beginner", 20, "intermediate", 10, "advanced", 6, "expert", 3));
        MIN_WEEKS.put("backend", Map.of("beginner", 24, "intermediate", 12, "advanced", 8, "expert", 4));
        MIN_WEEKS.put("system design", Map.of("beginner", 36, "intermediate", 20, "advanced", 12, "expert", 6));
        MIN_WEEKS.put("data engineering", Map.of("beginner", 24, "intermediate", 12, "advanced", 8, "expert", 4));
        MIN_WEEKS.put("cybersecurity", Map.of("beginner", 28, "intermediate", 16, "advanced", 10, "expert", 5));
        MIN_WEEKS.put("cloud", Map.of("beginner", 20, "intermediate", 10, "advanced", 6, "expert", 3));
        MIN_WEEKS.put("terraform", Map.of("beginner", 12, "intermediate", 6, "advanced", 3, "expert", 2));
        MIN_WEEKS.put("docker", Map.of("beginner", 8, "intermediate", 4, "advanced", 2, "expert", 1));
        MIN_WEEKS.put("programming", Map.of("beginner", 24, "intermediate", 12, "advanced", 8, "expert", 4));
        MIN_WEEKS.put("software engineering", Map.of("beginner", 36, "intermediate", 20, "advanced", 12, "expert", 6));
    }

    // Mastery/expert level keywords
    private static final Set<String> MASTERY_KEYWORDS = Set.of(
        "master", "mastery", "expert", "expert-level", "professional", "senior",
        "become an expert", "become expert", "fully learn", "completely learn",
        "learn everything", "know everything", "deep expertise", "production-ready"
    );

    // Time extraction patterns
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(?i)\\bin\\s+(\\d+)\\s*(day|days|week|weeks|month|months|hour|hours)\\b"
    );

    public FeasibilityResult assess(String userInput) {
        String lower = userInput.toLowerCase();

        // Extract requested timeline
        RequestedTimeline timeline = extractTimeline(lower);
        if (timeline == null) {
            // No explicit timeline — feasible by default
            return FeasibilityResult.feasible();
        }

        // Detect domain
        String domain = detectDomain(lower);
        if (domain == null) {
            return FeasibilityResult.feasible();
        }

        // Detect target level
        String level = detectLevel(lower);

        // Get minimum realistic weeks
        Map<String, Integer> domainMinWeeks = MIN_WEEKS.get(domain);
        if (domainMinWeeks == null) {
            return FeasibilityResult.feasible();
        }
        int minWeeks = domainMinWeeks.getOrDefault(level, domainMinWeeks.get("intermediate"));

        // Compare requested vs minimum
        int requestedWeeks = timeline.toWeeks();
        if (requestedWeeks >= minWeeks) {
            return FeasibilityResult.feasible();
        }

        // Unrealistic — build explanation
        String minTimeStr = minWeeks >= 8 ? (minWeeks / 4) + " months" : minWeeks + " weeks";
        String requestedStr = timeline.originalText;

        String explanation = buildExplanation(domain, level, requestedStr, minTimeStr);
        String acceleratedOffer = buildAcceleratedOffer(domain, level, minWeeks);

        log.info("[feasibility] Unrealistic: domain={} level={} requested={}w min={}w",
                domain, level, requestedWeeks, minWeeks);

        return FeasibilityResult.unrealistic(explanation, minTimeStr, acceleratedOffer, domain, level);
    }

    private RequestedTimeline extractTimeline(String input) {
        Matcher m = TIME_PATTERN.matcher(input);
        if (!m.find()) return null;

        int amount = Integer.parseInt(m.group(1));
        String unit = m.group(2).toLowerCase();
        String original = m.group(0).trim();

        return new RequestedTimeline(amount, unit, original);
    }

    private String detectDomain(String input) {
        // Check longest match first
        List<String> domains = new ArrayList<>(MIN_WEEKS.keySet());
        domains.sort((a, b) -> b.length() - a.length());
        for (String domain : domains) {
            if (input.contains(domain)) return domain;
        }
        return null;
    }

    private String detectLevel(String input) {
        if (MASTERY_KEYWORDS.stream().anyMatch(input::contains)) return "expert";
        if (input.contains("advanced") || input.contains("senior")) return "advanced";
        if (input.contains("intermediate") || input.contains("mid-level")) return "intermediate";
        if (input.contains("beginner") || input.contains("from scratch") || input.contains("zero")) return "beginner";
        return "intermediate"; // default
    }

    private String buildExplanation(String domain, String level, String requested, String minimum) {
        String domainDisplay = domain.substring(0, 1).toUpperCase() + domain.substring(1);
        String levelDisplay  = level.substring(0, 1).toUpperCase() + level.substring(1);

        return String.format(
            "Reaching %s-level proficiency in %s %s is not achievable in %s. " +
            "Here's why:\n\n" +
            "• %s requires hands-on project experience, not just theory\n" +
            "• Production-grade skills need repeated practice across real scenarios\n" +
            "• The industry minimum for %s %s is %s of focused study (2–4 hours/day)\n\n" +
            "Rushing this timeline leads to surface-level knowledge that won't hold up in interviews or production.",
            levelDisplay, domainDisplay, domain.equals(level) ? "" : "(" + level + ")",
            requested, domainDisplay, levelDisplay, domainDisplay, minimum
        );
    }

    private String buildAcceleratedOffer(String domain, String level, int minWeeks) {
        int acceleratedWeeks = Math.max(minWeeks / 2, 4);
        String timeStr = acceleratedWeeks >= 8 ? (acceleratedWeeks / 4) + " months" : acceleratedWeeks + " weeks";

        return String.format(
            "I can generate an accelerated %s roadmap targeting the most critical %s skills " +
            "in %s — focusing on production-relevant knowledge and skipping non-essential theory. " +
            "This is the fastest realistic path. Would you like this accelerated plan?",
            level, domain, timeStr
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    private static class RequestedTimeline {
        final int    amount;
        final String unit;
        final String originalText;

        RequestedTimeline(int amount, String unit, String originalText) {
            this.amount       = amount;
            this.unit         = unit;
            this.originalText = originalText;
        }

        int toWeeks() {
            return switch (unit) {
                case "hour", "hours" -> 0; // less than a week
                case "day", "days"   -> Math.max(1, amount / 7);
                case "week", "weeks" -> amount;
                case "month", "months" -> amount * 4;
                default -> amount;
            };
        }
    }

    public static class FeasibilityResult {
        public final boolean feasible;
        public final String  explanation;
        public final String  minimumRealisticEstimate;
        public final String  acceleratedAlternative;
        public final String  domain;
        public final String  level;
        public final double  feasibilityScore; // 0.0 = impossible, 1.0 = realistic

        private FeasibilityResult(boolean feasible, String explanation,
                                  String minimumRealisticEstimate, String acceleratedAlternative,
                                  String domain, String level, double feasibilityScore) {
            this.feasible                 = feasible;
            this.explanation              = explanation;
            this.minimumRealisticEstimate = minimumRealisticEstimate;
            this.acceleratedAlternative   = acceleratedAlternative;
            this.domain                   = domain;
            this.level                    = level;
            this.feasibilityScore         = feasibilityScore;
        }

        public static FeasibilityResult feasible() {
            return new FeasibilityResult(true, null, null, null, null, null, 1.0);
        }

        public static FeasibilityResult unrealistic(String explanation, String minimum,
                                                     String accelerated, String domain, String level) {
            return new FeasibilityResult(false, explanation, minimum, accelerated, domain, level, 0.1);
        }
    }
}
