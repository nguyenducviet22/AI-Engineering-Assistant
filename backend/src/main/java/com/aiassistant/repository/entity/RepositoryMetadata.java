package com.aiassistant.repository.entity;

public record RepositoryMetadata(
        String language,
        String framework,
        String buildTool,
        String packageManager,
        int fileCount,
        long expandedBytes) {
}
