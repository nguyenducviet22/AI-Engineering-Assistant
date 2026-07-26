package com.aiassistant.ai;

import com.aiassistant.exception.ApiException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmService implements LlmService {
    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmService.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final OpenRouterChatProperties properties;

    public SpringAiLlmService(ObjectProvider<ChatModel> chatModelProvider, OpenRouterChatProperties properties) {
        this.chatModelProvider = chatModelProvider;
        this.properties = properties;
    }

    @Override
    public LlmResponse generate(Prompt prompt) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI Provider Unavailable", properties.missingApiKeyMessage());
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI Provider Unavailable", "Spring AI chat model is not configured.");
        }
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (RuntimeException ex) {
            throw AiProviderFailureTranslator.unavailable("chat", ex, log);
        }
        String content = response.getResult().getOutput().getText();
        String model = response.getMetadata() == null || response.getMetadata().getModel() == null
                ? properties.model()
                : response.getMetadata().getModel();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Integer tokenUsage = usage == null ? null : usage.getTotalTokens();
        return new LlmResponse(content, model, tokenUsage);
    }

}
