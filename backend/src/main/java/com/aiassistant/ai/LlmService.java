package com.aiassistant.ai;

import org.springframework.ai.chat.prompt.Prompt;

public interface LlmService {
    LlmResponse generate(Prompt prompt);

    record LlmResponse(String content, String model, Integer tokenUsage) {
    }
}
