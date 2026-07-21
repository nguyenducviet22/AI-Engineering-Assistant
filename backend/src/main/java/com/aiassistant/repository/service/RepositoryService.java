package com.aiassistant.repository.service;

import com.aiassistant.exception.ApiException;
import com.aiassistant.indexing.AsyncIndexingExecutor;
import com.aiassistant.indexing.IndexingProperties;
import com.aiassistant.indexing.RepositoryIndexingService;
import com.aiassistant.indexing.repository.CodeChunkRepository;
import com.aiassistant.indexing.repository.SourceFileRepository;
import com.aiassistant.repository.dto.RepositoryDtos.*;
import com.aiassistant.repository.entity.ProjectRepository;
import com.aiassistant.repository.entity.RepositoryMetadata;
import com.aiassistant.repository.entity.RepositoryStatus;
import com.aiassistant.repository.entity.RepositoryStatusHistory;
import com.aiassistant.repository.repository.ProjectRepositoryRepository;
import com.aiassistant.repository.repository.RepositoryStatusHistoryRepository;
import com.aiassistant.workspace.entity.Workspace;
import com.aiassistant.workspace.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RepositoryService {
    private final WorkspaceService workspaceService;
    private final ProjectRepositoryRepository repositories;
    private final ZipRepositoryValidator validator;
    private final RepositoryStorageService storage;
    private final RepositoryStatusHistoryRepository statusHistory;
    private final CodeChunkRepository codeChunks;
    private final SourceFileRepository sourceFiles;
    private final AsyncIndexingExecutor asyncIndexingExecutor;
    private final RepositoryIndexingService indexingService;
    private final IndexingProperties indexingProperties;

    public RepositoryService(WorkspaceService workspaceService, ProjectRepositoryRepository repositories,
                             ZipRepositoryValidator validator, RepositoryStorageService storage,
                             RepositoryStatusHistoryRepository statusHistory,
                             CodeChunkRepository codeChunks, SourceFileRepository sourceFiles,
                             AsyncIndexingExecutor asyncIndexingExecutor, RepositoryIndexingService indexingService,
                             IndexingProperties indexingProperties) {
        this.workspaceService = workspaceService;
        this.repositories = repositories;
        this.validator = validator;
        this.storage = storage;
        this.statusHistory = statusHistory;
        this.codeChunks = codeChunks;
        this.sourceFiles = sourceFiles;
        this.asyncIndexingExecutor = asyncIndexingExecutor;
        this.indexingService = indexingService;
        this.indexingProperties = indexingProperties;
    }

    @Transactional
    public RepositoryResponse upload(Long ownerId, Long workspaceId, MultipartFile file) {
        Workspace workspace = workspaceService.requireOwned(ownerId, workspaceId);
        ProjectRepository repository = repositories.save(new ProjectRepository(workspace, cleanName(file.getOriginalFilename())));
        recordStatus(repository, RepositoryStatus.UPLOADING, null);
        repository.markValidating();
        recordStatus(repository, RepositoryStatus.VALIDATING, null);
        RepositoryMetadata metadata = validator.validate(file);
        repository.markValidated(metadata);
        recordStatus(repository, RepositoryStatus.INDEXING, null);
        storage.storeOriginalZip(workspaceId, repository.getId(), repository.getCurrentVersion(), file);
        enqueueAfterCommit(repository.getId(), repository.getCurrentVersion());
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
    public RepositoryStatusResponse retryIndexing(Long ownerId, Long id) {
        ProjectRepository repository = requireOwned(ownerId, id);
        repository.markIndexing();
        recordStatus(repository, RepositoryStatus.INDEXING, null);
        enqueueAfterCommit(repository.getId(), repository.getCurrentVersion());
        return new RepositoryStatusResponse(repository.getId(), repository.getStatus(), repository.getFailureReason());
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        ProjectRepository repository = requireOwned(ownerId, id);
        storage.deleteRepository(repository.getWorkspace().getId(), repository.getId());
        repository.getVersions().forEach(version -> {
            codeChunks.deleteBySourceFileRepositoryVersionId(version.getId());
            sourceFiles.deleteByRepositoryVersionId(version.getId());
        });
        statusHistory.deleteByRepositoryId(repository.getId());
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

    private void enqueueAfterCommit(Long repositoryId, Integer version) {
        if (!indexingProperties.enabled()) {
            return;
        }
        Runnable work = () -> {
            if (indexingProperties.asyncEnabled()) {
                asyncIndexingExecutor.indexAsync(repositoryId, version);
            } else {
                indexingService.index(repositoryId, version);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }

    private void recordStatus(ProjectRepository repository, RepositoryStatus status, String reason) {
        statusHistory.save(new RepositoryStatusHistory(repository, status, reason));
    }

    private String cleanName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "repository.zip";
        }
        return originalFilename.replace("\\", "/").substring(originalFilename.replace("\\", "/").lastIndexOf('/') + 1);
    }
}
