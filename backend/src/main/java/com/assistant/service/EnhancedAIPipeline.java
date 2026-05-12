package com.assistant.service;

import com.assistant.service.AIResponseValidator.ValidationResult;
import com.assistant.service.DomainDetectionEngine.DomainAnalysisResult;
import com.assistant.service.RoadmapQualityScorer.QualityScore;
import com.assistant.service.GroqClient.ChatMessage;
import com.assistant.config.ObservabilityConfig.AIPipelineMetrics;
import com.assistant.config.PerformanceMonitoringAspect.MonitorPerformance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Unified Adaptive AI Pipeline — Adaptive AI Learning Roadmap Platform
 *
 * Simplified pipeline (removed cascading quality-gate retries that caused
 * "duplicate element" errors on valid natural-language prompts):
 *
 *   1. Domain detection (cached)
 *   2. Skill level inference (Phase 8 adaptive)
 *   3. Roadmap strategy selection (beginner/intermediate/advanced/expert)
 *   4. Architecture profile detection (informational, no longer a hard gate)
 *   5. Groq API call with strategy-aware prompt (up to MAX_RETRIES)
 *   6. Structural JSON validation only
 *   7. Output polish (formatting, whitespace)
 *   8. Quality score recorded for observability (no rejection gate)
 *   9. Domain-aware fallback if all attempts fail
 *
 * Removed hard gates: SemanticUniquenessEngine, SelfCritiqueEngine,
 * TimelineRealismEngine, OutputQualityEngine, StackConsistencyEngine validation,
 * TechnologyOntologyEngine, DependencyGraphEngine — these caused cascading
 * retry failures and rejected valid roadmaps.
 */
@Service
public class EnhancedAIPipeline {

    private static final Logger log = LoggerFactory.getLogger(EnhancedAIPipeline.class);
    private static final int MAX_RETRIES = 3;

    private final GroqClient groqClient;
    private final AIResponseValidator validator;
    private final ObjectMapper objectMapper;
    private final DomainDetectionEngine domainEngine;
    private final DomainKnowledgeEngine knowledgeEngine;
    private final RoadmapQualityScorer qualityScorer;
    private final StackConsistencyEngine stackConsistencyEngine;
    private final AIPipelineMetrics metrics;
    private final OutputPolishEngine outputPolishEngine;
    private final SkillInferenceService skillInferenceService;
    private final RoadmapStrategyEngine roadmapStrategyEngine;

