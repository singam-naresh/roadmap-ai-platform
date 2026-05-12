package com.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 5 — AI Quality & Reasoning Engine Validation Tests
 *
 * Validates:
 * 1. No truncated steps pass completeness validation
 * 2. No vague steps pass output quality scoring
 * 3. No duplicate roadmap items pass uniqueness check
 * 4. Realistic timelines are enforced
 * 5. Role-aware progression is validated
 * 6. Quality score thresholds are enforced
 * 7. Semantic uniqueness detects duplicates
 * 8. Self-critique rejects weak generations
 * 9. Domain alignment is verified
 * 10. Output polish fixes formatting issues
 */
@SpringBootTest
public class Phase5QualityReasoningTest {

    @Autowired private OutputQualityEngine        outputQualityEngine;
    @Autowired private StepCompletenessValidator  completenessValidator;
    @Autowired private DomainReasoningEngine      domainReasoningEngine;
    @Autowired private RoadmapSpecificityEngine   specificityEngine;
    @Autowired private TimelineRealismEngine      timelineRealismEngine;
    @Autowired private SemanticUniquenessEngine   uniquenessEngine;
    @Autowired private RoleMaturityEngine         roleMaturityEngine;
    @Autowired private SelfCritiqueEngine         selfCritiqueEngine;
    @Autowired private OutputPolishEngine         outputPolishEngine;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Step completeness — truncated steps must be rejected
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testTruncatedStepsAreDetected() {
        List<String> truncated = List.of(
            "Develop a microservices",
            "Implement OAuth 2",
            "Create a Java",
            "Configure the",
            "Build a Spring"
        );

        for (String step : truncated) {
            List<String> issues = completenessValidator.detectIssues(step);
            assertFalse(issues.isEmpty(),
                "Truncated step must be detected: '" + step + "'");
        }
        System.out.println("✅ All truncated steps detected");
    }

    @Test
    void testCompleteStepsPassValidation() {
        List<String> complete = List.of(
            "Build a Spring Boot 3.x REST API with JWT authentication, PostgreSQL persistence, and Docker containerization",
            "Implement Apache Kafka event streaming with Schema Registry, consumer groups, and dead-letter queue handling",
            "Deploy a Kubernetes cluster with Helm charts, Horizontal Pod Autoscaling, and Prometheus monitoring"
        );

        for (String step : complete) {
            List<String> issues = completenessValidator.detectIssues(step);
            assertTrue(issues.isEmpty(),
                "Complete step should pass validation: '" + step + "' — issues: " + issues);
        }
        System.out.println("✅ Complete steps pass validation");
    }

