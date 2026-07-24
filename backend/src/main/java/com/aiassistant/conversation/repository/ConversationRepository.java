package com.aiassistant.conversation.repository;

import com.aiassistant.conversation.entity.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findAllByWorkspaceIdOrderByUpdatedAtDesc(Long workspaceId);
    Optional<Conversation> findByIdAndWorkspaceId(Long id, Long workspaceId);
    Optional<Conversation> findByIdAndWorkspaceOwnerId(Long id, Long ownerId);
}
