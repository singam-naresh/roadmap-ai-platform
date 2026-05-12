package com.assistant.service;

import com.assistant.service.TechnologyOntologyEngine.TechnologyRelationshipAnalysis;
import com.assistant.service.StackConsistencyEngine.ArchitectureProfile;
import com.assistant.service.StackConsistencyEngine.ConsistencyValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 3D - Technology Relationship Graph Engine Validation Tests
 * 
 * Validates that the relationship-aware technology intelligence prevents semantic replacement errors
 * and ensures proper technology relationships are maintained.
 */
@SpringBootTest
public class Phase3DRelationshipValidationTest {

    private TechnologyOntologyEngine ontologyEngine;
    private DependencyGraphEngine dependencyGraphEngine;
    private StackConsistencyEngine stackConsistencyEngine;

    @BeforeEach
    void setUp() {
        ontologyEngine = new TechnologyOntologyEngine();
        dependencyGraphEngine = new DependencyGraphEngine();
        stackConsistencyEngine = new StackConsistencyEngine(ontologyEngine, dependencyGraphEngine);
    }

    @Test
    void testPreventSemanticReplacementErrors() {
        // CRITICAL TEST: Ensure Kubernetes is NEVER replaced with Helm
        assertFalse(ontologyEngine.isValidReplacement("Kubernetes", "Helm"),
            "CRITICAL FAILURE: Kubernetes should NEVER be replaced with Helm (Helm is a Kubernetes tool, not alternative)");
        
        // CRITICAL TEST: Ensure Kafka is NEVER replaced with Prometheus
        assertFalse(ontologyEngine.isValidReplacement("Apache Kafka", "Prometheus"),
            "CRITICAL FAILURE: Kafka should NEVER be replaced with Prometheus (different categories entirely)");
        
        // CRITICAL TEST: Ensure PostgreSQL is NEVER replaced with Redis
        assertFalse(ontologyEngine.isValidReplacement("PostgreSQL", "Redis"),
            "CRITICAL FAILURE: PostgreSQL should NEVER be replaced with Redis (different database paradigms)");
        
        System.out.println("✅ PHASE 3D: Semantic replacement error prevention - PASSED");
    }

    @Test
    void testValidAlternativeReplacements() {
        // Valid orchestration alternatives
        assertTrue(ontologyEngine.isValidReplacement("Kubernetes", "ECS"),
            "Kubernetes should be replaceable with ECS (both container orchestration)");
        
        assertTrue(ontologyEngine.isValidReplacement("ECS", "Docker Swarm"),
            "ECS should be replaceable with Docker Swarm (both container orchestration)");
        
        // Valid messaging alternatives
        assertTrue(ontologyEngine.isValidReplacement("Apache Kafka", "Apache Pulsar"),
            "Kafka should be replaceable with Pulsar (both event streaming platforms)");
        
        assertTrue(ontologyEngine.isValidReplacement("RabbitMQ", "SQS"),
            "RabbitMQ should be replaceable with SQS (both message brokers)");
        
        // Valid database alternatives
        assertTrue(ontologyEngine.isValidReplacement("PostgreSQL", "MySQL"),
            "PostgreSQL should be replaceable with MySQL (both relational databases)");
        
        System.out.println("✅ PHASE 3D: Valid alternative replacements - PASSED");
    }

    @Test
    void testComplementaryRelationships() {
        // Kubernetes ecosystem complements
        List<String> kubernetesComplements = ontologyEngine.findComplementaryTechnologies("Kubernetes");
        assertTrue(kubernetesComplements.contains("Helm"),
            "Helm should complement Kubernetes (Helm manages Kubernetes packages)");
        assertTrue(kubernetesComplements.contains("Istio"),
            "Istio should complement Kubernetes (Istio provides service mesh for Kubernetes)");
        assertTrue(kubernetesComplements.contains("ArgoCD"),
            "ArgoCD should complement Kubernetes (ArgoCD provides GitOps for Kubernetes)");
        
        // Kafka ecosystem complements
        List<String> kafkaComplements = ontologyEngine.findComplementaryTechnologies("Apache Kafka");
        assertTrue(kafkaComplements.contains("Schema Registry"),
            "Schema Registry should complement Kafka (manages Kafka schemas)");
        
        // PostgreSQL complements
        List<String> postgresComplements = ontologyEngine.findComplementaryTechnologies("PostgreSQL");
        assertTrue(postgresComplements.contains("PgBouncer"),
            "PgBouncer should complement PostgreSQL (connection pooling for PostgreSQL)");
        
        System.out.println("✅ PHASE 3D: Complementary relationships - PASSED");
    }

    @Test
    void testDependencyValidation() {
        // Helm requires Kubernetes
        TechnologyRelationshipAnalysis helmAnalysis = ontologyEngine.analyzeTechnologyRelationships(
            List.of("Helm"));
        assertTrue(helmAnalysis.missingDependencies.contains("Helm requires Kubernetes"),
            "Helm should require Kubernetes dependency");
        
        // Istio requires Kubernetes
        TechnologyRelationshipAnalysis istioAnalysis = ontologyEngine.analyzeTechnologyRelationships(
            List.of("Istio"));
        assertTrue(istioAnalysis.missingDependencies.contains("Istio requires Kubernetes"),
            "Istio should require Kubernetes dependency");
        
        // Schema Registry requires Kafka
        TechnologyRelationshipAnalysis schemaAnalysis = ontologyEngine.analyzeTechnologyRelationships(
            List.of("Schema Registry"));
        assertTrue(schemaAnalysis.missingDependencies.contains("Schema Registry requires Apache Kafka"),
            "Schema Registry should require Kafka dependency");
        
        System.out.println("✅ PHASE 3D: Dependency validation - PASSED");
    }

