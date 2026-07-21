package com.aiassistant.repository.repository;

import com.aiassistant.repository.entity.RepositoryVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryVersionRepository extends JpaRepository<RepositoryVersion, Long> {
    Optional<RepositoryVersion> findByRepositoryIdAndVersion(Long repositoryId, Integer version);
}
