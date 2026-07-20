package com.aiassistant.repository.service;

import com.aiassistant.exception.ApiException;
import com.aiassistant.repository.entity.RepositoryMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ZipRepositoryValidator {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "java", "ts", "tsx", "js", "jsx", "sql", "md", "yml", "yaml", "xml", "json", "html", "css");
    private static final Set<String> IGNORED_SEGMENTS = Set.of("node_modules", "target", "build", "dist", "coverage", ".git", ".idea", ".vscode");
    private final ZipValidationProperties properties;

    public ZipRepositoryValidator(ZipValidationProperties properties) {
        this.properties = properties;
    }

    public RepositoryMetadata validate(MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported Archive", "Only ZIP repository uploads are supported.");
        }
        if (file.getSize() > properties.maxCompressedBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Repository Too Large", "Compressed ZIP exceeds the maximum allowed size.");
        }

        MetadataAccumulator metadata = new MetadataAccumulator();
        try (InputStream input = file.getInputStream(); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                validateEntryPath(entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }
                String extension = extension(entry.getName());
                if (isNestedArchive(extension)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Nested Archive Rejected", "Nested archives are not allowed in repository uploads.");
                }
                boolean ignored = shouldIgnore(entry.getName());
                long entryBytes = drainEntry(zip);
                if (entryBytes > properties.maxEntryBytes()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "File Too Large", "A repository file exceeds the per-file size limit.");
                }
                metadata.expandedBytes += entryBytes;
                if (metadata.expandedBytes > properties.maxExpandedBytes()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Repository Too Large", "Expanded ZIP contents exceed the maximum allowed size.");
                }
                if (!ignored) {
                    metadata.repositoryFiles++;
                    if (metadata.repositoryFiles > properties.maxFiles()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Repository Too Large", "Repository contains too many source files. Exclude generated folders such as node_modules, target, build, dist, coverage, and .git.");
                    }
                    if (SUPPORTED_EXTENSIONS.contains(extension)) {
                        metadata.supportedFiles++;
                        metadata.extensions.add(extension);
                        metadata.names.add(Path.of(entry.getName()).getFileName().toString().toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Repository Upload Failed", "ZIP file could not be read.");
        }
        if (metadata.supportedFiles == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported Repository", "No supported source files were found.");
        }
        if (file.getSize() > 0 && metadata.expandedBytes / Math.max(file.getSize(), 1) > properties.maxCompressionRatio()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Suspicious Compression Ratio", "ZIP expansion ratio exceeds the safety limit.");
        }
        return metadata.toRepositoryMetadata();
    }

    private long drainEntry(ZipInputStream zip) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            total += read;
            if (total > properties.maxEntryBytes() || total > properties.maxExpandedBytes()) {
                break;
            }
        }
        return total;
    }

    private void validateEntryPath(String name) {
        Path normalized = Path.of(name).normalize();
        String normalizedSlashes = name.replace('\\', '/');
        if (name.startsWith("/") || name.startsWith("\\") || normalized.isAbsolute()
                || normalizedSlashes.matches("^[A-Za-z]:/.*")
                || normalized.startsWith("..") || name.contains("..\\") || name.contains("../")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid ZIP Entry", "Repository archive contains an unsafe path.");
        }
        if (normalized.getNameCount() > properties.maxDepth()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Repository Too Deep", "Repository archive exceeds the maximum directory depth.");
        }
    }

    private boolean shouldIgnore(String name) {
        for (String segment : name.replace('\\', '/').split("/")) {
            if (IGNORED_SEGMENTS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isNestedArchive(String extension) {
        return Set.of("zip", "jar", "war", "7z", "gz", "rar").contains(extension);
    }

    private static final class MetadataAccumulator {
        private int supportedFiles;
        private int repositoryFiles;
        private long expandedBytes;
        private final Set<String> extensions = new HashSet<>();
        private final Set<String> names = new HashSet<>();

        private RepositoryMetadata toRepositoryMetadata() {
            String language = detectLanguage();
            String framework = names.contains("pom.xml") ? "Spring Boot" : names.contains("package.json") ? "React" : "Unknown";
            String buildTool = names.contains("pom.xml") ? "Maven" : names.contains("build.gradle") ? "Gradle" : "Unknown";
            String packageManager = names.contains("package.json") ? "npm" : "Unknown";
            return new RepositoryMetadata(language, framework, buildTool, packageManager, supportedFiles, expandedBytes);
        }

        private String detectLanguage() {
            if (extensions.contains("java")) return "Java";
            if (extensions.contains("ts") || extensions.contains("tsx")) return "TypeScript";
            if (extensions.contains("js") || extensions.contains("jsx")) return "JavaScript";
            if (extensions.contains("sql")) return "SQL";
            return "Markdown";
        }
    }
}
