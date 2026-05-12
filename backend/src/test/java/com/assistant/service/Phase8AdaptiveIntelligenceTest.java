package com.assistant.service;

import com.assistant.service.SkillInferenceService.SkillLevel;
import com.assistant.service.SkillInferenceService.SkillInference;
import com.assistant.service.RoadmapStrategyEngine.RoadmapStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 8 — Adaptive Roadmap Intelligence Validation Tests
 *
 * TEST 1: "I don't know Java basics" → ABSOLUTE_BEGINNER, no Spring Boot early
 * TEST 2: "advanced distributed AI infrastructure" → EXPERT, advanced tooling
 * TEST 3: Different prompts produce different strategies
 * TEST 4: Difficulty and timeline calibration
 * TEST 5: Forbidden topics enforced for beginners
 */
@SpringBootTest
public class Phase8AdaptiveIntelligenceTest {

    @Autowired private SkillInferenceService   skillInferenceService;
    @Autowired private RoadmapStrategyEngine   roadmapStrategyEngine;

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: Absolute beginner detection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testDontKnowJavaBasicsIsAbsoluteBeginner() {
        SkillInference result = skillInferenceService.infer("I don't know Java basics");
        assertEquals(SkillLevel.ABSOLUTE_BEGINNER, result.level,
                "\"I don't know Java basics\" must be ABSOLUTE_BEGINNER");
        assertTrue(result.confidence >= 0.8, "Confidence must be high: " + result.confidence);
        System.out.println("✅ \"I don't know Java basics\" → " + result.level + " (confidence=" + result.confidence + ")");
    }

    @Test
    void testFromScratchIsAbsoluteBeginner() {
        SkillInference result = skillInferenceService.infer("Learn Java from scratch");
        assertEquals(SkillLevel.ABSOLUTE_BEGINNER, result.level,
                "\"from scratch\" must be ABSOLUTE_BEGINNER");
        System.out.println("✅ \"Learn Java from scratch\" → " + result.level);
    }

    @Test
    void testNeverCodedIsAbsoluteBeginner() {
        SkillInference result = skillInferenceService.infer("I never coded before, want to learn programming");
        assertEquals(SkillLevel.ABSOLUTE_BEGINNER, result.level,
                "\"never coded\" must be ABSOLUTE_BEGINNER");
        System.out.println("✅ \"never coded before\" → " + result.level);
    }

