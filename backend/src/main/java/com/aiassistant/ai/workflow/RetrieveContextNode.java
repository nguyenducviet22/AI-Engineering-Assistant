package com.aiassistant.ai.workflow;

import com.aiassistant.retrieval.RepositoryRetrievalService;

public class RetrieveContextNode implements RepositoryChatNode {
    private final RepositoryRetrievalService retrievalService;

    public RetrieveContextNode(RepositoryRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        RepositoryRetrievalService.RepositoryRetrievalQuery query =
                new RepositoryRetrievalService.RepositoryRetrievalQuery(
                        state.workspaceId(),
                        state.conversationId(),
                        state.userMessage());
        return state.withRetrievalResult(retrievalService.retrieve(query)).mark(step());
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.RETRIEVE_CONTEXT;
    }
}
