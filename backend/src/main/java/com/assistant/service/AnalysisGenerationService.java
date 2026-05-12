package com.assistant.service;

import com.assistant.service.GroqClient.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles ANALYSIS intent: comparisons, reviews, evaluations, pros/cons.
 * Returns a structured JSON response with comparison sections and scoring.
 */
@Service
public class AnalysisGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisGenerationService.class);

    private final GroqClient groqClient;

    public AnalysisGenerationService(GroqClient groqClient) {
        this.groqClient = groqClient;
    }

    public String generate(String userInput) {
        log.info("[analysis] Generating analysis response for: {}", userInput);

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", buildSystemPrompt()),
                new ChatMessage("user", userInput)
        );

        return groqClient.chat(messages);
    }

    private String buildSystemPrompt() {
        return """
                You are an expert analytical assistant for the Adaptive AI Learning Roadmap Platform.

                Return ONLY a valid JSON object. No markdown outside the JSON. No text before or after.

                The JSON must follow this exact structure:
                {
                  "summary": "2-3 sentence overview of the analysis and key conclusion",
                  "verdict": "Clear recommendation or conclusion in one sentence",
                  "sections": [
                    {
                      "title": "Section title (e.g. 'Performance', 'Learning Curve', 'Pros')",
                      "content": "Detailed analysis for this section",
                      "score": 8
                    }
                  ],
                  "pros": ["pro 1", "pro 2"],
                  "cons": ["con 1", "con 2"],
                  "recommendations": ["recommendation 1", "recommendation 2"],
                  "useCases": ["best use case 1", "best use case 2"]
                }

                Rules:
                - "sections" should have 3-5 sections covering the most important dimensions.
                - "score" in each section is 1-10 (optional, use null if not applicable).
                - "verdict" must be a clear, opinionated conclusion — not wishy-washy.
                - "pros" and "cons" should be specific, not generic.
                - For comparisons: analyze each option fairly before giving a verdict.
                - For reviews: be honest about weaknesses, not just strengths.
                - For evaluations: use concrete criteria, not vague impressions.
                """;
    }
}
