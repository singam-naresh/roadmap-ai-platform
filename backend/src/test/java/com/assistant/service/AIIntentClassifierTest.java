package com.assistant.service;

import com.assistant.service.AIIntentClassifier.AIIntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Classification accuracy tests for AIIntentClassifier.
 *
 * Each test verifies that a given prompt is classified into the correct intent.
 * These tests serve as a regression suite — any change to the classifier
 * must not break these cases.
 */
class AIIntentClassifierTest {

    private AIIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new AIIntentClassifier();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @DisplayName("Intent classification accuracy")
    @CsvSource({
        // ── CHAT ──────────────────────────────────────────────────────────────
        "hello,                                          CHAT",
        "hi there,                                       CHAT",
        "hey,                                            CHAT",
        "who are you,                                    CHAT",
        "what are you,                                   CHAT",
        "what is aura os,                                CHAT",
        "are you an ai,                                  CHAT",
        "thanks,                                         CHAT",
        "ok got it,                                      CHAT",
        "who is the PM of India,                         CHAT",
        "what is the capital of France,                  CHAT",
        "when was Java created,                          CHAT",

        // ── ROADMAP ───────────────────────────────────────────────────────────
        "Generate a Java learning roadmap,               ROADMAP",
        "Java roadmap,                                   ROADMAP",
        "Create a learning roadmap for Python,           ROADMAP",
        "Become a full stack developer,                  ROADMAP",
        "How to become a software engineer,              ROADMAP",
        "Career path for data scientist,                 ROADMAP",
        "AI Engineer learning path,                      ROADMAP",
        "Complete guide to mastering React,              ROADMAP",
        "6 month plan to learn Spring Boot,              ROADMAP",
        "Transition from frontend to backend developer,  ROADMAP",
        "Step by step guide to DevOps,                   ROADMAP",
        "Master plan for becoming a senior developer,    ROADMAP",
        "Best Java backend roadmap 2026,                 ROADMAP",
        "Roadmap to become AI engineer,                  ROADMAP",
        "Full stack developer roadmap,                   ROADMAP",
        "Backend developer career roadmap,               ROADMAP",
        "Spring Boot learning roadmap for beginners,     ROADMAP",
        "How to become a data scientist roadmap,         ROADMAP",

        // ── CODING ────────────────────────────────────────────────────────────
        "write a hello world program using java,         CODING",
        "write a function to reverse a string in Python, CODING",
        "generate code for a REST API in Spring Boot,    CODING",
        "fix NullPointerException in my code,            CODING",
        "debug my Java code,                             CODING",
        "I have a stack trace error,                     CODING",
        "optimize this SQL query,                        CODING",
        "refactor my React component,                    CODING",
        "algorithm for binary search,                    CODING",
        "time complexity of quicksort,                   CODING",
        "write a regex for email validation,             CODING",
        "create a function to sort an array,             CODING",
        "compile error in my Java class,                 CODING",

        // ── ANALYSIS ──────────────────────────────────────────────────────────
        "Compare React vs Angular,                       ANALYSIS",
        "React versus Vue which is better,               ANALYSIS",
        "pros and cons of microservices,                 ANALYSIS",
        "difference between REST and GraphQL,            ANALYSIS",
        "should I use PostgreSQL or MongoDB,             ANALYSIS",
        "evaluate my startup idea,                       ANALYSIS",
        "review my code architecture,                    ANALYSIS",
        "advantages and disadvantages of Docker,         ANALYSIS",

        // ── PRODUCTIVITY ──────────────────────────────────────────────────────
        "plan my week,                                   PRODUCTIVITY",
        "create a daily schedule for deep work,          PRODUCTIVITY",
        "build a morning routine,                        PRODUCTIVITY",
        "time blocking system for developers,            PRODUCTIVITY",
        "pomodoro technique for studying,                PRODUCTIVITY",
        "habit tracker for fitness goals,                PRODUCTIVITY",

        // ── STARTUP ───────────────────────────────────────────────────────────
        "SaaS idea for developers,                       STARTUP",
        "GTM strategy for my app,                        STARTUP",
        "how to build a startup in 90 days,              STARTUP",
        "pitch deck for investor meeting,                STARTUP",
        "product market fit for B2B SaaS,                STARTUP",
        "revenue model for a marketplace,                STARTUP",

        // ── LEARNING ──────────────────────────────────────────────────────────
        "Teach me Java streams,                          LEARNING",
        "Explain how Java generics work,                 LEARNING",
        "Help me understand recursion,                   LEARNING",
        "Teach me about design patterns,                 LEARNING",
        "Explain the concept of polymorphism,            LEARNING",
        "I don't understand dependency injection,        LEARNING",
        "ELI5 what is a REST API,                        LEARNING",
        "Explain microservices in simple terms,          LEARNING",
        "What is the concept of closures in JavaScript,  LEARNING",
        "Beginner guide to Docker containers,            LEARNING",
    })
    void testClassification(String prompt, String expectedIntent) {
        AIIntentType result = classifier.classify(prompt.trim());
        assertThat(result.name())
                .as("Prompt: \"%s\" should be %s but was %s", prompt.trim(), expectedIntent.trim(), result.name())
                .isEqualTo(expectedIntent.trim());
    }
}
