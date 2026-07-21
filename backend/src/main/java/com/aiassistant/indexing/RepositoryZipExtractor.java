package com.aiassistant.indexing;

import com.aiassistant.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RepositoryZipExtractor {
    private final RepositoryFileFilter fileFilter;

    public RepositoryZipExtractor(RepositoryFileFilter fileFilter) {
        this.fileFilter = fileFilter;
    }

    public List<IndexableFile> extract(Path sourceZip, Path targetDirectory) {
        Path root = targetDirectory.toAbsolutePath().normalize();
        resetDirectory(root);
        List<IndexableFile> files = new ArrayList<>();
        try (InputStream input = Files.newInputStream(sourceZip); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !fileFilter.shouldIndex(entry.getName())) {
                    continue;
                }
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid ZIP Entry", "Repository archive contains an unsafe path.");
                }
                byte[] content = zip.readAllBytes();
                if (isLikelyBinary(content)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.write(target, content);
                String relativePath = root.relativize(target).toString().replace('\\', '/');
                files.add(new IndexableFile(target, relativePath, fileFilter.languageForPath(relativePath)));
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Repository Extraction Failed", "Repository ZIP could not be extracted for indexing.");
        }
        return files;
    }

    private boolean isLikelyBinary(byte[] content) {
        int inspected = Math.min(content.length, 4096);
        int control = 0;
        for (int index = 0; index < inspected; index++) {
            int value = content[index] & 0xff;
            if (value == 0) {
                return true;
            }
            if (value < 0x09 || (value > 0x0d && value < 0x20)) {
                control++;
            }
        }
        return inspected > 0 && control > inspected / 10;
    }

    private void resetDirectory(Path root) {
        try {
            if (Files.exists(root)) {
                try (var paths = Files.walk(root)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
                }
            }
            Files.createDirectories(root);
        } catch (IOException | IllegalStateException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Repository Extraction Failed", "Index extraction directory could not be prepared.");
        }
    }
}
