package com.aiassistant.ai.workflow;

import com.aiassistant.ai.LlmService;

public class LlmGenerationNode implements RepositoryChatNode {
    private final LlmService llmService;

    public LlmGenerationNode(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        if (state.refused() || !state.contextValid()) {
            return state.mark(step());
        }
        if (state.prompt() == null) {
            throw new IllegalStateException("Prompt must be selected before LLM generation.");
        }
        return state.withLlmResponse(llmService.generate(state.prompt())).mark(step());
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.LLM_GENERATION;
    }
}
