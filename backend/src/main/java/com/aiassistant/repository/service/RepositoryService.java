package com.aiassistant.repository.service;

import com.aiassistant.exception.ApiException;
import com.aiassistant.repository.dto.RepositoryDtos.*;
import com.aiassistant.repository.entity.ProjectRepository;
import com.aiassistant.repository.entity.RepositoryMetadata;
import com.aiassistant.repository.repository.ProjectRepositoryRepository;
import com.aiassistant.workspace.entity.Workspace;
import com.aiassistant.workspace.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RepositoryService {
    private final WorkspaceService workspaceService;
    private final ProjectRepositoryRepository repositories;
    private final ZipRepositoryValidator validator;
    private final RepositoryStorageService storage;

    public RepositoryService(WorkspaceService workspaceService, ProjectRepositoryRepository repositories,
                             ZipRepositoryValidator validator, RepositoryStorageService storage) {
        this.workspaceService = workspaceService;
        this.repositories = repositories;
        this.validator = validator;
        this.storage = storage;
    }

    @Transactional
    public RepositoryResponse upload(Long ownerId, Long workspaceId, MultipartFile file) {
        Workspace workspace = workspaceService.requireOwned(ownerId, workspaceId);
        RepositoryMetadata metadata = validator.validate(file);
        ProjectRepository repository = repositories.save(new ProjectRepository(workspace, cleanName(file.getOriginalFilename())));
        repository.markValidated(metadata);
        storage.storeOriginalZip(workspaceId, repository.getId(), repository.getCurrentVersion(), file);
        return toResponse(repository);
    }

    public RepositoryResponse get(Long ownerId, Long id) {
        return toResponse(requireOwned(ownerId, id));
    }

    public RepositoryStatusResponse status(Long ownerId, Long id) {
        ProjectRepository repository = requireOwned(ownerId, id);
        return new RepositoryStatusResponse(repository.getId(), repository.getStatus(), repository.getFailureReason());
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        ProjectRepository repository = requireOwned(ownerId, id);
        storage.deleteRepository(repository.getWorkspace().getId(), repository.getId());
        repositories.delete(repository);
    }

    private ProjectRepository requireOwned(Long ownerId, Long id) {
        return repositories.findByIdAndWorkspaceOwnerId(id, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Repository Not Found", "Repository was not found."));
    }

    private RepositoryResponse toResponse(ProjectRepository repository) {
        return new RepositoryResponse(repository.getId(), repository.getWorkspace().getId(), repository.getRepositoryName(),
                repository.getLanguage(), repository.getFramework(), repository.getBuildTool(), repository.getPackageManager(),
                repository.getStatus(), repository.getCurrentVersion(), repository.getFileCount(), repository.getRepositorySize(),
                repository.getFailureReason(), repository.getCreatedAt());
    }

    private String cleanName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "repository.zip";
        }
        return originalFilename.replace("\\", "/").substring(originalFilename.replace("\\", "/").lastIndexOf('/') + 1);
    }
}
