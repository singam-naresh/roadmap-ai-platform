package com.assistant.service;

import com.assistant.service.GroqClient.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * PHASE 5 — AI Self-Critique Engine
 *
 * After generation, the AI critiques its own roadmap against:
 *   - Coherence (do steps flow logically?)
 *   - Specificity (are technologies named concretely?)
 *   - Realism (are timelines and complexity appropriate?)
 *   - Completeness (are all required sections present?)
 *   - Progression quality (does difficulty increase appropriately?)
 *   - Dependency integrity (do prerequisites come before dependents?)
 *
 * Weak generations are automatically rejected and trigger regeneration.
 *
 * Uses a lightweight rule-based critique (no extra API call needed)
 * plus an optional LLM-based critique for high-stakes generations.
 */
@Service
public class SelfCritiqueEngine {

    private static final Logger log = LoggerFactory.getLogger(SelfCritiqueEngine.class);

    public static final double CRITIQUE_PASS_THRESHOLD = 0.70;

    private final GroqClient groqClient;

    public SelfCritiqueEngine(GroqClient groqClient) {
        this.groqClient = groqClient;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule-based critique (fast, no API call)
    // ─────────────────────────────────────────────────────────────────────────

    public CritiqueResult critiqueRuleBased(JsonNode roadmapJson, String domain, String expertiseLevel) {
        CritiqueResult result = new CritiqueResult();

        // 1. Coherence: steps should build on each other
        result.coherenceScore = scoreCoherence(roadmapJson);

        // 2. Specificity: concrete technologies present
        result.specificityScore = scoreSpecificity(roadmapJson);

        // 3. Realism: no impossible claims
        result.realismScore = scoreRealism(roadmapJson);

        // 4. Completeness: all required sections
        result.completenessScore = scoreCompleteness(roadmapJson);

        // 5. Progression: difficulty increases
        result.progressionScore = scoreProgression(roadmapJson);

        // 6. Dependency integrity: prerequisites before dependents
        result.dependencyScore = scoreDependencyIntegrity(roadmapJson);

        // Overall
        result.overallScore = (result.coherenceScore * 0.20) +
                              (result.specificityScore * 0.25) +
                              (result.realismScore * 0.15) +
                              (result.completenessScore * 0.15) +
                              (result.progressionScore * 0.15) +
                              (result.dependencyScore * 0.10);

        result.passes = result.overallScore >= CRITIQUE_PASS_THRESHOLD;

        // Generate critique feedback
        result.critiqueFeedback = generateCritiqueFeedback(result);

        log.info("[self-critique] domain={} level={} score={:.2f} passes={} coherence={:.2f} specificity={:.2f}",
                domain, expertiseLevel, result.overallScore, result.passes,
                result.coherenceScore, result.specificityScore);
        return result;
    }

    /**
     * LLM-based self-critique — asks the model to evaluate its own output.
     * Used only when rule-based critique is borderline (0.65–0.75).
     */
    public CritiqueResult critiqueLLM(JsonNode roadmapJson, String domain, String expertiseLevel) {
        try {
            String roadmapText = formatRoadmapForCritique(roadmapJson);
            String critiquePrompt = buildCritiquePrompt(roadmapText, domain, expertiseLevel);

            String response = groqClient.chat(List.of(
                    new ChatMessage("system", """
                        You are a senior technical reviewer evaluating a learning roadmap.
                        Respond ONLY with a JSON object: {"score": 0.0-1.0, "passes": true/false, "issues": ["issue1", "issue2"], "strengths": ["strength1"]}
                        Score 0.0-1.0 where 1.0 is perfect. Passes if score >= 0.70.
                        """),
                    new ChatMessage("user", critiquePrompt)
            ));

            return parseLLMCritiqueResponse(response, roadmapJson, domain, expertiseLevel);

        } catch (Exception e) {
            log.warn("[self-critique] LLM critique failed, falling back to rule-based: {}", e.getMessage());
            return critiqueRuleBased(roadmapJson, domain, expertiseLevel);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scoring methods
    // ─────────────────────────────────────────────────────────────────────────

    private double scoreCoherence(JsonNode json) {
        List<String> steps = extractSteps(json);
        if (steps.size() < 2) return 0.5;

        // Check for logical flow: each step should reference or build on previous concepts
        int coherentTransitions = 0;
        for (int i = 1; i < steps.size(); i++) {
            String prev = steps.get(i - 1).toLowerCase();
            String curr = steps.get(i).toLowerCase();
            // Simple heuristic: if they share at least one significant word, they're related
            Set<String> prevWords = significantWords(prev);
            Set<String> currWords = significantWords(curr);
            Set<String> shared = new HashSet<>(prevWords);
            shared.retainAll(currWords);
            if (!shared.isEmpty()) coherentTransitions++;
        }

        return (double) coherentTransitions / (steps.size() - 1);
    }

    private double scoreSpecificity(JsonNode json) {
        List<String> steps = extractSteps(json);
        if (steps.isEmpty()) return 0.0;

        long specificSteps = steps.stream()
                .filter(s -> containsConcretetech(s))
                .count();
        return (double) specificSteps / steps.size();
    }

    private double scoreRealism(JsonNode json) {
        double score = 1.0;
        String estimatedTime = json.path("estimatedTime").asText("").toLowerCase();

        // Penalize impossible timelines
        if (estimatedTime.matches(".*\\b[1-3]\\s*days?\\b.*")) score -= 0.5;
        if (estimatedTime.contains("1 week") && estimatedTime.contains("expert")) score -= 0.3;
        if (estimatedTime.isBlank() || estimatedTime.equals("varies")) score -= 0.2;

        // Penalize steps with impossible claims
        List<String> steps = extractSteps(json);
        for (String step : steps) {
            if (step.toLowerCase().contains("master") && step.toLowerCase().contains("day")) {
                score -= 0.1;
            }
        }

        return Math.max(0.0, score);
    }

    private double scoreCompleteness(JsonNode json) {
        double score = 0.0;
        if (!json.path("summary").isMissingNode() && json.path("summary").asText().length() > 50) score += 0.2;
        if (!json.path("estimatedTime").isMissingNode()) score += 0.1;
        if (!json.path("difficulty").isMissingNode()) score += 0.1;

        JsonNode steps = json.path("steps");
        if (steps.isArray() && steps.size() >= 5) score += 0.3;
        else if (steps.isArray() && steps.size() >= 3) score += 0.15;

        JsonNode tips = json.path("tips");
        if (tips.isArray() && tips.size() >= 2) score += 0.1;

        JsonNode mistakes = json.path("mistakesToAvoid");
        if (mistakes.isArray() && mistakes.size() >= 2) score += 0.1;

        JsonNode resources = json.path("resources");
        if (resources.isArray() && resources.size() >= 3) score += 0.1;

        return score;
    }

    private double scoreProgression(JsonNode json) {
        List<String> steps = extractSteps(json);
        if (steps.size() < 3) return 0.5;

        // Check that complexity indicators increase through the roadmap
        String[] complexityMarkers = {"basic", "simple", "fundamental", "advanced", "production",
                "distributed", "optimization", "architecture", "scale", "enterprise"};

        // Simple heuristic: later steps should have more complex vocabulary
        int firstHalfComplexity  = countComplexity(steps.subList(0, steps.size() / 2), complexityMarkers);
        int secondHalfComplexity = countComplexity(steps.subList(steps.size() / 2, steps.size()), complexityMarkers);

        if (secondHalfComplexity >= firstHalfComplexity) return 0.9;
        if (secondHalfComplexity >= firstHalfComplexity * 0.7) return 0.7;
        return 0.4;
    }

    private double scoreDependencyIntegrity(JsonNode json) {
        List<String> steps = extractSteps(json);
        if (steps.size() < 2) return 1.0;

        // Check that foundational steps come before advanced ones
        // Heuristic: "deploy" should come after "build", "test" after "implement"
        Map<String, Integer> conceptFirstAppearance = new HashMap<>();
        String[] concepts = {"build", "implement", "test", "deploy", "monitor", "optimize", "scale"};

        for (int i = 0; i < steps.size(); i++) {
            String lower = steps.get(i).toLowerCase();
            for (String concept : concepts) {
                if (lower.contains(concept) && !conceptFirstAppearance.containsKey(concept)) {
                    conceptFirstAppearance.put(concept, i);
                }
            }
        }

        // Validate ordering: build < test < deploy < monitor
        int violations = 0;
        if (conceptFirstAppearance.containsKey("deploy") && conceptFirstAppearance.containsKey("build")) {
            if (conceptFirstAppearance.get("deploy") < conceptFirstAppearance.get("build")) violations++;
        }
        if (conceptFirstAppearance.containsKey("monitor") && conceptFirstAppearance.containsKey("deploy")) {
            if (conceptFirstAppearance.get("monitor") < conceptFirstAppearance.get("deploy")) violations++;
        }

        return violations == 0 ? 1.0 : Math.max(0.3, 1.0 - violations * 0.3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> generateCritiqueFeedback(CritiqueResult result) {
        List<String> feedback = new ArrayList<>();
        if (result.coherenceScore < 0.6)    feedback.add("Steps lack logical flow — ensure each step builds on the previous");
        if (result.specificityScore < 0.7)  feedback.add("Too many steps lack concrete technology names");
        if (result.realismScore < 0.7)      feedback.add("Timeline or complexity claims are unrealistic");
        if (result.completenessScore < 0.7) feedback.add("Missing required sections (tips, resources, or enough steps)");
        if (result.progressionScore < 0.6)  feedback.add("Difficulty progression is not increasing — advanced topics appear too early");
        if (result.dependencyScore < 0.7)   feedback.add("Dependency ordering violated — prerequisites must come before dependents");
        if (feedback.isEmpty())             feedback.add("Roadmap passes all quality criteria");
        return feedback;
    }

    private boolean containsConcretetech(String step) {
        String lower = step.toLowerCase();
        String[] techTerms = {"spring", "kubernetes", "docker", "terraform", "prometheus", "kafka",
                "redis", "postgresql", "react", "typescript", "pytorch", "tensorflow", "vllm",
                "istio", "argocd", "helm", "elasticsearch", "nginx", "jwt", "oauth", "grpc"};
        for (String term : techTerms) {
            if (lower.contains(term)) return true;
        }
        return false;
    }

    private Set<String> significantWords(String text) {
        Set<String> stopWords = Set.of("a", "an", "the", "and", "or", "but", "in", "on", "at",
                "to", "for", "of", "with", "by", "from", "is", "are", "was", "were", "be",
                "been", "being", "have", "has", "had", "do", "does", "did", "will", "would",
                "could", "should", "may", "might", "must", "shall", "can", "need", "dare");
        return Arrays.stream(text.split("\\s+"))
                .filter(w -> w.length() > 3 && !stopWords.contains(w))
                .collect(Collectors.toSet());
    }

    private int countComplexity(List<String> steps, String[] markers) {
        int count = 0;
        for (String step : steps) {
            String lower = step.toLowerCase();
            for (String marker : markers) {
                if (lower.contains(marker)) count++;
            }
        }
        return count;
    }

    private List<String> extractSteps(JsonNode json) {
        JsonNode stepsNode = json.path("steps");
        if (!stepsNode.isArray()) return List.of();
        return StreamSupport.stream(stepsNode.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }

    private String formatRoadmapForCritique(JsonNode json) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summary: ").append(json.path("summary").asText("")).append("\n");
        sb.append("Estimated Time: ").append(json.path("estimatedTime").asText("")).append("\n");
        sb.append("Difficulty: ").append(json.path("difficulty").asText("")).append("\n");
        sb.append("Steps:\n");
        JsonNode steps = json.path("steps");
        if (steps.isArray()) {
            for (int i = 0; i < steps.size(); i++) {
                sb.append(i + 1).append(". ").append(steps.get(i).asText()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildCritiquePrompt(String roadmapText, String domain, String expertiseLevel) {
        return "Evaluate this " + domain + " roadmap for a " + expertiseLevel + " level learner:\n\n"
                + roadmapText + "\n\n"
                + "Score it 0.0-1.0 on: coherence, specificity, realism, completeness, progression, dependency integrity.";
    }

    private CritiqueResult parseLLMCritiqueResponse(String response, JsonNode roadmapJson,
                                                     String domain, String expertiseLevel) {
        try {
            // Try to extract JSON from response
            int start = response.indexOf('{');
            int end   = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = response.substring(start, end + 1);
                // Simple extraction without full parse
                double score = extractDouble(json, "score");
                CritiqueResult result = critiqueRuleBased(roadmapJson, domain, expertiseLevel);
                // Blend LLM score with rule-based
                result.overallScore = (result.overallScore + score) / 2.0;
                result.passes = result.overallScore >= CRITIQUE_PASS_THRESHOLD;
                return result;
            }
        } catch (Exception e) {
            log.warn("[self-critique] Failed to parse LLM response: {}", e.getMessage());
        }
        return critiqueRuleBased(roadmapJson, domain, expertiseLevel);
    }

    private double extractDouble(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return 0.7;
            int colon = json.indexOf(':', idx);
            int comma = json.indexOf(',', colon);
            int brace = json.indexOf('}', colon);
            int end = Math.min(comma < 0 ? Integer.MAX_VALUE : comma, brace < 0 ? Integer.MAX_VALUE : brace);
            return Double.parseDouble(json.substring(colon + 1, end).trim());
        } catch (Exception e) {
            return 0.7;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class CritiqueResult {
        public double       coherenceScore    = 0.0;
        public double       specificityScore  = 0.0;
        public double       realismScore      = 0.0;
        public double       completenessScore = 0.0;
        public double       progressionScore  = 0.0;
        public double       dependencyScore   = 0.0;
        public double       overallScore      = 0.0;
        public boolean      passes            = false;
        public List<String> critiqueFeedback  = new ArrayList<>();
    }
}
