package com.assistant.service;

import com.assistant.dto.RoadmapResponseDto;
import com.assistant.dto.RoadmapStepDto;
import com.assistant.dto.TaskSummaryDto;
import com.assistant.model.*;
import com.assistant.repository.RoadmapRepository;
import com.assistant.repository.RoadmapStepRepository;
import com.assistant.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RoadmapService {

    private static final Logger log = LoggerFactory.getLogger(RoadmapService.class);

    private final RoadmapRepository roadmapRepository;
    private final RoadmapStepRepository roadmapStepRepository;
    private final TaskRepository taskRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public RoadmapService(RoadmapRepository roadmapRepository,
                         RoadmapStepRepository roadmapStepRepository,
                         TaskRepository taskRepository,
                         UserService userService,
                         ObjectMapper objectMapper) {
        this.roadmapRepository = roadmapRepository;
        this.roadmapStepRepository = roadmapStepRepository;
        this.taskRepository = taskRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a roadmap from a task's AI output JSON
     */
    @Transactional
    public Roadmap createRoadmapFromTask(Long taskId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        // Check if roadmap already exists for this task
        Optional<Roadmap> existing = roadmapRepository.findByTaskId(taskId);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            // Parse the AI output JSON
            JsonNode aiOutput = objectMapper.readTree(task.getAiOutput());
            
            String title = task.getUserInput();
            String summary = aiOutput.path("summary").asText("");
            String estimatedTime = aiOutput.path("estimatedTime").asText("");
            String difficulty = aiOutput.path("difficulty").asText("Intermediate");
            
            // Extract steps
            JsonNode stepsNode = aiOutput.path("steps");
            int totalSteps = stepsNode.isArray() ? stepsNode.size() : 0;

            // Create roadmap
            Roadmap roadmap = new Roadmap(currentUser, task, title, summary, estimatedTime, difficulty, totalSteps);
            roadmap = roadmapRepository.save(roadmap);

            // Create steps
            if (stepsNode.isArray()) {
                for (int i = 0; i < stepsNode.size(); i++) {
                    String stepTitle = stepsNode.get(i).asText();
                    RoadmapStep step = new RoadmapStep(roadmap, i, stepTitle, "");
                    roadmapStepRepository.save(step);
                }
            }

            log.info("[roadmap] Created roadmap {} with {} steps for user {}", 
                    roadmap.getId(), totalSteps, currentUser.getEmail());

            return roadmap;

        } catch (Exception e) {
            log.error("[roadmap] Failed to create roadmap from task {}: {}", taskId, e.getMessage());
            throw new RuntimeException("Failed to create roadmap: " + e.getMessage());
        }
    }

    /**
     * Gets roadmap by task ID, creating it if it doesn't exist
     */
    public RoadmapResponseDto getRoadmapByTaskId(Long taskId) {
        Optional<Roadmap> existing = roadmapRepository.findByTaskId(taskId);
        Roadmap roadmap;
        if (existing.isPresent()) {
            roadmap = existing.get();
        } else {
            roadmap = createRoadmapFromTask(taskId);
        }
        return convertToDto(roadmap);
    }

    /**
     * Gets all roadmaps for current user
     */
    public List<RoadmapResponseDto> getUserRoadmaps() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("User not authenticated");
        }
        List<Roadmap> roadmaps = roadmapRepository.findByUserOrderByCreatedAtDesc(currentUser);
        return roadmaps.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * Gets roadmap steps
     */
    public List<RoadmapStepDto> getRoadmapSteps(Long roadmapId) {
        List<RoadmapStep> steps = roadmapStepRepository.findByRoadmapIdOrderByStepIndexAsc(roadmapId);
        return steps.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * Toggles step completion
     */
    @Transactional
    public RoadmapStepDto toggleStepCompletion(Long roadmapId, Integer stepIndex) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RuntimeException("Roadmap not found: " + roadmapId));

        RoadmapStep step = roadmapStepRepository.findByRoadmapAndStepIndex(roadmap, stepIndex)
                .orElseThrow(() -> new RuntimeException("Step not found: " + stepIndex));

        step.setCompleted(!step.getCompleted());
        step = roadmapStepRepository.save(step);

        // Update roadmap progress
        roadmap = roadmapRepository.save(roadmap);

        log.info("[roadmap] Step {} {} for roadmap {}", 
                stepIndex, step.getCompleted() ? "completed" : "uncompleted", roadmapId);

        return convertToDto(step);
    }

    /**
     * Updates step note
     */
    @Transactional
    public RoadmapStepDto updateStepNote(Long roadmapId, Integer stepIndex, String note) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RuntimeException("Roadmap not found: " + roadmapId));

        RoadmapStep step = roadmapStepRepository.findByRoadmapAndStepIndex(roadmap, stepIndex)
                .orElseThrow(() -> new RuntimeException("Step not found: " + stepIndex));

        step.setNotes(note);
        step = roadmapStepRepository.save(step);

        log.info("[roadmap] Updated note for step {} in roadmap {}", stepIndex, roadmapId);

        return convertToDto(step);
    }

    /**
     * Updates step priority
     */
    @Transactional
    public RoadmapStepDto updateStepPriority(Long roadmapId, Integer stepIndex, RoadmapStep.Priority priority) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RuntimeException("Roadmap not found: " + roadmapId));

        RoadmapStep step = roadmapStepRepository.findByRoadmapAndStepIndex(roadmap, stepIndex)
                .orElseThrow(() -> new RuntimeException("Step not found: " + stepIndex));

        step.setPriority(priority);
        step = roadmapStepRepository.save(step);

        log.info("[roadmap] Updated priority for step {} in roadmap {} to {}", 
                stepIndex, roadmapId, priority);

        return convertToDto(step);
    }

    /**
     * Gets roadmap by ID
     */
    public RoadmapResponseDto getRoadmapById(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RuntimeException("Roadmap not found: " + roadmapId));
        return convertToDto(roadmap);
    }

    /**
     * Exports roadmap as Markdown
     */
    public String exportAsMarkdown(RoadmapResponseDto roadmap, List<RoadmapStepDto> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(roadmap.getTitle()).append("\n\n");
        if (roadmap.getSummary() != null) {
            sb.append("> ").append(roadmap.getSummary()).append("\n\n");
        }
        sb.append("**Difficulty:** ").append(roadmap.getDifficulty()).append("  \n");
        sb.append("**Estimated Time:** ").append(roadmap.getEstimatedTime()).append("  \n");
        sb.append("**Progress:** ").append(roadmap.getProgressPercentage()).append("%\n\n");
        sb.append("---\n\n");
        sb.append("## Steps\n\n");
        for (RoadmapStepDto step : steps) {
            String checkbox = Boolean.TRUE.equals(step.getCompleted()) ? "[x]" : "[ ]";
            sb.append("- ").append(checkbox).append(" **Step ").append(step.getStepIndex() + 1).append(":** ")
              .append(step.getTitle()).append("\n");
            if (step.getNotes() != null && !step.getNotes().isBlank()) {
                sb.append("  > ").append(step.getNotes()).append("\n");
            }
        }
        sb.append("\n---\n");
        sb.append("*Generated by Adaptive AI Learning Roadmap Platform*\n");
        return sb.toString();
    }

    /**
     * Exports roadmap as JSON
     */
    public String exportAsJson(RoadmapResponseDto roadmap, List<RoadmapStepDto> steps) {
        try {
            java.util.Map<String, Object> export = new java.util.LinkedHashMap<>();
            export.put("id", roadmap.getId());
            export.put("title", roadmap.getTitle());
            export.put("summary", roadmap.getSummary());
            export.put("difficulty", roadmap.getDifficulty());
            export.put("estimatedTime", roadmap.getEstimatedTime());
            export.put("progressPercentage", roadmap.getProgressPercentage());
            export.put("completedSteps", roadmap.getCompletedSteps());
            export.put("totalSteps", roadmap.getTotalSteps());
            export.put("exportedAt", java.time.LocalDateTime.now().toString());
            export.put("steps", steps.stream().map(s -> {
                java.util.Map<String, Object> stepMap = new java.util.LinkedHashMap<>();
                stepMap.put("index", s.getStepIndex());
                stepMap.put("title", s.getTitle());
                stepMap.put("completed", s.getCompleted());
                stepMap.put("priority", s.getPriority());
                if (s.getNotes() != null) stepMap.put("notes", s.getNotes());
                return stepMap;
            }).collect(java.util.stream.Collectors.toList()));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export as JSON: " + e.getMessage());
        }
    }

    /**
     * Gets roadmap progress statistics
     */
    public RoadmapProgress getRoadmapProgress(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RuntimeException("Roadmap not found: " + roadmapId));

        List<RoadmapStep> steps = roadmapStepRepository.findByRoadmapOrderByStepIndexAsc(roadmap);
        
        long completedCount = steps.stream().mapToLong(s -> s.getCompleted() ? 1 : 0).sum();
        int progressPercentage = steps.isEmpty() ? 0 : (int) ((completedCount * 100) / steps.size());

        return new RoadmapProgress(
                roadmap.getId(),
                (int) completedCount,
                steps.size(),
                progressPercentage,
                roadmap.getUpdatedAt()
        );
    }

    /**
     * DTO for roadmap progress
     */
    public static class RoadmapProgress {
        private final Long roadmapId;
        private final int completedSteps;
        private final int totalSteps;
        private final int progressPercentage;
        private final java.time.LocalDateTime lastUpdated;

        public RoadmapProgress(Long roadmapId, int completedSteps, int totalSteps, int progressPercentage, java.time.LocalDateTime lastUpdated) {
            this.roadmapId = roadmapId;
            this.completedSteps = completedSteps;
            this.totalSteps = totalSteps;
            this.progressPercentage = progressPercentage;
            this.lastUpdated = lastUpdated;
        }

        // Getters
        public Long getRoadmapId() { return roadmapId; }
        public int getCompletedSteps() { return completedSteps; }
        public int getTotalSteps() { return totalSteps; }
        public int getProgressPercentage() { return progressPercentage; }
        public java.time.LocalDateTime getLastUpdated() { return lastUpdated; }
    }

    // DTO Conversion Methods
    private RoadmapResponseDto convertToDto(Roadmap roadmap) {
        RoadmapResponseDto dto = new RoadmapResponseDto(
            roadmap.getId(),
            roadmap.getTitle(),
            roadmap.getSummary(),
            roadmap.getEstimatedTime(),
            roadmap.getDifficulty(),
            roadmap.getProgressPercentage(),
            roadmap.getCompletedSteps(),
            roadmap.getTotalSteps(),
            roadmap.getCreatedAt(),
            roadmap.getUpdatedAt()
        );

        // Convert task to summary DTO
        if (roadmap.getTask() != null) {
            Task task = roadmap.getTask();
            TaskSummaryDto taskDto = new TaskSummaryDto(
                task.getId(),
                task.getUserInput(),
                task.getIntentType(),
                task.getCategory(),
                task.getCreatedAt()
            );
            dto.setTask(taskDto);
        }

        return dto;
    }

    private RoadmapStepDto convertToDto(RoadmapStep step) {
        return new RoadmapStepDto(
            step.getId(),
            step.getStepIndex(),
            step.getTitle(),
            step.getDescription(),
            step.getCompleted(),
            step.getNotes(),
            step.getPriority() != null ? step.getPriority().name() : "MEDIUM",
            step.getEstimatedDuration(),
            step.getCompletedAt(),
            step.getCreatedAt(),
            step.getUpdatedAt()
        );
    }
}