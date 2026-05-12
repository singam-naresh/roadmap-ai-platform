package com.assistant.repository;

import com.assistant.model.RoadmapStep;
import com.assistant.model.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapStepRepository extends JpaRepository<RoadmapStep, Long> {

    List<RoadmapStep> findByRoadmapOrderByStepIndexAsc(Roadmap roadmap);

    Optional<RoadmapStep> findByRoadmapAndStepIndex(Roadmap roadmap, Integer stepIndex);

    @Query("SELECT rs FROM RoadmapStep rs WHERE rs.roadmap.id = :roadmapId ORDER BY rs.stepIndex ASC")
    List<RoadmapStep> findByRoadmapIdOrderByStepIndexAsc(@Param("roadmapId") Long roadmapId);

    @Query("SELECT COUNT(rs) FROM RoadmapStep rs WHERE rs.roadmap = :roadmap AND rs.completed = true")
    Long countCompletedStepsByRoadmap(@Param("roadmap") Roadmap roadmap);

    @Query("SELECT COUNT(rs) FROM RoadmapStep rs WHERE rs.roadmap = :roadmap")
    Long countTotalStepsByRoadmap(@Param("roadmap") Roadmap roadmap);

    @Query("SELECT rs FROM RoadmapStep rs WHERE rs.roadmap = :roadmap AND rs.completed = false ORDER BY rs.priority DESC, rs.stepIndex ASC")
    List<RoadmapStep> findNextStepsByRoadmap(@Param("roadmap") Roadmap roadmap);

    @Query("SELECT rs FROM RoadmapStep rs WHERE rs.roadmap = :roadmap AND rs.priority = :priority ORDER BY rs.stepIndex ASC")
    List<RoadmapStep> findByRoadmapAndPriority(@Param("roadmap") Roadmap roadmap, @Param("priority") RoadmapStep.Priority priority);
}