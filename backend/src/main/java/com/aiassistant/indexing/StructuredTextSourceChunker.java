package com.aiassistant.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StructuredTextSourceChunker implements SourceChunker {
    private static final Pattern YAML_TOP_LEVEL = Pattern.compile("^[A-Za-z0-9_.-]+\\s*:");

    @Override
    public boolean supports(String language) {
        return "YAML".equalsIgnoreCase(language) || "JSON".equalsIgnoreCase(language) || "XML".equalsIgnoreCase(language);
    }

    @Override
    public List<RepositoryChunk> chunk(String content, ChunkingContext context) {
        if ("YAML".equalsIgnoreCase(context.language())) {
            return yamlChunks(content, context);
        }
        return bracketedChunks(content, context);
    }

    private List<RepositoryChunk> yamlChunks(String content, ChunkingContext context) {
        List<String> lines = LineText.lines(content);
        AtomicInteger chunkIndex = new AtomicInteger();
        List<Integer> starts = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (YAML_TOP_LEVEL.matcher(lines.get(index)).find()) {
                starts.add(index + 1);
            }
        }
        if (starts.isEmpty() || starts.getFirst() != 1) {
            starts.addFirst(1);
        }
        List<RepositoryChunk> chunks = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            int end = index + 1 < starts.size() ? starts.get(index + 1) - 1 : Math.max(1, lines.size());
            chunks.addAll(SemanticChunkFactory.chunks(
                    LineText.slice(lines, start, end), context, "", "", "", ChunkType.CONFIG, start, end, chunkIndex));
        }
        return chunks;
    }

    private List<RepositoryChunk> bracketedChunks(String content, ChunkingContext context) {
        List<String> lines = LineText.lines(content);
        AtomicInteger chunkIndex = new AtomicInteger();
        return SemanticChunkFactory.chunks(
                content, context, "", "", "", ChunkType.CONFIG, 1, Math.max(1, lines.size()), chunkIndex);
    }
}
