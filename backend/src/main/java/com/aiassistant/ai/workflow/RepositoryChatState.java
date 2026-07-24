package com.aiassistant.ai.workflow;

import com.aiassistant.ai.LlmService;
import com.aiassistant.ai.PromptBuilder;
import com.aiassistant.retrieval.Citation;
import com.aiassistant.retrieval.RetrievalResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.prompt.Prompt;

public record RepositoryChatState(
        Long workspaceId,
        Long conversationId,
        Long assistantMessageId,
        String userMessage,
        RepositoryChatIntent intent,
        RetrievalResult retrievalResult,
        boolean contextValid,
        boolean refused,
        String answer,
        Prompt prompt,
        LlmService.LlmResponse llmResponse,
        List<Citation> citations,
        List<PromptBuilder.ConversationTurn> conversationHistory,
        List<WorkflowStep> executionTrace
) {
    public static final String INSUFFICIENT_CONTEXT_MESSAGE =
            "I could not find sufficient repository context to answer that accurately.";

    public static RepositoryChatState start(Long workspaceId,
                                            Long conversationId,
                                            String userMessage,
                                            List<PromptBuilder.ConversationTurn> history) {
        return new RepositoryChatState(
                workspaceId,
                conversationId,
                null,
                userMessage,
                RepositoryChatIntent.UNKNOWN,
                RetrievalResult.empty(),
                false,
                false,
                "",
                null,
                null,
                List.of(),
                history == null ? List.of() : List.copyOf(history),
                List.of(WorkflowStep.START)
        );
    }

    public RepositoryChatState mark(WorkflowStep step) {
        List<WorkflowStep> next = new ArrayList<>(executionTrace);
        next.add(step);
        return withExecutionTrace(next);
    }

    public RepositoryChatState withIntent(RepositoryChatIntent value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, value, retrievalResult, contextValid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, executionTrace);
    }

    public RepositoryChatState withRetrievalResult(RetrievalResult value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, value, contextValid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, executionTrace);
    }

    public RepositoryChatState withContextValidation(boolean valid) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, valid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, executionTrace);
    }

    public RepositoryChatState withRefusal(String refusalAnswer) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, false,
                true, refusalAnswer, prompt, llmResponse, List.of(), conversationHistory, executionTrace);
    }

    public RepositoryChatState withPrompt(Prompt value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, answer, value, llmResponse, citations, conversationHistory, executionTrace);
    }

    public RepositoryChatState withLlmResponse(LlmService.LlmResponse value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, value == null ? answer : value.content(), prompt, value, citations, conversationHistory, executionTrace);
    }

    public RepositoryChatState withAnswer(String value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, value, prompt, llmResponse, citations, conversationHistory, executionTrace);
    }

    public RepositoryChatState withCitations(List<Citation> value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, answer, prompt, llmResponse, value == null ? List.of() : List.copyOf(value),
                conversationHistory, executionTrace);
    }

    public RepositoryChatState withPersistence(Long persistedConversationId, Long persistedAssistantMessageId) {
        return new RepositoryChatState(workspaceId, persistedConversationId, persistedAssistantMessageId, userMessage, intent,
                retrievalResult, contextValid, refused, answer, prompt, llmResponse, citations, conversationHistory, executionTrace);
    }

    private RepositoryChatState withExecutionTrace(List<WorkflowStep> value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, List.copyOf(value));
    }
}
