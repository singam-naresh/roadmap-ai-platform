package com.assistant.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_input", columnDefinition = "CLOB")
    private String userInput;

    @Column(name = "ai_output", columnDefinition = "CLOB")
    private String aiOutput;

    @Column(name = "mode", length = 50)
    private String mode;

    // Stored at write time — never re-computed from userInput on read
    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "skill_level", length = 50)
    private String skillLevel;

    @Column(name = "intent_type", length = 50)
    private String intentType;

    // ── Prompt versioning (Task 6) ────────────────────────────────────────────
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "schema_version", length = 20)
    private String schemaVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Required by JPA
    public Task() {}

    private Task(Builder builder) {
        this.id         = builder.id;
        this.user       = builder.user;
        this.userInput  = builder.userInput;
        this.aiOutput   = builder.aiOutput;
        this.mode       = builder.mode;
        this.category   = builder.category;
        this.skillLevel = builder.skillLevel;
        this.intentType = builder.intentType;
        this.modelName  = builder.modelName;
        this.promptVersion = builder.promptVersion;
        this.schemaVersion = builder.schemaVersion;
        this.createdAt  = builder.createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public User getUser()                      { return user; }
    public void setUser(User user)             { this.user = user; }

    public String getUserInput()               { return userInput; }
    public void setUserInput(String v)         { this.userInput = v; }

    public String getAiOutput()                { return aiOutput; }
    public void setAiOutput(String v)          { this.aiOutput = v; }

    public String getMode()                    { return mode; }
    public void setMode(String v)              { this.mode = v; }

    public String getCategory()                { return category; }
    public void setCategory(String v)          { this.category = v; }

    public String getSkillLevel()              { return skillLevel; }
    public void setSkillLevel(String v)        { this.skillLevel = v; }

    public String getIntentType()              { return intentType; }
    public void setIntentType(String v)        { this.intentType = v; }

    public String getModelName()               { return modelName; }
    public void setModelName(String v)         { this.modelName = v; }

    public String getPromptVersion()           { return promptVersion; }
    public void setPromptVersion(String v)     { this.promptVersion = v; }

    public String getSchemaVersion()           { return schemaVersion; }
    public void setSchemaVersion(String v)     { this.schemaVersion = v; }

    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long          id;
        private User          user;
        private String        userInput;
        private String        aiOutput;
        private String        mode;
        private String        category;
        private String        skillLevel;
        private String        intentType;
        private String        modelName;
        private String        promptVersion;
        private String        schemaVersion;
        private LocalDateTime createdAt;

        public Builder id(Long id)                   { this.id = id;               return this; }
        public Builder user(User user)                { this.user = user;           return this; }
        public Builder userInput(String v)           { this.userInput = v;         return this; }
        public Builder aiOutput(String v)            { this.aiOutput = v;          return this; }
        public Builder mode(String v)                { this.mode = v;              return this; }
        public Builder category(String v)            { this.category = v;          return this; }
        public Builder skillLevel(String v)          { this.skillLevel = v;        return this; }
        public Builder intentType(String v)          { this.intentType = v;        return this; }
        public Builder modelName(String v)           { this.modelName = v;         return this; }
        public Builder promptVersion(String v)       { this.promptVersion = v;     return this; }
        public Builder schemaVersion(String v)       { this.schemaVersion = v;     return this; }
        public Builder createdAt(LocalDateTime v)    { this.createdAt = v;         return this; }

        public Task build() { return new Task(this); }
    }
}
