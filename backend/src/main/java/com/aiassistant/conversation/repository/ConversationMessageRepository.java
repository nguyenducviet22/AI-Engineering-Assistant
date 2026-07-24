package com.aiassistant.conversation.repository;

import com.aiassistant.conversation.entity.ConversationMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findAllByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
