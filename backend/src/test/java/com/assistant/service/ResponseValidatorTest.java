package com.assistant.service;

import com.assistant.dto.TaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ResponseValidator — ensures null/missing fields are safely normalized
 * and the frontend never receives a response that would cause a crash.
 */
class ResponseValidatorTest {

    private ResponseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResponseValidator();
    }

    // ── Null safety ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("null response returns emergency fallback")
    void nullResponseReturnsFallback() {
        TaskResponse result = validator.validate(null);
        assertThat(result).isNotNull();
        assertThat(result.getIntentType()).isEqualTo("CHAT");
        assertThat(result.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("missing intentType defaults to CHAT")
    void missingIntentTypeDefaultsToChat() {
        TaskResponse r = TaskResponse.builder().userInput("hello").build();
        validator.validate(r);
        assertThat(r.getIntentType()).isEqualTo("CHAT");
    }

    // ── Shared field defaults ─────────────────────────────────────────────────

    @Test
    @DisplayName("blank summary gets default value")
    void blankSummaryGetsDefault() {
        TaskResponse r = TaskResponse.builder().intentType("CHAT").userInput("hi").build();
        validator.validate(r);
        assertThat(r.getSummary()).isNotBlank();
    }

    @Test
    @DisplayName("null category defaults to general")
    void nullCategoryDefaultsToGeneral() {
        TaskResponse r = TaskResponse.builder().intentType("CHAT").userInput("hi").build();
        validator.validate(r);
        assertThat(r.getCategory()).isEqualTo("general");
    }

    // ── ROADMAP validation ────────────────────────────────────────────────────

    @Test
    @DisplayName("ROADMAP with null steps gets fallback steps")
    void roadmapNullStepsGetsFallback() {
        TaskResponse r = TaskResponse.builder()
                .intentType("ROADMAP").userInput("Java roadmap")
                .summary("A plan for learning Java.")
                .build();
        validator.validate(r);
        assertThat(r.getSteps()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("ROADMAP with empty steps gets fallback steps")
    void roadmapEmptyStepsGetsFallback() {
        TaskResponse r = TaskResponse.builder()
                .intentType("ROADMAP").userInput("Java roadmap")
                .summary("A plan for learning Java.")
                .steps(List.of())
                .build();
        validator.validate(r);
        assertThat(r.getSteps()).isNotEmpty();
    }

    @Test
    @DisplayName("ROADMAP null arrays become empty lists")
    void roadmapNullArraysBecomeEmptyLists() {
        TaskResponse r = TaskResponse.builder()
                .intentType("ROADMAP").userInput("Java roadmap")
                .summary("A plan.")
                .steps(List.of("Step 1", "Step 2", "Step 3"))
                .build();
        validator.validate(r);
        assertThat(r.getPrerequisites()).isNotNull();
        assertThat(r.getTips()).isNotNull();
        assertThat(r.getMistakesToAvoid()).isNotNull();
        assertThat(r.getResources()).isNotNull();
    }

    // ── CHAT validation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("CHAT with null message falls back to aiOutput")
    void chatNullMessageFallsBackToAiOutput() {
        TaskResponse r = TaskResponse.builder()
                .intentType("CHAT").userInput("hello")
                .aiOutput("Hello! I'm Aura OS.")
                .build();
        validator.validate(r);
        assertThat(r.getMessage()).isEqualTo("Hello! I'm Aura OS.");
    }

    @Test
    @DisplayName("CHAT null suggestions becomes empty list")
    void chatNullSuggestionsBecomesEmptyList() {
        TaskResponse r = TaskResponse.builder()
                .intentType("CHAT").userInput("hello")
                .message("Hi there!")
                .build();
        validator.validate(r);
        assertThat(r.getSuggestions()).isNotNull();
    }

    // ── CODING validation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CODING null language defaults to text")
    void codingNullLanguageDefaultsToText() {
        TaskResponse r = TaskResponse.builder()
                .intentType("CODING").userInput("write hello world")
                .summary("Hello world example.")
                .build();
        validator.validate(r);
        assertThat(r.getLanguage()).isEqualTo("text");
    }

    @Test
    @DisplayName("CODING null arrays become empty lists")
    void codingNullArraysBecomeEmptyLists() {
        TaskResponse r = TaskResponse.builder()
                .intentType("CODING").userInput("write hello world")
                .summary("Hello world example.")
                .build();
        validator.validate(r);
        assertThat(r.getKeyPoints()).isNotNull();
        assertThat(r.getCommonMistakes()).isNotNull();
        assertThat(r.getClarificationQuestions()).isNotNull();
        assertThat(r.getCodeBlocks()).isNotNull();
    }

    // ── ANALYSIS validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("ANALYSIS null arrays become empty lists")
    void analysisNullArraysBecomeEmptyLists() {
        TaskResponse r = TaskResponse.builder()
                .intentType("ANALYSIS").userInput("compare React vs Angular")
                .summary("A comparison.")
                .build();
        validator.validate(r);
        assertThat(r.getPros()).isNotNull();
        assertThat(r.getCons()).isNotNull();
        assertThat(r.getRecommendations()).isNotNull();
        assertThat(r.getSections()).isNotNull();
    }

    // ── LEARNING validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("LEARNING null conceptTitle defaults to userInput")
    void learningNullConceptTitleDefaultsToUserInput() {
        TaskResponse r = TaskResponse.builder()
                .intentType("LEARNING").userInput("Explain Java streams")
                .summary("Java streams explained.")
                .build();
        validator.validate(r);
        assertThat(r.getConceptTitle()).isEqualTo("Explain Java streams");
    }

    // ── PRODUCTIVITY validation ───────────────────────────────────────────────

    @Test
    @DisplayName("PRODUCTIVITY null systemTitle gets default")
    void productivityNullSystemTitleGetsDefault() {
        TaskResponse r = TaskResponse.builder()
                .intentType("PRODUCTIVITY").userInput("plan my week")
                .summary("A weekly plan.")
                .build();
        validator.validate(r);
        assertThat(r.getSystemTitle()).isEqualTo("Productivity System");
    }

    // ── Unknown intent ────────────────────────────────────────────────────────

    @Test
    @DisplayName("unknown intentType applies CHAT validation safely")
    void unknownIntentAppliesChatValidation() {
        TaskResponse r = TaskResponse.builder()
                .intentType("FUTURE_INTENT").userInput("something")
                .aiOutput("Some response.")
                .build();
        validator.validate(r);
        // Should not throw, should have a message
        assertThat(r.getMessage()).isNotBlank();
    }
}
