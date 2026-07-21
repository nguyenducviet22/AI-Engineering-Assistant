package com.aiassistant.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MarkdownSourceChunker implements SourceChunker {
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.+");

    @Override
    public boolean supports(String language) {
        return "Markdown".equalsIgnoreCase(language);
    }

    @Override
    public List<RepositoryChunk> chunk(String content, ChunkingContext context) {
        List<String> lines = LineText.lines(content);
        AtomicInteger chunkIndex = new AtomicInteger();
        List<Integer> starts = new ArrayList<>();
        boolean inFence = false;
        for (int index = 0; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence;
            }
            if (!inFence && HEADING.matcher(lines.get(index)).matches()) {
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
                    LineText.slice(lines, start, end), context, "", "", "", ChunkType.DOCUMENTATION, start, end, chunkIndex));
        }
        return chunks;
    }
}
