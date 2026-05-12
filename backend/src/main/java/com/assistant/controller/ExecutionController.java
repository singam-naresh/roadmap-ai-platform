package com.assistant.controller;

import com.assistant.model.StepProgress;
import com.assistant.service.*;
import com.assistant.service.ExecutionStateEngine.StepExecutionState;
import com.assistant.service.ExecutionStateEngine.ExecutionSummary;
import com.assistant.service.TaskDependencyGraphEngine.DependencyGraph;
import com.assistant.service.AdaptiveRoadmapEngine.AdaptationReport;
import com.assistant.service.PriorityAdaptationEngine.PrioritizedStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Execution tracking API — Phase 4 extended.
 * Persists step completion, priorities, notes, and exposes
 * execution intelligence (state machine, dependency graph,
 * adaptive roadmap, priority scoring).
 */
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private final ExecutionService          executionService;
    private final ExecutionStateEngine      stateEngine;
    private final TaskDependencyGraphEngine dependencyEngine;
    private final AdaptiveRoadmapEngine     adaptiveEngine;
    private final PriorityAdaptationEngine  priorityEngine;
    private final ExecutionEventBus         eventBus;

    public ExecutionController(ExecutionService executionService,
                               ExecutionStateEngine stateEngine,
                               TaskDependencyGraphEngine dependencyEngine,
                               AdaptiveRoadmapEngine adaptiveEngine,
                               PriorityAdaptationEngine priorityEngine,
                               ExecutionEventBus eventBus) {
        this.executionService  = executionService;
        this.stateEngine       = stateEngine;
        this.dependencyEngine  = dependencyEngine;
        this.adaptiveEngine    = adaptiveEngine;
        this.priorityEngine    = priorityEngine;
        this.eventBus          = eventBus;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy endpoints (unchanged — backward compatible)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/{taskId}")
    public ResponseEntity<Map<Integer, StepProgress>> getProgress(@PathVariable Long taskId) {
        return ResponseEntity.ok(executionService.getProgress(taskId));
    }

    @PostMapping("/{taskId}/steps/{stepIndex}/complete")
    public ResponseEntity<Map<String, Object>> markComplete(
            @PathVariable Long taskId,
            @PathVariable int stepIndex,
            @RequestBody Map<String, Integer> body) {
        int totalSteps = body.getOrDefault("totalSteps", 1);
        int progress   = executionService.markComplete(taskId, stepIndex, totalSteps);

        // Also drive the state machine
        stateEngine.markComplete(taskId, stepIndex);

        // Compute unlocked steps
        List<Integer> unlocked = dependencyEngine.getUnlockedByCompletion(taskId, stepIndex, totalSteps);

        return ResponseEntity.ok(Map.of(
                "progress",  progress,
                "stepIndex", stepIndex,
                "completed", true,
                "unlockedSteps", unlocked
        ));
    }

    @DeleteMapping("/{taskId}/steps/{stepIndex}/complete")
    public ResponseEntity<Map<String, Object>> markIncomplete(
            @PathVariable Long taskId,
            @PathVariable int stepIndex,
            @RequestParam(defaultValue = "1") int totalSteps) {
        int progress = executionService.markIncomplete(taskId, stepIndex, totalSteps);
        stateEngine.markIncomplete(taskId, stepIndex);
        return ResponseEntity.ok(Map.of("progress", progress, "stepIndex", stepIndex, "completed", false));
    }

    @PutMapping("/{taskId}/steps/{stepIndex}/priority")
    public ResponseEntity<Void> setPriority(
            @PathVariable Long taskId,
            @PathVariable int stepIndex,
            @RequestBody Map<String, String> body) {
        executionService.setPriority(taskId, stepIndex, body.getOrDefault("priority", "MEDIUM"));
        eventBus.publish(ExecutionEventBus.EventType.PRIORITY_CHANGED, taskId, stepIndex,
                "Priority set to " + body.getOrDefault("priority", "MEDIUM"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{taskId}/steps/{stepIndex}/note")
    public ResponseEntity<Void> setNote(
            @PathVariable Long taskId,
            @PathVariable int stepIndex,
            @RequestBody Map<String, String> body) {
        executionService.setNote(taskId, stepIndex, body.getOrDefault("note", ""));
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4: State machine endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/executions/{taskId}/state — full state for all steps */
    @GetMapping("/{taskId}/state")
    public ResponseEntity<List<StepExecutionState>> getAllStepStates(@PathVariable Long taskId) {
        return ResponseEntity.ok(stateEngine.getAllStepStates(taskId));
    }

    /** GET /api/executions/{taskId}/steps/{stepIndex}/state */
    @GetMapping("/{taskId}/steps/{stepIndex}/state")
    public ResponseEntity<StepExecutionState> getStepState(
            @PathVariable Long taskId, @PathVariable int stepIndex) {
        return ResponseEntity.ok(stateEngine.getStepState(taskId, stepIndex));
    }

    /** POST /api/executions/{taskId}/steps/{stepIndex}/state — transition state */
    @PostMapping("/{taskId}/steps/{stepIndex}/state")
    public ResponseEntity<StepExecutionState> transitionState(
            @PathVariable Long taskId,
            @PathVariable int stepIndex,
            @RequestBody Map<String, String> body) {
        String stateStr = body.getOrDefault("state", "IN_PROGRESS");
        String notes    = body.get("notes");
        String blocker  = body.get("blocker");
        ExecutionStateEngine.StepState newState = ExecutionStateEngine.StepState.valueOf(stateStr.toUpperCase());
        return ResponseEntity.ok(stateEngine.transitionState(taskId, stepIndex, newState, notes, blocker));
    }

    /** GET /api/executions/{taskId}/summary?totalSteps=N */
    @GetMapping("/{taskId}/summary")
    public ResponseEntity<ExecutionSummary> getExecutionSummary(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int totalSteps) {
        return ResponseEntity.ok(stateEngine.getExecutionSummary(taskId, totalSteps));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4: Dependency graph endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/executions/{taskId}/dependencies?totalSteps=N */
    @GetMapping("/{taskId}/dependencies")
    public ResponseEntity<DependencyGraph> getDependencyGraph(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int totalSteps) {
        return ResponseEntity.ok(dependencyEngine.buildGraph(taskId, totalSteps));
    }

    /** GET /api/executions/{taskId}/ready?totalSteps=N — steps ready to start */
    @GetMapping("/{taskId}/ready")
    public ResponseEntity<Map<String, Object>> getReadySteps(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int totalSteps) {
        List<Integer> ready   = dependencyEngine.getReadySteps(taskId, totalSteps);
        List<Integer> blocked = dependencyEngine.getBlockedSteps(taskId, totalSteps);
        return ResponseEntity.ok(Map.of("readySteps", ready, "blockedSteps", blocked));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4: Adaptive roadmap endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/executions/{taskId}/adapt?roadmapId=N&totalSteps=N */
    @GetMapping("/{taskId}/adapt")
    public ResponseEntity<AdaptationReport> getAdaptationReport(
            @PathVariable Long taskId,
            @RequestParam Long roadmapId,
            @RequestParam(defaultValue = "0") int totalSteps) {
        return ResponseEntity.ok(adaptiveEngine.analyzeAndAdapt(roadmapId, taskId, totalSteps));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4: Priority scoring endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/executions/{taskId}/priority?roadmapId=N */
    @GetMapping("/{taskId}/priority")
    public ResponseEntity<List<PrioritizedStep>> getPrioritizedSteps(
            @PathVariable Long taskId,
            @RequestParam Long roadmapId) {
        return ResponseEntity.ok(priorityEngine.computePriority(roadmapId, taskId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4: Event log
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/executions/{taskId}/events */
    @GetMapping("/{taskId}/events")
    public ResponseEntity<List<ExecutionEventBus.ExecutionEvent>> getEvents(@PathVariable Long taskId) {
        return ResponseEntity.ok(eventBus.getRecentEvents(taskId));
    }
}
