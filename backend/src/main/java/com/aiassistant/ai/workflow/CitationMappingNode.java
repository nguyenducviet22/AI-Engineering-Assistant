package com.aiassistant.ai.workflow;

import com.aiassistant.retrieval.Citation;
import com.aiassistant.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CitationMappingNode implements RepositoryChatNode {
    private static final Pattern BRACKETED_FILE_LINES = Pattern.compile("\\[([^\\[\\]:]+):(\\d+)-(\\d+)]");
    private static final Pattern BRACKETED_FILE_COMMA_LINES = Pattern.compile("\\[([^\\[\\]]+?),\\s*lines\\s+(\\d+)-(\\d+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHUNK_MARKER = Pattern.compile("\\[chunk:([^\\]]+)]");

    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        if (state.refused()) {
            return state.withCitations(List.of()).mark(step());
        }
        List<Citation> citations = extractCitations(state.answer(), state.retrievalResult().chunks()).stream()
                .distinct()
                .toList();
        if (citations.isEmpty()) {
            throw new IllegalStateException("Non-refusal repository answers require citations mapped from response spans.");
        }
        return state.withCitations(citations).mark(step());
    }

    private List<Citation> extractCitations(String answer, List<RetrievedChunk> chunks) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        List<Citation> citations = new ArrayList<>();
        citations.addAll(bracketedFileLineCitations(answer, chunks));
        citations.addAll(bracketedFileCommaLineCitations(answer, chunks));
        citations.addAll(chunkMarkerCitations(answer, chunks));
        return citations;
    }

    private List<Citation> bracketedFileLineCitations(String answer, List<RetrievedChunk> chunks) {
        Matcher matcher = BRACKETED_FILE_LINES.matcher(answer);
        return matcher.results()
                .map(match -> matchToFileLineCitation(answer, chunks, match))
                .flatMap(Optional::stream)
                .toList();
    }

    private List<Citation> bracketedFileCommaLineCitations(String answer, List<RetrievedChunk> chunks) {
        Matcher matcher = BRACKETED_FILE_COMMA_LINES.matcher(answer);
        return matcher.results()
                .map(match -> matchToFileLineCitation(answer, chunks, match))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Citation> matchToFileLineCitation(String answer, List<RetrievedChunk> chunks, java.util.regex.MatchResult match) {
        String file = match.group(1);
        int start = Integer.parseInt(match.group(2));
        int end = Integer.parseInt(match.group(3));
        return chunks.stream()
                .filter(chunk -> sameFile(chunk.filePath(), file))
                .filter(chunk -> lineRangesOverlap(chunk.startLine(), chunk.endLine(), start, end))
                .max(Comparator.comparingDouble(RetrievedChunk::score))
                .map(chunk -> new Citation(
                        chunk.chunkId(),
                        chunk.filePath(),
                        Math.max(chunk.startLine(), start),
                        Math.min(chunk.endLine(), end),
                        match.start(),
                        match.end(),
                        answer.substring(match.start(), match.end())));
    }

    private List<Citation> chunkMarkerCitations(String answer, List<RetrievedChunk> chunks) {
        Matcher matcher = CHUNK_MARKER.matcher(answer);
        return matcher.results()
                .map(match -> chunks.stream()
                        .filter(chunk -> chunk.chunkId().equals(match.group(1)))
                        .findFirst()
                        .map(chunk -> new Citation(
                                chunk.chunkId(),
                                chunk.filePath(),
                                chunk.startLine(),
                                chunk.endLine(),
                                match.start(),
                                match.end(),
                                answer.substring(match.start(), match.end()))))
                .flatMap(Optional::stream)
                .toList();
    }

    private boolean sameFile(String chunkFilePath, String citedFilePath) {
        return chunkFilePath.equals(citedFilePath) || chunkFilePath.endsWith("/" + citedFilePath) || chunkFilePath.endsWith("\\" + citedFilePath);
    }

    private boolean lineRangesOverlap(int chunkStart, int chunkEnd, int citationStart, int citationEnd) {
        return chunkStart <= citationEnd && citationStart <= chunkEnd;
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.CITATION_MAPPING;
    }
}
