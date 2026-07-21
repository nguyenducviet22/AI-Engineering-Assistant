package com.aiassistant.embedding;

public interface EmbeddingVectorStore {
    void deleteByRepositoryVersion(Long repositoryVersionId);

    void store(Long repositoryVersionId, Long chunkId, EmbeddingVector vector);
}
