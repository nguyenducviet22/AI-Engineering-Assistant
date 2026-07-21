package com.aiassistant.indexing.repository;

import com.aiassistant.indexing.entity.CodeChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeChunkRepository extends JpaRepository<CodeChunk, Long> {
    List<CodeChunk> findByRepositoryIdAndVersion(Long repositoryId, Integer version);

    void deleteBySourceFileRepositoryVersionId(Long repositoryVersionId);
}
