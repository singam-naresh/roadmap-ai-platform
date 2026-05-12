package com.assistant.service;

import com.assistant.service.GroqClient.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles PRODUCTIVITY intent: scheduling, habits, time management, focus systems.
 *
 * Returns a structured JSON response designed for personal organization —
 * schedules, priorities, task blocks, time organization.
 * NOT a roadmap. No learning timelines, no career phases.
 */
@Service
public class ProductivityGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ProductivityGenerationService.class);

    private final GroqClient groqClient;

    public ProductivityGenerationService(GroqClient groqClient) {
        this.groqClient = groqClient;
    }

    public String generate(String userInput) {
        log.info("[productivity] Generating productivity response for: {}", userInput);

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", buildSystemPrompt()),
                new ChatMessage("user", userInput)
        );

        return groqClient.chat(messages);
    }

    private String buildSystemPrompt() {
        return """
                You are an expert productivity coach and personal organization specialist for the Adaptive AI Learning Roadmap Platform.

                Return ONLY a valid JSON object. No markdown outside the JSON. No text before or after.

                The JSON must follow this exact structure:
                {
                  "summary": "2-3 sentence overview of the productivity system or schedule being created",
                  "systemTitle": "Name of the productivity system or schedule",
                  "overview": "Brief explanation of the approach and why it works",
                  "schedule": [
                    {
                      "timeBlock": "Time or period (e.g. '6:00 AM - 7:00 AM', 'Morning', 'Monday')",
                      "activity": "What to do during this block",
                      "priority": "High | Medium | Low",
                      "duration": "Duration of this block"
                    }
                  ],
                  "priorities": ["top priority 1", "top priority 2", "top priority 3"],
                  "habits": ["daily habit 1", "daily habit 2"],
                  "tools": ["recommended tool 1", "recommended tool 2"],
                  "tips": ["productivity tip 1", "productivity tip 2"],
                  "commonMistakes": ["mistake to avoid 1", "mistake to avoid 2"],
                  "weeklyReview": "How to review and adjust this system weekly"
                }

                Rules:
                - "schedule" must contain at least 3 time blocks relevant to the user's request.
                - "timeBlock" should be specific (time ranges, days, or periods like Morning/Afternoon/Evening).
                - "priority" must be exactly: High, Medium, or Low.
                - "habits" should be small, actionable daily habits that support the system.
                - "tools" should name specific apps or tools (Notion, Todoist, Google Calendar, etc.).
                - "tips" should be practical, not generic.
                - "weeklyReview" should be a concrete 2-3 sentence review process.
                - Do NOT generate a learning roadmap or career path.
                - Do NOT include learning timelines or skill acquisition phases.
                """;
    }
}
