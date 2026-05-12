package com.assistant.service;

import com.assistant.model.Task;
import com.assistant.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Memory foundation — Phase 2 scaffolding.
 *
 * Tracks recurring topics, favorite intents, and learning continuity
 * by analysing the user's generation history. This is NOT full AI memory —
 * it is a lightweight, query-based pattern detector that provides context
 * for future prompt enrichment.
 *
 * Phase 3 will extend this with vector embeddings and semantic similarity.
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final TaskRepository taskRepository;

    public MemoryService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Returns a UserMemoryProfile derived from the user's full history.
     * Used to enrich prompts with personalized context.
     */
    public UserMemoryProfile buildProfile() {
        List<Task> all = taskRepository.findAllByOrderByCreatedAtDesc();

        if (all.isEmpty()) {
            return UserMemoryProfile.empty();
        }

        // Top intent types by frequency
        Map<String, Long> intentFreq = all.stream()
                .filter(t -> t.getIntentType() != null)
                .collect(Collectors.groupingBy(Task::getIntentType, Collectors.counting()));

        String favoriteIntent = intentFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("ROADMAP");

        // Top categories by frequency
        Map<String, Long> categoryFreq = all.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(Task::getCategory, Collectors.counting()));

        String favoriteCategory = categoryFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("general");

        // Recent topics (last 5 unique userInputs)
        List<String> recentTopics = all.stream()
                .map(Task::getUserInput)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

        // Recurring keywords across all inputs
        List<String> recurringKeywords = extractRecurringKeywords(all);

        log.debug("[memory] Profile built: favoriteIntent={}, favoriteCategory={}, recentTopics={}",
                favoriteIntent, favoriteCategory, recentTopics.size());

        return new UserMemoryProfile(
                favoriteIntent,
                favoriteCategory,
                recentTopics,
                recurringKeywords,
                all.size()
        );
    }

    /**
     * Extracts keywords that appear in 3+ different user inputs.
     * These represent the user's recurring interests.
     */
    private List<String> extractRecurringKeywords(List<Task> tasks) {
        Map<String, Long> wordFreq = tasks.stream()
                .map(Task::getUserInput)
                .filter(Objects::nonNull)
                .flatMap(input -> Arrays.stream(input.toLowerCase().split("\\s+")))
                .map(w -> w.replaceAll("[^a-zA-Z0-9]", ""))
                .filter(w -> w.length() > 4)
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        return wordFreq.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "about", "after", "again", "along", "also", "another", "before",
            "being", "between", "could", "doing", "during", "every", "first",
            "found", "given", "going", "great", "group", "having", "helps",
            "their", "there", "these", "those", "through", "under", "using",
            "where", "which", "while", "would", "write", "years"
    );

    // =========================================================================
    // Profile record
    // =========================================================================

    public record UserMemoryProfile(
            String favoriteIntent,
            String favoriteCategory,
            List<String> recentTopics,
            List<String> recurringKeywords,
            int totalGenerations
    ) {
        public static UserMemoryProfile empty() {
            return new UserMemoryProfile("ROADMAP", "general", List.of(), List.of(), 0);
        }

        public boolean hasHistory() {
            return totalGenerations > 0;
        }

        /**
         * Builds a concise context string for injection into AI prompts.
         * Only included when the user has meaningful history.
         */
        public String toPromptContext() {
            if (!hasHistory() || recentTopics.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("USER CONTEXT: This user has generated ").append(totalGenerations).append(" responses. ");
            sb.append("They frequently explore ").append(favoriteCategory).append(" topics. ");
            if (!recurringKeywords.isEmpty()) {
                sb.append("Recurring interests: ").append(String.join(", ", recurringKeywords.subList(0, Math.min(5, recurringKeywords.size())))).append(". ");
            }
            sb.append("Use this context to personalize your response.");
            return sb.toString();
        }
    }
}
