package com.aiassistant.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class SemanticChunkFactory {
    private static final int MAX_TOKENS = 1200;
    private static final int TARGET_SPLIT_TOKENS = 800;
    private static final double OVERLAP_RATIO = 0.10;

    private SemanticChunkFactory() {
    }

    static List<RepositoryChunk> chunks(
            String content,
            ChunkingContext context,
            String packageName,
            String className,
            String methodName,
            ChunkType chunkType,
            int startLine,
            int endLine,
            AtomicInteger chunkIndex) {
        if (LineText.estimatedTokens(content) <= MAX_TOKENS) {
            return List.of(chunk(content, context, packageName, className, methodName, chunkType, startLine, endLine, chunkIndex));
        }

        List<String> lines = LineText.lines(content);
        int targetChars = TARGET_SPLIT_TOKENS * 4;
        int estimatedLinesPerChunk = Math.max(1, Math.min(lines.size(), targetChars / Math.max(1, averageLineLength(lines))));
        int overlapLines = Math.max(1, (int) Math.ceil(estimatedLinesPerChunk * OVERLAP_RATIO));
        List<RepositoryChunk> chunks = new ArrayList<>();
        int cursor = 0;
        while (cursor < lines.size()) {
            int chars = 0;
            int endExclusive = cursor;
            while (endExclusive < lines.size() && (chars < targetChars || endExclusive == cursor)) {
                chars += lines.get(endExclusive).length() + 1;
                endExclusive++;
            }
            int childStart = startLine + cursor;
            int childEnd = startLine + endExclusive - 1;
            String childContent = String.join(System.lineSeparator(), lines.subList(cursor, endExclusive));
            chunks.add(chunk(childContent, context, packageName, className, methodName, chunkType, childStart, childEnd, chunkIndex));
            if (endExclusive >= lines.size()) {
                break;
            }
            cursor = Math.max(endExclusive - overlapLines, cursor + 1);
        }
        return chunks;
    }

    private static RepositoryChunk chunk(
            String content,
            ChunkingContext context,
            String packageName,
            String className,
            String methodName,
            ChunkType chunkType,
            int startLine,
            int endLine,
            AtomicInteger chunkIndex) {
        return new RepositoryChunk(
                context.workspaceId(),
                context.repositoryId(),
                context.version(),
                value(packageName),
                value(className),
                value(methodName),
                context.language(),
                value(context.framework()),
                context.filePath(),
                startLine,
                endLine,
                chunkType,
                chunkIndex.getAndIncrement(),
                content);
    }

    private static int averageLineLength(List<String> lines) {
        return (int) Math.max(1, lines.stream().mapToInt(String::length).average().orElse(1));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
