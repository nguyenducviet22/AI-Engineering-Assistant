package com.aiassistant.ai.workflow;

public class OutputValidationNode implements RepositoryChatNode {
    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        if (state.refused()) {
            return state.mark(step());
        }
        if (state.answer() == null || state.answer().isBlank()) {
            throw new IllegalStateException("LLM generation returned an empty answer.");
        }
        return state.mark(step());
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.OUTPUT_VALIDATION;
    }
}
