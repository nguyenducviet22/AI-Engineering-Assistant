package com.aiassistant.ai;

import com.aiassistant.ai.workflow.*;
import com.aiassistant.conversation.dto.ConversationDtos.ChatRequest;
import com.aiassistant.conversation.dto.ConversationDtos.ChatResponse;
import com.aiassistant.conversation.service.ConversationService;
import com.aiassistant.retrieval.RepositoryRetrievalService;
import com.aiassistant.workspace.service.WorkspaceService;
import org.springframework.stereotype.Service;

@Service
public class RepositoryChatService {
    private final WorkspaceService workspaceService;
    private final ConversationService conversationService;
    private final RepositoryRetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final LlmService llmService;

    public RepositoryChatService(WorkspaceService workspaceService,
                                 ConversationService conversationService,
                                 RepositoryRetrievalService retrievalService,
                                 PromptBuilder promptBuilder,
                                 LlmService llmService) {
        this.workspaceService = workspaceService;
        this.conversationService = conversationService;
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.llmService = llmService;
    }

    public ChatResponse chat(Long ownerId, Long workspaceId, ChatRequest request) {
        workspaceService.requireOwned(ownerId, workspaceId);
        if (request.conversationId() != null) {
            conversationService.requireOwnedConversation(ownerId, request.conversationId(), workspaceId);
        }
        RepositoryChatWorkflow workflow = new RepositoryChatWorkflow(
                new IntentDetectionNode(),
                new RetrieveContextNode(retrievalService),
                new ContextValidationNode(),
                new PromptSelectionNode(promptBuilder),
                new LlmGenerationNode(llmService),
                new OutputValidationNode(),
                new CitationMappingNode(),
                new PersistConversationNode(conversationService)
        );
        RepositoryChatState result = workflow.run(
                workspaceId,
                request.conversationId(),
                request.message(),
                conversationService.historyForPrompt(ownerId, request.conversationId())
        );
        return new ChatResponse(
                result.conversationId(),
                result.assistantMessageId(),
                result.answer(),
                result.citations(),
                result.refused(),
                result.llmResponse() == null ? null : result.llmResponse().model(),
                result.llmResponse() == null ? null : result.llmResponse().tokenUsage()
        );
    }
}
