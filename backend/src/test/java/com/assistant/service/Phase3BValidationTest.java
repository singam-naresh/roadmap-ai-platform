package com.assistant.service;

import com.assistant.service.TechnicalSpecificityEngine.SpecificityAnalysis;
import com.assistant.service.RoadmapQualityScorer.QualityScore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class Phase3BValidationTest {

    private final TechnicalSpecificityEngine specificityEngine = new TechnicalSpecificityEngine();
    private final RoadmapQualityScorer qualityScorer = new RoadmapQualityScorer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDistributedSystemsRoadmapSpecificity() throws Exception {
        // Simulate a distributed systems roadmap with generic terms
        String genericRoadmap = """
            {
              "summary": "Build a scalable distributed system with message queues and monitoring",
              "steps": [
                "Set up message queue for communication",
                "Configure monitoring system for observability", 
                "Implement load balancer for traffic distribution",
                "Deploy using container orchestration platform",
                "Set up service mesh for microservices communication"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(genericRoadmap);
        
        // Test specificity analysis
        SpecificityAnalysis analysis = specificityEngine.analyzeSpecificity(
            genericRoadmap, "DISTRIBUTED_SYSTEMS");
        
        System.out.println("=== DISTRIBUTED SYSTEMS ROADMAP ANALYSIS ===");
        System.out.println("Generic phrases found: " + analysis.foundGenericPhrases);
        System.out.println("Concrete technologies found: " + analysis.foundConcreteTechnologies);
        System.out.println("Specificity score: " + analysis.specificityScore);
        System.out.println("Is acceptable: " + analysis.isAcceptable);
        
        // Should detect multiple generic phrases
        assertTrue(analysis.genericPhraseCount >= 3, "Should detect multiple generic phrases");
        assertFalse(analysis.isAcceptable, "Generic roadmap should not be acceptable");
        
        // Test enhanced quality scoring
        QualityScore qualityScore = qualityScorer.scoreRoadmapWithSpecificity(
            roadmapJson, "DISTRIBUTED_SYSTEMS", analysis);
        
        System.out.println("Quality score: " + qualityScore.overallScore);
        System.out.println("Quality feedback: " + qualityScore.feedback);
        
        // Quality should be penalized for generic content
        assertTrue(qualityScore.overallScore < 0.7, "Quality should be low for generic content");
    }

    @Test
    public void testAIEngineeringRoadmapSpecificity() throws Exception {
        // Simulate an AI engineering roadmap with concrete technologies
        String concreteRoadmap = """
            {
              "summary": "Build production LLM fine-tuning pipeline with advanced optimization",
              "steps": [
                "Implement LoRA fine-tuning using PyTorch with DeepSpeed ZeRO-3 optimization",
                "Configure vLLM inference server with TensorRT-LLM acceleration",
                "Set up semantic chunking pipeline with cross-encoder reranking",
                "Deploy using Kubernetes with Prometheus monitoring and Grafana dashboards",
                "Implement RLHF training pipeline with PPO optimization"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(concreteRoadmap);
        
        // Test specificity analysis
        SpecificityAnalysis analysis = specificityEngine.analyzeSpecificity(
            concreteRoadmap, "AI_ENGINEERING");
        
        System.out.println("=== AI ENGINEERING ROADMAP ANALYSIS ===");
        System.out.println("Generic phrases found: " + analysis.foundGenericPhrases);
        System.out.println("Concrete technologies found: " + analysis.foundConcreteTechnologies);
        System.out.println("Specificity score: " + analysis.specificityScore);
        System.out.println("Is acceptable: " + analysis.isAcceptable);
        
        // Should detect concrete AI technologies
        assertTrue(analysis.concreteTechnologyCount >= 5, "Should detect multiple AI technologies");
        assertTrue(analysis.specificityScore > 0.5, "Should have high specificity score");
        assertTrue(analysis.isAcceptable, "Concrete AI roadmap should be acceptable");
        
        // Test enhanced quality scoring
        QualityScore qualityScore = qualityScorer.scoreRoadmapWithSpecificity(
            roadmapJson, "AI_ENGINEERING", analysis);
        
        System.out.println("Quality score: " + qualityScore.overallScore);
        System.out.println("Quality feedback: " + qualityScore.feedback);
        
        // Quality should be high for concrete, technical content
        assertTrue(qualityScore.overallScore > 0.7, "Quality should be high for concrete content");
    }

    @Test
    public void testGenericToConcreteEnhancement() {
        System.out.println("=== GENERIC TO CONCRETE ENHANCEMENT ===");
        
        String[] genericInputs = {
            "Set up message queue and monitoring system",
            "Deploy using container orchestration and service mesh",
            "Configure load balancer and caching layer",
            "Implement CI/CD pipeline with infrastructure as code"
        };
        
        for (String input : genericInputs) {
            String enhanced = specificityEngine.enhanceWithConcreteImplementations(
                input, "DISTRIBUTED_SYSTEMS");
            
            System.out.println("Original: " + input);
            System.out.println("Enhanced: " + enhanced);
            System.out.println("---");
            
            // Should replace generic terms
            assertNotEquals(input, enhanced, "Should enhance the input");
        }
    }

    @Test
    public void testProductionConcernsGeneration() {
        System.out.println("=== PRODUCTION CONCERNS GENERATION ===");
        
        String[] domains = {"AI_ENGINEERING", "JAVA_BACKEND", "DEVOPS"};
        
        for (String domain : domains) {
            var concerns = specificityEngine.generateProductionConcerns(domain);
            
            System.out.println(domain + " concerns: " + concerns);
            
            assertFalse(concerns.isEmpty(), "Should generate production concerns for " + domain);
            assertTrue(concerns.size() >= 3, "Should generate multiple concerns for " + domain);
        }
    }

    @Test
    public void testQualityImprovementWithSpecificity() throws Exception {
        // Test that specificity analysis improves quality scoring
        String roadmapWithMixedContent = """
            {
              "summary": "Build a microservices system with proper monitoring",
              "steps": [
                "Research requirements and understand concepts",
                "Implement Spring Boot microservices with Kafka messaging",
                "Set up monitoring system for observability",
                "Deploy using Kubernetes with Istio service mesh",
                "Configure Prometheus and Grafana for metrics collection"
              ]
            }
            """;

        JsonNode roadmapJson = objectMapper.readTree(roadmapWithMixedContent);
        
        // Score without specificity analysis
        QualityScore baseScore = qualityScorer.scoreRoadmap(roadmapJson, "JAVA_BACKEND");
        
        // Score with specificity analysis
        SpecificityAnalysis analysis = specificityEngine.analyzeSpecificity(
            roadmapWithMixedContent, "JAVA_BACKEND");
        QualityScore enhancedScore = qualityScorer.scoreRoadmapWithSpecificity(
            roadmapJson, "JAVA_BACKEND", analysis);
        
        System.out.println("=== QUALITY IMPROVEMENT TEST ===");
        System.out.println("Base quality score: " + baseScore.overallScore);
        System.out.println("Enhanced quality score: " + enhancedScore.overallScore);
        System.out.println("Base feedback: " + baseScore.feedback);
        System.out.println("Enhanced feedback: " + enhancedScore.feedback);
        
        // Enhanced scoring should provide more detailed feedback
        assertTrue(enhancedScore.feedback.size() >= baseScore.feedback.size(), 
                  "Enhanced scoring should provide more detailed feedback");
    }
}