package com.aiassistant.conversation.service;

import com.aiassistant.retrieval.Citation;
import java.util.List;

public record ConversationRetrievalContext(
        String question,
        String answer,
        List<Citation> citations
) {
    public ConversationRetrievalContext {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
