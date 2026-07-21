package com.aiassistant.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class SqlSourceChunker implements SourceChunker {
    @Override
    public boolean supports(String language) {
        return "SQL".equalsIgnoreCase(language);
    }

    @Override
    public List<RepositoryChunk> chunk(String content, ChunkingContext context) {
        List<String> lines = LineText.lines(content);
        AtomicInteger chunkIndex = new AtomicInteger();
        List<RepositoryChunk> chunks = new ArrayList<>();
        int statementStart = 1;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            for (int charIndex = 0; charIndex < line.length(); charIndex++) {
                char value = line.charAt(charIndex);
                if (value == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (value == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                } else if (value == ';' && !inSingleQuote && !inDoubleQuote) {
                    addStatement(lines, context, statementStart, index + 1, chunks, chunkIndex);
                    statementStart = index + 2;
                    break;
                }
            }
        }
        if (statementStart <= lines.size()) {
            addStatement(lines, context, statementStart, lines.size(), chunks, chunkIndex);
        }
        return chunks.isEmpty()
                ? SemanticChunkFactory.chunks(content, context, "", "", "", ChunkType.SQL, 1, Math.max(1, lines.size()), chunkIndex)
                : chunks;
    }

    private void addStatement(
            List<String> lines,
            ChunkingContext context,
            int startLine,
            int endLine,
            List<RepositoryChunk> chunks,
            AtomicInteger chunkIndex) {
        String content = LineText.slice(lines, startLine, endLine);
        if (!content.isBlank()) {
            chunks.addAll(SemanticChunkFactory.chunks(
                    content, context, "", "", "", ChunkType.SQL, startLine, endLine, chunkIndex));
        }
    }
}
