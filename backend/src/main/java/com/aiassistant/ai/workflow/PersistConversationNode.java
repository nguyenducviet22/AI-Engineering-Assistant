package com.aiassistant.ai.workflow;

import com.aiassistant.conversation.service.ConversationPersistencePort;
import com.aiassistant.conversation.service.ConversationPersistencePort.PersistedConversationTurn;

public class PersistConversationNode implements RepositoryChatNode {
    private final ConversationPersistencePort persistence;

    public PersistConversationNode() {
        this(ConversationPersistencePort.noop());
    }

    public PersistConversationNode(ConversationPersistencePort persistence) {
        this.persistence = persistence;
    }

    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        PersistedConversationTurn persisted = persistence.persist(state);
        return state.withPersistence(persisted.conversationId(), persisted.assistantMessageId()).mark(step());
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.PERSIST_CONVERSATION;
    }
}
