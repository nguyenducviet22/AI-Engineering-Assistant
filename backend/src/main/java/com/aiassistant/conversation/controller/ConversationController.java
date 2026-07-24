package com.aiassistant.conversation.controller;

import com.aiassistant.ai.RepositoryChatService;
import com.aiassistant.common.SecurityUtils;
import com.aiassistant.conversation.dto.ConversationDtos.*;
import com.aiassistant.conversation.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ConversationController {
    private final ConversationService conversationService;
    private final RepositoryChatService repositoryChatService;

    public ConversationController(ConversationService conversationService, RepositoryChatService repositoryChatService) {
        this.conversationService = conversationService;
        this.repositoryChatService = repositoryChatService;
    }

    @GetMapping("/workspaces/{id}/conversations")
    List<ConversationSummaryResponse> list(@PathVariable("id") Long workspaceId) {
        return conversationService.list(SecurityUtils.currentUser().id(), workspaceId);
    }

    @PostMapping("/workspaces/{id}/chat")
    ChatResponse chat(@PathVariable("id") Long workspaceId, @Valid @RequestBody ChatRequest request) {
        return repositoryChatService.chat(SecurityUtils.currentUser().id(), workspaceId, request);
    }

    @GetMapping("/conversations/{id}/messages")
    ConversationMessagesResponse messages(@PathVariable("id") Long conversationId) {
        return conversationService.messages(SecurityUtils.currentUser().id(), conversationId);
    }
}
