package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Weighted, confidence-scored AI intent classifier.
 *
 * Design principles:
 * 1. ROADMAP signals are checked FIRST and carry the highest weight.
 *    A prompt like "Generate a Java learning roadmap" must never fall into CODING.
 * 2. Each intent accumulates a score from matched keyword patterns.
 *    The intent with the highest score wins.
 * 3. A minimum confidence threshold prevents weak matches from overriding CHAT fallback.
 * 4. Phrase-level patterns (multi-word) score higher than single-word patterns.
 */
@Component
public class AIIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(AIIntentClassifier.class);

    // Minimum score required to claim a non-CHAT intent
    private static final int MIN_CONFIDENCE = 10;

    public enum AIIntentType {
        ROADMAP,      // learning plans, career paths, long-term structured goals
        CODING,       // debugging, code generation, algorithms, stack traces
        ANALYSIS,     // compare, review, evaluate, pros/cons
        PRODUCTIVITY, // scheduling, habits, time management
        STARTUP,      // SaaS ideas, GTM, founder advice
        LEARNING,     // explain concepts, tutorials, teaching mode
        CHAT          // greetings, factual Q&A, identity, small talk (fallback)
    }

    /**
     * Result object carrying the classified intent, confidence score,
     * and matched keywords for logging/debugging.
     */
    public record IntentClassificationResult(
            AIIntentType intentType,
            int confidence,
            List<String> matchedKeywords,
            String reasoning
    ) {}

    // =========================================================================
    // Public API
    // =========================================================================

    /** Convenience method — returns just the intent type. */
    public AIIntentType classify(String userInput) {
        return classifyWithConfidence(userInput).intentType();
    }

    /** Full classification with confidence score and matched keywords. */
    public IntentClassificationResult classifyWithConfidence(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return new IntentClassificationResult(AIIntentType.CHAT, 100, List.of(), "empty input");
        }

        String lower = userInput.toLowerCase().trim();

        // ── Hard-override: CHAT greetings/identity (unambiguous, check first) ──
        IntentClassificationResult chatOverride = checkChatOverride(lower);
        if (chatOverride != null) {
            log.info("[intent] CHAT (override) confidence={} for: {}", chatOverride.confidence(), userInput);
            return chatOverride;
        }

        // ── Score all intents ──────────────────────────────────────────────────
        int roadmapScore     = scoreRoadmap(lower);
        int codingScore      = scoreCoding(lower);
        int analysisScore    = scoreAnalysis(lower);
        int productivityScore = scoreProductivity(lower);
        int startupScore     = scoreStartup(lower);
        int learningScore    = scoreLearning(lower);
        int chatScore        = scoreChat(lower);

        // Find winner
        int[] scores = { roadmapScore, codingScore, analysisScore, productivityScore, startupScore, learningScore, chatScore };
        AIIntentType[] types = { AIIntentType.ROADMAP, AIIntentType.CODING, AIIntentType.ANALYSIS,
                AIIntentType.PRODUCTIVITY, AIIntentType.STARTUP, AIIntentType.LEARNING, AIIntentType.CHAT };

        int maxScore = -1;
        AIIntentType winner = AIIntentType.CHAT;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > maxScore) {
                maxScore = scores[i];
                winner = types[i];
            }
        }

        // If no intent scored above minimum, fall back to CHAT
        if (maxScore < MIN_CONFIDENCE) {
            winner = AIIntentType.CHAT;
            maxScore = chatScore > 0 ? chatScore : 5;
        }

        String reasoning = String.format(
                "roadmap=%d coding=%d analysis=%d productivity=%d startup=%d learning=%d chat=%d → %s",
                roadmapScore, codingScore, analysisScore, productivityScore, startupScore, learningScore, chatScore, winner);

        log.info("[intent] {} confidence={} for: {} | {}", winner, maxScore, userInput, reasoning);
        return new IntentClassificationResult(winner, maxScore, List.of(), reasoning);
    }

    // =========================================================================
    // Hard-override: unambiguous CHAT signals
    // =========================================================================

    /**
     * Returns a CHAT result immediately for greetings and identity questions.
     * These are so unambiguous that scoring is unnecessary.
     */
    private IntentClassificationResult checkChatOverride(String lower) {
        // Pure greetings — must be short AND match a greeting word exactly
        if (lower.length() < 30 && matchesAny(lower,
                "hello", "hi ", "hi!", "hey ", "hey!", "howdy",
                "good morning", "good evening", "good afternoon",
                "what's up", "sup ", "yo ")) {
            return new IntentClassificationResult(AIIntentType.CHAT, 100, List.of("greeting"), "greeting");
        }
        // Identity questions about the AI
        if (matchesAny(lower, "who are you", "what are you", "what is aura",
                "what is this platform", "what is roadmap ai",
                "tell me about yourself", "introduce yourself", "what can you do",
                "are you an ai", "are you a bot", "what's your name")) {
            return new IntentClassificationResult(AIIntentType.CHAT, 100, List.of("identity"), "identity question");
        }
        // Conversational acknowledgements — must be very short (< 15 chars) to avoid false positives
        if (lower.length() < 15 && matchesAny(lower,
                "thanks", "thank you", "okay", "got it", "cool", "nice")) {
            return new IntentClassificationResult(AIIntentType.CHAT, 100, List.of("acknowledgement"), "acknowledgement");
        }
        return null;
    }

    // =========================================================================
    // Scoring functions — higher score = stronger signal
    // =========================================================================

    /**
     * ROADMAP scoring.
     * Phrase-level patterns score 30 (very strong).
     * Single-word patterns score 10 (moderate).
     *
     * KEY FIX: "roadmap", "learning path", "career path", "become a", "guide to"
     * all score very high so they always beat CODING/LEARNING signals.
     */
    private int scoreRoadmap(String lower) {
        int score = 0;

        // Tier 1 — explicit roadmap phrases (30 pts each)
        if (lower.contains("roadmap"))          score += 30;
        if (lower.contains("learning path"))    score += 30;
        if (lower.contains("career path"))      score += 30;
        if (lower.contains("learning plan"))    score += 30;
        if (lower.contains("study plan"))       score += 30;
        if (lower.contains("career plan"))      score += 30;
        if (lower.contains("step by step guide")) score += 30;
        if (lower.contains("complete guide"))   score += 25;
        if (lower.contains("full guide"))       score += 25;

        // Tier 2 — strong goal/journey phrases (20 pts each)
        if (lower.contains("become a "))        score += 20;
        if (lower.contains("become an "))       score += 20;
        if (lower.contains("how to become"))    score += 20;
        if (lower.contains("path to "))         score += 20;
        if (lower.contains("journey to "))      score += 20;
        if (lower.contains("guide to "))        score += 20;
        if (lower.contains("plan to "))         score += 15;
        if (lower.contains("from scratch"))     score += 15;
        if (lower.contains("from zero"))        score += 15;
        if (lower.contains("mastery"))          score += 15;
        if (lower.contains("master plan"))      score += 20;
        if (lower.contains("transition to"))    score += 20;
        if (lower.contains("transition from"))  score += 20;
        if (lower.contains("switch career"))    score += 20;
        if (lower.contains("career change"))    score += 20;

        // Tier 3 — action + goal combos (10 pts each)
        if (lower.contains("learn ") && (lower.contains("month") || lower.contains("week") || lower.contains("year"))) score += 15;
        if (lower.contains("transition to"))    score += 15;
        if (lower.contains("switch to"))        score += 10;
        if (lower.contains("get into "))        score += 10;
        if (lower.contains("break into"))       score += 10;
        if (lower.contains("execution plan"))   score += 20;
        if (lower.contains("action plan"))      score += 20;

        return score;
    }

    /**
     * CODING scoring.
     *
     * KEY FIX: "write a hello world", "write code", "generate code" all score high.
     * But "roadmap" in the same prompt will outscore coding signals.
     */
    private int scoreCoding(String lower) {
        int score = 0;

        // Tier 1 — explicit code generation phrases (25 pts each)
        if (lower.contains("write a function"))     score += 25;
        if (lower.contains("write a class"))        score += 25;
        if (lower.contains("write a method"))       score += 25;
        if (lower.contains("write a program"))      score += 25;
        if (lower.contains("write code"))           score += 25;
        if (lower.contains("generate code"))        score += 25;
        if (lower.contains("create a function"))    score += 25;
        if (lower.contains("implement a function")) score += 25;
        if (lower.contains("hello world"))          score += 25;
        if (lower.contains("code snippet"))         score += 20;
        if (lower.contains("boilerplate"))          score += 20;

        // Tier 2 — debugging signals (20 pts each)
        if (lower.contains("nullpointerexception")) score += 20;
        if (lower.contains("null pointer"))         score += 20;
        if (lower.contains("stack trace"))          score += 20;
        if (lower.contains("exception"))            score += 15;
        if (lower.contains("compile error"))        score += 20;
        if (lower.contains("runtime error"))        score += 20;
        if (lower.contains("syntax error"))         score += 20;
        if (lower.contains("debug my"))             score += 20;
        if (lower.contains("fix this code"))        score += 20;
        if (lower.contains("fix my code"))          score += 20;
        if (lower.contains("not working"))          score += 10;
        if (lower.contains("why is my"))            score += 10;

        // Tier 3 — technical task phrases (15 pts each)
        if (lower.contains("optimize "))            score += 15;
        if (lower.contains("refactor "))            score += 15;
        if (lower.contains("review my code"))       score += 15;
        if (lower.contains("explain this code"))    score += 15;
        if (lower.contains("algorithm for"))        score += 15;
        if (lower.contains("data structure"))       score += 15;
        if (lower.contains("time complexity"))      score += 15;
        if (lower.contains("big o"))                score += 15;
        if (lower.contains("sql query"))            score += 15;
        if (lower.contains("regex "))               score += 15;
        if (lower.contains("api endpoint"))         score += 15;

        // Tier 4 — language + action verb combos (10 pts)
        // "write ... java/python/etc" — only if it's a code-writing request
        boolean hasWriteVerb = lower.contains("write ") || lower.contains("create ") || lower.contains("build ");
        boolean hasTechLang  = lower.contains("java") || lower.contains("python") || lower.contains("javascript")
                || lower.contains("typescript") || lower.contains("react") || lower.contains("spring");
        if (hasWriteVerb && hasTechLang && !lower.contains("roadmap") && !lower.contains("path")) {
            score += 10;
        }

        return score;
    }

    private int scoreAnalysis(String lower) {
        int score = 0;
        if (lower.contains(" vs "))                     score += 25;
        if (lower.contains(" versus "))                 score += 25;
        if (lower.contains("compare "))                 score += 20;
        if (lower.contains("difference between"))       score += 20;
        if (lower.contains("pros and cons"))            score += 20;
        if (lower.contains("pros/cons"))                score += 20;
        if (lower.contains("advantages and disadvantages")) score += 20;
        if (lower.contains("which is better"))          score += 20;
        if (lower.contains("which should i use"))       score += 20;
        if (lower.contains("evaluate "))                score += 25;
        if (lower.contains("review my "))               score += 20;
        if (lower.contains("analyze my"))               score += 20;
        if (lower.contains("analyse my"))               score += 20;
        if (lower.contains("audit my"))                 score += 20;
        if (lower.contains("give me feedback"))         score += 15;
        if (lower.contains("is it worth"))              score += 10;
        if (lower.contains("should i use"))             score += 10;
        if (lower.contains("should i choose"))          score += 10;
        return score;
    }

    private int scoreProductivity(String lower) {
        int score = 0;
        if (lower.contains("plan my week"))             score += 25;
        if (lower.contains("plan my day"))              score += 25;
        if (lower.contains("daily schedule"))           score += 20;
        if (lower.contains("weekly schedule"))          score += 20;
        if (lower.contains("time blocking"))            score += 20;
        if (lower.contains("time block"))               score += 15;
        if (lower.contains("pomodoro"))                 score += 20;
        if (lower.contains("focus session"))            score += 15;
        if (lower.contains("prioritize my"))            score += 15;
        if (lower.contains("organize my tasks"))        score += 15;
        if (lower.contains("habit tracker"))            score += 15;
        if (lower.contains("build a habit"))            score += 15;
        if (lower.contains("morning routine"))          score += 15;
        if (lower.contains("evening routine"))          score += 15;
        if (lower.contains("productivity system"))      score += 20;
        if (lower.contains("deep work"))                score += 15;
        if (lower.contains("getting things done"))      score += 15;
        if (lower.contains("gtd"))                      score += 15;
        return score;
    }

    private int scoreStartup(String lower) {
        int score = 0;
        if (lower.contains("startup idea"))             score += 25;
        if (lower.contains("saas idea"))                score += 25;
        if (lower.contains("business idea"))            score += 20;
        if (lower.contains("gtm strategy"))             score += 25;
        if (lower.contains("go to market"))             score += 25;
        if (lower.contains("product market fit"))       score += 25;
        if (lower.contains("mvp for"))                  score += 20;
        if (lower.contains("build a startup"))          score += 20;
        if (lower.contains("launch a startup"))         score += 20;
        if (lower.contains("founder advice"))           score += 20;
        if (lower.contains("raise funding"))            score += 20;
        if (lower.contains("pitch deck"))               score += 20;
        if (lower.contains("investor pitch"))           score += 20;
        if (lower.contains("revenue model"))            score += 15;
        if (lower.contains("business model"))           score += 15;
        if (lower.contains("bootstrap"))                score += 15;
        return score;
    }

    /**
     * LEARNING scoring.
     * "explain X" and "teach me X" are strong learning signals.
     * But "explain how to become a developer" should still be ROADMAP
     * because roadmap score will be higher.
     */
    private int scoreLearning(String lower) {
        int score = 0;
        if (lower.contains("explain "))                 score += 20;
        if (lower.contains("teach me"))                 score += 20;
        if (lower.contains("help me understand"))       score += 20;
        if (lower.contains("i don't understand"))       score += 15;
        if (lower.contains("what is the concept"))      score += 15;
        if (lower.contains("eli5"))                     score += 30;  // very strong learning signal
        if (lower.contains("in simple terms"))          score += 20;
        if (lower.contains("for dummies"))              score += 20;
        if (lower.contains("beginner guide to"))        score += 25;  // beats roadmap's "guide to" (20)
        if (lower.contains("introduction to"))          score += 10;
        if (lower.contains("overview of"))              score += 10;
        if (lower.contains("summary of"))               score += 10;
        if (lower.contains("what is the difference between")) score += 15;
        if (lower.contains("how does ") && lower.length() < 60) score += 10;
        return score;
    }

    /**
     * CHAT scoring — only for genuine conversational/factual prompts.
     * Short factual questions score moderately.
     */
    private int scoreChat(String lower) {
        int score = 0;
        // Short factual questions
        if (lower.length() < 80 && matchesAny(lower,
                "who is ", "who was ", "what is ", "what are ",
                "when did ", "when was ", "where is ", "where was ",
                "why is ", "why does ", "how many ", "how much ",
                "define ", "meaning of ")) {
            score += 15;
        }
        // Very short inputs are likely conversational
        if (lower.length() < 25) score += 10;
        return score;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private boolean matchesAny(String text, String... patterns) {
        for (String p : patterns) {
            if (text.contains(p)) return true;
        }
        return false;
    }
}
