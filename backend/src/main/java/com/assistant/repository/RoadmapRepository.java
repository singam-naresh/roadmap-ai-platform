package com.assistant.repository;

import com.assistant.model.Roadmap;
import com.assistant.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {

    List<Roadmap> findByUserOrderByCreatedAtDesc(User user);

    Optional<Roadmap> findByTaskId(Long taskId);

    @Query("SELECT r FROM Roadmap r WHERE r.user = :user AND r.progressPercentage < 100 ORDER BY r.updatedAt DESC")
    List<Roadmap> findActiveRoadmapsByUser(@Param("user") User user);

    @Query("SELECT r FROM Roadmap r WHERE r.user = :user AND r.progressPercentage = 100 ORDER BY r.updatedAt DESC")
    List<Roadmap> findCompletedRoadmapsByUser(@Param("user") User user);

    @Query("SELECT COUNT(r) FROM Roadmap r WHERE r.user = :user")
    Long countByUser(@Param("user") User user);

    @Query("SELECT COUNT(r) FROM Roadmap r WHERE r.user = :user AND r.progressPercentage = 100")
    Long countCompletedByUser(@Param("user") User user);

    @Query("SELECT AVG(r.progressPercentage) FROM Roadmap r WHERE r.user = :user")
    Double getAverageProgressByUser(@Param("user") User user);
}