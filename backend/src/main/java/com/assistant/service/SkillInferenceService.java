package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * PHASE 8 — Skill Inference Service
 *
 * Detects the user's actual experience level from natural language:
 *   ABSOLUTE_BEGINNER — "I don't know basics", "from scratch", "never coded"
 *   BEGINNER          — "learning", "new to", "just started", "beginner"
 *   INTERMEDIATE      — "know the basics", "some experience", "familiar with"
 *   ADVANCED          — "experienced", "already know X", "want to go deeper"
 *   EXPERT            — "production systems", "distributed", "advanced infra"
 *
 * Returns a SkillInference with level, confidence (0–1), and reasoning.
 */
@Service
public class SkillInferenceService {

    private static final Logger log = LoggerFactory.getLogger(SkillInferenceService.class);

    public enum SkillLevel {
        ABSOLUTE_BEGINNER, BEGINNER, INTERMEDIATE, ADVANCED, EXPERT;

        public String toExpertiseLevel() {
            return switch (this) {
                case ABSOLUTE_BEGINNER -> "BEGINNER";
                case BEGINNER          -> "BEGINNER";
                case INTERMEDIATE      -> "INTERMEDIATE";
                case ADVANCED          -> "ADVANCED";
                case EXPERT            -> "EXPERT";
            };
        }
    }

    public static class SkillInference {
        public final SkillLevel level;
        public final double     confidence;
        public final String     reasoning;

        public SkillInference(SkillLevel level, double confidence, String reasoning) {
            this.level      = level;
            this.confidence = confidence;
            this.reasoning  = reasoning;
        }
    }

    // ─── Signal patterns ─────────────────────────────────────────────────────

    private static final List<String> ABSOLUTE_BEGINNER_SIGNALS = List.of(
        "don't know", "do not know", "dont know",
        "no idea", "no experience", "zero experience",
        "never coded", "never programmed", "never written code",
        "complete beginner", "total beginner", "absolute beginner",
        "from scratch", "from zero", "from the beginning", "from the start",
        "never used", "never learned", "never tried",
        "don't understand", "do not understand",
        "basics", "fundamentals", "foundation",
        "where do i start", "where to start", "how to start",
        "i am new", "i'm new", "completely new",
        "no background", "no knowledge", "no prior",
        "just starting", "just started", "just beginning",
        "first time", "first steps",
        "i want to learn java", "i want to learn python", "i want to learn programming",
        "teach me", "help me learn", "guide me"
    );

    private static final List<String> BEGINNER_SIGNALS = List.of(
        "beginner", "newbie", "novice", "starter",
        "learning", "want to learn", "trying to learn",
        "new to", "getting started", "getting into",
        "basic", "simple", "easy",
        "introduction", "intro to", "intro for",
        "for beginners", "beginner friendly",
        "step by step", "step-by-step",
        "roadmap for learning", "how to become",
        "i know a little", "some basics"
    );

    private static final List<String> INTERMEDIATE_SIGNALS = List.of(
        "intermediate", "some experience", "familiar with",
        "know the basics", "understand the basics",
        "already know", "have experience with",
        "worked with", "used before",
        "want to improve", "want to get better",
        "next level", "go deeper", "go further",
        "beyond basics", "past basics",
        "i can build", "i have built", "i've built"
    );

    private static final List<String> ADVANCED_SIGNALS = List.of(
        "advanced", "experienced", "senior",
        "production", "enterprise", "at scale",
        "already know spring boot", "already know react", "already know python",
        "want advanced", "advanced topics", "advanced concepts",
        "deep dive", "in-depth", "comprehensive",
        "architect", "design patterns", "system design",
        "performance", "optimization", "scalability",
        "microservices", "distributed", "cloud native"
    );

    private static final List<String> EXPERT_SIGNALS = List.of(
        "expert", "principal", "staff engineer", "lead engineer",
        "large scale", "high throughput", "high performance",
        "kubernetes", "istio", "terraform", "argocd",
        "vllm", "deepspeed", "tensorrt", "triton",
        "distributed systems", "consensus", "raft", "paxos",
        "infrastructure", "platform engineering", "sre",
        "production ai", "ai infrastructure", "ml infrastructure",
        "advanced system design", "advanced distributed"
    );

