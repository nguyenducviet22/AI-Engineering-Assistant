package com.aiassistant.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ScriptSourceChunker implements SourceChunker {
    private static final Pattern BOUNDARY = Pattern.compile(
            "^\\s*(?:export\\s+default\\s+|export\\s+)?(?:(class)\\s+([A-Za-z_$][\\w$]*)|(async\\s+)?function\\s+([A-Za-z_$][\\w$]*)|(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_$][\\w$]*)\\s*=>)");

    @Override
    public boolean supports(String language) {
        return "TypeScript".equalsIgnoreCase(language) || "JavaScript".equalsIgnoreCase(language);
    }

    @Override
    public List<RepositoryChunk> chunk(String content, ChunkingContext context) {
        List<String> lines = LineText.lines(content);
        AtomicInteger chunkIndex = new AtomicInteger();
        List<RepositoryChunk> chunks = new ArrayList<>();
        String currentClass = "";
        int line = 0;
        while (line < lines.size()) {
            Matcher matcher = BOUNDARY.matcher(lines.get(line));
            if (!matcher.find()) {
                line++;
                continue;
            }
            boolean isClass = matcher.group(1) != null;
            String name = firstPresent(matcher.group(2), matcher.group(4), matcher.group(5));
            int endLine = findBlockEnd(lines, line);
            if (isClass) {
                currentClass = name;
                chunks.addAll(SemanticChunkFactory.chunks(
                        LineText.slice(lines, line + 1, endLine),
                        context,
                        "",
                        name,
                        "",
                        ChunkType.CLASS,
                        line + 1,
                        endLine,
                        chunkIndex));
            } else {
                chunks.addAll(SemanticChunkFactory.chunks(
                        LineText.slice(lines, line + 1, endLine),
                        context,
                        "",
                        currentClass,
                        name,
                        ChunkType.METHOD,
                        line + 1,
                        endLine,
                        chunkIndex));
            }
            line = Math.max(line + 1, endLine);
        }
        if (chunks.isEmpty()) {
            chunks.addAll(SemanticChunkFactory.chunks(
                    content, context, "", "", "", ChunkType.SOURCE, 1, Math.max(1, lines.size()), chunkIndex));
        }
        return chunks;
    }

    private int findBlockEnd(List<String> lines, int startIndex) {
        int depth = 0;
        boolean seenOpen = false;
        for (int index = startIndex; index < lines.size(); index++) {
            String line = stripStringsAndLineComment(lines.get(index));
            for (int charIndex = 0; charIndex < line.length(); charIndex++) {
                char value = line.charAt(charIndex);
                if (value == '{') {
                    depth++;
                    seenOpen = true;
                } else if (value == '}') {
                    depth--;
                    if (seenOpen && depth <= 0) {
                        return index + 1;
                    }
                }
            }
        }
        return startIndex + 1;
    }

    private String stripStringsAndLineComment(String line) {
        StringBuilder result = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (quote == 0 && value == '/' && index + 1 < line.length() && line.charAt(index + 1) == '/') {
                break;
            }
            if ((value == '"' || value == '\'' || value == '`') && (index == 0 || line.charAt(index - 1) != '\\')) {
                quote = quote == 0 ? value : quote == value ? 0 : quote;
                result.append(' ');
            } else {
                result.append(quote == 0 ? value : ' ');
            }
        }
        return result.toString();
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
