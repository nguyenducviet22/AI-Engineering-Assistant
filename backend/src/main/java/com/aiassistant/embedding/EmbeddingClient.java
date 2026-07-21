package com.aiassistant.embedding;

public interface EmbeddingClient {
    EmbeddingVector embed(String content);
}
