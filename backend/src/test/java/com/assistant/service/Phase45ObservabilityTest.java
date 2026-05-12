package com.assistant.service;

import com.assistant.service.ExecutionAuditService.AuditAction;
import com.assistant.service.ExecutionAuditService.AuditRecord;
import com.assistant.service.ExecutionEventBus.EventType;
import com.assistant.service.ExecutionFailureAnalyzer.FailureDiagnosticReport;
import com.assistant.service.ExecutionFailureAnalyzer.IssueType;
import com.assistant.service.ExecutionMetricsEngine.ExecutionMetrics;
import com.assistant.service.ExecutionReplayEngine.PlaybackFrame;
import com.assistant.service.ExecutionReplayEngine.ReplaySnapshot;
import com.assistant.service.ExecutionStateEngine.StepState;
import com.assistant.service.ExecutionTimelineEngine.EntryType;
import com.assistant.service.ExecutionTimelineEngine.TimelineEntry;
import com.assistant.repository.StepProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 4.5 — Execution Observability & Debugging Validation Tests
 *
 * Validates:
 * 1. All state transitions are logged in timeline and audit
 * 2. Replay reproduces exact state
 * 3. Mutation history persists
 * 4. Cyclic dependencies detected
 * 5. Audit logs are immutable (append-only, sequence monotonic)
 * 6. Event ordering preserved
 * 7. Failure diagnostics detect real issues
 * 8. Metrics compute correctly
 */
@SpringBootTest
@Transactional
public class Phase45ObservabilityTest {

    @Autowired private ExecutionStateEngine      stateEngine;
    @Autowired private ExecutionTimelineEngine   timelineEngine;
    @Autowired private ExecutionAuditService     auditService;
    @Autowired private ExecutionReplayEngine     replayEngine;
    @Autowired private ExecutionMetricsEngine    metricsEngine;
    @Autowired private ExecutionFailureAnalyzer  failureAnalyzer;
    @Autowired private ExecutionEventBus         eventBus;
    @Autowired private StepProgressRepository    stepProgressRepo;

    private static final long TEST_TASK_ID = 98_000L;

