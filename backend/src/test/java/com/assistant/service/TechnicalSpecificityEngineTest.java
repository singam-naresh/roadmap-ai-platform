package com.assistant.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TechnicalSpecificityEngineTest {

    private final TechnicalSpecificityEngine engine = new TechnicalSpecificityEngine();

    @Test
    public void testGenericPhraseDetection() {
        String genericContent = "Set up a message queue and configure monitoring system with load balancer";
        
        TechnicalSpecificityEngine.SpecificityAnalysis analysis = 
            engine.analyzeSpecificity(genericContent, "DISTRIBUTED_SYSTEMS");
        
        // Should detect generic phrases
        assertTrue(analysis.genericPhraseCount > 0, "Should detect generic phrases");
        assertTrue(analysis.foundGenericPhrases.contains("message queue"), "Should find 'message queue'");
        assertTrue(analysis.foundGenericPhrases.contains("monitoring system"), "Should find 'monitoring system'");
        assertTrue(analysis.foundGenericPhrases.contains("load balancer"), "Should find 'load balancer'");
        
        // Should have low specificity score
        assertTrue(analysis.specificityScore < 0.5, "Should have low specificity score");
        assertFalse(analysis.isAcceptable, "Should not be acceptable");
    }

    @Test
    public void testConcreteTechnologyDetection() {
        String concreteContent = "Set up Apache Kafka and configure Prometheus Grafana with Envoy Proxy";
        
        TechnicalSpecificityEngine.SpecificityAnalysis analysis = 
            engine.analyzeSpecificity(concreteContent, "DISTRIBUTED_SYSTEMS");
        
        System.out.println("Found concrete technologies: " + analysis.foundConcreteTechnologies);
        System.out.println("Concrete technology count: " + analysis.concreteTechnologyCount);
        System.out.println("Specificity score: " + analysis.specificityScore);
        
        // Should detect concrete technologies
        assertTrue(analysis.concreteTechnologyCount >= 2, "Should detect at least 2 concrete technologies");
        
        // Check if any of the expected technologies are found (case-insensitive)
        boolean hasKafka = analysis.foundConcreteTechnologies.stream()
            .anyMatch(tech -> tech.toLowerCase().contains("kafka"));
        boolean hasEnvoy = analysis.foundConcreteTechnologies.stream()
            .anyMatch(tech -> tech.toLowerCase().contains("envoy"));
        
        assertTrue(hasKafka, "Should find Kafka technology");
        assertTrue(hasEnvoy, "Should find Envoy technology");
        
        // Should have high specificity score
        assertTrue(analysis.specificityScore > 0.3, "Should have high specificity score");
        assertTrue(analysis.isAcceptable, "Should be acceptable");
    }

    @Test
    public void testConcreteImplementationEnhancement() {
        String genericContent = "Use a message queue for communication and set up monitoring system";
        
        String enhanced = engine.enhanceWithConcreteImplementations(genericContent, "DISTRIBUTED_SYSTEMS");
        
        System.out.println("Original: " + genericContent);
        System.out.println("Enhanced: " + enhanced);
        
        // Should replace generic terms with concrete implementations
        assertFalse(enhanced.toLowerCase().contains("message queue"), "Should not contain 'message queue'");
        
        // Should contain concrete technologies (more flexible check)
        boolean hasConcreteQueue = enhanced.contains("Kafka") || enhanced.contains("RabbitMQ") || 
                                  enhanced.contains("Pulsar") || enhanced.contains("SQS");
        boolean hasConcreteMonitoring = enhanced.contains("Prometheus") || enhanced.contains("DataDog") || 
                                       enhanced.contains("New Relic") || enhanced.contains("Grafana");
        
        assertTrue(hasConcreteQueue, "Should contain concrete message queue technology");
        assertTrue(hasConcreteMonitoring, "Should contain concrete monitoring technology");
    }

    @Test
    public void testProductionConcernsGeneration() {
        var concerns = engine.generateProductionConcerns("AI_ENGINEERING");
        
        assertFalse(concerns.isEmpty(), "Should generate production concerns");
        assertTrue(concerns.stream().anyMatch(c -> c.contains("GPU") || c.contains("model") || c.contains("inference")), 
                  "Should contain AI-specific concerns");
    }

    @Test
    public void testDomainSpecificAnalysis() {
        String aiContent = "Implement LoRA fine-tuning with DeepSpeed ZeRO-3 and deploy using vLLM with TensorRT optimization";
        
        TechnicalSpecificityEngine.SpecificityAnalysis analysis = 
            engine.analyzeSpecificity(aiContent, "AI_ENGINEERING");
        
        // Should detect AI-specific technologies
        assertTrue(analysis.concreteTechnologyCount >= 3, "Should detect multiple AI technologies");
        assertTrue(analysis.specificityScore > 0.5, "Should have high specificity for AI content");
        assertTrue(analysis.isAcceptable, "AI-specific content should be acceptable");
    }
}