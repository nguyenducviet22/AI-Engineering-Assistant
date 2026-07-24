package com.aiassistant.ai.workflow;

import com.aiassistant.ai.PromptBuilder;
import com.aiassistant.ai.PromptBuilder.RepositoryChatPromptRequest;
import com.aiassistant.ai.PromptBuilder.RetrievedContextBlock;

public class PromptSelectionNode implements RepositoryChatNode {
    private final PromptBuilder promptBuilder;

    public PromptSelectionNode(PromptBuilder promptBuilder) {
        this.promptBuilder = promptBuilder;
    }

    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        if (state.refused()) {
            return state.mark(step());
        }
        RepositoryChatPromptRequest request = new RepositoryChatPromptRequest(
                state.userMessage(),
                state.retrievalResult().chunks().stream()
                        .map(chunk -> new RetrievedContextBlock(
                                chunk.chunkId(),
                                chunk.chunkId(),
                                chunk.filePath(),
                                chunk.startLine(),
                                chunk.endLine(),
                                chunk.symbol(),
                                chunk.content(),
                                chunk.score()))
                        .toList(),
                state.conversationHistory());
        return state.withPrompt(promptBuilder.buildRepositoryChatPrompt(request).prompt()).mark(step());
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.PROMPT_SELECTION;
    }
}
