package com.aiassistant.indexing;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SemanticChunkingService {
    private final List<SourceChunker> chunkers;

    public SemanticChunkingService(List<SourceChunker> chunkers) {
        this.chunkers = chunkers;
    }

    public List<RepositoryChunk> chunk(String content, ChunkingContext context) {
        return chunkers.stream()
                .filter(chunker -> chunker.supports(context.language()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported indexing language: " + context.language()))
                .chunk(content, context);
    }
}
