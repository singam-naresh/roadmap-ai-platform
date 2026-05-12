package com.assistant.service;

import com.assistant.service.StackConsistencyEngine.ArchitectureAnalysis;
import com.assistant.service.StackConsistencyEngine.ConsistencyValidation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class StackConsistencyEngineTest {

    private final TechnologyOntologyEngine ontologyEngine = new TechnologyOntologyEngine();
    private final DependencyGraphEngine dependencyGraphEngine = new DependencyGraphEngine();
    private final StackConsistencyEngine engine = new StackConsistencyEngine(ontologyEngine, dependencyGraphEngine);

    @Test
    public void testKubernetesArchitectureDetection() {
        String kubernetesInput = "Build a cloud-native platform using Kubernetes with microservices and service mesh";
        
        ArchitectureAnalysis analysis = engine.analyzeArchitecturalIntent(kubernetesInput, "DEVOPS");
        
        assertEquals("CLOUD_NATIVE_KUBERNETES", analysis.recommendedProfile);
        assertTrue(analysis.profileConfidence > 0.0, "Should have confidence in Kubernetes profile");
        assertTrue(analysis.consistentTechnologies.contains("Kubernetes"));
        assertTrue(analysis.consistentTechnologies.contains("Istio"));
        assertTrue(analysis.consistentTechnologies.contains("Prometheus"));
        
        System.out.println("Kubernetes Analysis:");
        System.out.println("Profile: " + analysis.recommendedProfile);
        System.out.println("Confidence: " + analysis.profileConfidence);
        System.out.println("Technologies: " + analysis.consistentTechnologies.size());
    }

    @Test
    public void testAWSEnterpriseArchitectureDetection() {
        String awsInput = "Build an enterprise backend using AWS services with ECS and managed databases";
        
        ArchitectureAnalysis analysis = engine.analyzeArchitecturalIntent(awsInput, "JAVA_BACKEND");
        
        assertEquals("AWS_ENTERPRISE", analysis.recommendedProfile);
        assertTrue(analysis.consistentTechnologies.contains("ECS"));
        assertTrue(analysis.consistentTechnologies.contains("RDS"));
        assertTrue(analysis.consistentTechnologies.contains("SQS"));
        assertTrue(analysis.consistentTechnologies.contains("CloudWatch"));
        
        System.out.println("AWS Analysis:");
        System.out.println("Profile: " + analysis.recommendedProfile);
        System.out.println("Technologies: " + analysis.consistentTechnologies);
    }

    @Test
    public void testAIInfrastructureArchitectureDetection() {
        String aiInput = "Build an AI inference platform with vLLM and vector databases for RAG";
        
        ArchitectureAnalysis analysis = engine.analyzeArchitecturalIntent(aiInput, "AI_ENGINEERING");
        
        assertEquals("AI_INFERENCE_PLATFORM", analysis.recommendedProfile);
        assertTrue(analysis.consistentTechnologies.contains("vLLM"));
        assertTrue(analysis.consistentTechnologies.contains("FAISS"));
        assertTrue(analysis.consistentTechnologies.contains("TensorRT-LLM"));
        
        System.out.println("AI Analysis:");
        System.out.println("Profile: " + analysis.recommendedProfile);
        System.out.println("Technologies: " + analysis.consistentTechnologies);
    }

    @Test
    public void testConsistentTechnologyStackValidation() {
        // Test consistent Kubernetes stack
        List<String> kubernetesStack = List.of("Kubernetes", "Helm", "Istio", "Prometheus", "Grafana");
        
        ConsistencyValidation validation = engine.validateTechnologyStack(kubernetesStack, "CLOUD_NATIVE_KUBERNETES");
        
        assertTrue(validation.isConsistent, "Kubernetes stack should be consistent");
        assertTrue(validation.consistencyScore > 0.8, "Should have high consistency score");
        assertTrue(validation.conflictingPairs.isEmpty(), "Should have no conflicts");
        
        System.out.println("Kubernetes Stack Validation:");
        System.out.println("Consistent: " + validation.isConsistent);
        System.out.println("Score: " + validation.consistencyScore);
        System.out.println("Conflicts: " + validation.conflictingPairs);
    }

    @Test
    public void testInconsistentTechnologyStackValidation() {
        // Test mixed AWS + Kubernetes stack (should be inconsistent)
        List<String> mixedStack = List.of("Kubernetes", "SQS", "Prometheus", "CloudWatch", "ECS");
        
        ConsistencyValidation validation = engine.validateTechnologyStack(mixedStack, "CLOUD_NATIVE_KUBERNETES");
        
        assertFalse(validation.isConsistent, "Mixed stack should be inconsistent");
        assertTrue(validation.consistencyScore < 0.7, "Should have low consistency score");
        assertFalse(validation.conflictingPairs.isEmpty(), "Should detect conflicts");
        
        System.out.println("Mixed Stack Validation:");
        System.out.println("Consistent: " + validation.isConsistent);
        System.out.println("Score: " + validation.consistencyScore);
        System.out.println("Conflicts: " + validation.conflictingPairs);
        System.out.println("Inconsistent techs: " + validation.inconsistentTechnologies);
    }

    @Test
    public void testArchitecturalConsistencyEnhancement() {
        // Test roadmap with mixed technologies
        String mixedRoadmap = """
            Set up Kubernetes cluster and configure SQS messaging.
            Deploy services using ECS and monitor with Prometheus.
            Use CloudWatch for logging and Istio for service mesh.
            """;
        
        String enhanced = engine.enhanceRoadmapWithArchitecturalConsistency(mixedRoadmap, "CLOUD_NATIVE_KUBERNETES");
        
        System.out.println("Original roadmap: " + mixedRoadmap);
        System.out.println("Enhanced roadmap: " + enhanced);
        
        // Should replace AWS services with Kubernetes-compatible alternatives
        assertFalse(enhanced.toLowerCase().contains("sqs"), "Should replace SQS");
        assertFalse(enhanced.toLowerCase().contains("ecs"), "Should replace ECS");
        assertFalse(enhanced.toLowerCase().contains("cloudwatch"), "Should replace CloudWatch");
        
        // Should keep Kubernetes-compatible technologies
        assertTrue(enhanced.toLowerCase().contains("kubernetes"), "Should keep Kubernetes");
        assertTrue(enhanced.toLowerCase().contains("prometheus"), "Should keep Prometheus");
        assertTrue(enhanced.toLowerCase().contains("istio"), "Should keep Istio");
    }

    @Test
    public void testConsistentTechnologyGeneration() {
        // Test generating consistent technologies for different profiles
        List<String> kubernetesMonitoring = engine.generateConsistentTechnologyStack(
            "CLOUD_NATIVE_KUBERNETES", "MONITORING", 3);
        
        assertFalse(kubernetesMonitoring.isEmpty(), "Should generate monitoring technologies");
        assertTrue(kubernetesMonitoring.contains("Prometheus") || 
                  kubernetesMonitoring.contains("Grafana") || 
                  kubernetesMonitoring.contains("Jaeger"), "Should contain Kubernetes monitoring tools");
        
        System.out.println("Kubernetes monitoring stack: " + kubernetesMonitoring);
        
        List<String> awsCompute = engine.generateConsistentTechnologyStack(
            "AWS_ENTERPRISE", "COMPUTE", 3);
        
        assertFalse(awsCompute.isEmpty(), "Should generate compute technologies");
        assertTrue(awsCompute.contains("ECS") || 
                  awsCompute.contains("EKS") || 
                  awsCompute.contains("Lambda"), "Should contain AWS compute services");
        
        System.out.println("AWS compute stack: " + awsCompute);
    }

    @Test
    public void testHighScaleDistributedSystemsProfile() {
        String distributedInput = "Build a high-throughput event-driven system with Kafka and Cassandra";
        
        ArchitectureAnalysis analysis = engine.analyzeArchitecturalIntent(distributedInput, "SYSTEM_DESIGN");
        
        assertEquals("HIGH_SCALE_DISTRIBUTED_SYSTEMS", analysis.recommendedProfile);
        assertTrue(analysis.consistentTechnologies.contains("Apache Kafka"));
        assertTrue(analysis.consistentTechnologies.contains("Apache Cassandra"));
        assertTrue(analysis.consistentTechnologies.contains("Redis Cluster"));
        assertTrue(analysis.consistentTechnologies.contains("gRPC"));
        
        // Test validation of distributed systems stack
        List<String> distributedStack = List.of("Apache Kafka", "Apache Cassandra", "Redis Cluster", "gRPC", "Envoy Proxy");
        ConsistencyValidation validation = engine.validateTechnologyStack(distributedStack, "HIGH_SCALE_DISTRIBUTED_SYSTEMS");
        
        assertTrue(validation.isConsistent, "Distributed systems stack should be consistent");
        assertTrue(validation.consistencyScore > 0.8, "Should have high consistency score");
        
        System.out.println("Distributed Systems Analysis:");
        System.out.println("Profile: " + analysis.recommendedProfile);
        System.out.println("Consistency Score: " + validation.consistencyScore);
    }

    @Test
    public void testStartupMVPProfile() {
        String startupInput = "Build a simple MVP with Next.js and Supabase for rapid development";
        
        ArchitectureAnalysis analysis = engine.analyzeArchitecturalIntent(startupInput, "STARTUP");
        
        assertEquals("STARTUP_MVP", analysis.recommendedProfile);
        assertTrue(analysis.consistentTechnologies.contains("Next.js"));
        assertTrue(analysis.consistentTechnologies.contains("Supabase"));
        assertTrue(analysis.consistentTechnologies.contains("Vercel"));
        assertTrue(analysis.consistentTechnologies.contains("Stripe"));
        
        // Test that complex technologies are avoided in startup profile
        List<String> startupStack = List.of("Next.js", "Supabase", "NextAuth.js", "Vercel", "Stripe");
        ConsistencyValidation validation = engine.validateTechnologyStack(startupStack, "STARTUP_MVP");
        
        assertTrue(validation.isConsistent, "Startup MVP stack should be consistent");
        
        System.out.println("Startup MVP Analysis:");
        System.out.println("Profile: " + analysis.recommendedProfile);
        System.out.println("Technologies: " + analysis.consistentTechnologies);
    }

    @Test
    public void testConflictDetection() {
        // Test specific technology conflicts
        List<String> conflictingStack1 = List.of("SQS", "Kafka"); // Messaging conflict
        ConsistencyValidation validation1 = engine.validateTechnologyStack(conflictingStack1, "AWS_ENTERPRISE");
        
        assertFalse(validation1.isConsistent, "SQS + Kafka should be inconsistent");
        assertFalse(validation1.conflictingPairs.isEmpty(), "Should detect messaging conflict");
        
        List<String> conflictingStack2 = List.of("CloudWatch", "Prometheus"); // Monitoring conflict
        ConsistencyValidation validation2 = engine.validateTechnologyStack(conflictingStack2, "AWS_ENTERPRISE");
        
        assertFalse(validation2.isConsistent, "CloudWatch + Prometheus should be inconsistent");
        
        List<String> conflictingStack3 = List.of("ECS", "Kubernetes"); // Orchestration conflict
        ConsistencyValidation validation3 = engine.validateTechnologyStack(conflictingStack3, "AWS_ENTERPRISE");
        
        assertFalse(validation3.isConsistent, "ECS + Kubernetes should be inconsistent");
        
        System.out.println("Conflict Detection Results:");
        System.out.println("SQS + Kafka conflicts: " + validation1.conflictingPairs);
        System.out.println("CloudWatch + Prometheus conflicts: " + validation2.conflictingPairs);
        System.out.println("ECS + Kubernetes conflicts: " + validation3.conflictingPairs);
    }
}