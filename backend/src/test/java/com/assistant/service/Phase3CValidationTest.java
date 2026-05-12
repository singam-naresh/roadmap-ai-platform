package com.assistant.service;

import com.assistant.service.StackConsistencyEngine.ArchitectureAnalysis;
import com.assistant.service.StackConsistencyEngine.ConsistencyValidation;
import com.assistant.service.TechnicalSpecificityEngine.SpecificityAnalysis;
import com.assistant.service.RoadmapQualityScorer.QualityScore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class Phase3CValidationTest {

    private final TechnologyOntologyEngine ontologyEngine = new TechnologyOntologyEngine();
    private final DependencyGraphEngine dependencyGraphEngine = new DependencyGraphEngine();
    private final StackConsistencyEngine stackEngine = new StackConsistencyEngine(ontologyEngine, dependencyGraphEngine);
    private final TechnicalSpecificityEngine specificityEngine = new TechnicalSpecificityEngine();
    private final RoadmapQualityScorer qualityScorer = new RoadmapQualityScorer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testKubernetesPlatformEngineeringRoadmap() throws Exception {
        String kubernetesRoadmap = """
            {
              "summary": "Build production-ready Kubernetes platform with GitOps and observability",
              "steps": [
                "Set up Kubernetes cluster with Helm package management and RBAC configuration",
                "Deploy Istio service mesh with Envoy sidecars and traffic management policies",
                "Configure Prometheus monitoring with Grafana dashboards and AlertManager rules",
                "Implement ArgoCD GitOps workflow with automated deployment pipelines",
                "Set up distributed tracing with Jaeger and OpenTelemetry instrumentation",
                "Configure Loki log aggregation with Fluentd collectors and retention policies",
                "Implement pod autoscaling with HPA and VPA based on custom metrics"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(kubernetesRoadmap);
        
        // Test architecture detection
        ArchitectureAnalysis archAnalysis = stackEngine.analyzeArchitecturalIntent(
            kubernetesRoadmap, "DEVOPS");
        
        assertEquals("CLOUD_NATIVE_KUBERNETES", archAnalysis.recommendedProfile);
        assertTrue(archAnalysis.profileConfidence > 0.3, "Should have high confidence");
        
        // Test technology extraction and consistency
        List<String> technologies = List.of("Kubernetes", "Helm", "Istio", "Envoy", "Prometheus", 
                                           "Grafana", "ArgoCD", "Jaeger", "OpenTelemetry", "Loki", "Fluentd");
        ConsistencyValidation consistency = stackEngine.validateTechnologyStack(
            technologies, "CLOUD_NATIVE_KUBERNETES");
        
        assertTrue(consistency.isConsistent, "Kubernetes stack should be consistent");
        assertTrue(consistency.consistencyScore > 0.8, "Should have high consistency score");
        assertTrue(consistency.conflictingPairs.isEmpty(), "Should have no conflicts");
        
        // Test combined quality scoring
        SpecificityAnalysis specificity = specificityEngine.analyzeSpecificity(
            kubernetesRoadmap, "DEVOPS");
        QualityScore quality = qualityScorer.scoreRoadmapWithSpecificityAndConsistency(
            roadmapJson, "DEVOPS", specificity, consistency);
        
        System.out.println("=== KUBERNETES PLATFORM ENGINEERING ROADMAP ===");
        System.out.println("Architecture Profile: " + archAnalysis.recommendedProfile);
        System.out.println("Profile Confidence: " + archAnalysis.profileConfidence);
        System.out.println("Consistency Score: " + consistency.consistencyScore);
        System.out.println("Specificity Score: " + specificity.specificityScore);
        System.out.println("Overall Quality: " + quality.overallScore);
        System.out.println("Quality Level: " + quality.qualityLevel);
        System.out.println("Technologies: " + technologies);
        System.out.println("Conflicts: " + consistency.conflictingPairs);
        
        assertTrue(quality.overallScore > 0.8, "Should achieve high quality score");
        assertEquals("EXCELLENT", quality.qualityLevel);
    }

    @Test
    public void testHighScaleDistributedSystemsRoadmap() throws Exception {
        String distributedRoadmap = """
            {
              "summary": "Build high-throughput distributed event-driven architecture",
              "steps": [
                "Implement Apache Kafka cluster with Schema Registry and multi-partition topics",
                "Deploy Apache Cassandra with consistent hashing and tunable consistency levels",
                "Configure Redis Cluster for distributed caching with sentinel failover",
                "Implement gRPC services with Protocol Buffers and connection pooling",
                "Set up Envoy Proxy load balancing with circuit breaker patterns",
                "Deploy CQRS pattern with event sourcing using Kafka event store",
                "Implement Saga pattern for distributed transaction coordination"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(distributedRoadmap);
        
        // Test architecture detection
        ArchitectureAnalysis archAnalysis = stackEngine.analyzeArchitecturalIntent(
            distributedRoadmap, "SYSTEM_DESIGN");
        
        assertEquals("HIGH_SCALE_DISTRIBUTED_SYSTEMS", archAnalysis.recommendedProfile);
        
        // Test technology consistency
        List<String> technologies = List.of("Apache Kafka", "Apache Cassandra", "Redis Cluster", 
                                           "gRPC", "Envoy Proxy", "CQRS", "Saga Pattern");
        ConsistencyValidation consistency = stackEngine.validateTechnologyStack(
            technologies, "HIGH_SCALE_DISTRIBUTED_SYSTEMS");
        
        assertTrue(consistency.isConsistent, "Distributed systems stack should be consistent");
        assertTrue(consistency.consistencyScore > 0.8, "Should have high consistency score");
        
        System.out.println("=== HIGH-SCALE DISTRIBUTED SYSTEMS ROADMAP ===");
        System.out.println("Architecture Profile: " + archAnalysis.recommendedProfile);
        System.out.println("Consistency Score: " + consistency.consistencyScore);
        System.out.println("Technologies: " + technologies);
        System.out.println("Conflicts: " + consistency.conflictingPairs);
        
        assertTrue(consistency.conflictingPairs.isEmpty(), "Should have no architectural conflicts");
    }

    @Test
    public void testAIInfrastructureRoadmap() throws Exception {
        String aiRoadmap = """
            {
              "summary": "Build production AI inference platform with optimization",
              "steps": [
                "Implement model quantization using QLoRA and INT8 optimization techniques",
                "Deploy vLLM inference server with TensorRT-LLM acceleration and batching",
                "Configure FAISS vector database with semantic chunking and hybrid retrieval",
                "Set up Ray Serve for distributed model serving with auto-scaling",
                "Implement DeepSpeed training pipeline with ZeRO-3 optimization",
                "Deploy Kubernetes cluster with GPU node pools and resource quotas",
                "Configure Prometheus monitoring for GPU utilization and inference latency"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(aiRoadmap);
        
        // Test architecture detection
        ArchitectureAnalysis archAnalysis = stackEngine.analyzeArchitecturalIntent(
            aiRoadmap, "AI_ENGINEERING");
        
        assertEquals("AI_INFERENCE_PLATFORM", archAnalysis.recommendedProfile);
        
        // Test technology consistency
        List<String> technologies = List.of("QLoRA", "vLLM", "TensorRT-LLM", "FAISS", 
                                           "Ray Serve", "DeepSpeed", "Kubernetes", "Prometheus");
        ConsistencyValidation consistency = stackEngine.validateTechnologyStack(
            technologies, "AI_INFERENCE_PLATFORM");
        
        assertTrue(consistency.isConsistent, "AI infrastructure stack should be consistent");
        
        System.out.println("=== AI INFERENCE INFRASTRUCTURE ROADMAP ===");
        System.out.println("Architecture Profile: " + archAnalysis.recommendedProfile);
        System.out.println("Consistency Score: " + consistency.consistencyScore);
        System.out.println("Technologies: " + technologies);
        
        assertTrue(consistency.consistencyScore > 0.7, "Should have good consistency score");
    }

    @Test
    public void testAWSEnterpriseBackendRoadmap() throws Exception {
        String awsRoadmap = """
            {
              "summary": "Build enterprise-grade AWS backend with managed services",
              "steps": [
                "Set up VPC with multi-AZ subnets and NAT gateways for high availability",
                "Deploy ECS Fargate cluster with Application Load Balancer and auto-scaling",
                "Configure RDS Aurora PostgreSQL with read replicas and automated backups",
                "Implement SQS message queues with DLQ and SNS notification patterns",
                "Set up CloudWatch monitoring with custom metrics and CloudTrail logging",
                "Deploy CodePipeline CI/CD with CodeBuild and CloudFormation IaC",
                "Configure IAM roles and policies with least privilege access principles"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(awsRoadmap);
        
        // Test architecture detection
        ArchitectureAnalysis archAnalysis = stackEngine.analyzeArchitecturalIntent(
            awsRoadmap, "JAVA_BACKEND");
        
        assertEquals("AWS_ENTERPRISE", archAnalysis.recommendedProfile);
        
        // Test technology consistency
        List<String> technologies = List.of("VPC", "ECS", "Fargate", "ALB", "RDS", "Aurora", 
                                           "SQS", "SNS", "CloudWatch", "CodePipeline", "IAM");
        ConsistencyValidation consistency = stackEngine.validateTechnologyStack(
            technologies, "AWS_ENTERPRISE");
        
        assertTrue(consistency.isConsistent, "AWS enterprise stack should be consistent");
        assertTrue(consistency.conflictingPairs.isEmpty(), "Should have no AWS conflicts");
        
        System.out.println("=== AWS ENTERPRISE BACKEND ROADMAP ===");
        System.out.println("Architecture Profile: " + archAnalysis.recommendedProfile);
        System.out.println("Consistency Score: " + consistency.consistencyScore);
        System.out.println("Technologies: " + technologies);
    }

    @Test
    public void testInconsistentMixedArchitectureDetection() throws Exception {
        String mixedRoadmap = """
            {
              "summary": "Build platform with mixed technologies (should be inconsistent)",
              "steps": [
                "Set up Kubernetes cluster with ECS container orchestration",
                "Configure Prometheus monitoring with CloudWatch metrics collection",
                "Deploy Kafka messaging with SQS queue integration",
                "Use Istio service mesh with API Gateway load balancing",
                "Implement ArgoCD GitOps with CodePipeline deployment"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(mixedRoadmap);
        
        // Test that mixed architecture is detected as inconsistent
        List<String> mixedTechnologies = List.of("Kubernetes", "ECS", "Prometheus", "CloudWatch", 
                                                "Kafka", "SQS", "Istio", "API Gateway", "ArgoCD", "CodePipeline");
        
        // Test against Kubernetes profile
        ConsistencyValidation kubernetesValidation = stackEngine.validateTechnologyStack(
            mixedTechnologies, "CLOUD_NATIVE_KUBERNETES");
        
        assertFalse(kubernetesValidation.isConsistent, "Mixed stack should be inconsistent with Kubernetes");
        assertFalse(kubernetesValidation.conflictingPairs.isEmpty(), "Should detect conflicts");
        assertTrue(kubernetesValidation.consistencyScore < 0.7, "Should have low consistency score");
        
        // Test against AWS profile
        ConsistencyValidation awsValidation = stackEngine.validateTechnologyStack(
            mixedTechnologies, "AWS_ENTERPRISE");
        
        assertFalse(awsValidation.isConsistent, "Mixed stack should be inconsistent with AWS");
        assertFalse(awsValidation.conflictingPairs.isEmpty(), "Should detect conflicts");
        
        System.out.println("=== MIXED ARCHITECTURE INCONSISTENCY TEST ===");
        System.out.println("Kubernetes Consistency Score: " + kubernetesValidation.consistencyScore);
        System.out.println("Kubernetes Conflicts: " + kubernetesValidation.conflictingPairs);
        System.out.println("AWS Consistency Score: " + awsValidation.consistencyScore);
        System.out.println("AWS Conflicts: " + awsValidation.conflictingPairs);
        
        // Test architectural enhancement
        String enhanced = stackEngine.enhanceRoadmapWithArchitecturalConsistency(
            mixedRoadmap, "CLOUD_NATIVE_KUBERNETES");
        
        System.out.println("Original: " + mixedRoadmap);
        System.out.println("Enhanced: " + enhanced);
        
        // Enhanced version should have fewer conflicts
        assertNotEquals(mixedRoadmap, enhanced, "Should enhance the roadmap");
    }

    @Test
    public void testStartupMVPSimplicityValidation() throws Exception {
        String startupRoadmap = """
            {
              "summary": "Build simple MVP with modern stack for rapid development",
              "steps": [
                "Set up Next.js full-stack application with TypeScript and Tailwind CSS",
                "Configure Supabase backend with PostgreSQL database and real-time subscriptions",
                "Implement NextAuth.js authentication with Google and GitHub providers",
                "Integrate Stripe payment processing with subscription billing",
                "Deploy to Vercel with automatic CI/CD and preview deployments",
                "Set up Sentry error monitoring and PostHog analytics tracking",
                "Configure Resend email service for transactional notifications"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(startupRoadmap);
        
        // Test architecture detection
        ArchitectureAnalysis archAnalysis = stackEngine.analyzeArchitecturalIntent(
            startupRoadmap, "STARTUP");
        
        assertEquals("STARTUP_MVP", archAnalysis.recommendedProfile);
        
        // Test technology consistency (should avoid complex technologies)
        List<String> technologies = List.of("Next.js", "Supabase", "NextAuth.js", "Stripe", 
                                           "Vercel", "Sentry", "PostHog", "Resend");
        ConsistencyValidation consistency = stackEngine.validateTechnologyStack(
            technologies, "STARTUP_MVP");
        
        assertTrue(consistency.isConsistent, "Startup MVP stack should be consistent");
        assertTrue(consistency.conflictingPairs.isEmpty(), "Should have no conflicts");
        
        System.out.println("=== STARTUP MVP ROADMAP ===");
        System.out.println("Architecture Profile: " + archAnalysis.recommendedProfile);
        System.out.println("Consistency Score: " + consistency.consistencyScore);
        System.out.println("Technologies: " + technologies);
        
        // Verify no over-engineering
        assertFalse(technologies.contains("Kubernetes"), "Should not include Kubernetes for MVP");
        assertFalse(technologies.contains("Kafka"), "Should not include Kafka for MVP");
        assertFalse(technologies.contains("Cassandra"), "Should not include Cassandra for MVP");
    }

    @Test
    public void testArchitecturalProgressionLogic() {
        // Test that technology chains are detected correctly
        String kafkaInput = "Build event-driven system with Kafka Schema Registry and KSQL";
        ArchitectureAnalysis kafkaAnalysis = stackEngine.analyzeArchitecturalIntent(kafkaInput, "SYSTEM_DESIGN");
        
        assertTrue(kafkaAnalysis.progressionChains.contains("KAFKA_ECOSYSTEM"), 
                  "Should detect Kafka ecosystem progression");
        
        String kubernetesInput = "Deploy microservices using Kubernetes Helm ArgoCD and Prometheus";
        ArchitectureAnalysis k8sAnalysis = stackEngine.analyzeArchitecturalIntent(kubernetesInput, "DEVOPS");
        
        assertTrue(k8sAnalysis.progressionChains.contains("KUBERNETES_ECOSYSTEM"), 
                  "Should detect Kubernetes ecosystem progression");
        
        String ragInput = "Build RAG pipeline with embedding generation vector database and reranking";
        ArchitectureAnalysis ragAnalysis = stackEngine.analyzeArchitecturalIntent(ragInput, "AI_ENGINEERING");
        
        assertTrue(ragAnalysis.progressionChains.contains("RAG_PIPELINE"), 
                  "Should detect RAG pipeline progression");
        
        System.out.println("=== PROGRESSION CHAIN DETECTION ===");
        System.out.println("Kafka chains: " + kafkaAnalysis.progressionChains);
        System.out.println("Kubernetes chains: " + k8sAnalysis.progressionChains);
        System.out.println("RAG chains: " + ragAnalysis.progressionChains);
    }
}