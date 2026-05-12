package com.assistant.service;

import com.assistant.model.StepProgress;
import com.assistant.repository.StepProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4 — Task Dependency Execution Graph
 *
 * Tracks prerequisite relationships between roadmap steps.
 * Determines which steps are blocked, which are ready to start,
 * and computes the critical path through the roadmap.
 *
 * Dependencies are inferred from step ordering by default (step N
 * depends on step N-1) but can be overridden with explicit rules.
 */
@Service
public class TaskDependencyGraphEngine {

    private static final Logger log = LoggerFactory.getLogger(TaskDependencyGraphEngine.class);

    private final StepProgressRepository stepProgressRepository;

    public TaskDependencyGraphEngine(StepProgressRepository stepProgressRepository) {
        this.stepProgressRepository = stepProgressRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dependency analysis
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a dependency graph for a task's steps.
     * Default rule: each step depends on the previous one (sequential).
     * Steps with no dependencies are immediately available.
     */
    public DependencyGraph buildGraph(Long taskId, int totalSteps) {
        List<StepProgress> progress = stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId);
        Map<Integer, Boolean> completionMap = progress.stream()
                .collect(Collectors.toMap(StepProgress::getStepIndex, StepProgress::isCompleted));

        DependencyGraph graph = new DependencyGraph(taskId, totalSteps);

        for (int i = 0; i < totalSteps; i++) {
            boolean completed = completionMap.getOrDefault(i, false);
            Set<Integer> deps = i == 0 ? Set.of() : Set.of(i - 1); // sequential default

            boolean depsComplete = deps.stream().allMatch(d -> completionMap.getOrDefault(d, false));
            boolean blocked = !depsComplete && !completed;

            graph.addNode(new StepNode(i, completed, blocked, deps, depsComplete));
        }

        graph.computeCriticalPath();
        log.debug("[dep-graph] Built graph for task={} steps={} blocked={} ready={}",
                taskId, totalSteps, graph.blockedCount(), graph.readyCount());
        return graph;
    }

    /**
     * Returns the indices of steps that are ready to start
     * (all dependencies completed, not yet completed themselves).
     */
    public List<Integer> getReadySteps(Long taskId, int totalSteps) {
        return buildGraph(taskId, totalSteps).getReadySteps();
    }

    /**
     * Returns the indices of steps that are blocked.
     */
    public List<Integer> getBlockedSteps(Long taskId, int totalSteps) {
        return buildGraph(taskId, totalSteps).getBlockedSteps();
    }

    /**
     * Returns steps that can run in parallel (no dependency between them).
     */
    public List<List<Integer>> getParallelizableGroups(Long taskId, int totalSteps) {
        DependencyGraph graph = buildGraph(taskId, totalSteps);
        // Group ready steps that don't depend on each other
        List<Integer> ready = graph.getReadySteps();
        // For sequential default, only one step is ready at a time
        // With custom deps, multiple independent steps can be grouped
        List<List<Integer>> groups = new ArrayList<>();
        if (!ready.isEmpty()) {
            groups.add(ready);
        }
        return groups;
    }

    /**
     * Checks whether completing a step unlocks any downstream steps.
     * Returns the list of newly-unblocked step indices.
     */
    public List<Integer> getUnlockedByCompletion(Long taskId, int completedStepIndex, int totalSteps) {
        // After marking completedStepIndex done, which steps become unblocked?
        List<StepProgress> progress = stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId);
        Map<Integer, Boolean> completionMap = new HashMap<>();
        for (StepProgress p : progress) {
            completionMap.put(p.getStepIndex(), p.isCompleted());
        }
        // Simulate the completion
        completionMap.put(completedStepIndex, true);

        List<Integer> unlocked = new ArrayList<>();
        for (int i = 0; i < totalSteps; i++) {
            if (completionMap.getOrDefault(i, false)) continue; // already done
            Set<Integer> deps = i == 0 ? Set.of() : Set.of(i - 1);
            boolean wasBlocked = !deps.stream().allMatch(d -> {
                Map<Integer, Boolean> before = new HashMap<>(completionMap);
                before.put(completedStepIndex, false); // revert
                return before.getOrDefault(d, false);
            });
            boolean nowUnblocked = deps.stream().allMatch(d -> completionMap.getOrDefault(d, false));
            if (wasBlocked && nowUnblocked) {
                unlocked.add(i);
            }
        }

        if (!unlocked.isEmpty()) {
            log.info("[dep-graph] Completing step {} unlocks steps {} for task={}", completedStepIndex, unlocked, taskId);
        }
        return unlocked;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class StepNode {
        public final int         stepIndex;
        public final boolean     completed;
        public final boolean     blocked;
        public final Set<Integer> dependencies;
        public final boolean     dependenciesComplete;

        public StepNode(int stepIndex, boolean completed, boolean blocked,
                        Set<Integer> dependencies, boolean dependenciesComplete) {
            this.stepIndex            = stepIndex;
            this.completed            = completed;
            this.blocked              = blocked;
            this.dependencies         = dependencies;
            this.dependenciesComplete = dependenciesComplete;
        }

        public boolean isReady() {
            return !completed && !blocked && dependenciesComplete;
        }
    }

    public static class DependencyGraph {
        public final Long taskId;
        public final int  totalSteps;
        private final List<StepNode> nodes = new ArrayList<>();
        private List<Integer> criticalPath = new ArrayList<>();

        public DependencyGraph(Long taskId, int totalSteps) {
            this.taskId     = taskId;
            this.totalSteps = totalSteps;
        }

        public void addNode(StepNode node) { nodes.add(node); }

        public List<Integer> getReadySteps() {
            return nodes.stream().filter(StepNode::isReady)
                    .map(n -> n.stepIndex).collect(Collectors.toList());
        }

        public List<Integer> getBlockedSteps() {
            return nodes.stream().filter(n -> n.blocked)
                    .map(n -> n.stepIndex).collect(Collectors.toList());
        }

        public List<Integer> getCompletedSteps() {
            return nodes.stream().filter(n -> n.completed)
                    .map(n -> n.stepIndex).collect(Collectors.toList());
        }

        public int blockedCount() { return getBlockedSteps().size(); }
        public int readyCount()   { return getReadySteps().size(); }

        public List<Integer> getCriticalPath() { return criticalPath; }

        /** Critical path = longest chain of incomplete steps */
        public void computeCriticalPath() {
            // For sequential deps, critical path = all incomplete steps in order
            criticalPath = nodes.stream()
                    .filter(n -> !n.completed)
                    .map(n -> n.stepIndex)
                    .sorted()
                    .collect(Collectors.toList());
        }

        public Optional<StepNode> getNode(int stepIndex) {
            return nodes.stream().filter(n -> n.stepIndex == stepIndex).findFirst();
        }

        public List<StepNode> getAllNodes() { return Collections.unmodifiableList(nodes); }
    }
}
