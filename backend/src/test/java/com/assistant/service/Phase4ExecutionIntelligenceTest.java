package com.assistant.service;

import com.assistant.model.StepProgress;
import com.assistant.repository.RoadmapStepRepository;
import com.assistant.repository.StepProgressRepository;
import com.assistant.service.ExecutionStateEngine.StepState;
import com.assistant.service.ExecutionStateEngine.StepExecutionState;
import com.assistant.service.ExecutionStateEngine.ExecutionSummary;
import com.assistant.service.TaskDependencyGraphEngine.DependencyGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 4 — Execution Intelligence Engine Validation Tests
 *
 * Validates:
 * 1. Blocked tasks remain blocked until dependencies complete
 * 2. Completing prerequisites unlocks downstream tasks
 * 3. State machine transitions work correctly
 * 4. Progress metrics derive from real completion data
 * 5. Execution summary is accurate
 */
@SpringBootTest
@Transactional
public class Phase4ExecutionIntelligenceTest {

    @Autowired private ExecutionStateEngine      stateEngine;
    @Autowired private TaskDependencyGraphEngine dependencyEngine;
    @Autowired private StepProgressRepository    stepProgressRepo;

    // Use a unique task ID per test run to avoid cross-test contamination
    private static final long TEST_TASK_ID = 99_000L;

