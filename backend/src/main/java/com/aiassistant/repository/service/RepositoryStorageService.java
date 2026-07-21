package com.aiassistant.repository.service;

import com.aiassistant.exception.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RepositoryStorageService {
    private final Path root;

    public RepositoryStorageService(@Value("${app.storage.repositories-dir}") String repositoriesDir) {
        this.root = Path.of(repositoriesDir).toAbsolutePath().normalize();
    }

    public void storeOriginalZip(Long workspaceId, Long repositoryId, int version, MultipartFile file) {
        Path target = root.resolve("workspace-" + workspaceId).resolve("repository-" + repositoryId).resolve("v" + version).resolve("source.zip").normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Storage Path", "Repository storage path is unsafe.");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Repository Upload Failed", "Repository ZIP could not be stored.");
        }
    }

    public Path sourceZipPath(Long workspaceId, Long repositoryId, int version) {
        return versionDirectory(workspaceId, repositoryId, version).resolve("source.zip").normalize();
    }

    public Path extractedDirectory(Long workspaceId, Long repositoryId, int version) {
        return versionDirectory(workspaceId, repositoryId, version).resolve("extracted").normalize();
    }

    public void deleteRepository(Long workspaceId, Long repositoryId) {
        deleteDirectory(root.resolve("workspace-" + workspaceId).resolve("repository-" + repositoryId).normalize());
    }

    public void deleteWorkspace(Long workspaceId) {
        deleteDirectory(root.resolve("workspace-" + workspaceId).normalize());
    }

    private Path versionDirectory(Long workspaceId, Long repositoryId, int version) {
        Path directory = root.resolve("workspace-" + workspaceId).resolve("repository-" + repositoryId).resolve("v" + version).normalize();
        if (!directory.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Storage Path", "Repository storage path is unsafe.");
        }
        return directory;
    }

    private void deleteDirectory(Path target) {
        if (!target.startsWith(root) || !Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("Unable to delete repository storage.", ex);
                }
            });
        } catch (IOException | IllegalStateException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Repository Deletion Failed", "Repository files could not be removed.");
        }
    }
}
