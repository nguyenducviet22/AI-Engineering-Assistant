package com.aiassistant.retrieval;

public record Citation(
        String chunkId,
        String filePath,
        int startLine,
        int endLine,
        int responseStartIndex,
        int responseEndIndex,
        String responseText
) {
    public Citation(String chunkId, String filePath, int startLine, int endLine) {
        this(chunkId, filePath, startLine, endLine, -1, -1, "");
    }
}
