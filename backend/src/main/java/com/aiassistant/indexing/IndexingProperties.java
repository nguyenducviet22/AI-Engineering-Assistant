package com.aiassistant.indexing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.indexing")
public record IndexingProperties(boolean enabled, boolean asyncEnabled) {
}