    // ─── Domain-specific absolute beginner overrides ─────────────────────────
    // When user says "I don't know Java basics" — force ABSOLUTE_BEGINNER
    // regardless of other signals

    private static final Map<String, List<String>> DOMAIN_BEGINNER_OVERRIDES = Map.of(
        "java",   List.of("don't know java", "no java", "java basics", "java fundamentals", "learn java from"),
        "python", List.of("don't know python", "no python", "python basics", "learn python from"),
        "react",  List.of("don't know react", "no react", "react basics", "learn react from"),
        "spring", List.of("don't know spring", "no spring", "spring basics"),
        "javascript", List.of("don't know javascript", "no javascript", "js basics")
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public SkillInference infer(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return new SkillInference(SkillLevel.INTERMEDIATE, 0.3, "No input provided, defaulting to intermediate");
        }

        String lower = userInput.toLowerCase().trim();

        // ── 1. Check domain-specific beginner overrides first ─────────────────
        for (Map.Entry<String, List<String>> entry : DOMAIN_BEGINNER_OVERRIDES.entrySet()) {
            for (String signal : entry.getValue()) {
                if (lower.contains(signal)) {
                    log.info("[skill-inference] ABSOLUTE_BEGINNER detected via domain override: '{}'", signal);
                    return new SkillInference(SkillLevel.ABSOLUTE_BEGINNER, 0.95,
                            "User explicitly stated no knowledge of " + entry.getKey());
                }
            }
        }

        // ── 2. Score each level ───────────────────────────────────────────────
        int absoluteBeginnerScore = countMatches(lower, ABSOLUTE_BEGINNER_SIGNALS);
        int beginnerScore         = countMatches(lower, BEGINNER_SIGNALS);
        int intermediateScore     = countMatches(lower, INTERMEDIATE_SIGNALS);
        int advancedScore         = countMatches(lower, ADVANCED_SIGNALS);
        int expertScore           = countMatches(lower, EXPERT_SIGNALS);

        log.debug("[skill-inference] Scores — abs_beg:{} beg:{} int:{} adv:{} exp:{}",
                absoluteBeginnerScore, beginnerScore, intermediateScore, advancedScore, expertScore);

        // ── 3. Determine level ────────────────────────────────────────────────
        // Absolute beginner signals are very strong — even 1 match is decisive
        if (absoluteBeginnerScore >= 1) {
            double confidence = Math.min(0.95, 0.6 + absoluteBeginnerScore * 0.1);
            return new SkillInference(SkillLevel.ABSOLUTE_BEGINNER, confidence,
                    "Detected absolute beginner signals: " + absoluteBeginnerScore + " matches");
        }

        // Expert signals override everything else
        if (expertScore >= 2) {
            return new SkillInference(SkillLevel.EXPERT, Math.min(0.9, 0.5 + expertScore * 0.1),
                    "Detected expert signals: " + expertScore + " matches");
        }

        // Advanced
        if (advancedScore >= 2 || (advancedScore >= 1 && intermediateScore >= 1)) {
            return new SkillInference(SkillLevel.ADVANCED, 0.75,
                    "Detected advanced signals: " + advancedScore + " matches");
        }

        // Intermediate
        if (intermediateScore >= 1 || (beginnerScore == 0 && advancedScore == 0 && expertScore == 0)) {
            // Default to intermediate if no strong signals
            double confidence = intermediateScore >= 1 ? 0.7 : 0.4;
            return new SkillInference(SkillLevel.INTERMEDIATE, confidence,
                    intermediateScore >= 1 ? "Detected intermediate signals" : "No strong signals, defaulting to intermediate");
        }

        // Beginner
        if (beginnerScore >= 1) {
            return new SkillInference(SkillLevel.BEGINNER, Math.min(0.85, 0.5 + beginnerScore * 0.1),
                    "Detected beginner signals: " + beginnerScore + " matches");
        }

        // Default
        return new SkillInference(SkillLevel.INTERMEDIATE, 0.4, "No clear signals, defaulting to intermediate");
    }

    private int countMatches(String input, List<String> signals) {
        int count = 0;
        for (String signal : signals) {
            if (input.contains(signal)) count++;
        }
        return count;
    }
}
