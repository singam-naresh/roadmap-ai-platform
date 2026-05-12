package com.assistant.service;

import com.assistant.service.ExecutionEventBus.EventType;
import com.assistant.service.ExecutionEventBus.ExecutionEvent;
import com.assistant.service.ExecutionTimelineEngine.TimelineEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4.5 — Execution Event Inspector
 *
 * Provides deep inspection of the event stream:
 *   - Filter events by type, task, time range
 *   - Detect event storms (too many events in short window)
 *   - Identify recursive loops (same event repeating)
 *   - Detect invalid state transitions
 *   - Surface anomalies and warnings
 */
@Service
public class ExecutionEventInspector {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventInspector.class);

    // Storm detection: N events within T seconds = storm
    private static final int    STORM_EVENT_COUNT   = 10;
    private static final int    STORM_WINDOW_SECONDS = 5;
    private static final int    LOOP_REPEAT_COUNT   = 3;

    private final ExecutionEventBus       eventBus;
    private final ExecutionTimelineEngine timelineEngine;

    public ExecutionEventInspector(ExecutionEventBus eventBus,
                                   ExecutionTimelineEngine timelineEngine) {
        this.eventBus       = eventBus;
        this.timelineEngine = timelineEngine;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inspection
    // ─────────────────────────────────────────────────────────────────────────

    public InspectionReport inspect(Long taskId) {
        List<ExecutionEvent> events = eventBus.getRecentEvents(taskId);
        List<TimelineEntry>  timeline = timelineEngine.getTimeline(taskId);

        InspectionReport report = new InspectionReport(taskId);

        // Event type distribution
        report.eventTypeCounts = events.stream()
                .collect(Collectors.groupingBy(e -> e.type.name(), Collectors.counting()));

        // Storm detection
        report.storms = detectEventStorms(events);

        // Loop detection
        report.loops = detectRecursiveLoops(events);

        // Invalid transitions
        report.invalidTransitions = detectInvalidTransitions(timeline);

        // Anomalies
        report.anomalies = detectAnomalies(events, timeline);

        // Summary
        report.totalEvents    = events.size();
        report.timelineLength = timeline.size();
        report.hasIssues      = !report.storms.isEmpty() || !report.loops.isEmpty()
                                || !report.invalidTransitions.isEmpty();

        log.debug("[inspector] task={} events={} storms={} loops={} invalid={}",
                taskId, events.size(), report.storms.size(),
                report.loops.size(), report.invalidTransitions.size());
        return report;
    }

    public List<ExecutionEvent> filterEvents(Long taskId, EventType type) {
        return eventBus.getRecentEvents(taskId, type);
    }

    public List<ExecutionEvent> filterEventsSince(Long taskId, LocalDateTime since) {
        return eventBus.getRecentEvents(taskId).stream()
                .filter(e -> e.occurredAt.isAfter(since))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detection algorithms
    // ─────────────────────────────────────────────────────────────────────────

    private List<EventStorm> detectEventStorms(List<ExecutionEvent> events) {
        List<EventStorm> storms = new ArrayList<>();
        if (events.size() < STORM_EVENT_COUNT) return storms;

        // Sliding window check
        for (int i = 0; i <= events.size() - STORM_EVENT_COUNT; i++) {
            ExecutionEvent first = events.get(i);
            ExecutionEvent last  = events.get(i + STORM_EVENT_COUNT - 1);
            Duration window = Duration.between(first.occurredAt, last.occurredAt);

            if (window.getSeconds() <= STORM_WINDOW_SECONDS) {
                storms.add(new EventStorm(
                        first.occurredAt, last.occurredAt,
                        STORM_EVENT_COUNT, (int) window.getSeconds(),
                        "High event frequency detected: " + STORM_EVENT_COUNT
                                + " events in " + window.getSeconds() + "s"
                ));
                // Skip ahead to avoid duplicate storm reports
                i += STORM_EVENT_COUNT - 1;
            }
        }
        return storms;
    }

    private List<LoopDetection> detectRecursiveLoops(List<ExecutionEvent> events) {
        List<LoopDetection> loops = new ArrayList<>();

        // Group by (type, stepIndex) and check for rapid repeats
        Map<String, List<ExecutionEvent>> grouped = events.stream()
                .collect(Collectors.groupingBy(e -> e.type.name() + ":" + e.stepIndex));

        for (Map.Entry<String, List<ExecutionEvent>> entry : grouped.entrySet()) {
            List<ExecutionEvent> group = entry.getValue();
            if (group.size() >= LOOP_REPEAT_COUNT) {
                // Check if they happened within a short window
                ExecutionEvent first = group.get(0);
                ExecutionEvent last  = group.get(group.size() - 1);
                Duration span = Duration.between(first.occurredAt, last.occurredAt);

                if (span.getSeconds() < 60) { // same event type+step N times in 60s
                    String[] parts = entry.getKey().split(":");
                    loops.add(new LoopDetection(
                            parts[0], Integer.parseInt(parts[1]),
                            group.size(), span.getSeconds(),
                            "Possible loop: " + parts[0] + " on step " + parts[1]
                                    + " repeated " + group.size() + " times"
                    ));
                }
            }
        }
        return loops;
    }

    private List<InvalidTransition> detectInvalidTransitions(List<TimelineEntry> timeline) {
        List<InvalidTransition> invalid = new ArrayList<>();

        // Track per-step state
        Map<Integer, String> currentStates = new HashMap<>();

        for (TimelineEntry entry : timeline) {
            if (entry.type != ExecutionTimelineEngine.EntryType.STATE_TRANSITION) continue;

            String current = currentStates.getOrDefault(entry.stepIndex, "NOT_STARTED");
            String next    = entry.newState;

            if (next == null) continue;

            // Check for invalid transitions
            if ("COMPLETED".equals(current) && "COMPLETED".equals(next)) {
                invalid.add(new InvalidTransition(
                        entry.stepIndex, current, next,
                        "Step already completed — duplicate completion event",
                        entry.occurredAt
                ));
            }

            if ("COMPLETED".equals(current) && "BLOCKED".equals(next)) {
                invalid.add(new InvalidTransition(
                        entry.stepIndex, current, next,
                        "Completed step cannot be blocked",
                        entry.occurredAt
                ));
            }

            currentStates.put(entry.stepIndex, next);
        }
        return invalid;
    }

    private List<String> detectAnomalies(List<ExecutionEvent> events,
                                          List<TimelineEntry> timeline) {
        List<String> anomalies = new ArrayList<>();

        // Check for steps that have been in BLOCKED state for a long time
        Map<Integer, LocalDateTime> blockedSince = new HashMap<>();
        for (TimelineEntry entry : timeline) {
            if ("BLOCKED".equals(entry.newState)) {
                blockedSince.put(entry.stepIndex, entry.occurredAt);
            } else if (!"BLOCKED".equals(entry.previousState)) {
                blockedSince.remove(entry.stepIndex);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<Integer, LocalDateTime> entry : blockedSince.entrySet()) {
            Duration blocked = Duration.between(entry.getValue(), now);
            if (blocked.toDays() >= 2) {
                anomalies.add("Step " + entry.getKey() + " has been BLOCKED for "
                        + blocked.toDays() + " days");
            }
        }

        // Check for high failure rate
        long failures = events.stream().filter(e -> e.type == EventType.EXECUTION_FAILED).count();
        if (failures >= 3) {
            anomalies.add("High failure rate: " + failures + " failures detected");
        }

        return anomalies;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class InspectionReport {
        public final Long                  taskId;
        public       int                   totalEvents;
        public       int                   timelineLength;
        public       boolean               hasIssues;
        public       Map<String, Long>     eventTypeCounts = new HashMap<>();
        public       List<EventStorm>      storms          = new ArrayList<>();
        public       List<LoopDetection>   loops           = new ArrayList<>();
        public       List<InvalidTransition> invalidTransitions = new ArrayList<>();
        public       List<String>          anomalies       = new ArrayList<>();

        public InspectionReport(Long taskId) { this.taskId = taskId; }
    }

    public record EventStorm(LocalDateTime start, LocalDateTime end,
                             int eventCount, int windowSeconds, String description) {}

    public record LoopDetection(String eventType, int stepIndex,
                                int repeatCount, long spanSeconds, String description) {}

    public record InvalidTransition(int stepIndex, String fromState, String toState,
                                    String reason, LocalDateTime occurredAt) {}
}
