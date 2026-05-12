package com.assistant.repository;

import com.assistant.model.Task;
import com.assistant.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ── RAG context lookup ────────────────────────────────────────────────────
    List<Task> findTop3ByUserInputContainingIgnoreCaseOrderByCreatedAtDesc(String keyword);
    List<Task> findTop3ByUserAndUserInputContainingIgnoreCaseOrderByCreatedAtDesc(User user, String keyword);

    // ── Full history (no pagination) ──────────────────────────────────────────
    List<Task> findAllByOrderByCreatedAtDesc();
    List<Task> findAllByUserOrderByCreatedAtDesc(User user);
    List<Task> findByUserOrderByCreatedAtDesc(User user);

    // ── Recent tasks for deduplication ────────────────────────────────────────
    List<Task> findTop5ByOrderByCreatedAtDesc();
    List<Task> findTop5ByUserOrderByCreatedAtDesc(User user);

    // ── Paginated history ─────────────────────────────────────────────────────
    Page<Task> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ── Search by userInput ───────────────────────────────────────────────────
    @Query("SELECT t FROM Task t WHERE LOWER(t.userInput) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY t.createdAt DESC")
    Page<Task> searchByUserInput(@Param("query") String query, Pageable pageable);

    // ── Filter by intent type ─────────────────────────────────────────────────
    Page<Task> findByIntentTypeOrderByCreatedAtDesc(String intentType, Pageable pageable);

    // ── Filter by category ────────────────────────────────────────────────────
    Page<Task> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    // ── Combined search + filter ──────────────────────────────────────────────
    @Query("SELECT t FROM Task t WHERE " +
           "(:query IS NULL OR LOWER(t.userInput) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:intentType IS NULL OR t.intentType = :intentType) AND " +
           "(:category IS NULL OR t.category = :category) " +
           "ORDER BY t.createdAt DESC")
    Page<Task> findWithFilters(
            @Param("query")      String query,
            @Param("intentType") String intentType,
            @Param("category")   String category,
            Pageable pageable
    );

    // ── Analytics helpers ─────────────────────────────────────────────────────
    long countByIntentType(String intentType);
    long countByCategory(String category);
}
