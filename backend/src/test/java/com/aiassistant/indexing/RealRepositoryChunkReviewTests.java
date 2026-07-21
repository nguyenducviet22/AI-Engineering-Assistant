package com.aiassistant.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealRepositoryChunkReviewTests {
    @Test
    void chunkRealBackendFilesAndWriteReviewEvidence() throws Exception {
        SemanticChunkingService service = new SemanticChunkingService(List.of(
                new JavaSourceChunker(),
                new ScriptSourceChunker(),
                new SqlSourceChunker(),
                new MarkdownSourceChunker(),
                new StructuredTextSourceChunker()));
        List<Path> files = List.of(
                Path.of("src/main/java/com/aiassistant/repository/service/RepositoryService.java"),
                Path.of("src/main/java/com/aiassistant/repository/service/ZipRepositoryValidator.java"),
                Path.of("src/main/resources/application.yml"));

        StringBuilder evidence = new StringBuilder();
        int totalChunks = 0;
        for (Path file : files) {
            String language = file.toString().endsWith(".java") ? "Java" : "YAML";
            List<RepositoryChunk> chunks = service.chunk(Files.readString(file), context(language, file));
            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.hasRequiredMetadata()).isTrue());
            totalChunks += chunks.size();
            evidence.append(file).append(System.lineSeparator());
            chunks.stream()
                    .sorted(Comparator.comparingInt(RepositoryChunk::chunkIndex))
                    .limit(12)
                    .forEach(chunk -> evidence.append("  #")
                            .append(chunk.chunkIndex())
                            .append(" ")
                            .append(chunk.chunkType())
                            .append(" lines ")
                            .append(chunk.startLine())
                            .append("-")
                            .append(chunk.endLine())
                            .append(" class=")
                            .append(chunk.className())
                            .append(" method=")
                            .append(chunk.methodName())
                            .append(System.lineSeparator()));
        }

        Path output = Path.of("target/chunk-review/real-files.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, evidence.toString());
        assertThat(totalChunks).isGreaterThan(5);
    }

    private ChunkingContext context(String language, Path file) {
        return new ChunkingContext(10L, 20L, 3, "Spring Boot", file.toString().replace('\\', '/'), language);
    }
}
