package com.aiassistant.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.openrouter.chat")
public record OpenRouterChatProperties(
        String baseUrl,
        String apiKey,
        String model,
        int maxTokens,
        Duration timeout,
        int maxRetries
) {
    public String missingApiKeyMessage() {
        return "OpenRouter API key is missing.";
    }
}
