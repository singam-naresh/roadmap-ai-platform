package com.assistant.service;

import com.assistant.model.StepProgress;
import com.assistant.repository.StepProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 7 — Production Hardening Integration Tests
 *
 * Validates:
 * 1. Rate limiting allows and blocks correctly
 * 2. Concurrent generation is prevented
 * 3. Failure cooldown activates after threshold
 * 4. Step completion persists across simulated reloads
 * 5. Roadmap export produces valid Markdown and JSON
 * 6. Goal feasibility correctly rejects impossible timelines
 */
@SpringBootTest
@Transactional
public class Phase7IntegrationTest {

    @Autowired private RateLimitService       rateLimitService;
    @Autowired private GoalFeasibilityEngine  feasibilityEngine;
    @Autowired private StepProgressRepository stepProgressRepo;

    private static final long TEST_USER_ID = 77_000L;

    @BeforeEach
    void cleanup() {
        stepProgressRepo.deleteByTaskId(TEST_USER_ID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Rate limiting
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testRateLimitAllowsFirstRequest() {
        RateLimitService.RateLimitResult result = rateLimitService.checkAndRecord(TEST_USER_ID);
        assertTrue(result.allowed, "First request must be allowed");
        assertTrue(result.remainingRequests > 0, "Remaining requests must be > 0");
        rateLimitService.recordSuccess(TEST_USER_ID);
        System.out.println("✅ Rate limit allows first request, remaining=" + result.remainingRequests);
    }

    @Test
    void testRateLimitBlocksConcurrentGeneration() {
        // First request — marks active
        RateLimitService.RateLimitResult first = rateLimitService.checkAndRecord(TEST_USER_ID + 1);
        assertTrue(first.allowed);

        // Second request while first is active — must be blocked
        RateLimitService.RateLimitResult second = rateLimitService.checkAndRecord(TEST_USER_ID + 1);
        assertFalse(second.allowed, "Concurrent request must be blocked");
        assertEquals(RateLimitService.LimitType.CONCURRENT, second.type);

        rateLimitService.recordSuccess(TEST_USER_ID + 1);
        System.out.println("✅ Concurrent generation blocked: " + second.message);
    }

    @Test
    void testRateLimitAllowsAfterSuccess() {
        RateLimitService.RateLimitResult first = rateLimitService.checkAndRecord(TEST_USER_ID + 2);
        assertTrue(first.allowed);
        rateLimitService.recordSuccess(TEST_USER_ID + 2);

        // After success, next request should be allowed
        RateLimitService.RateLimitResult second = rateLimitService.checkAndRecord(TEST_USER_ID + 2);
        assertTrue(second.allowed, "Request after success must be allowed");
        rateLimitService.recordSuccess(TEST_USER_ID + 2);
        System.out.println("✅ Rate limit allows after success");
    }

    @Test
    void testForceReleaseAllowsNextRequestWithoutFailurePenalty() {
        long userId = TEST_USER_ID + 10;
        // Start a generation
        RateLimitService.RateLimitResult first = rateLimitService.checkAndRecord(userId);
        assertTrue(first.allowed);

        // Force-release (simulates client disconnect)
        rateLimitService.forceRelease(userId);

        // Next request should be allowed (no failure penalty)
        RateLimitService.RateLimitResult second = rateLimitService.checkAndRecord(userId);
        assertTrue(second.allowed, "Request after forceRelease must be allowed");
        rateLimitService.recordSuccess(userId);
        System.out.println("✅ forceRelease allows next request without failure penalty");
    }

    @Test
    void testForceReleaseDoesNotIncrementFailureCounter() {
        long userId = TEST_USER_ID + 11;
        // Force-release twice (simulates two client disconnects)
        for (int i = 0; i < 2; i++) {
            RateLimitService.RateLimitResult r = rateLimitService.checkAndRecord(userId);
            if (r.allowed) rateLimitService.forceRelease(userId);
        }

        // Should NOT be in cooldown (failure counter not incremented)
        RateLimitService.RateLimitResult result = rateLimitService.checkAndRecord(userId);
        assertNotEquals(RateLimitService.LimitType.COOLDOWN, result.type,
                "forceRelease must not trigger cooldown");
        if (result.allowed) rateLimitService.recordSuccess(userId);
        System.out.println("✅ forceRelease does not increment failure counter");
    }

    @Test
    void testFailureCooldownActivatesAfterThreshold() {
        long userId = TEST_USER_ID + 3;

        // Simulate 3 consecutive failures
        for (int i = 0; i < 3; i++) {
            RateLimitService.RateLimitResult r = rateLimitService.checkAndRecord(userId);
            if (r.allowed) {
                rateLimitService.recordFailure(userId);
            }
        }

        // 4th request should be in cooldown
        RateLimitService.RateLimitResult result = rateLimitService.checkAndRecord(userId);
        assertFalse(result.allowed, "Should be in cooldown after 3 failures");
        assertEquals(RateLimitService.LimitType.COOLDOWN, result.type);
        System.out.println("✅ Cooldown activated after failures: " + result.message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Goal feasibility
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testFeasibilityRejectsImpossibleTimeline() {
        GoalFeasibilityEngine.FeasibilityResult result =
                feasibilityEngine.assess("Become expert AI engineer in 2 weeks");

        assertFalse(result.feasible, "2-week expert AI goal must be rejected");
        assertNotNull(result.explanation);
        assertNotNull(result.minimumRealisticEstimate);
        assertNotNull(result.acceleratedAlternative);
        System.out.println("✅ Impossible timeline rejected: min=" + result.minimumRealisticEstimate);
    }

    @Test
    void testFeasibilityRejectsMasterDistributedIn5Days() {
        GoalFeasibilityEngine.FeasibilityResult result =
                feasibilityEngine.assess("Master distributed systems in 5 days");

        assertFalse(result.feasible, "5-day distributed systems mastery must be rejected");
        System.out.println("✅ 5-day distributed systems rejected: " + result.explanation.substring(0, 60) + "...");
    }

    @Test
    void testFeasibilityAcceptsRealisticTimeline() {
        GoalFeasibilityEngine.FeasibilityResult result =
                feasibilityEngine.assess("Learn Spring Boot and build a REST API");

        // No explicit timeline — should be feasible
        assertTrue(result.feasible, "Goal without explicit timeline must be feasible");
        System.out.println("✅ Realistic goal accepted");
    }

    @Test
    void testFeasibilityAcceptsReasonableTimeline() {
        GoalFeasibilityEngine.FeasibilityResult result =
                feasibilityEngine.assess("Learn Kubernetes in 6 months");

        assertTrue(result.feasible, "6-month Kubernetes goal must be feasible");
        System.out.println("✅ 6-month Kubernetes goal accepted");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Step completion persistence
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testStepCompletionPersists() {
        // Save a completed step
        StepProgress progress = new StepProgress();
        progress.setTaskId(TEST_USER_ID);
        progress.setStepIndex(0);
        progress.setCompleted(true);
        stepProgressRepo.save(progress);

        // Reload from DB (simulates page refresh)
        var reloaded = stepProgressRepo.findByTaskIdAndStepIndex(TEST_USER_ID, 0);
        assertTrue(reloaded.isPresent(), "Step progress must persist");
        assertTrue(reloaded.get().isCompleted(), "Completion state must persist");
        System.out.println("✅ Step completion persists across reload");
    }

    @Test
    void testMultipleStepsPersistIndependently() {
        for (int i = 0; i < 5; i++) {
            StepProgress p = new StepProgress();
            p.setTaskId(TEST_USER_ID);
            p.setStepIndex(i);
            p.setCompleted(i % 2 == 0); // even steps completed
            stepProgressRepo.save(p);
        }

        var all = stepProgressRepo.findByTaskIdOrderByStepIndexAsc(TEST_USER_ID);
        assertEquals(5, all.size());
        assertTrue(all.get(0).isCompleted());
        assertFalse(all.get(1).isCompleted());
        assertTrue(all.get(2).isCompleted());
        System.out.println("✅ Multiple step states persist independently");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Roadmap export
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testMarkdownExportProducesValidContent() {
        com.assistant.service.RoadmapService roadmapService =
                new com.assistant.service.RoadmapService(null, null, null, null, new com.fasterxml.jackson.databind.ObjectMapper());

        com.assistant.dto.RoadmapResponseDto roadmap = new com.assistant.dto.RoadmapResponseDto();
        roadmap.setId(1L);
        roadmap.setTitle("Spring Boot Mastery");
        roadmap.setSummary("A comprehensive roadmap");
        roadmap.setDifficulty("Intermediate");
        roadmap.setEstimatedTime("4–6 months");
        roadmap.setProgressPercentage(40);
        roadmap.setCompletedSteps(2);
        roadmap.setTotalSteps(5);

        com.assistant.dto.RoadmapStepDto step1 = new com.assistant.dto.RoadmapStepDto();
        step1.setStepIndex(0);
        step1.setTitle("Build a Spring Boot REST API with JWT auth");
        step1.setCompleted(true);

        com.assistant.dto.RoadmapStepDto step2 = new com.assistant.dto.RoadmapStepDto();
        step2.setStepIndex(1);
        step2.setTitle("Configure PostgreSQL with JPA");
        step2.setCompleted(false);

        String markdown = roadmapService.exportAsMarkdown(roadmap, java.util.List.of(step1, step2));

        assertTrue(markdown.contains("# Spring Boot Mastery"), "Must contain title");
        assertTrue(markdown.contains("[x]"), "Must contain completed checkbox");
        assertTrue(markdown.contains("[ ]"), "Must contain uncompleted checkbox");
        assertTrue(markdown.contains("Intermediate"), "Must contain difficulty");
        System.out.println("✅ Markdown export valid:\n" + markdown.substring(0, 100) + "...");
    }

    @Test
    void testJsonExportProducesValidJson() throws Exception {
        com.assistant.service.RoadmapService roadmapService =
                new com.assistant.service.RoadmapService(null, null, null, null, new com.fasterxml.jackson.databind.ObjectMapper());

        com.assistant.dto.RoadmapResponseDto roadmap = new com.assistant.dto.RoadmapResponseDto();
        roadmap.setId(1L);
        roadmap.setTitle("Kubernetes Platform Engineering");
        roadmap.setDifficulty("Advanced");
        roadmap.setEstimatedTime("3–4 months");
        roadmap.setProgressPercentage(0);
        roadmap.setCompletedSteps(0);
        roadmap.setTotalSteps(3);

        String json = roadmapService.exportAsJson(roadmap, java.util.List.of());

        // Validate it's parseable JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);

        assertEquals("Kubernetes Platform Engineering", node.path("title").asText());
        assertEquals("Advanced", node.path("difficulty").asText());
        assertTrue(node.has("exportedAt"), "Must have exportedAt timestamp");
        System.out.println("✅ JSON export valid: " + json.substring(0, 80) + "...");
    }
}