    // Accept all original constructor parameters for Spring DI compatibility
    // (unused engines are accepted but not stored)
    public EnhancedAIPipeline(GroqClient groqClient,
                               AIResponseValidator validator,
                               ObjectMapper objectMapper,
                               DomainDetectionEngine domainEngine,
                               DomainKnowledgeEngine knowledgeEngine,
                               RoadmapQualityScorer qualityScorer,
                               TechnicalSpecificityEngine specificityEngine,
                               StackConsistencyEngine stackConsistencyEngine,
                               TechnologyOntologyEngine technologyOntologyEngine,
                               DependencyGraphEngine dependencyGraphEngine,
                               AIPipelineMetrics metrics,
                               OutputQualityEngine outputQualityEngine,
                               StepCompletenessValidator completenessValidator,
                               DomainReasoningEngine domainReasoningEngine,
                               RoadmapSpecificityEngine roadmapSpecificityEngine,
                               TimelineRealismEngine timelineRealismEngine,
                               SemanticUniquenessEngine semanticUniquenessEngine,
                               RoleMaturityEngine roleMaturityEngine,
                               SelfCritiqueEngine selfCritiqueEngine,
                               OutputPolishEngine outputPolishEngine,
                               SkillInferenceService skillInferenceService,
                               RoadmapStrategyEngine roadmapStrategyEngine) {
        this.groqClient = groqClient;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.domainEngine = domainEngine;
        this.knowledgeEngine = knowledgeEngine;
        this.qualityScorer = qualityScorer;
        this.stackConsistencyEngine = stackConsistencyEngine;
        this.metrics = metrics;
        this.outputPolishEngine = outputPolishEngine;
        this.skillInferenceService = skillInferenceService;
        this.roadmapStrategyEngine = roadmapStrategyEngine;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Generates an adaptive roadmap for the given user input.
     *
     * @param sessionDomain  optional domain from conversation session context
     * @param conversationSummary optional summary of prior conversation messages
     *                            (e.g. "User said: I know nothing about Java")
     */
    @MonitorPerformance("roadmap.generation")
    public RoadmapGenerationResult generateRoadmap(String userInput, String mode,
                                                    String category, String skillLevel,
                                                    String sessionDomain,
                                                    String conversationSummary) {
        Timer.Sample sample = metrics.startRoadmapGeneration();
        RoadmapGenerationResult result = new RoadmapGenerationResult();

        try {
            // Step 1: Domain detection (use session domain if available)
            DomainAnalysisResult domainAnalysis;
            String primaryDomain;
            String secondaryDomain;

            if (sessionDomain != null && !sessionDomain.equals("GENERAL")) {
                primaryDomain   = sessionDomain;
                secondaryDomain = null;
                domainAnalysis  = domainEngine.analyzeDomain(userInput);
                log.info("[ai-pipeline] Using session domain: {}", primaryDomain);
            } else {
                domainAnalysis  = getCachedDomainAnalysis(userInput);
                primaryDomain   = domainAnalysis.getPrimaryDomain();
                secondaryDomain = domainAnalysis.getSecondaryDomain();
            }

            metrics.recordDomainDetection(primaryDomain, domainAnalysis.getConfidence());
            result.setDomainAnalysis(domainAnalysis);

            // Step 2: Skill inference — caller-provided level takes priority
            SkillInferenceService.SkillInference skillInference = skillInferenceService.infer(userInput);

            String expertiseLevel;
            if (skillLevel != null && !skillLevel.equals("intermediate")) {
                expertiseLevel = skillLevel.toUpperCase();
                log.info("[ai-pipeline] Using caller-provided skill level: {}", expertiseLevel);
            } else {
                expertiseLevel = skillInference.confidence >= 0.6
                        ? skillInference.level.toExpertiseLevel()
                        : knowledgeEngine.detectExpertiseLevel(userInput);
            }

            log.info("[ai-pipeline] domain={} expertise={} input='{}'",
                    primaryDomain, expertiseLevel,
                    userInput.substring(0, Math.min(60, userInput.length())));

            // Step 3: Build adaptive strategy
            RoadmapStrategyEngine.RoadmapStrategy strategy =
                    roadmapStrategyEngine.buildStrategy(skillInference.level, primaryDomain, userInput);

            // Step 4: Architecture profile (informational only)
            StackConsistencyEngine.ArchitectureAnalysis architectureAnalysis =
                    getCachedArchitectureAnalysis(userInput, primaryDomain);
            result.setArchitectureAnalysis(architectureAnalysis);

            // Step 5: Generation loop
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                log.info("[ai-pipeline] Attempt {}/{} domain={} level={}", attempt, MAX_RETRIES, primaryDomain, expertiseLevel);

                try {
                    List<ChatMessage> messages = buildPrompt(
                            userInput, mode, primaryDomain,
                            expertiseLevel, attempt, skillInference, strategy,
                            conversationSummary);

                    String rawResponse = groqClient.chat(messages);
                    ValidationResult validation = validator.validateRoadmapResponse(rawResponse);

                    if (validation.isValid()) {
                        JsonNode polishedJson = outputPolishEngine.polish(validation.getParsedJson());

                        // ── Post-generation validation ────────────────────────
                        // Reject roadmaps that violate skill-level constraints
                        String violationReason = checkSkillLevelViolation(polishedJson, expertiseLevel);
                        if (violationReason != null) {
                            log.warn("[ai-pipeline] Skill-level violation on attempt {}: {}", attempt, violationReason);
                            result.addValidationErrors(List.of("Skill-level violation: " + violationReason));
                            metrics.recordValidationFailure("skill_level_violation");
                            continue; // force retry with stronger constraints
                        }

                        // ── Enforce correct difficulty in output ──────────────
                        polishedJson = enforceDifficulty(polishedJson, expertiseLevel);

                        QualityScore qualityScore = qualityScorer.scoreRoadmap(polishedJson, primaryDomain);
                        result.setQualityScore(qualityScore);
                        metrics.recordQualityScore(primaryDomain, qualityScore.overallScore, qualityScore.qualityLevel);

                        log.info("[ai-pipeline] Success on attempt {} — quality={:.2f}", attempt, qualityScore.overallScore);
                        result.setSuccess(true);
                        result.setValidatedJson(polishedJson);
                        result.setRawResponse(rawResponse);
                        result.setAttempts(attempt);
                        return result;
                    }

                    log.warn("[ai-pipeline] Validation failed on attempt {}: {}", attempt, validation.getErrors());
                    result.addValidationErrors(validation.getErrors());
                    metrics.recordValidationFailure("structural_validation_failed");

                } catch (Exception e) {
                    log.error("[ai-pipeline] Error on attempt {}: {}", attempt, e.getMessage());
                    result.addError("Attempt " + attempt + " failed: " + e.getMessage());
                    metrics.recordValidationFailure("exception");
                }
            }

            log.warn("[ai-pipeline] All attempts failed — using fallback for domain={}", primaryDomain);
            result.setSuccess(false);
            result.setFallbackUsed(true);
            result.setValidatedJson(createDomainAwareFallback(userInput, primaryDomain, expertiseLevel));
            metrics.recordValidationFailure("all_attempts_failed");
            return result;

        } finally {
            metrics.endRoadmapGeneration(sample);
        }
    }

