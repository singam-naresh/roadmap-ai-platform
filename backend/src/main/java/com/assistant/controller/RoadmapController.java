package com.assistant.controller;

import com.assistant.dto.RoadmapResponseDto;
import com.assistant.dto.RoadmapStepDto;
import com.assistant.model.RoadmapStep;
import com.assistant.service.RoadmapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roadmaps")
public class RoadmapController {

    private final RoadmapService roadmapService;

    public RoadmapController(RoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    /**
     * GET /api/roadmaps/task/{taskId}
     * Get or create roadmap for a specific task
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<RoadmapResponseDto> getRoadmapByTaskId(@PathVariable Long taskId) {
        RoadmapResponseDto roadmap = roadmapService.getRoadmapByTaskId(taskId);
        return ResponseEntity.ok(roadmap);
    }

    /**
     * GET /api/roadmaps
     * Get all roadmaps for current user
     */
    @GetMapping
    public ResponseEntity<List<RoadmapResponseDto>> getUserRoadmaps() {
        List<RoadmapResponseDto> roadmaps = roadmapService.getUserRoadmaps();
        return ResponseEntity.ok(roadmaps);
    }

    /**
     * GET /api/roadmaps/{roadmapId}/steps
     * Get all steps for a roadmap
     */
    @GetMapping("/{roadmapId}/steps")
    public ResponseEntity<List<RoadmapStepDto>> getRoadmapSteps(@PathVariable Long roadmapId) {
        List<RoadmapStepDto> steps = roadmapService.getRoadmapSteps(roadmapId);
        return ResponseEntity.ok(steps);
    }

    /**
     * POST /api/roadmaps/{roadmapId}/steps/{stepIndex}/toggle
     * Toggle step completion
     */
    @PostMapping("/{roadmapId}/steps/{stepIndex}/toggle")
    public ResponseEntity<RoadmapStepDto> toggleStepCompletion(
            @PathVariable Long roadmapId,
            @PathVariable Integer stepIndex) {
        RoadmapStepDto step = roadmapService.toggleStepCompletion(roadmapId, stepIndex);
        return ResponseEntity.ok(step);
    }

    /**
     * PUT /api/roadmaps/{roadmapId}/steps/{stepIndex}/note
     * Update step note
     */
    @PutMapping("/{roadmapId}/steps/{stepIndex}/note")
    public ResponseEntity<RoadmapStepDto> updateStepNote(
            @PathVariable Long roadmapId,
            @PathVariable Integer stepIndex,
            @RequestBody Map<String, String> request) {
        String note = request.get("note");
        RoadmapStepDto step = roadmapService.updateStepNote(roadmapId, stepIndex, note);
        return ResponseEntity.ok(step);
    }

    /**
     * PUT /api/roadmaps/{roadmapId}/steps/{stepIndex}/priority
     * Update step priority
     */
    @PutMapping("/{roadmapId}/steps/{stepIndex}/priority")
    public ResponseEntity<RoadmapStepDto> updateStepPriority(
            @PathVariable Long roadmapId,
            @PathVariable Integer stepIndex,
            @RequestBody Map<String, String> request) {
        String priorityStr = request.get("priority");
        RoadmapStep.Priority priority = RoadmapStep.Priority.valueOf(priorityStr.toUpperCase());
        RoadmapStepDto step = roadmapService.updateStepPriority(roadmapId, stepIndex, priority);
        return ResponseEntity.ok(step);
    }

    /**
     * GET /api/roadmaps/{roadmapId}/progress
     * Get roadmap progress statistics
     */
    @GetMapping("/{roadmapId}/progress")
    public ResponseEntity<RoadmapService.RoadmapProgress> getRoadmapProgress(@PathVariable Long roadmapId) {
        RoadmapService.RoadmapProgress progress = roadmapService.getRoadmapProgress(roadmapId);
        return ResponseEntity.ok(progress);
    }

    /**
     * GET /api/roadmaps/{roadmapId}/export?format=markdown|json
     * Export roadmap in the requested format.
     */
    @GetMapping("/{roadmapId}/export")
    public ResponseEntity<String> exportRoadmap(
            @PathVariable Long roadmapId,
            @RequestParam(defaultValue = "markdown") String format) {

        com.assistant.dto.RoadmapResponseDto roadmap = roadmapService.getRoadmapById(roadmapId);
        java.util.List<com.assistant.dto.RoadmapStepDto> steps = roadmapService.getRoadmapSteps(roadmapId);

        String content;
        String contentType;
        String filename;

        if ("json".equalsIgnoreCase(format)) {
            content = roadmapService.exportAsJson(roadmap, steps);
            contentType = "application/json";
            filename = "roadmap-" + roadmapId + ".json";
        } else {
            content = roadmapService.exportAsMarkdown(roadmap, steps);
            contentType = "text/markdown";
            filename = "roadmap-" + roadmapId + ".md";
        }

        return ResponseEntity.ok()
                .header("Content-Type", contentType + "; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}