package com.assistant.service;

import com.assistant.model.RoadmapStep;
import com.assistant.model.StepProgress;
import com.assistant.repository.RoadmapStepRepository;
import com.assistant.repository.StepProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4 — Dynamic Reprioritization Engine
 *
 * Scores each step by:
 *   - Urgency (how long it's been waiting)
 *   - Blocker impact (how many steps it unblocks)
 *   - Learning ROI (difficulty vs. value)
 *   - Dependency importance (critical path position)
 *   - Execution momentum (recent activity)
 *
 * Returns a prioritized execution order.
 */
@Service
public class PriorityAdaptationEngine {

    private static final Logger log = LoggerFactory.getLogger(PriorityAdaptationEngine.class);

    private final RoadmapStepRepository  roadmapStepRepository;
    private final StepProgressRepository stepProgressRepository;

    public PriorityAdaptationEngine(RoadmapStepRepository roadmapStepRepository,
                                    StepProgressRepository stepProgressRepository) {
        this.roadmapStepRepository  = roadmapStepRepository;
        this.stepProgressRepository = stepProgressRepository;
    }

    public List<PrioritizedStep> computePriority(Long roadmapId, Long taskId) {
        List<RoadmapStep>  steps    = roadmapStepRepository.findByRoadmapIdOrderByStepIndexAsc(roadmapId);
        List<StepProgress> progress = stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId);

        Map<Integer, StepProgress> progressMap = progress.stream()
                .collect(Collectors.toMap(StepProgress::getStepIndex, p -> p));

        LocalDateTime now = LocalDateTime.now();
        List<PrioritizedStep> scored = new ArrayList<>();

        for (RoadmapStep step : steps) {
            StepProgress p = progressMap.get(step.getStepIndex());
            if (p != null && p.isCompleted()) continue; // skip completed

            double score = 0.0;

            // 1. Urgency: how long since last touched
            if (p != null && p.getUpdatedAt() != null) {
                long daysSince = java.time.Duration.between(p.getUpdatedAt(), now).toDays();
                score += Math.min(0.3, daysSince * 0.03); // up to 0.3 for 10+ days
            } else {
                score += 0.1; // never touched = moderate urgency
            }

            // 2. Blocker impact: steps that depend on this one
            long blockerImpact = steps.stream()
                    .filter(s -> s.getStepIndex() > step.getStepIndex())
                    .filter(s -> {
                        StepProgress sp = progressMap.get(s.getStepIndex());
                        return sp == null || !sp.isCompleted();
                    })
                    .count();
            score += Math.min(0.3, blockerImpact * 0.05);

            // 3. Priority from entity
            if (step.getPriority() != null) {
                score += switch (step.getPriority()) {
                    case CRITICAL -> 0.3;
                    case HIGH     -> 0.2;
                    case MEDIUM   -> 0.1;
                    case LOW      -> 0.0;
                };
            }

            // 4. Execution momentum: in-progress steps get a boost
            if (p != null && p.getNote() != null && p.getNote().contains("[IN_PROGRESS]")) {
                score += 0.2;
            }

            // 5. Critical path position: earlier steps score higher
            double positionScore = 1.0 - ((double) step.getStepIndex() / Math.max(1, steps.size()));
            score += positionScore * 0.1;

            scored.add(new PrioritizedStep(step.getStepIndex(), step.getTitle(), score,
                    step.getPriority() != null ? step.getPriority().name() : "MEDIUM",
                    p != null && p.getNote() != null && p.getNote().contains("[IN_PROGRESS]")));
        }

        // Sort by score descending
        scored.sort(Comparator.comparingDouble(PrioritizedStep::score).reversed());

        log.debug("[priority] Computed priority for roadmap={} — top step: {}",
                roadmapId, scored.isEmpty() ? "none" : scored.get(0).stepIndex());
        return scored;
    }

    public record PrioritizedStep(int stepIndex, String title, double score,
                                  String priority, boolean inProgress) {}
}
