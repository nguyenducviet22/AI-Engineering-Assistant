package com.aiassistant.repository.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public record ZipValidationProperties(
        long maxCompressedBytes,
        long maxExpandedBytes,
        long maxEntryBytes,
        int maxFiles,
        int maxDepth,
        int maxCompressionRatio) {
}
