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
 * PHASE 4.5 — Execution Metrics Engine
 *
 * Computes execution analytics:
 *   - Average completion velocity (steps/day)
 *   - Blocked-task frequency
 *   - Mutation frequency
 *   - Retry frequency
 *   - Failure hotspots (which steps fail most)
 *   - Roadmap churn rate (mutations per step)
 *   - Dependency bottlenecks (steps that block the most)
 */
@Service
public class ExecutionMetricsEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionMetricsEngine.class);

    private final StepProgressRepository  stepProgressRepository;
    private final ExecutionEventBus       eventBus;
    private final ExecutionTimelineEngine timelineEngine;

    public ExecutionMetricsEngine(StepProgressRepository stepProgressRepository,
                                  ExecutionEventBus eventBus,
                                  ExecutionTimelineEngine timelineEngine) {
        this.stepProgressRepository = stepProgressRepository;
        this.eventBus               = eventBus;
        this.timelineEngine         = timelineEngine;
    }

    public ExecutionMetrics computeMetrics(Long taskId, int totalSteps) {
        List<StepProgress>  progress = stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId);
        List<ExecutionEvent> events  = eventBus.getRecentEvents(taskId);
        List<TimelineEntry>  timeline = timelineEngine.getTimeline(taskId);

        ExecutionMetrics metrics = new ExecutionMetrics(taskId);

        // ── Completion velocity ───────────────────────────────────────────────
        metrics.completionVelocity = computeVelocity(progress);

        // ── Blocked frequency ─────────────────────────────────────────────────
        long blockedEvents = events.stream()
                .filter(e -> e.type == EventType.TASK_BLOCKED).count();
        metrics.blockedFrequency = totalSteps > 0 ? (double) blockedEvents / totalSteps : 0.0;

        // ── Mutation frequency ────────────────────────────────────────────────
        long mutations = timeline.stream()
                .filter(e -> e.type == EntryType.ROADMAP_MUTATION).count();
        metrics.mutationFrequency = totalSteps > 0 ? (double) mutations / totalSteps : 0.0;
        metrics.totalMutations    = (int) mutations;

        // ── Failure hotspots ──────────────────────────────────────────────────
        metrics.failureHotspots = computeFailureHotspots(events);

        // ── Retry frequency ───────────────────────────────────────────────────
        metrics.retryFrequency = computeRetryFrequency(timeline);

        // ── Roadmap churn rate ────────────────────────────────────────────────
        metrics.roadmapChurnRate = metrics.mutationFrequency;

        // ── Dependency bottlenecks ────────────────────────────────────────────
        metrics.dependencyBottlenecks = computeBottlenecks(timeline, totalSteps);

        // ── Consistency score ─────────────────────────────────────────────────
        metrics.consistencyScore = computeConsistencyScore(progress);

        // ── Focus patterns (most active hours) ───────────────────────────────
        metrics.focusPatterns = computeFocusPatterns(events);

        // ── Burn-down data ────────────────────────────────────────────────────
        metrics.burnDownData = computeBurnDown(progress, totalSteps);

        log.debug("[metrics] task={} velocity={:.2f} blocked={:.2f} mutations={} failures={}",
                taskId, metrics.completionVelocity, metrics.blockedFrequency,
                metrics.totalMutations, metrics.failureHotspots.size());
        return metrics;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Computation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private double computeVelocity(List<StepProgress> progress) {
        List<StepProgress> completed = progress.stream()
                .filter(StepProgress::isCompleted)
                .filter(p -> p.getUpdatedAt() != null)
                .sorted(Comparator.comparing(StepProgress::getUpdatedAt))
                .collect(Collectors.toList());

        if (completed.size() < 2) return completed.size();

        LocalDateTime first = completed.get(0).getUpdatedAt();
        LocalDateTime last  = completed.get(completed.size() - 1).getUpdatedAt();
        double days = Math.max(1, Duration.between(first, last).toDays());
        return completed.size() / days; // steps per day
    }

    private Map<Integer, Integer> computeFailureHotspots(List<ExecutionEvent> events) {
        return events.stream()
                .filter(e -> e.type == EventType.EXECUTION_FAILED)
                .collect(Collectors.groupingBy(
                        e -> e.stepIndex,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }

    private double computeRetryFrequency(List<TimelineEntry> timeline) {
        long retries = timeline.stream()
                .filter(e -> e.type == EntryType.RETRY).count();
        long total   = timeline.size();
        return total > 0 ? (double) retries / total : 0.0;
    }

    private List<Integer> computeBottlenecks(List<TimelineEntry> timeline, int totalSteps) {
        // Steps that appear most in BLOCKED events are bottlenecks
        Map<Integer, Long> blockedCounts = timeline.stream()
                .filter(e -> "BLOCKED".equals(e.newState))
                .collect(Collectors.groupingBy(e -> e.stepIndex, Collectors.counting()));

        return blockedCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(5)
                .collect(Collectors.toList());
    }

    private double computeConsistencyScore(List<StepProgress> progress) {
        if (progress.isEmpty()) return 0.0;

        // Consistency = how evenly distributed completions are over time
        List<LocalDateTime> completionTimes = progress.stream()
                .filter(StepProgress::isCompleted)
                .filter(p -> p.getUpdatedAt() != null)
                .map(StepProgress::getUpdatedAt)
                .sorted()
                .collect(Collectors.toList());

        if (completionTimes.size() < 2) return completionTimes.isEmpty() ? 0.0 : 0.5;

        // Compute gaps between completions
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < completionTimes.size(); i++) {
            gaps.add(Duration.between(completionTimes.get(i - 1), completionTimes.get(i)).toHours());
        }

        double avgGap = gaps.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = gaps.stream()
                .mapToDouble(g -> Math.pow(g - avgGap, 2))
                .average().orElse(0);

        // Low variance = high consistency
        double stdDev = Math.sqrt(variance);
        return Math.max(0.0, Math.min(1.0, 1.0 - (stdDev / Math.max(1, avgGap + 1))));
    }

    private Map<Integer, Integer> computeFocusPatterns(List<ExecutionEvent> events) {
        // Hour of day → event count
        return events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.occurredAt.getHour(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }

    private List<BurnDownPoint> computeBurnDown(List<StepProgress> progress, int totalSteps) {
        if (progress.isEmpty()) return List.of();

        // Sort completions by time and build cumulative burn-down
        List<StepProgress> completed = progress.stream()
                .filter(StepProgress::isCompleted)
                .filter(p -> p.getUpdatedAt() != null)
                .sorted(Comparator.comparing(StepProgress::getUpdatedAt))
                .collect(Collectors.toList());

        List<BurnDownPoint> points = new ArrayList<>();
        int remaining = totalSteps;

        for (StepProgress p : completed) {
            remaining--;
            points.add(new BurnDownPoint(p.getUpdatedAt(), remaining, totalSteps - remaining));
        }
        return points;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class ExecutionMetrics {
        public final Long                  taskId;
        public       double                completionVelocity;   // steps/day
        public       double                blockedFrequency;     // blocked events / total steps
        public       double                mutationFrequency;    // mutations / total steps
        public       int                   totalMutations;
        public       double                retryFrequency;
        public       double                roadmapChurnRate;
        public       double                consistencyScore;     // 0.0–1.0
        public       Map<Integer, Integer> failureHotspots;      // stepIndex → failure count
        public       List<Integer>         dependencyBottlenecks;
        public       Map<Integer, Integer> focusPatterns;        // hour → event count
        public       List<BurnDownPoint>   burnDownData;

        public ExecutionMetrics(Long taskId) {
            this.taskId                 = taskId;
            this.failureHotspots        = new HashMap<>();
            this.dependencyBottlenecks  = new ArrayList<>();
            this.focusPatterns          = new HashMap<>();
            this.burnDownData           = new ArrayList<>();
        }
    }

    public record BurnDownPoint(LocalDateTime timestamp, int remaining, int completed) {}
}
