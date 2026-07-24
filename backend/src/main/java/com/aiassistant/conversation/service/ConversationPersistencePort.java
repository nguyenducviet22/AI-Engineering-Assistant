package com.aiassistant.conversation.service;

import com.aiassistant.ai.workflow.RepositoryChatState;

public interface ConversationPersistencePort {
    PersistedConversationTurn persist(RepositoryChatState state);

    static ConversationPersistencePort noop() {
        return state -> new PersistedConversationTurn(state.conversationId(), null);
    }

    record PersistedConversationTurn(Long conversationId, Long assistantMessageId) {
    }
}
