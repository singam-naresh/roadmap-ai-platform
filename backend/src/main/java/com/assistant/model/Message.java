package com.assistant.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A single message within a Conversation.
 * role: "user" | "assistant"
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /** "user" or "assistant" */
    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "content", columnDefinition = "CLOB", nullable = false)
    private String content;

    /** intentType of the AI response for this message (null for user messages) */
    @Column(name = "intent_type", length = 50)
    private String intentType;

    /** ID of the Task record generated for this message (null for user messages) */
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Message() {}

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId()                          { return id; }
    public void setId(Long v)                    { this.id = v; }
    public Conversation getConversation()        { return conversation; }
    public void setConversation(Conversation v)  { this.conversation = v; }
    public String getRole()                      { return role; }
    public void setRole(String v)                { this.role = v; }
    public String getContent()                   { return content; }
    public void setContent(String v)             { this.content = v; }
    public String getIntentType()                { return intentType; }
    public void setIntentType(String v)          { this.intentType = v; }
    public Long getTaskId()                      { return taskId; }
    public void setTaskId(Long v)                { this.taskId = v; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime v)    { this.createdAt = v; }
}
