package com.aiassistant.ai.workflow;

import com.aiassistant.ai.PromptBuilder;
import com.aiassistant.conversation.service.ConversationRetrievalContext;
import java.util.List;
import java.util.Optional;

public class RepositoryChatWorkflow {
    private final List<RepositoryChatNode> nodes;

    public RepositoryChatWorkflow(IntentDetectionNode intentDetectionNode,
                                  RetrieveContextNode retrieveContextNode,
                                  ContextValidationNode contextValidationNode,
                                  PromptSelectionNode promptSelectionNode,
                                  LlmGenerationNode llmGenerationNode,
                                  OutputValidationNode outputValidationNode,
                                  CitationMappingNode citationMappingNode,
                                  PersistConversationNode persistConversationNode) {
        this.nodes = List.of(
                intentDetectionNode,
                retrieveContextNode,
                contextValidationNode,
                promptSelectionNode,
                llmGenerationNode,
                outputValidationNode,
                citationMappingNode,
                persistConversationNode
        );
    }

    public RepositoryChatState run(Long workspaceId,
                                   Long conversationId,
                                   String userMessage,
                                   List<PromptBuilder.ConversationTurn> history,
                                   Optional<ConversationRetrievalContext> retrievalContext) {
        RepositoryChatState state = RepositoryChatState.start(workspaceId, conversationId, userMessage, history, retrievalContext);
        for (RepositoryChatNode node : nodes) {
            state = node.apply(state);
        }
        return state.mark(WorkflowStep.END);
    }

    public RepositoryChatState run(Long workspaceId,
                                   Long conversationId,
                                   String userMessage,
                                   List<PromptBuilder.ConversationTurn> history) {
        return run(workspaceId, conversationId, userMessage, history, Optional.empty());
    }
}