    /** Overload without conversation summary. */
    public RoadmapGenerationResult generateRoadmap(String userInput, String mode,
                                                    String category, String skillLevel,
                                                    String sessionDomain) {
        return generateRoadmap(userInput, mode, category, skillLevel, sessionDomain, null);
    }

    /** Overload without session domain or conversation summary. */
    public RoadmapGenerationResult generateRoadmap(String userInput, String mode,
                                                    String category, String skillLevel) {
        return generateRoadmap(userInput, mode, category, skillLevel, null, null);
    }

    // =========================================================================
    // Prompt building
    // =========================================================================

    private List<ChatMessage> buildPrompt(String userInput, String mode,
                                           String primaryDomain, String expertiseLevel,
                                           int attempt,
                                           SkillInferenceService.SkillInference skillInference,
                                           RoadmapStrategyEngine.RoadmapStrategy strategy,
                                           String conversationSummary) {
        List<ChatMessage> messages = new ArrayList<>();

        messages.add(new ChatMessage("system",
                buildConsolidatedSystemPrompt(primaryDomain, expertiseLevel, mode,
                        strategy, skillInference.level, attempt, conversationSummary)));

        messages.add(new ChatMessage("user", userInput));
        return messages;
    }

    /**
     * Builds a single focused system prompt.
     *
     * Design principles:
     * - Mentor tone, not template engine
     * - Conversation context injected so the AI reasons from prior messages
     * - Difficulty MUST match skill level (beginner → Beginner, never Intermediate)
     * - Forbidden topics enforced hard for beginners
     * - Step quality examples show good vs bad
     * - Domain-specific resources
     * - Kept under ~1500 tokens to avoid Groq context limit
     */
    private String buildConsolidatedSystemPrompt(String domain, String expertiseLevel,
                                                   String mode,
                                                   RoadmapStrategyEngine.RoadmapStrategy strategy,
                                                   SkillInferenceService.SkillLevel level,
                                                   int attempt,
                                                   String conversationSummary) {
        boolean isBeginner = "BEGINNER".equals(expertiseLevel);
        boolean isAdvanced = "ADVANCED".equals(expertiseLevel) || "EXPERT".equals(expertiseLevel);
        StringBuilder sb = new StringBuilder();

        // ── Persona ───────────────────────────────────────────────────────────
        String persona = isBeginner
            ? switch (domain) {
                case "JAVA_BACKEND"   -> "a patient Java mentor who teaches complete beginners, starting from variables and loops";
                case "REACT_FRONTEND" -> "a patient frontend mentor who teaches complete beginners, starting from HTML, CSS, and JavaScript";
                case "AI_ENGINEERING" -> "a patient AI/ML mentor who teaches complete beginners, starting from Python fundamentals";
                case "DEVOPS"         -> "a patient DevOps mentor who teaches complete beginners, starting from Linux and Git";
                default               -> "a patient software engineering mentor who teaches complete beginners step by step";
              }
            : switch (domain) {
                case "AI_ENGINEERING"   -> "a senior AI engineer with deep experience in PyTorch, Hugging Face, and production ML systems";
                case "JAVA_BACKEND"     -> "a senior Java backend engineer with deep experience in Spring Boot and distributed systems";
                case "REACT_FRONTEND"   -> "a senior frontend engineer with deep experience in React, TypeScript, and modern web tooling";
                case "DEVOPS"           -> "a senior DevOps/SRE engineer with deep experience in Kubernetes, Terraform, and cloud-native systems";
                case "DATA_ENGINEERING" -> "a senior data engineer with deep experience in Apache Spark, Kafka, and data pipelines";
                case "SYSTEM_DESIGN"    -> "a principal engineer specializing in distributed systems design and large-scale architecture";
                default                 -> "a senior software engineer with broad production experience";
              };
        sb.append("You are ").append(persona).append(".\n\n");

        // ── Conversation context (most important for follow-up prompts) ────────
        if (conversationSummary != null && !conversationSummary.isBlank()) {
            sb.append("CONVERSATION CONTEXT (use this to personalize the roadmap):\n");
            sb.append(conversationSummary).append("\n\n");
            sb.append("The roadmap MUST be tailored to what the user said above. ");
            sb.append("Do not generate a generic roadmap — use the context.\n\n");
        }

        // ── JSON output format ────────────────────────────────────────────────
        sb.append(buildJsonFormatInstructions()).append("\n");

        // ── Skill level enforcement ───────────────────────────────────────────
        sb.append("SKILL LEVEL: ").append(strategy.difficultyLabel())
          .append(" — the 'difficulty' field MUST be exactly \"").append(strategy.difficultyLabel()).append("\"\n");
        sb.append("TIMELINE: ").append(strategy.realisticTimeline())
          .append(" — the 'estimatedTime' field MUST be exactly \"").append(strategy.realisticTimeline()).append("\"\n");
        sb.append("STEPS: Generate exactly ").append(strategy.recommendedStepCount()).append(" steps.\n\n");

        // ── Forbidden topics (hard block for beginners) ───────────────────────
        if (!strategy.forbiddenTopics().isEmpty()) {
            sb.append("FORBIDDEN — do NOT include these (too advanced for this level):\n");
            strategy.forbiddenTopics().stream().limit(10)
                    .forEach(t -> sb.append("  ✗ ").append(t).append("\n"));
            sb.append("\n");
        }

        // ── HARD BEGINNER CONSTRAINTS — injected separately for maximum enforcement ──
        if (isBeginner) {
            sb.append("ABSOLUTE BEGINNER HARD RULES — these are non-negotiable:\n");
            sb.append("  ✗ DO NOT include Kubernetes under any circumstances\n");
            sb.append("  ✗ DO NOT include Kafka under any circumstances\n");
            sb.append("  ✗ DO NOT include JWT or OAuth\n");
            sb.append("  ✗ DO NOT include Docker before Java fundamentals are complete\n");
            sb.append("  ✗ DO NOT include CI/CD pipelines\n");
            sb.append("  ✗ DO NOT include JPA advanced topics or Hibernate internals\n");
            sb.append("  ✗ DO NOT include distributed systems or microservices\n");
            sb.append("  ✗ DO NOT include Spring Security before Spring Boot basics\n");
            sb.append("  ✗ DO NOT include cloud architecture or load balancing\n");
            sb.append("  ✓ START with: variables, data types, conditions, loops, methods\n");
            sb.append("  ✓ THEN: arrays, OOP (classes, objects, inheritance)\n");
            sb.append("  ✓ THEN: collections, exception handling, file I/O\n");
            sb.append("  ✓ Spring Boot may appear only in the LAST 1-2 steps\n");
            sb.append("  ✓ difficulty MUST be \"Beginner\" — NEVER \"Intermediate\"\n");
            sb.append("  ✓ estimatedTime MUST be \"4-8 months\" or similar long timeline\n\n");
        }

        // ── Required topics ───────────────────────────────────────────────────
        if (!strategy.requiredTopics().isEmpty()) {
            sb.append("REQUIRED — these topics MUST appear in the roadmap:\n");
            strategy.requiredTopics().stream().limit(8)
                    .forEach(t -> sb.append("  ✓ ").append(t).append("\n"));
            sb.append("\n");
        }

        // ── Step quality ──────────────────────────────────────────────────────
        sb.append("STEP QUALITY — every step must be specific and educational:\n");
        sb.append("  ✗ BAD: \"Implement functionality\" / \"Deploy application\" / \"Set up monitoring\"\n");
        if (isBeginner) {
            sb.append("  ✓ GOOD: \"Learn Java variables: declare int, String, boolean, double and print them\"\n");
            sb.append("  ✓ GOOD: \"Build a Student class with name/grade fields, constructor, and getter methods\"\n");
        } else {
            sb.append("  ✓ GOOD: \"Configure Spring Security JWT: token generation, validation filter, role-based access\"\n");
            sb.append("  ✓ GOOD: \"Learn Kafka producers/consumers: partitioning, consumer groups, dead-letter queues\"\n");
        }
        sb.append("\n");

        // ── Mentor tone guidance ──────────────────────────────────────────────
        if (isBeginner) {
            sb.append("TONE: Write like a patient mentor. Use encouraging language. ");
            sb.append("Each step should feel achievable in 1-3 days. ");
            sb.append("The summary should acknowledge the user's starting point.\n\n");
        } else if (isAdvanced) {
            sb.append("TONE: Write like a senior engineer advising a peer. ");
            sb.append("Skip basics entirely. Focus on production concerns, architecture, and scale. ");
            sb.append("The summary should acknowledge what the user already knows.\n\n");
        }

        // ── Domain-specific resources ─────────────────────────────────────────
        sb.append("RESOURCES: ").append(getCompactResources(domain, isBeginner)).append("\n\n");

        // ── Mode ──────────────────────────────────────────────────────────────
        sb.append(buildModeInstructions(mode));

        if (attempt > 1) {
            sb.append("\nRETRY: Previous attempt had structural issues. Return valid JSON only. No markdown.\n");
        }

        return sb.toString();
    }

