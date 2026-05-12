package com.assistant.service;

import com.assistant.model.StepProgress;
import com.assistant.repository.StepProgressRepository;
import com.assistant.service.ExecutionEventBus.EventType;
import com.assistant.service.ExecutionEventBus.ExecutionEvent;
import com.assistant.service.ExecutionTimelineEngine.EntryType;
import com.assistant.service.ExecutionTimelineEngine.TimelineEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4.5 — Execution Failure Analyzer
 *
 * Analyzes execution failures and produces root-cause reports:
 *   - Repeated failures on specific steps
 *   - Stuck roadmaps (no progress for N days)
 *   - Cyclic dependencies (A→B→A)
 *   - Invalid state transitions
 *   - Excessive mutations (roadmap instability)
 *   - Event-loop risks
 *
 * Generates actionable debugging suggestions for each failure type.
 */
@Service
public class ExecutionFailureAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionFailureAnalyzer.class);

    private static final int    STUCK_DAYS          = 5;
    private static final int    FAILURE_THRESHOLD   = 3;
    private static final int    MUTATION_THRESHOLD  = 10;
    private static final int    LOOP_RISK_THRESHOLD = 5;

    private final StepProgressRepository  stepProgressRepository;
    private final ExecutionEventBus       eventBus;
    private final ExecutionTimelineEngine timelineEngine;

    public ExecutionFailureAnalyzer(StepProgressRepository stepProgressRepository,
                                    ExecutionEventBus eventBus,
                                    ExecutionTimelineEngine timelineEngine) {
        this.stepProgressRepository = stepProgressRepository;
        this.eventBus               = eventBus;
        this.timelineEngine         = timelineEngine;
    }

    public FailureDiagnosticReport analyze(Long taskId, int totalSteps) {
        List<StepProgress>   progress = stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId);
        List<ExecutionEvent> events   = eventBus.getRecentEvents(taskId);
        List<TimelineEntry>  timeline = timelineEngine.getTimeline(taskId);

        FailureDiagnosticReport report = new FailureDiagnosticReport(taskId);

        // ── Repeated failures ─────────────────────────────────────────────────
        analyzeRepeatedFailures(events, report);

        // ── Stuck roadmap ─────────────────────────────────────────────────────
        analyzeStuckRoadmap(progress, report);

        // ── Cyclic dependencies ───────────────────────────────────────────────
        analyzeCyclicDependencies(totalSteps, report);

        // ── Invalid transitions ───────────────────────────────────────────────
        analyzeInvalidTransitions(timeline, report);

        // ── Excessive mutations ───────────────────────────────────────────────
        analyzeExcessiveMutations(timeline, report);

        // ── Event-loop risk ───────────────────────────────────────────────────
        analyzeEventLoopRisk(events, report);

        // ── Overall health ────────────────────────────────────────────────────
        report.healthScore = computeHealthScore(report);
        report.overallStatus = report.healthScore > 0.7 ? "HEALTHY"
                : report.healthScore > 0.4 ? "DEGRADED" : "CRITICAL";

        log.info("[failure-analyzer] task={} health={:.2f} status={} issues={}",
                taskId, report.healthScore, report.overallStatus, report.issues.size());
        return report;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Analysis methods
    // ─────────────────────────────────────────────────────────────────────────

    private void analyzeRepeatedFailures(List<ExecutionEvent> events, FailureDiagnosticReport report) {
        Map<Integer, Long> failureCounts = events.stream()
                .filter(e -> e.type == EventType.EXECUTION_FAILED)
                .collect(Collectors.groupingBy(e -> e.stepIndex, Collectors.counting()));

        for (Map.Entry<Integer, Long> entry : failureCounts.entrySet()) {
            if (entry.getValue() >= FAILURE_THRESHOLD) {
                int stepIdx = entry.getKey();
                int count   = entry.getValue().intValue();
                report.issues.add(new FailureIssue(
                        IssueType.REPEATED_FAILURE,
                        "Step " + stepIdx + " has failed " + count + " times",
                        List.of(
                                "Review the requirements for step " + stepIdx,
                                "Break step " + stepIdx + " into smaller sub-tasks",
                                "Check if prerequisites for step " + stepIdx + " are truly complete",
                                "Consider injecting a reinforcement step before step " + stepIdx
                        ),
                        Severity.HIGH
                ));
            }
        }
    }

    private void analyzeStuckRoadmap(List<StepProgress> progress, FailureDiagnosticReport report) {
        if (progress.isEmpty()) return;

        LocalDateTime threshold = LocalDateTime.now().minusDays(STUCK_DAYS);
        boolean allOld = progress.stream()
                .filter(p -> p.getUpdatedAt() != null)
                .allMatch(p -> p.getUpdatedAt().isBefore(threshold));

        boolean anyIncomplete = progress.stream().anyMatch(p -> !p.isCompleted());

        if (allOld && anyIncomplete) {
            report.issues.add(new FailureIssue(
                    IssueType.STUCK_ROADMAP,
                    "No progress for " + STUCK_DAYS + "+ days with incomplete steps remaining",
                    List.of(
                            "Set a specific time block for roadmap work today",
                            "Review the next step and break it into a 15-minute task",
                            "Check if any steps are blocked by external dependencies",
                            "Consider simplifying the current step"
                    ),
                    Severity.MEDIUM
            ));
        }
    }

    private void analyzeCyclicDependencies(int totalSteps, FailureDiagnosticReport report) {
        // For sequential deps (default), cycles are impossible.
        // This would detect cycles in custom dependency graphs.
        // With sequential deps: step N depends on N-1, so no cycles possible.
        // Placeholder for when custom deps are supported.
        if (totalSteps > 50) {
            // Large roadmaps have higher risk of logical cycles in custom deps
            report.issues.add(new FailureIssue(
                    IssueType.CYCLIC_DEPENDENCY_RISK,
                    "Large roadmap (" + totalSteps + " steps) — verify no circular dependencies exist",
                    List.of("Review step dependencies for circular references"),
                    Severity.LOW
            ));
        }
    }

    private void analyzeInvalidTransitions(List<TimelineEntry> timeline, FailureDiagnosticReport report) {
        Map<Integer, String> currentStates = new HashMap<>();

        for (TimelineEntry entry : timeline) {
            if (entry.type != EntryType.STATE_TRANSITION) continue;
            String current = currentStates.getOrDefault(entry.stepIndex, "NOT_STARTED");
            String next    = entry.newState;
            if (next == null) continue;

            // COMPLETED → BLOCKED is invalid
            if ("COMPLETED".equals(current) && "BLOCKED".equals(next)) {
                report.issues.add(new FailureIssue(
                        IssueType.INVALID_TRANSITION,
                        "Step " + entry.stepIndex + ": COMPLETED → BLOCKED (invalid)",
                        List.of(
                                "Verify the step was correctly marked complete",
                                "Check if a dependency was incorrectly re-added"
                        ),
                        Severity.HIGH
                ));
            }
            currentStates.put(entry.stepIndex, next);
        }
    }

    private void analyzeExcessiveMutations(List<TimelineEntry> timeline, FailureDiagnosticReport report) {
        long mutations = timeline.stream()
                .filter(e -> e.type == EntryType.ROADMAP_MUTATION).count();

        if (mutations >= MUTATION_THRESHOLD) {
            report.issues.add(new FailureIssue(
                    IssueType.EXCESSIVE_MUTATIONS,
                    "Roadmap has been mutated " + mutations + " times — possible instability",
                    List.of(
                            "Review whether the original roadmap was appropriate for the skill level",
                            "Consider regenerating the roadmap with a clearer goal",
                            "Reduce automatic adaptation sensitivity"
                    ),
                    Severity.MEDIUM
            ));
        }
    }

    private void analyzeEventLoopRisk(List<ExecutionEvent> events, FailureDiagnosticReport report) {
        // Detect rapid repeated events of the same type on the same step
        Map<String, Long> eventCounts = events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.type.name() + ":" + e.stepIndex,
                        Collectors.counting()
                ));

        for (Map.Entry<String, Long> entry : eventCounts.entrySet()) {
            if (entry.getValue() >= LOOP_RISK_THRESHOLD) {
                String[] parts = entry.getKey().split(":");
                report.issues.add(new FailureIssue(
                        IssueType.EVENT_LOOP_RISK,
                        "Event " + parts[0] + " on step " + parts[1]
                                + " fired " + entry.getValue() + " times — possible loop",
                        List.of(
                                "Check for duplicate event publishers",
                                "Verify state transition guards are working correctly",
                                "Review subscriber handlers for re-entrant calls"
                        ),
                        Severity.HIGH
                ));
            }
        }
    }

    private double computeHealthScore(FailureDiagnosticReport report) {
        double score = 1.0;
        for (FailureIssue issue : report.issues) {
            score -= switch (issue.severity) {
                case CRITICAL -> 0.4;
                case HIGH     -> 0.2;
                case MEDIUM   -> 0.1;
                case LOW      -> 0.05;
            };
        }
        return Math.max(0.0, score);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public enum IssueType {
        REPEATED_FAILURE, STUCK_ROADMAP, CYCLIC_DEPENDENCY_RISK,
        INVALID_TRANSITION, EXCESSIVE_MUTATIONS, EVENT_LOOP_RISK
    }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public static class FailureIssue {
        public final IssueType    type;
        public final String       description;
        public final List<String> suggestions;
        public final Severity     severity;

        public FailureIssue(IssueType type, String description,
                            List<String> suggestions, Severity severity) {
            this.type        = type;
            this.description = description;
            this.suggestions = suggestions;
            this.severity    = severity;
        }
    }

    public static class FailureDiagnosticReport {
        public final Long              taskId;
        public final List<FailureIssue> issues = new ArrayList<>();
        public       double            healthScore;
        public       String            overallStatus; // HEALTHY | DEGRADED | CRITICAL

        public FailureDiagnosticReport(Long taskId) { this.taskId = taskId; }
    }
}
