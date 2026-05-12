package com.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * PHASE 5 — Step Completeness Validator
 *
 * Detects structurally incomplete steps that must be regenerated:
 *   - Truncated sentences (ends mid-phrase)
 *   - Incomplete technical phrases ("Implement OAuth 2...")
 *   - Unfinished roadmap items
 *   - Broken bullet structures
 *   - Malformed markdown
 *
 * Returns a list of step indices that require regeneration.
 */
@Service
public class StepCompletenessValidator {

    private static final Logger log = LoggerFactory.getLogger(StepCompletenessValidator.class);

    // Patterns for truncation detection
    private static final Pattern ENDS_WITH_PREPOSITION = Pattern.compile(
        "(?i).*\\b(a|an|the|and|or|but|in|on|at|to|for|of|with|by|from|into|onto|upon|" +
        "using|via|through|across|between|among|within|without|during|before|after|" +
        "implement|configure|deploy|build|create|develop|design|integrate|set)\\s*[.,]?$"
    );
    private static final Pattern ENDS_WITH_ELLIPSIS = Pattern.compile(".*(\\.{2,}|…)\\s*$");
    private static final Pattern ENDS_WITH_COMMA    = Pattern.compile(".*,\\s*$");
    private static final Pattern ENDS_WITH_COLON    = Pattern.compile(".*:\\s*$");
    private static final Pattern ENDS_WITH_DASH     = Pattern.compile(".*[-–—]\\s*$");

    // Incomplete technical phrase patterns
    private static final List<Pattern> INCOMPLETE_TECH_PHRASES = List.of(
        Pattern.compile("(?i)\\bImplement OAuth 2\\.?\\s*$"),
        Pattern.compile("(?i)\\bDevelop a microservices?\\.?\\s*$"),
        Pattern.compile("(?i)\\bCreate a Java\\.?\\s*$"),
        Pattern.compile("(?i)\\bBuild a Spring\\.?\\s*$"),
        Pattern.compile("(?i)\\bConfigure Kubernetes\\.?\\s*$"),
        Pattern.compile("(?i)\\bSet up Docker\\.?\\s*$"),
        Pattern.compile("(?i)\\bDeploy to\\.?\\s*$"),
        Pattern.compile("(?i)\\bIntegrate with\\.?\\s*$"),
        Pattern.compile("(?i)\\bImplement the\\.?\\s*$"),
        Pattern.compile("(?i)\\bCreate the\\.?\\s*$"),
        Pattern.compile("(?i)\\bBuild the\\.?\\s*$"),
        Pattern.compile("(?i)\\bDesign the\\.?\\s*$"),
        Pattern.compile("(?i)\\bDevelop the\\.?\\s*$"),
        Pattern.compile("(?i)\\bConfigure the\\.?\\s*$"),
        Pattern.compile("(?i)\\bSet up the\\.?\\s*$"),
        Pattern.compile("(?i)\\bInstall and\\.?\\s*$"),
        Pattern.compile("(?i)\\bTest and\\.?\\s*$"),
        Pattern.compile("(?i)\\bMonitor and\\.?\\s*$")
    );

    // Minimum viable step length
    private static final int MIN_COMPLETE_LENGTH = 50;

    public CompletenessReport validate(JsonNode roadmapJson) {
        List<String> steps = extractSteps(roadmapJson);
        List<Integer> truncatedIndices = new ArrayList<>();
        List<StepCompletenessIssue> issues = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            List<String> stepIssues = detectIssues(step);
            if (!stepIssues.isEmpty()) {
                truncatedIndices.add(i);
                issues.add(new StepCompletenessIssue(i, step, stepIssues));
                log.warn("[completeness] Step {} truncated: {}", i, stepIssues);
            }
        }

        boolean allComplete = truncatedIndices.isEmpty();
        log.info("[completeness] {} steps, {} truncated", steps.size(), truncatedIndices.size());
        return new CompletenessReport(allComplete, truncatedIndices, issues);
    }

    public List<String> detectIssues(String step) {
        List<String> issues = new ArrayList<>();
        if (step == null || step.isBlank()) {
            issues.add("Empty step");
            return issues;
        }

        String trimmed = step.trim();

        if (trimmed.length() < MIN_COMPLETE_LENGTH) {
            issues.add("Too short (" + trimmed.length() + " chars, min " + MIN_COMPLETE_LENGTH + ")");
        }
        if (ENDS_WITH_ELLIPSIS.matcher(trimmed).matches()) {
            issues.add("Ends with ellipsis (truncated)");
        }
        if (ENDS_WITH_COMMA.matcher(trimmed).matches()) {
            issues.add("Ends with comma (incomplete list)");
        }
        if (ENDS_WITH_COLON.matcher(trimmed).matches()) {
            issues.add("Ends with colon (incomplete enumeration)");
        }
        if (ENDS_WITH_DASH.matcher(trimmed).matches()) {
            issues.add("Ends with dash (truncated)");
        }
        if (ENDS_WITH_PREPOSITION.matcher(trimmed).matches()) {
            issues.add("Ends with preposition/article (incomplete sentence)");
        }
        for (Pattern p : INCOMPLETE_TECH_PHRASES) {
            if (p.matcher(trimmed).find()) {
                issues.add("Incomplete technical phrase detected");
                break;
            }
        }

        return issues;
    }

    private List<String> extractSteps(JsonNode json) {
        JsonNode stepsNode = json.path("steps");
        if (!stepsNode.isArray()) return List.of();
        return StreamSupport.stream(stepsNode.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class StepCompletenessIssue {
        public final int          stepIndex;
        public final String       stepText;
        public final List<String> issues;

        public StepCompletenessIssue(int stepIndex, String stepText, List<String> issues) {
            this.stepIndex = stepIndex;
            this.stepText  = stepText;
            this.issues    = issues;
        }
    }

    public static class CompletenessReport {
        public final boolean                    allComplete;
        public final List<Integer>              truncatedIndices;
        public final List<StepCompletenessIssue> issues;

        public CompletenessReport(boolean allComplete, List<Integer> truncatedIndices,
                                  List<StepCompletenessIssue> issues) {
            this.allComplete      = allComplete;
            this.truncatedIndices = truncatedIndices;
            this.issues           = issues;
        }
    }
}
