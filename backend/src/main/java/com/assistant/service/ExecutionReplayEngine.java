package com.assistant.service;

import com.assistant.service.ExecutionAuditService.AuditAction;
import com.assistant.service.ExecutionAuditService.AuditRecord;
import com.assistant.service.ExecutionStateEngine.StepState;
import com.assistant.service.ExecutionTimelineEngine.TimelineEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4.5 — Execution Replay Engine
 *
 * Reconstructs the exact execution state of a roadmap at any point in time
 * by replaying the audit log from the beginning up to a target timestamp
 * or sequence number.
 *
 * Supports:
 *   - Point-in-time state reconstruction
 *   - Step-by-step event sequence playback
 *   - Dependency unlock flow visualization
 *   - Mutation history replay
 */
@Service
public class ExecutionReplayEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionReplayEngine.class);

    private final ExecutionAuditService    auditService;
    private final ExecutionTimelineEngine  timelineEngine;

    public ExecutionReplayEngine(ExecutionAuditService auditService,
                                 ExecutionTimelineEngine timelineEngine) {
        this.auditService   = auditService;
        this.timelineEngine = timelineEngine;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reconstructs the full execution state at a given point in time.
     * Returns a map of stepIndex → state at that moment.
     */
    public ReplaySnapshot replayAtTime(Long taskId, LocalDateTime pointInTime) {
        List<AuditRecord> records = auditService.getAuditLog(taskId).stream()
                .filter(r -> !r.timestamp.isAfter(pointInTime))
                .sorted(Comparator.comparingLong(r -> r.sequence))
                .collect(Collectors.toList());

        return buildSnapshot(taskId, records, pointInTime);
    }

    /**
     * Reconstructs state up to a specific audit sequence number.
     */
    public ReplaySnapshot replayAtSequence(Long taskId, long targetSequence) {
        List<AuditRecord> records = auditService.getAuditLog(taskId).stream()
                .filter(r -> r.sequence <= targetSequence)
                .sorted(Comparator.comparingLong(r -> r.sequence))
                .collect(Collectors.toList());

        return buildSnapshot(taskId, records, null);
    }

    /**
     * Returns the full event sequence for playback (ordered by time).
     */
    public List<PlaybackFrame> getPlaybackSequence(Long taskId) {
        List<TimelineEntry> timeline = timelineEngine.getTimeline(taskId);
        List<PlaybackFrame> frames = new ArrayList<>();

        Map<Integer, String> currentStates = new HashMap<>();

        for (int i = 0; i < timeline.size(); i++) {
            TimelineEntry entry = timeline.get(i);
            String prevState = currentStates.getOrDefault(entry.stepIndex, "NOT_STARTED");

            frames.add(new PlaybackFrame(
                    i,
                    entry.occurredAt,
                    entry.type.name(),
                    entry.stepIndex,
                    prevState,
                    entry.newState != null ? entry.newState : prevState,
                    entry.notes,
                    entry.actor,
                    new HashMap<>(currentStates) // snapshot of all states at this frame
            ));

            // Update current state
            if (entry.newState != null) {
                currentStates.put(entry.stepIndex, entry.newState);
            }
        }

        log.debug("[replay] Generated {} playback frames for task={}", frames.size(), taskId);
        return frames;
    }

    /**
     * Returns the dependency unlock flow — which completions triggered which unlocks.
     */
    public List<UnlockEvent> getDependencyUnlockFlow(Long taskId) {
        return timelineEngine.getTimeline(taskId,
                        ExecutionTimelineEngine.EntryType.DEPENDENCY_UNLOCK)
                .stream()
                .map(e -> new UnlockEvent(
                        e.occurredAt,
                        e.stepIndex,
                        e.notes != null ? extractTriggerStep(e.notes) : -1
                ))
                .collect(Collectors.toList());
    }

    /**
     * Returns the mutation history — all roadmap changes in order.
     */
    public List<MutationRecord> getMutationHistory(Long taskId) {
        return auditService.getAuditLog(taskId, AuditAction.ROADMAP_MUTATION)
                .stream()
                .map(r -> new MutationRecord(
                        r.sequence, r.timestamp, r.stepIndex,
                        r.newValue, r.reason, r.actor
                ))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private ReplaySnapshot buildSnapshot(Long taskId, List<AuditRecord> records,
                                          LocalDateTime pointInTime) {
        Map<Integer, String> stepStates = new HashMap<>();
        Map<Integer, String> stepPriorities = new HashMap<>();
        List<String> appliedMutations = new ArrayList<>();

        for (AuditRecord record : records) {
            switch (record.action) {
                case STATE_TRANSITION -> {
                    if (record.newValue != null) {
                        stepStates.put(record.stepIndex, record.newValue);
                    }
                }
                case PRIORITY_CHANGE -> {
                    if (record.newValue != null) {
                        stepPriorities.put(record.stepIndex, record.newValue);
                    }
                }
                case ROADMAP_MUTATION -> {
                    appliedMutations.add(record.newValue + " at step " + record.stepIndex);
                }
                default -> {}
            }
        }

        long completedCount = stepStates.values().stream()
                .filter("COMPLETED"::equals).count();

        ReplaySnapshot snapshot = new ReplaySnapshot(
                taskId,
                pointInTime != null ? pointInTime : LocalDateTime.now(),
                records.isEmpty() ? 0 : records.get(records.size() - 1).sequence,
                stepStates,
                stepPriorities,
                appliedMutations,
                (int) completedCount,
                records.size()
        );

        log.debug("[replay] Snapshot for task={}: {} steps, {} completed, {} mutations",
                taskId, stepStates.size(), completedCount, appliedMutations.size());
        return snapshot;
    }

    private int extractTriggerStep(String notes) {
        // Parse "Unlocked by completion of step N"
        try {
            String[] parts = notes.split("step ");
            if (parts.length > 1) {
                return Integer.parseInt(parts[parts.length - 1].trim());
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class ReplaySnapshot {
        public final Long                taskId;
        public final LocalDateTime       pointInTime;
        public final long                lastSequence;
        public final Map<Integer, String> stepStates;      // stepIndex → state name
        public final Map<Integer, String> stepPriorities;  // stepIndex → priority
        public final List<String>        appliedMutations;
        public final int                 completedCount;
        public final int                 eventsReplayed;

        public ReplaySnapshot(Long taskId, LocalDateTime pointInTime, long lastSequence,
                              Map<Integer, String> stepStates, Map<Integer, String> stepPriorities,
                              List<String> appliedMutations, int completedCount, int eventsReplayed) {
            this.taskId           = taskId;
            this.pointInTime      = pointInTime;
            this.lastSequence     = lastSequence;
            this.stepStates       = stepStates;
            this.stepPriorities   = stepPriorities;
            this.appliedMutations = appliedMutations;
            this.completedCount   = completedCount;
            this.eventsReplayed   = eventsReplayed;
        }
    }

    public static class PlaybackFrame {
        public final int                  frameIndex;
        public final LocalDateTime        timestamp;
        public final String               eventType;
        public final int                  stepIndex;
        public final String               previousState;
        public final String               newState;
        public final String               notes;
        public final String               actor;
        public final Map<Integer, String> allStatesAtFrame;

        public PlaybackFrame(int frameIndex, LocalDateTime timestamp, String eventType,
                             int stepIndex, String previousState, String newState,
                             String notes, String actor, Map<Integer, String> allStatesAtFrame) {
            this.frameIndex       = frameIndex;
            this.timestamp        = timestamp;
            this.eventType        = eventType;
            this.stepIndex        = stepIndex;
            this.previousState    = previousState;
            this.newState         = newState;
            this.notes            = notes;
            this.actor            = actor;
            this.allStatesAtFrame = allStatesAtFrame;
        }
    }

    public record UnlockEvent(LocalDateTime timestamp, int unlockedStep, int triggerStep) {}

    public record MutationRecord(long sequence, LocalDateTime timestamp, int stepIndex,
                                 String mutationType, String description, String actor) {}
}
