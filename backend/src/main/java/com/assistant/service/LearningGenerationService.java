package com.assistant.service;

import com.assistant.service.GroqClient.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles LEARNING intent: explain concepts, tutorials, teaching mode.
 *
 * Skill-level aware: beginner explanations use analogies and simple language;
 * advanced explanations go deep into internals and edge cases.
 *
 * Returns structured JSON for the LearningRenderer — concept explanation,
 * examples with code, exercises, misconceptions, and next topics.
 */
@Service
public class LearningGenerationService {

    private static final Logger log = LoggerFactory.getLogger(LearningGenerationService.class);

    private final GroqClient groqClient;
    private final SkillInferenceService skillInferenceService;

    public LearningGenerationService(GroqClient groqClient,
                                      SkillInferenceService skillInferenceService) {
        this.groqClient          = groqClient;
        this.skillInferenceService = skillInferenceService;
    }

    public String generate(String userInput) {
        return generate(userInput, null);
    }

    /**
     * Generate a learning response with optional session context.
     * @param sessionSkillLevel skill level from conversation session (may be null)
     */
    public String generate(String userInput, String sessionSkillLevel) {
        // Detect skill level from input, or use session context
        String skillLevel = sessionSkillLevel;
        if (skillLevel == null || skillLevel.equals("intermediate")) {
            SkillInferenceService.SkillInference inference = skillInferenceService.infer(userInput);
            if (inference.confidence >= 0.6) {
                skillLevel = inference.level.toExpertiseLevel();
            } else {
                skillLevel = "INTERMEDIATE";
            }
        } else {
            skillLevel = skillLevel.toUpperCase();
        }

        log.info("[learning] Generating learning response — skill={} input='{}'",
                skillLevel, userInput.substring(0, Math.min(60, userInput.length())));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", buildSystemPrompt(skillLevel)));
        messages.add(new ChatMessage("user", userInput));

        return groqClient.chat(messages);
    }

    private String buildSystemPrompt(String skillLevel) {
        boolean isBeginner = "BEGINNER".equals(skillLevel);
        boolean isAdvanced = "ADVANCED".equals(skillLevel) || "EXPERT".equals(skillLevel);

        StringBuilder sb = new StringBuilder();

        // Persona
        if (isBeginner) {
            sb.append("You are a patient, encouraging tutor who explains concepts to complete beginners.\n");
            sb.append("Use simple language, real-world analogies, and short code examples.\n");
            sb.append("Never assume prior knowledge. Explain every term you use.\n\n");
        } else if (isAdvanced) {
            sb.append("You are a senior engineer explaining advanced concepts to experienced developers.\n");
            sb.append("Go deep into internals, edge cases, performance implications, and production concerns.\n");
            sb.append("Skip basic explanations — focus on nuance, tradeoffs, and real-world application.\n\n");
        } else {
            sb.append("You are a knowledgeable tutor explaining concepts to developers with some experience.\n");
            sb.append("Balance clarity with depth. Use concrete examples and practical code.\n\n");
        }

        // JSON format
        sb.append("""
                Return ONLY a valid JSON object. No markdown outside the JSON. No text before or after.

                The JSON must follow this exact structure:
                {
                  "summary": "2-3 sentence overview of what the learner will understand after this",
                  "conceptTitle": "The main concept being taught",
                  "explanation": "Clear explanation of the concept — use analogies for beginners, internals for advanced",
                  "keyPoints": ["key point 1", "key point 2", "key point 3"],
                  "examples": [
                    {
                      "title": "Example title",
                      "description": "What this example demonstrates and why it matters",
                      "code": "actual runnable code snippet, or empty string if not applicable"
                    }
                  ],
                  "commonMisconceptions": ["misconception 1", "misconception 2"],
                  "practiceExercises": ["exercise 1", "exercise 2", "exercise 3"],
                  "bestPractices": ["best practice 1", "best practice 2"],
                  "resources": ["resource with URL 1", "resource with URL 2"],
                  "nextTopics": ["logical next topic 1", "logical next topic 2"]
                }
                """);

        // Skill-level specific rules
        if (isBeginner) {
            sb.append("""
                BEGINNER RULES:
                - "explanation" must use a real-world analogy before any technical detail
                - "examples" must include at least 2 simple, runnable code snippets
                - "code" must be minimal — 5-15 lines maximum, heavily commented
                - "practiceExercises" must be small, achievable tasks (e.g. "Write a for loop that prints 1 to 10")
                - "nextTopics" must be the immediate next concept, not advanced topics
                - DO NOT mention: frameworks, design patterns, distributed systems, or production concerns
                - Use encouraging language: "Great starting point!", "This is the foundation of..."
                """);
        } else if (isAdvanced) {
            sb.append("""
                ADVANCED RULES:
                - "explanation" must cover internals, implementation details, and edge cases
                - "examples" must show real-world production patterns, not toy examples
                - "code" should demonstrate best practices, error handling, and performance considerations
                - "commonMisconceptions" must address subtle bugs and performance pitfalls experts encounter
                - "practiceExercises" must be challenging: implement from scratch, optimize, or debug complex scenarios
                - "bestPractices" must include production-grade recommendations
                """);
        } else {
            sb.append("""
                INTERMEDIATE RULES:
                - "explanation" must be clear and practical with concrete examples
                - "examples" must show real use cases, not just syntax demos
                - "code" should be complete and runnable (10-30 lines)
                - "practiceExercises" must build toward a real feature or component
                - "nextTopics" should progress logically toward more advanced concepts
                """);
        }

        sb.append("""
                QUALITY RULES (apply to all levels):
                - "explanation" must be at least 3 sentences — no one-liners
                - "examples" must contain at least 1 entry with real code
                - "practiceExercises" must be actionable, not vague ("Write a program that..." not "Practice this concept")
                - "resources" must include real URLs (e.g. "Oracle Java Docs — https://docs.oracle.com/javase/tutorial/")
                - "nextTopics" must be 2-3 specific topics, not generic ("Learn OOP" not "Learn more")
                - Do NOT generate a roadmap or timeline — this is a teaching response
                """);

        return sb.toString();
    }
}
