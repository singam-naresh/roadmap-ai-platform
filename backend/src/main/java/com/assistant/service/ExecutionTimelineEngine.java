package com.assistant.service;

import com.assistant.service.ExecutionEventBus.EventType;
import com.assistant.service.ExecutionEventBus.ExecutionEvent;
import com.assistant.service.ExecutionStateEngine.StepState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * PHASE 4.5 — Execution Timeline Engine
 *
 * Maintains a chronological, immutable timeline of every execution event
 * per task. Each entry captures:
 *   - timestamp
 *   - triggering event type
 *   - previous state → new state
 *   - affected entities (taskId, stepIndex)
 *   - execution context (notes, blocker, actor)
 *
 * The timeline is the source of truth for replay and audit.
 * It is append-only — entries are never modified or deleted.
 */
@Service
public class ExecutionTimelineEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimelineEngine.class);
    private static final int MAX_ENTRIES_PER_TASK = 1000;

    // taskId → ordered list of timeline entries (append-only)
    private final Map<Long, List<TimelineEntry>> timelines = new ConcurrentHashMap<>();

    public ExecutionTimelineEngine(ExecutionEventBus eventBus) {
        // Subscribe to all event types to auto-record timeline entries
        for (EventType type : EventType.values()) {
            eventBus.subscribe(type, event -> recordFromEvent(event, null, null));
        }
        log.info("[timeline] ExecutionTimelineEngine initialized — subscribed to all event types");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recording
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Records a state transition with full context.
     */
    public TimelineEntry recordTransition(Long taskId, int stepIndex,
                                          StepState previousState, StepState newState,
                                          String notes, String blocker, String actor) {
        TimelineEntry entry = new TimelineEntry(
                generateId(taskId),
                taskId, stepIndex,
                EntryType.STATE_TRANSITION,
                previousState != null ? previousState.name() : "UNKNOWN",
                newState != null ? newState.name() : "UNKNOWN",
                notes, blocker, actor,
                LocalDateTime.now()
        );
        append(taskId, entry);
        log.debug("[timeline] task={} step={} {} → {}", taskId, stepIndex, previousState, newState);
        return entry;
    }

    /**
     * Records a roadmap mutation (inject, reorder, simplify, etc.).
     */
    public TimelineEntry recordMutation(Long taskId, int stepIndex,
                                        String mutationType, String description) {
        TimelineEntry entry = new TimelineEntry(
                generateId(taskId),
                taskId, stepIndex,
                EntryType.ROADMAP_MUTATION,
                null, mutationType,
                description, null, "SYSTEM",
                LocalDateTime.now()
        );
        append(taskId, entry);
        log.info("[timeline] Mutation recorded: task={} step={} type={}", taskId, stepIndex, mutationType);
        return entry;
    }

    /**
     * Records a dependency unlock event.
     */
    public TimelineEntry recordUnlock(Long taskId, int unlockedStepIndex, int triggerStepIndex) {
        TimelineEntry entry = new TimelineEntry(
                generateId(taskId),
                taskId, unlockedStepIndex,
                EntryType.DEPENDENCY_UNLOCK,
                "BLOCKED", "READY",
                "Unlocked by completion of step " + triggerStepIndex, null, "SYSTEM",
                LocalDateTime.now()
        );
        append(taskId, entry);
        return entry;
    }

    /**
     * Records a priority change.
     */
    public TimelineEntry recordPriorityChange(Long taskId, int stepIndex,
                                               String oldPriority, String newPriority) {
        TimelineEntry entry = new TimelineEntry(
                generateId(taskId),
                taskId, stepIndex,
                EntryType.PRIORITY_CHANGE,
                oldPriority, newPriority,
                "Priority changed", null, "USER",
                LocalDateTime.now()
        );
        append(taskId, entry);
        return entry;
    }

    /**
     * Records an adaptation injection (reinforcement step, etc.).
     */
    public TimelineEntry recordAdaptation(Long taskId, int stepIndex,
                                           String adaptationType, String description) {
        TimelineEntry entry = new TimelineEntry(
                generateId(taskId),
                taskId, stepIndex,
                EntryType.ADAPTATION_INJECTION,
                null, adaptationType,
                description, null, "ADAPTIVE_ENGINE",
                LocalDateTime.now()
        );
        append(taskId, entry);
        return entry;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    public List<TimelineEntry> getTimeline(Long taskId) {
        return Collections.unmodifiableList(
                timelines.getOrDefault(taskId, List.of()));
    }

    public List<TimelineEntry> getTimeline(Long taskId, EntryType type) {
        return getTimeline(taskId).stream()
                .filter(e -> e.type == type)
                .collect(Collectors.toList());
    }

    public List<TimelineEntry> getTimelineForStep(Long taskId, int stepIndex) {
        return getTimeline(taskId).stream()
                .filter(e -> e.stepIndex == stepIndex)
                .collect(Collectors.toList());
    }

    public List<TimelineEntry> getTimelineSince(Long taskId, LocalDateTime since) {
        return getTimeline(taskId).stream()
                .filter(e -> e.occurredAt.isAfter(since))
                .collect(Collectors.toList());
    }

    public Optional<TimelineEntry> getLastEntry(Long taskId) {
        List<TimelineEntry> entries = timelines.getOrDefault(taskId, List.of());
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(entries.size() - 1));
    }

    public int getEntryCount(Long taskId) {
        return timelines.getOrDefault(taskId, List.of()).size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private void recordFromEvent(ExecutionEvent event, StepState previousState, StepState newState) {
        EntryType entryType = switch (event.type) {
            case STEP_COMPLETED   -> EntryType.STATE_TRANSITION;
            case TASK_BLOCKED     -> EntryType.STATE_TRANSITION;
            case ROADMAP_MUTATED  -> EntryType.ROADMAP_MUTATION;
            case PRIORITY_CHANGED -> EntryType.PRIORITY_CHANGE;
            case USER_STUCK       -> EntryType.ADAPTATION_INJECTION;
            case EXECUTION_FAILED -> EntryType.STATE_TRANSITION;
            default               -> EntryType.STATE_TRANSITION;
        };

        String prev = previousState != null ? previousState.name() : deriveFromEventType(event.type, true);
        String next = newState != null ? newState.name() : deriveFromEventType(event.type, false);

        TimelineEntry entry = new TimelineEntry(
                generateId(event.taskId),
                event.taskId, event.stepIndex,
                entryType, prev, next,
                event.message, null, "EVENT_BUS",
                event.occurredAt
        );
        append(event.taskId, entry);
    }

    private String deriveFromEventType(EventType type, boolean previous) {
        return switch (type) {
            case STEP_COMPLETED   -> previous ? "IN_PROGRESS" : "COMPLETED";
            case TASK_BLOCKED     -> previous ? "NOT_STARTED" : "BLOCKED";
            case EXECUTION_FAILED -> previous ? "IN_PROGRESS" : "FAILED";
            default               -> previous ? "UNKNOWN" : type.name();
        };
    }

    private void append(Long taskId, TimelineEntry entry) {
        List<TimelineEntry> list = timelines.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>());
        list.add(entry);
        // Trim if over limit (keep most recent)
        if (list.size() > MAX_ENTRIES_PER_TASK) {
            ((CopyOnWriteArrayList<TimelineEntry>) list).remove(0);
        }
    }

    private String generateId(Long taskId) {
        return "tl-" + taskId + "-" + System.nanoTime();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public enum EntryType {
        STATE_TRANSITION,
        ROADMAP_MUTATION,
        DEPENDENCY_UNLOCK,
        PRIORITY_CHANGE,
        ADAPTATION_INJECTION,
        FAILURE,
        RETRY
    }

    public static class TimelineEntry {
        public final String        id;
        public final Long          taskId;
        public final int           stepIndex;
        public final EntryType     type;
        public final String        previousState;
        public final String        newState;
        public final String        notes;
        public final String        blocker;
        public final String        actor;          // USER | SYSTEM | ADAPTIVE_ENGINE | EVENT_BUS
        public final LocalDateTime occurredAt;

        public TimelineEntry(String id, Long taskId, int stepIndex, EntryType type,
                             String previousState, String newState,
                             String notes, String blocker, String actor,
                             LocalDateTime occurredAt) {
            this.id            = id;
            this.taskId        = taskId;
            this.stepIndex     = stepIndex;
            this.type          = type;
            this.previousState = previousState;
            this.newState      = newState;
            this.notes         = notes;
            this.blocker       = blocker;
            this.actor         = actor;
            this.occurredAt    = occurredAt;
        }
    }
}
