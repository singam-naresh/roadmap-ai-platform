package com.assistant.dto;

import com.assistant.model.RoadmapStep;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoadmapStepDto {
    private Long id;
    private Integer stepIndex;
    private String title;
    private String description;
    private Boolean completed;
    private String notes;
    private String priority;
    private String estimatedDuration;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public RoadmapStepDto() {}

    public RoadmapStepDto(Long id, Integer stepIndex, String title, String description, 
                         Boolean completed, String notes, String priority, String estimatedDuration,
                         LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.stepIndex = stepIndex;
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.notes = notes;
        this.priority = priority;
        this.estimatedDuration = estimatedDuration;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Constructor from entity - prevents Hibernate proxy serialization issues
    public RoadmapStepDto(RoadmapStep step) {
        this.id = step.getId();
        this.stepIndex = step.getStepIndex();
        this.title = step.getTitle();
        this.description = step.getDescription();
        this.completed = step.getCompleted();
        this.notes = step.getNotes();
        this.priority = step.getPriority() != null ? step.getPriority().name() : "MEDIUM";
        this.estimatedDuration = step.getEstimatedDuration();
        this.completedAt = step.getCompletedAt();
        this.createdAt = step.getCreatedAt();
        this.updatedAt = step.getUpdatedAt();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(String estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}