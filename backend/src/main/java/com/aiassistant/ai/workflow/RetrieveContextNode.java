package com.aiassistant.ai.workflow;

import com.aiassistant.retrieval.RepositoryRetrievalService;

public class RetrieveContextNode implements RepositoryChatNode {
    private final RepositoryRetrievalService retrievalService;
    private final QueryCondensationService queryCondensationService;

    public RetrieveContextNode(RepositoryRetrievalService retrievalService) {
        this(retrievalService, new QueryCondensationService());
    }

    public RetrieveContextNode(RepositoryRetrievalService retrievalService,
                               QueryCondensationService queryCondensationService) {
        this.retrievalService = retrievalService;
        this.queryCondensationService = queryCondensationService;
    }

    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        RepositoryRetrievalService.RepositoryRetrievalQuery rawQuery = new RepositoryRetrievalService.RepositoryRetrievalQuery(
                state.workspaceId(),
                state.conversationId(),
                state.userMessage());
        var rawResult = retrievalService.retrieve(rawQuery);
        String retrievalQuestion = state.userMessage();
        var selectedResult = rawResult;
        if (!ContextValidationNode.isGoodEnough(rawResult.chunks())
                && queryCondensationService.shouldAttemptCondensation(state.userMessage(), rawResult, state.retrievalContext())) {
            retrievalQuestion = queryCondensationService.condense(state.userMessage(), state.retrievalContext());
            selectedResult = retrievalService.retrieve(new RepositoryRetrievalService.RepositoryRetrievalQuery(
                    state.workspaceId(),
                    state.conversationId(),
                    retrievalQuestion));
        }
        return state.withRetrievalQuery(retrievalQuestion)
                .withRetrievalResult(selectedResult)
                .mark(step());
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.RETRIEVE_CONTEXT;
    }
}
