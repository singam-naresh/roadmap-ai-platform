package com.assistant.service;

import com.assistant.model.Task;
import com.assistant.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes real analytics metrics from the database.
 * All values are derived from live Task records — no hardcoded numbers.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final TaskRepository taskRepository;

    public AnalyticsService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Returns a complete analytics summary for the dashboard.
     */
    public AnalyticsSummary getSummary() {
        List<Task> all = taskRepository.findAllByOrderByCreatedAtDesc();

        int total          = all.size();
        int thisWeek       = countThisWeek(all);
        int learningCount  = countByIntent(all, "LEARNING");
        int codingCount    = countByIntent(all, "CODING");
        int roadmapCount   = countByIntent(all, "ROADMAP") + countByIntent(all, "STARTUP");
        int streak         = computeStreak(all);

        Map<String, Long> intentDistribution = all.stream()
                .filter(t -> t.getIntentType() != null)
                .collect(Collectors.groupingBy(Task::getIntentType, Collectors.counting()));

        Map<String, Long> categoryDistribution = all.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(Task::getCategory, Collectors.counting()));

        List<DailyActivity> dailyActivity = buildDailyActivity(all, 30);

        log.debug("[analytics] Summary computed: total={}, thisWeek={}, streak={}", total, thisWeek, streak);

        return new AnalyticsSummary(
                total, thisWeek, learningCount, codingCount, roadmapCount,
                streak, intentDistribution, categoryDistribution, dailyActivity
        );
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private int countThisWeek(List<Task> tasks) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        return (int) tasks.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(weekAgo))
                .count();
    }

    private int countByIntent(List<Task> tasks, String intent) {
        return (int) tasks.stream()
                .filter(t -> intent.equalsIgnoreCase(t.getIntentType()))
                .count();
    }

    /**
     * Computes the current learning streak: consecutive days with at least one generation.
     * Counts backwards from today.
     */
    private int computeStreak(List<Task> tasks) {
        if (tasks.isEmpty()) return 0;

        Set<LocalDate> activeDays = tasks.stream()
                .filter(t -> t.getCreatedAt() != null)
                .map(t -> t.getCreatedAt().toLocalDate())
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate day = LocalDate.now();

        // Allow today or yesterday as the streak start (don't break streak if user hasn't generated today yet)
        if (!activeDays.contains(day) && !activeDays.contains(day.minusDays(1))) {
            return 0;
        }
        if (!activeDays.contains(day)) {
            day = day.minusDays(1);
        }

        while (activeDays.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /**
     * Builds a daily activity breakdown for the last N days.
     * Returns one entry per day with the count of generations.
     */
    private List<DailyActivity> buildDailyActivity(List<Task> tasks, int days) {
        Map<LocalDate, Long> countsByDay = tasks.stream()
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> t.getCreatedAt().isAfter(LocalDateTime.now().minusDays(days)))
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().toLocalDate(),
                        Collectors.counting()
                ));

        List<DailyActivity> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            result.add(new DailyActivity(date.toString(), countsByDay.getOrDefault(date, 0L).intValue()));
        }
        return result;
    }

    // =========================================================================
    // Response records
    // =========================================================================

    public record AnalyticsSummary(
            int totalGenerations,
            int thisWeekGenerations,
            int learningCount,
            int codingCount,
            int roadmapCount,
            int currentStreak,
            Map<String, Long> intentDistribution,
            Map<String, Long> categoryDistribution,
            List<DailyActivity> dailyActivity
    ) {}

    public record DailyActivity(String date, int count) {}
}