    private String getCompactResources(String domain, boolean isBeginner) {
        return switch (domain) {
            case "JAVA_BACKEND" -> isBeginner
                ? "BroCode Java (YouTube), Amigoscode Java, Oracle Java Docs (https://docs.oracle.com/javase/tutorial/), GeeksForGeeks Java"
                : "Baeldung (https://www.baeldung.com/), Spring Docs (https://docs.spring.io/), Java Brains (YouTube), Amigoscode";
            case "REACT_FRONTEND" ->
                "React Docs (https://react.dev/), MDN Web Docs (https://developer.mozilla.org/), freeCodeCamp React, Frontend Masters";
            case "AI_ENGINEERING" -> isBeginner
                ? "fast.ai (https://course.fast.ai/), PyTorch Tutorials (https://pytorch.org/tutorials/), Hugging Face Course, Andrej Karpathy (YouTube)"
                : "Hugging Face Docs (https://huggingface.co/docs), DeepSpeed (https://www.deepspeed.ai/), vLLM (https://docs.vllm.ai/)";
            case "DEVOPS" ->
                "Kubernetes Docs (https://kubernetes.io/docs/), Terraform Docs (https://developer.hashicorp.com/terraform/), KillerCoda (https://killercoda.com/), TechWorld with Nana (YouTube)";
            case "SYSTEM_DESIGN" ->
                "Designing Data-Intensive Applications (https://dataintensive.net/), System Design Primer (https://github.com/donnemartin/system-design-primer), ByteByteGo (https://blog.bytebytego.com/)";
            default ->
                "Official documentation, freeCodeCamp (https://www.freecodecamp.org/), roadmap.sh (https://roadmap.sh/)";
        };
    }

