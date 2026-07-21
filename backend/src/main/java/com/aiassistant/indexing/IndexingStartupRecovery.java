package com.aiassistant.indexing;

import com.aiassistant.repository.entity.RepositoryStatus;
import com.aiassistant.repository.entity.RepositoryStatusHistory;
import com.aiassistant.repository.repository.ProjectRepositoryRepository;
import com.aiassistant.repository.repository.RepositoryStatusHistoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IndexingStartupRecovery implements ApplicationRunner {
    static final String INTERRUPTED_REASON = "Indexing interrupted by server restart.";

    private final ProjectRepositoryRepository repositories;
    private final RepositoryStatusHistoryRepository statusHistory;

    public IndexingStartupRecovery(ProjectRepositoryRepository repositories, RepositoryStatusHistoryRepository statusHistory) {
        this.repositories = repositories;
        this.statusHistory = statusHistory;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repositories.findByStatus(RepositoryStatus.INDEXING)
                .forEach(repository -> {
                    repository.markFailed(INTERRUPTED_REASON);
                    statusHistory.save(new RepositoryStatusHistory(repository, RepositoryStatus.FAILED, INTERRUPTED_REASON));
                });
    }
}
