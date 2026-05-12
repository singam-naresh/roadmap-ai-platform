package com.assistant.service;

import com.assistant.model.RoadmapStep;
import com.assistant.model.StepProgress;
import com.assistant.repository.RoadmapStepRepository;
import com.assistant.repository.StepProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4 — Adaptive Roadmap Engine
 *
 * Mutates roadmaps based on execution progress:
 *   - Detects slow progress and injects reinforcement tasks
 *   - Simplifies difficult paths when user is stuck
 *   - Accelerates advanced users by skipping basics
 *   - Dynamically reprioritizes steps
 *   - Recommends shortcuts
 *   - Detects skill gaps and injects supporting steps
 *
 * Mutations operate on RoadmapStep entities WITHOUT regenerating the
 * entire roadmap (no AI call needed for mutations).
 */
@Service
public class AdaptiveRoadmapEngine {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveRoadmapEngine.class);

    // Thresholds
    private static final int    STUCK_THRESHOLD_DAYS    = 3;  // no progress for 3 days = stuck
    private static final double SLOW_PROGRESS_THRESHOLD = 0.1; // < 10% per week = slow
    private static final int    FAILURE_THRESHOLD       = 2;  // 2+ failures = inject reinforcement

    private final RoadmapStepRepository  roadmapStepRepository;
    private final StepProgressRepository stepProgressRepository;
    private final ExecutionEventBus      eventBus;

    public AdaptiveRoadmapEngine(RoadmapStepRepository roadmapStepRepository,
                                 StepProgressRepository stepProgressRepository,
                                 ExecutionEventBus eventBus) {
        this.roadmapStepRepository  = roadmapStepRepository;
        this.stepProgressRepository = stepProgressRepository;
        this.eventBus               = eventBus;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adaptation analysis
    // ─────────────────────────────────────────────────────────────────────────

    public AdaptationReport analyzeAndAdapt(Long roadmapId, Long taskId, int totalSteps) {
        AdaptationReport report = new AdaptationReport(roadmapId, taskId);

        List<StepProgress> progress = stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId);
        List<RoadmapStep>  steps    = roadmapStepRepository.findByRoadmapIdOrderByStepIndexAsc(roadmapId);

        // Detect stuck state
        if (isUserStuck(progress)) {
            report.addSignal(AdaptationSignal.USER_STUCK, "No progress for " + STUCK_THRESHOLD_DAYS + "+ days");
            eventBus.publish(ExecutionEventBus.EventType.USER_STUCK, taskId, -1, "User stuck on roadmap");
        }

        // Detect slow progress
        double weeklyRate = computeWeeklyProgressRate(progress, totalSteps);
        if (weeklyRate < SLOW_PROGRESS_THRESHOLD && !progress.isEmpty()) {
            report.addSignal(AdaptationSignal.SLOW_PROGRESS,
                    String.format("Weekly progress rate: %.1f%%", weeklyRate * 100));
        }

        // Detect repeated failures
        Map<Integer, Integer> failureCounts = countFailures(progress);
        for (Map.Entry<Integer, Integer> entry : failureCounts.entrySet()) {
            if (entry.getValue() >= FAILURE_THRESHOLD) {
                report.addSignal(AdaptationSignal.REPEATED_FAILURE,
                        "Step " + entry.getKey() + " failed " + entry.getValue() + " times");
                report.stepsNeedingReinforcement.add(entry.getKey());
            }
        }

        // Generate recommendations
        report.recommendations = generateRecommendations(report, steps, progress);

        // Generate mutation suggestions (not auto-applied — caller decides)
        report.mutations = generateMutations(report, steps, progress);

        log.info("[adaptive] roadmap={} signals={} mutations={}", roadmapId,
                report.signals.size(), report.mutations.size());
        return report;
    }

    /**
     * Injects a reinforcement step before a struggling step.
     * Returns the new step's index.
     */
    @Transactional
    public int injectReinforcementStep(Long roadmapId, int beforeStepIndex, String reinforcementTitle) {
        List<RoadmapStep> steps = roadmapStepRepository.findByRoadmapIdOrderByStepIndexAsc(roadmapId);

        // Shift all steps at or after beforeStepIndex up by 1
        for (RoadmapStep step : steps) {
            if (step.getStepIndex() >= beforeStepIndex) {
                step.setStepIndex(step.getStepIndex() + 1);
                roadmapStepRepository.save(step);
            }
        }

        // Find the roadmap reference from any existing step
        if (steps.isEmpty()) {
            throw new RuntimeException("No steps found for roadmap: " + roadmapId);
        }
        com.assistant.model.Roadmap roadmap = steps.get(0).getRoadmap();

        // Insert new reinforcement step
        RoadmapStep newStep = new RoadmapStep(roadmap, beforeStepIndex, reinforcementTitle,
                "Reinforcement step to strengthen foundational understanding before proceeding.");
        newStep.setPriority(RoadmapStep.Priority.HIGH);
        roadmapStepRepository.save(newStep);

        eventBus.publish(ExecutionEventBus.EventType.ROADMAP_MUTATED,
                roadmap.getTask() != null ? roadmap.getTask().getId() : -1L,
                beforeStepIndex, "Injected reinforcement: " + reinforcementTitle);

        log.info("[adaptive] Injected reinforcement step at index {} in roadmap {}", beforeStepIndex, roadmapId);
        return beforeStepIndex;
    }

    /**
     * Reprioritizes a step.
     */
    @Transactional
    public void reprioritizeStep(Long roadmapId, int stepIndex, RoadmapStep.Priority newPriority) {
        List<RoadmapStep> steps = roadmapStepRepository.findByRoadmapIdOrderByStepIndexAsc(roadmapId);
        steps.stream()
                .filter(s -> s.getStepIndex().equals(stepIndex))
                .findFirst()
                .ifPresent(s -> {
                    s.setPriority(newPriority);
                    roadmapStepRepository.save(s);
                    log.info("[adaptive] Reprioritized step {} to {} in roadmap {}", stepIndex, newPriority, roadmapId);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isUserStuck(List<StepProgress> progress) {
        if (progress.isEmpty()) return false;
        LocalDateTime threshold = LocalDateTime.now().minusDays(STUCK_THRESHOLD_DAYS);
        // Stuck if last update was before threshold and nothing is completed recently
        return progress.stream()
                .filter(p -> p.getUpdatedAt() != null)
                .allMatch(p -> p.getUpdatedAt().isBefore(threshold));
    }

    private double computeWeeklyProgressRate(List<StepProgress> progress, int totalSteps) {
        if (totalSteps == 0) return 0.0;
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long recentCompletions = progress.stream()
                .filter(p -> p.isCompleted() && p.getUpdatedAt() != null && p.getUpdatedAt().isAfter(weekAgo))
                .count();
        return (double) recentCompletions / totalSteps;
    }

    private Map<Integer, Integer> countFailures(List<StepProgress> progress) {
        Map<Integer, Integer> failures = new HashMap<>();
        for (StepProgress p : progress) {
            if (p.getNote() != null && p.getNote().contains("[FAILED]")) {
                failures.merge(p.getStepIndex(), 1, Integer::sum);
            }
        }
        return failures;
    }

    private List<String> generateRecommendations(AdaptationReport report,
                                                  List<RoadmapStep> steps,
                                                  List<StepProgress> progress) {
        List<String> recs = new ArrayList<>();

        if (report.hasSignal(AdaptationSignal.USER_STUCK)) {
            recs.add("You've been stuck for a few days. Try breaking the next step into smaller tasks.");
            recs.add("Consider reviewing prerequisites before continuing.");
        }

        if (report.hasSignal(AdaptationSignal.SLOW_PROGRESS)) {
            recs.add("Your progress has slowed. Set a daily 30-minute practice goal.");
        }

        if (!report.stepsNeedingReinforcement.isEmpty()) {
            for (int stepIdx : report.stepsNeedingReinforcement) {
                String stepTitle = steps.stream()
                        .filter(s -> s.getStepIndex().equals(stepIdx))
                        .map(RoadmapStep::getTitle)
                        .findFirst()
                        .orElse("Step " + stepIdx);
                recs.add("You're struggling with: \"" + stepTitle + "\". A reinforcement exercise has been suggested.");
            }
        }

        return recs;
    }

    private List<RoadmapMutation> generateMutations(AdaptationReport report,
                                                     List<RoadmapStep> steps,
                                                     List<StepProgress> progress) {
        List<RoadmapMutation> mutations = new ArrayList<>();

        // Suggest reinforcement injections for failing steps
        for (int stepIdx : report.stepsNeedingReinforcement) {
            String stepTitle = steps.stream()
                    .filter(s -> s.getStepIndex().equals(stepIdx))
                    .map(RoadmapStep::getTitle)
                    .findFirst()
                    .orElse("Step " + stepIdx);

            String reinforcementTitle = "Fundamentals review: " + extractCoreConcept(stepTitle);
            mutations.add(new RoadmapMutation(
                    MutationType.INJECT_REINFORCEMENT, stepIdx, reinforcementTitle,
                    "Inject foundational review before step " + stepIdx));
        }

        // Suggest priority boost for blocked steps
        if (report.hasSignal(AdaptationSignal.USER_STUCK)) {
            // Find the first incomplete step and suggest boosting it
            progress.stream()
                    .filter(p -> !p.isCompleted())
                    .min(Comparator.comparingInt(StepProgress::getStepIndex))
                    .ifPresent(p -> mutations.add(new RoadmapMutation(
                            MutationType.REPRIORITIZE, p.getStepIndex(), null,
                            "Boost priority of next step to CRITICAL")));
        }

        return mutations;
    }

    private String extractCoreConcept(String stepTitle) {
        // Extract first meaningful noun phrase (simple heuristic)
        String[] words = stepTitle.split("\\s+");
        if (words.length <= 3) return stepTitle;
        // Take first 3 words as the concept
        return String.join(" ", Arrays.copyOfRange(words, 0, Math.min(3, words.length)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public enum AdaptationSignal {
        USER_STUCK, SLOW_PROGRESS, REPEATED_FAILURE, SKILL_GAP, FAST_PROGRESS
    }

    public enum MutationType {
        INJECT_REINFORCEMENT, REPRIORITIZE, SIMPLIFY, SPLIT, SKIP_BASICS
    }

    public static class RoadmapMutation {
        public final MutationType type;
        public final int          targetStepIndex;
        public final String       newContent;
        public final String       rationale;

        public RoadmapMutation(MutationType type, int targetStepIndex, String newContent, String rationale) {
            this.type            = type;
            this.targetStepIndex = targetStepIndex;
            this.newContent      = newContent;
            this.rationale       = rationale;
        }
    }

    public static class AdaptationReport {
        public final Long roadmapId;
        public final Long taskId;
        public final List<Map.Entry<AdaptationSignal, String>> signals = new ArrayList<>();
        public final List<Integer>       stepsNeedingReinforcement = new ArrayList<>();
        public       List<String>        recommendations           = new ArrayList<>();
        public       List<RoadmapMutation> mutations               = new ArrayList<>();

        public AdaptationReport(Long roadmapId, Long taskId) {
            this.roadmapId = roadmapId;
            this.taskId    = taskId;
        }

        public void addSignal(AdaptationSignal signal, String detail) {
            signals.add(Map.entry(signal, detail));
        }

        public boolean hasSignal(AdaptationSignal signal) {
            return signals.stream().anyMatch(e -> e.getKey() == signal);
        }
    }
}
