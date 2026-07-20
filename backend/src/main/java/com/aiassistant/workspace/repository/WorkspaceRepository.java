package com.aiassistant.workspace.repository;

import com.aiassistant.workspace.entity.Workspace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<Workspace> findByIdAndOwnerId(Long id, Long ownerId);
}
