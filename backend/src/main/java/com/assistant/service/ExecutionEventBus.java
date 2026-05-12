package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * PHASE 4 — Execution Event Bus
 *
 * Lightweight in-process event bus for execution lifecycle events.
 * Subscribers register handlers; the engine publishes events.
 *
 * Events: STEP_COMPLETED, TASK_BLOCKED, ROADMAP_MUTATED,
 *         SKILL_IMPROVED, USER_STUCK, PRIORITY_CHANGED, EXECUTION_FAILED
 */
@Service
public class ExecutionEventBus {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventBus.class);

    public enum EventType {
        STEP_COMPLETED,
        TASK_BLOCKED,
        ROADMAP_MUTATED,
        SKILL_IMPROVED,
        USER_STUCK,
        PRIORITY_CHANGED,
        EXECUTION_FAILED
    }

    public static class ExecutionEvent {
        public final EventType     type;
        public final Long          taskId;
        public final int           stepIndex;
        public final String        message;
        public final LocalDateTime occurredAt;

        public ExecutionEvent(EventType type, Long taskId, int stepIndex, String message) {
            this.type       = type;
            this.taskId     = taskId;
            this.stepIndex  = stepIndex;
            this.message    = message;
            this.occurredAt = LocalDateTime.now();
        }

        @Override
        public String toString() {
            return String.format("ExecutionEvent{type=%s, taskId=%d, step=%d, msg='%s'}",
                    type, taskId, stepIndex, message);
        }
    }

    // Per-type subscriber lists
    private final Map<EventType, List<Consumer<ExecutionEvent>>> subscribers = new ConcurrentHashMap<>();

    // Recent event log (last 200 events, in-memory)
    private final List<ExecutionEvent> recentEvents = new CopyOnWriteArrayList<>();
    private static final int MAX_RECENT = 200;

    public void subscribe(EventType type, Consumer<ExecutionEvent> handler) {
        subscribers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.debug("[event-bus] Subscribed to {}", type);
    }

    public void publish(EventType type, Long taskId, int stepIndex, String message) {
        ExecutionEvent event = new ExecutionEvent(type, taskId, stepIndex, message);

        // Store in recent log
        recentEvents.add(event);
        if (recentEvents.size() > MAX_RECENT) {
            recentEvents.remove(0);
        }

        log.info("[event-bus] {}", event);

        // Notify subscribers
        List<Consumer<ExecutionEvent>> handlers = subscribers.getOrDefault(type, List.of());
        for (Consumer<ExecutionEvent> handler : handlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.error("[event-bus] Handler error for {}: {}", type, e.getMessage());
            }
        }
    }

    public List<ExecutionEvent> getRecentEvents(Long taskId) {
        return recentEvents.stream()
                .filter(e -> e.taskId.equals(taskId))
                .toList();
    }

    public List<ExecutionEvent> getRecentEvents(Long taskId, EventType type) {
        return recentEvents.stream()
                .filter(e -> e.taskId.equals(taskId) && e.type == type)
                .toList();
    }

    public List<ExecutionEvent> getAllRecentEvents() {
        return new ArrayList<>(recentEvents);
    }
}
