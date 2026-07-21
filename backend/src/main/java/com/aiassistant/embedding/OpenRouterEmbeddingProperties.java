package com.aiassistant.embedding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.openrouter")
public record OpenRouterEmbeddingProperties(
        String baseUrl,
        String apiKey,
        String embeddingModel,
        Duration timeout,
        int maxRetries,
        boolean enabled) {
    public String resolvedBaseUrl() {
        return baseUrl == null || baseUrl.isBlank() ? "https://openrouter.ai/api/v1" : baseUrl;
    }

    public String resolvedEmbeddingModel() {
        return embeddingModel == null || embeddingModel.isBlank() ? "openai/text-embedding-3-small" : embeddingModel;
    }

    public Duration resolvedTimeout() {
        return timeout == null ? Duration.ofSeconds(30) : timeout;
    }
}
