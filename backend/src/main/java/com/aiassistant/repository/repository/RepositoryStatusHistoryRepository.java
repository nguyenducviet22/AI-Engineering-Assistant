package com.aiassistant.repository.repository;

import com.aiassistant.repository.entity.RepositoryStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryStatusHistoryRepository extends JpaRepository<RepositoryStatusHistory, Long> {
    List<RepositoryStatusHistory> findByRepositoryIdOrderByCreatedAtAscIdAsc(Long repositoryId);

    void deleteByRepositoryId(Long repositoryId);
}
