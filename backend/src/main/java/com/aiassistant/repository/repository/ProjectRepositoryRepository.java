package com.aiassistant.repository.repository;

import com.aiassistant.repository.entity.ProjectRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryRepository extends JpaRepository<ProjectRepository, Long> {
    Optional<ProjectRepository> findByIdAndWorkspaceOwnerId(Long id, Long ownerId);
}
