package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DomainDetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DomainDetectionEngine.class);

    // Domain definitions with keywords and patterns
    private static final Map<String, DomainDefinition> DOMAINS = Map.of(
        "AI_ENGINEERING", new DomainDefinition("AI Engineering", 
            List.of("ai", "machine learning", "ml", "llm", "fine tuning", "transformers", "neural", "deep learning", 
                   "hugging face", "pytorch", "tensorflow", "langchain", "vector", "embedding", "rag", "chatbot",
                   "nlp", "computer vision", "reinforcement learning", "rlhf", "lora", "qlora", "peft"),
            List.of("artificial intelligence", "large language model", "fine-tuning", "prompt engineering")),

        "JAVA_BACKEND", new DomainDefinition("Java Backend",
            List.of("java", "spring", "spring boot", "jpa", "hibernate", "maven", "gradle", "microservices",
                   "kafka", "redis", "postgresql", "mysql", "rest api", "jvm", "tomcat", "security"),
            List.of("backend development", "server-side", "enterprise java")),

        "REACT_FRONTEND", new DomainDefinition("React Frontend",
            List.of("react", "javascript", "typescript", "jsx", "hooks", "redux", "next.js", "vite", "webpack",
                   "css", "tailwind", "styled-components", "state management", "component", "frontend"),
            List.of("user interface", "web development", "single page application")),

        "FULL_STACK", new DomainDefinition("Full Stack Development",
            List.of("full stack", "fullstack", "mern", "mean", "lamp", "end-to-end", "frontend backend",
                   "web application", "database", "api", "deployment"),
            List.of("complete application", "web development stack")),

        "DEVOPS", new DomainDefinition("DevOps Engineering",
            List.of("devops", "docker", "kubernetes", "k8s", "ci/cd", "jenkins", "github actions", "terraform",
                   "ansible", "aws", "azure", "gcp", "monitoring", "prometheus", "grafana", "deployment"),
            List.of("infrastructure", "continuous integration", "cloud deployment")),

        "DATA_ENGINEERING", new DomainDefinition("Data Engineering",
            List.of("data engineering", "etl", "data pipeline", "spark", "hadoop", "kafka", "airflow",
                   "snowflake", "databricks", "data warehouse", "big data", "streaming", "batch processing"),
            List.of("data processing", "data architecture", "analytics pipeline")),

        "CYBERSECURITY", new DomainDefinition("Cybersecurity",
            List.of("cybersecurity", "security", "penetration testing", "ethical hacking", "vulnerability",
                   "encryption", "authentication", "authorization", "owasp", "security audit", "compliance"),
            List.of("information security", "network security", "application security")),

        "MOBILE_DEVELOPMENT", new DomainDefinition("Mobile Development",
            List.of("mobile", "android", "ios", "react native", "flutter", "swift", "kotlin", "xamarin",
                   "mobile app", "app development", "cross-platform"),
            List.of("mobile application", "smartphone app", "tablet app")),

        "CLOUD_ENGINEERING", new DomainDefinition("Cloud Engineering",
            List.of("cloud", "aws", "azure", "gcp", "serverless", "lambda", "cloud architecture", "s3",
                   "ec2", "cloud native", "microservices", "containers", "cloud migration"),
            List.of("cloud computing", "cloud infrastructure", "cloud services")),

        "SYSTEM_DESIGN", new DomainDefinition("System Design",
            List.of("system design", "architecture", "scalability", "distributed systems", "load balancing",
                   "caching", "database design", "high availability", "performance", "system architecture"),
            List.of("large scale systems", "software architecture", "distributed architecture"))
    );

    public DomainAnalysisResult analyzeDomain(String userInput) {
        String normalizedInput = userInput.toLowerCase().trim();
        
        Map<String, Double> domainScores = new HashMap<>();
        
        // Calculate scores for each domain
        for (Map.Entry<String, DomainDefinition> entry : DOMAINS.entrySet()) {
            String domainKey = entry.getKey();
            DomainDefinition domain = entry.getValue();
            
            double score = calculateDomainScore(normalizedInput, domain);
            if (score > 0) {
                domainScores.put(domainKey, score);
            }
        }
        
        // Find primary and secondary domains
        List<Map.Entry<String, Double>> sortedDomains = domainScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        
        String primaryDomain = sortedDomains.isEmpty() ? "GENERAL" : sortedDomains.get(0).getKey();
        String secondaryDomain = sortedDomains.size() > 1 ? sortedDomains.get(1).getKey() : null;
        
        double confidence = sortedDomains.isEmpty() ? 0.0 : sortedDomains.get(0).getValue();
        
        log.info("[domain-detection] Input: '{}' -> Primary: {} (confidence: {:.2f})", 
                userInput, primaryDomain, confidence);
        
        return new DomainAnalysisResult(primaryDomain, secondaryDomain, confidence, domainScores);
    }
    
    private double calculateDomainScore(String input, DomainDefinition domain) {
        double score = 0.0;
        
        // Direct keyword matches (higher weight)
        for (String keyword : domain.getKeywords()) {
            if (input.contains(keyword)) {
                score += 2.0;
            }
        }
        
        // Phrase matches (highest weight)
        for (String phrase : domain.getPhrases()) {
            if (input.contains(phrase)) {
                score += 3.0;
            }
        }
        
        // Normalize by total possible score
        double maxScore = (domain.getKeywords().size() * 2.0) + (domain.getPhrases().size() * 3.0);
        return maxScore > 0 ? score / maxScore : 0.0;
    }
    
    public DomainDefinition getDomainDefinition(String domainKey) {
        return DOMAINS.get(domainKey);
    }
    
    public Set<String> getAllDomains() {
        return DOMAINS.keySet();
    }
    
    // Domain definition class
    public static class DomainDefinition {
        private final String displayName;
        private final List<String> keywords;
        private final List<String> phrases;
        
        public DomainDefinition(String displayName, List<String> keywords, List<String> phrases) {
            this.displayName = displayName;
            this.keywords = keywords;
            this.phrases = phrases;
        }
        
        public String getDisplayName() { return displayName; }
        public List<String> getKeywords() { return keywords; }
        public List<String> getPhrases() { return phrases; }
    }
    
    // Analysis result class
    public static class DomainAnalysisResult {
        private final String primaryDomain;
        private final String secondaryDomain;
        private final double confidence;
        private final Map<String, Double> allScores;
        
        public DomainAnalysisResult(String primaryDomain, String secondaryDomain, 
                                  double confidence, Map<String, Double> allScores) {
            this.primaryDomain = primaryDomain;
            this.secondaryDomain = secondaryDomain;
            this.confidence = confidence;
            this.allScores = allScores;
        }
        
        public String getPrimaryDomain() { return primaryDomain; }
        public String getSecondaryDomain() { return secondaryDomain; }
        public double getConfidence() { return confidence; }
        public Map<String, Double> getAllScores() { return allScores; }
        
        public boolean isHighConfidence() { return confidence > 0.3; }
        public boolean hasSecondaryDomain() { return secondaryDomain != null; }
    }
}