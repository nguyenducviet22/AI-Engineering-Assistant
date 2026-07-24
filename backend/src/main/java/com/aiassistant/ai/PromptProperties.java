package com.aiassistant.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.prompt")
public record PromptProperties(
        int maxPromptCharacters,
        int maxContextCharacters,
        int maxHistoryCharacters,
        int maxHistoryMessages
) {
    public int resolvedMaxPromptCharacters() {
        return maxPromptCharacters > 0 ? maxPromptCharacters : 24000;
    }

    public int resolvedMaxContextCharacters() {
        return maxContextCharacters > 0 ? maxContextCharacters : 14000;
    }

    public int resolvedMaxHistoryCharacters() {
        return maxHistoryCharacters > 0 ? maxHistoryCharacters : 6000;
    }

    public int resolvedMaxHistoryMessages() {
        return maxHistoryMessages > 0 ? maxHistoryMessages : 8;
    }
}
