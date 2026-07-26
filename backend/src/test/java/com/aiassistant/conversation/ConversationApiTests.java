package com.aiassistant.conversation;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiassistant.ai.LlmService;
import com.aiassistant.retrieval.RetrievalResult;
import com.aiassistant.retrieval.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationApiTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private com.aiassistant.retrieval.RepositoryRetrievalService retrievalService;
    @MockBean
    private LlmService llmService;

    @Test
    void chatPersistsConversationAndOwnerScopedMessageEndpoints() throws Exception {
        when(retrievalService.retrieve(any())).thenReturn(new RetrievalResult(List.of(
                new RetrievedChunk("auth-controller", "src/main/java/AuthController.java", 10, 40, "AuthController.login", "Login flow implementation.", 0.91),
                new RetrievedChunk("auth-service", "src/main/java/AuthService.java", 20, 60, "AuthService.authenticate", "Authentication service implementation.", 0.55)
        )));
        when(llmService.generate(any(Prompt.class))).thenReturn(new LlmService.LlmResponse(
                "Login starts in the controller. [AuthController.java:10-40]",
                "openai/gpt-4.1",
                72
        ));
        String ownerToken = register("conversation-owner@example.com");
        String otherToken = register("conversation-other@example.com");
        long workspaceId = createWorkspace(ownerToken);

        MvcResult chatResult = mvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Where does login start?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", equalTo("Login starts in the controller. [AuthController.java:10-40]")))
                .andExpect(jsonPath("$.refused", equalTo(false)))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.model", equalTo("openai/gpt-4.1")))
                .andReturn();
        JsonNode chat = objectMapper.readTree(chatResult.getResponse().getContentAsString());
        long conversationId = chat.get("conversationId").asLong();

        mvc.perform(get("/api/v1/workspaces/" + workspaceId + "/conversations")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", equalTo((int) conversationId)));

        mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].role", equalTo("USER")))
                .andExpect(jsonPath("$.messages[1].role", equalTo("ASSISTANT")))
                .andExpect(jsonPath("$.messages[1].citations", hasSize(1)))
                .andExpect(jsonPath("$.messages[1].citations[0].chunkId", equalTo("auth-controller")));

        mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void uncitedLlmAnswerReturnsStandardRefusalInsteadOfServerError() throws Exception {
        when(retrievalService.retrieve(any())).thenReturn(new RetrievalResult(List.of(
                new RetrievedChunk("history", "VNR202_SPST/agent.md", 34, 40, "Ho Chi Minh context", "Historical context mentions Ho Chi Minh.", 0.45),
                new RetrievedChunk("timeline", "VNR202_SPST/agent.md", 87, 95, "Vietnam timeline", "Related timeline content.", 0.39)
        )));
        when(llmService.generate(any(Prompt.class))).thenReturn(new LlmService.LlmResponse(
                "There is not enough repository data to answer accurately.",
                "openai/gpt-4.1",
                35
        ));
        String ownerToken = register("uncited-answer-owner@example.com");
        long workspaceId = createWorkspace(ownerToken);

        mvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Can you tell me more about Ho Chi Minh?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", equalTo("I could not find sufficient repository context to answer that accurately.")))
                .andExpect(jsonPath("$.refused", equalTo(true)))
                .andExpect(jsonPath("$.citations", hasSize(0)))
                .andExpect(jsonPath("$.model", nullValue()))
                .andExpect(jsonPath("$.tokenUsage", nullValue()));
    }

    private String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\",\"fullName\":\"Test User\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createWorkspace(String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\",\"description\":\"Chat\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