    private String buildModeInstructions(String mode) {
        return switch (mode) {
            case "detailed" -> """
                    OUTPUT MODE: DETAILED
                    - 8-12 comprehensive steps with specific technical requirements
                    - 5-7 expert tips with advanced techniques
                    - 4-5 detailed mistakes with prevention strategies
                    - 6-8 resources including official docs and advanced tutorials
                    """;
            case "simplified" -> """
                    OUTPUT MODE: SIMPLIFIED
                    - 4-6 focused steps covering core requirements
                    - 2-3 practical tips for getting started
                    - 2-3 common beginner mistakes to avoid
                    - 3-4 beginner-friendly resources
                    """;
            default -> """
                    OUTPUT MODE: BALANCED
                    - 6-8 well-structured steps with clear outcomes
                    - 3-4 actionable tips
                    - 3-4 important mistakes to avoid
                    - 4-5 quality resources
                    """;
        };
    }

    private String buildJsonFormatInstructions() {
        return """
                Return ONLY a valid JSON object. No markdown. No code fences. No text outside the JSON.

                The JSON must contain exactly these fields:
                {
                  "summary": "2-3 sentence description of the approach and expected outcome",
                  "estimatedTime": "realistic time estimate (e.g. '4-6 weeks', '3 months')",
                  "difficulty": "Beginner | Intermediate | Advanced",
                  "prerequisites": ["prerequisite 1", "prerequisite 2"],
                  "steps": ["actionable step 1", "actionable step 2", "actionable step 3"],
                  "tips": ["tip 1", "tip 2"],
                  "mistakesToAvoid": ["mistake 1", "mistake 2"],
                  "resources": ["resource 1", "resource 2"]
                }

                RULES:
                - All fields required. Use [] for empty arrays.
                - "difficulty" must be exactly: Beginner, Intermediate, or Advanced.
                - Each step must be 25+ characters and start with an action verb.
                """;
    }

    // =========================================================================
    // Post-generation validation
    // =========================================================================

