package com.aiassistant.indexing;

import java.util.List;

public interface SourceChunker {
    boolean supports(String language);

    List<RepositoryChunk> chunk(String content, ChunkingContext context);
}