    @Test
    void testCompleteBeginnerIsAbsoluteBeginner() {
        SkillInference result = skillInferenceService.infer("complete beginner to Java");
        assertEquals(SkillLevel.ABSOLUTE_BEGINNER, result.level);
        System.out.println("✅ \"complete beginner\" → " + result.level);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: Expert detection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testAdvancedDistributedAIIsExpert() {
        SkillInference result = skillInferenceService.infer(
                "advanced distributed AI infrastructure roadmap with vLLM and DeepSpeed");
        assertTrue(result.level == SkillLevel.EXPERT || result.level == SkillLevel.ADVANCED,
                "Advanced AI infra must be EXPERT or ADVANCED, got: " + result.level);
        System.out.println("✅ Advanced AI infra → " + result.level + " (confidence=" + result.confidence + ")");
    }

    @Test
    void testKubernetesIstioIsAdvancedOrExpert() {
        SkillInference result = skillInferenceService.infer(
                "production Kubernetes platform with Istio service mesh and ArgoCD GitOps");
        assertTrue(result.level == SkillLevel.EXPERT || result.level == SkillLevel.ADVANCED,
                "Production K8s must be ADVANCED or EXPERT, got: " + result.level);
        System.out.println("✅ Production Kubernetes → " + result.level);
    }

    @Test
    void testAlreadyKnowSpringBootIsIntermediate() {
        SkillInference result = skillInferenceService.infer(
                "I already know Spring Boot, want to learn Kafka and microservices");
        assertTrue(result.level == SkillLevel.INTERMEDIATE || result.level == SkillLevel.ADVANCED,
                "\"already know Spring Boot\" must be INTERMEDIATE or ADVANCED, got: " + result.level);
        System.out.println("✅ \"already know Spring Boot\" → " + result.level);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: Different prompts produce different strategies
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testBeginnerAndExpertStrategiesDiffer() {
        SkillInference beginnerInference = skillInferenceService.infer("I don't know Java basics");
        SkillInference expertInference   = skillInferenceService.infer("advanced distributed AI infrastructure");

        RoadmapStrategy beginnerStrategy = roadmapStrategyEngine.buildStrategy(
                beginnerInference.level, "JAVA_BACKEND", "I don't know Java basics");
        RoadmapStrategy expertStrategy   = roadmapStrategyEngine.buildStrategy(
                expertInference.level, "AI_ENGINEERING", "advanced distributed AI infrastructure");

        // Difficulty must differ
        assertNotEquals(beginnerStrategy.difficultyLabel(), expertStrategy.difficultyLabel(),
                "Beginner and expert strategies must have different difficulty labels");

        // Timeline must differ
        assertNotEquals(beginnerStrategy.realisticTimeline(), expertStrategy.realisticTimeline(),
                "Beginner and expert strategies must have different timelines");

        // Step counts must differ
        assertNotEquals(beginnerStrategy.recommendedStepCount(), expertStrategy.recommendedStepCount(),
                "Beginner and expert strategies must have different step counts");

        System.out.println("✅ Strategies differ:");
        System.out.println("   Beginner: " + beginnerStrategy.difficultyLabel() + " / " + beginnerStrategy.realisticTimeline());
        System.out.println("   Expert:   " + expertStrategy.difficultyLabel() + " / " + expertStrategy.realisticTimeline());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4: Difficulty and timeline calibration
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testAbsoluteBeginnerJavaStrategyIsBeginnerDifficulty() {
        RoadmapStrategy strategy = roadmapStrategyEngine.buildStrategy(
                SkillLevel.ABSOLUTE_BEGINNER, "JAVA_BACKEND", "I don't know Java basics");

        assertEquals("Beginner", strategy.difficultyLabel(),
                "Absolute beginner Java must have Beginner difficulty");
        assertTrue(strategy.realisticTimeline().contains("month"),
                "Absolute beginner timeline must be in months: " + strategy.realisticTimeline());
        System.out.println("✅ Absolute beginner Java: " + strategy.difficultyLabel() + " / " + strategy.realisticTimeline());
    }

    @Test
    void testExpertAIStrategyIsExpertDifficulty() {
        RoadmapStrategy strategy = roadmapStrategyEngine.buildStrategy(
                SkillLevel.EXPERT, "AI_ENGINEERING", "advanced AI infrastructure");

        assertEquals("Expert", strategy.difficultyLabel(),
                "Expert AI must have Expert difficulty");
        System.out.println("✅ Expert AI: " + strategy.difficultyLabel() + " / " + strategy.realisticTimeline());
    }

    @Test
    void testIntermediateJavaStrategyIsIntermediateDifficulty() {
        RoadmapStrategy strategy = roadmapStrategyEngine.buildStrategy(
                SkillLevel.INTERMEDIATE, "JAVA_BACKEND", "Spring Boot REST API");

        assertEquals("Intermediate", strategy.difficultyLabel());
        System.out.println("✅ Intermediate Java: " + strategy.difficultyLabel() + " / " + strategy.realisticTimeline());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5: Forbidden topics enforced for beginners
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testAbsoluteBeginnerJavaForbidsSpringBoot() {
        RoadmapStrategy strategy = roadmapStrategyEngine.buildStrategy(
                SkillLevel.ABSOLUTE_BEGINNER, "JAVA_BACKEND", "I don't know Java basics");

        assertTrue(strategy.forbiddenTopics().contains("Spring Boot"),
                "Spring Boot must be forbidden for absolute beginner Java");
        assertTrue(strategy.forbiddenTopics().contains("Kafka"),
                "Kafka must be forbidden for absolute beginner Java");
        assertTrue(strategy.forbiddenTopics().contains("Docker"),
                "Docker must be forbidden for absolute beginner Java");
        System.out.println("✅ Absolute beginner Java forbidden topics: " + strategy.forbiddenTopics());
    }

    @Test
    void testAbsoluteBeginnerJavaRequiresFundamentals() {
        RoadmapStrategy strategy = roadmapStrategyEngine.buildStrategy(
                SkillLevel.ABSOLUTE_BEGINNER, "JAVA_BACKEND", "I don't know Java basics");

        boolean hasVariables = strategy.requiredTopics().stream()
                .anyMatch(t -> t.toLowerCase().contains("variable") || t.toLowerCase().contains("data type"));
        boolean hasOOP = strategy.requiredTopics().stream()
                .anyMatch(t -> t.toLowerCase().contains("object") || t.toLowerCase().contains("oop") || t.toLowerCase().contains("class"));

        assertTrue(hasVariables, "Absolute beginner must require variables/data types");
        assertTrue(hasOOP, "Absolute beginner must require OOP basics");
        System.out.println("✅ Absolute beginner Java required topics include fundamentals");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6: Strategy prompt contains correct content
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testStrategyPromptContainsDifficultyAndTimeline() {
        RoadmapStrategy strategy = roadmapStrategyEngine.buildStrategy(
                SkillLevel.ABSOLUTE_BEGINNER, "JAVA_BACKEND", "I don't know Java basics");
        String prompt = roadmapStrategyEngine.buildStrategyPrompt(
                strategy, SkillLevel.ABSOLUTE_BEGINNER, "JAVA_BACKEND");

        assertTrue(prompt.contains("Beginner"), "Prompt must contain difficulty label");
        assertTrue(prompt.contains("month"), "Prompt must contain timeline in months");
        assertTrue(prompt.contains("ABSOLUTE_BEGINNER"), "Prompt must state the skill level");
        assertTrue(prompt.contains("FORBIDDEN"), "Prompt must list forbidden topics");
        System.out.println("✅ Strategy prompt contains all required sections");
    }

    @Test
    void testStrategyPromptForExpertContainsAdvancedContent() {
        RoadmapStrategy strategy = roadmapStrategyEngine.buildStrategy(
                SkillLevel.EXPERT, "AI_ENGINEERING", "advanced AI infrastructure");
        String prompt = roadmapStrategyEngine.buildStrategyPrompt(
                strategy, SkillLevel.EXPERT, "AI_ENGINEERING");

        assertTrue(prompt.contains("Expert"), "Expert prompt must contain Expert difficulty");
        assertTrue(prompt.contains("EXPERT"), "Expert prompt must state EXPERT level");
        System.out.println("✅ Expert strategy prompt contains advanced content");
    }
}
