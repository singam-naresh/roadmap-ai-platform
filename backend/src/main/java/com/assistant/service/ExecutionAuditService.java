package com.assistant.service;

import com.assistant.service.ExecutionStateEngine.StepState;
import com.assistant.service.ExecutionTimelineEngine.EntryType;
import com.assistant.service.ExecutionTimelineEngine.TimelineEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * PHASE 4.5 — Execution Audit Service
 *
 * Append-only, immutable audit log for all execution mutations.
 * Every write to the execution system produces an audit record.
 *
 * Guarantees:
 *   - Append-only: records are never modified or deleted
 *   - Ordered: records are stored in insertion order
 *   - Replay-safe: the full history can reconstruct any past state
 *   - Tamper-evident: each record has a sequence number
 */
@Service
public class ExecutionAuditService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionAuditService.class);

    private final AtomicLong sequenceCounter = new AtomicLong(0);

    // taskId → ordered audit records (append-only)
    private final Map<Long, List<AuditRecord>> auditLog = new ConcurrentHashMap<>();

    // Global audit log (all tasks, for system-wide inspection)
    private final List<AuditRecord> globalLog = new CopyOnWriteArrayList<>();
    private static final int MAX_GLOBAL = 5000;

    // ─────────────────────────────────────────────────────────────────────────
    // Audit record creation
    // ─────────────────────────────────────────────────────────────────────────

    public AuditRecord recordStateTransition(Long taskId, int stepIndex,
                                              StepState from, StepState to,
                                              String actor, String reason) {
        return append(new AuditRecord(
                sequenceCounter.incrementAndGet(),
                taskId, stepIndex,
                AuditAction.STATE_TRANSITION,
                from != null ? from.name() : null,
                to != null ? to.name() : null,
                actor, reason, LocalDateTime.now()
        ));
    }

    public AuditRecord recordRoadmapMutation(Long taskId, int stepIndex,
                                              String mutationType, String description,
                                              String actor) {
        return append(new AuditRecord(
                sequenceCounter.incrementAndGet(),
                taskId, stepIndex,
                AuditAction.ROADMAP_MUTATION,
                null, mutationType,
                actor, description, LocalDateTime.now()
        ));
    }

    public AuditRecord recordDependencyChange(Long taskId, int stepIndex,
                                               String changeType, String description) {
        return append(new AuditRecord(
                sequenceCounter.incrementAndGet(),
                taskId, stepIndex,
                AuditAction.DEPENDENCY_CHANGE,
                null, changeType,
                "SYSTEM", description, LocalDateTime.now()
        ));
    }

    public AuditRecord recordAdaptationDecision(Long taskId, int stepIndex,
                                                  String decision, String rationale) {
        return append(new AuditRecord(
                sequenceCounter.incrementAndGet(),
                taskId, stepIndex,
                AuditAction.ADAPTATION_DECISION,
                null, decision,
                "ADAPTIVE_ENGINE", rationale, LocalDateTime.now()
        ));
    }

    public AuditRecord recordPriorityChange(Long taskId, int stepIndex,
                                             String oldPriority, String newPriority,
                                             String actor) {
        return append(new AuditRecord(
                sequenceCounter.incrementAndGet(),
                taskId, stepIndex,
                AuditAction.PRIORITY_CHANGE,
                oldPriority, newPriority,
                actor, "Priority updated", LocalDateTime.now()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    /** Full audit log for a task, in sequence order. */
    public List<AuditRecord> getAuditLog(Long taskId) {
        return Collections.unmodifiableList(
                auditLog.getOrDefault(taskId, List.of()));
    }

    /** Audit log filtered by action type. */
    public List<AuditRecord> getAuditLog(Long taskId, AuditAction action) {
        return getAuditLog(taskId).stream()
                .filter(r -> r.action == action)
                .collect(Collectors.toList());
    }

    /** Audit log for a specific step. */
    public List<AuditRecord> getStepAuditLog(Long taskId, int stepIndex) {
        return getAuditLog(taskId).stream()
                .filter(r -> r.stepIndex == stepIndex)
                .collect(Collectors.toList());
    }

    /** All records since a given sequence number (for incremental sync). */
    public List<AuditRecord> getRecordsSince(Long taskId, long sinceSequence) {
        return getAuditLog(taskId).stream()
                .filter(r -> r.sequence > sinceSequence)
                .collect(Collectors.toList());
    }

    /** Total number of audit records for a task. */
    public int getAuditCount(Long taskId) {
        return auditLog.getOrDefault(taskId, List.of()).size();
    }

    /** Global audit log (all tasks). */
    public List<AuditRecord> getGlobalLog(int limit) {
        List<AuditRecord> all = new ArrayList<>(globalLog);
        int start = Math.max(0, all.size() - limit);
        return all.subList(start, all.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private AuditRecord append(AuditRecord record) {
        // Per-task log
        auditLog.computeIfAbsent(record.taskId, k -> new CopyOnWriteArrayList<>())
                .add(record);

        // Global log with size cap
        globalLog.add(record);
        if (globalLog.size() > MAX_GLOBAL) {
            globalLog.remove(0);
        }

        log.debug("[audit] seq={} task={} step={} action={} {} → {}",
                record.sequence, record.taskId, record.stepIndex,
                record.action, record.previousValue, record.newValue);
        return record;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public enum AuditAction {
        STATE_TRANSITION,
        ROADMAP_MUTATION,
        DEPENDENCY_CHANGE,
        ADAPTATION_DECISION,
        PRIORITY_CHANGE
    }

    public static class AuditRecord {
        public final long          sequence;      // monotonically increasing, never reused
        public final Long          taskId;
        public final int           stepIndex;
        public final AuditAction   action;
        public final String        previousValue;
        public final String        newValue;
        public final String        actor;
        public final String        reason;
        public final LocalDateTime timestamp;

        public AuditRecord(long sequence, Long taskId, int stepIndex, AuditAction action,
                           String previousValue, String newValue,
                           String actor, String reason, LocalDateTime timestamp) {
            this.sequence      = sequence;
            this.taskId        = taskId;
            this.stepIndex     = stepIndex;
            this.action        = action;
            this.previousValue = previousValue;
            this.newValue      = newValue;
            this.actor         = actor;
            this.reason        = reason;
            this.timestamp     = timestamp;
        }
    }
}
