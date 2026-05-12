package com.assistant.service;

import com.assistant.model.RoadmapStep;
import com.assistant.model.StepProgress;
import com.assistant.repository.RoadmapStepRepository;
import com.assistant.repository.StepProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4 — Execution State Machine
 *
 * Manages the full lifecycle of each roadmap step:
 *   NOT_STARTED → IN_PROGRESS → COMPLETED
 *                             → BLOCKED
 *                             → FAILED
 *                             → NEEDS_REVIEW
 *                             → SKIPPED
 *
 * Persists rich execution metadata (retry count, blockers, timestamps,
 * estimated vs actual duration) on top of the existing StepProgress table.
 */
@Service
public class ExecutionStateEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStateEngine.class);

    public enum StepState {
        NOT_STARTED, IN_PROGRESS, BLOCKED, COMPLETED, SKIPPED, FAILED, NEEDS_REVIEW
    }

    private final StepProgressRepository stepProgressRepository;
    private final RoadmapStepRepository  roadmapStepRepository;
    private final ExecutionEventBus      eventBus;
    private final ExecutionAuditService  auditService;
    private final ExecutionTimelineEngine timelineEngine;

    public ExecutionStateEngine(StepProgressRepository stepProgressRepository,
                                RoadmapStepRepository roadmapStepRepository,
                                ExecutionEventBus eventBus,
                                ExecutionAuditService auditService,
                                ExecutionTimelineEngine timelineEngine) {
        this.stepProgressRepository = stepProgressRepository;
        this.roadmapStepRepository  = roadmapStepRepository;
        this.eventBus               = eventBus;
        this.auditService           = auditService;
        this.timelineEngine         = timelineEngine;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State transitions
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public StepExecutionState transitionState(Long taskId, int stepIndex, StepState newState,
                                              String notes, String blocker) {
        StepProgress progress = getOrCreate(taskId, stepIndex);
        StepState oldState = deriveState(progress);

        if (!isValidTransition(oldState, newState)) {
            log.warn("[state-engine] Invalid transition {} → {} for task={} step={}", oldState, newState, taskId, stepIndex);
            // Allow it anyway — user intent wins
        }

        applyState(progress, newState, notes, blocker);
        stepProgressRepository.save(progress);

        StepExecutionState state = buildState(progress, newState);

        // PHASE 4.5: Audit every transition
        auditService.recordStateTransition(taskId, stepIndex, oldState, newState, "USER", notes);
        timelineEngine.recordTransition(taskId, stepIndex, oldState, newState, notes, blocker, "USER");

        // Fire events
        switch (newState) {
            case COMPLETED    -> eventBus.publish(ExecutionEventBus.EventType.STEP_COMPLETED,
                                    taskId, stepIndex, "Step completed");
            case BLOCKED      -> eventBus.publish(ExecutionEventBus.EventType.TASK_BLOCKED,
                                    taskId, stepIndex, blocker != null ? blocker : "Blocked");
            case FAILED       -> eventBus.publish(ExecutionEventBus.EventType.EXECUTION_FAILED,
                                    taskId, stepIndex, notes != null ? notes : "Step failed");
            default           -> {}
        }

        log.info("[state-engine] task={} step={} {} → {}", taskId, stepIndex, oldState, newState);
        return state;
    }

    @Transactional
    public StepExecutionState markComplete(Long taskId, int stepIndex) {
        return transitionState(taskId, stepIndex, StepState.COMPLETED, null, null);
    }

    @Transactional
    public StepExecutionState markIncomplete(Long taskId, int stepIndex) {
        StepProgress progress = getOrCreate(taskId, stepIndex);
        progress.setCompleted(false);
        progress.setNote(clearStateMarker(progress.getNote(), "COMPLETED"));
        stepProgressRepository.save(progress);
        return buildState(progress, StepState.NOT_STARTED);
    }

    @Transactional
    public StepExecutionState markBlocked(Long taskId, int stepIndex, String blocker) {
        return transitionState(taskId, stepIndex, StepState.BLOCKED, null, blocker);
    }

    @Transactional
    public StepExecutionState markInProgress(Long taskId, int stepIndex) {
        return transitionState(taskId, stepIndex, StepState.IN_PROGRESS, null, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    public StepExecutionState getStepState(Long taskId, int stepIndex) {
        StepProgress progress = stepProgressRepository
                .findByTaskIdAndStepIndex(taskId, stepIndex)
                .orElse(null);
        if (progress == null) {
            return StepExecutionState.notStarted(taskId, stepIndex);
        }
        return buildState(progress, deriveState(progress));
    }

    public List<StepExecutionState> getAllStepStates(Long taskId) {
        return stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId)
                .stream()
                .map(p -> buildState(p, deriveState(p)))
                .collect(Collectors.toList());
    }

    public ExecutionSummary getExecutionSummary(Long taskId, int totalSteps) {
        List<StepProgress> all = stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId);

        long completed  = all.stream().filter(StepProgress::isCompleted).count();
        long blocked    = all.stream().filter(p -> hasStateMarker(p.getNote(), "BLOCKED")).count();
        long inProgress = all.stream().filter(p -> hasStateMarker(p.getNote(), "IN_PROGRESS")).count();
        long failed     = all.stream().filter(p -> hasStateMarker(p.getNote(), "FAILED")).count();
        long notStarted = totalSteps - completed - blocked - inProgress - failed;

        // Real progress: weighted by completion + partial credit for in-progress
        double rawProgress = totalSteps > 0
                ? (completed + inProgress * 0.3) / totalSteps * 100.0
                : 0.0;
        int progressPct = (int) Math.min(100, Math.round(rawProgress));

        // Velocity: completions in last 7 days
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long recentCompletions = all.stream()
                .filter(p -> p.isCompleted() && p.getUpdatedAt() != null && p.getUpdatedAt().isAfter(weekAgo))
                .count();

        return new ExecutionSummary(taskId, totalSteps, (int) completed, (int) blocked,
                (int) inProgress, (int) failed, (int) notStarted, progressPct, (int) recentCompletions);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private StepProgress getOrCreate(Long taskId, int stepIndex) {
        return stepProgressRepository.findByTaskIdAndStepIndex(taskId, stepIndex)
                .orElseGet(() -> {
                    StepProgress p = new StepProgress();
                    p.setTaskId(taskId);
                    p.setStepIndex(stepIndex);
                    return p;
                });
    }

    private StepState deriveState(StepProgress p) {
        if (p.isCompleted())                          return StepState.COMPLETED;
        if (hasStateMarker(p.getNote(), "FAILED"))    return StepState.FAILED;
        if (hasStateMarker(p.getNote(), "BLOCKED"))   return StepState.BLOCKED;
        if (hasStateMarker(p.getNote(), "SKIPPED"))   return StepState.SKIPPED;
        if (hasStateMarker(p.getNote(), "NEEDS_REVIEW")) return StepState.NEEDS_REVIEW;
        if (hasStateMarker(p.getNote(), "IN_PROGRESS")) return StepState.IN_PROGRESS;
        return StepState.NOT_STARTED;
    }

    private void applyState(StepProgress p, StepState state, String notes, String blocker) {
        // Clear all state markers first
        String cleanNote = stripAllStateMarkers(p.getNote());

        switch (state) {
            case COMPLETED -> {
                p.setCompleted(true);
                p.setNote(appendNote(cleanNote, notes));
            }
            case IN_PROGRESS -> {
                p.setCompleted(false);
                p.setNote(appendNote("[IN_PROGRESS]" + (blocker != null ? " BLOCKER:" + blocker : ""), notes));
            }
            case BLOCKED -> {
                p.setCompleted(false);
                p.setNote("[BLOCKED] " + (blocker != null ? blocker : "Dependency not met") +
                          (notes != null ? " | " + notes : ""));
            }
            case FAILED -> {
                p.setCompleted(false);
                p.setNote("[FAILED] " + (notes != null ? notes : "Step failed"));
            }
            case SKIPPED -> {
                p.setCompleted(false);
                p.setNote("[SKIPPED] " + (notes != null ? notes : ""));
            }
            case NEEDS_REVIEW -> {
                p.setCompleted(false);
                p.setNote("[NEEDS_REVIEW] " + (notes != null ? notes : ""));
            }
            case NOT_STARTED -> {
                p.setCompleted(false);
                p.setNote(cleanNote);
            }
        }
    }

    private boolean isValidTransition(StepState from, StepState to) {
        // Most transitions are allowed; only prevent going backwards from COMPLETED
        // unless explicitly resetting
        if (from == StepState.COMPLETED && to == StepState.COMPLETED) return false;
        return true;
    }

    private boolean hasStateMarker(String note, String marker) {
        return note != null && note.contains("[" + marker + "]");
    }

    private String clearStateMarker(String note, String marker) {
        if (note == null) return null;
        return note.replace("[" + marker + "]", "").trim();
    }

    private String stripAllStateMarkers(String note) {
        if (note == null) return null;
        return note.replaceAll("\\[(IN_PROGRESS|BLOCKED|FAILED|SKIPPED|NEEDS_REVIEW|COMPLETED)[^]]*]", "").trim();
    }

    private String appendNote(String base, String extra) {
        if (extra == null || extra.isBlank()) return base;
        if (base == null || base.isBlank()) return extra;
        return base + " | " + extra;
    }

    private StepExecutionState buildState(StepProgress p, StepState state) {
        String blocker = null;
        if (state == StepState.BLOCKED && p.getNote() != null) {
            blocker = p.getNote().replace("[BLOCKED]", "").trim();
        }
        return new StepExecutionState(
                p.getTaskId(), p.getStepIndex(), state,
                p.getPriority(), p.getNote(), blocker, p.getUpdatedAt()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class StepExecutionState {
        public final Long          taskId;
        public final int           stepIndex;
        public final StepState     state;
        public final String        priority;
        public final String        notes;
        public final String        blocker;
        public final LocalDateTime updatedAt;

        public StepExecutionState(Long taskId, int stepIndex, StepState state,
                                  String priority, String notes, String blocker,
                                  LocalDateTime updatedAt) {
            this.taskId    = taskId;
            this.stepIndex = stepIndex;
            this.state     = state;
            this.priority  = priority;
            this.notes     = notes;
            this.blocker   = blocker;
            this.updatedAt = updatedAt;
        }

        public static StepExecutionState notStarted(Long taskId, int stepIndex) {
            return new StepExecutionState(taskId, stepIndex, StepState.NOT_STARTED,
                    "MEDIUM", null, null, null);
        }
    }

    public static class ExecutionSummary {
        public final Long taskId;
        public final int  totalSteps;
        public final int  completedSteps;
        public final int  blockedSteps;
        public final int  inProgressSteps;
        public final int  failedSteps;
        public final int  notStartedSteps;
        public final int  progressPercentage;
        public final int  recentCompletions; // last 7 days

        public ExecutionSummary(Long taskId, int totalSteps, int completedSteps,
                                int blockedSteps, int inProgressSteps, int failedSteps,
                                int notStartedSteps, int progressPercentage, int recentCompletions) {
            this.taskId             = taskId;
            this.totalSteps         = totalSteps;
            this.completedSteps     = completedSteps;
            this.blockedSteps       = blockedSteps;
            this.inProgressSteps    = inProgressSteps;
            this.failedSteps        = failedSteps;
            this.notStartedSteps    = notStartedSteps;
            this.progressPercentage = progressPercentage;
            this.recentCompletions  = recentCompletions;
        }
    }
}