    @Test
    void testConflictDetection() {
        // SQS conflicts with Kafka
        TechnologyRelationshipAnalysis conflictAnalysis = ontologyEngine.analyzeTechnologyRelationships(
            List.of("SQS", "Apache Kafka"));
        assertFalse(conflictAnalysis.conflicts.isEmpty(),
            "SQS and Kafka should be detected as conflicting technologies");
        
        // CloudWatch conflicts with Prometheus
        TechnologyRelationshipAnalysis monitoringConflict = ontologyEngine.analyzeTechnologyRelationships(
            List.of("CloudWatch", "Prometheus"));
        assertFalse(monitoringConflict.conflicts.isEmpty(),
            "CloudWatch and Prometheus should be detected as conflicting");
        
        System.out.println("✅ PHASE 3D: Conflict detection - PASSED");
    }

    @Test
    void testArchitecturalConsistencyWithRelationships() {
        String kubernetesRoadmap = """
            Build a cloud-native platform using Kubernetes for orchestration.
            Deploy Helm for package management and Istio for service mesh.
            Use Prometheus for monitoring and ArgoCD for GitOps deployment.
            """;
        
        // This should be enhanced, not broken by replacements
        String enhanced = stackConsistencyEngine.enhanceRoadmapWithArchitecturalConsistency(
            kubernetesRoadmap, "CLOUD_NATIVE_KUBERNETES");
        
        // Verify Kubernetes ecosystem is preserved
        assertTrue(enhanced.contains("Kubernetes"),
            "Kubernetes should be preserved in cloud-native profile");
        assertTrue(enhanced.contains("Helm"),
            "Helm should be preserved as Kubernetes complement");
        assertTrue(enhanced.contains("Istio"),
            "Istio should be preserved as Kubernetes complement");
        assertTrue(enhanced.contains("Prometheus"),
            "Prometheus should be preserved in cloud-native stack");
        
        System.out.println("✅ PHASE 3D: Architectural consistency with relationships - PASSED");
    }

    @Test
    void testBestReplacementSelection() {
        // Test best replacement for Kubernetes in AWS profile
        String bestReplacement = ontologyEngine.findBestReplacement(
            "Kubernetes", "AWS_ENTERPRISE", List.of("ECS", "Lambda", "CloudWatch"));
        
        assertEquals("ECS", bestReplacement,
            "ECS should be the best Kubernetes replacement in AWS Enterprise profile");
        
        // Test best replacement for Kafka in AWS profile
        String kafkaReplacement = ontologyEngine.findBestReplacement(
            "Apache Kafka", "AWS_ENTERPRISE", List.of("SQS", "SNS", "Kinesis"));
        
        assertTrue(List.of("SQS", "Kinesis").contains(kafkaReplacement),
            "SQS or Kinesis should be the best Kafka replacement in AWS profile");
        
        System.out.println("✅ PHASE 3D: Best replacement selection - PASSED");
    }

    @Test
    void testRelationshipCoherenceScoring() {
        // High coherence: Kubernetes ecosystem
        TechnologyRelationshipAnalysis highCoherence = ontologyEngine.analyzeTechnologyRelationships(
            List.of("Kubernetes", "Helm", "Istio", "Prometheus", "Grafana"));
        
        assertTrue(highCoherence.coherenceScore > 0.7,
            "Kubernetes ecosystem should have high coherence score: " + highCoherence.coherenceScore);
        
        // Low coherence: Mixed conflicting technologies
        TechnologyRelationshipAnalysis lowCoherence = ontologyEngine.analyzeTechnologyRelationships(
            List.of("Kubernetes", "ECS", "SQS", "Apache Kafka"));
        
        assertTrue(lowCoherence.coherenceScore < 0.5,
            "Mixed conflicting technologies should have low coherence score: " + lowCoherence.coherenceScore);
        
        System.out.println("✅ PHASE 3D: Relationship coherence scoring - PASSED");
    }

    @Test
    void testCriticalSemanticErrorPrevention() {
        // Test the most critical semantic errors that were happening before Phase 3D
        
        // 1. Infrastructure platform → Tool replacement (FORBIDDEN)
        assertFalse(ontologyEngine.isValidReplacement("Kubernetes", "Helm"));
        assertFalse(ontologyEngine.isValidReplacement("Docker Swarm", "Helm"));
        assertFalse(ontologyEngine.isValidReplacement("ECS", "Helm"));
        
        // 2. Database → Monitoring tool replacement (FORBIDDEN)
        assertFalse(ontologyEngine.isValidReplacement("PostgreSQL", "Prometheus"));
        assertFalse(ontologyEngine.isValidReplacement("MongoDB", "Grafana"));
        assertFalse(ontologyEngine.isValidReplacement("Redis", "CloudWatch"));
        
        // 3. Messaging platform → Component replacement (FORBIDDEN)
        assertFalse(ontologyEngine.isValidReplacement("Apache Kafka", "Schema Registry"));
        assertFalse(ontologyEngine.isValidReplacement("RabbitMQ", "Prometheus"));
        
        // 4. Orchestrator → Package manager replacement (FORBIDDEN)
        assertFalse(ontologyEngine.isValidReplacement("Docker Swarm", "Helm"));
        assertFalse(ontologyEngine.isValidReplacement("Nomad", "Helm"));
        
        System.out.println("✅ PHASE 3D: Critical semantic error prevention - PASSED");
        System.out.println("🎉 ALL PHASE 3D RELATIONSHIP VALIDATION TESTS PASSED!");
    }
}