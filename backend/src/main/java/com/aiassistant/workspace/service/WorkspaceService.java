package com.aiassistant.workspace.service;

import com.aiassistant.auth.entity.User;
import com.aiassistant.auth.repository.UserRepository;
import com.aiassistant.exception.ApiException;
import com.aiassistant.repository.service.RepositoryStorageService;
import com.aiassistant.workspace.dto.WorkspaceDtos.*;
import com.aiassistant.workspace.entity.Workspace;
import com.aiassistant.workspace.repository.WorkspaceRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaces;
    private final UserRepository users;
    private final RepositoryStorageService storage;

    public WorkspaceService(WorkspaceRepository workspaces, UserRepository users, RepositoryStorageService storage) {
        this.workspaces = workspaces;
        this.users = users;
        this.storage = storage;
    }

    public List<WorkspaceResponse> list(Long ownerId) {
        return workspaces.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WorkspaceResponse create(Long ownerId, WorkspaceRequest request) {
        User owner = users.findById(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User Not Found", "Workspace owner was not found."));
        Workspace workspace = workspaces.save(new Workspace(owner, request.name(), request.description(), request.language(), request.framework(), request.visibility()));
        return toResponse(workspace);
    }

    public WorkspaceResponse get(Long ownerId, Long id) {
        return toResponse(requireOwned(ownerId, id));
    }

    @Transactional
    public WorkspaceResponse update(Long ownerId, Long id, WorkspaceRequest request) {
        Workspace workspace = requireOwned(ownerId, id);
        workspace.update(request.name(), request.description(), request.language(), request.framework(), request.visibility());
        return toResponse(workspace);
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        Workspace workspace = requireOwned(ownerId, id);
        storage.deleteWorkspace(workspace.getId());
        workspaces.delete(workspace);
    }

    public Workspace requireOwned(Long ownerId, Long id) {
        return workspaces.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workspace Not Found", "Workspace was not found."));
    }

    public Workspace requireExisting(Long id) {
        return workspaces.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workspace Not Found", "Workspace was not found."));
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getDescription(), workspace.getLanguage(),
                workspace.getFramework(), workspace.getVisibility(), workspace.getCreatedAt(), workspace.getUpdatedAt());
    }
}