    /**
     * Checks whether a generated roadmap violates skill-level constraints.
     *
     * For BEGINNER roadmaps: rejects if advanced topics appear in steps.
     * For ADVANCED roadmaps: rejects if the roadmap is full of beginner syntax steps.
     *
     * Returns null if valid, or a description of the violation if invalid.
     */
    private String checkSkillLevelViolation(JsonNode roadmapJson, String expertiseLevel) {
        JsonNode stepsNode = roadmapJson.path("steps");
        if (!stepsNode.isArray() || stepsNode.size() == 0) return null;

        // Collect all step text
        StringBuilder allSteps = new StringBuilder();
        for (JsonNode step : stepsNode) {
            allSteps.append(step.asText().toLowerCase()).append(" ");
        }
        String steps = allSteps.toString();

        if ("BEGINNER".equals(expertiseLevel)) {
            // These topics must NOT appear in beginner roadmaps
            String[] forbidden = {
                "kubernetes", "kafka", "jwt", "oauth", "microservice",
                "distributed system", "terraform", "argocd", "istio",
                "ci/cd pipeline", "github actions", "jenkins",
                "docker compose advanced", "service mesh",
                "event sourcing", "cqrs", "saga pattern",
                "deepspeed", "vllm", "tensorrt", "rlhf",
                "gpu orchestration", "tensor parallel"
            };
            for (String topic : forbidden) {
                if (steps.contains(topic)) {
                    return "Beginner roadmap contains forbidden topic: " + topic;
                }
            }
        }

        if ("ADVANCED".equals(expertiseLevel) || "EXPERT".equals(expertiseLevel)) {
            // Advanced roadmaps should not be full of beginner syntax steps
            long beginnerStepCount = 0;
            for (JsonNode step : stepsNode) {
                String s = step.asText().toLowerCase();
                if (s.contains("hello world") || s.contains("install java") ||
                    s.contains("learn variables") || s.contains("learn loops") ||
                    s.contains("learn conditions") || s.contains("basic syntax")) {
                    beginnerStepCount++;
                }
            }
            // If more than half the steps are beginner-level, reject
            if (beginnerStepCount > stepsNode.size() / 2) {
                return "Advanced roadmap contains too many beginner steps (" + beginnerStepCount + ")";
            }
        }

        return null; // valid
    }

    /**
     * Enforces the correct difficulty label in the JSON output.
     * Overrides whatever the AI generated to match the actual skill level.
     */
    private JsonNode enforceDifficulty(JsonNode roadmapJson, String expertiseLevel) {
        try {
            String correctDifficulty = switch (expertiseLevel) {
                case "BEGINNER" -> "Beginner";
                case "ADVANCED", "EXPERT" -> "Advanced";
                default -> "Intermediate";
            };

            String currentDifficulty = roadmapJson.path("difficulty").asText("");
            if (correctDifficulty.equals(currentDifficulty)) return roadmapJson; // already correct

            // Override the difficulty field
            com.fasterxml.jackson.databind.node.ObjectNode mutable =
                    (com.fasterxml.jackson.databind.node.ObjectNode) roadmapJson.deepCopy();
            mutable.put("difficulty", correctDifficulty);

            // Also fix timeline for beginners if it looks too short
            if ("BEGINNER".equals(expertiseLevel)) {
                String timeline = roadmapJson.path("estimatedTime").asText("");
                // If timeline is less than 2 months, override it
                if (timeline.contains("week") || timeline.contains("days") ||
                    timeline.matches(".*[1-6]\\s*week.*")) {
                    mutable.put("estimatedTime", "4-6 months");
                }
            }

            log.info("[ai-pipeline] Enforced difficulty: {} → {}", currentDifficulty, correctDifficulty);
            return mutable;
        } catch (Exception e) {
            log.warn("[ai-pipeline] Could not enforce difficulty: {}", e.getMessage());
            return roadmapJson;
        }
    }

    // =========================================================================
    // Fallback generation
    // =========================================================================

