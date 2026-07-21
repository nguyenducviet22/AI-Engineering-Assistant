package com.aiassistant.indexing;

import com.aiassistant.embedding.EmbeddingClient;
import com.aiassistant.embedding.EmbeddingVector;
import com.aiassistant.embedding.EmbeddingVectorStore;
import com.aiassistant.exception.ApiException;
import com.aiassistant.indexing.entity.CodeChunk;
import com.aiassistant.indexing.entity.SourceFile;
import com.aiassistant.indexing.repository.CodeChunkRepository;
import com.aiassistant.indexing.repository.SourceFileRepository;
import com.aiassistant.repository.entity.ProjectRepository;
import com.aiassistant.repository.entity.RepositoryStatus;
import com.aiassistant.repository.entity.RepositoryStatusHistory;
import com.aiassistant.repository.entity.RepositoryVersion;
import com.aiassistant.repository.repository.ProjectRepositoryRepository;
import com.aiassistant.repository.repository.RepositoryStatusHistoryRepository;
import com.aiassistant.repository.repository.RepositoryVersionRepository;
import com.aiassistant.repository.service.RepositoryStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryIndexingService {
    private final ProjectRepositoryRepository repositories;
    private final RepositoryVersionRepository versions;
    private final SourceFileRepository sourceFiles;
    private final CodeChunkRepository codeChunks;
    private final RepositoryStorageService storage;
    private final RepositoryStatusHistoryRepository statusHistory;
    private final RepositoryZipExtractor extractor;
    private final SemanticChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingVectorStore vectorStore;

    public RepositoryIndexingService(ProjectRepositoryRepository repositories,
                                     RepositoryVersionRepository versions,
                                     SourceFileRepository sourceFiles,
                                     CodeChunkRepository codeChunks,
                                     RepositoryStorageService storage,
                                     RepositoryStatusHistoryRepository statusHistory,
                                     RepositoryZipExtractor extractor,
                                     SemanticChunkingService chunkingService,
                                     EmbeddingClient embeddingClient,
                                     EmbeddingVectorStore vectorStore) {
        this.repositories = repositories;
        this.versions = versions;
        this.sourceFiles = sourceFiles;
        this.codeChunks = codeChunks;
        this.storage = storage;
        this.statusHistory = statusHistory;
        this.extractor = extractor;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void index(Long repositoryId, Integer versionNumber) {
        ProjectRepository repository = repositories.findById(repositoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Repository Not Found", "Repository was not found."));
        RepositoryVersion version = versions.findByRepositoryIdAndVersion(repositoryId, versionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Repository Version Not Found", "Repository version was not found."));
        try {
            repository.markIndexing();
            clearVersion(version.getId());
            List<IndexableFile> files = extractor.extract(
                    storage.sourceZipPath(repository.getWorkspace().getId(), repository.getId(), versionNumber),
                    storage.extractedDirectory(repository.getWorkspace().getId(), repository.getId(), versionNumber));
            for (IndexableFile file : files) {
                indexFile(repository, version, file);
            }
            repository.markReady();
            recordStatus(repository, RepositoryStatus.READY, null);
        } catch (RuntimeException ex) {
            String reason = rootMessage(ex);
            repository.markFailed(reason);
            recordStatus(repository, RepositoryStatus.FAILED, reason);
        }
    }

    @Transactional
    public void retry(Long repositoryId) {
        ProjectRepository repository = repositories.findById(repositoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Repository Not Found", "Repository was not found."));
        index(repository.getId(), repository.getCurrentVersion());
    }

    private void indexFile(ProjectRepository repository, RepositoryVersion version, IndexableFile file) {
        try {
            String content = Files.readString(file.absolutePath());
            SourceFile sourceFile = sourceFiles.save(new SourceFile(
                    version,
                    file.absolutePath().getFileName().toString(),
                    file.relativePath(),
                    file.language(),
                    Files.size(file.absolutePath()),
                    checksum(content)));
            ChunkingContext context = new ChunkingContext(
                    repository.getWorkspace().getId(),
                    repository.getId(),
                    version.getVersion(),
                    repository.getFramework(),
                    file.relativePath(),
                    file.language());
            for (RepositoryChunk chunk : chunkingService.chunk(content, context)) {
                CodeChunk saved = codeChunks.save(toEntity(sourceFile, chunk));
                EmbeddingVector vector = embeddingClient.embed(chunk.content());
                vectorStore.store(version.getId(), saved.getId(), vector);
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Repository Indexing Failed", "Source file could not be indexed.");
        }
    }

    private void clearVersion(Long repositoryVersionId) {
        vectorStore.deleteByRepositoryVersion(repositoryVersionId);
        codeChunks.deleteBySourceFileRepositoryVersionId(repositoryVersionId);
        sourceFiles.deleteByRepositoryVersionId(repositoryVersionId);
    }

    private CodeChunk toEntity(SourceFile sourceFile, RepositoryChunk chunk) {
        return new CodeChunk(sourceFile, chunk.chunkIndex(), chunk.chunkType(), chunk.content(), chunk.startLine(), chunk.endLine(),
                chunk.workspaceId(), chunk.repositoryId(), chunk.version(), chunk.packageName(), chunk.className(), chunk.methodName(),
                chunk.language(), chunk.framework(), chunk.filePath());
    }

    private String checksum(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String rootMessage(RuntimeException ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? "Repository indexing failed." : message;
    }

    private void recordStatus(ProjectRepository repository, RepositoryStatus status, String reason) {
        statusHistory.save(new RepositoryStatusHistory(repository, status, reason));
    }
}
