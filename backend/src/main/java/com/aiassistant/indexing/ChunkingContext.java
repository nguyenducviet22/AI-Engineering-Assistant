package com.aiassistant.indexing;

public record ChunkingContext(
        Long workspaceId,
        Long repositoryId,
        Integer version,
        String framework,
        String filePath,
        String language) {
}
