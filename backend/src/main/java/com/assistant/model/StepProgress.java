package com.assistant.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks completion state, priority, and notes for a single step
 * within a roadmap generation. Persists across page refreshes.
 */
@Entity
@Table(name = "step_progress",
       uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "step_index"}))
public class StepProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** Zero-based index of the step within the roadmap */
    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(name = "priority", length = 20)
    private String priority = "MEDIUM";

    @Column(name = "note", columnDefinition = "CLOB")
    private String note;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public StepProgress() {}

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId()                        { return id; }
    public void setId(Long v)                  { this.id = v; }
    public Long getTaskId()                    { return taskId; }
    public void setTaskId(Long v)              { this.taskId = v; }
    public int getStepIndex()                  { return stepIndex; }
    public void setStepIndex(int v)            { this.stepIndex = v; }
    public boolean isCompleted()               { return completed; }
    public void setCompleted(boolean v)        { this.completed = v; }
    public String getPriority()                { return priority; }
    public void setPriority(String v)          { this.priority = v; }
    public String getNote()                    { return note; }
    public void setNote(String v)              { this.note = v; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)  { this.updatedAt = v; }
}
