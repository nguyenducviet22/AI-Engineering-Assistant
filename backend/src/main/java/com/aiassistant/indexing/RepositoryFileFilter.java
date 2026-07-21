package com.aiassistant.indexing;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RepositoryFileFilter {
    private static final Set<String> IGNORED_SEGMENTS = Set.of(
            "node_modules", "target", "build", "dist", "coverage", ".git", ".idea", ".vscode");
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "pdf", "zip", "jar", "war", "class", "exe", "dll",
            "so", "dylib", "mp4", "mov", "avi", "mp3", "wav", "ttf", "woff", "woff2");

    public boolean shouldIndex(String path) {
        String normalized = path.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (IGNORED_SEGMENTS.contains(segment)) {
                return false;
            }
        }
        String extension = extension(normalized);
        return languageForPath(normalized) != null && !BINARY_EXTENSIONS.contains(extension);
    }

    public String languageForPath(String path) {
        return switch (extension(path)) {
            case "java" -> "Java";
            case "ts", "tsx" -> "TypeScript";
            case "js", "jsx" -> "JavaScript";
            case "sql" -> "SQL";
            case "md", "markdown" -> "Markdown";
            case "yml", "yaml" -> "YAML";
            case "json" -> "JSON";
            case "xml" -> "XML";
            default -> null;
        };
    }

    private String extension(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? "" : path.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
