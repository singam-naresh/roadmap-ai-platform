package com.assistant.controller;

import com.assistant.service.*;
import com.assistant.service.ExecutionAuditService.AuditRecord;
import com.assistant.service.ExecutionEventInspector.InspectionReport;
import com.assistant.service.ExecutionFailureAnalyzer.FailureDiagnosticReport;
import com.assistant.service.ExecutionMetricsEngine.ExecutionMetrics;
import com.assistant.service.ExecutionReplayEngine.PlaybackFrame;
import com.assistant.service.ExecutionReplayEngine.ReplaySnapshot;
import com.assistant.service.ExecutionTimelineEngine.TimelineEntry;
import com.assistant.service.DependencyVisualizationService.GraphVisualization;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PHASE 4.5 — Execution Observability Controller
 *
 * Exposes all observability, debugging, and replay endpoints:
 *
 *   GET /api/execution/{taskId}/timeline
 *   GET /api/execution/{taskId}/audit
 *   GET /api/execution/{taskId}/replay
 *   GET /api/execution/{taskId}/graph
 *   GET /api/execution/{taskId}/metrics
 *   GET /api/execution/{taskId}/failures
 *   GET /api/execution/{taskId}/inspect
 *   GET /api/execution/{taskId}/playback
 */
@RestController
@RequestMapping("/api/execution")
public class ExecutionObservabilityController {

    private final ExecutionTimelineEngine       timelineEngine;
    private final ExecutionAuditService         auditService;
    private final ExecutionReplayEngine         replayEngine;
    private final DependencyVisualizationService vizService;
    private final ExecutionMetricsEngine        metricsEngine;
    private final ExecutionFailureAnalyzer      failureAnalyzer;
    private final ExecutionEventInspector       eventInspector;

