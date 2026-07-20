package com.aiassistant.workspace.dto;

import com.aiassistant.workspace.entity.WorkspaceVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class WorkspaceDtos {
    private WorkspaceDtos() {}

    public record WorkspaceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 80) String language,
            @NotBlank @Size(max = 80) String framework,
            @NotNull WorkspaceVisibility visibility) {}

    public record WorkspaceResponse(
            Long id,
            String name,
            String description,
            String language,
            String framework,
            WorkspaceVisibility visibility,
            Instant createdAt,
            Instant updatedAt) {}
}
