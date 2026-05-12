package com.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class RoadmapQualityScorer {

    private static final Logger log = LoggerFactory.getLogger(RoadmapQualityScorer.class);

    // Quality scoring thresholds
    private static final double MIN_ACCEPTABLE_SCORE = 0.65;
    private static final double HIGH_QUALITY_SCORE = 0.80;

    // Patterns for quality detection - Enhanced for Expert Level
    private static final Pattern TECHNICAL_TERMS = Pattern.compile(
        "\\b(api|framework|library|database|algorithm|architecture|implementation|deployment|testing|optimization|security|performance|" +
        "kubernetes|docker|terraform|prometheus|grafana|elasticsearch|kafka|redis|postgresql|mongodb|" +
        "pytorch|tensorflow|transformers|lora|qlora|rlhf|tensorrt|triton|vllm|deepspeed|" +
        "spring|hibernate|microservices|circuit breaker|saga pattern|cqrs|event sourcing|" +
        "react|nextjs|typescript|suspense|concurrent|ssr|hydration|webpack|vite)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ADVANCED_TOOLS = Pattern.compile(
        "\\b(kubernetes|istio|prometheus|grafana|terraform|helm|argocd|flux|tekton|" +
        "pytorch|tensorrt|triton|vllm|deepspeed|fsdp|lora|qlora|rlhf|dpo|peft|" +
        "spring boot|hibernate|kafka|redis cluster|elasticsearch|" +
        "react 18|nextjs 14|typescript|tailwind|redux toolkit|" +
        "docker|containerd|buildah|skaffold|kustomize)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern GENERIC_WORDS = Pattern.compile(
        "\\b(learn|study|understand|explore|research|familiarize|get started|basic|simple|general|" +
        "overview|introduction|fundamentals|basics|beginner|tutorial)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ANTI_GENERIC_VIOLATIONS = Pattern.compile(
        "\\b(research requirements|create project plan|learn basics|understand concepts|" +
        "get familiar with|explore the topic|study the field|basic understanding|" +
        "general knowledge|overview of|introduction to)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern EXPERT_TERMINOLOGY = Pattern.compile(
        "\\b(architecture|infrastructure|optimization|scalability|performance|distributed|" +
        "production|enterprise|deployment|monitoring|observability|reliability|" +
        "fault tolerance|load balancing|auto-scaling|service mesh|" +
        "fine-tuning|quantization|inference|embedding|tokenization|" +
        "microservices|event-driven|reactive|asynchronous|" +
        "server-side rendering|hydration|code splitting|bundle optimization)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ACTION_VERBS = Pattern.compile(
        "^(build|create|implement|develop|design|configure|deploy|setup|integrate|optimize|test|write|code|" +
        "architect|engineer|construct|establish|provision|orchestrate|automate|monitor|scale|" +
        "fine-tune|quantize|containerize|dockerize|kubernetes|helm|terraform)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public QualityScore scoreRoadmap(JsonNode roadmapJson, String domain) {
        QualityScore score = new QualityScore();
        
        // Score different aspects
        score.technicalSpecificity = scoreTechnicalSpecificity(roadmapJson);
        score.implementationDepth = scoreImplementationDepth(roadmapJson);
        score.domainRelevance = scoreDomainRelevance(roadmapJson, domain);
        score.uniqueness = scoreUniqueness(roadmapJson);
        score.completeness = scoreCompleteness(roadmapJson);
        score.actionability = scoreActionability(roadmapJson);
        
        // Calculate overall score (weighted average)
        score.overallScore = calculateOverallScore(score);
        
        // Determine quality level
        score.qualityLevel = determineQualityLevel(score.overallScore);
        
        // Generate feedback
        score.feedback = generateFeedback(score);
        
        log.info("[quality-scorer] Domain: {} | Overall: {:.2f} | Level: {} | Technical: {:.2f} | Implementation: {:.2f}",
                domain, score.overallScore, score.qualityLevel, score.technicalSpecificity, score.implementationDepth);
        
        return score;
    }

    public QualityScore scoreRoadmapWithSpecificity(JsonNode roadmapJson, String domain, 
                                                   TechnicalSpecificityEngine.SpecificityAnalysis specificityAnalysis) {
        QualityScore score = scoreRoadmap(roadmapJson, domain);
        
        // Enhance scoring with specificity analysis
        if (specificityAnalysis != null) {
            // Boost technical specificity score based on concrete technology usage
            double specificityBonus = specificityAnalysis.specificityScore * 0.3;
            score.technicalSpecificity = Math.min(1.0, score.technicalSpecificity + specificityBonus);
            
            // Penalize for generic phrases
            double genericPenalty = Math.min(0.3, specificityAnalysis.genericPhraseCount * 0.05);
            score.uniqueness = Math.max(0.0, score.uniqueness - genericPenalty);
            
            // Recalculate overall score
            score.overallScore = calculateOverallScore(score);
            score.qualityLevel = determineQualityLevel(score.overallScore);
            
            // Enhanced feedback with specificity insights
            List<String> enhancedFeedback = new ArrayList<>(generateFeedback(score));
            
            if (!specificityAnalysis.isAcceptable) {
                enhancedFeedback.add("Technical specificity too low - use concrete technologies instead of generic terms");
            }
            
            if (!specificityAnalysis.foundGenericPhrases.isEmpty()) {
                enhancedFeedback.add("Replace generic phrases: " + 
                    String.join(", ", specificityAnalysis.foundGenericPhrases.subList(0, 
                        Math.min(3, specificityAnalysis.foundGenericPhrases.size()))));
            }
            
            if (specificityAnalysis.concreteTechnologyCount > 0) {
                enhancedFeedback.add("Good use of concrete technologies: " + 
                    specificityAnalysis.concreteTechnologyCount + " specific tools mentioned");
            }
            
            score.feedback = enhancedFeedback;
            
            log.info("[quality-scorer] Enhanced with specificity - Overall: {:.2f} | Specificity: {:.2f} | Generic: {} | Concrete: {}", 
                    score.overallScore, specificityAnalysis.specificityScore, 
                    specificityAnalysis.genericPhraseCount, specificityAnalysis.concreteTechnologyCount);
        }
        
        return score;
    }

    public QualityScore scoreRoadmapWithSpecificityAndConsistency(JsonNode roadmapJson, String domain, 
                                                                 TechnicalSpecificityEngine.SpecificityAnalysis specificityAnalysis,
                                                                 StackConsistencyEngine.ConsistencyValidation consistencyValidation) {
        QualityScore score = scoreRoadmapWithSpecificity(roadmapJson, domain, specificityAnalysis);
        
        // Enhance scoring with consistency analysis
        if (consistencyValidation != null) {
            // Boost technical specificity score based on architectural consistency
            double consistencyBonus = consistencyValidation.consistencyScore * 0.2;
            score.technicalSpecificity = Math.min(1.0, score.technicalSpecificity + consistencyBonus);
            
            // Penalize for architectural conflicts
            double conflictPenalty = Math.min(0.4, consistencyValidation.conflictingPairs.size() * 0.1);
            score.domainRelevance = Math.max(0.0, score.domainRelevance - conflictPenalty);
            
            // Recalculate overall score
            score.overallScore = calculateOverallScore(score);
            score.qualityLevel = determineQualityLevel(score.overallScore);
            
            // Enhanced feedback with consistency insights
            List<String> enhancedFeedback = new ArrayList<>(score.feedback);
            
            if (!consistencyValidation.isConsistent) {
                enhancedFeedback.add("Architectural consistency issues detected");
            }
            
            if (!consistencyValidation.conflictingPairs.isEmpty()) {
                enhancedFeedback.add("Technology conflicts: " + 
                    String.join(", ", consistencyValidation.conflictingPairs.subList(0, 
                        Math.min(2, consistencyValidation.conflictingPairs.size()))));
            }
            
            if (!consistencyValidation.inconsistentTechnologies.isEmpty()) {
                enhancedFeedback.add("Inconsistent technologies: " + 
                    String.join(", ", consistencyValidation.inconsistentTechnologies.subList(0, 
                        Math.min(3, consistencyValidation.inconsistentTechnologies.size()))));
            }
            
            if (consistencyValidation.consistencyScore > 0.8) {
                enhancedFeedback.add("Excellent architectural consistency with coherent technology stack");
            }
            
            score.feedback = enhancedFeedback;
            
            log.info("[quality-scorer] Enhanced with consistency - Overall: {:.2f} | Consistency: {:.2f} | Conflicts: {}", 
                    score.overallScore, consistencyValidation.consistencyScore, 
                    consistencyValidation.conflictingPairs.size());
        }
        
        return score;
    }

    public QualityScore scoreRoadmapWithSpecificityConsistencyAndRelationships(JsonNode roadmapJson, String domain, 
                                                                              TechnicalSpecificityEngine.SpecificityAnalysis specificityAnalysis,
                                                                              StackConsistencyEngine.ConsistencyValidation consistencyValidation,
                                                                              TechnologyOntologyEngine.TechnologyRelationshipAnalysis relationshipAnalysis) {
        QualityScore score = scoreRoadmapWithSpecificityAndConsistency(roadmapJson, domain, specificityAnalysis, consistencyValidation);
        
        // PHASE 3D: Enhance scoring with relationship analysis
        if (relationshipAnalysis != null) {
            // Boost technical specificity based on relationship coherence
            double relationshipBonus = relationshipAnalysis.coherenceScore * 0.15;
            score.technicalSpecificity = Math.min(1.0, score.technicalSpecificity + relationshipBonus);
            
            // Penalize for technology conflicts and missing dependencies
            double conflictPenalty = Math.min(0.3, relationshipAnalysis.conflicts.size() * 0.1);
            double dependencyPenalty = Math.min(0.2, relationshipAnalysis.missingDependencies.size() * 0.05);
            score.domainRelevance = Math.max(0.0, score.domainRelevance - conflictPenalty - dependencyPenalty);
            
            // Bonus for complementary technology usage
            long complementaryCount = relationshipAnalysis.relationships.stream()
                .mapToLong(r -> r.type == TechnologyOntologyEngine.RelationshipType.COMPLEMENTS ? 1 : 0)
                .sum();
            double complementaryBonus = Math.min(0.2, complementaryCount * 0.03);
            score.implementationDepth = Math.min(1.0, score.implementationDepth + complementaryBonus);
            
            // Recalculate overall score
            score.overallScore = calculateOverallScore(score);
            score.qualityLevel = determineQualityLevel(score.overallScore);
            
            // Enhanced feedback with relationship insights
            List<String> enhancedFeedback = new ArrayList<>(score.feedback);
            
            if (relationshipAnalysis.coherenceScore < 0.6) {
                enhancedFeedback.add("Technology relationship coherence needs improvement");
            }
            
            if (!relationshipAnalysis.conflicts.isEmpty()) {
                enhancedFeedback.add("Technology conflicts detected: " + 
                    String.join(", ", relationshipAnalysis.conflicts.subList(0, 
                        Math.min(2, relationshipAnalysis.conflicts.size()))));
            }
            
            if (!relationshipAnalysis.missingDependencies.isEmpty()) {
                enhancedFeedback.add("Missing dependencies: " + 
                    String.join(", ", relationshipAnalysis.missingDependencies.subList(0, 
                        Math.min(2, relationshipAnalysis.missingDependencies.size()))));
            }
            
            if (complementaryCount > 3) {
                enhancedFeedback.add("Excellent use of complementary technologies");
            }
            
            if (relationshipAnalysis.coherenceScore > 0.8 && relationshipAnalysis.conflicts.isEmpty()) {
                enhancedFeedback.add("Outstanding technology relationship coherence with zero conflicts");
            }
            
            score.feedback = enhancedFeedback;
            
            log.info("[quality-scorer] Enhanced with relationships - Overall: {:.2f} | Coherence: {:.2f} | Conflicts: {} | Dependencies: {}", 
                    score.overallScore, relationshipAnalysis.coherenceScore, 
                    relationshipAnalysis.conflicts.size(), relationshipAnalysis.missingDependencies.size());
        }
        
        return score;
    }

    private double scoreTechnicalSpecificity(JsonNode roadmapJson) {
        double score = 0.0;
        int totalSteps = 0;
        
        JsonNode steps = roadmapJson.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                String stepText = step.asText().toLowerCase();
                totalSteps++;
                
                // Count technical terms (higher weight for advanced tools)
                long techTerms = TECHNICAL_TERMS.matcher(stepText).results().count();
                long advancedTools = ADVANCED_TOOLS.matcher(stepText).results().count();
                long expertTerms = EXPERT_TERMINOLOGY.matcher(stepText).results().count();
                long genericWords = GENERIC_WORDS.matcher(stepText).results().count();
                long antiGenericViolations = ANTI_GENERIC_VIOLATIONS.matcher(stepText).results().count();
                
                // Calculate step score with bonuses for advanced content
                double stepScore = (techTerms * 0.2) + (advancedTools * 0.4) + (expertTerms * 0.3) 
                                 - (genericWords * 0.3) - (antiGenericViolations * 0.5);
                stepScore = Math.max(0, Math.min(1, stepScore / 2.0)); // Normalize to 0-1
                
                score += stepScore;
            }
        }
        
        return totalSteps > 0 ? score / totalSteps : 0.0;
    }

    private double scoreImplementationDepth(JsonNode roadmapJson) {
        double score = 0.0;
        
        // Check for implementation-focused language
        JsonNode steps = roadmapJson.path("steps");
        if (steps.isArray()) {
            int implementationSteps = 0;
            for (JsonNode step : steps) {
                String stepText = step.asText();
                
                // Check for action verbs
                if (ACTION_VERBS.matcher(stepText).find()) {
                    implementationSteps++;
                }
                
                // Check for specific implementation details
                if (stepText.contains("code") || stepText.contains("implement") || 
                    stepText.contains("build") || stepText.contains("deploy")) {
                    implementationSteps++;
                }
            }
            
            score = (double) implementationSteps / steps.size();
        }
        
        // Bonus for detailed tips and resources
        JsonNode tips = roadmapJson.path("tips");
        JsonNode resources = roadmapJson.path("resources");
        
        if (tips.isArray() && tips.size() >= 3) score += 0.1;
        if (resources.isArray() && resources.size() >= 4) score += 0.1;
        
        return Math.min(1.0, score);
    }

    private double scoreDomainRelevance(JsonNode roadmapJson, String domain) {
        if (domain == null || domain.equals("GENERAL")) return 0.5;
        
        // Enhanced domain-specific keywords with advanced terminology
        Map<String, List<String>> domainKeywords = Map.of(
            "AI_ENGINEERING", List.of("transformer", "llm", "pytorch", "tensorflow", "hugging face", "langchain", 
                                    "fine-tuning", "rag", "lora", "qlora", "rlhf", "dpo", "peft", "tensorrt", 
                                    "triton", "vllm", "deepspeed", "quantization", "embedding", "tokenization"),
            "JAVA_BACKEND", List.of("spring", "hibernate", "jpa", "maven", "microservices", "kafka", "redis", 
                                  "postgresql", "circuit breaker", "saga pattern", "cqrs", "event sourcing", 
                                  "distributed tracing", "observability", "reactive", "webflux"),
            "REACT_FRONTEND", List.of("react", "jsx", "hooks", "redux", "nextjs", "typescript", "tailwind", 
                                    "component", "suspense", "concurrent", "ssr", "hydration", "server components", 
                                    "streaming", "code splitting", "bundle optimization"),
            "DEVOPS", List.of("docker", "kubernetes", "terraform", "jenkins", "ci/cd", "aws", "monitoring", 
                            "deployment", "helm", "argocd", "istio", "prometheus", "grafana", "observability", 
                            "gitops", "infrastructure as code", "service mesh"),
            "DATA_ENGINEERING", List.of("spark", "kafka", "airflow", "etl", "data pipeline", "snowflake", 
                                      "databricks", "sql", "streaming", "batch processing", "data warehouse", 
                                      "data lake", "dbt", "delta lake"),
            "SYSTEM_DESIGN", List.of("scalability", "load balancing", "caching", "distributed", "microservices", 
                                   "database sharding", "consistency", "availability", "partition tolerance", 
                                   "cap theorem", "eventual consistency", "consensus")
        );
        
        List<String> keywords = domainKeywords.getOrDefault(domain, List.of());
        if (keywords.isEmpty()) return 0.5;
        
        String fullText = roadmapJson.toString().toLowerCase();
        long matchCount = keywords.stream()
            .mapToLong(keyword -> fullText.split(keyword, -1).length - 1)
            .sum();
        
        // Higher threshold for expert-level content
        return Math.min(1.0, (double) matchCount / (keywords.size() * 0.6));
    }

    private double scoreUniqueness(JsonNode roadmapJson) {
        // Check for generic phrases that indicate low uniqueness
        String[] genericPhrases = {
            "learn the basics", "get familiar with", "understand the fundamentals",
            "explore the topic", "research the field", "study the concepts",
            "basic understanding", "general knowledge", "overview of"
        };
        
        String fullText = roadmapJson.toString().toLowerCase();
        long genericCount = Arrays.stream(genericPhrases)
            .mapToLong(phrase -> fullText.split(phrase, -1).length - 1)
            .sum();
        
        // Higher generic count = lower uniqueness
        return Math.max(0.0, 1.0 - (genericCount * 0.1));
    }

    private double scoreCompleteness(JsonNode roadmapJson) {
        double score = 0.0;
        
        // Check required fields
        if (!roadmapJson.path("summary").isMissingNode() && 
            roadmapJson.path("summary").asText().length() > 50) score += 0.2;
        
        if (!roadmapJson.path("estimatedTime").isMissingNode()) score += 0.1;
        if (!roadmapJson.path("difficulty").isMissingNode()) score += 0.1;
        
        JsonNode steps = roadmapJson.path("steps");
        if (steps.isArray() && steps.size() >= 5) score += 0.3;
        
        JsonNode tips = roadmapJson.path("tips");
        if (tips.isArray() && tips.size() >= 2) score += 0.1;
        
        JsonNode mistakes = roadmapJson.path("mistakesToAvoid");
        if (mistakes.isArray() && mistakes.size() >= 2) score += 0.1;
        
        JsonNode resources = roadmapJson.path("resources");
        if (resources.isArray() && resources.size() >= 3) score += 0.1;
        
        return score;
    }

    private double scoreActionability(JsonNode roadmapJson) {
        JsonNode steps = roadmapJson.path("steps");
        if (!steps.isArray() || steps.size() == 0) return 0.0;
        
        int actionableSteps = 0;
        for (JsonNode step : steps) {
            String stepText = step.asText();
            
            // Check for specific, actionable language
            if (ACTION_VERBS.matcher(stepText).find() && 
                stepText.length() > 30 && 
                !GENERIC_WORDS.matcher(stepText).find()) {
                actionableSteps++;
            }
        }
        
        return (double) actionableSteps / steps.size();
    }

    private double calculateOverallScore(QualityScore score) {
        // Weighted average of all components
        return (score.technicalSpecificity * 0.25) +
               (score.implementationDepth * 0.25) +
               (score.domainRelevance * 0.20) +
               (score.uniqueness * 0.10) +
               (score.completeness * 0.10) +
               (score.actionability * 0.10);
    }

    private String determineQualityLevel(double overallScore) {
        if (overallScore >= HIGH_QUALITY_SCORE) return "EXCELLENT";
        if (overallScore >= MIN_ACCEPTABLE_SCORE) return "GOOD";
        if (overallScore >= 0.45) return "ACCEPTABLE";
        return "POOR";
    }

    private List<String> generateFeedback(QualityScore score) {
        List<String> feedback = new ArrayList<>();
        
        if (score.technicalSpecificity < 0.6) {
            feedback.add("Add more specific technical terms and tools");
        }
        
        if (score.implementationDepth < 0.6) {
            feedback.add("Focus more on implementation and hands-on tasks");
        }
        
        if (score.domainRelevance < 0.6) {
            feedback.add("Include more domain-specific concepts and technologies");
        }
        
        if (score.uniqueness < 0.7) {
            feedback.add("Avoid generic language and provide more specific guidance");
        }
        
        if (score.actionability < 0.7) {
            feedback.add("Make steps more actionable with clear deliverables");
        }
        
        if (feedback.isEmpty()) {
            feedback.add("High quality roadmap with excellent technical depth");
        }
        
        return feedback;
    }

    public boolean isAcceptableQuality(QualityScore score) {
        return score.overallScore >= MIN_ACCEPTABLE_SCORE;
    }

    // Quality score container
    public static class QualityScore {
        public double technicalSpecificity = 0.0;
        public double implementationDepth = 0.0;
        public double domainRelevance = 0.0;
        public double uniqueness = 0.0;
        public double completeness = 0.0;
        public double actionability = 0.0;
        public double overallScore = 0.0;
        public String qualityLevel = "POOR";
        public List<String> feedback = new ArrayList<>();
        
        public boolean isHighQuality() {
            return overallScore >= HIGH_QUALITY_SCORE;
        }
        
        public boolean isAcceptable() {
            return overallScore >= MIN_ACCEPTABLE_SCORE;
        }
    }
}