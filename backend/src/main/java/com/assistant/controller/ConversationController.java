package com.assistant.controller;

import com.assistant.dto.TaskRequest;
import com.assistant.dto.TaskResponse;
import com.assistant.model.Conversation;
import com.assistant.model.Message;
import com.assistant.model.User;
import com.assistant.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PHASE 6 — Conversation Controller
 *
 * REST endpoints for the persistent conversation workspace:
 *   GET  /api/conversations              — list user's conversations
 *   GET  /api/conversations/{id}/messages — get messages in a thread
 *   POST /api/conversations/{id}/continue — continue a roadmap conversation
 *   POST /api/conversations/{id}/roadmap/{roadmapId}/action — step-level actions
 *   POST /api/feasibility               — check goal feasibility before generation
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService        conversationService;
    private final TaskService                taskService;
    private final GoalFeasibilityEngine      feasibilityEngine;
    private final RoadmapContinuationService continuationService;
    private final UserService                userService;

    public ConversationController(ConversationService conversationService,
                                   TaskService taskService,
                                   GoalFeasibilityEngine feasibilityEngine,
                                   RoadmapContinuationService continuationService,
                                   UserService userService) {
        this.conversationService  = conversationService;
        this.taskService          = taskService;
        this.feasibilityEngine    = feasibilityEngine;
        this.continuationService  = continuationService;
        this.userService          = userService;
    }

    /**
     * GET /api/conversations
     * Returns all conversations for the current user, newest first.
     */
    @GetMapping
    public ResponseEntity<List<Conversation>> getConversations() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(conversationService.getConversationsForUser(user));
    }

    /**
     * GET /api/conversations/{id}/messages
     * Returns all messages in a conversation thread.
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Message>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.getMessages(id));
    }

    /**
     * POST /api/conversations/{id}/continue
     * Continues an existing conversation with a new message.
     * Body: { "userInput": "...", "mode": "default" }
     */
    @PostMapping("/{id}/continue")
    public ResponseEntity<TaskResponse> continueConversation(
            @PathVariable Long id,
            @RequestBody TaskRequest request) {
        // Inject the conversation ID so TaskService threads it correctly
        // and restores session context (skill level, intent, domain)
        request.setConversationId(id);
        TaskResponse response = taskService.processTask(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/conversations/{convId}/roadmap/{roadmapId}/action
     * Performs a continuation action on an active roadmap (with conversation context).
     * Body: { "userInput": "expand step 3" }
     */
    @PostMapping("/{convId}/roadmap/{roadmapId}/action")
    public ResponseEntity<RoadmapContinuationService.ContinuationResponse> roadmapAction(
            @PathVariable Long convId,
            @PathVariable Long roadmapId,
            @RequestBody Map<String, String> body) {
        String userInput = body.getOrDefault("userInput", "");
        RoadmapContinuationService.ContinuationResponse response =
                continuationService.handleContinuation(roadmapId, userInput, convId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/roadmap-actions/{taskId}
     * Direct roadmap action by task ID — no conversation context required.
     * Used by the step action menu in the roadmap renderer.
     * Body: { "userInput": "explain the concept in step 3" }
     */
    @PostMapping("/roadmap-actions/{taskId}")
    public ResponseEntity<RoadmapContinuationService.ContinuationResponse> directRoadmapAction(
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        String userInput = body.getOrDefault("userInput", "");
        // Find the roadmap for this task, or use taskId as roadmapId fallback
        Long roadmapId = taskId; // RoadmapContinuationService handles missing roadmap gracefully
        RoadmapContinuationService.ContinuationResponse response =
                continuationService.handleContinuation(roadmapId, userInput, null);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/feasibility
     * Checks whether a goal is realistic before generation.
     * Body: { "userInput": "Become expert AI engineer in 2 weeks" }
     */
    @PostMapping("/feasibility")
    public ResponseEntity<GoalFeasibilityEngine.FeasibilityResult> checkFeasibility(
            @RequestBody Map<String, String> body) {
        String userInput = body.getOrDefault("userInput", "");
        GoalFeasibilityEngine.FeasibilityResult result = feasibilityEngine.assess(userInput);
        return ResponseEntity.ok(result);
    }
}
