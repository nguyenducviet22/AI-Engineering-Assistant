package com.aiassistant.retrieval;

public interface RepositoryRetrievalService {
    RetrievalResult retrieve(RepositoryRetrievalQuery query);

    record RepositoryRetrievalQuery(Long workspaceId, Long conversationId, String question) {
    }
}