    @Test
    void testRoadmapWithTruncatedStepsFailsValidation() throws Exception {
        JsonNode roadmap = buildRoadmap(List.of(
            "Develop a microservices",
            "Implement OAuth 2",
            "Build a Spring"
        ), "3–6 months", "Intermediate");

        StepCompletenessValidator.CompletenessReport report = completenessValidator.validate(roadmap);
        assertFalse(report.allComplete, "Roadmap with truncated steps must fail completeness");
        assertEquals(3, report.truncatedIndices.size());
        System.out.println("✅ Roadmap with truncated steps fails completeness: " + report.truncatedIndices);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Output quality — vague steps must score low
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testVagueStepsScoreLow() {
        List<String> vagueSteps = List.of(
            "Learn backend development",
            "Study various frameworks",
            "Understand some concepts",
            "Explore the field"
        );

        for (String step : vagueSteps) {
            OutputQualityEngine.StepQualityScore score =
                    outputQualityEngine.scoreStep(step, 0, vagueSteps);
            assertTrue(score.score < OutputQualityEngine.STEP_QUALITY_THRESHOLD,
                "Vague step must score below threshold: '" + step + "' scored " + score.score);
        }
        System.out.println("✅ Vague steps score below threshold");
    }

    @Test
    void testConcreteStepsScoreHigh() {
        List<String> concreteSteps = List.of(
            "Build a Spring Boot 3.x REST API with JWT authentication, PostgreSQL persistence via JPA, Docker containerization, and JUnit 5 integration tests",
            "Implement Apache Kafka event streaming with Schema Registry for Avro serialization, consumer groups with offset management, and dead-letter queue handling",
            "Deploy microservices on Kubernetes with Helm charts, configure Horizontal Pod Autoscaling based on CPU/memory metrics, and set up Prometheus + Grafana monitoring"
        );

        for (String step : concreteSteps) {
            OutputQualityEngine.StepQualityScore score =
                    outputQualityEngine.scoreStep(step, 0, concreteSteps);
            assertTrue(score.score >= OutputQualityEngine.STEP_QUALITY_THRESHOLD,
                "Concrete step must score above threshold: '" + step.substring(0, 50) + "...' scored " + score.score);
        }
        System.out.println("✅ Concrete steps score above threshold");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Semantic uniqueness — duplicate steps must be detected
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testDuplicateStepsAreDetected() {
        List<String> stepsWithDuplicates = List.of(
            "Build a Spring Boot REST API with PostgreSQL",
            "Implement Docker containerization for the application",
            "Build a Spring Boot REST API with PostgreSQL database",  // near-duplicate of step 1
            "Configure Kubernetes deployment with Helm charts",
            "Set up Prometheus monitoring with Grafana dashboards"
        );

        SemanticUniquenessEngine.UniquenessReport report =
                uniquenessEngine.analyzeSteps(stepsWithDuplicates);

        assertFalse(report.duplicatePairs.isEmpty(),
            "Duplicate steps must be detected");
        System.out.println("✅ Duplicate steps detected: " + report.duplicatePairs.size() + " pairs");
    }

    @Test
    void testUniqueStepsPassUniquenessCheck() {
        List<String> uniqueSteps = List.of(
            "Build a Spring Boot 3.x REST API with JWT authentication and PostgreSQL persistence",
            "Implement Apache Kafka event streaming with Schema Registry and consumer groups",
            "Deploy on Kubernetes with Helm charts and configure Horizontal Pod Autoscaling",
            "Set up Prometheus + Grafana observability stack with custom dashboards and AlertManager",
            "Implement distributed tracing with Jaeger and OpenTelemetry instrumentation"
        );

        SemanticUniquenessEngine.UniquenessReport report =
                uniquenessEngine.analyzeSteps(uniqueSteps);

        assertTrue(report.duplicatePairs.isEmpty(),
            "Unique steps must pass uniqueness check, got duplicates: " + report.duplicatePairs);
        System.out.println("✅ Unique steps pass uniqueness check");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Timeline realism — impossible timelines must be rejected
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testImpossibleTimelinesAreRejected() throws Exception {
        // "1 day" for Kubernetes is impossible
        JsonNode roadmap = buildRoadmap(List.of("Deploy Kubernetes cluster"), "1 day", "Intermediate");
        TimelineRealismEngine.TimelineValidationResult result =
                timelineRealismEngine.validate(roadmap, "DEVOPS", "INTERMEDIATE");

        assertTrue(result.isImpossible || result.isTooShort,
            "1-day timeline for Kubernetes must be rejected");
        System.out.println("✅ Impossible timeline rejected: " + result.issues);
    }

    @Test
    void testVagueTimelinesAreRejected() throws Exception {
        JsonNode roadmap = buildRoadmap(List.of("Build something"), "a few weeks", "Intermediate");
        TimelineRealismEngine.TimelineValidationResult result =
                timelineRealismEngine.validate(roadmap, "JAVA_BACKEND", "INTERMEDIATE");

        assertTrue(result.isVague, "Vague timeline 'a few weeks' must be flagged");
        System.out.println("✅ Vague timeline flagged: " + result.issues);
    }

    @Test
    void testRealisticTimelinePassesValidation() throws Exception {
        JsonNode roadmap = buildRoadmap(List.of("Build Spring Boot API"), "4–6 months", "Intermediate");
        TimelineRealismEngine.TimelineValidationResult result =
                timelineRealismEngine.validate(roadmap, "JAVA_BACKEND", "INTERMEDIATE");

        assertTrue(result.isValid, "Realistic timeline must pass: " + result.issues);
        System.out.println("✅ Realistic timeline passes validation");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Role maturity — expert content must not appear in beginner roadmaps
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testBeginnerRoadmapDoesNotContainExpertTerms() {
        List<String> beginnerSteps = List.of(
            "Learn Python basics and NumPy fundamentals",
            "Build a simple neural network with PyTorch",
            "Train a text classifier using scikit-learn"
        );

        // These steps should NOT trigger violations for BEGINNER level
        RoleMaturityEngine.MaturityValidationResult result =
                roleMaturityEngine.validate(beginnerSteps, "AI_ENGINEERING", "BEGINNER");

        // Beginner steps should not contain expert-only terms like "DeepSpeed ZeRO-3"
        boolean hasExpertViolation = result.violations.stream()
                .anyMatch(v -> v.contains("DeepSpeed") || v.contains("FSDP") || v.contains("TensorRT"));
        assertFalse(hasExpertViolation,
            "Beginner steps should not trigger expert-term violations");
        System.out.println("✅ Beginner roadmap passes role maturity check");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Self-critique — weak generations must be rejected
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testWeakRoadmapFailsSelfCritique() throws Exception {
        JsonNode weakRoadmap = buildRoadmap(List.of(
            "Learn about things",
            "Study some concepts",
            "Understand the basics"
        ), "varies", "Beginner");

        SelfCritiqueEngine.CritiqueResult critique =
                selfCritiqueEngine.critiqueRuleBased(weakRoadmap, "JAVA_BACKEND", "BEGINNER");

        assertFalse(critique.passes,
            "Weak roadmap must fail self-critique (score=" + critique.overallScore + ")");
        System.out.println("✅ Weak roadmap fails self-critique: score=" + critique.overallScore);
    }

    @Test
    void testStrongRoadmapPassesSelfCritique() throws Exception {
        JsonNode strongRoadmap = buildRoadmap(List.of(
            "Build a Spring Boot 3.x REST API with JWT authentication, PostgreSQL persistence via JPA, and Docker containerization",
            "Implement Apache Kafka event streaming with Schema Registry, consumer groups, and dead-letter queue handling",
            "Deploy on Kubernetes with Helm charts, configure HPA, and set up Prometheus + Grafana monitoring",
            "Implement distributed tracing with Jaeger and OpenTelemetry, configure AlertManager rules",
            "Secure the API with OAuth2/OIDC, implement RBAC authorization, and add rate limiting with Redis"
        ), "4–6 months", "Intermediate");

        SelfCritiqueEngine.CritiqueResult critique =
                selfCritiqueEngine.critiqueRuleBased(strongRoadmap, "JAVA_BACKEND", "INTERMEDIATE");

        assertTrue(critique.passes,
            "Strong roadmap must pass self-critique (score=" + critique.overallScore + ")");
        System.out.println("✅ Strong roadmap passes self-critique: score=" + critique.overallScore);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Domain alignment — reasoning engine produces correct progression
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testDomainReasoningProducesCorrectProgression() {
        DomainReasoningEngine.DomainReasoning reasoning =
                domainReasoningEngine.reason("Build a production AI system", "AI_ENGINEERING", "ADVANCED");

        assertNotNull(reasoning.skillProgression);
        assertFalse(reasoning.skillProgression.isEmpty());
        assertNotNull(reasoning.realisticTimeline);
        assertFalse(reasoning.realisticTimeline.isBlank());

        // Advanced level should skip basic Python fundamentals
        boolean hasBasicPython = reasoning.skillProgression.stream()
                .anyMatch(s -> s.toLowerCase().contains("python basics"));
        assertFalse(hasBasicPython,
            "ADVANCED level should skip Python basics in progression");

        System.out.println("✅ Domain reasoning for AI_ENGINEERING ADVANCED: " +
                reasoning.skillProgression.size() + " steps, timeline=" + reasoning.realisticTimeline);
    }

    @Test
    void testBeginnerReasoningIncludesFoundationalSteps() {
        DomainReasoningEngine.DomainReasoning reasoning =
                domainReasoningEngine.reason("Learn Java backend", "JAVA_BACKEND", "BEGINNER");

        assertNotNull(reasoning.skillProgression);
        assertFalse(reasoning.skillProgression.isEmpty());

        // Beginner should include foundational steps
        boolean hasFoundational = reasoning.skillProgression.stream()
                .anyMatch(s -> s.toLowerCase().contains("java") ||
                               s.toLowerCase().contains("spring") ||
                               s.toLowerCase().contains("rest"));
        assertTrue(hasFoundational, "BEGINNER progression must include foundational Java/Spring steps");
        System.out.println("✅ Beginner domain reasoning includes foundational steps");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Specificity — steps must contain all four elements
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testVagueStepFailsSpecificityAnalysis() {
        String vagueStep = "Learn backend development";
        RoadmapSpecificityEngine.SpecificityAnalysisResult result =
                specificityEngine.analyzeStep(vagueStep, "JAVA_BACKEND");

        assertTrue(result.score < 0.5, "Vague step must score below 0.5: " + result.score);
        assertFalse(result.missingElements.isEmpty(), "Vague step must have missing elements");
        System.out.println("✅ Vague step fails specificity: score=" + result.score +
                " missing=" + result.missingElements);
    }

    @Test
    void testConcreteStepPassesSpecificityAnalysis() {
        String concreteStep = "Build a Spring Boot 3.x REST API with JWT authentication, " +
                "PostgreSQL persistence via JPA, Docker containerization, " +
                "and JUnit 5 integration tests targeting 95%+ code coverage";
        RoadmapSpecificityEngine.SpecificityAnalysisResult result =
                specificityEngine.analyzeStep(concreteStep, "JAVA_BACKEND");

        assertTrue(result.score >= 0.7, "Concrete step must score >= 0.7: " + result.score);
        System.out.println("✅ Concrete step passes specificity: score=" + result.score);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Output polish — formatting issues are fixed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testOutputPolishFixesDoubleSpaces() {
        String messy = "Build  a  Spring  Boot  API";
        String polished = outputPolishEngine.polishStep(messy);
        assertFalse(polished.contains("  "), "Double spaces must be removed");
        System.out.println("✅ Double spaces removed: '" + polished + "'");
    }

    @Test
    void testOutputPolishCapitalizesFirstLetter() {
        String lower = "build a spring boot api with jwt authentication";
        String polished = outputPolishEngine.polishStep(lower);
        assertTrue(Character.isUpperCase(polished.charAt(0)),
            "First letter must be capitalized");
        System.out.println("✅ First letter capitalized: '" + polished.substring(0, 20) + "...'");
    }

    @Test
    void testOutputPolishRemovesTrailingComma() {
        String withComma = "Build a Spring Boot API, configure PostgreSQL,";
        String polished = outputPolishEngine.polishStep(withComma);
        assertFalse(polished.endsWith(","), "Trailing comma must be removed");
        System.out.println("✅ Trailing comma removed");
    }

    @Test
    void testOutputPolishNormalizesDifficulty() throws Exception {
        JsonNode roadmap = buildRoadmap(List.of("Build something"), "3–6 months", "medium");
        JsonNode polished = outputPolishEngine.polish(roadmap);
        assertEquals("Intermediate", polished.path("difficulty").asText(),
            "Difficulty 'medium' must be normalized to 'Intermediate'");
        System.out.println("✅ Difficulty normalized: medium → Intermediate");
    }

    @Test
    void testOutputPolishNormalizesTimeline() throws Exception {
        JsonNode roadmap = buildRoadmap(List.of("Build something"), "3-6 months", "Intermediate");
        JsonNode polished = outputPolishEngine.polish(roadmap);
        String timeline = polished.path("estimatedTime").asText();
        assertTrue(timeline.contains("–"), "Timeline must use en-dash: " + timeline);
        System.out.println("✅ Timeline normalized: 3-6 months → " + timeline);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private JsonNode buildRoadmap(List<String> steps, String estimatedTime, String difficulty) throws Exception {
        ObjectNode roadmap = objectMapper.createObjectNode();
        roadmap.put("summary", "A comprehensive roadmap for building production-grade systems with modern tools and best practices");
        roadmap.put("estimatedTime", estimatedTime);
        roadmap.put("difficulty", difficulty);

        ArrayNode stepsNode = objectMapper.createArrayNode();
        steps.forEach(stepsNode::add);
        roadmap.set("steps", stepsNode);

        ArrayNode tips = objectMapper.createArrayNode();
        tips.add("Use version control for all code");
        tips.add("Write tests before implementing features");
        roadmap.set("tips", tips);

        ArrayNode mistakes = objectMapper.createArrayNode();
        mistakes.add("Skipping tests to save time");
        mistakes.add("Not using environment variables for secrets");
        roadmap.set("mistakesToAvoid", mistakes);

        ArrayNode resources = objectMapper.createArrayNode();
        resources.add("Official documentation");
        resources.add("GitHub repositories");
        resources.add("Community forums");
        roadmap.set("resources", resources);

        return roadmap;
    }
}
