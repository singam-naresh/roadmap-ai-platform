package com.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * PHASE 5 — Semantic Uniqueness Engine
 *
 * Detects:
 *   - Repeated roadmap structures (same step pattern across multiple steps)
 *   - Repeated phrasing (same phrases used in multiple steps)
 *   - Duplicate outputs (near-identical steps)
 *
 * Uses Jaccard similarity for phrase-level comparison and
 * phrase entropy scoring to measure information density.
 *
 * No external embedding service required — pure in-process analysis.
 */
@Service
public class SemanticUniquenessEngine {

    private static final Logger log = LoggerFactory.getLogger(SemanticUniquenessEngine.class);

    private static final double DUPLICATE_THRESHOLD    = 0.65; // Jaccard similarity
    private static final double LOW_ENTROPY_THRESHOLD  = 2.5;  // bits per token
    private static final int    MIN_PHRASE_LENGTH      = 3;    // minimum n-gram size

    public UniquenessReport analyzeRoadmap(JsonNode roadmapJson) {
        List<String> steps = extractSteps(roadmapJson);
        return analyzeSteps(steps);
    }

    public UniquenessReport analyzeSteps(List<String> steps) {
        UniquenessReport report = new UniquenessReport();

        if (steps.size() < 2) {
            report.isUnique = true;
            return report;
        }

        // ── Duplicate detection ───────────────────────────────────────────────
        for (int i = 0; i < steps.size(); i++) {
            for (int j = i + 1; j < steps.size(); j++) {
                double sim = jaccardSimilarity(normalize(steps.get(i)), normalize(steps.get(j)));
                if (sim >= DUPLICATE_THRESHOLD) {
                    report.duplicatePairs.add(new DuplicatePair(i, j, sim,
                            "Steps " + (i+1) + " and " + (j+1) + " are " +
                            String.format("%.0f%%", sim * 100) + " similar"));
                }
            }
        }

        // ── Repeated phrase detection ─────────────────────────────────────────
        Map<String, List<Integer>> phraseOccurrences = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            Set<String> phrases = extractNGrams(steps.get(i), MIN_PHRASE_LENGTH);
            for (String phrase : phrases) {
                phraseOccurrences.computeIfAbsent(phrase, k -> new ArrayList<>()).add(i);
            }
        }
        report.repeatedPhrases = phraseOccurrences.entrySet().stream()
                .filter(e -> e.getValue().size() >= 3) // phrase appears in 3+ steps
                .sorted(Map.Entry.<String, List<Integer>>comparingByValue(
                        Comparator.comparingInt(List::size)).reversed())
                .limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        // ── Entropy scoring ───────────────────────────────────────────────────
        report.entropyScores = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            double entropy = computeEntropy(steps.get(i));
            report.entropyScores.add(entropy);
            if (entropy < LOW_ENTROPY_THRESHOLD) {
                report.lowEntropySteps.add(i);
            }
        }

        // ── Overall uniqueness ────────────────────────────────────────────────
        report.averageEntropy = report.entropyScores.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        report.isUnique = report.duplicatePairs.isEmpty()
                && report.repeatedPhrases.isEmpty()
                && report.lowEntropySteps.size() <= 1;

        log.debug("[uniqueness] steps={} duplicates={} repeated_phrases={} low_entropy={} avg_entropy={:.2f}",
                steps.size(), report.duplicatePairs.size(), report.repeatedPhrases.size(),
                report.lowEntropySteps.size(), report.averageEntropy);
        return report;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Algorithms
    // ─────────────────────────────────────────────────────────────────────────

    private double jaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        if (setA.isEmpty() && setB.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> extractNGrams(String text, int n) {
        String[] tokens = normalize(text).split("\\s+");
        Set<String> ngrams = new HashSet<>();
        for (int i = 0; i <= tokens.length - n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < i + n; j++) {
                if (j > i) sb.append(' ');
                sb.append(tokens[j]);
            }
            ngrams.add(sb.toString());
        }
        return ngrams;
    }

    /**
     * Shannon entropy of the word distribution in a text.
     * Higher entropy = more diverse vocabulary = more information.
     */
    private double computeEntropy(String text) {
        String[] tokens = normalize(text).split("\\s+");
        if (tokens.length == 0) return 0.0;

        Map<String, Long> freq = Arrays.stream(tokens)
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        double entropy = 0.0;
        for (long count : freq.values()) {
            double p = (double) count / tokens.length;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    public static class UniquenessReport {
        public boolean                    isUnique         = false;
        public List<DuplicatePair>        duplicatePairs   = new ArrayList<>();
        public Map<String, List<Integer>> repeatedPhrases  = new LinkedHashMap<>();
        public List<Integer>              lowEntropySteps  = new ArrayList<>();
        public List<Double>               entropyScores    = new ArrayList<>();
        public double                     averageEntropy   = 0.0;
    }

    public record DuplicatePair(int stepA, int stepB, double similarity, String description) {}
}
