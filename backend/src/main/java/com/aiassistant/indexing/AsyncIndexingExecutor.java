package com.aiassistant.indexing;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncIndexingExecutor {
    private final RepositoryIndexingService indexingService;

    public AsyncIndexingExecutor(RepositoryIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @Async("indexingTaskExecutor")
    public void indexAsync(Long repositoryId, Integer version) {
        indexingService.index(repositoryId, version);
    }
}
