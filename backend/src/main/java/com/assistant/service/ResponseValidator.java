package com.assistant.service;

import com.assistant.dto.TaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Intent-aware response validator.
 *
 * Validates every AI response before it is returned to the frontend.
 * Ensures required fields are present, arrays are non-null, and strings
 * are sanitized. Applies safe fallbacks for missing or malformed data
 * so the frontend never receives a response that would cause a crash.
 */
@Component
public class ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidator.class);

    // ── Shared fallbacks ──────────────────────────────────────────────────────
    private static final String FALLBACK_SUMMARY = "Response generated successfully.";

    /**
     * Validates and normalizes a TaskResponse in-place.
     * Returns the same object with all null/missing fields replaced by safe defaults.
     */
    public TaskResponse validate(TaskResponse response) {
        if (response == null) {
            log.error("[validator] Received null TaskResponse — this should never happen");
            return buildEmergencyFallback();
        }

        String intent = response.getIntentType();
        if (intent == null || intent.isBlank()) {
            log.warn("[validator] Missing intentType — defaulting to CHAT");
            response.setIntentType("CHAT");
            intent = "CHAT";
        }

        // Shared fields — always required
        validateSharedFields(response);

        // Intent-specific validation
        switch (intent) {
            case "ROADMAP", "STARTUP" -> validateRoadmapFields(response);
            case "CHAT"               -> validateChatFields(response);
            case "CODING"             -> validateCodingFields(response);
            case "ANALYSIS"           -> validateAnalysisFields(response);
            case "LEARNING"           -> validateLearningFields(response);
            case "PRODUCTIVITY"       -> validateProductivityFields(response);
            default -> {
                log.warn("[validator] Unknown intentType '{}' — applying CHAT validation", intent);
                validateChatFields(response);
            }
        }

        return response;
    }

    // =========================================================================
    // Shared field validation
    // =========================================================================

    private void validateSharedFields(TaskResponse r) {
        if (isBlank(r.getSummary())) {
            log.debug("[validator] Missing summary for intent={}", r.getIntentType());
            r.setSummary(FALLBACK_SUMMARY);
        }
        if (isBlank(r.getCategory()))   r.setCategory("general");
        if (isBlank(r.getSkillLevel())) r.setSkillLevel("intermediate");
        if (isBlank(r.getMode()))       r.setMode("default");
        if (isBlank(r.getUserInput()))  r.setUserInput("");
        if (isBlank(r.getAiOutput()))   r.setAiOutput("");
    }

    // =========================================================================
    // Intent-specific validators
    // =========================================================================

    private void validateRoadmapFields(TaskResponse r) {
        if (isBlank(r.getEstimatedTime())) r.setEstimatedTime("Varies");
        // Resolve difficulty dynamically from skill level — never hardcode "Intermediate"
        if (isBlank(r.getDifficulty())) {
            r.setDifficulty(resolveDifficulty(r.getSkillLevel()));
        }
        r.setPrerequisites(safeList(r.getPrerequisites()));
        r.setMistakesToAvoid(safeList(r.getMistakesToAvoid()));
        r.setResources(safeList(r.getResources()));
        r.setTips(safeList(r.getTips()));

        // Steps are critical — must have at least one
        List<String> steps = safeList(r.getSteps());
        if (steps.isEmpty()) {
            log.warn("[validator] ROADMAP response has no steps — applying fallback steps");
            steps = List.of(
                "Define your goal with a clear, measurable outcome.",
                "Break the goal into concrete sub-tasks.",
                "Execute the first sub-task and track your progress."
            );
        }
        r.setSteps(steps);
    }

    /**
     * Resolves the correct difficulty label from the stored skill level.
     * This ensures beginner roadmaps always show "Beginner", not "Intermediate".
     */
    private String resolveDifficulty(String skillLevel) {
        if (skillLevel == null) return "Intermediate";
        return switch (skillLevel.toLowerCase()) {
            case "beginner"     -> "Beginner";
            case "advanced"     -> "Advanced";
            case "expert"       -> "Advanced";
            default             -> "Intermediate";
        };
    }

    private void validateChatFields(TaskResponse r) {
        if (isBlank(r.getMessage())) {
            // Fall back to aiOutput if message is missing
            String fallback = isBlank(r.getAiOutput()) ? "I'm here to help. What would you like to know?" : r.getAiOutput();
            log.debug("[validator] CHAT missing message — using aiOutput as fallback");
            r.setMessage(fallback);
        }
        r.setSuggestions(safeList(r.getSuggestions()));
    }

    private void validateCodingFields(TaskResponse r) {
        if (isBlank(r.getLanguage()))     r.setLanguage("text");
        if (isBlank(r.getExplanation()))  r.setExplanation("");
        r.setKeyPoints(safeList(r.getKeyPoints()));
        r.setCommonMistakes(safeList(r.getCommonMistakes()));
        r.setResources(safeList(r.getResources()));
        r.setClarificationQuestions(safeList(r.getClarificationQuestions()));

        // Code blocks — must have at least one if no clarification questions
        if (safeList(r.getClarificationQuestions()).isEmpty()) {
            List<TaskResponse.CodeBlock> blocks = r.getCodeBlocks() != null ? r.getCodeBlocks() : new ArrayList<>();
            if (blocks.isEmpty()) {
                log.warn("[validator] CODING response has no code blocks and no clarification questions");
                // Don't add fake code — just ensure the list is non-null
            }
            r.setCodeBlocks(blocks);
        } else {
            r.setCodeBlocks(r.getCodeBlocks() != null ? r.getCodeBlocks() : new ArrayList<>());
        }
    }

    private void validateAnalysisFields(TaskResponse r) {
        if (isBlank(r.getVerdict())) r.setVerdict("");
        r.setPros(safeList(r.getPros()));
        r.setCons(safeList(r.getCons()));
        r.setRecommendations(safeList(r.getRecommendations()));
        r.setUseCases(safeList(r.getUseCases()));
        r.setSections(r.getSections() != null ? r.getSections() : new ArrayList<>());
    }

    private void validateLearningFields(TaskResponse r) {
        if (isBlank(r.getConceptTitle()))       r.setConceptTitle(r.getUserInput());
        if (isBlank(r.getConceptExplanation())) r.setConceptExplanation("");
        r.setKeyPoints(safeList(r.getKeyPoints()));
        r.setCommonMisconceptions(safeList(r.getCommonMisconceptions()));
        r.setPracticeExercises(safeList(r.getPracticeExercises()));
        r.setBestPractices(safeList(r.getBestPractices()));
        r.setResources(safeList(r.getResources()));
        r.setNextTopics(safeList(r.getNextTopics()));
        r.setExamples(r.getExamples() != null ? r.getExamples() : new ArrayList<>());
    }

    private void validateProductivityFields(TaskResponse r) {
        if (isBlank(r.getSystemTitle())) r.setSystemTitle("Productivity System");
        if (isBlank(r.getOverview()))    r.setOverview("");
        if (isBlank(r.getWeeklyReview())) r.setWeeklyReview("");
        r.setPriorities(safeList(r.getPriorities()));
        r.setHabits(safeList(r.getHabits()));
        r.setTools(safeList(r.getTools()));
        r.setTips(safeList(r.getTips()));
        r.setCommonMistakes(safeList(r.getCommonMistakes()));
        r.setSchedule(r.getSchedule() != null ? r.getSchedule() : new ArrayList<>());
    }

    // =========================================================================
    // Emergency fallback — returned when response is null
    // =========================================================================

    private TaskResponse buildEmergencyFallback() {
        return TaskResponse.builder()
                .intentType("CHAT")
                .userInput("")
                .summary("An error occurred while generating the response.")
                .message("I encountered an issue processing your request. Please try again.")
                .suggestions(List.of("Try rephrasing your question", "Start a new conversation"))
                .category("general")
                .skillLevel("intermediate")
                .mode("default")
                .aiOutput("")
                .build();
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private List<String> safeList(List<String> list) {
        return list != null ? list : new ArrayList<>();
    }
}
