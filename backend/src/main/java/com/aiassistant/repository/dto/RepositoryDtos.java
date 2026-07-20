package com.aiassistant.repository.dto;

import com.aiassistant.repository.entity.RepositoryStatus;
import java.time.Instant;

public final class RepositoryDtos {
    private RepositoryDtos() {}

    public record RepositoryResponse(
            Long id,
            Long workspaceId,
            String repositoryName,
            String language,
            String framework,
            String buildTool,
            String packageManager,
            RepositoryStatus status,
            Integer currentVersion,
            Integer fileCount,
            Long repositorySize,
            String failureReason,
            Instant createdAt) {}

    public record RepositoryStatusResponse(Long id, RepositoryStatus status, String failureReason) {}
}
