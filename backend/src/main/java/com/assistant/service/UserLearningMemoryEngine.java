package com.assistant.service;

import com.assistant.model.Task;
import com.assistant.model.User;
import com.assistant.repository.StepProgressRepository;
import com.assistant.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 4 — User Learning Memory Engine
 *
 * Tracks per-user learning patterns across all tasks:
 *   - Completed technologies / domains
 *   - Weak areas (repeated failures / slow progress)
 *   - Learning velocity (completions per week)
 *   - Preferred domains
 *   - Skill confidence scores
 *
 * Generates personalized recommendations and adaptive difficulty signals.
 */
@Service
public class UserLearningMemoryEngine {

    private static final Logger log = LoggerFactory.getLogger(UserLearningMemoryEngine.class);

    private final TaskRepository          taskRepository;
    private final StepProgressRepository  stepProgressRepository;

    public UserLearningMemoryEngine(TaskRepository taskRepository,
                                    StepProgressRepository stepProgressRepository) {
        this.taskRepository         = taskRepository;
        this.stepProgressRepository = stepProgressRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Profile building
    // ─────────────────────────────────────────────────────────────────────────

    public LearningProfile buildProfile(User user) {
        List<Task> tasks = taskRepository.findAllByUserOrderByCreatedAtDesc(user);

        // Domain frequency
        Map<String, Long> domainFreq = tasks.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().toLowerCase(), Collectors.counting()));

        // Preferred domain = most frequent
        String preferredDomain = domainFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("general");

        // Skill level distribution
        Map<String, Long> skillDist = tasks.stream()
                .filter(t -> t.getSkillLevel() != null)
                .collect(Collectors.groupingBy(t -> t.getSkillLevel().toLowerCase(), Collectors.counting()));

        // Learning velocity: tasks completed in last 7 days
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long recentTasks = tasks.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(weekAgo))
                .count();

        // Consistency score: days with activity in last 30 days
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        long activeDays = tasks.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(monthAgo))
                .map(t -> t.getCreatedAt().toLocalDate())
                .distinct()
                .count();
        double consistencyScore = Math.min(1.0, activeDays / 30.0);

        // Skill confidence per domain (based on task count + recency)
        Map<String, Double> skillConfidence = computeSkillConfidence(tasks);

        // Weak areas: domains with few completions or high failure markers
        List<String> weakAreas = identifyWeakAreas(tasks, domainFreq);

        LearningProfile profile = new LearningProfile(
                user.getId(), preferredDomain, domainFreq, skillDist,
                (int) recentTasks, consistencyScore, skillConfidence, weakAreas,
                tasks.size()
        );

        log.debug("[learning-memory] Profile for user={}: preferred={}, velocity={}, consistency={:.2f}",
                user.getId(), preferredDomain, recentTasks, consistencyScore);
        return profile;
    }

    public List<String> generateRecommendations(LearningProfile profile) {
        List<String> recs = new ArrayList<>();

        // Velocity-based
        if (profile.weeklyVelocity == 0) {
            recs.add("You haven't started any tasks this week — pick up where you left off in " + profile.preferredDomain);
        } else if (profile.weeklyVelocity >= 5) {
            recs.add("Great momentum! Consider tackling an advanced " + profile.preferredDomain + " challenge");
        }

        // Consistency
        if (profile.consistencyScore < 0.3) {
            recs.add("Try to practice daily — even 15 minutes builds lasting skills");
        }

        // Weak areas
        for (String weak : profile.weakAreas) {
            recs.add("Reinforce your " + weak + " skills with a focused practice session");
        }

        // Skill gaps
        if (profile.totalTasks < 3) {
            recs.add("Complete a few more tasks to unlock personalized learning paths");
        }

        return recs;
    }

    public String suggestAdaptiveDifficulty(LearningProfile profile, String domain) {
        Double confidence = profile.skillConfidence.getOrDefault(domain.toLowerCase(), 0.5);
        if (confidence > 0.75) return "ADVANCED";
        if (confidence > 0.4)  return "INTERMEDIATE";
        return "BEGINNER";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Double> computeSkillConfidence(List<Task> tasks) {
        Map<String, List<Task>> byDomain = tasks.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().toLowerCase()));

        Map<String, Double> confidence = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, List<Task>> entry : byDomain.entrySet()) {
            String domain = entry.getKey();
            List<Task> domainTasks = entry.getValue();

            // Base score from count (logarithmic)
            double countScore = Math.min(1.0, Math.log(domainTasks.size() + 1) / Math.log(10));

            // Recency bonus: tasks in last 30 days
            long recent = domainTasks.stream()
                    .filter(t -> t.getCreatedAt() != null &&
                                 t.getCreatedAt().isAfter(now.minusDays(30)))
                    .count();
            double recencyBonus = Math.min(0.3, recent * 0.05);

            confidence.put(domain, Math.min(1.0, countScore + recencyBonus));
        }
        return confidence;
    }

    private List<String> identifyWeakAreas(List<Task> tasks, Map<String, Long> domainFreq) {
        // Weak = domain tried but with low frequency relative to preferred
        if (domainFreq.isEmpty()) return List.of();

        long maxFreq = domainFreq.values().stream().max(Long::compareTo).orElse(1L);
        return domainFreq.entrySet().stream()
                .filter(e -> e.getValue() < maxFreq * 0.3 && e.getValue() >= 1)
                .map(Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class LearningProfile {
        public final Long                userId;
        public final String              preferredDomain;
        public final Map<String, Long>   domainFrequency;
        public final Map<String, Long>   skillLevelDistribution;
        public final int                 weeklyVelocity;
        public final double              consistencyScore;
        public final Map<String, Double> skillConfidence;
        public final List<String>        weakAreas;
        public final int                 totalTasks;

        public LearningProfile(Long userId, String preferredDomain,
                               Map<String, Long> domainFrequency,
                               Map<String, Long> skillLevelDistribution,
                               int weeklyVelocity, double consistencyScore,
                               Map<String, Double> skillConfidence,
                               List<String> weakAreas, int totalTasks) {
            this.userId                 = userId;
            this.preferredDomain        = preferredDomain;
            this.domainFrequency        = domainFrequency;
            this.skillLevelDistribution = skillLevelDistribution;
            this.weeklyVelocity         = weeklyVelocity;
            this.consistencyScore       = consistencyScore;
            this.skillConfidence        = skillConfidence;
            this.weakAreas              = weakAreas;
            this.totalTasks             = totalTasks;
        }
    }
}
