package com.assistant.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A multi-turn conversation session.
 *
 * Stores session context so follow-up prompts preserve:
 *   - skill level detected in the first message
 *   - locked intent (ROADMAP, LEARNING, CODING, CHAT)
 *   - domain (JAVA_BACKEND, AI_ENGINEERING, etc.)
 *
 * This prevents context loss between turns — e.g. a user who said
 * "I am a complete beginner" will always get beginner content
 * for the rest of the session.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "title", length = 255)
    private String title;

    /** ACTIVE | INACTIVE */
    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    /**
     * Locked intent for this session.
     * Once set, all follow-up messages use this intent unless explicitly changed.
     * Values: ROADMAP | LEARNING | CODING | CHAT | ANALYSIS | PRODUCTIVITY
     */
    @Column(name = "locked_intent", length = 50)
    private String lockedIntent;

    /**
     * Detected skill level for this session.
     * Values: beginner | intermediate | advanced
     * Persisted so follow-up prompts don't re-detect and drift.
     */
    @Column(name = "skill_level", length = 30)
    private String skillLevel;

    /**
     * Primary domain detected in this session.
     * Values: JAVA_BACKEND | REACT_FRONTEND | AI_ENGINEERING | DEVOPS | etc.
     */
    @Column(name = "domain", length = 50)
    private String domain;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<Message> messages = new ArrayList<>();

    public Conversation() {}

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.lastMessageAt == null) this.lastMessageAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId()                          { return id; }
    public void setId(Long v)                    { this.id = v; }
    public User getUser()                        { return user; }
    public void setUser(User user)               { this.user = user; }
    public String getTitle()                     { return title; }
    public void setTitle(String v)               { this.title = v; }
    public String getStatus()                    { return status; }
    public void setStatus(String v)              { this.status = v; }
    public String getLockedIntent()              { return lockedIntent; }
    public void setLockedIntent(String v)        { this.lockedIntent = v; }
    public String getSkillLevel()                { return skillLevel; }
    public void setSkillLevel(String v)          { this.skillLevel = v; }
    public String getDomain()                    { return domain; }
    public void setDomain(String v)              { this.domain = v; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime v)    { this.createdAt = v; }
    public LocalDateTime getLastMessageAt()      { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime v){ this.lastMessageAt = v; }
    public List<Message> getMessages()           { return messages; }
    public void setMessages(List<Message> v)     { this.messages = v; }
}
