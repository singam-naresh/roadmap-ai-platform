package com.assistant.service;

import com.assistant.service.GroqClient.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles CODING intent: debugging, code generation, algorithm help, API issues.
 *
 * Clarification-aware: if the prompt is vague (no code, no error, no language),
 * the AI is instructed to ask targeted follow-up questions instead of producing
 * generic filler.
 */
@Service
public class CodingGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CodingGenerationService.class);

    private final GroqClient groqClient;

    public CodingGenerationService(GroqClient groqClient) {
        this.groqClient = groqClient;
    }

    public String generate(String userInput) {
        log.info("[coding] Generating coding response for: {}", userInput);

        boolean isVague = isVaguePrompt(userInput);
        log.debug("[coding] Vague prompt: {}", isVague);

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", buildSystemPrompt(isVague)),
                new ChatMessage("user", userInput)
        );

        return groqClient.chat(messages);
    }

    /**
     * Detects whether the coding prompt is too vague to produce a useful response.
     * A vague prompt has no code snippet, no error message, no specific language,
     * and no concrete technical detail.
     */
    private boolean isVaguePrompt(String userInput) {
        String lower = userInput.toLowerCase();

        // Has concrete technical content — not vague
        boolean hasCode       = lower.contains("```") || lower.contains("public ") || lower.contains("def ")
                || lower.contains("function ") || lower.contains("class ") || lower.contains("import ");
        boolean hasError      = lower.contains("exception") || lower.contains("error") || lower.contains("stack trace")
                || lower.contains("nullpointer") || lower.contains("compile") || lower.contains("crash");
        boolean hasSpecific   = lower.contains("hello world") || lower.contains("algorithm")
                || lower.contains("sort") || lower.contains("search") || lower.contains("sql")
                || lower.contains("regex") || lower.contains("api") || lower.contains("rest");
        boolean hasLanguage   = lower.contains("java") || lower.contains("python") || lower.contains("javascript")
                || lower.contains("typescript") || lower.contains("react") || lower.contains("spring")
                || lower.contains("node") || lower.contains("go ") || lower.contains("rust")
                || lower.contains("c++") || lower.contains("kotlin") || lower.contains("swift");

        // Vague if: no code, no error, no specific task, and prompt is short
        boolean isShort = userInput.trim().split("\\s+").length < 8;
        return !hasCode && !hasError && !hasSpecific && isShort && !hasLanguage;
    }

    private String buildSystemPrompt(boolean isVague) {
        if (isVague) {
            return buildClarificationPrompt();
        }
        return buildCodeGenerationPrompt();
    }

    /**
     * Used when the prompt is too vague.
     * Instructs the AI to ask targeted follow-up questions.
     */
    private String buildClarificationPrompt() {
        return """
                You are an expert software engineering assistant for the Adaptive AI Learning Roadmap Platform.

                The user has asked a vague coding question without providing enough context.
                DO NOT produce generic filler or a generic explanation.
                Instead, ask 2-4 targeted follow-up questions to gather the information you need.

                Return ONLY a valid JSON object. No markdown outside the JSON.

                Use this structure:
                {
                  "summary": "I need a bit more context to help you effectively.",
                  "language": "",
                  "explanation": "To give you the most accurate help, I have a few questions:",
                  "codeBlocks": [],
                  "keyPoints": [],
                  "commonMistakes": [],
                  "resources": [],
                  "clarificationQuestions": [
                    "What programming language or framework are you using?",
                    "Can you share the error message or stack trace?",
                    "Can you paste the relevant code snippet?",
                    "What behavior are you expecting vs what is actually happening?"
                  ]
                }

                Rules:
                - "clarificationQuestions" must contain 2-4 specific, targeted questions.
                - Questions must be relevant to the user's specific request.
                - Do NOT produce code or explanations — only ask questions.
                - Keep "explanation" to one sentence introducing the questions.
                """;
    }

    /**
     * Used for concrete coding requests.
     * Produces structured code output with explanations.
     */
    private String buildCodeGenerationPrompt() {
        return """
                You are an expert software engineering assistant for the Adaptive AI Learning Roadmap Platform.

                Return ONLY a valid JSON object. No markdown outside the JSON. No text before or after.

                The JSON must follow this exact structure:
                {
                  "summary": "Brief explanation of what the code does or what the fix addresses",
                  "language": "detected programming language (e.g. Java, Python, JavaScript)",
                  "explanation": "Detailed explanation of the approach, why it works, and any important notes",
                  "codeBlocks": [
                    {
                      "label": "Short label for this block (e.g. 'Solution', 'Fixed Code', 'Example Usage')",
                      "language": "java",
                      "code": "the actual code here — use \\n for newlines inside JSON strings"
                    }
                  ],
                  "keyPoints": ["important point 1", "important point 2"],
                  "commonMistakes": ["mistake to avoid 1", "mistake to avoid 2"],
                  "resources": ["Official Java Docs", "Stack Overflow reference"],
                  "clarificationQuestions": []
                }

                Rules:
                - "codeBlocks" must contain at least one block with real, working code.
                - "code" must be properly escaped for JSON strings (\\n for newlines, \\" for quotes).
                - "explanation" should be clear, educational, and explain WHY not just WHAT.
                - "keyPoints" should highlight the most important concepts or gotchas.
                - For debugging: identify the ROOT CAUSE, not just the symptom.
                - For code generation: write clean, production-quality code with inline comments.
                - For algorithms: include time and space complexity in keyPoints.
                - For hello world / simple examples: include a complete, runnable example.
                - "clarificationQuestions" should be an empty array [] for concrete requests.
                """;
    }
}
