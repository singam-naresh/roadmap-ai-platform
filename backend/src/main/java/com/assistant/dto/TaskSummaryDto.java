package com.assistant.dto;

import java.time.LocalDateTime;

public class TaskSummaryDto {
    private Long id;
    private String userInput;
    private String intentType;
    private String category;
    private LocalDateTime createdAt;

    // Constructors
    public TaskSummaryDto() {}

    public TaskSummaryDto(Long id, String userInput, String intentType, String category, LocalDateTime createdAt) {
        this.id = id;
        this.userInput = userInput;
        this.intentType = intentType;
        this.category = category;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public String getIntentType() { return intentType; }
    public void setIntentType(String intentType) { this.intentType = intentType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}