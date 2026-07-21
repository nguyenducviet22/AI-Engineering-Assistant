package com.aiassistant.indexing;

import java.nio.file.Path;

public record IndexableFile(Path absolutePath, String relativePath, String language) {
}
