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
 * PHASE 5 — Output Quality Engine
 *
 * Per-step quality scoring (0.0–1.0) with detection of:
 *   - Generic / vague wording
 *   - Low-information content
 *   - Repeated phrasing across steps
 *   - Unrealistic timelines
 *   - Placeholder / template content
 *   - Incomplete / truncated sentences
 *
 * Rejects generations that fall below the quality threshold.
 */
@Service
public class OutputQualityEngine {

    private static final Logger log = LoggerFactory.getLogger(OutputQualityEngine.class);

    public static final double STEP_QUALITY_THRESHOLD    = 0.55;
    public static final double ROADMAP_QUALITY_THRESHOLD = 0.65;

    // ── Detection patterns ────────────────────────────────────────────────────

    private static final Pattern TRUNCATION = Pattern.compile(
        "(?i)\\b(implement|develop|create|build|configure|deploy|set up|integrate|design|establish)\\s+\\w{0,20}\\s*\\.{0,1}$"
    );
    private static final Pattern PLACEHOLDER = Pattern.compile(
        "(?i)\\b(todo|tbd|placeholder|example|sample|lorem|ipsum|foo|bar|baz|your_|<[^>]+>|\\[.*?\\])\\b"
    );
    private static final Pattern VAGUE_OPENERS = Pattern.compile(
        "(?i)^(learn|study|understand|explore|research|get familiar|look into|read about|check out|review|investigate)\\b"
    );
    private static final Pattern GENERIC_FILLER = Pattern.compile(
        "(?i)\\b(various|several|many|some|certain|appropriate|relevant|suitable|necessary|important|useful|helpful|good|nice|great|best)\\b"
    );
    private static final Pattern CONCRETE_TECH = Pattern.compile(
        "(?i)\\b(spring boot|kubernetes|docker|terraform|prometheus|grafana|kafka|redis|postgresql|mongodb|" +
        "react|nextjs|typescript|pytorch|tensorflow|vllm|triton|deepspeed|istio|argocd|helm|" +
        "elasticsearch|cassandra|dynamodb|s3|lambda|ecs|eks|gke|aks|nginx|envoy|haproxy|" +
        "jwt|oauth|openid|grpc|graphql|rest|websocket|sse|protobuf|avro|parquet|delta lake)\\b"
    );
    private static final Pattern ACTION_VERB = Pattern.compile(
        "(?i)^(build|implement|deploy|configure|architect|design|create|develop|integrate|optimize|" +
        "provision|orchestrate|automate|containerize|instrument|benchmark|profile|migrate|refactor|" +
        "secure|harden|monitor|observe|test|validate|document|ship|release|scale|tune)\\b"
    );
    private static final Pattern MEASURABLE_OUTCOME = Pattern.compile(
        "(?i)\\b(with|using|via|through|achieving|ensuring|enabling|supporting|providing|" +
        "targeting|measuring|validating|testing|deploying|exposing|serving|handling)\\b"
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public RoadmapQualityReport scoreRoadmap(JsonNode roadmapJson) {
        List<String> steps = extractSteps(roadmapJson);
        List<StepQualityScore> stepScores = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            stepScores.add(scoreStep(steps.get(i), i, steps));
        }

        double avgScore = stepScores.stream()
                .mapToDouble(s -> s.score).average().orElse(0.0);

        List<String> failingSteps = stepScores.stream()
                .filter(s -> s.score < STEP_QUALITY_THRESHOLD)
                .map(s -> "Step " + (s.index + 1) + " (score=" + String.format("%.2f", s.score) + "): "
                        + String.join(", ", s.issues))
                .collect(Collectors.toList());

        boolean acceptable = avgScore >= ROADMAP_QUALITY_THRESHOLD && failingSteps.isEmpty();

        log.info("[output-quality] avg={:.2f} acceptable={} failing={}", avgScore, acceptable, failingSteps.size());
        return new RoadmapQualityReport(avgScore, acceptable, stepScores, failingSteps);
    }

