package com.assistant.service;

import com.assistant.dto.TaskResponse;
import com.assistant.dto.TaskRequest;
import com.assistant.model.Task;
import com.assistant.model.User;
import com.assistant.repository.TaskRepository;
import com.assistant.service.AIIntentClassifier.AIIntentType;
import com.assistant.service.GroqClient.ChatMessage;
import com.assistant.config.ObservabilityConfig.AIPipelineMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final int MAX_RETRIES = 2;

    // ── Prompt versioning (Task 6) ────────────────────────────────────────────
    private static final String PROMPT_VERSION  = "2.0";
    private static final String SCHEMA_VERSION  = "2.0";
    private static final String MODEL_NAME      = "llama-3.3-70b-versatile";

    private final TaskRepository           taskRepository;
    private final GroqClient               groqClient;
    private final ObjectMapper             objectMapper;
    private final AIIntentClassifier       intentClassifier;
    private final ResponseValidator        responseValidator;
    private final ChatGenerationService    chatService;
    private final CodingGenerationService  codingService;
    private final AnalysisGenerationService analysisService;
    private final LearningGenerationService learningService;
    private final ProductivityGenerationService productivityService;
    private final ConversationService      conversationService;
    private final ResourceEnhancementService resourceEnhancementService;
    private final UserService              userService;
    private final EnhancedAIPipeline enhancedAIPipeline;
    private final RoadmapService roadmapService;
    private final DomainDetectionEngine domainEngine;
    private final TechnicalSpecificityEngine specificityEngine;
    private final StackConsistencyEngine stackConsistencyEngine;
    private final AIPipelineMetrics metrics;
    private final GoalFeasibilityEngine feasibilityEngine;

    public TaskService(TaskRepository taskRepository,
                       GroqClient groqClient,
                       ObjectMapper objectMapper,
                       AIIntentClassifier intentClassifier,
                       ResponseValidator responseValidator,
                       ChatGenerationService chatService,
                       CodingGenerationService codingService,
                       AnalysisGenerationService analysisService,
                       LearningGenerationService learningService,
                       ProductivityGenerationService productivityService,
                       ConversationService conversationService,
                       ResourceEnhancementService resourceEnhancementService,
                       UserService userService,
                       RoadmapService roadmapService,
                       EnhancedAIPipeline enhancedAIPipeline,
                       DomainDetectionEngine domainEngine,
                       TechnicalSpecificityEngine specificityEngine,
                       StackConsistencyEngine stackConsistencyEngine,
                       AIPipelineMetrics metrics,
                       GoalFeasibilityEngine feasibilityEngine) {
        this.taskRepository      = taskRepository;
        this.groqClient          = groqClient;
        this.objectMapper        = objectMapper;
        this.intentClassifier    = intentClassifier;
        this.responseValidator   = responseValidator;
        this.chatService         = chatService;
        this.codingService       = codingService;
        this.analysisService     = analysisService;
        this.learningService     = learningService;
        this.productivityService = productivityService;
        this.conversationService = conversationService;
        this.resourceEnhancementService = resourceEnhancementService;
        this.userService         = userService;
        this.roadmapService      = roadmapService;
        this.enhancedAIPipeline  = enhancedAIPipeline;
        this.domainEngine        = domainEngine;
        this.specificityEngine   = specificityEngine;
        this.stackConsistencyEngine = stackConsistencyEngine;
        this.metrics             = metrics;
        this.feasibilityEngine   = feasibilityEngine;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public TaskResponse processTask(TaskRequest request) {
        String userInput     = request.getUserInput().trim();
        String mode          = normalizeMode(request.getMode());
        Long   conversationId = request.getConversationId();

        // ── Step 1: Check for recent duplicates ──────────────────────────────
        if (isDuplicateRequest(userInput, conversationId)) {
            log.info("[task] Duplicate request detected, returning existing response");
            return getRecentSimilarTask(userInput);
        }

        // ── Step 2: Restore session context if continuing a conversation ─────
        ConversationService.SessionContext sessionCtx = null;
        if (conversationId != null) {
            sessionCtx = conversationService.getSessionContext(conversationId);
            log.info("[task] Restored session context — intent={} skill={} domain={}",
                    sessionCtx.lockedIntent(), sessionCtx.skillLevel(), sessionCtx.domain());
        }

        // ── Step 3: Classify intent ──────────────────────────────────────────
        // Use locked intent from session if available; otherwise classify fresh
        AIIntentType intent;
        if (sessionCtx != null && sessionCtx.lockedIntent() != null
                && !sessionCtx.lockedIntent().equals("CHAT")) {
            // Keep the session's locked intent for continuations
            try {
                intent = AIIntentType.valueOf(sessionCtx.lockedIntent());
                log.info("[task] Using locked session intent: {}", intent);
            } catch (IllegalArgumentException e) {
                intent = intentClassifier.classify(userInput);
            }
        } else {
            intent = intentClassifier.classify(userInput);
        }
        log.info("[task] Intent: {}, Mode: {}, ConvId: {}, Input: {}", intent, mode, conversationId, userInput);

        // ── Step 4: Feasibility check for roadmap/learning goals ─────────────
        if (intent == AIIntentType.ROADMAP || intent == AIIntentType.LEARNING) {
            GoalFeasibilityEngine.FeasibilityResult feasibility = feasibilityEngine.assess(userInput);
            if (!feasibility.feasible) {
                log.info("[task] Unrealistic goal detected: {}", userInput);
                Task task = versionedTaskBuilder(userInput,
                        feasibility.explanation + "\n\n" + feasibility.acceleratedAlternative,
                        mode, "general", "general", "CHAT").build();
                Task saved = taskRepository.save(task);
                return TaskResponse.builder()
                        .id(saved.getId())
                        .userInput(userInput)
                        .intentType("CHAT")
                        .category("general")
                        .skillLevel("general")
                        .mode(mode)
                        .aiOutput(feasibility.explanation)
                        .createdAt(saved.getCreatedAt())
                        .message(feasibility.explanation)
                        .suggestions(List.of(
                                "Generate an accelerated " + (feasibility.domain != null ? feasibility.domain : "learning") + " roadmap",
                                "Show me a realistic " + (feasibility.minimumRealisticEstimate != null ? feasibility.minimumRealisticEstimate : "timeline") + " plan",
                                "What can I realistically achieve in 4 weeks?"
                        ))
                        .summary("Goal feasibility check: " + feasibility.minimumRealisticEstimate + " minimum required")
                        .build();
            }
        }

        // ── Step 5: Route to dedicated handler ───────────────────────────────
        TaskResponse response = switch (intent) {
            case CHAT        -> processChatTask(userInput, mode, conversationId);
            case CODING      -> processCodingTask(userInput, mode);
            case ANALYSIS    -> processAnalysisTask(userInput, mode);
            case LEARNING    -> processLearningTask(userInput, mode, sessionCtx);
            case PRODUCTIVITY -> processProductivityTask(userInput, mode);
            case ROADMAP, STARTUP -> processRoadmapTask(userInput, mode, intent, sessionCtx, conversationId);
        };

        // ── Step 6: Update session context with detected skill/domain ────────
        if (conversationId != null && response != null) {
            conversationService.updateSessionContext(
                    conversationId,
                    response.getSkillLevel(),
                    detectDomainFromCategory(response.getCategory())
            );
        }

        // ── Step 7: Validate and normalize before returning ──────────────────
        return responseValidator.validate(response);
    }

    public List<TaskResponse> getAllTasks() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
        
        return taskRepository.findAllByUserOrderByCreatedAtDesc(currentUser)
                .stream()
                .map(this::toResponseFromStored)
                .collect(Collectors.toList());
    }

    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
        return toResponseFromStored(task);
    }

    // =========================================================================
    // Intent Handlers
    // =========================================================================

    /** CHAT: conversational response with multi-turn memory support */
    private TaskResponse processChatTask(String userInput, String mode, Long conversationId) {
        String rawResponse;

        if (conversationId != null) {
            // Continue existing conversation with full context
            String systemPrompt = chatService.getSystemPrompt();
            List<ChatMessage> messages = conversationService.buildContextualMessages(
                    conversationId, systemPrompt, userInput);
            rawResponse = groqClient.chat(messages);
            conversationService.appendUserMessage(conversationId, userInput);
        } else {
            rawResponse = chatService.generate(userInput);
            // Detect skill/domain from this first message so follow-ups inherit it
            String detectedSkill  = detectSkillLevel(userInput);
            String detectedDomain = detectDomainFromInput(userInput);
            var conv = conversationService.createConversation(
                    userInput, detectedSkill, "CHAT", detectedDomain,
                    userService.getCurrentUser());
            conversationId = conv.getId();
        }

        Task task = versionedTaskBuilder(userInput, rawResponse, mode, "chat", "general", "CHAT").build();
        Task saved = taskRepository.save(task);

        conversationService.appendAssistantMessage(conversationId, rawResponse, "CHAT", saved.getId());

        // Update session context — if user revealed skill/domain in this message, persist it
        String detectedSkill  = detectSkillLevel(userInput);
        String detectedDomain = detectDomainFromInput(userInput);
        if (!detectedSkill.equals("intermediate") || !detectedDomain.equals("GENERAL")) {
            conversationService.updateSessionContext(conversationId, detectedSkill, detectedDomain);
        }

        final Long finalConvId = conversationId;
        return TaskResponse.builder()
                .id(saved.getId())
                .userInput(userInput)
                .intentType("CHAT")
                .category("chat")
                .skillLevel("general")
                .mode(mode)
                .aiOutput(rawResponse)
                .createdAt(saved.getCreatedAt())
                .message(rawResponse)
                .suggestions(buildChatSuggestions(userInput))
                .summary(truncate(rawResponse, 120))
                .conversationId(finalConvId)
                .build();
    }

    /** CODING: structured code response with code blocks */
    private TaskResponse processCodingTask(String userInput, String mode) {
        String rawResponse = codingService.generate(userInput);
        CodingOutput parsed = parseCodingOutput(rawResponse);

        Task task = versionedTaskBuilder(userInput, rawResponse, mode, "coding", "intermediate", "CODING").build();
        Task saved = taskRepository.save(task);

        // Enhance resources with functional links
        List<String> enhancedResources = resourceEnhancementService.enhanceResources(
            parsed.resources(), "coding", userInput);

        return TaskResponse.builder()
                .id(saved.getId())
                .userInput(userInput)
                .intentType("CODING")
                .category("coding")
                .skillLevel("intermediate")
                .mode(mode)
                .aiOutput(rawResponse)
                .createdAt(saved.getCreatedAt())
                .summary(parsed.summary())
                .language(parsed.language())
                .explanation(parsed.explanation())
                .codeBlocks(parsed.codeBlocks())
                .keyPoints(parsed.keyPoints())
                .commonMistakes(parsed.commonMistakes())
                .resources(enhancedResources)
                .clarificationQuestions(parsed.clarificationQuestions())
                .build();
    }

    /** ANALYSIS: comparison/review response with sections */
    private TaskResponse processAnalysisTask(String userInput, String mode) {
        String rawResponse = analysisService.generate(userInput);
        AnalysisOutput parsed = parseAnalysisOutput(rawResponse);

        Task task = versionedTaskBuilder(userInput, rawResponse, mode, "analysis", "intermediate", "ANALYSIS").build();
        Task saved = taskRepository.save(task);

        return TaskResponse.builder()
                .id(saved.getId())
                .userInput(userInput)
                .intentType("ANALYSIS")
                .category("analysis")
                .skillLevel("intermediate")
                .mode(mode)
                .aiOutput(rawResponse)
                .createdAt(saved.getCreatedAt())
                .summary(parsed.summary())
                .verdict(parsed.verdict())
                .sections(parsed.sections())
                .pros(parsed.pros())
                .cons(parsed.cons())
                .recommendations(parsed.recommendations())
                .useCases(parsed.useCases())
                .build();
    }

    /** LEARNING: concept explanation, examples, exercises — NOT a roadmap */
    private TaskResponse processLearningTask(String userInput, String mode,
                                              ConversationService.SessionContext sessionCtx) {
        // Pass session skill level so learning content matches the user's level
        String sessionSkill = sessionCtx != null ? sessionCtx.skillLevel() : null;
        String rawResponse = learningService.generate(userInput, sessionSkill);
        LearningOutput parsed = parseLearningOutput(rawResponse);

        String skillLevel = sessionSkill != null ? sessionSkill : detectSkillLevel(userInput);

        Task task = versionedTaskBuilder(userInput, rawResponse, mode, "learning", skillLevel, "LEARNING").build();
        Task saved = taskRepository.save(task);

        List<String> enhancedResources = resourceEnhancementService.enhanceResources(
            parsed.resources(), "learning", userInput);

        return TaskResponse.builder()
                .id(saved.getId())
                .userInput(userInput)
                .intentType("LEARNING")
                .category("learning")
                .skillLevel(skillLevel)
                .mode(mode)
                .aiOutput(rawResponse)
                .createdAt(saved.getCreatedAt())
                .summary(parsed.summary())
                .conceptTitle(parsed.conceptTitle())
                .conceptExplanation(parsed.explanation())
                .keyPoints(parsed.keyPoints())
                .examples(parsed.examples())
                .commonMisconceptions(parsed.commonMisconceptions())
                .practiceExercises(parsed.practiceExercises())
                .bestPractices(parsed.bestPractices())
                .resources(enhancedResources)
                .nextTopics(parsed.nextTopics())
                .build();
    }

    /** PRODUCTIVITY: schedules, habits, time blocks — NOT a roadmap */
    private TaskResponse processProductivityTask(String userInput, String mode) {
        String rawResponse = productivityService.generate(userInput);
        ProductivityOutput parsed = parseProductivityOutput(rawResponse);

        Task task = versionedTaskBuilder(userInput, rawResponse, mode, "productivity", "intermediate", "PRODUCTIVITY").build();
        Task saved = taskRepository.save(task);

        return TaskResponse.builder()
                .id(saved.getId())
                .userInput(userInput)
                .intentType("PRODUCTIVITY")
                .category("productivity")
                .skillLevel("intermediate")
                .mode(mode)
                .aiOutput(rawResponse)
                .createdAt(saved.getCreatedAt())
                .summary(parsed.summary())
                .systemTitle(parsed.systemTitle())
                .overview(parsed.overview())
                .schedule(parsed.schedule())
                .priorities(parsed.priorities())
                .habits(parsed.habits())
                .tools(parsed.tools())
                .tips(parsed.tips())
                .commonMistakes(parsed.commonMistakes())
                .weeklyReview(parsed.weeklyReview())
                .build();
    }

    /** ROADMAP / STARTUP: structured execution plan with domain intelligence and quality scoring */
    private TaskResponse processRoadmapTask(String userInput, String mode, AIIntentType intent,
                                             ConversationService.SessionContext sessionCtx,
                                             Long conversationId) {

        // ── Step A: Detect if the prompt is vague ────────────────────────────
        boolean isVague = conversationService.isVagueRoadmapPrompt(userInput);

        // ── Step B: Resolve context from conversation history if vague ────────
        ConversationService.ResolvedContext resolved = ConversationService.ResolvedContext.empty();

        if (isVague && conversationId != null) {
            resolved = conversationService.resolveContextFromHistory(conversationId);
            log.info("[roadmap] Vague prompt — resolved from history: skill={} domain={} topic='{}'",
                    resolved.skillLevel(), resolved.domain(), resolved.topic());
        } else if (isVague && sessionCtx != null) {
            // Try to find the conversation ID from the user's most recent conversation
            Long activeConvId = getActiveConversationId(sessionCtx);
            if (activeConvId != null) {
                resolved = conversationService.resolveContextFromHistory(activeConvId);
            }
        }

        // ── Step C: If vague AND no context found → ask for clarification ─────
        if (isVague && !resolved.hasContext() && conversationId == null && sessionCtx == null) {
            log.info("[roadmap] Vague prompt with no context — returning clarification request");
            return buildClarificationResponse(userInput, mode);
        }

        // ── Step D: Build effective userInput for generation ──────────────────
        String effectiveInput = userInput;
        if (isVague && resolved.hasContext() && resolved.topic() != null) {
            effectiveInput = "Generate a " + resolved.skillLevel() + " roadmap for " + resolved.topic();
            if (resolved.lastUserMessage() != null
                    && !resolved.lastUserMessage().equalsIgnoreCase(userInput)
                    && resolved.lastUserMessage().length() < 200) {
                effectiveInput += ". Context: " + resolved.lastUserMessage();
            }
            log.info("[roadmap] Enriched vague prompt to: '{}'", effectiveInput);
        }

        // ── Step E: Determine skill level ─────────────────────────────────────
        String skillLevel;
        if (sessionCtx != null && sessionCtx.skillLevel() != null
                && !sessionCtx.skillLevel().equals("intermediate")) {
            skillLevel = sessionCtx.skillLevel();
            log.info("[roadmap] Using session skill level: {}", skillLevel);
        } else if (resolved.hasContext() && !resolved.skillLevel().equals("intermediate")) {
            skillLevel = resolved.skillLevel();
            log.info("[roadmap] Using history-resolved skill level: {}", skillLevel);
        } else {
            skillLevel = detectSkillLevel(userInput);
        }

        // ── Step F: Determine domain ──────────────────────────────────────────
        String sessionDomain = null;
        if (sessionCtx != null && sessionCtx.domain() != null
                && !sessionCtx.domain().equals("GENERAL")) {
            sessionDomain = sessionCtx.domain();
        } else if (resolved.hasContext() && !resolved.domain().equals("GENERAL")) {
            sessionDomain = resolved.domain();
        }

        // ── Step G: Determine category ────────────────────────────────────────
        String category = detectCategory(effectiveInput, intent);
        if (category.equals("general") && sessionDomain != null) {
            category = domainToCategory(sessionDomain);
        }

        log.info("[roadmap] Generating — category={} skill={} domain={} vague={}",
                category, skillLevel, sessionDomain, isVague);

        // ── Step H: Build conversation summary for context injection ──────────
        // This is what makes the AI reason from prior messages instead of
        // generating a generic roadmap
        String conversationSummary = buildConversationSummary(
                conversationId, resolved, sessionCtx, skillLevel, sessionDomain);

        // ── Step I: Generate roadmap ──────────────────────────────────────────
        EnhancedAIPipeline.RoadmapGenerationResult generationResult =
            enhancedAIPipeline.generateRoadmap(effectiveInput, mode, category, skillLevel,
                    sessionDomain, conversationSummary);

        if (!generationResult.isSuccess()) {
            log.error("[roadmap] Pipeline failed: {}", generationResult.getErrors());
        }

        ParsedOutput parsed = convertJsonToParsedOutput(generationResult.getValidatedJson());
        String storedJson   = toJsonString(parsed);
        String intentStr    = intent.name();

        Task task = versionedTaskBuilder(userInput, storedJson, mode, category, skillLevel, intentStr).build();
        Task saved = taskRepository.save(task);

        // ── Step I: Persist roadmap entity ────────────────────────────────────
        try {
            roadmapService.createRoadmapFromTask(saved.getId());
        } catch (Exception e) {
            log.warn("[roadmap] Failed to create roadmap entity: {}", e.getMessage());
        }

        // ── Step J: Create conversation if none exists, so follow-ups work ────
        Long activeConvId = conversationId;
        if (activeConvId == null) {
            var conv = conversationService.createConversation(
                    userInput, skillLevel, intentStr,
                    sessionDomain != null ? sessionDomain : "GENERAL",
                    userService.getCurrentUser());
            activeConvId = conv.getId();
            conversationService.appendAssistantMessage(activeConvId, storedJson, intentStr, saved.getId());
        } else {
            // Update existing conversation with the roadmap response
            conversationService.appendUserMessage(activeConvId, userInput);
            conversationService.appendAssistantMessage(activeConvId, storedJson, intentStr, saved.getId());
            // Update session context with resolved skill/domain
            conversationService.updateSessionContext(activeConvId, skillLevel,
                    sessionDomain != null ? sessionDomain : "GENERAL");
        }

        TaskResponse response = buildRoadmapResponse(saved, parsed, category, skillLevel, intentStr);
        response.setConversationId(activeConvId); // always attach so frontend threads it
        return response;
    }

    // =========================================================================
    // Roadmap Prompt Building
    // =========================================================================

    private List<ChatMessage> buildRoadmapMessages(String userInput, String mode,
                                                    String category, String skillLevel) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", buildRoadmapSystemPrompt(mode, category, skillLevel)));

        // Enhanced context injection - include user's learning patterns and preferences
        User currentUser = userService.getCurrentUser();
        if (currentUser != null) {
            // Get user's recent tasks for context
            List<Task> recentTasks = fetchRecentUserTasks(currentUser, 5);
            if (!recentTasks.isEmpty()) {
                StringBuilder userContext = new StringBuilder();
                userContext.append("USER CONTEXT — Build on the user's learning journey:\n");
                
                // Analyze user's skill progression
                Map<String, Long> categoryCount = recentTasks.stream()
                    .collect(Collectors.groupingBy(t -> t.getCategory() != null ? t.getCategory() : "general", 
                             Collectors.counting()));
                
                userContext.append("Recent focus areas: ");
                categoryCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .forEach(entry -> userContext.append(entry.getKey()).append(" (").append(entry.getValue()).append(" tasks), "));
                
                userContext.append("\n\nRecent goals:\n");
                recentTasks.stream().limit(3).forEach(task -> 
                    userContext.append("- ").append(task.getUserInput()).append("\n"));
                
                userContext.append("\nTailor this roadmap to build on their existing knowledge and interests.");
                messages.add(new ChatMessage("system", userContext.toString()));
            }
        }

        // Check for follow-up context
        if (conversationService.isFollowUp(userInput)) {
            List<Task> relevantTasks = fetchRelevantTasks(userInput);
            if (!relevantTasks.isEmpty()) {
                StringBuilder ctx = new StringBuilder();
                ctx.append("FOLLOW-UP CONTEXT — This appears to be a follow-up request.\n");
                ctx.append("Previous related conversation:\n\n");
                
                Task mostRelevant = relevantTasks.get(0);
                ctx.append("Previous request: ").append(mostRelevant.getUserInput()).append("\n");
                
                // Try to parse previous roadmap for context
                try {
                    JsonNode previousOutput = objectMapper.readTree(mostRelevant.getAiOutput());
                    JsonNode steps = previousOutput.path("steps");
                    if (steps.isArray() && steps.size() > 0) {
                        ctx.append("Previous roadmap had ").append(steps.size()).append(" steps:\n");
                        for (int i = 0; i < Math.min(3, steps.size()); i++) {
                            ctx.append("- ").append(steps.get(i).asText()).append("\n");
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not parse previous roadmap for context: {}", e.getMessage());
                }
                
                ctx.append("\nBuild on this context and avoid repeating information already covered.");
                messages.add(new ChatMessage("system", ctx.toString()));
            }
        }

        messages.add(new ChatMessage("user", userInput));
        return messages;
    }

    private List<Task> fetchRecentUserTasks(User user, int limit) {
        return taskRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String buildRoadmapSystemPrompt(String mode, String category, String skillLevel) {
        return buildJsonFormatInstructions()
                + "\n" + buildCategoryInstructions(category)
                + "\n" + buildSkillLevelInstructions(skillLevel)
                + "\n" + buildModeInstructions(mode)
                + "\n" + buildStepQualityRules();
    }

    private String buildJsonFormatInstructions() {
        return """
                You are an expert adaptive planning assistant. Generate precise, implementation-focused action plans.

                Return ONLY a valid JSON object. No markdown. No code fences. No text outside the JSON.

                The JSON must contain exactly these fields:
                {
                  "summary": "2-3 sentence description of the approach and expected outcome",
                  "estimatedTime": "realistic time estimate (e.g. '2-3 weeks', '6 months')",
                  "difficulty": "Beginner | Intermediate | Advanced",
                  "prerequisites": ["prerequisite 1", "prerequisite 2"],
                  "steps": ["step 1", "step 2", "step 3"],
                  "tips": ["tip 1", "tip 2"],
                  "mistakesToAvoid": ["mistake 1", "mistake 2"],
                  "resources": ["resource 1", "resource 2"]
                }

                Rules:
                - All fields required. Use [] for empty arrays.
                - "difficulty" must be exactly: Beginner, Intermediate, or Advanced.
                - "steps" must be actionable, sequenced, and implementation-focused.
                """;
    }

    private String buildCategoryInstructions(String category) {
        return switch (category) {
            case "coding" -> """
                    CATEGORY: Software Development
                    - Structure: setup → core concepts → implementation → testing → deployment.
                    - Name specific languages, frameworks, tools, and libraries.
                    - Resources: official docs, GitHub repos, LeetCode, freeCodeCamp, Udemy.
                    """;
            case "career" -> """
                    CATEGORY: Career Development
                    - Structure: self-assessment → skill gap → portfolio/resume → networking → interview prep.
                    - Resources: LinkedIn, Glassdoor, job boards, GitHub, Behance.
                    """;
            case "learning" -> """
                    CATEGORY: Skill Acquisition
                    - Structure: foundation → core concepts → practice → projects → mastery.
                    - Use spaced repetition and active recall principles.
                    - Resources: Coursera, edX, YouTube channels, official docs, books.
                    """;
            case "fitness" -> """
                    CATEGORY: Health & Fitness
                    - Structure: baseline → goal setting → program design → nutrition → tracking.
                    - Resources: MyFitnessPal, Strava, YouTube fitness channels.
                    """;
            case "business", "startup" -> """
                    CATEGORY: Business & Entrepreneurship
                    - Structure: validation → planning → execution → growth → scaling.
                    - Include market research, competitive analysis, customer discovery.
                    - Resources: Notion, Stripe, Mailchimp, YC resources, Indie Hackers.
                    """;
            case "content" -> """
                    CATEGORY: Content Creation
                    - Structure: niche → strategy → production → publishing → growth → monetization.
                    - Resources: Canva, DaVinci Resolve, Notion, analytics platforms.
                    """;
            case "productivity" -> """
                    CATEGORY: Productivity & Organization
                    - Structure: audit → system design → tool setup → habit formation → review.
                    - Recommend GTD, Time Blocking, Pomodoro where relevant.
                    - Resources: Notion, Todoist, Obsidian, Atomic Habits, Deep Work.
                    """;
            default -> """
                    CATEGORY: General Planning
                    - Structure steps logically from preparation through execution to review.
                    - Be specific and actionable. Include concrete tools and resources.
                    """;
        };
    }

    private String buildSkillLevelInstructions(String skillLevel) {
        return switch (skillLevel) {
            case "beginner" -> """
                    SKILL LEVEL: Beginner — assume zero prior knowledge, define terms, build confidence early.
                    """;
            case "advanced" -> """
                    SKILL LEVEL: Advanced — skip basics, focus on architecture, optimization, production concerns.
                    """;
            default -> """
                    SKILL LEVEL: Intermediate — skip fundamentals, focus on practical implementation.
                    """;
        };
    }

    private String buildModeInstructions(String mode) {
        return switch (mode) {
            case "detailed" -> """
                    OUTPUT MODE: DETAILED — Maximum depth and implementation focus
                    - 8-12 comprehensive steps with sub-tasks
                    - Include specific tools, commands, and code examples where applicable
                    - Add implementation details, configuration steps, and troubleshooting
                    - 5-7 expert tips with advanced techniques
                    - 4-5 detailed mistakes with prevention strategies
                    - 6-8 resources including official docs, advanced tutorials, and expert blogs
                    - Assume user wants thorough, production-ready guidance
                    """;
            case "simplified" -> """
                    OUTPUT MODE: SIMPLIFIED — Beginner-friendly essentials
                    - 3-5 high-level steps focusing on core actions only
                    - Use simple language, avoid jargon, explain technical terms
                    - 2-3 basic tips for getting started successfully
                    - 1-2 common beginner mistakes to avoid
                    - 2-3 beginner-friendly resources (tutorials, getting started guides)
                    - Assume user is new to the topic and needs confidence building
                    """;
            case "balanced" -> """
                    OUTPUT MODE: BALANCED — Practical implementation focus
                    - 5-7 well-structured steps with clear outcomes
                    - Balance between detail and accessibility
                    - 3-4 practical tips for efficient execution
                    - 2-3 important mistakes that cause delays
                    - 4-5 quality resources covering theory and practice
                    - Assume user has some experience but needs structured guidance
                    """;
            default -> """
                    OUTPUT MODE: DEFAULT — Standard comprehensive approach
                    - 5-7 steps with moderate detail level
                    - 2-3 tips, 2-3 mistakes, 3-4 resources
                    - Balance accessibility with thoroughness
                    """;
        };
    }

    private String buildStepQualityRules() {
        return """
                STEP QUALITY: Every step must start with an action verb, contain a measurable outcome,
                be specific to the user's goal, and follow logical sequence.
                NEVER use: "learn more about", "explore the topic", "get familiar with".
                """;
    }

    // =========================================================================
    // Parsing: Roadmap
    // =========================================================================

    private static final List<String> FALLBACK_STEPS = List.of(
            "Define your specific goal with a measurable outcome and a target deadline.",
            "Break the goal into 3-5 concrete sub-tasks you can start immediately.",
            "Execute the first sub-task and document what you learn.",
            "Review progress weekly and adjust your approach based on results."
    );
    private static final String FALLBACK_SUMMARY = "A structured plan could not be generated. Use the steps below as a starting framework.";
    private static final List<String> FALLBACK_TIPS = List.of("Start with the smallest possible action to build momentum.");
    private static final List<String> FALLBACK_MISTAKES = List.of("Avoid planning indefinitely without taking action.");

    private ParsedOutput parseRoadmapOutput(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) return null;
        try {
            String cleaned = stripCodeFences(rawResponse);
            String json    = extractJsonObject(cleaned);
            JsonNode root  = objectMapper.readTree(json);

            return new ParsedOutput(
                    root.path("summary").asText(null),
                    root.path("estimatedTime").asText("Varies"),
                    root.path("difficulty").asText("Intermediate"),
                    parseStringArray(root.path("prerequisites")),
                    parseStringArray(root.path("steps")),
                    parseStringArray(root.path("tips")),
                    parseStringArray(root.path("mistakesToAvoid")),
                    parseStringArray(root.path("resources"))
            );
        } catch (Exception e) {
            log.warn("[parse:roadmap] Failed: {}", e.getMessage());
            return null;
        }
    }

    private boolean isQualityAcceptable(ParsedOutput p) {
        if (p == null) return false;
        if (p.steps() == null || p.steps().size() < 3) { log.warn("[quality] < 3 steps"); return false; }
        long short_ = p.steps().stream().filter(s -> s != null && s.trim().length() < 15).count();
        if (short_ > 0) { log.warn("[quality] {} short steps", short_); return false; }
        long distinct = p.steps().stream().map(String::toLowerCase).distinct().count();
        if (distinct < p.steps().size()) { log.warn("[quality] duplicate steps"); return false; }
        if (p.summary() == null || p.summary().isBlank() || p.summary().length() < 30) { log.warn("[quality] bad summary"); return false; }
        return true;
    }

    private ParsedOutput convertJsonToParsedOutput(JsonNode json) {
        try {
            return new ParsedOutput(
                json.path("summary").asText(""),
                json.path("estimatedTime").asText("Varies"),
                json.path("difficulty").asText("Intermediate"),
                parseStringArray(json.path("prerequisites")),
                parseStringArray(json.path("steps")),
                parseStringArray(json.path("tips")),
                parseStringArray(json.path("mistakesToAvoid")),
                parseStringArray(json.path("resources"))
            );
        } catch (Exception e) {
            log.error("[roadmap] Failed to convert JSON to ParsedOutput: {}", e.getMessage());
            return fallbackOutput();
        }
    }

    private ParsedOutput fallbackOutput() {
        return new ParsedOutput(FALLBACK_SUMMARY, "Depends on effort", "Intermediate",
                List.of(), FALLBACK_STEPS, FALLBACK_TIPS, FALLBACK_MISTAKES, List.of());
    }

    private CodingOutput parseCodingOutput(String rawResponse) {
        try {
            String cleaned = stripCodeFences(rawResponse);
            String json    = extractJsonObject(cleaned);
            JsonNode root  = objectMapper.readTree(json);

            List<TaskResponse.CodeBlock> blocks = new ArrayList<>();
            JsonNode blocksNode = root.path("codeBlocks");
            if (blocksNode.isArray()) {
                for (JsonNode b : blocksNode) {
                    blocks.add(new TaskResponse.CodeBlock(
                            b.path("label").asText("Code"),
                            b.path("language").asText("text"),
                            b.path("code").asText("")
                    ));
                }
            }

            return new CodingOutput(
                    root.path("summary").asText("Code response"),
                    root.path("language").asText(""),
                    root.path("explanation").asText(""),
                    blocks,
                    parseStringArray(root.path("keyPoints")),
                    parseStringArray(root.path("commonMistakes")),
                    parseStringArray(root.path("resources")),
                    parseStringArray(root.path("clarificationQuestions"))
            );
        } catch (Exception e) {
            log.warn("[parse:coding] Failed: {}", e.getMessage());
            return new CodingOutput(
                    "Code response",
                    "text",
                    rawResponse,
                    List.of(new TaskResponse.CodeBlock("Response", "text", rawResponse)),
                    List.of(), List.of(), List.of(), List.of()
            );
        }
    }

    // =========================================================================
    // Parsing: Analysis
    // =========================================================================

    private AnalysisOutput parseAnalysisOutput(String rawResponse) {
        try {
            String cleaned = stripCodeFences(rawResponse);
            String json    = extractJsonObject(cleaned);
            JsonNode root  = objectMapper.readTree(json);

            List<TaskResponse.AnalysisSection> sections = new ArrayList<>();
            JsonNode sectionsNode = root.path("sections");
            if (sectionsNode.isArray()) {
                for (JsonNode s : sectionsNode) {
                    TaskResponse.AnalysisSection sec = new TaskResponse.AnalysisSection();
                    sec.setTitle(s.path("title").asText(""));
                    sec.setContent(s.path("content").asText(""));
                    if (!s.path("score").isMissingNode() && !s.path("score").isNull()) {
                        sec.setScore(s.path("score").asInt());
                    }
                    sections.add(sec);
                }
            }

            return new AnalysisOutput(
                    root.path("summary").asText("Analysis response"),
                    root.path("verdict").asText(""),
                    sections,
                    parseStringArray(root.path("pros")),
                    parseStringArray(root.path("cons")),
                    parseStringArray(root.path("recommendations")),
                    parseStringArray(root.path("useCases"))
            );
        } catch (Exception e) {
            log.warn("[parse:analysis] Failed: {}", e.getMessage());
            return new AnalysisOutput(rawResponse, "", List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    // =========================================================================
    // Response Mapping
    // =========================================================================

    private TaskResponse buildRoadmapResponse(Task task, ParsedOutput p,
                                               String category, String skillLevel, String intentType) {
        // Enhance resources with functional links
        List<String> enhancedResources = resourceEnhancementService.enhanceResources(
            p.resources(), category, task.getUserInput());

        return TaskResponse.builder()
                .id(task.getId())
                .userInput(task.getUserInput())
                .intentType(intentType)
                .summary(p.summary())
                .estimatedTime(p.estimatedTime())
                .difficulty(p.difficulty())
                .prerequisites(p.prerequisites())
                .steps(p.steps())
                .tips(p.tips())
                .mistakesToAvoid(p.mistakesToAvoid())
                .resources(enhancedResources)
                .category(category)
                .skillLevel(skillLevel)
                .aiOutput(task.getAiOutput())
                .mode(task.getMode())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private TaskResponse toResponseFromStored(Task task) {
        String intentType = task.getIntentType() != null ? task.getIntentType() : "ROADMAP";
        String category   = task.getCategory()   != null ? task.getCategory()   : "general";
        String skillLevel = task.getSkillLevel()  != null ? task.getSkillLevel() : "intermediate";

        return switch (intentType) {
            case "CHAT" -> TaskResponse.builder()
                    .id(task.getId()).userInput(task.getUserInput())
                    .intentType("CHAT").category(category).skillLevel(skillLevel)
                    .mode(task.getMode()).aiOutput(task.getAiOutput()).createdAt(task.getCreatedAt())
                    .message(task.getAiOutput())
                    .summary(truncate(task.getAiOutput(), 120))
                    .suggestions(List.of())
                    .build();

            case "CODING" -> {
                CodingOutput parsed = parseCodingOutput(task.getAiOutput());
                yield TaskResponse.builder()
                        .id(task.getId()).userInput(task.getUserInput())
                        .intentType("CODING").category(category).skillLevel(skillLevel)
                        .mode(task.getMode()).aiOutput(task.getAiOutput()).createdAt(task.getCreatedAt())
                        .summary(parsed.summary()).language(parsed.language())
                        .explanation(parsed.explanation()).codeBlocks(parsed.codeBlocks())
                        .keyPoints(parsed.keyPoints()).commonMistakes(parsed.commonMistakes())
                        .resources(parsed.resources()).clarificationQuestions(parsed.clarificationQuestions())
                        .build();
            }

            case "ANALYSIS" -> {
                AnalysisOutput parsed = parseAnalysisOutput(task.getAiOutput());
                yield TaskResponse.builder()
                        .id(task.getId()).userInput(task.getUserInput())
                        .intentType("ANALYSIS").category(category).skillLevel(skillLevel)
                        .mode(task.getMode()).aiOutput(task.getAiOutput()).createdAt(task.getCreatedAt())
                        .summary(parsed.summary()).verdict(parsed.verdict())
                        .sections(parsed.sections()).pros(parsed.pros()).cons(parsed.cons())
                        .recommendations(parsed.recommendations()).useCases(parsed.useCases())
                        .build();
            }

            case "LEARNING" -> {
                LearningOutput parsed = parseLearningOutput(task.getAiOutput());
                yield TaskResponse.builder()
                        .id(task.getId()).userInput(task.getUserInput())
                        .intentType("LEARNING").category(category).skillLevel(skillLevel)
                        .mode(task.getMode()).aiOutput(task.getAiOutput()).createdAt(task.getCreatedAt())
                        .summary(parsed.summary()).conceptTitle(parsed.conceptTitle())
                        .conceptExplanation(parsed.explanation()).keyPoints(parsed.keyPoints())
                        .examples(parsed.examples()).commonMisconceptions(parsed.commonMisconceptions())
                        .practiceExercises(parsed.practiceExercises()).bestPractices(parsed.bestPractices())
                        .resources(parsed.resources()).nextTopics(parsed.nextTopics())
                        .build();
            }

            case "PRODUCTIVITY" -> {
                ProductivityOutput parsed = parseProductivityOutput(task.getAiOutput());
                yield TaskResponse.builder()
                        .id(task.getId()).userInput(task.getUserInput())
                        .intentType("PRODUCTIVITY").category(category).skillLevel(skillLevel)
                        .mode(task.getMode()).aiOutput(task.getAiOutput()).createdAt(task.getCreatedAt())
                        .summary(parsed.summary()).systemTitle(parsed.systemTitle())
                        .overview(parsed.overview()).schedule(parsed.schedule())
                        .priorities(parsed.priorities()).habits(parsed.habits())
                        .tools(parsed.tools()).tips(parsed.tips())
                        .commonMistakes(parsed.commonMistakes()).weeklyReview(parsed.weeklyReview())
                        .build();
            }

            default -> {
                // ROADMAP / STARTUP — backward compatible with pre-intent-routing history
                ParsedOutput parsed = parseRoadmapOutput(task.getAiOutput());
                if (parsed == null) parsed = fallbackOutput();
                yield buildRoadmapResponse(task, parsed, category, skillLevel, intentType);
            }
        };
    }

    // =========================================================================
    // Category & Skill Detection (for roadmap intents)
    // =========================================================================

    private String detectCategory(String input, AIIntentType intent) {
        return switch (intent) {
            case STARTUP     -> "startup";
            case PRODUCTIVITY -> "productivity";
            case LEARNING    -> "learning";
            default          -> detectCategoryFromKeywords(input);
        };
    }

    private String detectCategoryFromKeywords(String input) {
        String lower = input.toLowerCase();
        if (containsAny(lower, "code", "coding", "program", "java", "python", "javascript",
                "react", "spring", "api", "backend", "frontend", "database", "sql",
                "docker", "algorithm", "software", "developer", "engineer")) return "coding";
        if (containsAny(lower, "career", "job", "resume", "interview", "linkedin", "salary")) return "career";
        if (containsAny(lower, "learn", "study", "course", "tutorial", "master", "skill")) return "learning";
        if (containsAny(lower, "fitness", "workout", "gym", "diet", "health", "yoga")) return "fitness";
        if (containsAny(lower, "business", "startup", "product", "launch", "market", "entrepreneur")) return "business";
        if (containsAny(lower, "content", "blog", "youtube", "video", "writing", "creator")) return "content";
        if (containsAny(lower, "productive", "productivity", "habit", "routine", "focus")) return "productivity";
        return "general";
    }

    private String detectSkillLevel(String input) {
        String lower = input.toLowerCase();
        if (containsAny(lower, "beginner", "from scratch", "no experience", "basics", "introduction",
                "new to", "don't know", "no idea", "never", "complete beginner", "total beginner",
                "teach me", "help me learn", "i want to learn", "just starting", "first time",
                "no background", "zero experience", "where do i start")) return "beginner";
        if (containsAny(lower, "advanced", "expert", "senior", "deep dive", "architecture",
                "production", "distributed", "kubernetes", "terraform", "deepspeed", "vllm")) return "advanced";
        return "intermediate";
    }

    /** Maps a category string to a domain identifier for session context. */
    private String detectDomainFromCategory(String category) {
        if (category == null) return "GENERAL";
        return switch (category.toLowerCase()) {
            case "coding"   -> "JAVA_BACKEND";
            case "learning" -> "GENERAL";
            case "startup"  -> "GENERAL";
            default         -> "GENERAL";
        };
    }

    /**
     * Detects the primary domain from a user input string.
     * Used to seed the conversation context when the user first reveals their topic.
     */
    private String detectDomainFromInput(String input) {
        String lower = input.toLowerCase();
        if (containsAny(lower, "java"))                                    return "JAVA_BACKEND";
        if (containsAny(lower, "spring boot", "spring"))                   return "JAVA_BACKEND";
        if (containsAny(lower, "react", "next.js", "nextjs"))              return "REACT_FRONTEND";
        if (containsAny(lower, "javascript", "typescript"))                return "REACT_FRONTEND";
        if (containsAny(lower, "machine learning", "pytorch", "tensorflow",
                "llm", "ai engineer", "ml engineer"))                      return "AI_ENGINEERING";
        if (containsAny(lower, "kubernetes", "terraform", "devops"))       return "DEVOPS";
        if (containsAny(lower, "system design", "distributed systems"))    return "SYSTEM_DESIGN";
        if (containsAny(lower, "data engineering", "spark", "airflow"))    return "DATA_ENGINEERING";
        if (containsAny(lower, "android", "ios", "flutter", "mobile"))     return "MOBILE_DEVELOPMENT";
        if (containsAny(lower, "aws", "gcp", "azure", "cloud engineer"))   return "CLOUD_ENGINEERING";
        if (containsAny(lower, "security", "cybersecurity"))               return "CYBERSECURITY";
        return "GENERAL";
    }

    /**
     * Returns a friendly clarification response when the prompt is vague
     * and there is no conversation context to resolve from.
     */
    private TaskResponse buildClarificationResponse(String userInput, String mode) {
        String message = "I'd love to build you a personalized roadmap! " +
                "Could you tell me a bit more about what you want to learn?\n\n" +
                "For example:\n" +
                "• \"I know nothing about Java — build me a beginner roadmap\"\n" +
                "• \"I already know Spring Boot — teach me distributed systems\"\n" +
                "• \"I want to learn React from scratch\"\n" +
                "• \"I'm an experienced developer, give me an advanced DevOps roadmap\"";

        Task task = versionedTaskBuilder(userInput, message, mode, "general", "general", "CHAT").build();
        Task saved = taskRepository.save(task);

        return TaskResponse.builder()
                .id(saved.getId())
                .userInput(userInput)
                .intentType("CHAT")
                .category("general")
                .skillLevel("general")
                .mode(mode)
                .aiOutput(message)
                .createdAt(saved.getCreatedAt())
                .message(message)
                .suggestions(List.of(
                        "I know nothing about Java — build me a beginner roadmap",
                        "I already know Spring Boot — teach me distributed systems",
                        "I want to learn React from scratch",
                        "Give me an advanced DevOps roadmap"
                ))
                .summary("Clarification needed to generate a personalized roadmap")
                .build();
    }

    /**
     * Gets the most recent active conversation ID for the current user.
     * Used when we have session context but no explicit conversationId.
     */
    private Long getActiveConversationId(ConversationService.SessionContext sessionCtx) {
        if (sessionCtx == null) return null;
        User user = userService.getCurrentUser();
        if (user == null) return null;
        return conversationService.getConversationsForUser(user)
                .stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .findFirst()
                .map(c -> c.getId())
                .orElse(null);
    }

    /** Maps a pipeline domain string to a category string. */
    private String domainToCategory(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND", "REACT_FRONTEND", "FULL_STACK",
                 "MOBILE_DEVELOPMENT", "DATA_ENGINEERING",
                 "AI_ENGINEERING", "DEVOPS", "SYSTEM_DESIGN",
                 "CLOUD_ENGINEERING", "CYBERSECURITY" -> "coding";
            default -> "general";
        };
    }

    /**
     * Builds a concise conversation summary to inject into the AI prompt.
     *
     * This is the key mechanism that makes the AI generate context-aware roadmaps.
     * Instead of just sending the current prompt, we tell the AI what the user
     * said before — their skill level, domain, and goals.
     *
     * Example output:
     *   "The user said: 'I know absolutely nothing about Java programming.'
     *    Detected skill level: beginner. Domain: Java backend development.
     *    Generate a roadmap that starts from the absolute basics."
     */
    private String buildConversationSummary(Long conversationId,
                                             ConversationService.ResolvedContext resolved,
                                             ConversationService.SessionContext sessionCtx,
                                             String skillLevel,
                                             String sessionDomain) {
        StringBuilder sb = new StringBuilder();

        // Include the last user message from history if we resolved context from it
        if (resolved != null && resolved.hasContext() && resolved.lastUserMessage() != null) {
            String lastMsg = resolved.lastUserMessage();
            if (lastMsg.length() > 200) lastMsg = lastMsg.substring(0, 200) + "...";
            sb.append("The user previously said: \"").append(lastMsg).append("\"\n");
        }

        // Skill level statement
        if ("beginner".equalsIgnoreCase(skillLevel)) {
            sb.append("The user is a COMPLETE BEGINNER with no prior experience. ");
            sb.append("Start from the absolute basics — variables, loops, and fundamentals. ");
            sb.append("Do NOT include advanced topics.\n");
        } else if ("advanced".equalsIgnoreCase(skillLevel)) {
            sb.append("The user is EXPERIENCED and already knows the basics. ");
            sb.append("Skip fundamentals entirely. Focus on advanced, production-grade content.\n");
        }

        // Domain statement
        if (sessionDomain != null && !sessionDomain.equals("GENERAL")) {
            String domainDisplay = switch (sessionDomain) {
                case "JAVA_BACKEND"     -> "Java backend development";
                case "REACT_FRONTEND"   -> "React frontend development";
                case "AI_ENGINEERING"   -> "AI/ML engineering";
                case "DEVOPS"           -> "DevOps and platform engineering";
                case "SYSTEM_DESIGN"    -> "distributed systems and system design";
                case "DATA_ENGINEERING" -> "data engineering";
                default                 -> sessionDomain.toLowerCase().replace("_", " ");
            };
            sb.append("The roadmap topic is: ").append(domainDisplay).append(".\n");
        }

        // Load recent conversation messages for additional context
        if (conversationId != null) {
            try {
                List<com.assistant.model.Message> recentMessages =
                        conversationService.getMessages(conversationId);
                // Take last 3 user messages (not the current one)
                List<String> userMessages = recentMessages.stream()
                        .filter(m -> "user".equals(m.getRole()))
                        .map(com.assistant.model.Message::getContent)
                        .filter(c -> c != null && c.length() > 5)
                        .collect(Collectors.toList());

                if (userMessages.size() > 1) {
                    // There are prior messages — include the most relevant ones
                    int start = Math.max(0, userMessages.size() - 3);
                    List<String> context = userMessages.subList(start, userMessages.size() - 1);
                    if (!context.isEmpty()) {
                        sb.append("Prior conversation messages:\n");
                        context.forEach(msg -> {
                            String truncated = msg.length() > 150 ? msg.substring(0, 150) + "..." : msg;
                            sb.append("  - \"").append(truncated).append("\"\n");
                        });
                    }
                }
            } catch (Exception e) {
                log.debug("[roadmap] Could not load conversation messages for summary: {}", e.getMessage());
            }
        }

        String summary = sb.toString().trim();
        return summary.isEmpty() ? null : summary;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) { if (text.contains(kw)) return true; }
        return false;
    }

    // =========================================================================
    // RAG
    // =========================================================================

    private List<Task> fetchRelevantTasks(String userInput) {
        List<String> keywords = extractKeywords(userInput);
        if (keywords.isEmpty()) return List.of();
        LinkedHashMap<Long, Task> seen = new LinkedHashMap<>();
        for (String keyword : keywords) {
            for (Task t : taskRepository.findTop3ByUserInputContainingIgnoreCaseOrderByCreatedAtDesc(keyword)) {
                seen.putIfAbsent(t.getId(), t);
                if (seen.size() == 3) break;
            }
            if (seen.size() == 3) break;
        }
        return new ArrayList<>(seen.values());
    }

    private List<String> extractKeywords(String userInput) {
        Set<String> stopWords = Set.of("for","with","this","that","from","have","will","about",
                "into","then","than","when","what","which","your","their","there","these",
                "those","should","would","could","make","need","want","just","also","some",
                "more","very","been","were","they","them");
        return Arrays.stream(userInput.split("\\s+"))
                .map(w -> w.replaceAll("[^a-zA-Z0-9]", "").toLowerCase())
                .filter(w -> w.length() > 4)
                .filter(w -> !stopWords.contains(w))
                .distinct().limit(3).collect(Collectors.toList());
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Returns a pre-configured Task.Builder with versioning metadata stamped.
     * All task saves must go through this to ensure consistent versioning.
     */
    private Task.Builder versionedTaskBuilder(String userInput, String aiOutput,
                                               String mode, String category,
                                               String skillLevel, String intentType) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
        
        return Task.builder()
                .user(currentUser)
                .userInput(userInput)
                .aiOutput(aiOutput)
                .mode(mode)
                .category(category)
                .skillLevel(skillLevel)
                .intentType(intentType)
                .modelName(MODEL_NAME)
                .promptVersion(PROMPT_VERSION)
                .schemaVersion(SCHEMA_VERSION);
    }

    private List<String> buildChatSuggestions(String userInput) {
        String lower = userInput.toLowerCase();

        // Identity questions
        if (containsAny(lower, "who are you", "what are you", "what can you do", "introduce yourself")) {
            return List.of(
                "I'm a complete beginner — build me a Java roadmap from scratch",
                "I know Spring Boot — teach me distributed systems",
                "Explain what a REST API is with examples"
            );
        }

        // Beginner signals
        if (containsAny(lower, "beginner", "from scratch", "don't know", "new to", "never", "teach me", "help me learn")) {
            return List.of(
                "Generate a beginner roadmap for me",
                "Explain the first concept I should learn",
                "What should I build as my first project?"
            );
        }

        // Advanced signals
        if (containsAny(lower, "advanced", "distributed", "production", "architecture", "scale")) {
            return List.of(
                "Generate an advanced roadmap for this topic",
                "Explain the key architectural patterns",
                "What are the most common production pitfalls?"
            );
        }

        // Default
        return List.of(
            "Generate a personalized roadmap for this",
            "Explain this concept in more detail",
            "What should I learn next?"
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }

    private String stripCodeFences(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        return s;
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) return text;
        return text.substring(start, end + 1);
    }

    private List<String> parseStringArray(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) return new ArrayList<>();
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText).filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());
    }

    private String toJsonString(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private String normalizeMode(String mode) {
        if (mode == null) return "default";
        return switch (mode.toLowerCase()) {
            case "detailed"   -> "detailed";
            case "simplified" -> "simplified";
            default           -> "default";
        };
    }

    // =========================================================================
    // Internal records
    // =========================================================================

    private record ParsedOutput(String summary, String estimatedTime, String difficulty,
                                 List<String> prerequisites, List<String> steps,
                                 List<String> tips, List<String> mistakesToAvoid, List<String> resources) {}

    private record CodingOutput(String summary, String language, String explanation,
                                 List<TaskResponse.CodeBlock> codeBlocks, List<String> keyPoints,
                                 List<String> commonMistakes, List<String> resources,
                                 List<String> clarificationQuestions) {}

    private record AnalysisOutput(String summary, String verdict,
                                   List<TaskResponse.AnalysisSection> sections,
                                   List<String> pros, List<String> cons,
                                   List<String> recommendations, List<String> useCases) {}

    // =========================================================================
    // Parsing: Learning
    // =========================================================================

    private LearningOutput parseLearningOutput(String rawResponse) {
        try {
            String cleaned = stripCodeFences(rawResponse);
            String json    = extractJsonObject(cleaned);
            JsonNode root  = objectMapper.readTree(json);

            List<TaskResponse.LearningExample> examples = new ArrayList<>();
            JsonNode examplesNode = root.path("examples");
            if (examplesNode.isArray()) {
                for (JsonNode e : examplesNode) {
                    TaskResponse.LearningExample ex = new TaskResponse.LearningExample();
                    ex.setTitle(e.path("title").asText(""));
                    ex.setDescription(e.path("description").asText(""));
                    ex.setCode(e.path("code").asText(""));
                    examples.add(ex);
                }
            }

            return new LearningOutput(
                    root.path("summary").asText("Learning response"),
                    root.path("conceptTitle").asText(""),
                    root.path("explanation").asText(""),
                    parseStringArray(root.path("keyPoints")),
                    examples,
                    parseStringArray(root.path("commonMisconceptions")),
                    parseStringArray(root.path("practiceExercises")),
                    parseStringArray(root.path("bestPractices")),
                    parseStringArray(root.path("resources")),
                    parseStringArray(root.path("nextTopics"))
            );
        } catch (Exception e) {
            log.warn("[parse:learning] Failed: {}", e.getMessage());
            return new LearningOutput(rawResponse, "", rawResponse,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    // =========================================================================
    // Parsing: Productivity
    // =========================================================================

    private ProductivityOutput parseProductivityOutput(String rawResponse) {
        try {
            String cleaned = stripCodeFences(rawResponse);
            String json    = extractJsonObject(cleaned);
            JsonNode root  = objectMapper.readTree(json);

            List<TaskResponse.ScheduleBlock> schedule = new ArrayList<>();
            JsonNode scheduleNode = root.path("schedule");
            if (scheduleNode.isArray()) {
                for (JsonNode s : scheduleNode) {
                    TaskResponse.ScheduleBlock block = new TaskResponse.ScheduleBlock();
                    block.setTimeBlock(s.path("timeBlock").asText(""));
                    block.setActivity(s.path("activity").asText(""));
                    block.setPriority(s.path("priority").asText("Medium"));
                    block.setDuration(s.path("duration").asText(""));
                    schedule.add(block);
                }
            }

            return new ProductivityOutput(
                    root.path("summary").asText("Productivity response"),
                    root.path("systemTitle").asText(""),
                    root.path("overview").asText(""),
                    schedule,
                    parseStringArray(root.path("priorities")),
                    parseStringArray(root.path("habits")),
                    parseStringArray(root.path("tools")),
                    parseStringArray(root.path("tips")),
                    parseStringArray(root.path("commonMistakes")),
                    root.path("weeklyReview").asText("")
            );
        } catch (Exception e) {
            log.warn("[parse:productivity] Failed: {}", e.getMessage());
            return new ProductivityOutput(rawResponse, "", rawResponse,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");
        }
    }

    private record LearningOutput(String summary, String conceptTitle, String explanation,
                                   List<String> keyPoints, List<TaskResponse.LearningExample> examples,
                                   List<String> commonMisconceptions, List<String> practiceExercises,
                                   List<String> bestPractices, List<String> resources, List<String> nextTopics) {}

    private record ProductivityOutput(String summary, String systemTitle, String overview,
                                       List<TaskResponse.ScheduleBlock> schedule, List<String> priorities,
                                       List<String> habits, List<String> tools, List<String> tips,
                                       List<String> commonMistakes, String weeklyReview) {}

    // =========================================================================
    // Deduplication Logic
    // =========================================================================

    /**
     * Checks if the current request is a duplicate of a recent task.
     * Prevents generating the same response multiple times within a short timeframe.
     */
    private boolean isDuplicateRequest(String userInput, Long conversationId) {
        // Skip deduplication for conversation threads (follow-ups are expected)
        if (conversationId != null) {
            return false;
        }

        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return false;
        }

        // Check for exact matches in the last 10 minutes
        List<Task> recentTasks = taskRepository.findTop5ByUserOrderByCreatedAtDesc(currentUser);
        String normalizedInput = normalizeForComparison(userInput);

        for (Task task : recentTasks) {
            // Skip if older than 10 minutes
            if (task.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusMinutes(10))) {
                continue;
            }

            String taskInput = normalizeForComparison(task.getUserInput());
            
            // Exact match
            if (normalizedInput.equals(taskInput)) {
                log.info("[dedup] Exact duplicate found: {}", task.getId());
                return true;
            }

            // High similarity (>90% match)
            if (calculateSimilarity(normalizedInput, taskInput) > 0.9) {
                log.info("[dedup] High similarity duplicate found: {}", task.getId());
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the most recent similar task response.
     */
    private TaskResponse getRecentSimilarTask(String userInput) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }

        List<Task> recentTasks = taskRepository.findTop5ByUserOrderByCreatedAtDesc(currentUser);
        String normalizedInput = normalizeForComparison(userInput);

        for (Task task : recentTasks) {
            String taskInput = normalizeForComparison(task.getUserInput());
            
            if (normalizedInput.equals(taskInput) || 
                calculateSimilarity(normalizedInput, taskInput) > 0.9) {
                return toResponseFromStored(task);
            }
        }

        // Fallback - should not happen if isDuplicateRequest was called first
        throw new RuntimeException("No similar task found");
    }

    /**
     * Normalizes text for comparison by removing extra whitespace, 
     * converting to lowercase, and removing punctuation.
     */
    private String normalizeForComparison(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                   .replaceAll("[^a-z0-9\\s]", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * Calculates similarity between two strings using a simple word-based approach.
     * Returns a value between 0.0 (no similarity) and 1.0 (identical).
     *
     * FIXED: Uses HashSet instead of Set.of() to handle duplicate words in prompts.
     * Set.of() throws IllegalArgumentException on duplicate elements (e.g., "java java").
     */
    private double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        if (text1.equals(text2)) return 1.0;

        // Use HashSet — tolerates duplicate words in natural language prompts
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
