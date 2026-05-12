package com.assistant.service;

import com.assistant.model.Roadmap;
import com.assistant.model.RoadmapStep;
import com.assistant.repository.RoadmapRepository;
import com.assistant.repository.RoadmapStepRepository;
import com.assistant.service.GroqClient.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 6 — Roadmap Continuation Service
 *
 * Handles follow-up actions on an existing roadmap:
 *   - expand a step
 *   - shorten timeline
 *   - increase difficulty
 *   - add technologies
 *   - convert to production-grade
 *   - generate project ideas
 *   - generate interview prep
 *   - add exercises / resources
 *
 * Maintains roadmap context across prompts and updates the
 * existing roadmap instead of generating a disconnected new one.
 */
@Service
public class RoadmapContinuationService {

    private static final Logger log = LoggerFactory.getLogger(RoadmapContinuationService.class);

    private final GroqClient             groqClient;
    private final RoadmapRepository      roadmapRepository;
    private final RoadmapStepRepository  roadmapStepRepository;
    private final ObjectMapper           objectMapper;

    public RoadmapContinuationService(GroqClient groqClient,
                                       RoadmapRepository roadmapRepository,
                                       RoadmapStepRepository roadmapStepRepository,
                                       ObjectMapper objectMapper) {
        this.groqClient            = groqClient;
        this.roadmapRepository     = roadmapRepository;
        this.roadmapStepRepository = roadmapStepRepository;
        this.objectMapper          = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Action detection
    // ─────────────────────────────────────────────────────────────────────────

    public ContinuationAction detectAction(String userInput) {
        String lower = userInput.toLowerCase();

        if (lower.contains("expand") || lower.contains("more detail") || lower.contains("elaborate"))
            return ContinuationAction.EXPAND_STEP;
        if (lower.contains("shorten") || lower.contains("faster") || lower.contains("accelerate"))
            return ContinuationAction.SHORTEN_TIMELINE;
        if (lower.contains("harder") || lower.contains("more difficult") || lower.contains("advanced version"))
            return ContinuationAction.INCREASE_DIFFICULTY;
        if (lower.contains("add") && (lower.contains("technolog") || lower.contains("tool") || lower.contains("framework")))
            return ContinuationAction.ADD_TECHNOLOGIES;
        if (lower.contains("production") || lower.contains("enterprise") || lower.contains("production-grade"))
            return ContinuationAction.PRODUCTION_GRADE;
        if (lower.contains("project") || lower.contains("build something") || lower.contains("hands-on"))
            return ContinuationAction.GENERATE_PROJECT;
        if (lower.contains("interview") || lower.contains("interview prep") || lower.contains("interview question"))
            return ContinuationAction.INTERVIEW_PREP;
        if (lower.contains("exercise") || lower.contains("practice") || lower.contains("resource"))
            return ContinuationAction.ADD_EXERCISES;
        if (lower.contains("quiz") || lower.contains("test my knowledge") || lower.contains("assess"))
            return ContinuationAction.GENERATE_QUIZ;
        if (lower.contains("explain") || lower.contains("what is") || lower.contains("how does"))
            return ContinuationAction.EXPLAIN_CONCEPT;
        if (lower.contains("code") || lower.contains("template") || lower.contains("starter"))
            return ContinuationAction.CODE_TEMPLATE;

        return ContinuationAction.GENERAL_REFINEMENT;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Continuation handlers
    // ─────────────────────────────────────────────────────────────────────────

    public ContinuationResponse handleContinuation(Long roadmapId, String userInput,
                                                    Long conversationId) {
        // Try to find the roadmap; if not found, use a lightweight context
        com.assistant.model.Roadmap roadmap;
        java.util.List<com.assistant.model.RoadmapStep> steps;
        try {
            roadmap = roadmapRepository.findById(roadmapId)
                    .orElse(null);
            steps = roadmap != null
                    ? roadmapStepRepository.findByRoadmapIdOrderByStepIndexAsc(roadmapId)
                    : java.util.List.of();
        } catch (Exception e) {
            log.warn("[continuation] Roadmap {} not found, using minimal context", roadmapId);
            roadmap = null;
            steps   = java.util.List.of();
        }

        ContinuationAction action = detectAction(userInput);

        log.info("[continuation] roadmap={} action={} input='{}'", roadmapId, action, userInput);

        if (roadmap == null) {
            // No roadmap found — do a general AI response with the prompt
            String response = groqClient.chat(java.util.List.of(
                    new GroqClient.ChatMessage("user", userInput)));
            return new ContinuationResponse(action(action), response, "AI response", roadmapId);
        }

        return switch (action) {
            case EXPAND_STEP        -> expandStep(roadmap, steps, userInput);
            case SHORTEN_TIMELINE   -> shortenTimeline(roadmap, steps, userInput);
            case INCREASE_DIFFICULTY -> increaseDifficulty(roadmap, steps, userInput);
            case ADD_TECHNOLOGIES   -> addTechnologies(roadmap, steps, userInput);
            case PRODUCTION_GRADE   -> convertToProductionGrade(roadmap, steps, userInput);
            case GENERATE_PROJECT   -> generateProject(roadmap, steps, userInput);
            case INTERVIEW_PREP     -> generateInterviewPrep(roadmap, steps, userInput);
            case ADD_EXERCISES      -> addExercises(roadmap, steps, userInput);
            case GENERATE_QUIZ      -> generateQuiz(roadmap, steps, userInput);
            case EXPLAIN_CONCEPT    -> explainConcept(roadmap, steps, userInput);
            case CODE_TEMPLATE      -> generateCodeTemplate(roadmap, steps, userInput);
            default                 -> generalRefinement(roadmap, steps, userInput);
        };
    }

    private ContinuationResponse expandStep(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        // Detect which step to expand
        int stepIndex = extractStepIndex(userInput, steps);
        String stepTitle = stepIndex >= 0 && stepIndex < steps.size()
                ? steps.get(stepIndex).getTitle() : "the requested step";

        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "TASK: Expand step " + (stepIndex + 1) + " (\"" + stepTitle + "\") into 3–5 detailed sub-steps. " +
                "Each sub-step must be implementation-focused with specific technologies, commands, and measurable outcomes. " +
                "Return as a JSON array: {\"subSteps\": [\"sub-step 1\", \"sub-step 2\", ...]}";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.EXPAND_STEP), response,
                "Expanded step " + (stepIndex + 1) + ": " + stepTitle, roadmap.getId());
    }