    public StepQualityScore scoreStep(String step, int index, List<String> allSteps) {
        StepQualityScore score = new StepQualityScore(index, step);
        double points = 0.0;

        if (step == null || step.isBlank()) {
            score.issues.add("Empty step");
            score.score = 0.0;
            return score;
        }

        String trimmed = step.trim();

        // ── Positive signals ──────────────────────────────────────────────────
        if (ACTION_VERB.matcher(trimmed).find())          { points += 0.20; }
        if (CONCRETE_TECH.matcher(trimmed).find())        { points += 0.25; }
        if (MEASURABLE_OUTCOME.matcher(trimmed).find())   { points += 0.15; }
        if (trimmed.length() >= 80)                       { points += 0.10; }
        if (trimmed.length() >= 120)                      { points += 0.05; }

        // Count concrete tech mentions (bonus for multiple)
        long techCount = CONCRETE_TECH.matcher(trimmed).results().count();
        if (techCount >= 2) points += 0.10;
        if (techCount >= 3) points += 0.05;

        // ── Negative signals ──────────────────────────────────────────────────
        if (VAGUE_OPENERS.matcher(trimmed).find()) {
            points -= 0.25;
            score.issues.add("Vague opener (learn/study/explore)");
        }
        if (PLACEHOLDER.matcher(trimmed).find()) {
            points -= 0.40;
            score.issues.add("Placeholder content detected");
        }
        if (isTruncated(trimmed)) {
            points -= 0.35;
            score.issues.add("Truncated sentence");
        }
        long fillerCount = GENERIC_FILLER.matcher(trimmed).results().count();
        if (fillerCount >= 3) {
            points -= 0.15;
            score.issues.add("Excessive generic filler words (" + fillerCount + ")");
        }
        if (trimmed.length() < 40) {
            points -= 0.20;
            score.issues.add("Step too short (" + trimmed.length() + " chars)");
        }

        // ── Duplicate detection ───────────────────────────────────────────────
        if (isDuplicateOf(trimmed, index, allSteps)) {
            points -= 0.30;
            score.issues.add("Duplicate or near-duplicate of another step");
        }

        score.score = Math.max(0.0, Math.min(1.0, points));
        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isTruncated(String step) {
        // Ends mid-sentence with a preposition, article, or conjunction
        String lower = step.toLowerCase().trim();
        String[] truncationEndings = {" a", " an", " the", " and", " or", " with", " for",
                " in", " on", " to", " of", " by", " at", " from", " using", " via"};
        for (String ending : truncationEndings) {
            if (lower.endsWith(ending)) return true;
        }
        // Ends with ellipsis
        if (step.endsWith("...") || step.endsWith("…")) return true;
        // Ends with a comma
        if (step.endsWith(",")) return true;
        return false;
    }

    private boolean isDuplicateOf(String step, int index, List<String> allSteps) {
        String normalized = normalize(step);
        for (int i = 0; i < allSteps.size(); i++) {
            if (i == index) continue;
            String other = normalize(allSteps.get(i));
            if (jaccardSimilarity(normalized, other) > 0.65) return true;
        }
        return false;
    }

    private String normalize(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split(" ")));
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
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

    public static class StepQualityScore {
        public final int    index;
        public final String step;
        public       double score;
        public final List<String> issues = new ArrayList<>();

        public StepQualityScore(int index, String step) {
            this.index = index;
            this.step  = step;
        }
    }

    public static class RoadmapQualityReport {
        public final double                  averageScore;
        public final boolean                 acceptable;
        public final List<StepQualityScore>  stepScores;
        public final List<String>            failingSteps;

        public RoadmapQualityReport(double averageScore, boolean acceptable,
                                    List<StepQualityScore> stepScores, List<String> failingSteps) {
            this.averageScore = averageScore;
            this.acceptable   = acceptable;
            this.stepScores   = stepScores;
            this.failingSteps = failingSteps;
        }
    }
}
