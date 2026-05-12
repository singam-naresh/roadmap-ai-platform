package com.assistant.dto;

import com.assistant.model.Roadmap;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoadmapResponseDto {
    private Long id;
    private String title;
    private String summary;
    private String estimatedTime;
    private String difficulty;
    private Integer progressPercentage;
    private Integer completedSteps;
    private Integer totalSteps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private TaskSummaryDto task;
    private List<RoadmapStepDto> steps;
    private UserDto user;

    // Constructors
    public RoadmapResponseDto() {}

    public RoadmapResponseDto(Long id, String title, String summary, String estimatedTime, 
                             String difficulty, Integer progressPercentage, Integer completedSteps, 
                             Integer totalSteps, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.estimatedTime = estimatedTime;
        this.difficulty = difficulty;
        this.progressPercentage = progressPercentage;
        this.completedSteps = completedSteps;
        this.totalSteps = totalSteps;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Constructor from entity - prevents Hibernate proxy serialization issues
    public RoadmapResponseDto(Roadmap roadmap) {
        this.id = roadmap.getId();
        this.title = roadmap.getTitle();
        this.summary = roadmap.getSummary();
        this.estimatedTime = roadmap.getEstimatedTime();
        this.difficulty = roadmap.getDifficulty();
        this.createdAt = roadmap.getCreatedAt();
        this.updatedAt = roadmap.getUpdatedAt();
        
        // Safely handle user relationship
        if (roadmap.getUser() != null) {
            this.user = new UserDto(roadmap.getUser());
        }
        
        // Convert steps to DTOs to avoid lazy loading issues
        if (roadmap.getSteps() != null) {
            this.steps = roadmap.getSteps().stream()
                    .map(RoadmapStepDto::new)
                    .collect(Collectors.toList());
            this.totalSteps = this.steps.size();
            this.completedSteps = (int) this.steps.stream()
                    .mapToInt(step -> step.getCompleted() != null && step.getCompleted() ? 1 : 0)
                    .sum();
            this.progressPercentage = this.totalSteps > 0 ? 
                    (this.completedSteps * 100) / this.totalSteps : 0;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public Integer getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Integer progressPercentage) { this.progressPercentage = progressPercentage; }

    public Integer getCompletedSteps() { return completedSteps; }
    public void setCompletedSteps(Integer completedSteps) { this.completedSteps = completedSteps; }

    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public TaskSummaryDto getTask() { return task; }
    public void setTask(TaskSummaryDto task) { this.task = task; }

    public List<RoadmapStepDto> getSteps() { return steps; }
    public void setSteps(List<RoadmapStepDto> steps) { this.steps = steps; }

    public UserDto getUser() { return user; }
    public void setUser(UserDto user) { this.user = user; }
}