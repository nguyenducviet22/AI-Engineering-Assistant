package com.aiassistant;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase1ApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshLogoutAndProfileFlow() throws Exception {
        JsonNode registered = postJson("/api/v1/auth/register",
                "{\"email\":\"dev@example.com\",\"password\":\"Password123\",\"fullName\":\"Dev User\"}");
        String accessToken = registered.get("accessToken").asText();
        String refreshToken = registered.get("refreshToken").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo("dev@example.com")));
        mvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Dev Updated\",\"avatar\":\"https://example.com/avatar.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", equalTo("Dev Updated")));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@example.com\",\"password\":\"Password123\",\"fullName\":\"Other\"}"))
                .andExpect(status().isConflict());

        JsonNode refreshed = postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        String rotatedRefreshToken = refreshed.get("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode logoutUser = postJson("/api/v1/auth/register",
                "{\"email\":\"logout@example.com\",\"password\":\"Password123\",\"fullName\":\"Logout User\"}");
        String logoutRefreshToken = logoutUser.get("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + logoutRefreshToken + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + logoutRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode passwordUser = postJson("/api/v1/auth/register",
                "{\"email\":\"password@example.com\",\"password\":\"Password123\",\"fullName\":\"Password User\"}");
        mvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + passwordUser.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123\",\"newPassword\":\"NewPassword123\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"password@example.com\",\"password\":\"NewPassword123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void workspaceCrudIsScopedToOwner() throws Exception {
        String ownerToken = register("owner@example.com");
        String otherToken = register("other@example.com");
        JsonNode workspace = postJson("/api/v1/workspaces", ownerToken,
                "{\"name\":\"Backend API\",\"description\":\"Phase 1\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}");
        long workspaceId = workspace.get("id").asLong();

        mvc.perform(get("/api/v1/workspaces/" + workspaceId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend API v2\",\"description\":\"Phase 1\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Backend API v2")));
    }

    @Test
    void repositoryUploadValidatesAndCreatesIndexingPlaceholder() throws Exception {
        String token = register("repo-owner@example.com");
        JsonNode workspace = postJson("/api/v1/workspaces", token,
                "{\"name\":\"Repo\",\"description\":\"Upload\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}");
        MockMultipartFile zip = new MockMultipartFile("file", "sample.zip", "application/zip",
                zipBytes(new Entry("pom.xml", "<project></project>"), new Entry("src/main/java/App.java", "class App {}")));

        MvcResult uploadResult = mvc.perform(multipart("/api/v1/workspaces/" + workspace.get("id").asLong() + "/repositories")
                        .file(zip)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("INDEXING")))
                .andExpect(jsonPath("$.language", equalTo("Java")))
                .andExpect(jsonPath("$.framework", equalTo("Spring Boot")))
                .andExpect(jsonPath("$.currentVersion", equalTo(1)))
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String otherToken = register("repo-reader@example.com");
        mvc.perform(get("/api/v1/repositories/" + uploaded.get("id").asLong()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        Path stored = Path.of("target/test-repositories")
                .resolve("workspace-" + workspace.get("id").asLong())
                .resolve("repository-" + uploaded.get("id").asLong());
        org.assertj.core.api.Assertions.assertThat(Files.exists(stored)).isTrue();
        mvc.perform(delete("/api/v1/repositories/" + uploaded.get("id").asLong()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    void repositoryUploadRejectsUnsafeArchives() throws Exception {
        String token = register("zip-owner@example.com");
        JsonNode workspace = postJson("/api/v1/workspaces", token,
                "{\"name\":\"Repo\",\"description\":\"Upload\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}");

        uploadZip(token, workspace.get("id").asLong(), "slip.zip", zipBytes(new Entry("../evil.java", "class Evil {}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Invalid ZIP Entry")));
        uploadZip(token, workspace.get("id").asLong(), "drive.zip", zipBytes(new Entry("C:/evil.java", "class Evil {}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Invalid ZIP Entry")));
        uploadZip(token, workspace.get("id").asLong(), "nested.zip", zipBytes(new Entry("nested.zip", "not really zip")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Nested Archive Rejected")));
        uploadZip(token, workspace.get("id").asLong(), "bomb.zip", zipBytes(new Entry("src/main/java/App.java", "class App {}"),
                        new Entry("large.bin", "A".repeat(200_000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Suspicious Compression Ratio")));
        uploadZip(token, workspace.get("id").asLong(), "empty.zip", zipBytes(new Entry("README.png", "binary-ish")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Unsupported Repository")));
    }

    @Test
    void repositoryUploadIgnoresGeneratedFoldersForFileCount() throws Exception {
        String token = register("generated-owner@example.com");
        JsonNode workspace = postJson("/api/v1/workspaces", token,
                "{\"name\":\"Repo\",\"description\":\"Upload\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}");

        MvcResult result = mvc.perform(multipart("/api/v1/workspaces/" + workspace.get("id").asLong() + "/repositories")
                        .file(new MockMultipartFile("file", "with-node-modules.zip", "application/zip",
                                zipBytesWithGeneratedFiles(5_100)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount", equalTo(2)))
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(result.getResponse().getContentAsString());
        mvc.perform(delete("/api/v1/repositories/" + uploaded.get("id").asLong()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
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

    private String register(String email) throws Exception {
        JsonNode response = postJson("/api/v1/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Password123\",\"fullName\":\"Test User\"}");
        return response.get("accessToken").asText();
    }

    private ResultActionsDsl uploadZip(String token, long workspaceId, String name, byte[] bytes) throws Exception {
        MockMultipartFile zip = new MockMultipartFile("file", name, "application/zip", bytes);
        return new ResultActionsDsl(mvc.perform(multipart("/api/v1/workspaces/" + workspaceId + "/repositories").file(zip).header("Authorization", "Bearer " + token)));
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

    private byte[] zipBytesWithGeneratedFiles(int generatedFiles) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index < generatedFiles; index++) {
                String separator = index % 2 == 0 ? "/" : "\\";
                zip.putNextEntry(new ZipEntry("node_modules" + separator + "pkg-" + index + separator + "index.js"));
                zip.write("module.exports = {};".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("pom.xml"));
            zip.write("<project></project>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("src/main/java/App.java"));
            zip.write("class App {}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private record Entry(String name, String content) {}

    private record ResultActionsDsl(org.springframework.test.web.servlet.ResultActions actions) {
        org.springframework.test.web.servlet.ResultActions andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            return actions.andExpect(matcher);
        }
    }
}