    public ExecutionObservabilityController(
            ExecutionTimelineEngine timelineEngine,
            ExecutionAuditService auditService,
            ExecutionReplayEngine replayEngine,
            DependencyVisualizationService vizService,
            ExecutionMetricsEngine metricsEngine,
            ExecutionFailureAnalyzer failureAnalyzer,
            ExecutionEventInspector eventInspector) {
        this.timelineEngine  = timelineEngine;
        this.auditService    = auditService;
        this.replayEngine    = replayEngine;
        this.vizService      = vizService;
        this.metricsEngine   = metricsEngine;
        this.failureAnalyzer = failureAnalyzer;
        this.eventInspector  = eventInspector;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timeline
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/execution/{taskId}/timeline
     * Full chronological timeline for a task.
     */
    @GetMapping("/{taskId}/timeline")
    public ResponseEntity<List<TimelineEntry>> getTimeline(@PathVariable Long taskId) {
        return ResponseEntity.ok(timelineEngine.getTimeline(taskId));
    }

    /**
     * GET /api/execution/{taskId}/timeline/step/{stepIndex}
     * Timeline filtered to a specific step.
     */
    @GetMapping("/{taskId}/timeline/step/{stepIndex}")
    public ResponseEntity<List<TimelineEntry>> getStepTimeline(
            @PathVariable Long taskId, @PathVariable int stepIndex) {
        return ResponseEntity.ok(timelineEngine.getTimelineForStep(taskId, stepIndex));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audit log
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/execution/{taskId}/audit
     * Full immutable audit log for a task.
     */
    @GetMapping("/{taskId}/audit")
    public ResponseEntity<List<AuditRecord>> getAuditLog(@PathVariable Long taskId) {
        return ResponseEntity.ok(auditService.getAuditLog(taskId));
    }

    /**
     * GET /api/execution/{taskId}/audit/since/{sequence}
     * Incremental audit log since a sequence number.
     */
    @GetMapping("/{taskId}/audit/since/{sequence}")
    public ResponseEntity<List<AuditRecord>> getAuditLogSince(
            @PathVariable Long taskId, @PathVariable long sequence) {
        return ResponseEntity.ok(auditService.getRecordsSince(taskId, sequence));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/execution/{taskId}/replay?at=2024-01-15T10:30:00
     * Point-in-time state reconstruction.
     */
    @GetMapping("/{taskId}/replay")
    public ResponseEntity<ReplaySnapshot> replayAtTime(
            @PathVariable Long taskId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        LocalDateTime target = at != null ? at : LocalDateTime.now();
        return ResponseEntity.ok(replayEngine.replayAtTime(taskId, target));
    }

    /**
     * GET /api/execution/{taskId}/replay/sequence/{seq}
     * Replay up to a specific audit sequence number.
     */
    @GetMapping("/{taskId}/replay/sequence/{seq}")
    public ResponseEntity<ReplaySnapshot> replayAtSequence(
            @PathVariable Long taskId, @PathVariable long seq) {
        return ResponseEntity.ok(replayEngine.replayAtSequence(taskId, seq));
    }

    /**
     * GET /api/execution/{taskId}/playback
     * Full step-by-step playback sequence for visualization.
     */
    @GetMapping("/{taskId}/playback")
    public ResponseEntity<List<PlaybackFrame>> getPlayback(@PathVariable Long taskId) {
        return ResponseEntity.ok(replayEngine.getPlaybackSequence(taskId));
    }

    /**
     * GET /api/execution/{taskId}/mutations
     * Mutation history for roadmap evolution diff.
     */
    @GetMapping("/{taskId}/mutations")
    public ResponseEntity<List<ExecutionReplayEngine.MutationRecord>> getMutationHistory(
            @PathVariable Long taskId) {
        return ResponseEntity.ok(replayEngine.getMutationHistory(taskId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dependency graph visualization
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/execution/{taskId}/graph?totalSteps=N
     * Frontend-ready dependency graph JSON.
     */
    @GetMapping("/{taskId}/graph")
    public ResponseEntity<GraphVisualization> getDependencyGraph(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int totalSteps,
            @RequestParam(required = false) List<String> stepTitles) {
        return ResponseEntity.ok(vizService.buildVisualization(taskId, totalSteps, stepTitles));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/execution/{taskId}/metrics?totalSteps=N
     * Full execution analytics.
     */
    @GetMapping("/{taskId}/metrics")
    public ResponseEntity<ExecutionMetrics> getMetrics(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int totalSteps) {
        return ResponseEntity.ok(metricsEngine.computeMetrics(taskId, totalSteps));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Failure diagnostics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/execution/{taskId}/failures?totalSteps=N
     * Root-cause failure analysis with debugging suggestions.
     */
    @GetMapping("/{taskId}/failures")
    public ResponseEntity<FailureDiagnosticReport> getFailureDiagnostics(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int totalSteps) {
        return ResponseEntity.ok(failureAnalyzer.analyze(taskId, totalSteps));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event inspection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/execution/{taskId}/inspect
     * Deep event stream inspection — storms, loops, invalid transitions.
     */
    @GetMapping("/{taskId}/inspect")
    public ResponseEntity<InspectionReport> inspectEvents(@PathVariable Long taskId) {
        return ResponseEntity.ok(eventInspector.inspect(taskId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Combined dashboard snapshot
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/execution/{taskId}/dashboard?totalSteps=N
     * Single endpoint returning all observability data for the debug panel.
     */
    @GetMapping("/{taskId}/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int totalSteps) {

        return ResponseEntity.ok(Map.of(
                "timeline",   timelineEngine.getTimeline(taskId),
                "audit",      auditService.getAuditLog(taskId),
                "metrics",    metricsEngine.computeMetrics(taskId, totalSteps),
                "failures",   failureAnalyzer.analyze(taskId, totalSteps),
                "inspection", eventInspector.inspect(taskId),
                "graph",      vizService.buildVisualization(taskId, totalSteps, null),
                "mutations",  replayEngine.getMutationHistory(taskId)
        ));
    }
}