    @BeforeEach
    void cleanup() {
        stepProgressRepo.deleteByTaskId(TEST_TASK_ID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. State machine transitions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testNotStartedByDefault() {
        StepExecutionState state = stateEngine.getStepState(TEST_TASK_ID, 0);
        assertEquals(StepState.NOT_STARTED, state.state, "New step must be NOT_STARTED");
        System.out.println("✅ Default state is NOT_STARTED");
    }

    @Test
    void testTransitionToInProgress() {
        StepExecutionState state = stateEngine.markInProgress(TEST_TASK_ID, 0);
        assertEquals(StepState.IN_PROGRESS, state.state);
        System.out.println("✅ Transition to IN_PROGRESS works");
    }

    @Test
    void testTransitionToCompleted() {
        stateEngine.markInProgress(TEST_TASK_ID, 0);
        StepExecutionState state = stateEngine.markComplete(TEST_TASK_ID, 0);
        assertEquals(StepState.COMPLETED, state.state);
        System.out.println("✅ Transition to COMPLETED works");
    }

    @Test
    void testTransitionToBlocked() {
        StepExecutionState state = stateEngine.markBlocked(TEST_TASK_ID, 1, "Step 0 not done");
        assertEquals(StepState.BLOCKED, state.state);
        assertNotNull(state.blocker);
        assertTrue(state.blocker.contains("Step 0 not done"));
        System.out.println("✅ Transition to BLOCKED with blocker message works");
    }

    @Test
    void testMarkIncompleteResetsState() {
        stateEngine.markComplete(TEST_TASK_ID, 0);
        stateEngine.markIncomplete(TEST_TASK_ID, 0);
        StepExecutionState state = stateEngine.getStepState(TEST_TASK_ID, 0);
        assertNotEquals(StepState.COMPLETED, state.state);
        System.out.println("✅ markIncomplete resets COMPLETED state");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Dependency graph — blocked until prerequisites complete
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testStep1BlockedUntilStep0Complete() {
        // With 3 steps and no completions, step 1 and 2 should be blocked
        DependencyGraph graph = dependencyEngine.buildGraph(TEST_TASK_ID, 3);

        List<Integer> blocked = graph.getBlockedSteps();
        assertTrue(blocked.contains(1), "Step 1 must be blocked when step 0 is not done");
        assertTrue(blocked.contains(2), "Step 2 must be blocked when step 1 is not done");
        System.out.println("✅ Steps 1 and 2 are blocked when step 0 is incomplete");
    }

    @Test
    void testStep0IsReadyWithNoDependencies() {
        DependencyGraph graph = dependencyEngine.buildGraph(TEST_TASK_ID, 3);
        List<Integer> ready = graph.getReadySteps();
        assertTrue(ready.contains(0), "Step 0 must be ready (no dependencies)");
        System.out.println("✅ Step 0 is ready with no dependencies");
    }

    @Test
    void testCompletingStep0UnlocksStep1() {
        // Complete step 0
        stateEngine.markComplete(TEST_TASK_ID, 0);

        List<Integer> unlocked = dependencyEngine.getUnlockedByCompletion(TEST_TASK_ID, 0, 3);
        assertTrue(unlocked.contains(1), "Completing step 0 must unlock step 1");
        System.out.println("✅ Completing step 0 unlocks step 1: " + unlocked);
    }

    @Test
    void testStep1ReadyAfterStep0Complete() {
        // Mark step 0 complete in DB
        StepProgress p = new StepProgress();
        p.setTaskId(TEST_TASK_ID);
        p.setStepIndex(0);
        p.setCompleted(true);
        stepProgressRepo.save(p);

        DependencyGraph graph = dependencyEngine.buildGraph(TEST_TASK_ID, 3);
        List<Integer> ready = graph.getReadySteps();
        assertTrue(ready.contains(1), "Step 1 must be ready after step 0 is complete");
        assertFalse(ready.contains(0), "Step 0 must not be in ready list (already done)");
        System.out.println("✅ Step 1 becomes ready after step 0 completes");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Real progress calculation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testProgressIsZeroWithNoCompletions() {
        ExecutionSummary summary = stateEngine.getExecutionSummary(TEST_TASK_ID, 5);
        assertEquals(0, summary.progressPercentage, "Progress must be 0 with no completions");
        System.out.println("✅ Progress is 0 with no completions");
    }

    @Test
    void testProgressReflectsActualCompletions() {
        // Complete 2 out of 4 steps
        stateEngine.markComplete(TEST_TASK_ID, 0);
        stateEngine.markComplete(TEST_TASK_ID, 1);

        ExecutionSummary summary = stateEngine.getExecutionSummary(TEST_TASK_ID, 4);
        assertEquals(2, summary.completedSteps);
        assertTrue(summary.progressPercentage >= 50, "Progress must be >= 50% with 2/4 done");
        System.out.println("✅ Progress reflects actual completions: " + summary.progressPercentage + "%");
    }

    @Test
    void testProgressPersistsAfterReload() {
        // Complete step 0
        stateEngine.markComplete(TEST_TASK_ID, 0);

        // Reload from DB (simulate page refresh)
        List<StepExecutionState> states = stateEngine.getAllStepStates(TEST_TASK_ID);
        boolean step0Completed = states.stream()
                .filter(s -> s.stepIndex == 0)
                .anyMatch(s -> s.state == StepState.COMPLETED);
        assertTrue(step0Completed, "Step 0 completion must persist after reload");
        System.out.println("✅ Progress persists after reload");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Execution summary accuracy
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testExecutionSummaryCountsCorrectly() {
        stateEngine.markComplete(TEST_TASK_ID, 0);
        stateEngine.markInProgress(TEST_TASK_ID, 1);
        stateEngine.markBlocked(TEST_TASK_ID, 2, "Waiting for step 1");

        ExecutionSummary summary = stateEngine.getExecutionSummary(TEST_TASK_ID, 5);

        assertEquals(1, summary.completedSteps,  "Must count 1 completed");
        assertEquals(1, summary.inProgressSteps, "Must count 1 in-progress");
        assertEquals(1, summary.blockedSteps,     "Must count 1 blocked");
        assertEquals(5, summary.totalSteps,       "Total must match");
        System.out.println("✅ Execution summary counts correctly: " + summary.completedSteps + " done, "
                + summary.inProgressSteps + " in-progress, " + summary.blockedSteps + " blocked");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Critical path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testCriticalPathExcludesCompletedSteps() {
        // Complete step 0
        StepProgress p = new StepProgress();
        p.setTaskId(TEST_TASK_ID);
        p.setStepIndex(0);
        p.setCompleted(true);
        stepProgressRepo.save(p);

        DependencyGraph graph = dependencyEngine.buildGraph(TEST_TASK_ID, 3);
        List<Integer> criticalPath = graph.getCriticalPath();

        assertFalse(criticalPath.contains(0), "Critical path must not include completed step 0");
        assertTrue(criticalPath.contains(1), "Critical path must include incomplete step 1");
        System.out.println("✅ Critical path excludes completed steps: " + criticalPath);
    }
}
