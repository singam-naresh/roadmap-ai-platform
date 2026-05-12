package com.assistant.service;

import com.assistant.model.StepProgress;
import com.assistant.repository.StepProgressRepository;
import com.assistant.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages persistent step-level execution tracking for roadmap generations.
 * All state survives page refreshes and backend restarts.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final StepProgressRepository stepProgressRepository;
    private final TaskRepository         taskRepository;

    public ExecutionService(StepProgressRepository stepProgressRepository,
                             TaskRepository taskRepository) {
        this.stepProgressRepository = stepProgressRepository;
        this.taskRepository         = taskRepository;
    }

    /**
     * Returns all step progress records for a task, keyed by stepIndex.
     */
    public Map<Integer, StepProgress> getProgress(Long taskId) {
        return stepProgressRepository.findByTaskIdOrderByStepIndexAsc(taskId)
                .stream()
                .collect(Collectors.toMap(StepProgress::getStepIndex, s -> s));
    }

    /**
     * Marks a step as complete. Creates the record if it doesn't exist.
     * Returns the updated overall completion percentage.
     */
    @Transactional
    public int markComplete(Long taskId, int stepIndex, int totalSteps) {
        StepProgress sp = getOrCreate(taskId, stepIndex);
        sp.setCompleted(true);
        stepProgressRepository.save(sp);
        log.info("[exec] Step {} of task {} marked complete", stepIndex, taskId);
        return computeProgress(taskId, totalSteps);
    }

    /**
     * Marks a step as incomplete.
     */
    @Transactional
    public int markIncomplete(Long taskId, int stepIndex, int totalSteps) {
        StepProgress sp = getOrCreate(taskId, stepIndex);
        sp.setCompleted(false);
        stepProgressRepository.save(sp);
        return computeProgress(taskId, totalSteps);
    }

    /**
     * Sets the priority for a step.
     */
    @Transactional
    public void setPriority(Long taskId, int stepIndex, String priority) {
        StepProgress sp = getOrCreate(taskId, stepIndex);
        sp.setPriority(priority.toUpperCase());
        stepProgressRepository.save(sp);
    }

    /**
     * Saves a note for a step.
     */
    @Transactional
    public void setNote(Long taskId, int stepIndex, String note) {
        StepProgress sp = getOrCreate(taskId, stepIndex);
        sp.setNote(note);
        stepProgressRepository.save(sp);
    }

    /**
     * Computes overall completion percentage for a task.
     */
    public int computeProgress(Long taskId, int totalSteps) {
        if (totalSteps == 0) return 0;
        long completed = stepProgressRepository.countCompletedByTaskId(taskId);
        return (int) Math.round((completed * 100.0) / totalSteps);
    }

    // =========================================================================

    private StepProgress getOrCreate(Long taskId, int stepIndex) {
        return stepProgressRepository.findByTaskIdAndStepIndex(taskId, stepIndex)
                .orElseGet(() -> {
                    StepProgress sp = new StepProgress();
                    sp.setTaskId(taskId);
                    sp.setStepIndex(stepIndex);
                    return sp;
                });
    }
}
