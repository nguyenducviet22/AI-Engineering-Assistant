package com.aiassistant.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PgvectorFlywaySchemaVerificationCheck {
    private static final String URL = System.getenv().getOrDefault(
            "PGVECTOR_VERIFY_JDBC_URL",
            "jdbc:postgresql://localhost:55432/ai_verify");
    private static final String USER = System.getenv().getOrDefault("PGVECTOR_VERIFY_USER", "postgres");
    private static final String PASSWORD = System.getenv().getOrDefault("PGVECTOR_VERIFY_PASSWORD", "postgres");

    @Test
    void verifiesPhase1BaselineThenPhase2AndFreshFlywaySchemaOnRealPostgres() throws Exception {
        assertDatabaseReachable();

        resetSchema("baseline_verify");
        applyPhase1SchemaAndSeedData("baseline_verify");
        migrate("baseline_verify");
        assertFlywayVersions("baseline_verify", Set.of("1:<< Flyway Baseline >>", "2:repository processing"));
        assertExpectedSchema("baseline_verify");

        resetSchema("fresh_verify");
        migrate("fresh_verify");
        assertFlywayVersions("fresh_verify", Set.of("1:phase1 schema", "2:repository processing"));
        assertExpectedSchema("fresh_verify");
    }

    private void assertDatabaseReachable() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select 1")) {
            assertThat(result.next()).isTrue();
        }
    }

    private void resetSchema(String schema) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + schema + " cascade");
            statement.execute("create schema " + schema);
        }
    }

    private void applyPhase1SchemaAndSeedData(String schema) throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "migration", "V1__phase1_schema.sql"));
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.execute(sql);
            statement.execute("""
                    insert into users(email, password_hash, full_name, role, created_at, updated_at)
                    values ('phase1@example.com', 'hash', 'Phase One', 'USER', now(), now())
                    """);
            statement.execute("""
                    insert into workspaces(owner_id, name, language, framework, visibility, created_at, updated_at)
                    values (1, 'Phase 1 Workspace', 'Java', 'Spring Boot', 'PRIVATE', now(), now())
                    """);
            statement.execute("""
                    insert into repositories(workspace_id, repository_name, status, created_at)
                    values (1, 'phase1-repo', 'READY', now())
                    """);
            statement.execute("""
                    insert into repository_versions(repository_id, version, upload_date)
                    values (1, 1, now())
                    """);
        }
    }

    private void migrate(String schema) {
        Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("filesystem:src/main/resources/db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();
    }

    private void assertFlywayVersions(String schema, Set<String> expectedVersionsAndDescriptions) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        select version || ':' || description as version_and_description
                        from %s.flyway_schema_history
                        where success = true
                        """.formatted(schema))) {
            Set<String> actual = readSet(result, "version_and_description");
            assertThat(actual).containsAll(expectedVersionsAndDescriptions);
        }
    }

    private void assertExpectedSchema(String schema) throws Exception {
        assertColumns(schema);
        assertForeignKeys(schema);
        assertIndexes(schema);
        assertVectorExtensionAndType(schema);
    }

    private void assertColumns(String schema) throws Exception {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("users", Set.of("id", "email", "password_hash", "full_name", "avatar", "role", "created_at", "updated_at"));
        expected.put("workspaces", Set.of("id", "owner_id", "name", "description", "language", "framework", "visibility", "created_at", "updated_at"));
        expected.put("repositories", Set.of("id", "workspace_id", "repository_name", "language", "framework", "build_tool", "package_manager", "status", "current_version", "file_count", "repository_size", "failure_reason", "created_at"));
        expected.put("repository_versions", Set.of("id", "repository_id", "version", "commit_hash", "upload_date"));
        expected.put("refresh_tokens", Set.of("id", "user_id", "token_hash", "expires_at", "revoked_at", "created_at"));
        expected.put("source_files", Set.of("id", "repository_version_id", "file_name", "path", "language", "size_bytes", "checksum"));
        expected.put("repository_status_history", Set.of("id", "repository_id", "status", "reason", "created_at"));
        expected.put("code_chunks", Set.of("id", "source_file_id", "chunk_index", "chunk_type", "content", "start_line", "end_line", "workspace_id", "repository_id", "version", "package_name", "class_name", "method_name", "language", "framework", "file_path"));
        expected.put("chunk_embeddings", Set.of("id", "chunk_id", "model", "dimensions", "embedding", "created_at"));

        for (Map.Entry<String, Set<String>> entry : expected.entrySet()) {
            try (Connection connection = connection();
                    Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            select column_name
                            from information_schema.columns
                            where table_schema = '%s' and table_name = '%s'
                            """.formatted(schema, entry.getKey()))) {
                assertThat(readSet(result, "column_name"))
                        .as(schema + "." + entry.getKey() + " columns")
                        .containsExactlyInAnyOrderElementsOf(entry.getValue());
            }
        }
    }

    private void assertForeignKeys(String schema) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        select tc.table_name || '.' || kcu.column_name || '->' || ccu.table_name || '.' || ccu.column_name as fk
                        from information_schema.table_constraints tc
                        join information_schema.key_column_usage kcu
                          on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema
                        join information_schema.constraint_column_usage ccu
                          on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema
                        where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = '%s'
                        """.formatted(schema))) {
            assertThat(readSet(result, "fk")).contains(
                    "workspaces.owner_id->users.id",
                    "repositories.workspace_id->workspaces.id",
                    "repository_versions.repository_id->repositories.id",
                    "refresh_tokens.user_id->users.id",
                    "source_files.repository_version_id->repository_versions.id",
                    "repository_status_history.repository_id->repositories.id",
                    "code_chunks.source_file_id->source_files.id",
                    "chunk_embeddings.chunk_id->code_chunks.id");
        }
    }

    private void assertIndexes(String schema) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        select indexname
                        from pg_indexes
                        where schemaname = '%s'
                        """.formatted(schema))) {
            assertThat(readSet(result, "indexname")).contains(
                    "idx_refresh_token_hash",
                    "idx_source_files_repository_version",
                    "idx_repository_status_history_repository",
                    "idx_source_files_version_path",
                    "idx_code_chunks_source_file",
                    "idx_code_chunks_metadata",
                    "idx_code_chunks_symbols",
                    "idx_chunk_embeddings_chunk",
                    "idx_chunk_embeddings_vector_cosine");
        }
    }

    private void assertVectorExtensionAndType(String schema) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet extension = statement.executeQuery("select extname from pg_extension where extname = 'vector'")) {
            assertThat(readSet(extension, "extname")).contains("vector");
        }
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet type = statement.executeQuery("""
                        select format_type(a.atttypid, a.atttypmod) as column_type
                        from pg_attribute a
                        join pg_class c on c.oid = a.attrelid
                        join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = '%s'
                          and c.relname = 'chunk_embeddings'
                          and a.attname = 'embedding'
                          and not a.attisdropped
                        """.formatted(schema))) {
            assertThat(readSet(type, "column_type"))
                    .anySatisfy(columnType -> assertThat(columnType).endsWith("vector(1536)"));
        }
    }

    private Set<String> readSet(ResultSet result, String column) throws Exception {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        while (result.next()) {
            values.add(result.getString(column));
        }
        return values;
    }

    private Connection connection() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", USER);
        properties.setProperty("password", PASSWORD);
        return DriverManager.getConnection(URL, properties);
    }
}
