package com.aiassistant.conversation.dto;

import com.aiassistant.retrieval.Citation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ConversationDtos {
    private ConversationDtos() {}

    public record ConversationSummaryResponse(Long id, Long workspaceId, String title, Instant createdAt, Instant updatedAt) {}

    public record ChatRequest(Long conversationId, @NotBlank @Size(max = 8000) String message) {}

    public record ChatResponse(
            Long conversationId,
            Long assistantMessageId,
            String answer,
            List<Citation> citations,
            boolean refused,
            String model,
            Integer tokenUsage
    ) {}

    public record ConversationMessagesResponse(Long conversationId, List<MessageResponse> messages) {}

    public record MessageResponse(
            Long id,
            String role,
            String content,
            List<Citation> citations,
            Instant createdAt
    ) {}
}
