package com.assistant.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ExpertDomainValidationTest {

    @Autowired
    private DomainDetectionEngine domainEngine;
    
    @Autowired
    private DomainKnowledgeEngine knowledgeEngine;
    
    @Autowired
    private RoadmapQualityScorer qualityScorer;

    @Test
    public void testAIEngineeringDomainDetection() {
        String input = "Create an advanced roadmap for becoming an AI engineer specializing in LLM fine tuning, RAG systems, and inference optimization";
        
        var result = domainEngine.analyzeDomain(input);
        
        assertEquals("AI_ENGINEERING", result.getPrimaryDomain());
        assertTrue(result.getConfidence() > 0.7);
        assertTrue(result.isHighConfidence());
    }

    @Test
    public void testExpertiseLevelDetection() {
        // Expert level detection
        String expertInput = "Advanced production-grade LLM fine-tuning with LoRA, QLoRA, and RLHF optimization for enterprise deployment";
        String expertLevel = knowledgeEngine.detectExpertiseLevel(expertInput);
        assertEquals("EXPERT", expertLevel);
        
        // Advanced level detection
        String advancedInput = "Build microservices architecture with Kubernetes and monitoring";
        String advancedLevel = knowledgeEngine.detectExpertiseLevel(advancedInput);
        assertEquals("ADVANCED", advancedLevel);
        
        // Beginner level detection
        String beginnerInput = "Learn React basics";
        String beginnerLevel = knowledgeEngine.detectExpertiseLevel(beginnerInput);
        assertEquals("BEGINNER", beginnerLevel);
    }

    @Test
    public void testDomainKnowledgeContext() {
        String context = knowledgeEngine.buildDomainContext("AI_ENGINEERING", null, "EXPERT");
        
        // Should contain advanced AI concepts
        assertTrue(context.contains("LoRA"));
        assertTrue(context.contains("QLoRA"));
        assertTrue(context.contains("RLHF"));
        assertTrue(context.contains("TensorRT"));
        assertTrue(context.contains("DeepSpeed"));
        assertTrue(context.contains("vLLM"));
        
        // Should contain production tools
        assertTrue(context.contains("PyTorch"));
        assertTrue(context.contains("Hugging Face"));
        assertTrue(context.contains("Triton"));
    }

    @Test
    public void testJavaBackendExpertKnowledge() {
        String context = knowledgeEngine.buildDomainContext("JAVA_BACKEND", null, "EXPERT");
        
        // Should contain advanced Java concepts
        assertTrue(context.contains("CQRS"));
        assertTrue(context.contains("Event Sourcing"));
        assertTrue(context.contains("Circuit Breaker"));
        assertTrue(context.contains("Saga Pattern"));
        assertTrue(context.contains("Distributed Tracing"));
        
        // Should contain production tools
        assertTrue(context.contains("Spring Boot"));
        assertTrue(context.contains("Kafka"));
        assertTrue(context.contains("Kubernetes"));
    }

    @Test
    public void testReactFrontendExpertKnowledge() {
        String context = knowledgeEngine.buildDomainContext("REACT_FRONTEND", null, "EXPERT");
        
        // Should contain advanced React concepts
        assertTrue(context.contains("Server Components"));
        assertTrue(context.contains("Suspense"));
        assertTrue(context.contains("Concurrent"));
        assertTrue(context.contains("Streaming SSR"));
        
        // Should contain production tools
        assertTrue(context.contains("Next.js"));
        assertTrue(context.contains("TypeScript"));
        assertTrue(context.contains("Tailwind"));
    }

    @Test
    public void testDevOpsExpertKnowledge() {
        String context = knowledgeEngine.buildDomainContext("DEVOPS", null, "EXPERT");
        
        // Should contain advanced DevOps concepts
        assertTrue(context.contains("GitOps"));
        assertTrue(context.contains("Service Mesh"));
        assertTrue(context.contains("Observability"));
        assertTrue(context.contains("SRE"));
        
        // Should contain production tools
        assertTrue(context.contains("Kubernetes"));
        assertTrue(context.contains("Terraform"));
        assertTrue(context.contains("ArgoCD"));
        assertTrue(context.contains("Prometheus"));
    }

    // Validation test cases for expected expert-level outputs
    public static class ExpertOutputValidation {
        
        public static final String AI_ENGINEERING_EXPERT_INPUT = 
            "Create an advanced roadmap for becoming an AI engineer specializing in LLM fine tuning, RAG systems, and inference optimization";
        
        public static final String[] EXPECTED_AI_CONCEPTS = {
            "LoRA", "QLoRA", "RLHF", "DPO", "PEFT", "TensorRT", "Triton", "vLLM", 
            "DeepSpeed", "quantization", "inference optimization", "distributed training"
        };
        
        public static final String JAVA_BACKEND_EXPERT_INPUT = 
            "Build enterprise microservices architecture with event sourcing and distributed systems";
        
        public static final String[] EXPECTED_JAVA_CONCEPTS = {
            "CQRS", "Event Sourcing", "Saga Pattern", "Circuit Breaker", "Distributed Tracing",
            "Spring Boot", "Kafka", "Kubernetes", "Observability"
        };
        
        public static final String REACT_FRONTEND_EXPERT_INPUT = 
            "Create production React application with server-side rendering and performance optimization";
        
        public static final String[] EXPECTED_REACT_CONCEPTS = {
            "Server Components", "Streaming SSR", "Suspense", "Concurrent Rendering",
            "Next.js", "TypeScript", "Code Splitting", "Performance Optimization"
        };
        
        public static final String DEVOPS_EXPERT_INPUT = 
            "Design production Kubernetes platform with GitOps and observability";
        
        public static final String[] EXPECTED_DEVOPS_CONCEPTS = {
            "GitOps", "ArgoCD", "Helm", "Istio", "Prometheus", "Grafana", 
            "Service Mesh", "Observability", "SRE"
        };
    }
}