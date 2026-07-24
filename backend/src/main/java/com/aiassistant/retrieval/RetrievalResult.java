package com.aiassistant.retrieval;

import java.util.List;

public record RetrievalResult(List<RetrievedChunk> chunks) {
    public RetrievalResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public static RetrievalResult empty() {
        return new RetrievalResult(List.of());
    }
}
