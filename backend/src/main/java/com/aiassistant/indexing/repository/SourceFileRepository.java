package com.aiassistant.indexing.repository;

import com.aiassistant.indexing.entity.SourceFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceFileRepository extends JpaRepository<SourceFile, Long> {
    List<SourceFile> findByRepositoryVersionId(Long repositoryVersionId);

    void deleteByRepositoryVersionId(Long repositoryVersionId);
}
