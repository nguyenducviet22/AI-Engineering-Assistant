package com.aiassistant.conversation;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiassistant.retrieval.RetrievalResult;
import com.aiassistant.retrieval.RetrievedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
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
class SpringAiProviderFailureApiTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private com.aiassistant.retrieval.RepositoryRetrievalService retrievalService;
    @MockBean
    private ChatModel chatModel;

    @Test
    void chatProviderFailureReturnsSafeUnavailableResponseWithoutRawProviderBody() throws Exception {
        when(retrievalService.retrieve(any())).thenReturn(new RetrievalResult(List.of(
                new RetrievedChunk("auth-filter", "src/main/java/JwtAuthenticationFilter.java", 15, 41,
                        "JwtAuthenticationFilter", "Reads the bearer token and authenticates the user.", 0.88)
        )));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException(
                "OpenRouter 401 invalid_api_key raw-provider-body sk-test-do-not-return"));

        String token = register("provider-failure-owner@example.com");
        long workspaceId = createWorkspace(token);

        mvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How does JwtAuthenticationFilter authenticate a request?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo(503)))
                .andExpect(jsonPath("$.error", equalTo("AI Provider Unavailable")))
                .andExpect(jsonPath("$.message", equalTo("The assistant is temporarily unavailable, please try again.")))
                .andExpect(jsonPath("$.path", equalTo("/api/v1/workspaces/" + workspaceId + "/chat")))
                .andExpect(jsonPath("$.message", not(containsString("raw-provider-body"))))
                .andExpect(jsonPath("$.message", not(containsString("invalid_api_key"))))
                .andExpect(jsonPath("$.message", not(containsString("sk-test-do-not-return"))));
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
                        .content("{\"name\":\"Provider Failure\",\"description\":\"Chat\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
