package com.assistant.repository;

import com.assistant.model.Conversation;
import com.assistant.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findAllByOrderByLastMessageAtDesc();
    List<Conversation> findByStatusOrderByLastMessageAtDesc(String status);
    List<Conversation> findByUserOrderByLastMessageAtDesc(User user);
}
