package com.aiassistant.conversation.service;

import com.aiassistant.ai.workflow.RepositoryChatState;
import com.aiassistant.ai.PromptBuilder.ConversationTurn;
import com.aiassistant.conversation.dto.ConversationDtos.ConversationMessagesResponse;
import com.aiassistant.conversation.dto.ConversationDtos.ConversationSummaryResponse;
import com.aiassistant.conversation.dto.ConversationDtos.MessageResponse;
import com.aiassistant.conversation.entity.Conversation;
import com.aiassistant.conversation.entity.ConversationMessage;
import com.aiassistant.conversation.entity.MessageRole;
import com.aiassistant.conversation.repository.ConversationMessageRepository;
import com.aiassistant.conversation.repository.ConversationRepository;
import com.aiassistant.exception.ApiException;
import com.aiassistant.workspace.entity.Workspace;
import com.aiassistant.workspace.service.WorkspaceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService implements ConversationPersistencePort {
    private static final int MAX_TITLE_LENGTH = 80;

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public ConversationService(ConversationRepository conversations,
                               ConversationMessageRepository messages,
                               WorkspaceService workspaceService,
                               ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    public List<ConversationSummaryResponse> list(Long ownerId, Long workspaceId) {
        workspaceService.requireOwned(ownerId, workspaceId);
        return conversations.findAllByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
                .map(conversation -> new ConversationSummaryResponse(
                        conversation.getId(),
                        conversation.getWorkspace().getId(),
                        conversation.getTitle(),
                        conversation.getCreatedAt(),
                        conversation.getUpdatedAt()))
                .toList();
    }

    public ConversationMessagesResponse messages(Long ownerId, Long conversationId) {
        Conversation conversation = requireOwnedConversation(ownerId, conversationId);
        List<MessageResponse> responses = messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(message -> new MessageResponse(
                        message.getId(),
                        message.getRole().name(),
                        message.getContent(),
                        readCitations(message.getCitationsJson()),
                        message.getCreatedAt()))
                .toList();
        return new ConversationMessagesResponse(conversation.getId(), responses);
    }

    public List<ConversationTurn> historyForPrompt(Long ownerId, Long conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        requireOwnedConversation(ownerId, conversationId);
        return messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(message -> message.getRole() == MessageRole.USER || message.getRole() == MessageRole.ASSISTANT)
                .map(message -> new ConversationTurn(message.getRole().name(), message.getContent()))
                .toList();
    }

    public Conversation requireOwnedConversation(Long ownerId, Long conversationId, Long workspaceId) {
        Conversation conversation = requireOwnedConversation(ownerId, conversationId);
        if (!conversation.getWorkspace().getId().equals(workspaceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Conversation Not Found", "Conversation was not found.");
        }
        return conversation;
    }

    public Conversation requireOwnedConversation(Long ownerId, Long conversationId) {
        return conversations.findByIdAndWorkspaceOwnerId(conversationId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation Not Found", "Conversation was not found."));
    }

    @Override
    @Transactional
    public PersistedConversationTurn persist(RepositoryChatState state) {
        Conversation conversation = resolveConversation(state);
        messages.save(new ConversationMessage(
                conversation,
                MessageRole.USER,
                state.userMessage(),
                null,
                null,
                null,
                null,
                null
        ));
        ConversationMessage assistant = messages.save(new ConversationMessage(
                conversation,
                MessageRole.ASSISTANT,
                state.answer(),
                writeJson(state.citations()),
                writeJson(state.retrievalResult().chunks()),
                state.llmResponse() == null ? null : state.llmResponse().model(),
                state.llmResponse() == null ? null : state.llmResponse().tokenUsage(),
                null
        ));
        conversation.touch();
        return new PersistedConversationTurn(conversation.getId(), assistant.getId());
    }

    private Conversation resolveConversation(RepositoryChatState state) {
        if (state.conversationId() != null) {
            return conversations.findByIdAndWorkspaceId(state.conversationId(), state.workspaceId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation Not Found", "Conversation was not found."));
        }
        Workspace workspace = workspaceService.requireExisting(state.workspaceId());
        return conversations.save(new Conversation(workspace, titleFrom(state.userMessage())));
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) {
            return "Repository chat";
        }
        String trimmed = message.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= MAX_TITLE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_TITLE_LENGTH - 3) + "...";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Conversation Persistence Failed", "Unable to serialize conversation metadata.");
        }
    }

    private List<com.aiassistant.retrieval.Citation> readCitations(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Conversation Read Failed", "Unable to deserialize conversation citations.");
        }
    }
}
