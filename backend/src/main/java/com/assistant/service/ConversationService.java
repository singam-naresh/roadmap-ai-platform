package com.assistant.service;

import com.assistant.model.Conversation;
import com.assistant.model.Message;
import com.assistant.model.User;
import com.assistant.repository.ConversationRepository;
import com.assistant.repository.MessageRepository;
import com.assistant.service.GroqClient.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Manages multi-turn conversations with full session context preservation.
 *
 * Key behaviors:
 *   - Persists skill level, locked intent, and domain per conversation
 *   - Every follow-up inherits the session's detected skill level
 *   - Intent is locked after the first message unless explicitly changed
 *   - Conversation history is injected into every AI request
 *
 * This prevents context drift — a beginner who said "I don't know Java"
 * will always get beginner content for the rest of the session.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    // Max messages to inject as context (keeps prompts manageable)
    private static final int MAX_CONTEXT_MESSAGES = 12;

    private final ConversationRepository conversationRepository;
    private final MessageRepository      messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                                MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository      = messageRepository;
    }

    // =========================================================================
    // Session creation
    // =========================================================================

    /**
     * Creates a new conversation with detected session context.
     * The skill level, intent, and domain are locked for the session.
     */
    @Transactional
    public Conversation createConversation(String firstUserMessage,
                                            String skillLevel,
                                            String intentType,
                                            String domain,
                                            User user) {
        Conversation conv = new Conversation();
        conv.setTitle(truncate(firstUserMessage, 80));
        conv.setStatus("ACTIVE");
        conv.setSkillLevel(skillLevel);
        conv.setLockedIntent(intentType);
        conv.setDomain(domain);
        conv.setUser(user);

        Conversation saved = conversationRepository.save(conv);

        // Persist the first user message
        appendMessage(saved, "user", firstUserMessage, null, null);

        log.info("[conv] Created conversation {} — intent={} skill={} domain={}",
                saved.getId(), intentType, skillLevel, domain);
        return saved;
    }

    /**
     * Legacy overload — creates conversation without context (defaults to intermediate/CHAT).
     */
    @Transactional
    public Conversation createConversation(String firstUserMessage) {
        return createConversation(firstUserMessage, "intermediate", "CHAT", "GENERAL", null);
    }

    // =========================================================================
    // Session context retrieval
    // =========================================================================

    /**
     * Returns the session context for a conversation.
     * Used to restore skill level, intent, and domain for follow-up prompts.
     */
    public SessionContext getSessionContext(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .map(conv -> new SessionContext(
                        conv.getSkillLevel() != null ? conv.getSkillLevel() : "intermediate",
                        conv.getLockedIntent() != null ? conv.getLockedIntent() : "CHAT",
                        conv.getDomain() != null ? conv.getDomain() : "GENERAL"
                ))
                .orElse(SessionContext.defaults());
    }

    /**
     * Updates the session context if better information is detected.
     * Only updates if the new value is more specific than the current one.
     */
    @Transactional
    public void updateSessionContext(Long conversationId, String skillLevel, String domain) {
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            boolean changed = false;
            // Only update skill level if it's more specific (beginner > intermediate > advanced)
            if (skillLevel != null && !skillLevel.equals("intermediate")) {
                conv.setSkillLevel(skillLevel);
                changed = true;
            }
            // Only update domain if it's more specific than GENERAL
            if (domain != null && !domain.equals("GENERAL") &&
                (conv.getDomain() == null || conv.getDomain().equals("GENERAL"))) {
                conv.setDomain(domain);
                changed = true;
            }
            if (changed) {
                conversationRepository.save(conv);
                log.debug("[conv] Updated session context {} — skill={} domain={}", conversationId, skillLevel, domain);
            }
        });
    }

    // =========================================================================
    // Message management
    // =========================================================================

    @Transactional
    public void appendUserMessage(Long conversationId, String content) {
        Conversation conv = getConversation(conversationId);
        appendMessage(conv, "user", content, null, null);
        conv.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    @Transactional
    public void appendAssistantMessage(Long conversationId, String content,
                                        String intentType, Long taskId) {
        Conversation conv = getConversation(conversationId);
        appendMessage(conv, "assistant", content, intentType, taskId);
        conv.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    // =========================================================================
    // Context-aware prompt building
    // =========================================================================

    /**
     * Builds a message list with:
     *   1. System prompt (with session context injected)
     *   2. Last N conversation messages (oldest first)
     *   3. Current user input
     *
     * This gives the AI full memory of the session including the user's
     * skill level, domain, and conversation history.
     */
    public List<ChatMessage> buildContextualMessages(Long conversationId,
                                                      String systemPrompt,
                                                      String currentUserInput) {
        List<ChatMessage> messages = new ArrayList<>();

        // Inject session context into the system prompt
        SessionContext ctx = getSessionContext(conversationId);
        String enrichedSystem = systemPrompt + buildSessionContextBlock(ctx);
        messages.add(new ChatMessage("system", enrichedSystem));

        // Inject conversation history (oldest first)
        List<Message> history = messageRepository
                .findTop10ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(history);

        for (Message m : history) {
            // Truncate very long messages to keep context manageable
            String content = m.getContent();
            if (content != null && content.length() > 800) {
                content = content.substring(0, 800) + "… [truncated for context]";
            }
            messages.add(new ChatMessage(m.getRole(), content));
        }

        messages.add(new ChatMessage("user", currentUserInput));
        return messages;
    }

    /**
     * Builds a context block that gets appended to the system prompt.
     * This ensures the AI always knows the user's skill level and domain.
     */
    public String buildSessionContextBlock(SessionContext ctx) {
        if (ctx == null) return "";
        StringBuilder sb = new StringBuilder("\n\n=== SESSION CONTEXT (MUST FOLLOW) ===\n");
        sb.append("User skill level: ").append(ctx.skillLevel()).append("\n");
        sb.append("Session domain: ").append(ctx.domain()).append("\n");
        sb.append("Active intent: ").append(ctx.lockedIntent()).append("\n");

        if ("beginner".equalsIgnoreCase(ctx.skillLevel())) {
            sb.append("\n⚠ BEGINNER USER — STRICT CONTENT RULES:\n");
            sb.append("This user is a BEGINNER. They explicitly stated they are new to this topic.\n");
            sb.append("ABSOLUTELY DO NOT include any of these topics:\n");
            sb.append("  - Kubernetes, Docker Compose advanced, Terraform\n");
            sb.append("  - Kafka, message queues, event streaming\n");
            sb.append("  - Microservices, distributed systems, service mesh\n");
            sb.append("  - CI/CD pipelines, GitOps, ArgoCD\n");
            sb.append("  - JWT, OAuth, API Gateway\n");
            sb.append("  - Cloud architecture, load balancing, auto-scaling\n");
            sb.append("  - Design patterns (CQRS, Event Sourcing, Saga)\n");
            sb.append("  - GPU orchestration, tensor parallelism, DeepSpeed\n");
            sb.append("\nINSTEAD, focus on:\n");
            sb.append("  - Programming fundamentals (variables, loops, functions)\n");
            sb.append("  - Core language syntax and basic data structures\n");
            sb.append("  - Simple projects that build confidence\n");
            sb.append("  - Step-by-step explanations with examples\n");
        } else if ("advanced".equalsIgnoreCase(ctx.skillLevel())) {
            sb.append("\nADVANCED USER: Skip basics. Focus on production-grade patterns, architecture, and performance.\n");
        }
        sb.append("=== END SESSION CONTEXT ===");
        return sb.toString();
    }

    // =========================================================================
    // Context resolution from conversation history
    // =========================================================================

    /**
     * Detects whether a prompt is vague — i.e. it has no specific topic,
     * domain, or skill signal of its own.
     *
     * Examples of vague prompts:
     *   "generate roadmap", "roadmap for this", "make me a roadmap",
     *   "generate a personalized roadmap", "create a plan for me"
     */
    public boolean isVagueRoadmapPrompt(String userInput) {
        String lower = userInput.toLowerCase().trim();
        // Short prompts with no domain keywords are vague
        if (lower.split("\\s+").length > 12) return false; // long prompts have their own context

        boolean hasDomain = containsAny(lower,
            "java", "python", "javascript", "react", "spring", "node", "kotlin",
            "devops", "kubernetes", "docker", "terraform", "aws", "gcp", "azure",
            "machine learning", "ai", "ml", "pytorch", "tensorflow", "llm",
            "data engineering", "spark", "kafka", "sql", "database",
            "frontend", "backend", "fullstack", "mobile", "android", "ios",
            "system design", "distributed", "microservices", "security");

        return !hasDomain;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }

    /**
     * Resolves topic, skill level, and domain from recent conversation messages.
     *
     * Scans the last N messages for:
     *   - Skill signals ("I know nothing", "I'm a beginner", "I already know X")
     *   - Domain signals ("Java", "Python", "React", "Kubernetes", etc.)
     *   - Topic signals (what the user has been discussing)
     *
     * Returns a ResolvedContext with the best available information.
     */
    public ResolvedContext resolveContextFromHistory(Long conversationId) {
        if (conversationId == null) return ResolvedContext.empty();

        List<Message> history = messageRepository
                .findTop10ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(history); // oldest first

        if (history.isEmpty()) return ResolvedContext.empty();

        String combinedText = history.stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(Message::getContent)
                .filter(c -> c != null)
                .collect(java.util.stream.Collectors.joining(" "))
                .toLowerCase();

        // ── Skill level detection from history ────────────────────────────────
        String skillLevel = "intermediate";
        if (containsAny(combinedText,
                "know nothing", "no idea", "never", "from scratch", "from zero",
                "complete beginner", "total beginner", "absolute beginner",
                "don't know", "no experience", "zero experience",
                "just starting", "first time", "teach me", "help me learn",
                "i want to learn", "new to programming", "new to coding")) {
            skillLevel = "beginner";
        } else if (containsAny(combinedText,
                "already know", "i know", "familiar with", "experienced",
                "worked with", "i've built", "i have built", "senior",
                "production", "at scale", "advanced")) {
            skillLevel = "advanced";
        }

        // ── Domain detection from history ─────────────────────────────────────
        String domain = "GENERAL";
        String topic  = null;

        if (containsAny(combinedText, "java")) {
            domain = "JAVA_BACKEND"; topic = "Java";
        } else if (containsAny(combinedText, "spring boot", "spring")) {
            domain = "JAVA_BACKEND"; topic = "Spring Boot";
        } else if (containsAny(combinedText, "react", "next.js", "nextjs")) {
            domain = "REACT_FRONTEND"; topic = "React";
        } else if (containsAny(combinedText, "javascript", "typescript", "js", "ts")) {
            domain = "REACT_FRONTEND"; topic = "JavaScript";
        } else if (containsAny(combinedText, "python")) {
            domain = "AI_ENGINEERING"; topic = "Python";
        } else if (containsAny(combinedText, "machine learning", "ml", "ai", "pytorch", "tensorflow", "llm")) {
            domain = "AI_ENGINEERING"; topic = "AI/ML";
        } else if (containsAny(combinedText, "kubernetes", "docker", "terraform", "devops", "ci/cd")) {
            domain = "DEVOPS"; topic = "DevOps";
        } else if (containsAny(combinedText, "system design", "distributed systems", "scalability")) {
            domain = "SYSTEM_DESIGN"; topic = "System Design";
        } else if (containsAny(combinedText, "data engineering", "spark", "airflow", "kafka")) {
            domain = "DATA_ENGINEERING"; topic = "Data Engineering";
        } else if (containsAny(combinedText, "android", "ios", "mobile", "flutter", "react native")) {
            domain = "MOBILE_DEVELOPMENT"; topic = "Mobile Development";
        } else if (containsAny(combinedText, "aws", "gcp", "azure", "cloud")) {
            domain = "CLOUD_ENGINEERING"; topic = "Cloud Engineering";
        } else if (containsAny(combinedText, "security", "cybersecurity", "penetration")) {
            domain = "CYBERSECURITY"; topic = "Cybersecurity";
        }

        // ── Extract the most recent user message as topic hint ────────────────
        String lastUserMessage = history.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second) // last element
                .map(Message::getContent)
                .orElse(null);

        log.info("[conv] Resolved context from history — skill={} domain={} topic='{}'",
                skillLevel, domain, topic);

        return new ResolvedContext(skillLevel, domain, topic, lastUserMessage, !domain.equals("GENERAL"));
    }

    /**
     * Holds context resolved from conversation history.
     */
    public record ResolvedContext(
            String skillLevel,
            String domain,
            String topic,
            String lastUserMessage,
            boolean hasContext
    ) {
        public static ResolvedContext empty() {
            return new ResolvedContext("intermediate", "GENERAL", null, null, false);
        }
    }

    // =========================================================================
    // Queries
    // =========================================================================

    public List<Message> getMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public List<Conversation> getConversationsForUser(User user) {
        return conversationRepository.findByUserOrderByLastMessageAtDesc(user);
    }

    public List<Conversation> getAllConversations() {
        return conversationRepository.findAllByOrderByLastMessageAtDesc();
    }

    public boolean isFollowUp(String userInput) {
        String lower = userInput.toLowerCase().trim();
        return FOLLOW_UP_SIGNALS.stream().anyMatch(lower::contains);
    }

    @Transactional
    public void markInactiveIfStale(Long conversationId) {
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            if (conv.getLastMessageAt() != null &&
                conv.getLastMessageAt().isBefore(LocalDateTime.now().minusHours(24))) {
                conv.setStatus("INACTIVE");
                conversationRepository.save(conv);
            }
        });
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private Conversation getConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + id));
    }

    private void appendMessage(Conversation conv, String role, String content,
                                String intentType, Long taskId) {
        Message msg = new Message();
        msg.setConversation(conv);
        msg.setRole(role);
        msg.setContent(content);
        msg.setIntentType(intentType);
        msg.setTaskId(taskId);
        messageRepository.save(msg);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static final List<String> FOLLOW_UP_SIGNALS = List.of(
            "tell me more", "expand on", "explain more", "more detail",
            "continue", "go on", "what else", "elaborate",
            "can you explain", "what about", "how about", "and then",
            "what does that mean", "give me an example", "show me an example",
            "generate a roadmap for this", "roadmap for this",
            "make it beginner", "make it advanced", "simplify this"
    );

    // =========================================================================
    // Data classes
    // =========================================================================

    /**
     * Immutable session context — skill level, intent, and domain
     * locked for the duration of a conversation.
     */
    public record SessionContext(String skillLevel, String lockedIntent, String domain) {
        public static SessionContext defaults() {
            return new SessionContext("intermediate", "CHAT", "GENERAL");
        }
    }
}
