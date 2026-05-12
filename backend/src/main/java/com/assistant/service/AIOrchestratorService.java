package com.assistant.service;

import com.assistant.dto.TaskRequest;
import com.assistant.dto.TaskResponse;
import com.assistant.model.Task;
import com.assistant.model.User;
import com.assistant.repository.TaskRepository;
import com.assistant.service.AIIntentClassifier.AIIntentType;
import com.assistant.service.GroqClient.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central orchestration service for all AI interactions.
 * Handles intent classification, context injection, memory management,
 * refinement modes, and routing to specialized generation services.
 */
@Service
public class AIOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestratorService.class);
    
    // Track token usage and rate limiting
    private final Map<String, TokenUsageTracker> userTokenUsage = new ConcurrentHashMap<>();
    
    private final AIIntentClassifier intentClassifier;
    private final ConversationService conversationService;
    private final MemoryService memoryService;
    private final UserService userService;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    
    // Generation services
    private final ChatGenerationService chatService;
    private final CodingGenerationService codingService;
    private final AnalysisGenerationService analysisService;
    private final LearningGenerationService learningService;
    private final ProductivityGenerationService productivityService;
    private final RoadmapService roadmapService;

    public AIOrchestratorService(AIIntentClassifier intentClassifier,
                                ConversationService conversationService,
                                MemoryService memoryService,
                                UserService userService,
                                TaskRepository taskRepository,
                                ObjectMapper objectMapper,
                                ChatGenerationService chatService,
                                CodingGenerationService codingService,
                                AnalysisGenerationService analysisService,
                                LearningGenerationService learningService,
                                ProductivityGenerationService productivityService,
                                RoadmapService roadmapService) {
        this.intentClassifier = intentClassifier;
        this.conversationService = conversationService;
        this.memoryService = memoryService;
        this.userService = userService;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.codingService = codingService;
        this.analysisService = analysisService;
        this.learningService = learningService;
        this.productivityService = productivityService;
        this.roadmapService = roadmapService;
    }

    /**
     * Main orchestration method - handles all AI requests
     */
    public TaskResponse orchestrateAIRequest(TaskRequest request) {
        String userInput = request.getUserInput().trim();
        String mode = normalizeMode(request.getMode());
        Long conversationId = request.getConversationId();
        
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }

        // Track token usage
        TokenUsageTracker tracker = getUserTokenTracker(currentUser.getEmail());
        if (tracker.isRateLimited()) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        try {
            // Step 1: Intent classification with context
            AIIntentType intent = classifyIntentWithContext(userInput, currentUser);
            
            // Step 2: Build enhanced context
            AIContext context = buildAIContext(userInput, mode, conversationId, currentUser, intent);
            
            // Step 3: Route to appropriate generation service
            TaskResponse response = routeToGenerationService(intent, context);
            
            // Step 4: Post-process and enhance response
            response = enhanceResponse(response, context);
            
            // Step 5: Update memory and conversation
            updateMemoryAndConversation(response, context);
            
            // Step 6: Track token usage
            tracker.recordRequest();
            
            log.info("[orchestrator] Successfully processed {} request for user {}", 
                    intent, currentUser.getEmail());
            
            return response;
            
        } catch (Exception e) {
            log.error("[orchestrator] Failed to process request for user {}: {}", 
                    currentUser.getEmail(), e.getMessage());
            throw new RuntimeException("AI processing failed: " + e.getMessage());
        }
    }

    /**
     * Enhanced intent classification using user context
     */
    private AIIntentType classifyIntentWithContext(String userInput, User user) {
        AIIntentType baseIntent = intentClassifier.classify(userInput);
        
        // Enhance classification with user history
        List<Task> recentTasks = taskRepository.findByUserOrderByCreatedAtDesc(user)
                .stream().limit(5).toList();
        
        // If user frequently creates roadmaps and asks follow-up questions,
        // bias towards roadmap refinement
        if (conversationService.isFollowUp(userInput) && 
            recentTasks.stream().anyMatch(t -> "ROADMAP".equals(t.getIntentType()))) {
            
            if (userInput.toLowerCase().contains("shorten") || 
                userInput.toLowerCase().contains("simplify") ||
                userInput.toLowerCase().contains("detail")) {
                return AIIntentType.ROADMAP; // Treat as roadmap refinement
            }
        }
        
        return baseIntent;
    }

    /**
     * Build comprehensive AI context
     */
    private AIContext buildAIContext(String userInput, String mode, Long conversationId, 
                                   User user, AIIntentType intent) {
        return AIContext.builder()
                .userInput(userInput)
                .mode(mode)
                .conversationId(conversationId)
                .user(user)
                .intent(intent)
                .userMemory(null) // memoryService.getUserMemory(user) - TODO: implement
                .conversationHistory(getConversationHistory(conversationId))
                .recentTasks(getRecentUserTasks(user, 5))
                .build();
    }

    /**
     * Route to appropriate generation service based on intent
     */
    private TaskResponse routeToGenerationService(AIIntentType intent, AIContext context) {
        // For now, delegate to existing services without context
        // TODO: Enhance each service to accept AIContext
        
        return switch (intent) {
            case CHAT -> {
                String response = chatService.generate(context.getUserInput());
                yield createTaskResponse(response, context, "CHAT");
            }
            case CODING -> {
                String response = codingService.generate(context.getUserInput());
                yield createTaskResponse(response, context, "CODING");
            }
            case ANALYSIS -> {
                String response = analysisService.generate(context.getUserInput());
                yield createTaskResponse(response, context, "ANALYSIS");
            }
            case LEARNING -> {
                String response = learningService.generate(context.getUserInput());
                yield createTaskResponse(response, context, "LEARNING");
            }
            case PRODUCTIVITY -> {
                String response = productivityService.generate(context.getUserInput());
                yield createTaskResponse(response, context, "PRODUCTIVITY");
            }
            case ROADMAP, STARTUP -> {
                // Delegate to TaskService for now
                throw new RuntimeException("Roadmap generation should use TaskService directly");
            }
        };
    }

    private TaskResponse createTaskResponse(String aiOutput, AIContext context, String intentType) {
        TaskResponse response = new TaskResponse();
        response.setUserInput(context.getUserInput());
        response.setAiOutput(aiOutput);
        response.setIntentType(intentType);
        response.setMode(context.getMode());
        response.setConversationId(context.getConversationId());
        return response;
    }

    /**
     * Enhanced roadmap generation with full context
     */
    private TaskResponse generateRoadmapWithContext(AIContext context) {
        // This would integrate with the existing TaskService roadmap logic
        // but with enhanced context awareness
        
        // For now, delegate to TaskService but with enhanced context
        TaskRequest request = new TaskRequest();
        request.setUserInput(context.getUserInput());
        request.setMode(context.getMode());
        request.setConversationId(context.getConversationId());
        
        // TODO: Integrate this properly with TaskService
        throw new RuntimeException("Roadmap generation with context not yet implemented");
    }

    /**
     * Enhance response with additional context and metadata
     */
    private TaskResponse enhanceResponse(TaskResponse response, AIContext context) {
        // Add conversation ID if missing
        if (response.getConversationId() == null && context.getConversationId() != null) {
            response.setConversationId(context.getConversationId());
        }
        
        // Add refinement suggestions based on mode
        if (context.getMode().equals("default")) {
            // Suggest trying detailed or simplified modes
        }
        
        return response;
    }

    /**
     * Update memory and conversation after successful generation
     */
    private void updateMemoryAndConversation(TaskResponse response, AIContext context) {
        // Update user memory with new patterns
        // memoryService.updateUserMemory(context.getUser(), response); - TODO: implement
        
        // Update conversation if applicable
        if (context.getConversationId() != null) {
            conversationService.appendAssistantMessage(
                    context.getConversationId(), 
                    response.getAiOutput(), 
                    response.getIntentType(), 
                    response.getId()
            );
        }
    }

    // Helper methods
    private String normalizeMode(String mode) {
        if (mode == null) return "default";
        return switch (mode.toLowerCase()) {
            case "detailed", "detail" -> "detailed";
            case "simplified", "simple", "basic" -> "simplified";
            case "balanced" -> "balanced";
            default -> "default";
        };
    }

    private TokenUsageTracker getUserTokenTracker(String userEmail) {
        return userTokenUsage.computeIfAbsent(userEmail, k -> new TokenUsageTracker());
    }

    private List<ChatMessage> getConversationHistory(Long conversationId) {
        if (conversationId == null) return List.of();
        return conversationService.buildContextualMessages(conversationId, "", "");
    }

    private List<Task> getRecentUserTasks(User user, int limit) {
        return taskRepository.findByUserOrderByCreatedAtDesc(user)
                .stream().limit(limit).toList();
    }

    /**
     * AI Context container
     */
    public static class AIContext {
        private String userInput;
        private String mode;
        private Long conversationId;
        private User user;
        private AIIntentType intent;
        private Object userMemory;
        private List<ChatMessage> conversationHistory;
        private List<Task> recentTasks;

        // Builder pattern
        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private AIContext context = new AIContext();
            
            public Builder userInput(String userInput) { context.userInput = userInput; return this; }
            public Builder mode(String mode) { context.mode = mode; return this; }
            public Builder conversationId(Long conversationId) { context.conversationId = conversationId; return this; }
            public Builder user(User user) { context.user = user; return this; }
            public Builder intent(AIIntentType intent) { context.intent = intent; return this; }
            public Builder userMemory(Object userMemory) { context.userMemory = userMemory; return this; }
            public Builder conversationHistory(List<ChatMessage> history) { context.conversationHistory = history; return this; }
            public Builder recentTasks(List<Task> tasks) { context.recentTasks = tasks; return this; }
            
            public AIContext build() { return context; }
        }

        // Getters
        public String getUserInput() { return userInput; }
        public String getMode() { return mode; }
        public Long getConversationId() { return conversationId; }
        public User getUser() { return user; }
        public AIIntentType getIntent() { return intent; }
        public Object getUserMemory() { return userMemory; }
        public List<ChatMessage> getConversationHistory() { return conversationHistory; }
        public List<Task> getRecentTasks() { return recentTasks; }
    }

    /**
     * Token usage tracking for rate limiting
     */
    private static class TokenUsageTracker {
        private int requestCount = 0;
        private long lastReset = System.currentTimeMillis();
        private static final int MAX_REQUESTS_PER_HOUR = 100;
        private static final long HOUR_IN_MS = 60 * 60 * 1000;

        public boolean isRateLimited() {
            resetIfNeeded();
            return requestCount >= MAX_REQUESTS_PER_HOUR;
        }

        public void recordRequest() {
            resetIfNeeded();
            requestCount++;
        }

        private void resetIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - lastReset > HOUR_IN_MS) {
                requestCount = 0;
                lastReset = now;
            }
        }
    }
}