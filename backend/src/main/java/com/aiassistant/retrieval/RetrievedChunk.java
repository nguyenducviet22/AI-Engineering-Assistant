package com.aiassistant.retrieval;

public record RetrievedChunk(
        String chunkId,
        String filePath,
        int startLine,
        int endLine,
        String symbol,
        String content,
        double score
) {
    public Citation toCitation() {
        return new Citation(chunkId, filePath, startLine, endLine);
    }
}
