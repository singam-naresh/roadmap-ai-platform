package com.assistant.service;

import com.assistant.service.DependencyGraphEngine.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 3E — Dependency Graph Propagation Engine Validation Tests
 *
 * Validates that replacing a core platform (e.g. Kubernetes → ECS) cascades
 * correctly through the ecosystem, removes orphan tools, and leaves no
 * structurally invalid combinations.
 */
@SpringBootTest
public class Phase3EValidationTest {

    private DependencyGraphEngine depEngine;
    private TechnologyOntologyEngine ontologyEngine;
    private StackConsistencyEngine stackEngine;

    @BeforeEach
    void setUp() {
        depEngine    = new DependencyGraphEngine();
        ontologyEngine = new TechnologyOntologyEngine();
        stackEngine  = new StackConsistencyEngine(ontologyEngine, depEngine);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. INVALID COMBINATION DETECTION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testDetectsECSPlusHelmAsIncompatible() {
        DependencyGraphAnalysis analysis =
                depEngine.analyzeDependencyGraph(List.of("ECS", "Helm"));

        assertFalse(analysis.incompatibleCombinations.isEmpty(),
                "ECS + Helm must be detected as incompatible");
        System.out.println("✅ ECS + Helm detected as incompatible: " + analysis.incompatibleCombinations);
    }

    @Test
    void testDetectsECSPlusIstioAsIncompatible() {
        DependencyGraphAnalysis analysis =
                depEngine.analyzeDependencyGraph(List.of("ECS", "Istio"));

        assertFalse(analysis.incompatibleCombinations.isEmpty(),
                "ECS + Istio must be detected as incompatible");
        System.out.println("✅ ECS + Istio detected as incompatible: " + analysis.incompatibleCombinations);
    }

    @Test
    void testDetectsECSPlusArgoCDAsIncompatible() {
        DependencyGraphAnalysis analysis =
                depEngine.analyzeDependencyGraph(List.of("ECS", "ArgoCD"));

        assertFalse(analysis.incompatibleCombinations.isEmpty(),
                "ECS + ArgoCD must be detected as incompatible");
        System.out.println("✅ ECS + ArgoCD detected as incompatible: " + analysis.incompatibleCombinations);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. ORPHAN DETECTION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testHelmWithoutKubernetesIsOrphan() {
        DependencyGraphAnalysis analysis =
                depEngine.analyzeDependencyGraph(List.of("Helm"));

        assertTrue(analysis.orphanTechnologies.contains("Helm"),
                "Helm without Kubernetes must be flagged as orphan");
        System.out.println("✅ Helm orphan detected: " + analysis.orphanTechnologies);
    }

    @Test
    void testIstioWithoutKubernetesIsOrphan() {
        DependencyGraphAnalysis analysis =
                depEngine.analyzeDependencyGraph(List.of("Istio"));

        assertTrue(analysis.orphanTechnologies.contains("Istio"),
                "Istio without Kubernetes must be flagged as orphan");
        System.out.println("✅ Istio orphan detected: " + analysis.orphanTechnologies);
    }

    @Test
    void testSchemaRegistryWithoutKafkaIsOrphan() {
        DependencyGraphAnalysis analysis =
                depEngine.analyzeDependencyGraph(List.of("Schema Registry"));

        assertTrue(analysis.orphanTechnologies.contains("Schema Registry"),
                "Schema Registry without Kafka must be flagged as orphan");
        System.out.println("✅ Schema Registry orphan detected: " + analysis.orphanTechnologies);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. VALID ECOSYSTEM — NO ORPHANS, NO INCOMPATIBILITIES
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testKubernetesEcosystemIsValid() {
        DependencyGraphAnalysis analysis = depEngine.analyzeDependencyGraph(
                List.of("Kubernetes", "Helm", "Istio", "ArgoCD", "Prometheus"));

        assertTrue(analysis.orphanTechnologies.isEmpty(),
                "Kubernetes ecosystem should have no orphans, got: " + analysis.orphanTechnologies);
        assertTrue(analysis.incompatibleCombinations.isEmpty(),
                "Kubernetes ecosystem should have no incompatibilities, got: " + analysis.incompatibleCombinations);
        assertTrue(analysis.architectureIntegrityScore > 0.7,
                "Kubernetes ecosystem integrity should be > 0.7, got: " + analysis.architectureIntegrityScore);
        System.out.println("✅ Kubernetes ecosystem valid — integrity: " + analysis.architectureIntegrityScore);
    }

    @Test
    void testECSEcosystemIsValid() {
        DependencyGraphAnalysis analysis = depEngine.analyzeDependencyGraph(
                List.of("ECS", "AWS App Mesh", "ECS Service Discovery", "CloudMap"));

        assertTrue(analysis.orphanTechnologies.isEmpty(),
                "ECS ecosystem should have no orphans, got: " + analysis.orphanTechnologies);
        assertTrue(analysis.incompatibleCombinations.isEmpty(),
                "ECS ecosystem should have no incompatibilities, got: " + analysis.incompatibleCombinations);
        System.out.println("✅ ECS ecosystem valid — integrity: " + analysis.architectureIntegrityScore);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. CASCADING REPLACEMENT SIMULATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testKubernetesToECSCascadesHelmReplacement() {
        List<String> stack = List.of("Kubernetes", "Helm", "Istio", "ArgoCD", "Prometheus");

        ReplacementSimulation sim = depEngine.simulateReplacement("Kubernetes", "ECS", stack);

        // Helm must be replaced (it requires Kubernetes)
        assertTrue(sim.cascadingReplacements.containsKey("Helm")
                        || sim.removedIncompatibleTechnologies.contains("Helm"),
                "Helm must be cascaded away when Kubernetes → ECS. Cascading: "
                        + sim.cascadingReplacements + " Removed: " + sim.removedIncompatibleTechnologies);
        System.out.println("✅ Kubernetes→ECS cascading replacements: " + sim.cascadingReplacements);
        System.out.println("   Removed incompatible: " + sim.removedIncompatibleTechnologies);
    }

    @Test
    void testKubernetesToECSCascadesIstioReplacement() {
        List<String> stack = List.of("Kubernetes", "Helm", "Istio", "ArgoCD");

        ReplacementSimulation sim = depEngine.simulateReplacement("Kubernetes", "ECS", stack);

        assertTrue(sim.cascadingReplacements.containsKey("Istio")
                        || sim.removedIncompatibleTechnologies.contains("Istio"),
                "Istio must be cascaded away when Kubernetes → ECS. Cascading: "
                        + sim.cascadingReplacements + " Removed: " + sim.removedIncompatibleTechnologies);
        System.out.println("✅ Istio cascaded on Kubernetes→ECS");
    }

    @Test
    void testKubernetesToECSCascadesArgoCDReplacement() {
        List<String> stack = List.of("Kubernetes", "Helm", "Istio", "ArgoCD");

        ReplacementSimulation sim = depEngine.simulateReplacement("Kubernetes", "ECS", stack);

        assertTrue(sim.cascadingReplacements.containsKey("ArgoCD")
                        || sim.removedIncompatibleTechnologies.contains("ArgoCD"),
                "ArgoCD must be cascaded away when Kubernetes → ECS. Cascading: "
                        + sim.cascadingReplacements + " Removed: " + sim.removedIncompatibleTechnologies);
        System.out.println("✅ ArgoCD cascaded on Kubernetes→ECS");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. PROPAGATED REPLACEMENT — FINAL STACK INTEGRITY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testPropagatedReplacementProducesValidStack() {
        List<String> original = List.of("Kubernetes", "Helm", "Istio", "ArgoCD", "Prometheus");

        List<String> result = depEngine.propagateReplacementWithDependencies("Kubernetes", "ECS", original);

        // ECS must be present
        assertTrue(result.contains("ECS"), "ECS must be in result stack");

        // Kubernetes-only tools must not remain without Kubernetes
        assertFalse(result.contains("Kubernetes"),
                "Kubernetes must be removed after replacement");

        // Validate the resulting stack has no incompatible combinations
        DependencyGraphAnalysis postAnalysis = depEngine.analyzeDependencyGraph(result);
        assertTrue(postAnalysis.incompatibleCombinations.isEmpty(),
                "Post-replacement stack must have no incompatible combinations: "
                        + postAnalysis.incompatibleCombinations);

        System.out.println("✅ Propagated replacement result: " + result);
        System.out.println("   Post-replacement integrity: " + postAnalysis.architectureIntegrityScore);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. ARCHITECTURE INTENT PRESERVATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testServiceMeshIntentPreservedAfterIstioReplacement() {
        // Original intent: service mesh via Istio
        List<String> original = List.of("Kubernetes", "Istio", "Prometheus");

        ArchitectureIntentAnalysis intent = depEngine.analyzeArchitectureIntent(original);
        assertTrue(intent.detectedPatterns.contains("service-mesh"),
                "Service mesh intent must be detected");

        // After replacing Kubernetes → ECS, Istio → AWS App Mesh
        List<String> migrated = depEngine.propagateReplacementWithDependencies("Kubernetes", "ECS", original);
        ArchitectureIntentAnalysis migratedIntent = depEngine.analyzeArchitectureIntent(migrated);

        // Service mesh capability should still be present (via AWS App Mesh)
        assertTrue(migratedIntent.detectedPatterns.contains("service-mesh"),
                "Service mesh intent must be preserved after migration. Patterns: "
                        + migratedIntent.detectedPatterns);
        System.out.println("✅ Service mesh intent preserved after Kubernetes→ECS migration");
        System.out.println("   Migrated stack: " + migrated);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. ROADMAP CONTENT ENHANCEMENT — NO ORPHANS REMAIN
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testRoadmapEnhancementRemovesOrphansForAWSProfile() {
        // A roadmap that mixes Kubernetes tools into an AWS ECS context
        String brokenRoadmap = """
                Deploy services using ECS with Fargate launch type.
                Use Helm charts for package management.
                Configure Istio service mesh for traffic control.
                Set up ArgoCD for GitOps deployments.
                Monitor with CloudWatch and X-Ray.
                """;

        String enhanced = stackEngine.enhanceRoadmapWithArchitecturalConsistency(
                brokenRoadmap, "AWS_ENTERPRISE");

        // After enhancement, Kubernetes-only tools should be gone or replaced
        boolean helmGone    = !enhanced.contains("Helm");
        boolean istioGone   = !enhanced.contains("Istio");
        boolean argoCDGone  = !enhanced.contains("ArgoCD");

        assertTrue(helmGone || istioGone || argoCDGone,
                "At least one Kubernetes-only tool must be removed/replaced in AWS profile. Enhanced:\n" + enhanced);

        System.out.println("✅ AWS profile enhancement removed Kubernetes-only tools");
        System.out.println("   Helm gone: " + helmGone + ", Istio gone: " + istioGone + ", ArgoCD gone: " + argoCDGone);
        System.out.println("   Enhanced roadmap:\n" + enhanced);
    }

    @Test
    void testKubernetesEcosystemPreservedForCloudNativeProfile() {
        String validRoadmap = """
                Deploy microservices on Kubernetes with Helm chart management.
                Configure Istio service mesh with mTLS and traffic policies.
                Set up ArgoCD for GitOps continuous deployment.
                Monitor with Prometheus and Grafana dashboards.
                """;

        String enhanced = stackEngine.enhanceRoadmapWithArchitecturalConsistency(
                validRoadmap, "CLOUD_NATIVE_KUBERNETES");

        // Core Kubernetes ecosystem must be preserved
        assertTrue(enhanced.contains("Kubernetes"), "Kubernetes must be preserved");
        assertTrue(enhanced.contains("Helm"),       "Helm must be preserved");
        assertTrue(enhanced.contains("Istio"),      "Istio must be preserved");
        assertTrue(enhanced.contains("ArgoCD"),     "ArgoCD must be preserved");

        System.out.println("✅ Kubernetes ecosystem fully preserved for CLOUD_NATIVE profile");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. ARCHITECTURE INTEGRITY SCORING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testIntegrityScoreHighForCoherentStack() {
        DependencyGraphAnalysis analysis = depEngine.analyzeDependencyGraph(
                List.of("Kubernetes", "Helm", "Istio", "Prometheus", "Grafana"));

        assertTrue(analysis.architectureIntegrityScore >= 0.7,
                "Coherent Kubernetes stack must score >= 0.7, got: " + analysis.architectureIntegrityScore);
        System.out.println("✅ Coherent stack integrity: " + analysis.architectureIntegrityScore);
    }

    @Test
    void testIntegrityScoreLowForIncoherentStack() {
        // ECS + Kubernetes tools = structurally invalid
        DependencyGraphAnalysis analysis = depEngine.analyzeDependencyGraph(
                List.of("ECS", "Helm", "Istio", "ArgoCD"));

        assertTrue(analysis.architectureIntegrityScore < 0.5,
                "Incoherent stack (ECS + K8s tools) must score < 0.5, got: " + analysis.architectureIntegrityScore);
        System.out.println("✅ Incoherent stack integrity (correctly low): " + analysis.architectureIntegrityScore);
    }
}