    private JsonNode createDomainAwareFallback(String userInput, String primaryDomain, String expertiseLevel) {
        try {
            boolean isBeginner = "BEGINNER".equals(expertiseLevel);
            String steps = getDomainFallbackSteps(primaryDomain, isBeginner);
            String domainName = getDomainDisplayName(primaryDomain);
            String difficulty = isBeginner ? "Beginner" : "Intermediate";
            String timeline   = isBeginner ? "4-6 months" : "6-10 weeks";
            String fallbackJson = String.format("""
                {
                  "summary": "A structured %s roadmap tailored to your current level.",
                  "estimatedTime": "%s",
                  "difficulty": "%s",
                  "prerequisites": ["Development environment setup", "Git basics"],
                  "steps": %s,
                  "tips": [
                    "Focus on hands-on practice — build something after every concept",
                    "Don't skip the fundamentals, they compound over time",
                    "Join a community (Discord, Reddit, Stack Overflow) to get unstuck faster"
                  ],
                  "mistakesToAvoid": [
                    "Jumping to advanced topics before the basics are solid",
                    "Tutorial hell — build real projects, not just follow along"
                  ],
                  "resources": [
                    "Official documentation for the primary technology",
                    "freeCodeCamp — https://www.freecodecamp.org/",
                    "roadmap.sh — https://roadmap.sh/"
                  ]
                }
                """, domainName, timeline, difficulty, steps);
            return objectMapper.readTree(fallbackJson);
        } catch (Exception e) {
            log.error("[ai-pipeline] Failed to create fallback: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String getDomainFallbackSteps(String domain, boolean isBeginner) {
        if (isBeginner) {
            return switch (domain) {
                case "JAVA_BACKEND" -> """
                    [
                      "Install Java JDK 21 and IntelliJ IDEA Community Edition. Write your first Hello World program",
                      "Learn variables and data types: int, String, boolean, double. Practice printing and arithmetic",
                      "Learn control flow: if/else statements, for loops, while loops. Build a number guessing game",
                      "Learn methods: define them, pass parameters, return values. Build a simple calculator",
                      "Learn Object-Oriented Programming: classes, objects, constructors, and encapsulation",
                      "Learn Java Collections: ArrayList and HashMap. Build a student grade tracker",
                      "Learn exception handling: try/catch/finally. Handle common errors gracefully",
                      "Build a complete console application that uses all concepts learned so far"
                    ]
                    """;
                case "REACT_FRONTEND" -> """
                    [
                      "Learn HTML5: structure, headings, paragraphs, links, images, and forms",
                      "Learn CSS: selectors, box model, flexbox, and grid layout",
                      "Learn JavaScript fundamentals: variables, functions, arrays, objects, and DOM manipulation",
                      "Learn ES6+: arrow functions, destructuring, spread operator, and promises",
                      "Learn React basics: components, JSX syntax, and rendering elements",
                      "Learn React state: useState hook, event handlers, and conditional rendering",
                      "Learn useEffect hook: fetch data from an API and handle loading/error states",
                      "Build a complete React app: todo list or weather app using a public API"
                    ]
                    """;
                case "AI_ENGINEERING" -> """
                    [
                      "Learn Python fundamentals: variables, loops, functions, lists, and dictionaries",
                      "Learn NumPy: create arrays, perform operations, and understand broadcasting",
                      "Learn Pandas: load CSV files, filter data, group by, and basic analysis",
                      "Learn Matplotlib: create line plots, bar charts, and scatter plots",
                      "Learn statistics basics: mean, median, variance, and distributions",
                      "Learn scikit-learn: train/test split, linear regression, and classification",
                      "Understand neural networks: neurons, layers, activation functions, and forward pass",
                      "Build a simple classifier using PyTorch: train, evaluate, and improve accuracy"
                    ]
                    """;
                default -> """
                    [
                      "Set up your development environment and learn the basic syntax of your chosen language",
                      "Learn variables, data types, and basic operators",
                      "Learn control flow: conditions and loops",
                      "Learn functions: how to define, call, and reuse them",
                      "Learn basic data structures: arrays, lists, and dictionaries",
                      "Build a small project that uses everything you have learned",
                      "Learn version control with Git: commit, push, branch, and merge",
                      "Share your project on GitHub and get feedback"
                    ]
                    """;
            };
        }
        // Non-beginner fallbacks
        return switch (domain) {
            case "JAVA_BACKEND" -> """
                [
                  "Build a Spring Boot REST API with layered architecture: controller, service, repository",
                  "Implement Spring Security with JWT: token generation, validation filter, and role-based access",
                  "Connect to PostgreSQL with Spring Data JPA: entities, repositories, and custom queries",
                  "Add integration testing with Testcontainers and unit testing with Mockito",
                  "Implement async processing with Spring Events or a message queue",
                  "Add observability: Micrometer metrics, health checks, and structured logging",
                  "Containerize with Docker and deploy with a CI/CD pipeline using GitHub Actions"
                ]
                """;
            case "AI_ENGINEERING" -> """
                [
                  "Fine-tune a pre-trained model using Hugging Face Trainer API with LoRA/QLoRA",
                  "Build a RAG pipeline: document ingestion, chunking, embedding, and retrieval with FAISS",
                  "Deploy a model as a REST API using FastAPI with async inference and request batching",
                  "Implement model evaluation: BLEU, ROUGE, perplexity, and human preference metrics",
                  "Set up experiment tracking with MLflow: log parameters, metrics, and artifacts",
                  "Optimize inference with quantization (INT8/INT4) and vLLM for production serving",
                  "Build a complete ML pipeline with data preprocessing, training, evaluation, and monitoring"
                ]
                """;
            case "DEVOPS" -> """
                [
                  "Write Dockerfiles and Docker Compose for multi-service applications",
                  "Deploy to Kubernetes: pods, deployments, services, ingress, and ConfigMaps",
                  "Write Terraform to provision cloud infrastructure on AWS or GCP",
                  "Set up CI/CD with GitHub Actions: build, test, lint, and deploy on every push",
                  "Configure Prometheus metrics collection and build Grafana dashboards with alerts",
                  "Implement secrets management with HashiCorp Vault or cloud-native solutions",
                  "Practice GitOps with ArgoCD: declarative deployments and automated sync"
                ]
                """;
            default -> """
                [
                  "Define your goal clearly and identify the specific skills you need to build",
                  "Set up your development environment with all required tools and dependencies",
                  "Implement the core functionality following best practices for your domain",
                  "Add comprehensive testing: unit tests, integration tests, and end-to-end tests",
                  "Add observability: logging, metrics, and health checks",
                  "Deploy to a cloud platform and set up a CI/CD pipeline",
                  "Build a portfolio project that demonstrates your skills to employers"
                ]
                """;
        };
    }

    private String getDomainDisplayName(String domain) {
        return switch (domain) {
            case "AI_ENGINEERING"    -> "AI/ML Engineering";
            case "JAVA_BACKEND"      -> "Java Backend Development";
            case "REACT_FRONTEND"    -> "React Frontend Development";
            case "FULL_STACK"        -> "Full Stack Development";
            case "DEVOPS"            -> "DevOps Engineering";
            case "DATA_ENGINEERING"  -> "Data Engineering";
            case "CYBERSECURITY"     -> "Cybersecurity";
            case "MOBILE_DEVELOPMENT"-> "Mobile Development";
            case "CLOUD_ENGINEERING" -> "Cloud Engineering";
            case "SYSTEM_DESIGN"     -> "System Design";
            default                  -> "Software Development";
        };
    }

    // =========================================================================
    // Caching
    // =========================================================================

    @Cacheable(value = "domainAnalysis", key = "#userInput.hashCode()")
    private DomainAnalysisResult getCachedDomainAnalysis(String userInput) {
        return domainEngine.analyzeDomain(userInput);
    }

    @Cacheable(value = "architectureAnalysis", key = "#userInput.hashCode() + '_' + #domain")
    private StackConsistencyEngine.ArchitectureAnalysis getCachedArchitectureAnalysis(String userInput, String domain) {
        return stackConsistencyEngine.analyzeArchitecturalIntent(userInput, domain);
    }

    @Async("aiPipelineExecutor")
    public CompletableFuture<Void> precomputeAnalysis(String userInput) {
        try {
            DomainAnalysisResult domainAnalysis = domainEngine.analyzeDomain(userInput);
            stackConsistencyEngine.analyzeArchitecturalIntent(userInput, domainAnalysis.getPrimaryDomain());
        } catch (Exception e) {
            log.error("[ai-pipeline] Background precomputation failed: {}", e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    // =========================================================================
    // Result class
    // =========================================================================

    public static class RoadmapGenerationResult {
        private boolean success = false;
        private boolean fallbackUsed = false;
        private JsonNode validatedJson;
        private String rawResponse;
        private int attempts = 0;
        private List<String> errors = new ArrayList<>();
        private List<String> validationErrors = new ArrayList<>();
        private DomainAnalysisResult domainAnalysis;
        private QualityScore qualityScore;
        private List<String> qualityFeedback = new ArrayList<>();
        private StackConsistencyEngine.ArchitectureAnalysis architectureAnalysis;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public boolean isFallbackUsed() { return fallbackUsed; }
        public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }

        public JsonNode getValidatedJson() { return validatedJson; }
        public void setValidatedJson(JsonNode validatedJson) { this.validatedJson = validatedJson; }

        public String getRawResponse() { return rawResponse; }
        public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }

        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }

        public List<String> getValidationErrors() { return validationErrors; }
        public void addValidationErrors(List<String> errors) { this.validationErrors.addAll(errors); }

        public DomainAnalysisResult getDomainAnalysis() { return domainAnalysis; }
        public void setDomainAnalysis(DomainAnalysisResult domainAnalysis) { this.domainAnalysis = domainAnalysis; }

        public QualityScore getQualityScore() { return qualityScore; }
        public void setQualityScore(QualityScore qualityScore) { this.qualityScore = qualityScore; }

        public List<String> getQualityFeedback() { return qualityFeedback; }
        public void addQualityFeedback(String feedback) { this.qualityFeedback.add(feedback); }

        public StackConsistencyEngine.ArchitectureAnalysis getArchitectureAnalysis() { return architectureAnalysis; }
        public void setArchitectureAnalysis(StackConsistencyEngine.ArchitectureAnalysis architectureAnalysis) {
            this.architectureAnalysis = architectureAnalysis;
        }
    }
}
