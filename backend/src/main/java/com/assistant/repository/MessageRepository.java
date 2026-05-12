package com.assistant.repository;

import com.assistant.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /** Returns the last N messages for context injection */
    List<Message> findTop10ByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
