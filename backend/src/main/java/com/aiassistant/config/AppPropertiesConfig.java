package com.aiassistant.config;

import com.aiassistant.ai.OpenRouterChatProperties;
import com.aiassistant.ai.PromptProperties;
import com.aiassistant.embedding.OpenRouterEmbeddingProperties;
import com.aiassistant.indexing.IndexingProperties;
import com.aiassistant.repository.service.ZipValidationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ZipValidationProperties.class,
        IndexingProperties.class,
        OpenRouterEmbeddingProperties.class,
        OpenRouterChatProperties.class,
        PromptProperties.class
})
public class AppPropertiesConfig {
}
