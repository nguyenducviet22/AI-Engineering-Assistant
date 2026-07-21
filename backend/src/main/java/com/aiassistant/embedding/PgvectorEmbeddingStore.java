package com.aiassistant.embedding;

import java.util.stream.Collectors;
import com.aiassistant.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PgvectorEmbeddingStore implements EmbeddingVectorStore {
    private final JdbcTemplate jdbcTemplate;

    public PgvectorEmbeddingStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void deleteByRepositoryVersion(Long repositoryVersionId) {
        jdbcTemplate.update("""
                delete from chunk_embeddings
                where chunk_id in (
                    select cc.id
                    from code_chunks cc
                    join source_files sf on sf.id = cc.source_file_id
                    where sf.repository_version_id = ?
                )
                """, repositoryVersionId);
    }

    @Override
    public void store(Long repositoryVersionId, Long chunkId, EmbeddingVector vector) {
        if (vector.dimensions() != 1536 || vector.values().size() != 1536) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Embedding Dimension Mismatch", "Embedding vector must contain exactly 1536 dimensions.");
        }
        int inserted = jdbcTemplate.update(
                """
                insert into chunk_embeddings (chunk_id, model, dimensions, embedding)
                select ?, ?, ?, ?::vector
                from code_chunks cc
                join source_files sf on sf.id = cc.source_file_id
                where cc.id = ? and sf.repository_version_id = ?
                """,
                chunkId,
                vector.model(),
                vector.dimensions(),
                vector.values().stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]")),
                chunkId,
                repositoryVersionId);
        if (inserted != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Embedding Storage Failed", "Embedding chunk does not belong to the target repository version.");
        }
    }
}
