package com.assistant.repository;

import com.assistant.model.StepProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StepProgressRepository extends JpaRepository<StepProgress, Long> {

    List<StepProgress> findByTaskIdOrderByStepIndexAsc(Long taskId);

    Optional<StepProgress> findByTaskIdAndStepIndex(Long taskId, int stepIndex);

    @Query("SELECT COUNT(s) FROM StepProgress s WHERE s.taskId = :taskId AND s.completed = true")
    long countCompletedByTaskId(@Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);
}
