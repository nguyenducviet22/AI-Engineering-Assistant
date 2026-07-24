package com.aiassistant.retrieval;

import com.aiassistant.embedding.EmbeddingClient;
import com.aiassistant.embedding.EmbeddingVector;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PgvectorRepositoryRetrievalService implements RepositoryRetrievalService {
    private static final int TOP_K = 8;

    private final EmbeddingClient embeddingClient;
    private final JdbcTemplate jdbcTemplate;

    public PgvectorRepositoryRetrievalService(EmbeddingClient embeddingClient, JdbcTemplate jdbcTemplate) {
        this.embeddingClient = embeddingClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RetrievalResult retrieve(RepositoryRetrievalQuery query) {
        EmbeddingVector questionVector = embeddingClient.embed(query.question());
        String vectorLiteral = questionVector.values().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
        List<RetrievedChunk> chunks = jdbcTemplate.query("""
                        select
                            cc.id,
                            cc.file_path,
                            cc.start_line,
                            cc.end_line,
                            cc.class_name,
                            cc.method_name,
                            cc.content,
                            (1 - (ce.embedding <=> ?::vector)) as score
                        from chunk_embeddings ce
                        join code_chunks cc on cc.id = ce.chunk_id
                        join repositories r on r.id = cc.repository_id
                        where cc.workspace_id = ?
                          and r.status = 'READY'
                          and r.id = (
                              select selected.id
                              from repositories selected
                              where selected.workspace_id = ?
                                and selected.status = 'READY'
                              order by selected.created_at desc, selected.id desc
                              limit 1
                          )
                          and cc.version = r.current_version
                        order by ce.embedding <=> ?::vector
                        limit ?
                        """,
                (rs, rowNum) -> new RetrievedChunk(
                        String.valueOf(rs.getLong("id")),
                        rs.getString("file_path"),
                        rs.getInt("start_line"),
                        rs.getInt("end_line"),
                        symbol(rs.getString("class_name"), rs.getString("method_name")),
                        rs.getString("content"),
                        rs.getDouble("score")
                ),
                vectorLiteral,
                query.workspaceId(),
                query.workspaceId(),
                vectorLiteral,
                TOP_K);
        return new RetrievalResult(chunks.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(TOP_K)
                .toList());
    }

    private static String symbol(String className, String methodName) {
        if (methodName != null && !methodName.isBlank()) {
            return className == null || className.isBlank() ? methodName : className + "." + methodName;
        }
        return className == null ? "" : className;
    }
}
