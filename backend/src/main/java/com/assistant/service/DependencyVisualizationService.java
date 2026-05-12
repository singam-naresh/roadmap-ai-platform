package com.assistant.service;

import com.assistant.service.TaskDependencyGraphEngine.DependencyGraph;
import com.assistant.service.TaskDependencyGraphEngine.StepNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4.5 — Dependency Graph Visualizer API
 *
 * Converts the internal DependencyGraph into a frontend-ready JSON structure
 * with nodes, edges, blocked relationships, unlocked paths, and critical path.
 *
 * The output is designed for direct consumption by a graph visualization
 * library (e.g., React Flow, D3, Cytoscape).
 */
@Service
public class DependencyVisualizationService {

    private static final Logger log = LoggerFactory.getLogger(DependencyVisualizationService.class);

    private final TaskDependencyGraphEngine dependencyEngine;

    public DependencyVisualizationService(TaskDependencyGraphEngine dependencyEngine) {
        this.dependencyEngine = dependencyEngine;
    }

    public GraphVisualization buildVisualization(Long taskId, int totalSteps,
                                                  List<String> stepTitles) {
        DependencyGraph graph = dependencyEngine.buildGraph(taskId, totalSteps);
        List<Integer> criticalPath = graph.getCriticalPath();
        List<Integer> readySteps   = graph.getReadySteps();
        List<Integer> blockedSteps = graph.getBlockedSteps();
        List<Integer> completedSteps = graph.getCompletedSteps();

        // Build nodes
        List<VisNode> nodes = new ArrayList<>();
        for (StepNode node : graph.getAllNodes()) {
            String title = (stepTitles != null && node.stepIndex < stepTitles.size())
                    ? stepTitles.get(node.stepIndex)
                    : "Step " + (node.stepIndex + 1);

            String status;
            if (node.completed)                        status = "COMPLETED";
            else if (node.blocked)                     status = "BLOCKED";
            else if (readySteps.contains(node.stepIndex)) status = "READY";
            else                                       status = "NOT_STARTED";

            boolean onCriticalPath = criticalPath.contains(node.stepIndex);

            nodes.add(new VisNode(
                    "step-" + node.stepIndex,
                    node.stepIndex,
                    title,
                    status,
                    onCriticalPath,
                    node.dependencies
            ));
        }

        // Build edges (dependency arrows)
        List<VisEdge> edges = new ArrayList<>();
        for (StepNode node : graph.getAllNodes()) {
            for (int dep : node.dependencies) {
                boolean isBlocked = node.blocked;
                boolean isUnlocked = !isBlocked && !node.completed;
                edges.add(new VisEdge(
                        "edge-" + dep + "-" + node.stepIndex,
                        "step-" + dep,
                        "step-" + node.stepIndex,
                        isBlocked ? "BLOCKED" : isUnlocked ? "UNLOCKED" : "COMPLETED",
                        criticalPath.contains(dep) && criticalPath.contains(node.stepIndex)
                ));
            }
        }

        // Build dependency chains (groups of connected steps)
        List<List<Integer>> chains = buildDependencyChains(graph, totalSteps);

        GraphVisualization viz = new GraphVisualization(
                taskId, totalSteps, nodes, edges,
                criticalPath, readySteps, blockedSteps, completedSteps,
                chains,
                completedSteps.size(),
                blockedSteps.size(),
                readySteps.size()
        );

        log.debug("[dep-viz] Built visualization for task={}: {} nodes, {} edges, {} on critical path",
                taskId, nodes.size(), edges.size(), criticalPath.size());
        return viz;
    }

    private List<List<Integer>> buildDependencyChains(DependencyGraph graph, int totalSteps) {
        // For sequential deps, the chain is just the full sequence
        // For more complex graphs, this would do a proper DFS
        List<Integer> chain = new ArrayList<>();
        for (int i = 0; i < totalSteps; i++) {
            chain.add(i);
        }
        return List.of(chain);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes (frontend-ready)
    // ─────────────────────────────────────────────────────────────────────────

    public static class GraphVisualization {
        public final Long              taskId;
        public final int               totalSteps;
        public final List<VisNode>     nodes;
        public final List<VisEdge>     edges;
        public final List<Integer>     criticalPath;
        public final List<Integer>     readySteps;
        public final List<Integer>     blockedSteps;
        public final List<Integer>     completedSteps;
        public final List<List<Integer>> dependencyChains;
        public final int               completedCount;
        public final int               blockedCount;
        public final int               readyCount;

        public GraphVisualization(Long taskId, int totalSteps,
                                  List<VisNode> nodes, List<VisEdge> edges,
                                  List<Integer> criticalPath, List<Integer> readySteps,
                                  List<Integer> blockedSteps, List<Integer> completedSteps,
                                  List<List<Integer>> dependencyChains,
                                  int completedCount, int blockedCount, int readyCount) {
            this.taskId           = taskId;
            this.totalSteps       = totalSteps;
            this.nodes            = nodes;
            this.edges            = edges;
            this.criticalPath     = criticalPath;
            this.readySteps       = readySteps;
            this.blockedSteps     = blockedSteps;
            this.completedSteps   = completedSteps;
            this.dependencyChains = dependencyChains;
            this.completedCount   = completedCount;
            this.blockedCount     = blockedCount;
            this.readyCount       = readyCount;
        }
    }

    public static class VisNode {
        public final String       id;
        public final int          stepIndex;
        public final String       title;
        public final String       status;       // COMPLETED | BLOCKED | READY | NOT_STARTED
        public final boolean      onCriticalPath;
        public final Set<Integer> dependencies;

        public VisNode(String id, int stepIndex, String title, String status,
                       boolean onCriticalPath, Set<Integer> dependencies) {
            this.id             = id;
            this.stepIndex      = stepIndex;
            this.title          = title;
            this.status         = status;
            this.onCriticalPath = onCriticalPath;
            this.dependencies   = dependencies;
        }
    }

    public static class VisEdge {
        public final String  id;
        public final String  source;
        public final String  target;
        public final String  status;         // BLOCKED | UNLOCKED | COMPLETED
        public final boolean onCriticalPath;

        public VisEdge(String id, String source, String target,
                       String status, boolean onCriticalPath) {
            this.id             = id;
            this.source         = source;
            this.target         = target;
            this.status         = status;
            this.onCriticalPath = onCriticalPath;
        }
    }
}
