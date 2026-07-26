package com.aiassistant.ai.workflow;

import com.aiassistant.ai.LlmService;
import com.aiassistant.ai.PromptBuilder;
import com.aiassistant.conversation.service.ConversationRetrievalContext;
import com.aiassistant.retrieval.Citation;
import com.aiassistant.retrieval.RetrievalResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        Optional<ConversationRetrievalContext> retrievalContext,
        String retrievalQuery,
        List<WorkflowStep> executionTrace
) {
    public static final String INSUFFICIENT_CONTEXT_MESSAGE =
            "I could not find sufficient repository context to answer that accurately.";

    public static RepositoryChatState start(Long workspaceId,
                                            Long conversationId,
                                            String userMessage,
                                            List<PromptBuilder.ConversationTurn> history,
                                            Optional<ConversationRetrievalContext> retrievalContext) {
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
                retrievalContext == null ? Optional.empty() : retrievalContext,
                userMessage,
                List.of(WorkflowStep.START)
        );
    }

    public static RepositoryChatState start(Long workspaceId,
                                            Long conversationId,
                                            String userMessage,
                                            List<PromptBuilder.ConversationTurn> history) {
        return start(workspaceId, conversationId, userMessage, history, Optional.empty());
    }

    public RepositoryChatState mark(WorkflowStep step) {
        List<WorkflowStep> next = new ArrayList<>(executionTrace);
        next.add(step);
        return withExecutionTrace(next);
    }

    public RepositoryChatState withIntent(RepositoryChatIntent value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, value, retrievalResult, contextValid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, retrievalContext, retrievalQuery, executionTrace);
    }

    public RepositoryChatState withRetrievalResult(RetrievalResult value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, value, contextValid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, retrievalContext, retrievalQuery, executionTrace);
    }

    public RepositoryChatState withRetrievalQuery(String value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, retrievalContext, value, executionTrace);
    }

    public RepositoryChatState withContextValidation(boolean valid) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, valid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, retrievalContext, retrievalQuery, executionTrace);
    }

    public RepositoryChatState withRefusal(String refusalAnswer) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, false,
                true, refusalAnswer, prompt, llmResponse, List.of(), conversationHistory, retrievalContext, retrievalQuery, executionTrace);
    }

    public RepositoryChatState withPrompt(Prompt value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, answer, value, llmResponse, citations, conversationHistory, retrievalContext, retrievalQuery, executionTrace);
    }

    public RepositoryChatState withLlmResponse(LlmService.LlmResponse value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, value == null ? answer : value.content(), prompt, value, citations, conversationHistory, retrievalContext,
                retrievalQuery, executionTrace);
    }

    public RepositoryChatState withAnswer(String value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, value, prompt, llmResponse, citations, conversationHistory, retrievalContext, retrievalQuery, executionTrace);
    }

    public RepositoryChatState withCitations(List<Citation> value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, answer, prompt, llmResponse, value == null ? List.of() : List.copyOf(value),
                conversationHistory, retrievalContext, retrievalQuery, executionTrace);
    }

    public RepositoryChatState withPersistence(Long persistedConversationId, Long persistedAssistantMessageId) {
        return new RepositoryChatState(workspaceId, persistedConversationId, persistedAssistantMessageId, userMessage, intent,
                retrievalResult, contextValid, refused, answer, prompt, llmResponse, citations, conversationHistory, retrievalContext,
                retrievalQuery, executionTrace);
    }

    private RepositoryChatState withExecutionTrace(List<WorkflowStep> value) {
        return new RepositoryChatState(workspaceId, conversationId, assistantMessageId, userMessage, intent, retrievalResult, contextValid,
                refused, answer, prompt, llmResponse, citations, conversationHistory, retrievalContext, retrievalQuery, List.copyOf(value));
    }
}