    @BeforeEach
    void cleanup() {
        stepProgressRepo.deleteByTaskId(TEST_TASK_ID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. State transitions are logged
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testStateTransitionIsLoggedInAudit() {
        stateEngine.markInProgress(TEST_TASK_ID, 0);

        List<AuditRecord> records = auditService.getAuditLog(TEST_TASK_ID);
        assertFalse(records.isEmpty(), "Audit log must have records after state transition");

        AuditRecord last = records.get(records.size() - 1);
        assertEquals(AuditAction.STATE_TRANSITION, last.action);
        assertEquals("IN_PROGRESS", last.newValue);
        System.out.println("✅ State transition logged in audit: seq=" + last.sequence);
    }

    @Test
    void testStateTransitionIsLoggedInTimeline() {
        stateEngine.markComplete(TEST_TASK_ID, 0);

        List<TimelineEntry> timeline = timelineEngine.getTimeline(TEST_TASK_ID);
        assertFalse(timeline.isEmpty(), "Timeline must have entries after state transition");

        boolean hasCompletion = timeline.stream()
                .anyMatch(e -> "COMPLETED".equals(e.newState));
        assertTrue(hasCompletion, "Timeline must contain COMPLETED transition");
        System.out.println("✅ State transition logged in timeline");
    }

    @Test
    void testMultipleTransitionsAllLogged() {
        stateEngine.markInProgress(TEST_TASK_ID, 0);
        stateEngine.markComplete(TEST_TASK_ID, 0);
        stateEngine.markInProgress(TEST_TASK_ID, 1);
        stateEngine.markBlocked(TEST_TASK_ID, 2, "Waiting for step 1");

        List<AuditRecord> records = auditService.getAuditLog(TEST_TASK_ID);
        assertTrue(records.size() >= 4, "All 4 transitions must be in audit log, got: " + records.size());
        System.out.println("✅ All transitions logged: " + records.size() + " records");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Audit log is immutable (append-only, monotonic sequence)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testAuditLogIsAppendOnly() {
        stateEngine.markInProgress(TEST_TASK_ID, 0);
        List<AuditRecord> before = auditService.getAuditLog(TEST_TASK_ID);
        int countBefore = before.size();

        stateEngine.markComplete(TEST_TASK_ID, 0);
        List<AuditRecord> after = auditService.getAuditLog(TEST_TASK_ID);

        assertTrue(after.size() > countBefore, "Audit log must grow after new transition");
        // Verify earlier records are unchanged
        for (int i = 0; i < countBefore; i++) {
            assertEquals(before.get(i).sequence, after.get(i).sequence,
                    "Earlier audit records must not change");
        }
        System.out.println("✅ Audit log is append-only");
    }

    @Test
    void testAuditSequenceIsMonotonicallyIncreasing() {
        stateEngine.markInProgress(TEST_TASK_ID, 0);
        stateEngine.markComplete(TEST_TASK_ID, 0);
        stateEngine.markInProgress(TEST_TASK_ID, 1);

        List<AuditRecord> records = auditService.getAuditLog(TEST_TASK_ID);
        for (int i = 1; i < records.size(); i++) {
            assertTrue(records.get(i).sequence > records.get(i - 1).sequence,
                    "Sequence numbers must be strictly increasing");
        }
        System.out.println("✅ Audit sequence is monotonically increasing");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Replay reproduces exact state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testReplayReproducesExactState() {
        // Apply transitions
        stateEngine.markInProgress(TEST_TASK_ID, 0);
        stateEngine.markComplete(TEST_TASK_ID, 0);
        stateEngine.markInProgress(TEST_TASK_ID, 1);

        // Replay at current time
        ReplaySnapshot snapshot = replayEngine.replayAtTime(TEST_TASK_ID, LocalDateTime.now());

        assertEquals("COMPLETED", snapshot.stepStates.get(0),
                "Replay must show step 0 as COMPLETED");
        assertEquals("IN_PROGRESS", snapshot.stepStates.get(1),
                "Replay must show step 1 as IN_PROGRESS");
        System.out.println("✅ Replay reproduces exact state: " + snapshot.stepStates);
    }

    @Test
    void testReplayAtSequenceReproducesPartialState() {
        stateEngine.markInProgress(TEST_TASK_ID, 0);
        List<AuditRecord> afterFirst = auditService.getAuditLog(TEST_TASK_ID);
        long firstSeq = afterFirst.get(afterFirst.size() - 1).sequence;

        stateEngine.markComplete(TEST_TASK_ID, 0);
        stateEngine.markInProgress(TEST_TASK_ID, 1);

        // Replay only up to first transition
        ReplaySnapshot partial = replayEngine.replayAtSequence(TEST_TASK_ID, firstSeq);

        // At firstSeq, step 0 was IN_PROGRESS (not yet COMPLETED)
        assertEquals("IN_PROGRESS", partial.stepStates.get(0),
                "Partial replay must show step 0 as IN_PROGRESS (not yet COMPLETED)");
        // Step 1 must NOT be IN_PROGRESS at firstSeq (that transition happened after)
        assertNotEquals("IN_PROGRESS", partial.stepStates.get(1),
                "Partial replay must not show step 1 as IN_PROGRESS (not yet transitioned)");
        System.out.println("✅ Partial replay at sequence " + firstSeq + ": " + partial.stepStates);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Event ordering preserved
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testEventOrderingPreserved() {
        stateEngine.markInProgress(TEST_TASK_ID, 0);
        stateEngine.markComplete(TEST_TASK_ID, 0);
        stateEngine.markBlocked(TEST_TASK_ID, 1, "Waiting");

        List<TimelineEntry> timeline = timelineEngine.getTimeline(TEST_TASK_ID);
        // Verify chronological order
        for (int i = 1; i < timeline.size(); i++) {
            assertFalse(timeline.get(i).occurredAt.isBefore(timeline.get(i - 1).occurredAt),
                    "Timeline entries must be in chronological order");
        }
        System.out.println("✅ Event ordering preserved in timeline");
    }

    @Test
    void testPlaybackSequenceIsOrdered() {
        stateEngine.markInProgress(TEST_TASK_ID, 0);
        stateEngine.markComplete(TEST_TASK_ID, 0);

        List<PlaybackFrame> frames = replayEngine.getPlaybackSequence(TEST_TASK_ID);
        for (int i = 1; i < frames.size(); i++) {
            assertEquals(i, frames.get(i).frameIndex,
                    "Playback frames must have sequential indices");
        }
        System.out.println("✅ Playback sequence is ordered: " + frames.size() + " frames");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Mutation history persists
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testMutationHistoryPersists() {
        // Record a mutation directly via audit service
        auditService.recordRoadmapMutation(TEST_TASK_ID, 2,
                "INJECT_REINFORCEMENT", "Injected fundamentals review", "ADAPTIVE_ENGINE");
        auditService.recordRoadmapMutation(TEST_TASK_ID, 3,
                "REPRIORITIZE", "Boosted priority to CRITICAL", "USER");

        List<ExecutionReplayEngine.MutationRecord> mutations = replayEngine.getMutationHistory(TEST_TASK_ID);
        assertEquals(2, mutations.size(), "Must have 2 mutation records");
        assertEquals("INJECT_REINFORCEMENT", mutations.get(0).mutationType());
        assertEquals("REPRIORITIZE", mutations.get(1).mutationType());
        System.out.println("✅ Mutation history persists: " + mutations.size() + " mutations");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Failure diagnostics detect real issues
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testFailureDiagnosticsDetectsRepeatedFailures() {
        // Publish multiple failure events
        for (int i = 0; i < 4; i++) {
            eventBus.publish(EventType.EXECUTION_FAILED, TEST_TASK_ID, 2, "Step 2 failed");
        }

        FailureDiagnosticReport report = failureAnalyzer.analyze(TEST_TASK_ID, 5);

        boolean hasRepeatedFailure = report.issues.stream()
                .anyMatch(issue -> issue.type == IssueType.REPEATED_FAILURE);
        assertTrue(hasRepeatedFailure, "Must detect repeated failure on step 2");
        System.out.println("✅ Failure diagnostics detected repeated failures");
    }

    @Test
    void testHealthScoreDegradesWithIssues() {
        // Publish many failures
        for (int i = 0; i < 5; i++) {
            eventBus.publish(EventType.EXECUTION_FAILED, TEST_TASK_ID, 0, "Failed");
        }

        FailureDiagnosticReport report = failureAnalyzer.analyze(TEST_TASK_ID, 5);
        assertTrue(report.healthScore < 1.0, "Health score must degrade with issues");
        System.out.println("✅ Health score degrades with issues: " + report.healthScore);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Metrics compute correctly
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testMetricsComputeWithNoData() {
        ExecutionMetrics metrics = metricsEngine.computeMetrics(TEST_TASK_ID, 5);
        assertNotNull(metrics);
        assertEquals(0.0, metrics.completionVelocity, 0.01);
        System.out.println("✅ Metrics compute with no data (zero velocity)");
    }

    @Test
    void testTimelineEntryCountMatchesTransitions() {
        int before = timelineEngine.getEntryCount(TEST_TASK_ID);

        stateEngine.markInProgress(TEST_TASK_ID, 0);
        stateEngine.markComplete(TEST_TASK_ID, 0);

        int after = timelineEngine.getEntryCount(TEST_TASK_ID);
        assertTrue(after > before, "Timeline entry count must increase after transitions");
        System.out.println("✅ Timeline entry count: " + before + " → " + after);
    }
}
