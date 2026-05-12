package com.assistant.controller;

import com.assistant.dto.TaskRequest;
import com.assistant.dto.TaskResponse;
import com.assistant.model.Task;
import com.assistant.model.User;
import com.assistant.repository.TaskRepository;
import com.assistant.service.RateLimitService;
import com.assistant.service.TaskService;
import com.assistant.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskService      taskService;
    private final TaskRepository   taskRepository;
    private final RateLimitService rateLimitService;
    private final UserService      userService;

    public TaskController(TaskService taskService, TaskRepository taskRepository,
                          RateLimitService rateLimitService, UserService userService) {
        this.taskService      = taskService;
        this.taskRepository   = taskRepository;
        this.rateLimitService = rateLimitService;
        this.userService      = userService;
    }

    /**
     * POST /api/tasks
     * Submit a new task for AI processing.
     */
    @PostMapping
    public ResponseEntity<?> createTask(@RequestBody TaskRequest request) {
        User user = userService.getCurrentUser();
        Long userId = user != null ? user.getId() : -1L;

        // Phase 7: rate limiting
        RateLimitService.RateLimitResult limit = rateLimitService.checkAndRecord(userId);
        if (!limit.allowed) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(limit.retryAfterSeconds))
                    .body(Map.of(
                            "error",             limit.message,
                            "type",              limit.type.name(),
                            "retryAfter",        limit.retryAfterSeconds,
                            "remainingRequests", limit.remainingRequests
                    ));
        }

        try {
            TaskResponse response = taskService.processTask(request);
            rateLimitService.recordSuccess(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Only count as a failure if it's a genuine backend error (5xx equivalent).
            // Validation errors, bad requests, and user-input issues are NOT abuse.
            boolean isBackendError = !(e instanceof IllegalArgumentException)
                    && !(e instanceof org.springframework.web.bind.MethodArgumentNotValidException)
                    && !e.getMessage().contains("validation")
                    && !e.getMessage().contains("invalid")
                    && !e.getMessage().contains("not found");
            if (isBackendError) {
                rateLimitService.recordFailure(userId);
            } else {
                // Release the lock without penalty — this was a user input issue
                rateLimitService.forceRelease(userId);
            }
            throw e;
        }
    }

    /**
     * GET /api/tasks
     * Retrieve all past tasks (history), newest first.
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    /**
     * GET /api/tasks/search?q=java&intent=CODING&category=coding&page=0&size=20
     * Paginated, searchable, filterable history.
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchTasks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));

        // Normalize empty strings to null so JPQL IS NULL checks work
        String queryParam    = (q        != null && !q.isBlank())        ? q        : null;
        String intentParam   = (intent   != null && !intent.isBlank())   ? intent   : null;
        String categoryParam = (category != null && !category.isBlank()) ? category : null;

        Page<Task> taskPage = taskRepository.findWithFilters(queryParam, intentParam, categoryParam, pageable);

        List<TaskResponse> items = taskPage.getContent()
                .stream()
                .map(t -> taskService.getTaskById(t.getId()))
                .toList();

        return ResponseEntity.ok(Map.of(
                "items",      items,
                "totalItems", taskPage.getTotalElements(),
                "totalPages", taskPage.getTotalPages(),
                "page",       page,
                "size",       size
        ));
    }

    /**
     * GET /api/tasks/{id}
     * Retrieve a specific task by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    /**
     * DELETE /api/tasks/{id}
     * Delete a task from history.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        if (!taskRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        taskRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/tasks/stream
     * Submit a task and receive a streaming SSE response.
     *
     * Stream protocol:
     *   data: {"chunk":"...","done":false}          — incremental text chunk
     *   data: {"chunk":"","done":true,"taskId":N}   — final event, stream complete
     *   data: {"error":"...","done":true}            — error event, stream complete
     *   data: {"error":"...","retryAfter":N}         — rate-limit event, stream complete
     *
     * The emitter is always completed (never left open) to prevent
     * ERR_INCOMPLETE_CHUNKED_ENCODING and orphaned connections.
     */
    @PostMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamTask(
            @RequestBody TaskRequest request) {

        User user   = userService.getCurrentUser();
        Long userId = user != null ? user.getId() : -1L;

        // 120-second timeout — always triggers onTimeout which completes the emitter
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120_000L);

        // ── Lifecycle handlers — CRITICAL for clean connection termination ──
        emitter.onCompletion(() ->
            log.info("[stream] Emitter completed for user={}", userId));

        // onTimeout is overridden below after completeOnce is created
        // onError releases the lock without failure penalty
        emitter.onError(ex -> {
            log.warn("[stream] Emitter error for user={}: {}", userId, ex.getMessage());
            rateLimitService.forceRelease(userId);
        });

        // ── Rate limit check ──────────────────────────────────────────────────
        RateLimitService.RateLimitResult limit = rateLimitService.checkAndRecord(userId);
        if (!limit.allowed) {
            try {
                String payload = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .writeValueAsString(java.util.Map.of(
                                "error",             limit.message,
                                "retryAfter",        limit.retryAfterSeconds,
                                "remainingRequests", limit.remainingRequests,
                                "type",              limit.type.name(),
                                "done",              true
                        ));
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(payload));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        // ── Background generation ─────────────────────────────────────────────
        // Phase 7.2: atomic completion guard — emitter.complete() called exactly once
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        Runnable completeOnce = () -> {
            if (completed.compareAndSet(false, true)) {
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        };

        // Update lifecycle handlers to use completeOnce
        emitter.onTimeout(() -> {
            log.warn("[stream] Emitter timed out for user={}", userId);
            rateLimitService.forceRelease(userId);
            completeOnce.run();
        });

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean success = false;
            try {
                log.info("[stream] STREAM_START user={} input='{}'",
                        userId, request.getUserInput().substring(0, Math.min(50, request.getUserInput().length())));

                TaskResponse response = taskService.processTask(request);
                success = true;
                rateLimitService.recordSuccess(userId);

                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                String content = response.getAiOutput() != null ? response.getAiOutput() : "";
                int chunkSize  = 80;

                for (int i = 0; i < content.length(); i += chunkSize) {
                    // Stop sending if already completed (client disconnected)
                    if (completed.get()) {
                        log.info("[stream] STREAM_ABORT early exit — emitter already completed, user={}", userId);
                        return;
                    }
                    String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
                    String payload = mapper.writeValueAsString(java.util.Map.of(
                            "chunk", chunk,
                            "done",  false
                    ));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(payload));
                    log.debug("[stream] STREAM_CHUNK user={} offset={}", userId, i);
                    Thread.sleep(15);
                }

                if (!completed.get()) {
                    String finalPayload = mapper.writeValueAsString(java.util.Map.of(
                            "chunk",  "",
                            "done",   true,
                            "taskId", response.getId()
                    ));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(finalPayload));
                    log.info("[stream] STREAM_COMPLETE user={} taskId={}", userId, response.getId());
                }

            } catch (java.io.IOException ioEx) {
                log.info("[stream] STREAM_ABORT client disconnected user={}: {}", userId, ioEx.getMessage());
                if (!success) rateLimitService.forceRelease(userId);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.info("[stream] STREAM_ABORT interrupted user={}", userId);
                if (!success) rateLimitService.forceRelease(userId);

            } catch (Exception e) {
                log.error("[stream] STREAM_ERROR user={}: {}", userId, e.getMessage());
                if (!success) rateLimitService.recordFailure(userId);
                if (!completed.get()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper =
                                new com.fasterxml.jackson.databind.ObjectMapper();
                        String errPayload = mapper.writeValueAsString(java.util.Map.of(
                                "error", e.getMessage() != null ? e.getMessage() : "Generation failed",
                                "done",  true
                        ));
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(errPayload));
                    } catch (Exception ignored) {}
                }

            } finally {
                completeOnce.run(); // guaranteed single completion
            }
        });

        return emitter;
    }
}
