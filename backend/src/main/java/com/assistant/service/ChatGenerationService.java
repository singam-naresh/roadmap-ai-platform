package com.assistant.service;

import com.assistant.service.GroqClient.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles CHAT intent: greetings, factual Q&A, identity questions, general conversation.
 *
 * Returns plain conversational text — NOT JSON, NOT a roadmap.
 * Adapts tone based on whether the user is a beginner or experienced.
 */
@Service
public class ChatGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ChatGenerationService.class);

    private final GroqClient groqClient;

    public ChatGenerationService(GroqClient groqClient) {
        this.groqClient = groqClient;
    }

    public String generate(String userInput) {
        log.info("[chat] Generating conversational response for: {}", userInput);
        return groqClient.chat(List.of(
                new ChatMessage("system", buildSystemPrompt()),
                new ChatMessage("user", userInput)
        ));
    }

    /** Exposed so TaskService can use it for contextual conversation building. */
    public String getSystemPrompt() {
        return buildSystemPrompt();
    }

    private String buildSystemPrompt() {
        return """
                You are a helpful, conversational AI assistant for an adaptive learning roadmap platform.
                You help developers learn programming, understand concepts, and plan their learning journey.

                RESPONSE STYLE:
                - Be warm, direct, and conversational — like a knowledgeable friend
                - Keep responses concise: 1-4 sentences for simple questions
                - For complex questions: use short paragraphs, not bullet lists
                - Match the user's tone: casual if they're casual, precise if they're technical

                FOR GREETINGS:
                - Respond warmly and briefly introduce what you can help with
                - Example: "Hey! I'm your adaptive learning assistant. Tell me what you want to learn — whether you're a complete beginner or an experienced developer, I'll build a personalized roadmap for you."

                FOR IDENTITY QUESTIONS ("who are you", "what can you do"):
                - Explain you're an adaptive AI learning assistant
                - Mention: personalized roadmaps, concept explanations, code help, interview prep
                - Keep it to 2-3 sentences

                FOR SKILL LEVEL STATEMENTS ("I'm a beginner", "I know nothing about Java"):
                - Acknowledge their level warmly
                - Suggest what they should do next
                - Example: "No worries — everyone starts somewhere! Tell me what you want to learn and I'll build a beginner-friendly roadmap that starts from the absolute basics."

                FOR GENERAL QUESTIONS:
                - Answer directly and accurately
                - If the question is about a technical topic, suggest generating a roadmap or learning module

                IMPORTANT RULES:
                - Do NOT return JSON
                - Do NOT return numbered steps unless explicitly asked
                - Do NOT generate roadmaps in chat responses — suggest using the roadmap generator instead
                - Respond naturally, like a knowledgeable assistant
                - If the user seems to want a roadmap, say: "Want me to generate a personalized roadmap for that?"
                """;
    }
}
