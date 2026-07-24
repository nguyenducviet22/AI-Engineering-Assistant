package com.aiassistant.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {
    private static final String REPOSITORY_CHAT_TEMPLATE = "prompts/repository-chat.md";

    public String repositoryChatTemplate() {
        try {
            return new ClassPathResource(REPOSITORY_CHAT_TEMPLATE)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Repository chat prompt template could not be loaded.", ex);
        }
    }
}
