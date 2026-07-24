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
    private static final String SAFE_PROVIDER_MESSAGE = "The assistant is temporarily unavailable, please try again.";

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
            throw translateProviderFailure(ex);
        }
        String content = response.getResult().getOutput().getText();
        String model = response.getMetadata() == null || response.getMetadata().getModel() == null
                ? properties.model()
                : response.getMetadata().getModel();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Integer tokenUsage = usage == null ? null : usage.getTotalTokens();
        return new LlmResponse(content, model, tokenUsage);
    }

    private ApiException translateProviderFailure(RuntimeException ex) {
        ProviderFailureCategory category = categorize(ex);
        log.warn("OpenRouter chat request failed: category={}", category, ex);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI Provider Unavailable", SAFE_PROVIDER_MESSAGE);
    }

    private ProviderFailureCategory categorize(Throwable ex) {
        String message = flattenMessages(ex).toLowerCase();
        if (message.contains("401") || message.contains("403") || message.contains("unauthorized")
                || message.contains("forbidden") || message.contains("invalid api key") || message.contains("authentication")) {
            return ProviderFailureCategory.AUTHENTICATION;
        }
        if (message.contains("429") || message.contains("rate limit") || message.contains("too many requests")) {
            return ProviderFailureCategory.RATE_LIMIT;
        }
        if (message.contains("timeout") || message.contains("timed out") || message.contains("read timed")) {
            return ProviderFailureCategory.TIMEOUT;
        }
        if (message.contains("402") || message.contains("insufficient credit") || message.contains("insufficient balance")
                || message.contains("credits")) {
            return ProviderFailureCategory.INSUFFICIENT_CREDITS;
        }
        return ProviderFailureCategory.PROVIDER_ERROR;
    }

    private String flattenMessages(Throwable ex) {
        StringBuilder builder = new StringBuilder();
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private enum ProviderFailureCategory {
        AUTHENTICATION,
        RATE_LIMIT,
        TIMEOUT,
        INSUFFICIENT_CREDITS,
        PROVIDER_ERROR
    }
}
