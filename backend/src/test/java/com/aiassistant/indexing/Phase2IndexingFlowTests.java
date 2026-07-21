package com.aiassistant.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiassistant.auth.entity.User;
import com.aiassistant.auth.repository.UserRepository;
import com.aiassistant.embedding.EmbeddingClient;
import com.aiassistant.embedding.EmbeddingVector;
import com.aiassistant.embedding.EmbeddingVectorStore;
import com.aiassistant.indexing.repository.CodeChunkRepository;
import com.aiassistant.repository.entity.ProjectRepository;
import com.aiassistant.repository.entity.RepositoryMetadata;
import com.aiassistant.repository.entity.RepositoryStatus;
import com.aiassistant.repository.entity.RepositoryStatusHistory;
import com.aiassistant.repository.repository.ProjectRepositoryRepository;
import com.aiassistant.repository.repository.RepositoryStatusHistoryRepository;
import com.aiassistant.workspace.entity.Workspace;
import com.aiassistant.workspace.entity.WorkspaceVisibility;
import com.aiassistant.workspace.repository.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"app.indexing.enabled=true", "app.indexing.async-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase2IndexingFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RepositoryStatusHistoryRepository statusHistory;
    @Autowired CodeChunkRepository codeChunks;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired ProjectRepositoryRepository repositories;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IndexingStartupRecovery startupRecovery;
    @Autowired ControllableEmbeddingClient embeddingClient;

    @Test
    void uploadRepositoryIndexesSynchronouslyAndRecordsFullStatusChain() throws Exception {
        String token = register("phase2-ready@example.com");
        JsonNode workspace = postJson("/api/v1/workspaces", token,
                "{\"name\":\"Repo\",\"description\":\"Phase 2\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}");
        MockMultipartFile zip = new MockMultipartFile("file", "phase2.zip", "application/zip",
                zipBytes(
                        new Entry("pom.xml", "<project></project>"),
                        new Entry("src/main/java/com/example/AuthService.java", """
                                package com.example;
                                public class AuthService {
                                    public String login(String email) {
                                        return email.trim();
                                    }
                                }
                                """)));

        MvcResult upload = mvc.perform(multipart("/api/v1/workspaces/" + workspace.get("id").asLong() + "/repositories")
                        .file(zip)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("INDEXING")))
                .andReturn();
        long repositoryId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/v1/repositories/" + repositoryId + "/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("READY")));

        assertThat(statusHistory.findByRepositoryIdOrderByCreatedAtAscIdAsc(repositoryId))
                .extracting(RepositoryStatusHistory::getStatus)
                .containsExactly(RepositoryStatus.UPLOADING, RepositoryStatus.VALIDATING, RepositoryStatus.INDEXING, RepositoryStatus.READY);
        assertThat(codeChunks.findByRepositoryIdAndVersion(repositoryId, 1))
                .isNotEmpty()
                .allSatisfy(chunk -> {
                    assertThat(chunk.getWorkspaceId()).isEqualTo(workspace.get("id").asLong());
                    assertThat(chunk.getRepositoryId()).isEqualTo(repositoryId);
                    assertThat(chunk.getVersion()).isEqualTo(1);
                    assertThat(chunk.getPackageName()).isNotNull();
                    assertThat(chunk.getClassName()).isNotNull();
                    assertThat(chunk.getMethodName()).isNotNull();
                    assertThat(chunk.getLanguage()).isNotBlank();
                    assertThat(chunk.getFramework()).isNotNull();
                    assertThat(chunk.getFilePath()).isNotBlank();
                    assertThat(chunk.getStartLine()).isPositive();
                    assertThat(chunk.getEndLine()).isGreaterThanOrEqualTo(chunk.getStartLine());
                });
    }

    @Test
    void startupRecoveryMarksInterruptedIndexingAsFailedWithRetryableReason() throws Exception {
        User user = users.save(new User("restart-owner@example.com", passwordEncoder.encode("Password123"), "Restart Owner"));
        Workspace workspace = workspaces.save(new Workspace(user, "Restart", "Recovery", "Java", "Spring Boot", WorkspaceVisibility.PRIVATE));
        ProjectRepository repository = repositories.save(new ProjectRepository(workspace, "restart.zip"));
        repository.markValidated(new RepositoryMetadata("Java", "Spring Boot", "Maven", "Unknown", 1, 100));
        repositories.save(repository);

        startupRecovery.run(null);

        ProjectRepository recovered = repositories.findById(repository.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(RepositoryStatus.FAILED);
        assertThat(recovered.getFailureReason()).isEqualTo(IndexingStartupRecovery.INTERRUPTED_REASON);
        assertThat(statusHistory.findByRepositoryIdOrderByCreatedAtAscIdAsc(repository.getId()))
                .last()
                .satisfies(event -> {
                    assertThat(event.getStatus()).isEqualTo(RepositoryStatus.FAILED);
                    assertThat(event.getReason()).isEqualTo(IndexingStartupRecovery.INTERRUPTED_REASON);
                });
    }

    @Test
    void failedIndexingCanRetryWithoutDuplicatingPartialChunks() throws Exception {
        String token = register("phase2-retry@example.com");
        JsonNode workspace = postJson("/api/v1/workspaces", token,
                "{\"name\":\"Retry\",\"description\":\"Phase 2\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}");
        embeddingClient.failRequests.set(true);
        MockMultipartFile zip = new MockMultipartFile("file", "retry.zip", "application/zip",
                zipBytes(new Entry("src/main/java/com/example/RetryService.java", """
                        package com.example;
                        public class RetryService {
                            void first() {}
                            void second() {}
                        }
                        """)));

        MvcResult upload = mvc.perform(multipart("/api/v1/workspaces/" + workspace.get("id").asLong() + "/repositories")
                        .file(zip)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long repositoryId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/v1/repositories/" + repositoryId + "/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("FAILED")))
                .andExpect(jsonPath("$.failureReason", equalTo("forced embedding failure")));
        int partialChunks = codeChunks.findByRepositoryIdAndVersion(repositoryId, 1).size();
        assertThat(partialChunks).isGreaterThan(0);

        embeddingClient.failRequests.set(false);
        mvc.perform(post("/api/v1/repositories/" + repositoryId + "/retry-indexing").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("INDEXING")));
        mvc.perform(get("/api/v1/repositories/" + repositoryId + "/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("READY")));

        List<?> finalChunks = codeChunks.findByRepositoryIdAndVersion(repositoryId, 1);
        assertThat(finalChunks.size()).isGreaterThan(partialChunks);
        mvc.perform(post("/api/v1/repositories/" + repositoryId + "/retry-indexing").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("INDEXING")));
        mvc.perform(get("/api/v1/repositories/" + repositoryId + "/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("READY")));
        assertThat(codeChunks.findByRepositoryIdAndVersion(repositoryId, 1)).hasSize(finalChunks.size());
        assertThat(statusHistory.findByRepositoryIdOrderByCreatedAtAscIdAsc(repositoryId))
                .extracting(RepositoryStatusHistory::getStatus)
                .contains(RepositoryStatus.FAILED, RepositoryStatus.READY);
    }


    private String register(String email) throws Exception {
        return postJson("/api/v1/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Password123\",\"fullName\":\"Test User\"}")
                .get("accessToken").asText();
    }

    private JsonNode postJson(String url, String body) throws Exception {
        MvcResult result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String url, String token, String body) throws Exception {
        MvcResult result = mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private byte[] zipBytes(Entry... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private record Entry(String name, String content) {
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        @Primary
        ControllableEmbeddingClient fakeEmbeddingClient() {
            return new ControllableEmbeddingClient();
        }

        @Bean
        @Primary
        EmbeddingVectorStore fakeEmbeddingVectorStore() {
            return new EmbeddingVectorStore() {
                @Override
                public void deleteByRepositoryVersion(Long repositoryVersionId) {
                }

                @Override
                public void store(Long repositoryVersionId, Long chunkId, EmbeddingVector vector) {
                    assertThat(vector.dimensions()).isEqualTo(1536);
                }
            };
        }
    }

    static class ControllableEmbeddingClient implements EmbeddingClient {
        final AtomicBoolean failRequests = new AtomicBoolean();

        @Override
        public EmbeddingVector embed(String content) {
            if (failRequests.get()) {
                throw new IllegalStateException("forced embedding failure");
            }
            return new EmbeddingVector("test-embedding", 1536, java.util.Collections.nCopies(1536, 0.01d));
        }
    }
}
