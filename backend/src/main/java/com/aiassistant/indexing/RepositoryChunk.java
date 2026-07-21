package com.aiassistant.indexing;

public record RepositoryChunk(
        Long workspaceId,
        Long repositoryId,
        Integer version,
        String packageName,
        String className,
        String methodName,
        String language,
        String framework,
        String filePath,
        int startLine,
        int endLine,
        ChunkType chunkType,
        int chunkIndex,
        String content) {

    public boolean hasRequiredMetadata() {
        return workspaceId != null
                && repositoryId != null
                && version != null
                && packageName != null
                && className != null
                && methodName != null
                && notBlank(language)
                && framework != null
                && notBlank(filePath)
                && startLine > 0
                && endLine >= startLine;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