    private ContinuationResponse shortenTimeline(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "TASK: Rewrite this roadmap with a 40% shorter timeline by: " +
                "1) Removing non-essential steps, 2) Parallelizing independent steps, " +
                "3) Focusing only on production-critical skills. " +
                "Keep the same JSON structure. Maintain technical depth — just remove fluff.";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.SHORTEN_TIMELINE), response,
                "Accelerated roadmap generated", roadmap.getId());
    }

    private ContinuationResponse increaseDifficulty(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "TASK: Upgrade this roadmap to ADVANCED/EXPERT level by: " +
                "1) Replacing basic steps with production-grade equivalents, " +
                "2) Adding architecture, scaling, and observability concerns, " +
                "3) Including advanced tooling (distributed systems, performance tuning, etc.). " +
                "Keep the same JSON structure.";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.INCREASE_DIFFICULTY), response,
                "Advanced roadmap generated", roadmap.getId());
    }

    private ContinuationResponse addTechnologies(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "User request: " + userInput + "\n\n" +
                "TASK: Integrate the requested technologies into the existing roadmap steps. " +
                "Update relevant steps to include the new technologies with proper context. " +
                "Return the updated steps array as JSON.";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.ADD_TECHNOLOGIES), response,
                "Technologies added to roadmap", roadmap.getId());
    }

    private ContinuationResponse convertToProductionGrade(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "TASK: Convert this roadmap to production-grade by adding to each step: " +
                "monitoring (Prometheus/Grafana), security hardening, CI/CD pipeline, " +
                "error handling, logging, performance optimization, and deployment automation. " +
                "Return updated steps JSON.";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.PRODUCTION_GRADE), response,
                "Production-grade roadmap generated", roadmap.getId());
    }

    private ContinuationResponse generateProject(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "TASK: Generate 3 hands-on project ideas that reinforce this roadmap's skills. " +
                "Each project must: 1) Use the technologies from the roadmap, " +
                "2) Be completable in 1–2 weeks, 3) Be portfolio-worthy. " +
                "Return as JSON: {\"projects\": [{\"title\": \"\", \"description\": \"\", \"techStack\": [], \"deliverables\": []}]}";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.GENERATE_PROJECT), response,
                "Project ideas generated", roadmap.getId());
    }

    private ContinuationResponse generateInterviewPrep(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "TASK: Generate interview preparation material for this roadmap's domain. Include: " +
                "1) 10 technical interview questions with answers, " +
                "2) 3 system design questions, " +
                "3) Key concepts to master, " +
                "4) Common mistakes to avoid in interviews. " +
                "Return as JSON: {\"questions\": [], \"systemDesign\": [], \"keyConcepts\": [], \"mistakes\": []}";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.INTERVIEW_PREP), response,
                "Interview prep generated", roadmap.getId());
    }

    private ContinuationResponse addExercises(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "TASK: Add practical exercises and curated resources for each step. " +
                "Exercises must be hands-on coding/implementation tasks. " +
                "Resources must be specific (official docs, GitHub repos, courses). " +
                "Return as JSON: {\"exercises\": [{\"stepIndex\": 0, \"exercise\": \"\", \"resource\": \"\"}]}";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.ADD_EXERCISES), response,
                "Exercises and resources added", roadmap.getId());
    }

    private ContinuationResponse generateQuiz(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "TASK: Generate a 10-question quiz to test knowledge of this roadmap's content. " +
                "Mix multiple-choice and short-answer questions. " +
                "Return as JSON: {\"questions\": [{\"question\": \"\", \"options\": [], \"answer\": \"\", \"explanation\": \"\"}]}";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.GENERATE_QUIZ), response,
                "Quiz generated", roadmap.getId());
    }

    private ContinuationResponse explainConcept(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "User question: " + userInput + "\n\n" +
                "TASK: Explain the requested concept in the context of this roadmap. " +
                "Include: 1) Clear definition, 2) Why it matters for this domain, " +
                "3) Practical example, 4) How it connects to other roadmap steps.";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.EXPLAIN_CONCEPT), response,
                "Concept explained", roadmap.getId());
    }

    private ContinuationResponse generateCodeTemplate(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "User request: " + userInput + "\n\n" +
                "TASK: Generate a production-ready code template/starter for the requested component. " +
                "Include: proper structure, error handling, logging, and comments. " +
                "Use the technologies from this roadmap.";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.CODE_TEMPLATE), response,
                "Code template generated", roadmap.getId());
    }

    private ContinuationResponse generalRefinement(Roadmap roadmap, List<RoadmapStep> steps, String userInput) {
        String prompt = buildContextPrompt(roadmap, steps) + "\n\n" +
                "User request: " + userInput + "\n\n" +
                "TASK: Refine or update this roadmap based on the user's request. " +
                "Maintain the existing structure and quality level. " +
                "Return the updated roadmap as JSON.";

        String response = groqClient.chat(List.of(new ChatMessage("user", prompt)));
        return new ContinuationResponse(action(ContinuationAction.GENERAL_REFINEMENT), response,
                "Roadmap refined", roadmap.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildContextPrompt(Roadmap roadmap, List<RoadmapStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("ACTIVE ROADMAP CONTEXT:\n");
        sb.append("Title: ").append(roadmap.getTitle()).append("\n");
        sb.append("Summary: ").append(roadmap.getSummary()).append("\n");
        sb.append("Difficulty: ").append(roadmap.getDifficulty()).append("\n");
        sb.append("Estimated Time: ").append(roadmap.getEstimatedTime()).append("\n");
        sb.append("Progress: ").append(roadmap.getProgressPercentage()).append("%\n\n");

        // Inject skill-level safety guard
        String difficulty = roadmap.getDifficulty();
        if (difficulty != null && difficulty.equalsIgnoreCase("Beginner")) {
            sb.append("SKILL LEVEL GUARD: This is a BEGINNER roadmap. ");
            sb.append("Do NOT add: Kubernetes, Kafka, Terraform, microservices, distributed systems, ");
            sb.append("CI/CD pipelines, GPU orchestration, or any advanced enterprise topics.\n\n");
        }

        sb.append("CURRENT STEPS:\n");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i).getTitle()).append("\n");
        }
        return sb.toString();
    }

    private int extractStepIndex(String userInput, List<RoadmapStep> steps) {
        // Try to find "step N" pattern
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("step\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(userInput);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1)) - 1;
            return Math.max(0, Math.min(n, steps.size() - 1));
        }
        // Try to find step by keyword match
        String lower = userInput.toLowerCase();
        for (int i = 0; i < steps.size(); i++) {
            if (lower.contains(steps.get(i).getTitle().toLowerCase().substring(0, Math.min(10, steps.get(i).getTitle().length())))) {
                return i;
            }
        }
        return 0; // default to first step
    }

    private String action(ContinuationAction a) { return a.name(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public enum ContinuationAction {
        EXPAND_STEP, SHORTEN_TIMELINE, INCREASE_DIFFICULTY, ADD_TECHNOLOGIES,
        PRODUCTION_GRADE, GENERATE_PROJECT, INTERVIEW_PREP, ADD_EXERCISES,
        GENERATE_QUIZ, EXPLAIN_CONCEPT, CODE_TEMPLATE, GENERAL_REFINEMENT
    }

    public static class ContinuationResponse {
        public final String actionType;
        public final String content;
        public final String summary;
        public final Long   roadmapId;

        public ContinuationResponse(String actionType, String content, String summary, Long roadmapId) {
            this.actionType = actionType;
            this.content    = content;
            this.summary    = summary;
            this.roadmapId  = roadmapId;
        }
    }
}
